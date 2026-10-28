# Tradução comentada C/C++ → Java — Unidade 2 de AED

**Projeto:** Rota Vital — rede de distribuição de hemocomponentes
**Entrega:** Unidade 2 — algoritmos de roteirização, priorização, indexação e compatibilidade
**Repositório:** `rsc3-pixel/Projeto3-2026.2`

Este documento explica, algoritmo por algoritmo, **por que a versão Java corresponde à
lógica da versão em C/C++**. Cada afirmação aponta para o código efetivamente escrito,
nos dois lados. O equivalente da Unidade 1 é [`U1-traducao-c-java.md`](U1-traducao-c-java.md),
de onde este documento herda a estrutura.

---

## 0. Cobertura de linguagens — leia antes

A cobertura **não é uniforme entre os quatro algoritmos**, e isso é dito aqui para não
ser descoberto no meio da leitura:

| Algoritmo | C | C++ | Java |
|---|:---:|:---:|:---:|
| **1. Roteirização por vizinho mais próximo** | — | ✅ | ✅ |
| **2. FEFO por inserção ordenada** | ✅ | ✅ | ✅ |
| **3. Índice de estoque por hash** | — | ✅ | ✅ |
| **4. Compatibilidade ABO/Rh por matriz** | — | ✅ | ✅ |

O critério da disciplina pede "lógica equivalente à versão em **C/C++**", e C++ atende
em todos os quatro. O FEFO tem as três versões porque foi o primeiro algoritmo portado,
na W10, quando a contraparte ainda era escrita em C; os outros três foram portados
diretamente para C++ na W12. A seção 2 aproveita essa cobertura tripla para mostrar a
**progressão** entre as três linguagens, que é o caso mais didático da entrega.

**Como executar as duas versões:**

```bash
# C++ — as quatro demonstrações num único executável
cd aed/unidade2/cpp && make && ./rota_vital_u2

# C — apenas o FEFO
cd aed/unidade2/c && make && ./rota_vital_u2

# Java — os testes dos quatro algoritmos
./mvnw test
```

---

## 1. Roteirização por vizinho mais próximo

### 1.1 Por que este algoritmo, neste domínio

Um veículo sai de um hemocentro carregado e precisa atender vários hospitais na mesma
viagem. A pergunta não é "qual o caminho mais curto entre dois pontos" — essa a Unidade 1
já resolveu com Dijkstra — e sim **em que ordem visitar**.

A ordem ótima é o problema do caixeiro viajante, de custo exponencial. O vizinho mais
próximo entrega uma ordem boa em tempo polinomial, que é o que a operação precisa.

**A decisão central** está em [`W10-roteirizacao-vizinho-mais-proximo.md`](W10-roteirizacao-vizinho-mais-proximo.md):
a distância de cada passo vem do **caminho mínimo**, não da aresta direta. A malha é
esparsa de propósito — cada unidade liga apenas às 4 mais próximas — então entre dois
hospitais quaisquer muitas vezes não existe aresta direta. Medir por aresta deixaria de
fora pontos alcançáveis por duas ou três pernas.

### 1.2 O laço guloso, lado a lado

**C++** — `aed/unidade2/cpp/vizinho_mais_proximo.cpp`:

```cpp
while (!pendentes.vazia()) {
    const std::vector<DistanciaAte> distancias = dijkstra_.calcularDistancias(atual);

    std::string escolhido;
    double menorDistancia = 0.0;

    pendentes.paraCada([&](const std::string& pendente) {
        const DistanciaAte* entrada = nullptr;
        for (const DistanciaAte& d : distancias) {
            if (d.vertice == pendente) { entrada = &d; break; }
        }
        if (entrada == nullptr) return;              // ausência = sem caminho
        if (escolhido.empty() || entrada->distancia < menorDistancia) {
            escolhido = pendente;
            menorDistancia = entrada->distancia;
        }
    });

    if (escolhido.empty()) break;                    // nada mais alcançável

    resultado.ordemDeVisita.push_back(escolhido);
    pendentes.remover(escolhido);
    atual = escolhido;
}
```

**Java** — `src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java`:

