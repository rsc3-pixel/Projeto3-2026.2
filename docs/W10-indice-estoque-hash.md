# W10 — Índice de estoque por hash: como as colisões são tratadas

> Documento da Unidade 2 de AED, subtarefa PI3-132. Complementa a
> [`evidencias-pi3-131.md`](evidencias-pi3-131.md), que integrou o índice ao caminho real de
> alocação.

## 1. O índice

| Elemento | O quê |
|---|---|
| Estrutura | `HashMap<ChaveEstoque, List<Bolsa>>` |
| Chave | par de enums: `GrupoSanguineo` + `TipoHemocomponente` |
| Valor | lista das bolsas daquela combinação |
| Arquivo | [`IndiceEstoque.java`](../src/main/java/com/rotavital/estruturas/IndiceEstoque.java) |

Sem o índice, responder "quais bolsas O- de concentrado de hemácias existem" exigiria percorrer o
estoque inteiro, O(n) por consulta. Com a chave composta, a consulta cai direto no balde da
combinação.

Quem o mantém vivo é o
[`IndiceEstoqueServico`](../src/main/java/com/rotavital/servico/IndiceEstoqueServico.java), que
guarda apenas bolsas `DISPONIVEL` e sincroniza com o banco depois do commit. Quem o consulta é a
**alocação automática**, `POST /api/v1/requisicoes/{id}/itens/{itemId}/alocacoes/automatica`, que
busca as compatíveis por hash e escolhe por FEFO. Ambos foram entregues na PI3-131.

## 2. Como o `HashMap` do Java trata colisão

Os valores abaixo foram lidos do código-fonte do JDK 21 usado pelo projeto
(`$JAVA_HOME/lib/src.zip`, `java.base/java/util/HashMap.java`), não de memória:

| Constante | Valor | Linha |
|---|---:|---:|
| `DEFAULT_INITIAL_CAPACITY` | 16 (`1 << 4`) | 238 |
| `DEFAULT_LOAD_FACTOR` | 0.75 | 250 |
| `TREEIFY_THRESHOLD` | 8 | 260 |
| `UNTREEIFY_THRESHOLD` | 6 | 267 |
| `MIN_TREEIFY_CAPACITY` | 64 | 275 |

O caminho de uma chave até o balde tem dois passos:

**Espalhamento** (linha 338) — o `hashCode` da chave é misturado com os próprios bits altos:

```java
(h = key.hashCode()) ^ (h >>> 16)
```

Isso existe porque o índice do balde usa só os bits baixos; sem a mistura, chaves que diferem
apenas nos bits altos cairiam todas no mesmo lugar.

**Índice do balde** (linha 636) — uma máscara, não um resto de divisão:

```java
tab[i = (n - 1) & hash]
```

Funciona porque `n` é sempre potência de dois, então `n - 1` é uma máscara dos bits baixos.

**Quando duas chaves caem no mesmo balde**, o `HashMap` encadeia as entradas numa lista ligada e
desempata por `equals`. Se a cadeia crescer:

- ao inserir a 8ª entrada no mesmo balde (`binCount >= TREEIFY_THRESHOLD - 1`, linha 649), ele
  chama `treeifyBin`;
- dentro de `treeifyBin` (linha 763), se a tabela tiver **menos de 64 baldes**, ele **dobra a
  tabela** em vez de criar a árvore — redistribuir costuma resolver;
- com 64 baldes ou mais, a lista vira **árvore rubro-negra**, e a busca naquele balde passa de
  O(k) para O(log k);
- quando a árvore encolhe abaixo de 6 entradas (`UNTREEIFY_THRESHOLD`), volta a ser lista.

Além disso, a tabela dobra sempre que o número de entradas passa de `capacidade × 0,75`.

## 3. Antes: a colisão acontecia por sorte

A chave usava `Objects.hash(grupoSanguineo, tipo)`. `Objects.hash` combina os `hashCode` dos
argumentos — e o `hashCode` de um enum é o **hash de identidade** herdado de `Object`, um número
derivado do endereço/estado do objeto, que a especificação **não** garante entre execuções.

