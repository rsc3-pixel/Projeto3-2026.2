# W10 — FEFO por inserção ordenada: tradução comentada C → Java

> Documento da Unidade 2 de AED. O equivalente da Unidade 1 é
> [`U1-traducao-c-java.md`](U1-traducao-c-java.md), de onde este documento herda a
> estrutura: trecho em C, trecho em Java, e o comentário do que muda e por quê.

## 1. Objetivo

A Unidade 2 de AED pede **priorização FEFO por inserção ordenada**: manter a coleção
ordenada no momento em que cada elemento entra, em vez de ordenar depois ou delegar a
ordem a uma estrutura pronta.

Na Unidade 1 o projeto resolveu a mesma necessidade com **fila de prioridade sobre heap
binário** (PI3-47): um `PriorityQueue` do `java.util` com `Comparator`. Funciona, mas não
demonstra a técnica — a ordenação acontece dentro da biblioteca padrão.

A estrutura não é um exercício isolado. Ela é o ponto onde o RotaVital decide **qual bolsa
sai primeiro**, e tem dois consumidores em produção:

- [`SelecaoFefo`](../src/main/java/com/rotavital/alocacao/SelecaoFefo.java), que monta a fila
  com as candidatas alocáveis e lê o topo;
- [`ServicoAlocacaoRota`](../src/main/java/com/rotavital/alocacao/ServicoAlocacaoRota.java),
  que faz o mesmo depois de filtrar por alcance na malha.

FEFO existe para reduzir descarte por vencimento: consumindo primeiro o que vence antes,
sobra no estoque o que tem mais prazo.

## 2. A técnica

A lista é mantida ordenada o tempo todo. Cada bolsa nova é colocada **direto na posição
certa**, percorrendo a partir do início até achar o ponto de encaixe. Não existe passo de
ordenação depois: a ordem é invariante da estrutura, e por isso a bolsa que vence primeiro
está sempre no início.

**Critério de ordem:** data de validade crescente; no empate, código de rastreio crescente.
O desempate pelo código é o que torna o resultado determinístico — duas bolsas de mesma
validade sempre resolvem para a mesma escolha, em qualquer ordem de montagem do estoque.

```
adicionar(bolsa):
    novo <- no(bolsa)

    se lista vazia:                      // CASO 1
        primeiro <- novo

    senao se vemAntes(bolsa, primeiro.bolsa):   // CASO 2
        novo.proximo <- primeiro
        primeiro     <- novo

    senao:                               // CASO 3
        anterior <- primeiro
        atual    <- primeiro.proximo
        enquanto atual != nulo E vemAntes(atual.bolsa, bolsa):
            anterior <- atual
            atual    <- atual.proximo
        novo.proximo     <- atual        // atual nulo = encaixe no fim
        anterior.proximo <- novo

    tamanho <- tamanho + 1
```

O CASO 3 cobre **meio e fim com as mesmas duas linhas**: quando `atual` é nulo, o nó entra
no final, e nenhum ramo extra é necessário.

A condição do laço usa `vemAntes(atual, nova)` — e não a negação de `vemAntes(nova, atual)`.
A diferença aparece nos empates: assim o percurso avança também sobre os iguais, e a bolsa
nova acaba **depois** deles. É o que torna a inserção estável.

## 3. Tradução comentada C → Java

> **De onde vem o código em C.** Os trechos abaixo são do arquivo
> [`aed/unidade2/c/fila_fefo.c`](../aed/unidade2/c/fila_fefo.c), escrito neste repositório
> como contraparte da versão Java, no mesmo estilo da Unidade 1
> ([`aed/unidade1/c/lista_estoque.c`](../aed/unidade1/c/lista_estoque.c)). Ele compila com
> `gcc -Wall -Wextra -std=c11` sem avisos e roda sem vazamentos. **Não é cópia de código
> mostrado em sala** — é a versão clássica da inserção ordenada, escrita aqui para que a
> comparação seja com linhas reais e verificáveis, e não com um trecho solto no texto.
>
> O lado Java é a
> [`FilaPrioridadeFefo`](../src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java).

### 3.1 Correspondência entre os arquivos