```java
while (!pendentes.isEmpty()) {
    Map<T, Double> distancias = dijkstra.calcularDistancias(atual);

    T escolhido = maisProximoEntre(pendentes, distancias);

    if (escolhido == null) {
        break;
    }

    ResultadoRota<T> trecho = dijkstra.calcularRota(atual, escolhido);
    custoTotal += trecho.custoTotal();
    ordemDeVisita.add(escolhido);
    pendentes.remove(escolhido);
    atual = escolhido;
}
```

**Por que são o mesmo algoritmo.** A estrutura é idêntica: um Dijkstra por passo mede
todos os pendentes de uma vez, escolhe-se o menor, remove-se dos pendentes e avança.
Três detalhes se correspondem linha a linha:

1. **Ausência significa "sem caminho".** Nos dois lados, pendente que não aparece na
   tabela de distâncias é ignorado — não existe valor sentinela de infinito.
2. **O `break` garante o término.** Quando nada é alcançável, insistir reexecutaria o
   mesmo cálculo indefinidamente, porque nada mudaria entre as voltas.
3. **O desempate é a comparação estrita.** `<` e não `<=`, percorrendo na ordem de
   entrada: o empate preserva o primeiro informado.

### 1.3 O grafo: onde a tradução mais diverge

**Java** — `Grafo.java`:

```java
private final Map<T, Map<T, Double>> adjacencias = new HashMap<>();
```

**C++** — `grafo.hpp`:

```cpp
struct NoAresta {
    std::string destino;
    double      peso    = 0.0;
    NoAresta*   proxima = nullptr;
};

struct NoVertice {
    std::string id;
    NoAresta*   primeiraAresta = nullptr;
    NoVertice*  proximo        = nullptr;
};

NoVertice* primeiro_ = nullptr;
NoVertice* ultimo_   = nullptr;   // preserva a ordem de inserção em O(1)
```

Uma linha de Java contra duas estruturas e três ponteiros em C++. **Não é a mesma
implementação, e isso é deliberado**: a Unidade 2 pede a estrutura feita à mão, e
`std::map` apagaria o encadeamento, que é o objeto do exercício.

A consequência é de custo, não de resultado: o Java acha um vértice em O(1) pelo hash;
o C++ percorre a cadeia, O(V).

### 1.4 A divergência mais relevante da entrega: O(V²) contra O((V+E) log V)

Esta é a divergência que mais importa, e está registrada no cabeçalho de
`aed/unidade2/cpp/dijkstra.hpp`.

**Java** — escolhe o próximo vértice com um heap binário:

```java
PriorityQueue<Candidato<T>> fila =
        new PriorityQueue<>(Comparator.comparingDouble(Candidato::distancia));
```

**C++** — escolhe por varredura linear:

```cpp
std::string menorAberto() const {
    const No* escolhido = nullptr;
    for (const No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
        if (atual->fechado) continue;
        if (escolhido == nullptr || atual->distancia < escolhido->distancia) {
            escolhido = atual;
        }
    }
    return escolhido == nullptr ? std::string{} : escolhido->vertice;
}
```

| | Java | C++ |
|---|---|---|
| Escolher o próximo vértice | O(log V), heap | **O(V)**, varredura |
| Consultar a tabela | O(1), `HashMap` | **O(V)**, cadeia |
| **Total** | **O((V+E) log V)** | **O(V²)** |

**Por que a divergência é aceitável aqui.** Dois motivos.

O primeiro é de escala. Com V = 12 e E = 64, V² dá 144 contra cerca de 270 para
(V+E)·log V — nesse tamanho a versão assintoticamente "pior" chega a fazer **menos
trabalho**. A diferença só apareceria numa rede muito maior.

O segundo é o objetivo do exercício. Trocar as cadeias por `std::map` e
`std::priority_queue` recuperaria o O((V+E) log V) em poucas linhas, mas apagaria o
encadeamento e o religamento de ponteiros, que é o que a Unidade 2 quer mostrar.

