# Tradução comentada C → Java — Unidade 1 de AED

**Projeto:** Rota Vital — rede de distribuição de hemocomponentes
**Entrega:** Unidade 1 — estruturas básicas de domínio
**Repositório:** `rsc3-pixel/Projeto3-2026.2`

Este documento explica, estrutura por estrutura, **por que a versão Java corresponde
à lógica da versão em C**. Cada afirmação aponta para o código efetivamente escrito,
nos dois lados.

---

## 1. Modelagem de domínio e escolha das estruturas

O sistema Rota Vital movimenta três coisas: bolsas de sangue em estoque, requisições
que chegam dos hospitais e o registro do que foi feito. Cada uma pede uma estrutura
diferente, e a escolha vem do comportamento do domínio, não de preferência:

| Domínio | Estrutura | Por que essa |
|---|---|---|
| **Estoque de bolsas** | Lista encadeada | Uma bolsa entra ao ser coletada e sai de qualquer posição — quando é alocada, transferida ou descartada. Remoção no meio é a operação normal, não a exceção. |
| **Requisições hospitalares** | Fila (FIFO) | Requisição é atendida por ordem de chegada. Quem pediu primeiro é atendido primeiro. |
| **Histórico de operações** | Pilha (LIFO) | O histórico é consultado do mais recente para o mais antigo: "o que aconteceu por último com esta bolsa". |

As três foram implementadas **duas vezes**: em C, com `malloc`/`free` e ponteiros
explícitos, e em Java, com encadeamento feito à mão.

> **Observação sobre o escopo:** nenhuma das estruturas ordena por data de validade,
> indexa por hash ou calcula rota. Priorização FEFO, índice hash e roteirização são
> conteúdo da Unidade 2 e ficaram deliberadamente de fora desta entrega.

---

## 2. Correspondência entre os arquivos

| Estrutura | Versão em C | Versão em Java | Testes |
|---|---|---|---|
| Lista | `aed/unidade1/c/lista_estoque.{h,c}` | `ListaEstoque<T>` | `ListaEstoqueTest` |
| Fila | `aed/unidade1/c/fila_requisicoes.{h,c}` | `FilaRequisicoes<T>` | `FilaRequisicoesTest` |
| Pilha | `aed/unidade1/c/pilha_historico.{h,c}` | `PilhaHistorico<T>` | `PilhaHistoricoTest` |
| Domínio | `aed/unidade1/c/dominio.h` | `com.rotavital.dominio` | — |
| Demonstração | `aed/unidade1/c/main.c` | — | — |

Pacote Java: `com.rotavital.estruturas.basicas`

**Decisão que sustenta toda esta tradução:** a versão Java **não usa** `LinkedList`,
`ArrayDeque`, `ArrayList` nem qualquer coleção pronta do `java.util` como
armazenamento. O encadeamento é feito à mão, com uma classe interna `No<E>`. Se as
estruturas fossem embrulhos sobre coleções prontas, não haveria tradução a comentar:
a lógica estaria dentro da biblioteca, não no código do aluno.

---

## 3. O nó: onde a correspondência começa

```c
/* lista_estoque.h */
typedef struct NoEstoque {
    Bolsa bolsa;                  /* copia da bolsa, armazenada no proprio no */
    struct NoEstoque *proximo;    /* NULL quando este e o ultimo no */
} NoEstoque;
```

```java
// ListaEstoque.java
private static final class No<E> {
    private final E elemento;
    private No<E> proximo;
}
```

O `struct NoEstoque *proximo` vira `No<E> proximo`. São a mesma coisa em natureza
diferente: em C, um endereço de memória; em Java, uma referência. Os dois podem ser
nulos, e nos dois casos nulo significa a mesma coisa — **fim da cadeia**.

Uma diferença de tipagem: em C o nó guarda uma **cópia da struct** (`novo->bolsa = bolsa`
copia os bytes inteiros). Em Java o nó guarda uma **referência ao objeto**. Isso não
altera a lógica de encadeamento, que é o objeto desta entrega, mas muda quem é dono do
dado: em C a lista é dona de uma cópia; em Java ela compartilha o objeto com quem
inseriu.

