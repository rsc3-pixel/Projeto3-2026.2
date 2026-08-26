# W04 — Estruturas-base (AED)

## Objetivo

Implementar em memoria as estruturas de dados usadas pelo Rota Vital para representar conexoes da malha logistica, priorizar bolsas pelo criterio FEFO e indexar o estoque por grupo sanguineo e tipo de hemocomponente.

## Implementacao entregue

### 1. Grafo em memoria

Arquivo: `src/main/java/com/rotavital/estruturas/Grafo.java`

Representacao adotada: **lista de adjacencia** com `HashMap<T, Set<T>>`.

Operacoes implementadas:
- inserir vertice;
- inserir aresta;
- listar vizinhos;
- verificar existencia de vertice;
- consultar quantidade de vertices;
- suporte a grafo direcionado e nao direcionado.

O algoritmo de caminho minimo nao foi implementado, conforme o fora de escopo da W04.

### 2. Fila de prioridade FEFO

Arquivo: `src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java`

Representacao: `PriorityQueue<Bolsa>`.

Prioridade:
1. menor `dataValidade`;
2. em caso de empate, menor `codigoRastreio` para manter resultado deterministico.

Operacoes implementadas:
- adicionar bolsa;
- consultar proxima bolsa sem remover;
- retirar proxima bolsa;
- verificar se a fila esta vazia;
- consultar tamanho.

### 3. Indice hash de estoque

Arquivo: `src/main/java/com/rotavital/estruturas/IndiceEstoque.java`

Representacao: `HashMap<ChaveEstoque, List<Bolsa>>`.

A chave combina:
- `GrupoSanguineo`;
- `TipoHemocomponente`.

Operacoes implementadas:
- adicionar bolsa ao indice;
- buscar bolsas por grupo sanguineo + componente;
- remover bolsa do indice.

A localizacao da lista correspondente a uma chave possui custo medio **O(1)**.

## Testes unitarios

Arquivos:
- `src/test/java/com/rotavital/estruturas/GrafoTest.java`
- `src/test/java/com/rotavital/estruturas/FilaPrioridadeFefoTest.java`
- `src/test/java/com/rotavital/estruturas/IndiceEstoqueTest.java`

Os testes cobrem os criterios pedidos, incluindo:
- estrutura vazia;
- elemento unico;
- insercao e consulta;
- ordenacao FEFO;
- empate de validade;
- separacao correta das bolsas por chave hash;
- arestas e vizinhos do grafo;
- prevencao de arestas duplicadas.

Os testes foram escritos com **JUnit 5**. O repositorio recebido ainda nao possui Maven/Gradle configurado; por isso a configuracao do runner de testes permanece uma decisao de build do projeto, fora da implementacao das estruturas.

## Validacao realizada nesta entrega

As classes de producao foram compiladas com `javac` e foi executado um smoke test cobrindo:
- FEFO vazio, ordenacao e empate;
- indice hash vazio e buscas por chaves diferentes;
- grafo vazio, vertice, aresta e listagem de vizinhos.

Resultado da validacao: **OK**.

## Complexidade

A tabela detalhada esta em `docs/complexidade-estruturas.md`.

Resumo:

| Estrutura | Operacao | Complexidade |
|---|---|---:|
| Grafo | Inserir vertice | O(1) medio |
| Grafo | Inserir aresta | O(1) medio |
| Grafo | Obter vizinhos | O(1) para localizar; O(grau(v)) para percorrer |
| FEFO | Adicionar bolsa | O(log n) |
| FEFO | Consultar proxima | O(1) |
| FEFO | Retirar proxima | O(log n) |
| Indice hash | Adicionar bolsa | O(1) amortizado |
| Indice hash | Buscar grupo + componente | O(1) medio para localizar a lista |
| Indice hash | Remover bolsa | O(k) dentro do grupo |

## Checklist dos criterios de aceite

- [x] Grafo permite inserir vertice.
- [x] Grafo permite inserir aresta.
- [x] Grafo permite listar vizinhos.
- [x] Fila de prioridade devolve primeiro a bolsa de menor validade.
- [x] Empates de validade possuem criterio deterministico.
- [x] Indice hash busca por grupo sanguineo e tipo de componente.
- [x] Busca da chave do indice possui tempo constante medio.
- [x] Existem testes para estrutura vazia.
- [x] Existem testes para elemento unico.
- [x] Existe teste para empate.
- [x] Complexidades principais estao documentadas.
- [x] Caminho minimo nao foi implementado.
- [x] Persistencia nao foi implementada.

## Dependencia PI3-13

A W04 declara dependencia da PI3-13 para a decisao de representacao do grafo. Nesta entrega foi usada **lista de adjacencia (`HashMap + Set`)**. Se a PI3-13 registrar outra estrutura, o contrato publico de `Grafo` pode permanecer o mesmo e somente sua representacao interna precisa ser adaptada.