Medindo as 32 combinações com a conta que o `HashMap` faz, no JDK 21 deste projeto:

| Capacidade | Baldes usados | Combinações em balde já ocupado | Maior cadeia |
|---:|---:|---:|---:|
| 16 | 13 | 19 | 5 |
| 32 | 18 | 14 | 4 |
| 64 | 24 | **8** | 3 |

As cadeias eram curtas — muito abaixo das 8 entradas que acionariam a árvore — então **as colisões
eram inofensivas**. O problema não era desempenho: era o projeto não controlar quantas haveria.

Duas medições mostram isso. Rodando **o mesmo programa** cinco vezes, o resultado foi sempre 8
colisões — parece estável. Mas rodando **outro programa** na mesma JVM, com as mesmas classes, o
número caiu para 6: o hash de identidade depende da ordem de alocação, e qualquer mudança no que o
programa faz antes muda os valores.

A prova mais direta é variar o modo de hash de identidade da HotSpot, com
`-XX:+UnlockExperimentalVMOptions -XX:hashCode=N`, na mesma tabela de 64 baldes:

| `-XX:hashCode` | Colisões | Maior cadeia |
|---:|---:|---:|
| 0 | 9 | 3 |
| 1 | 10 | 2 |
| **2** | **31** | **32** |
| 3 | 6 | 2 |
| 4 | 24 | 5 |
| 5 (padrão) | 8 | 3 |

O modo 2 devolve hash constante: **as 32 chaves vão para o mesmo balde**, cadeia de 32, e a busca
vira O(n). É um modo de diagnóstico, não de produção — mas deixa claro que o número de colisões
dependia de algo fora do código da aplicação.

## 4. Depois: zero colisão por construção

A chave passou a usar um **hash perfeito** sobre o espaço de chaves:

```java
static int hashDaChave(GrupoSanguineo grupoSanguineo, TipoHemocomponente tipo) {
    return grupoSanguineo.ordinal() * TipoHemocomponente.values().length + tipo.ordinal();
}
```

e o mapa é criado com capacidade fixa:

```java
private static final int CAPACIDADE = 64;
private final Map<ChaveEstoque, List<Bolsa>> indice = new HashMap<>(CAPACIDADE);
```

Três consequências, nesta ordem:

1. **Cada combinação recebe um valor distinto de 0 a 31.** É aritmética posicional: o grupo escolhe
   o bloco de 4, o tipo escolhe a posição dentro do bloco.
2. **O espalhamento não altera nada.** Com valores abaixo de 2¹⁶, `h >>> 16` é zero, e
   `h ^ 0 == h`. O índice do balde é o próprio hash.
3. **Nenhum balde é compartilhado e a tabela nunca dobra.** São 32 baldes distintos entre 64, e 32
   fica abaixo do limite de carga (64 × 0,75 = 48).

As 32 combinações e seus hashes, que são também os índices de balde:

| Grupo \ Tipo | `CONCENTRADO_HEMACIAS` | `PLASMA_FRESCO_CONGELADO` | `CONCENTRADO_PLAQUETAS` | `CRIOPRECIPITADO` |
|---|---:|---:|---:|---:|
| `A_POS` | 0 | 1 | 2 | 3 |
| `A_NEG` | 4 | 5 | 6 | 7 |
| `B_POS` | 8 | 9 | 10 | 11 |
| `B_NEG` | 12 | 13 | 14 | 15 |
| `AB_POS` | 16 | 17 | 18 | 19 |
| `AB_NEG` | 20 | 21 | 22 | 23 |
| `O_POS` | 24 | 25 | 26 | 27 |
| `O_NEG` | 28 | 29 | 30 | 31 |

O mesmo resultado em toda execução, em qualquer máquina virtual, com qualquer flag: `ordinal()` é a
posição declarada do enum, não um endereço.

## 5. Por que esta chave distribui bem

Não é mérito da função de hash: é mérito do **espaço de chaves**.

