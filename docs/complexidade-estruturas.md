# Complexidade das estruturas-base

> Representacao adotada nesta entrega para o grafo: **lista de adjacencia usando HashMap + Set**. Como a W04 depende da PI3-13, se a decisao registrada nela for diferente, somente a representacao interna do grafo precisa ser ajustada antes do merge.

## Grafo em memoria

Representacao: lista de adjacencia (`HashMap<T, Set<T>>`).

| Operacao | Complexidade media | Justificativa |
|---|---:|---|
| Inserir vertice | O(1) | Insercao media em `HashMap`. |
| Verificar vertice | O(1) | Busca media em `HashMap`. |
| Inserir aresta | O(1) | Busca dos vertices no `HashMap` e insercao media no `Set`; em grafo nao direcionado sao duas insercoes constantes. |
| Obter/listar vizinhos | O(1) para obter a colecao; O(grau(v)) para percorrer | A lista de adjacencia do vertice e encontrada diretamente; percorrer todos os vizinhos depende do grau do vertice. |
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

| Operacao | Complexidade media | Justificativa |
|---|---:|---|
| Adicionar bolsa | O(1) amortizado | Busca/insercao media no `HashMap` e adicao amortizada ao fim da `ArrayList`. |
| Buscar por grupo + componente | O(1) medio para localizar a lista | A chave hash leva diretamente ao grupo de bolsas. Percorrer os `k` resultados custa O(k), o que e inevitavel para consumir os resultados. |
| Remover bolsa | O(k) no grupo encontrado | Encontrar a lista e O(1) medio, mas remover de `ArrayList` pode exigir percorrer/deslocar elementos do grupo. |

## Relacao com os criterios de aceite

- O grafo permite inserir vertice, inserir aresta e listar vizinhos.
- A fila FEFO prioriza a menor `dataValidade`.
- Empates de validade sao resolvidos pelo `codigoRastreio`, garantindo comportamento deterministico.
- O indice usa hash pela combinacao de grupo sanguineo e tipo de hemocomponente, com busca media O(1) para localizar o conjunto correspondente.
- Os testes contemplam estrutura vazia, elemento unico e empate de validade.