> **Atenção ao ler a documentação do projeto.** `Dijkstra.java`, a seção 6 do
> [`W02-escopo-grafo-rede-distribuicao.md`](W02-escopo-grafo-rede-distribuicao.md) e o
> [`premissas-malha-rede.md`](premissas-malha-rede.md) afirmam **O((V+E) log V)**. Eles
> falam da versão Java, que é a que roda em produção. A versão C++ é didática e assume
> O(V²) conscientemente. Sem esta nota, a entrega teria duas afirmações contraditórias
> sobre o mesmo algoritmo.

### 1.5 Evidência de equivalência

A demonstração em C++ reproduz **exatamente** o roteiro que a versão Java produz sobre a
malha real das 12 unidades:

```
ordem de visita: HC04 HC05 HC06 HC09 HC12 HC11 HC10 HC07
trechos: 4.2, 3.1, 22.0, 21.3, 16.1, 35.4, 3.4, 68.1 minutos
custo total: 173.6 minutos
```

Além disso, a **etapa 10** do executável compara a malha construída em C++ com os valores
documentados em [`premissas-malha-rede.md`](premissas-malha-rede.md) e imprime **7 linhas
CONFERE**:

| Verificação | Esperado | Obtido |
|---|---|---|
| total de arestas dirigidas | 64 | 64 |
| HC01 → HC05, caminho mínimo | 5,9 min | 5,9 |
| HC01 → HC05, caminho | HC01 → HC02 → HC05 | idem |
| HC01 → HC05, aresta direta | 6,0 min | 6,0 |
| HC01 → HC12, caminho mínimo | 50,1 min | 50,1 |
| HC01 → HC12, caminho | HC01 → HC04 → HC09 → HC12 | idem |
| HC01 → HC11, caminho mínimo | 44,3 min | 44,3 |

O par HC01 → HC05 é o mais informativo: o caminho mínimo custa **5,9** passando por HC02,
enquanto a **aresta direta custa 6,0**. É a prova, em dado real, de que medir por aresta
direta daria resultado diferente.

---

## 2. FEFO por inserção ordenada — as três versões

### 2.1 Por que esta técnica, neste domínio

FEFO existe para reduzir descarte por vencimento: consumindo primeiro o que vence antes,
sobra no estoque o que tem mais prazo.

A Unidade 1 resolvia isso com fila de prioridade sobre heap binário. A Unidade 2 pede
**inserção ordenada**: a lista é mantida ordenada no momento em que cada bolsa entra, sem
passo de ordenação depois. A troca e sua justificativa estão em
[`W10-fefo-insercao-ordenada.md`](W10-fefo-insercao-ordenada.md).

### 2.2 O critério de ordem, nas três linguagens

**C** — `aed/unidade2/c/fila_fefo.c`:

```c
static int vem_antes(const Bolsa *a, const Bolsa *b) {
    int comparacao_de_validade = strcmp(a->data_validade, b->data_validade);
    if (comparacao_de_validade != 0) {
        return comparacao_de_validade < 0;
    }
    return strcmp(a->codigo_rastreio, b->codigo_rastreio) < 0;
}
```

**Java** — `FilaPrioridadeFefo.java`:

```java
private boolean vemAntes(Bolsa a, Bolsa b) {
    int comparacaoDeValidade = a.getDataValidade().compareTo(b.getDataValidade());
    if (comparacaoDeValidade != 0) {
        return comparacaoDeValidade < 0;
    }
    return a.getCodigoRastreio().compareTo(b.getCodigoRastreio()) < 0;
}
```

Tradução direta, com uma diferença de tipo: em C a data é texto ISO `"AAAA-MM-DD"`, e
`strcmp` responde a mesma pergunta que `compareTo` sobre `LocalDate`, **porque texto ISO
ordena na mesma ordem do calendário**. Escolher `DD/MM/AAAA` quebraria a comparação em C
sem quebrar nada em Java.

O Java **não** usa `Comparator` de propósito: seria mais curto, mas esconderia a
comparação campo a campo que a versão em C faz.

### 2.3 A inserção, nas três versões

**C** — struct manipulada por funções soltas:

```c
if (fila->primeiro == NULL) {
    fila->primeiro = novo;                      /* CASO 1 */
} else if (vem_antes(&bolsa, &fila->primeiro->bolsa)) {
    novo->proximo = fila->primeiro;             /* CASO 2 */
    fila->primeiro = novo;
} else {
    NoFefo *anterior = fila->primeiro;          /* CASO 3 */
    NoFefo *atual = fila->primeiro->proximo;
    while (atual != NULL && vem_antes(&atual->bolsa, &bolsa)) {
        anterior = atual;
        atual = atual->proximo;
    }
    novo->proximo = atual;
    anterior->proximo = novo;
}
fila->tamanho++;
```

**C++** — método de classe, com os ponteiros virando membros:

```cpp
if (primeiro_ == nullptr) {
    primeiro_ = novo;                           // CASO 1
} else if (vemAntes(bolsa, primeiro_->bolsa)) {
    novo->proximo = primeiro_;                  // CASO 2
    primeiro_ = novo;
} else {
    No* anterior = primeiro_;                   // CASO 3
    No* atual = primeiro_->proximo;
    while (atual != nullptr && vemAntes(atual->bolsa, bolsa)) {
        anterior = atual;
        atual = atual->proximo;
    }
    novo->proximo = atual;
    anterior->proximo = novo;
}
tamanho_++;
```

**Java** — idêntico, com referências no lugar de ponteiros:

```java
if (primeiro == null) {
    primeiro = novo;
} else if (vemAntes(bolsa, primeiro.bolsa)) {
    novo.proximo = primeiro;
    primeiro = novo;
} else {
    No anterior = primeiro;
    No atual = primeiro.proximo;
    while (atual != null && vemAntes(atual.bolsa, bolsa)) {
        anterior = atual;
        atual = atual.proximo;
    }
    novo.proximo = atual;
    anterior.proximo = novo;
}
tamanho++;
```

**As três são a mesma lógica, linha a linha.** Os três casos, a ordem das atribuições e
o percurso com dois ponteiros são idênticos. O que muda é só a sintaxe de acesso:
`fila->primeiro`, `primeiro_`, `primeiro`.

**Um detalhe que sobrevive às três versões, e não é acidente:** a condição do laço usa
`vemAntes(atual, nova)` e **não** a negação de `vemAntes(nova, atual)`. A diferença
aparece nos empates: assim o percurso avança também sobre os iguais, e a bolsa nova acaba
**depois** deles. É o que torna a inserção estável. É o tipo de linha que alguém
"simplifica" e quebra sem perceber — por isso está comentada nos três arquivos.

### 2.4 A progressão: de funções soltas a RAII a coletor de lixo

Esta é a parte mais didática da entrega, porque o mesmo algoritmo aparece sob três
regimes de gerenciamento de memória.

**C — quem usa precisa lembrar de destruir:**

```c
FilaFefo *fila = fila_criar();
if (fila == NULL) { /* tratar falta de memória */ }
/* ... usar ... */
fila_destruir(fila);          /* esquecer aqui = vazamento */
```

**C++ — o destrutor faz sozinho:**

```cpp
FilaFefo::~FilaFefo() {
    No* atual = primeiro_;
    while (atual != nullptr) {
        No* proximo = atual->proximo;   // guarda ANTES do delete
        delete atual;
        atual = proximo;
    }
    primeiro_ = nullptr;
    tamanho_  = 0;
}
```

Declarar `FilaFefo fila;` basta: o construtor faz o papel de `fila_criar` e o destrutor o
de `fila_destruir`, **chamado sozinho ao sair do escopo, mesmo que uma exceção interrompa
o caminho**. Esquecer de liberar deixou de ser possível. Isso é RAII.

**Java — não existe esse método:**

Perdida a referência à fila, o coletor de lixo recolhe a cadeia inteira. Não há destrutor
a escrever nem `delete` a chamar.

| Aspecto | C | C++ | Java |
|---|---|---|---|
| Criar | `fila_criar()`, pode devolver `NULL` | construtor, não falha | construtor |
| Destruir | `fila_destruir()`, **manual** | destrutor, **automático** | coletor de lixo |
| Risco de vazamento | real, se esquecer | nenhum | nenhum |
| Cópia acidental | possível, sem aviso | **proibida** por `= delete` | referência, sem cópia |