- **Pequeno:** 8 grupos × 4 tipos = 32 combinações.
- **Fechado:** enum tem conjunto de valores finito e conhecido em tempo de compilação. Não existe
  combinação inválida, nem variação de maiúscula, nem espaço sobrando — problemas que uma chave de
  texto concatenado traria.
- **Previsível:** o número de chaves não cresce com o estoque. Mil bolsas ou um milhão, continuam
  sendo as mesmas 32 chaves.

É justamente por o espaço ser pequeno e fechado que o hash perfeito é possível. Numa chave aberta —
um código de rastreio, por exemplo — não daria para enumerar os valores, e aí o encadeamento do
`HashMap` seria a resposta certa.

## 6. O que **não** é colisão

Várias bolsas da mesma combinação ficam na mesma lista. Isso é o **valor** do mapa, não o balde:

```
chave (O_NEG, CONCENTRADO_HEMACIAS) → [BS001, BS002, BS003]
```

Não é colisão de hash — é o agrupamento desejado, e é a razão de o índice existir. Confundir os dois
leva a "otimizar" o que já está certo. Fixado no teste `bolsasDaMesmaChaveFicamJuntas`.

## 7. Limites e manutenção

**Se um enum ganhar valores**, a fórmula continua perfeita, porque usa `values().length` em vez de
um número fixo. O que precisa ser revisto é a `CAPACIDADE`: o produto dos dois tamanhos tem de caber
em `64 × 0,75 = 48`. Hoje são 32; haveria folga até 48 combinações — por exemplo, 12 grupos × 4
tipos. Passando disso, a capacidade precisa dobrar para 128.

**Mudar a ordem dos valores de um enum** altera os `ordinal()` e, portanto, os hashes. Isso **não
corrompe dado nenhum**: o hash não é persistido em lugar algum — vive apenas em memória, dentro
deste mapa, e é recalculado a cada carga do índice. O que fica gravado no banco é o nome da
constante, por `@Enumerated(EnumType.STRING)`.

## 8. Complexidade

| Operação | Custo |
|---|---|
| Localizar a chave | **O(1)**, sem colisão: um acesso ao balde |
| `buscar` | O(1) + **O(k)** para copiar as k bolsas daquela chave |
| `adicionar` | O(1) + O(k) para verificar duplicata na lista |
| `remover` | O(1) + O(k) para achar e remover da lista |

O O(1) da localização agora é exato, não "médio": sem colisão, não há cadeia a percorrer. O O(k) é
no tamanho do balde — as bolsas daquela combinação — e é inevitável para consumir o resultado.

## 9. Testes que provam

Em [`IndiceEstoqueTest`](../src/test/java/com/rotavital/estruturas/IndiceEstoqueTest.java),
`@Nested Colisao`:

| Teste | O que prova |
|---|---|
| `espacoDeChavesEhFechadoEPequeno` | 8 grupos, 4 tipos, 32 combinações |
| `hashPerfeitoSobreAsTrintaEDuasCombinacoes` | as 32 têm hash distinto, todos entre 0 e 31 |
| `nenhumBaldeCompartilhadoComCapacidadeSessentaEQuatro` | reproduz `(h ^ (h >>> 16)) & 63`: 32 baldes distintos |
| `hashEhEstavelEDeterministico` | o hash é `ordinal × 4 + ordinal`, não depende da JVM |
| `contratoEqualsHashCode` | nas 1024 comparações, hash igual ⇔ combinação igual |
| `cadaCombinacaoDevolveApenasASuaBolsa` | com as 32 indexadas, cada busca traz só a sua |
| `bolsasDaMesmaChaveFicamJuntas` | agrupamento na mesma chave, que não é colisão |

## 10. Rastreabilidade

**PI3-96 — W10, Índice de estoque por hash em Java (AED)**:

| Subtarefa | Entrega | PR |
|---|---|---|
| **PI3-131** | Revisão do índice, `IndiceEstoqueServico` e alocação automática | #65 (base `main`) |
| **PI3-132** | Hash sem colisão e este documento | base da PI3-131 |

A equivalência com a versão em C/C++ fica para outra subtarefa da PI3-96; nenhum código em C foi
escrito aqui.