---

## 4. Lista encadeada — estoque de bolsas

### 4.1 Inserção no fim

```c
if (lista->ultimo == NULL) {
    lista->primeiro = novo;
    lista->ultimo = novo;
} else {
    lista->ultimo->proximo = novo;
    lista->ultimo = novo;
}
```

```java
if (ultimo == null) {
    primeiro = novo;
    ultimo = novo;
} else {
    ultimo.proximo = novo;
    ultimo = novo;
}
```

As duas versões mantêm **dois ponteiros de controle** (`primeiro`/`ultimo` em C,
`primeiro`/`ultimo` em Java) para que a inserção no fim custe O(1) em vez de percorrer
a lista toda.

**A ordem das duas atribuições não é estética.** `ultimo->proximo = novo` liga o antigo
último ao novo; só depois `ultimo = novo` avança o ponteiro de controle. Invertendo,
`ultimo` apontaria para o nó novo antes de alguém apontar para ele, e o antigo último —
junto com a lista inteira — ficaria inalcançável. Em C isso é vazamento de memória; em
Java, o coletor de lixo recolheria a lista viva. O erro é o mesmo, a consequência tem
nomes diferentes.

### 4.2 Remoção — os três casos de ponteiro

A remoção é a operação que mais exige manipulação explícita, e é onde a equivalência
fica mais visível. Nas duas versões, a varredura guarda o nó anterior:

```c
NoEstoque *atual = lista->primeiro;
NoEstoque *anterior = NULL;
while (atual != NULL && strcmp(atual->bolsa.codigo_rastreio, codigo) != 0) {
    anterior = atual;
    atual = atual->proximo;
}
```

```java
No<T> atual = primeiro;
No<T> anterior = null;
while (atual != null && !Objects.equals(atual.elemento, elemento)) {
    anterior = atual;
    atual = atual.proximo;
}
```

O motivo é idêntico nos dois: em lista **simplesmente** encadeada não existe caminho de
volta. Para desligar um nó, é preciso ter em mãos quem aponta para ele. A única
diferença é a comparação — `strcmp` sobre o campo de código em C, `Objects.equals` em
Java — porque C não tem igualdade de structs e Java delega ao `equals` do elemento.

Os três casos, lado a lado:

| Caso | C | Java |
|---|---|---|
| **1 — primeiro nó** | `lista->primeiro = atual->proximo;` | `primeiro = atual.proximo;` |
| **2 — meio ou fim** | `anterior->proximo = atual->proximo;` | `anterior.proximo = atual.proximo;` |
| **3 — era o último** | `lista->ultimo = anterior;` | `ultimo = anterior;` |

O caso 3 merece nota: ele **acumula** com os outros dois, não os substitui. Se o nó
removido era o único da lista, o caso 1 põe `primeiro` em nulo e o caso 3 põe `ultimo`
em nulo — exatamente o estado de lista vazia, sem precisar de tratamento próprio.

Em C, esquecer o caso 3 é um defeito grave: `ultimo` continuaria apontando para memória
já liberada, e a próxima inserção escreveria em cima dela. Em Java não há memória
liberada, mas o defeito existe do mesmo jeito em outra forma — `ultimo` apontaria para
um nó que não pertence mais à lista, e a próxima inserção ligaria o elemento novo a um
nó órfão, perdendo-o silenciosamente. **Mesmo bug, sintoma diferente:** em C, corrupção
de memória; em Java, dado que some sem erro.

### 4.3 A única divergência real: quem devolve a memória

```c
free(atual);        /* libera o no, nunca a lista */
lista->tamanho--;
```

```java
// Sem free: ao sair do encadeamento o no fica inalcancavel.
atual.proximo = null;
tamanho--;
```

Esta é a diferença central entre as duas versões, e a única que não é tradução direta.

Em C, o nó foi alocado com `malloc` e precisa de um `free` correspondente. Sem ele, a
memória do nó continua reservada pelo resto da execução — o programa funciona, a lista
fica correta, e o vazamento só aparece sob carga.