A proibição de cópia em C++ merece nota: sem ela, duas filas apontariam para os mesmos
nós e cada destrutor tentaria liberar os mesmos endereços. O `= delete` faz o compilador
recusar, em vez de gerar código quebrado em silêncio.

### 2.5 Como o valor retirado volta ao chamador

Três idiomas para o mesmo problema — "retirar da fila, que pode estar vazia":

```c
/* C: parâmetro de saída + código de retorno */
Bolsa saida;
if (fila_retirar_proxima(fila, &saida) == RV_OK) { /* usar saida */ }
```

```cpp
// C++: o valor é o próprio retorno
if (auto retirada = fila.retirarProxima()) { /* usar *retirada */ }
```

```java
// Java: Optional
fila.retirarProxima().ifPresent(bolsa -> { /* usar bolsa */ });
```

Em C, o valor precisa ser copiado **antes** do `free`, porque o nó deixa de existir e
devolver um ponteiro entregaria endereço inválido. Em C++ e Java o caso vazio fica
explícito no tipo: quem chama não consegue esquecer de testar.

---

## 3. Índice de estoque por hash

### 3.1 Por que esta estrutura, neste domínio

Achar "as bolsas O− de concentrado de hemácias" percorrendo o estoque inteiro é O(n) por
consulta — e a alocação faz essa pergunta a cada requisição. Com chave composta por grupo
sanguíneo e tipo de hemocomponente, a consulta cai direto no balde.

### 3.2 O hash perfeito, e por que ele existe

**Java** — `IndiceEstoque.java`:

```java
private static final int CAPACIDADE = 64;
private final Map<ChaveEstoque, List<Bolsa>> indice = new HashMap<>(CAPACIDADE);

static int hashDaChave(GrupoSanguineo grupoSanguineo, TipoHemocomponente tipo) {
    return grupoSanguineo.ordinal() * TipoHemocomponente.values().length + tipo.ordinal();
}
```

**C++** — `indice_estoque.cpp`:

```cpp
int IndiceEstoque::hashDaChave(GrupoSanguineo grupo, TipoHemocomponente tipo) {
    return static_cast<int>(grupo) * TOTAL_TIPOS + static_cast<int>(tipo);
}
```

A fórmula é a mesma: aritmética posicional, em que o grupo escolhe o bloco de 4 posições
e o tipo escolhe a posição dentro do bloco. As 32 combinações recebem valores distintos
de 0 a 31.

**Por que a fórmula é essa, e não `Objects.hash`.** A história está em
[`W10-indice-estoque-hash.md`](W10-indice-estoque-hash.md). Antes da PI3-132, a chave
usava `Objects.hash(grupo, tipo)`, que combina o `hashCode` dos enums — o de **identidade**
herdado de `Object`, que a especificação não garante entre execuções. Medindo as 32
combinações numa tabela de 64 baldes, o número de colisões **variou de 6 a 31** conforme o
modo de hash de identidade da HotSpot; no modo que devolve hash constante, as 32 chaves
caem no mesmo balde. Eram colisões inofensivas — cadeias curtas — mas o projeto **não
controlava quantas seriam**. O hash por `ordinal` trocou "raro por sorte" por "zero por
construção".

### 3.3 Onde as duas versões realmente divergem

| | Java | C++ |
|---|---|---|
| Tabela | `HashMap`, com dispersão e baldes prontos | vetor `No**` alocado com `new[]` |
| Dispersão | `(n-1) & (h ^ (h >>> 16))`, da biblioteca | o próprio `hashDaChave`, escrito |
| Capacidade | 64, porque o `HashMap` dobra acima de 75% | **32**, porque não há redimensionamento |
| Encadeamento | existe para colisão de chave | guarda as **bolsas** da combinação |

