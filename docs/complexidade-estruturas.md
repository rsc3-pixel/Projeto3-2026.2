# Complexidade das estruturas-base

> Representacao adotada para o grafo: **lista de adjacencia usando HashMap aninhado (`Map<T, Map<T, Double>>`)**, alinhada com as decisoes do escopo do grafo (PI3-13): arestas ponderadas por tempo estimado em minutos e grafo dirigido por padrao.

## Grafo em memoria

Representacao: lista de adjacencia ponderada (`HashMap<T, HashMap<T, Double>>`), onde o mapa interno associa cada vizinho ao peso da aresta.

| Operacao | Complexidade media | Justificativa |
|---|---:|---|
| Inserir vertice | O(1) | Insercao media em `HashMap`. |
| Verificar vertice | O(1) | Busca media em `HashMap`. |
| Inserir aresta | O(1) | Busca dos vertices no `HashMap` e insercao media no mapa interno; em grafo nao dirigido sao duas insercoes constantes. |
| Obter/listar vizinhos | O(1) para obter a colecao; O(grau(v)) para percorrer | A lista de adjacencia do vertice e encontrada diretamente; percorrer todos os vizinhos depende do grau do vertice. |
| Consultar peso de uma aresta | O(1) medio | Duas buscas em `HashMap`: o vertice de origem e o destino no mapa interno. |
| Listar vertices | O(1) para obter a colecao; O(V) para percorrer | `HashMap.keySet()` devolve uma visao; o Dijkstra percorre para inicializar as distancias. |
| Quantidade de vertices | O(1) | `HashMap.size()`. |

## Fila de prioridade FEFO

Representacao: `PriorityQueue<Bolsa>` ordenada por `dataValidade` e, em caso de empate, por `codigoRastreio`.

| Operacao | Complexidade | Justificativa |
|---|---:|---|
| Adicionar bolsa | O(log n) | A heap precisa restaurar sua propriedade de ordenacao. |
| Consultar proxima bolsa | O(1) | O menor elemento fica na raiz da heap. |
| Retirar proxima bolsa | O(log n) | Remove a raiz e reorganiza a heap. |
| Verificar se esta vazia | O(1) | Consulta direta ao tamanho da estrutura. |
| Obter tamanho | O(1) | A fila mantem o tamanho internamente. |

## Indice hash de estoque

Representacao: `HashMap<ChaveEstoque, List<Bolsa>>`, onde a chave combina `GrupoSanguineo` + `TipoHemocomponente`.

A chave usa hash perfeito por `ordinal`, e o mapa e criado com capacidade 64: as 32 combinacoes possiveis caem em baldes distintos, sem colisao e sem redimensionamento. Entao o custo de localizar a chave e O(1) exato, nao apenas medio. Detalhes em [W10-indice-estoque-hash.md](W10-indice-estoque-hash.md).

| Operacao | Complexidade media | Justificativa |
|---|---:|---|
| Adicionar bolsa | O(1) amortizado | Busca/insercao media no `HashMap` e adicao amortizada ao fim da `ArrayList`. |
| Buscar por grupo + componente | O(1) para localizar a lista | A chave hash leva diretamente ao grupo de bolsas. Percorrer os `k` resultados custa O(k), o que e inevitavel para consumir os resultados. |
| Remover bolsa | O(k) no grupo encontrado | Encontrar a lista e O(1) medio, mas remover de `ArrayList` pode exigir percorrer/deslocar elementos do grupo. |

## Relacao com os criterios de aceite

- O grafo permite inserir vertice, inserir aresta com peso e listar vizinhos, com ou sem os pesos.
- Pesos negativos sao rejeitados na insercao, condicao exigida pela corretude do Dijkstra (PI3-19).
- A fila FEFO prioriza a menor `dataValidade`.
- Empates de validade sao resolvidos pelo `codigoRastreio`, garantindo comportamento deterministico.
- O indice usa hash pela combinacao de grupo sanguineo e tipo de hemocomponente, com busca O(1) para localizar o conjunto correspondente, sem colisao por construcao (PI3-132).
- Os testes contemplam estrutura vazia, elemento unico e empate de validade.