| Papel | Versão em C | Versão em Java | Testes |
|---|---|---|---|
| Fila FEFO | `aed/unidade2/c/fila_fefo.{h,c}` | `FilaPrioridadeFefo` | `FilaPrioridadeFefoTest` |
| Domínio | `aed/unidade2/c/dominio.h` | `com.rotavital.dominio.Bolsa` | — |
| Demonstração | `aed/unidade2/c/main.c` | — | — |

### 3.2 O nó

```c
/* fila_fefo.h:28-31 */
typedef struct NoFefo {
    Bolsa bolsa;                  /* copia da bolsa, armazenada no proprio no */
    struct NoFefo *proximo;       /* NULL quando este e o ultimo no */
} NoFefo;
```

```java
// FilaPrioridadeFefo.java:90-98
private static final class No {
    private final Bolsa bolsa;
    private No proximo;

    private No(Bolsa bolsa) {
        this.bolsa = bolsa;
        this.proximo = null;
    }
}
```

`struct NoFefo *proximo` vira a referência `No.proximo`; `NULL` vira `null`. A divergência é
de armazenamento: o C guarda uma **cópia da struct** dentro do nó, o Java guarda uma
**referência ao objeto**. É a mesma diferença já registrada na tradução da Unidade 1.

### 3.3 A comparação

```c
/* fila_fefo.c:15-21 */
static int vem_antes(const Bolsa *a, const Bolsa *b) {
    int comparacao_de_validade = strcmp(a->data_validade, b->data_validade);
    if (comparacao_de_validade != 0) {
        return comparacao_de_validade < 0;
    }
    return strcmp(a->codigo_rastreio, b->codigo_rastreio) < 0;
}
```

```java
// FilaPrioridadeFefo.java:243-249
private boolean vemAntes(Bolsa a, Bolsa b) {
    int comparacaoDeValidade = a.getDataValidade().compareTo(b.getDataValidade());
    if (comparacaoDeValidade != 0) {
        return comparacaoDeValidade < 0;
    }
    return a.getCodigoRastreio().compareTo(b.getCodigoRastreio()) < 0;
}
```

Tradução direta, com uma diferença de tipo: em C a data é texto no formato ISO
`"AAAA-MM-DD"`, e `strcmp` responde a mesma pergunta que `compareTo` sobre `LocalDate`,
porque texto ISO ordena na mesma ordem do calendário. Escolher outro formato — `DD/MM/AAAA`,
por exemplo — quebraria a comparação em C sem quebrar nada em Java.

O Java **não** usa `Comparator` de propósito. Um `Comparator.comparing(...).thenComparing(...)`
seria mais curto, mas esconderia a comparação campo a campo que a versão em C faz, e o mapa
desta seção deixaria de ser linha a linha.

### 3.4 Alocação do nó

```c
/* fila_fefo.c:43-48 */
NoFefo *novo = (NoFefo *) malloc(sizeof(NoFefo));
if (novo == NULL) {
    return RV_ERRO;
}
novo->bolsa = bolsa;
novo->proximo = NULL;
```

```java
// FilaPrioridadeFefo.java:113
No novo = new No(bolsa);
```

Aqui está a **única divergência real** entre as duas versões, a mesma da Unidade 1: em C o
`malloc` pode falhar e o código precisa tratar, devolvendo `RV_ERRO`; em Java o `new` ou
devolve o objeto ou lança `OutOfMemoryError`, que não se trata linha a linha. Pelo mesmo
motivo, não há `free` no lado Java — o coletor de lixo recolhe o nó quando ninguém mais o
referencia.

### 3.5 Caso 1 — fila vazia

```c
/* fila_fefo.c:51-52 */
/* CASO 1 - fila vazia: o no novo e o unico, e tambem o primeiro. */
fila->primeiro = novo;
```

```java
// FilaPrioridadeFefo.java:116-117
// CASO 1 - fila vazia: o no novo e o unico, e tambem o primeiro.
primeiro = novo;
```

`fila->primeiro` vira o campo `primeiro`. O nó já nasceu com `proximo` nulo, então não há
mais nada a ligar.

### 3.6 Caso 2 — a nova bolsa assume o início

```c
/* fila_fefo.c:61-62 */
novo->proximo = fila->primeiro;
fila->primeiro = novo;
```