**A capacidade diferente não é inconsistência.** No Java, 64 existe porque o `HashMap`
dobra a tabela ao passar de 75% de ocupação, e 32 chaves em 64 baldes ficam abaixo desse
limite. Em C++ não há redimensionamento nenhum, então 32 baldes para 32 chaves bastam. A
propriedade — zero colisão — é a mesma.

**Sobre o encadeamento, uma distinção que vale fixar:** várias bolsas da mesma combinação
ficam na mesma cadeia. **Isso não é colisão de hash** — é o agrupamento desejado, e é a
razão de o índice existir. Colisão seria duas *chaves* diferentes disputando o mesmo
balde, e aqui isso não acontece.

### 3.4 Evidência de equivalência

A **etapa 11** do executável indexa uma bolsa em cada uma das 32 combinações e imprime o
balde de todas. Saem em sequência, de 0 a 31, sem repetição:

```
A+   CONCENTRADO_HEMACIAS       0        O-   CONCENTRADO_HEMACIAS      28
A+   PLASMA_FRESCO_CONGELADO    1        O-   PLASMA_FRESCO_CONGELADO   29
A+   CONCENTRADO_PLAQUETAS      2        O-   CONCENTRADO_PLAQUETAS     30
A+   CRIOPRECIPITADO            3        O-   CRIOPRECIPITADO           31

CONFERE  as 32 chaves ocuparam 32 baldes distintos (repetidos: 0)
```

A mesma etapa demonstra o agrupamento: somando uma segunda bolsa O−, o balde 28 passa a
ter 2 bolsas.

---

## 4. Compatibilidade ABO/Rh por matriz

### 4.1 Por que uma matriz, neste domínio

Transfundir o grupo errado mata. A regra é uma tabela fechada: 8 grupos × 8 grupos, por
tipo de hemocomponente. Consulta direta é a estrutura certa — não há cálculo a fazer, há
um fato a consultar.

> **A compatibilidade aqui é didática e não substitui protocolo clínico.** Na prática
> entram prova cruzada e pesquisa de anticorpos irregulares, que este projeto não modela.

**Duas propriedades que a implementação precisa preservar:**

1. **A relação não é simétrica.** O− doa para A+, mas A+ não doa para O−. Inverter os
   argumentos é a transfusão que mata — por isso os parâmetros se chamam `doador` e
   `receptor`, nunca `a` e `b`.
2. **O plasma inverte a regra.** Hemácias carregam antígenos; plasma carrega anticorpos.
   Quem doa hemácia para todos (O−) tem plasma que só serve para receptores O; AB é o
   doador universal de plasma. Ignorar essa inversão é o erro mais comum ao modelar
   compatibilidade, porque a intuição construída com hemácias leva para o lado errado.

### 4.2 As duas tabelas

**Java** — `MatrizCompatibilidade.java`, `EnumMap` de receptor para `EnumSet`:

```java
HEMACIAS.put(A_POS,  EnumSet.of(O_NEG, O_POS, A_NEG, A_POS));
HEMACIAS.put(O_NEG,  EnumSet.of(O_NEG));
HEMACIAS.put(AB_POS, EnumSet.allOf(GrupoSanguineo.class));

PLASMA.put(A_POS,  EnumSet.of(A_NEG, A_POS, AB_NEG, AB_POS));
PLASMA.put(O_NEG,  EnumSet.allOf(GrupoSanguineo.class));
PLASMA.put(AB_POS, EnumSet.of(AB_NEG, AB_POS));
```

**C++** — `matriz_compatibilidade.cpp`, vetores de bits:

```cpp
constexpr unsigned char HEMACIAS[TOTAL_GRUPOS] = {
    /* A_POS  */ O_NEG | O_POS | A_NEG | A_POS,
    /* A_NEG  */ O_NEG | A_NEG,
    ...
    /* O_NEG  */ O_NEG
};

constexpr unsigned char PLASMA[TOTAL_GRUPOS] = {
    /* A_POS  */ A_NEG | A_POS | AB_NEG | AB_POS,
    ...
    /* O_NEG  */ TODOS
};
```

**Por que vetor de bits corresponde ao `EnumSet`.** O `EnumSet` do Java **já é** um
conjunto de bits por baixo — um `long` com um bit por constante. A versão C++ escreve
isso explicitamente, com um `unsigned char` por linha. A consulta vira um deslocamento e
um E lógico:

```cpp
bool MatrizCompatibilidade::compativel(GrupoSanguineo doador,
                                       GrupoSanguineo receptor,
                                       TipoHemocomponente tipo) {
    if (!grupoValido(doador)) return false;
    return (doadoresEmBits(receptor, tipo) & bitDe(doador)) != 0;
}
```

O(1) nos dois lados, e as duas tabelas juntas ocupam 16 bytes em C++.

### 4.3 A ordem dos enums é contrato entre as duas versões

Os enums C++ foram declarados com valores explícitos, **na mesma ordem dos enums Java**:

```cpp
enum class GrupoSanguineo {
    A_POS = 0, A_NEG = 1, B_POS = 2, B_NEG = 3,
    AB_POS = 4, AB_NEG = 5, O_POS = 6, O_NEG = 7
};
```

O número não é detalhe de implementação: ele é usado como **posição de bit** nas tabelas
de compatibilidade e como **parte do hash** do índice de estoque. Mudar a ordem de um lado
sem mudar do outro quebraria a correspondência silenciosamente.

### 4.4 Argumento inválido não lança

Nos dois lados, grupo ou tipo fora da faixa devolve `false` em `compativel` e conjunto
vazio em `doadoresPara` — **nunca exceção**. A razão é de domínio, e está dita no javadoc
do Java: *ausência de dado não é autorização para transfundir*.

### 4.5 Evidência de equivalência

A **etapa 12** do executável percorre os 8 grupos nas duas tabelas e compara com uma
referência **extraída executando a versão Java**, não transcrita à mão. São **16 linhas
CONFERE**, mais 3 verificações de propriedade:

```
CONFERE  CONCENTRADO_HEMACIAS     recebe A+   de: A+ A- O+ O-
CONFERE  CONCENTRADO_HEMACIAS     recebe O-   de: O-
CONFERE  CONCENTRADO_HEMACIAS     recebe AB+  de: A+ A- B+ B- AB+ AB- O+ O-
CONFERE  PLASMA_FRESCO_CONGELADO  recebe A+   de: A+ A- AB+ AB-
CONFERE  PLASMA_FRESCO_CONGELADO  recebe O-   de: A+ A- B+ B- AB+ AB- O+ O-
...
CONFERE  inverter os argumentos muda a resposta
CONFERE  plasma de O- não serve para receptor A+
CONFERE  grupo inválido devolve conjunto vazio, sem exceção
```

Repare no contraste entre as linhas de `O-`: em hemácias recebe **só de O−**; em plasma
recebe **de todos**. É a inversão, visível em dado real.

---

## 5. Resumo: o que é tradução e o que é divergência

| Aspecto | C / C++ | Java | É tradução ou divergência? |
|---|---|---|---|
| Três casos da inserção ordenada | idênticos | idênticos | Tradução direta |
| Ordem das atribuições no religamento | idêntica | idêntica | Tradução direta |
| Percurso com `anterior`/`atual` | idêntico | idêntico | Tradução direta |
| Critério de ordem FEFO | validade, depois código | idem | Tradução direta |
| Laço guloso do vizinho mais próximo | idêntico | idêntico | Tradução direta |
| Desempate por comparação estrita | `<` | `<` | Tradução direta |
| Ausência = "sem caminho" | fora da tabela | fora do mapa | Tradução direta |
| Fórmula do hash perfeito | `ordinal × 4 + ordinal` | idem | Tradução direta |
| Tabelas de compatibilidade | mesmas 16 linhas | mesmas 16 linhas | Tradução direta |
| Não simetria e inversão do plasma | preservadas | preservadas | Tradução direta |
| Fim da cadeia | `NULL` / `nullptr` | `null` | Tradução direta |
| **Lista de adjacência** | cadeias de nós, O(V) | `HashMap`, O(1) | **Divergência de estrutura** |
| **Dijkstra** | varredura, **O(V²)** | heap, **O((V+E) log V)** | **Divergência de complexidade** |
| **Tabela hash** | vetor `new[]`, 32 baldes | `HashMap`, 64 | **Divergência de estrutura** |
| **Alocação** | `malloc` / `new` + checagem | `new`, sem checagem | **Divergência real** |
| **Liberação** | `free` / destrutor | coletor de lixo | **Divergência real** |
| **Criar e destruir** | C: manual; C++: RAII | coletor de lixo | **Divergência real** |
| Tipo do vértice/elemento | `std::string` fixo | genérico `<T>` | Divergência de idioma |
| Valor retirado | C: ponteiro de saída; C++: `std::optional` | `Optional` | Divergência de idioma |
| Entrada inválida | `RV_ERRO` / `Resultado::Erro` | `IllegalArgumentException` | Divergência de idioma |
| Cópia do objeto | proibida por `= delete` | referência | Divergência de idioma |