Em Java não existe linha equivalente ao `free`. O coletor de lixo recupera o nó assim
que nenhuma referência aponta para ele, e isso acontece naturalmente quando o nó sai do
encadeamento. **A lógica de ponteiros é idêntica; o que muda é quem devolve a memória.**

O `atual.proximo = null` não é o `free` disfarçado — é um cuidado adicional que o C não
precisa ter. Se alguém guardou uma referência ao nó removido, deixá-lo apontando para o
resto da lista manteria a cadeia inteira viva na memória. Soltar a ligação corta esse
caminho. É o mesmo raciocínio de alcançabilidade do C, aplicado na direção oposta.

---

## 5. Fila FIFO — requisições hospitalares

### 5.1 Dois ponteiros, dois papéis

```c
typedef struct {
    NoFila *frente;    /* de onde sai */
    NoFila *tras;      /* por onde entra */
    int tamanho;
} FilaRequisicoes;
```

```java
private No<T> frente;
private No<T> tras;
private int tamanho;
```

A fila separa entrada e saída: enfileirar mexe em `tras`, desenfileirar mexe em
`frente`. É o que mantém as duas operações em O(1).

A inserção repete a estrutura da lista, e o teste de fila vazia é feito sobre `tras`:

```c
if (fila->tras == NULL) { fila->frente = novo; fila->tras = novo; }
else { fila->tras->proximo = novo; fila->tras = novo; }
```

```java
if (tras == null) { frente = novo; tras = novo; }
else { tras.proximo = novo; tras = novo; }
```

Testar `tras` em vez de `frente` é proposital nas duas versões: é a referência que a
inserção manipula. Os dois são nulos ao mesmo tempo, então o resultado seria o mesmo —
mas ler o ponteiro que se vai escrever deixa a intenção explícita.

### 5.2 O detalhe que as duas versões precisam tratar

```c
fila->frente = removido->proximo;
if (fila->frente == NULL) {
    fila->tras = NULL;
}
```

```java
frente = removido.proximo;
if (frente == null) {
    tras = null;
}
```

Quando sai o último elemento, `frente` fica nulo — mas `tras` continuaria apontando
para o nó que acabou de sair. A próxima inserção faria `tras->proximo = novo` sobre um
nó que não pertence mais à fila.

Em C isso é escrita em memória liberada, com comportamento indefinido. Em Java o nó
ainda existe, mas está fora da fila: o elemento novo seria ligado a ele e **sumiria** —
`frente` continuaria nula, a fila pareceria vazia, e o dado estaria perdido sem nenhum
erro. As duas versões precisam da mesma linha, pelo mesmo motivo lógico.

### 5.3 Como o valor retirado é devolvido

```c
int fila_desenfileirar(FilaRequisicoes *fila, Requisicao *saida);
/* copia ANTES do free: depois dele o conteudo do no nao existe mais */
if (saida != NULL) { *saida = removido->requisicao; }
```

```java
public Optional<T> desenfileirar()
// return Optional.of(removido.elemento);
```

Aqui as linguagens divergem por idioma, não por lógica. C não tem retorno opcional:
devolve um código (`RV_OK` / `RV_ERRO`) e escreve o valor num **ponteiro de saída**
passado pelo chamador. Java devolve `Optional<T>`, vazio quando não há o que retirar.

As duas expressam a mesma ideia: **fila vazia é resposta válida, não erro.** Nenhuma
das versões lança exceção nem derruba o programa quando não há elemento. O padrão em C
exige que a cópia aconteça antes do `free`, porque depois dele o conteúdo do nó não
existe mais — uma preocupação que o Java não tem, já que o objeto sobrevive enquanto o
`Optional` o referenciar.

---

## 6. Pilha LIFO — histórico de operações

### 6.1 Um único ponteiro

```c
typedef struct NoPilha {
    Operacao operacao;
    struct NoPilha *abaixo;   /* no que estava no topo antes deste */
} NoPilha;
```

```java
private static final class No<E> {
    private final E elemento;
    private No<E> proximo;
}
```