```java
// FilaPrioridadeFefo.java:124-125
novo.proximo = primeiro;
primeiro = novo;
```

**A ordem das duas atribuições não é estética.** Primeiro o nó novo passa a apontar para o
antigo início; só então a referência da fila avança. Invertendo, `primeiro` apontaria para o
nó novo antes de alguém apontar para o antigo primeiro, e **a lista inteira ficaria
inalcançável**. Em C isso é vazamento de memória; em Java, o coletor recolheria a fila viva.
O erro é o mesmo, a consequência tem nomes diferentes — exatamente como na inserção da lista
da Unidade 1.

### 3.7 Caso 3 — percurso e encaixe

```c
/* fila_fefo.c:75-89 */
NoFefo *anterior = fila->primeiro;
NoFefo *atual = fila->primeiro->proximo;

while (atual != NULL && vem_antes(&atual->bolsa, &bolsa)) {
    anterior = atual;
    atual = atual->proximo;
}

novo->proximo = atual;
anterior->proximo = novo;
```

```java
// FilaPrioridadeFefo.java:136-148
No anterior = primeiro;
No atual = primeiro.proximo;

while (atual != null && vemAntes(atual.bolsa, bolsa)) {
    anterior = atual;
    atual = atual.proximo;
}

novo.proximo = atual;
anterior.proximo = novo;
```

Tradução linha a linha, sem divergência. Os **dois ponteiros** existem pelo mesmo motivo nas
duas versões: em lista simplesmente encadeada não há como voltar, então religar um nó exige
ter em mãos quem aponta para ele.

As duas últimas linhas resolvem **meio e fim de uma vez**: quando `atual` é `NULL`/`null`, o
nó entra no final. Nenhuma das versões precisa de um ramo extra para isso.

### 3.8 Retirada

```c
/* fila_fefo.c:108-123 */
NoFefo *removido = fila->primeiro;

if (saida != NULL) {
    *saida = removido->bolsa;     /* copia antes do free */
}

fila->primeiro = removido->proximo;

free(removido);
fila->tamanho--;
```

```java
// FilaPrioridadeFefo.java:179-186
No removido = primeiro;
primeiro = removido.proximo;

removido.proximo = null;

tamanho--;
return removido.bolsa;
```

Duas divergências de idioma, nenhuma de lógica:

- **Como o valor sai.** O C usa parâmetro de saída, porque o nó deixa de existir no `free` e
  devolver um ponteiro para ele entregaria endereço inválido. O Java devolve a referência
  direto, que sobrevive ao descarte do nó.
- **A limpeza.** O `free(removido)` do C não tem equivalente; no lugar dele o Java solta
  `removido.proximo`, para que um nó retirado não segure a fila viva caso alguém guarde uma
  referência a ele.

Em comum, e esse é o ponto da técnica: retirar é **apenas avançar a referência do início**.
Não há reorganização, porque a lista já está ordenada.

### 3.9 Resumo das diferenças

| Aspecto | C | Java | É tradução ou divergência? |
|---|---|---|---|
| Ligação entre nós | `struct NoFefo *proximo` | `No proximo` | Tradução direta |
| Fim da cadeia | `NULL` | `null` | Tradução direta |
| Três casos da inserção | idênticos | idênticos | Tradução direta |
| Ordem das atribuições no encaixe | idêntica | idêntica | Tradução direta |
| Percurso com `anterior`/`atual` | idêntico | idêntico | Tradução direta |
| Comparação de validade | `strcmp` sobre texto ISO | `compareTo` sobre `LocalDate` | Divergência de tipagem |
| Alocação | `malloc` + checagem | `new` | **Divergência real** |
| Devolução de memória | `free(no)` explícito | coletor de lixo | **Divergência real** |
| Armazenamento do dado | cópia da struct | referência ao objeto | Divergência de tipagem |
| Valor retirado | parâmetro de saída | valor de retorno | Divergência de idioma |
| Fila vazia | `NULL` / `RV_ERRO` | `null` | Tradução direta |
| Entrada inválida | `RV_ERRO` | `IllegalArgumentException` | Divergência de idioma |

## 4. Heap (U1) × inserção ordenada (U2)

A versão da Unidade 1 era um `PriorityQueue` com `Comparator`:

```java
private final PriorityQueue<Bolsa> fila = new PriorityQueue<>(
        Comparator.comparing(Bolsa::getDataValidade)
                .thenComparing(Bolsa::getCodigoRastreio)
);
```

| Operação | Heap (U1) | Inserção ordenada (U2) |
|---|---|---|
| `adicionar` | O(log n) | **O(n)** |
| `consultarProxima` | O(1) | O(1) |
| `retirarProxima` | O(log n) | **O(1)** |
| `tamanho` / `estaVazia` | O(1) | O(1) |
| `descartarVencidas` | não existia — exigiria varrer a árvore | **O(k)** para k vencidas |

**Por que a troca é aceitável.** A fila guarda as bolsas de **uma combinação de grupo
sanguíneo e hemocomponente**, não o estoque inteiro: são poucas por unidade. O O(n) da
inserção é barato em valor absoluto. Em troca, `consultarProxima` e `retirarProxima` — que é
o que a alocação executa a cada requisição — ficaram O(1). A estrutura ficou mais cara onde o
estoque é **abastecido** e mais barata onde é **consumido**.

**Por que a ordem de saída não muda.** O critério é o mesmo nas duas versões: validade
crescente, depois código de rastreio. O heap e a lista ordenada apenas *mantêm* esse critério
de formas diferentes — um por propriedade da árvore, outro por invariante da lista. Para a
mesma entrada, a sequência de saída é idêntica. É isso que torna a troca verificável: se a
ordem mudasse, algum dos 29 testes dos consumidores teria quebrado.

## 5. Sem estoque e bolsas vencidas

### 5.1 Estrutura vazia devolve nulo, não exceção

`consultarProxima()` e `retirarProxima()` devolvem `null` quando a fila está vazia. Estoque
sem bolsa é **situação normal da operação**, não erro de programa: lançar obrigaria todo
chamador a se defender com `try/catch` de algo previsível.

Quem converte isso em resposta de domínio é a `SelecaoFefo`, que embrulha o nulo em
`Optional.ofNullable(...)` e devolve `Optional.empty()`. O contrato já era esse antes da
troca — mudá-lo quebraria o consumidor.

Na versão em C o equivalente é devolver `NULL` em `fila_consultar_proxima` e `RV_ERRO` em
`fila_retirar_proxima`, seguindo a convenção de retorno da Unidade 1.

### 5.2 As vencidas estão todas no início

```c
/* fila_fefo.c:141-142 */
while (fila->primeiro != NULL
       && esta_vencida(&fila->primeiro->bolsa, data_referencia)) {
```

```java
// FilaPrioridadeFefo.java:215
while (primeiro != null && primeiro.bolsa.estaVencida(dataReferencia)) {
```

A lista é ordenada por validade crescente, então uma bolsa vencida **não pode** estar depois
de uma válida. O descarte remove do início e **para na primeira válida**: toda bolsa depois
dela vence ainda mais tarde, e não há o que procurar no resto. Daí o O(k).

No heap isso não seria possível: a ordem só vale para a raiz, e descobrir quais venceram
exigiria varrer a árvore inteira. É um ganho que veio junto com a técnica.

### 5.3 A borda: a bolsa que vence hoje ainda vale

Quem decide o vencimento é
[`Bolsa.estaVencida`](../src/main/java/com/rotavital/dominio/Bolsa.java):

```java
return dataReferencia.isAfter(dataValidade);
```

`isAfter`, e não `isAfter || isEqual`. A bolsa cuja validade é **exatamente** a data de
referência ainda é válida — ela só vence no dia seguinte. É regra de negócio, não detalhe de
implementação, e está fixada no teste `bolsaQueVenceNaDataDeReferenciaEhMantida`.

A versão em C reproduz a mesma regra com `strcmp(data_referencia, bolsa->data_validade) > 0`
(`fila_fefo.c:25`): estritamente maior, nunca maior ou igual.

## 6. Impacto nos consumidores: nenhum

A API pública não mudou — `adicionar`, `consultarProxima`, `retirarProxima`, `estaVazia` e
`tamanho` têm as mesmas assinaturas e o mesmo contrato. `descartarVencidas` é acréscimo.

```
$ git diff --stat main...feat/fefo-insercao-ordenada-pi3-155 -- src/main/java/com/rotavital/alocacao
(saída vazia)
```