**As divergências reais são três**, e todas vêm do modelo de memória ou de escolha
deliberada de estrutura: alocação, liberação e as estruturas feitas à mão. A lógica dos
quatro algoritmos — que é o objeto desta unidade — é a mesma nos dois lados.

**Por que `std::string` fixo e não template.** A implementação mora nos arquivos `.cpp`;
uma classe template precisaria ficar inteira no cabeçalho para o compilador instanciar.
Como a rede identifica unidade por texto e a bolsa por código de rastreio, o tipo fixo não
custa nada e mantém a separação entre cabeçalho e implementação.

---

## 6. Resumo das evidências de equivalência

As três demonstrações produzem, juntas, **27 linhas CONFERE e nenhuma DIVERGE**:

| Evidência | Onde | Quantidade |
|---|---|---|
| Malha contra `premissas-malha-rede.md` | etapa 10 | 7 CONFERE |
| 32 chaves em 32 baldes distintos | etapa 11 | 1 CONFERE |
| Matriz contra a versão Java | etapa 12 | 16 + 3 CONFERE |

Dois pontos sobre o que essas conferências **de fato** garantem:

- A referência da matriz foi **gerada executando a versão Java**, não transcrita. Se as
  duas implementações divergirem, a demonstração acusa.
- Os pesos da malha em C++ são uma **fotografia** do que o `CarregadorMalha.java` calcula,
  e não um cálculo. Mudar `VELOCIDADE_MEDIA_KMH`, `FATOR_SINUOSIDADE`,
  `VIZINHOS_POR_UNIDADE` ou uma coordenada no Java **invalida** esses números — e é por
  isso que a etapa 10 existe: a desatualização passa a aparecer na execução, em vez de
  ficar errada em silêncio.

As duas foram testadas por mutação: adulterando um peso da malha, duas linhas passaram a
marcar DIVERGE; quebrando a inversão do plasma, outras duas. As conferências não são
decorativas.

---

## 7. Revisão conjunta AED + POO

**Data:** [PREENCHER]
**Participantes:** [PREENCHER]

### 7.1 O que foi verificado

| Algoritmo | Verificado | Resultado |
|---|---|---|
| Roteirização por vizinho mais próximo | [PREENCHER] | [PREENCHER] |
| FEFO por inserção ordenada | [PREENCHER] | [PREENCHER] |
| Índice de estoque por hash | [PREENCHER] | [PREENCHER] |
| Compatibilidade ABO/Rh | [PREENCHER] | [PREENCHER] |

### 7.2 Roteiro sugerido para a revisão

Para cada algoritmo, confirmar que:

1. os trechos citados neste documento **batem com o código atual** dos dois lados;
2. a justificativa de equivalência se sustenta ao ler os dois trechos lado a lado;
3. as divergências listadas na seção 5 estão todas nomeadas, nenhuma escondida;
4. as evidências da seção 6 reproduzem ao rodar `make && ./rota_vital_u2`.

Do lado de POO, confirmar que as classes Java continuam consumíveis pela aplicação:
sem dependência de framework nas estruturas, genéricas em `<T>` onde cabe, e devolvendo
coleções imutáveis.

### 7.3 Conclusões

[PREENCHER]

### 7.4 Pendências levantadas

[PREENCHER]