A pilha precisa de um ponteiro só, o `topo`. O campo de ligação tem nomes diferentes —
`abaixo` em C, `proximo` em Java — e **a semântica é a mesma**: o nó que estava no topo
antes deste. O nome em C foi escolhido para deixar a direção explícita; em Java, para
manter a classe `No` uniforme entre as três estruturas.

### 6.2 A ordem das atribuições

```c
novo->abaixo = pilha->topo;
pilha->topo = novo;
```

```java
novo.proximo = topo;
topo = novo;
```

Duas linhas, nas duas linguagens, na mesma ordem. O nó novo passa a apontar para o
antigo topo **antes** de o topo subir para ele. Assim o resto da pilha permanece
alcançável durante toda a operação.

Na ordem inversa — mover o topo primeiro — o endereço de tudo o que estava embaixo seria
perdido no mesmo instante. Em C, vazamento de toda a pilha; em Java, o coletor recolheria
tudo. É o exemplo mais limpo desta entrega de como a lógica de ponteiros é idêntica e só
a consequência muda de nome.

Note também que **nenhuma das versões trata a pilha vazia como caso especial**: com a
pilha vazia, `topo` é nulo, o nó novo fica com ligação nula e vira o fundo. O caso
degenerado é absorvido pelo caso geral, nos dois lados.

### 6.3 Remoção

```c
pilha->topo = removido->abaixo;
free(removido);
```

```java
topo = removido.proximo;
removido.proximo = null;
```

O topo desce para o nó de baixo. Se era o único, a ligação é nula e a pilha volta ao
estado vazio — de novo sem caso especial.

---

## 7. Resumo das diferenças

| Aspecto | C | Java | É tradução ou divergência? |
|---|---|---|---|
| Ligação entre nós | `struct No *proximo` | `No<E> proximo` | Tradução direta |
| Fim da cadeia | `NULL` | `null` | Tradução direta |
| Ordem das atribuições | idêntica | idêntica | Tradução direta |
| Três casos da remoção | idênticos | idênticos | Tradução direta |
| Devolução de memória | `free(no)` explícito | coletor de lixo | **Divergência real** |
| Armazenamento do dado | cópia da struct | referência ao objeto | Divergência de tipagem |
| Retorno de operação | código + ponteiro de saída | `Optional<T>` | Divergência de idioma |
| Erro de uso | retorna `RV_ERRO` | `IllegalArgumentException` para nulo | Divergência de idioma |
| Tipo armazenado | fixo por estrutura | genérico `<T>` | Divergência de idioma |

As quatro últimas linhas são escolhas de linguagem, não mudanças de algoritmo. A lógica
de encadeamento — que é o objeto desta unidade — é a mesma nos dois lados, linha a linha.

---

## 8. Integração com a aplicação

As classes Java foram escritas para serem consumidas pela aplicação Spring Boot sem
travar nada:

- **genéricas em `<T>`** — não dependem de nenhuma classe do domínio, então servem para
  bolsas, requisições, operações ou qualquer outro tipo
- **sem anotação do Spring e sem dependência externa** — são Java puro, instanciáveis em
  qualquer contexto, inclusive em teste unitário sem subir a aplicação
- **`listar()` devolve lista imutável** — quem recebe não consegue corromper o
  encadeamento por fora
- **operações sobre estrutura vazia devolvem `Optional.empty()`** — quem chama checa e
  segue, sem precisar envolver cada chamada em `try/catch`

---

## 9. Como executar

**Versão em C:**

```bash
cd aed/unidade1/c
make
./rota_vital_u1
```

Compila com `gcc -Wall -Wextra -std=c11` sem warnings. A demonstração percorre as três
estruturas com dados sintéticos, imprimindo o estado a cada passo e nomeando os três
casos de ponteiro na remoção.

**Versão em Java:**

```bash
./mvnw test -Dtest="ListaEstoqueTest,FilaRequisicoesTest,PilhaHistoricoTest"
```

Os testes cobrem, para cada estrutura: inserir, remover, consultar, estrutura vazia,
elemento inexistente, ordem preservada e os três casos de remoção da lista.