`SelecaoFefo` e `ServicoAlocacaoRota` não tiveram uma linha alterada, e os testes deles
passam sem ajuste:

| Teste | Quantidade |
|---|---:|
| `SelecaoFefoTest` | 9 |
| `ServicoAlocacaoRotaTest` | 12 |
| `CriteriosAceiteAlocacaoTest` | 6 |
| `PropriedadesAlocacaoTest` | 1 |
| `DemoPontaAPontaTest` | 1 |
| **Total** | **29** |

Essa é a prova de que a troca de técnica é transparente: se os consumidores precisassem ser
ajustados, não haveria como separar o que mudou por causa da técnica do que mudou por causa
da interface.

## 7. Testes

`FilaPrioridadeFefoTest` tem 20 testes, agrupados pelos critérios da PI3-95:

**Ordenação** — `insercaoNoInicio`, `insercaoNoMeio`, `insercaoNoFim`,
`entradaEmbaralhadaSaiOrdenada` (oito bolsas embaralhadas com semente fixa),
`devolveSempreABolsaQueVencePrimeiro`.

**Empate de validade** — `empateMaiorCodigoInseridoPrimeiro`,
`empateMenorCodigoInseridoPrimeiro` (as duas ordens de inserção),
`empateDeValidadeEhResolvidoDeFormaDeterministicaPeloCodigo`.

**Estoque vazio** — `filaVaziaNaoTemProximaBolsa`, `filaNovaDevolveNull`,
`depoisDeEsvaziarVoltaAoEstadoVazio`.

**Vencidas** — `descartaSoAsVencidasDoInicio`, `bolsaQueVenceNaDataDeReferenciaEhMantida`,
`filaTodaVencidaFicaVazia`, `filaSemVencidasNaoMuda`, `listaImutavelEDataNulaLanca`.

**Contrato** — `elementoUnicoEhRetornado`, `tamanhoAcompanhaAsOperacoes`, `bolsaNulaLanca`.

**Critério da task mãe** — `proximaEhSempreADeMenorValidade`: após *cada* inserção, em vinte
ordens de entrada embaralhadas com semente fixa, `consultarProxima` é comparada com o mínimo
recalculado de forma independente da estrutura.

Todas as datas dos testes são fixas — nenhum `LocalDate.now()`.

A versão em C tem a demonstração equivalente em
[`aed/unidade2/c/main.c`](../aed/unidade2/c/main.c), com as seis etapas: inserção fora de
ordem, empate, consulta sem retirar, descarte das vencidas, retirada até esvaziar e
liberação da memória.

## 8. Rastreabilidade

**PI3-95 — W10, Priorização FEFO por inserção ordenada em Java (AED)**, dividida em duas
subtarefas:

| Subtarefa | Entrega | PR |
|---|---|---|
| **PI3-155** | `FilaPrioridadeFefo` reescrita com inserção ordenada; `FilaPrioridadeFefoTest` de 4 para 20 testes | #60 (base `main`) |
| **PI3-156** | `aed/unidade2/c/` e este documento | base da PI3-155 |

Arquivos:

- [`FilaPrioridadeFefo.java`](../src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java) — PI3-155
- [`FilaPrioridadeFefoTest.java`](../src/test/java/com/rotavital/estruturas/FilaPrioridadeFefoTest.java) — PI3-155
- [`aed/unidade2/c/fila_fefo.{h,c}`](../aed/unidade2/c/) e `main.c` — PI3-156
- Evidências: [`evidencias-pi3-155.md`](evidencias-pi3-155.md) e [`evidencias-pi3-156.md`](evidencias-pi3-156.md)

**Critérios de aceite da PI3-95:**

1. *A bolsa com validade mais próxima é sempre a primeira alocada* — PI3-155, teste
   `proximaEhSempreADeMenorValidade`.
2. *Testes cobrindo ordenação, empate de validade e estoque vazio* — PI3-155, seção 7 deste
   documento.
3. *Inserção ordenada com lógica equivalente à versão em C/C++* — PI3-156, seção 3.
4. *Tratar "sem estoque" e bolsas vencidas* — PI3-155 (`descartarVencidas`, nulo em fila
   vazia), seção 5 deste documento.
