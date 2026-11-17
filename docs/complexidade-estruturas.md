# Complexidade dos algoritmos e estruturas (AED)

**Tarefa:** PI3-101 (subtarefas PI3-191 a PI3-194)

Cada complexidade abaixo aponta o trecho do codigo que a justifica. As versoes em Java (aplicacao) e em C++ (aed/unidade2/cpp) sao analisadas separadamente, porque usam estruturas diferentes e, em alguns casos, chegam a complexidades diferentes. A secao 7 reune essas divergencias.

Notacao: **V** vertices e **E** arestas do grafo, **k** pontos de entrega pendentes, **n** bolsas na fila, **m** bolsas no mesmo grupo (grupo sanguineo + componente), **G = 8** grupos sanguineos.

---

## 1. Grafo em memoria

**Java:** lista de adjacencia em `HashMap<T, HashMap<T, Double>>`, onde o mapa interno associa cada vizinho ao peso da aresta.

| Operacao | Complexidade | Justificativa |
|---|---:|---|
| Inserir vertice | O(1) medio | Insercao em `HashMap`. |
| Verificar vertice | O(1) medio | Busca em `HashMap`. |
| Inserir aresta | O(1) medio | Busca dos dois vertices e insercao no mapa interno; em grafo nao dirigido sao duas insercoes constantes. |
| Obter/listar vizinhos | O(1) para obter; O(grau(v)) para percorrer | A lista do vertice e encontrada direto; percorrer depende do grau. |
| Consultar peso de uma aresta | O(1) medio | Duas buscas em `HashMap`. |
| Listar vertices | O(1) para obter; O(V) para percorrer | `keySet()` devolve uma visao. |

**C++:** lista de vertices encadeada, cada vertice com sua lista de arestas. Localizar um vertice e busca linear ([grafo.cpp:33](../aed/unidade2/cpp/grafo.cpp#L33)), entao verificar vertice custa O(V), obter os vizinhos O(V + grau(v)) e inserir aresta O(V + grau(v)).

---

## 2. Caminho minimo (Dijkstra)

### Java: O((V + E) log V)

[Dijkstra.java:132](../src/main/java/com/rotavital/estruturas/Dijkstra.java#L132) usa `PriorityQueue` com remocao preguicosa: uma distancia melhor entra como candidato novo em vez de atualizar o antigo, e o candidato velho e descartado quando sai da fila, porque o vertice ja foi visitado ([linha 144](../src/main/java/com/rotavital/estruturas/Dijkstra.java#L144)).

- Cada aresta gera no maximo uma insercao na fila: O(E) insercoes de O(log E) cada.
- Como E < V², log E = O(log V).
- Total: **O((V + E) log V)** de tempo e **O(V + E)** de espaco (as distancias, os predecessores e a fila).

### C++: O(V² + E·V) no pior caso, O(V²) na malha do projeto

A tabela de distancias e uma lista encadeada sem heap. O laco principal ([dijkstra.cpp:136](../aed/unidade2/cpp/dijkstra.cpp#L136)) fecha um vertice por volta, V voltas no total, e em cada uma:

- `menorAberto` percorre a tabela inteira para achar o menor aberto ([dijkstra.cpp:80](../aed/unidade2/cpp/dijkstra.cpp#L80)): O(V);
- cada aresta que sai do vertice chama `relaxar`, que procura o destino na tabela com busca linear ([dijkstra.cpp:44](../aed/unidade2/cpp/dijkstra.cpp#L44)): O(V) por aresta.

Somando, **O(V² + E·V)**. Num grafo denso (E proximo de V²) isso chega a O(V³). Na malha do Rota Vital, porem, cada unidade se liga as 4 mais proximas (`PremissasMalha.VIZINHOS_POR_UNIDADE`), entao E <= 8V, e o termo E·V tambem e O(V²): **O(V²)** na pratica do projeto.

**Medicao.** O Dijkstra em C++ da equipe foi cronometrado em grafos sinteticos (`g++ -O2`, media de 3 execucoes):

| Grau fixo 4, variando V | Tempo | Razao |
|---|---:|---:|
| V = 250 | 0,25 ms | |
| V = 500 | 1,11 ms | x4,45 |
| V = 1000 | 5,14 ms | x4,63 |
| V = 2000 | 20,42 ms | x3,97 |

| V fixo 1000, variando o grau | Tempo | Razao |
|---|---:|---:|
| grau 4 | 5,03 ms | |
| grau 8 | 5,38 ms | x1,07 |
| grau 16 | 7,39 ms | x1,37 |
| grau 32 | 9,99 ms | x1,35 |

Dobrar V com o grau fixo quadruplica o tempo: crescimento quadratico, como previsto. Dobrar o grau pesa bem menos que o pior caso sugere, e o motivo esta no codigo: cada vertice novo entra **no inicio** da tabela ([dijkstra.cpp:51](../aed/unidade2/cpp/dijkstra.cpp#L51)), e os vizinhos do vertice que acabou de fechar costumam ser os mais recentes, entao a busca linear os encontra logo.

---

## 3. Roteirizacao por vizinho mais proximo

Heuristica gulosa: a partir da origem, vai sempre para o pendente mais proximo, ate visitar todos ou nao restar pendente alcancavel.

### Java: O(k · (V + E) log V)

O laco ([VizinhoMaisProximo.java:148](../src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java#L148)) roda no maximo k vezes, uma por ponto visitado. Em cada volta:

| Passo | Custo | Trecho |
|---|---:|---|
| Distancias a partir do ponto atual | O((V + E) log V) | `dijkstra.calcularDistancias(atual)`, [linha 152](../src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java#L152) |
| Escolher o pendente mais proximo | O(k) | laco sobre os pendentes com consulta O(1) no `HashMap` de distancias, [linha 203](../src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java#L203) |
| Reconstruir o caminho do trecho | O((V + E) log V) | `dijkstra.calcularRota(atual, escolhido)`, [linha 170](../src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java#L170) |

Total: **O(k · (V + E) log V)** de tempo e **O(V + E + k)** de espaco.

**Observacao.** `calcularRota` roda o Dijkstra inteiro de novo ([Dijkstra.java:89](../src/main/java/com/rotavital/estruturas/Dijkstra.java#L89)), a partir do mesmo ponto que `calcularDistancias` acabou de processar. A complexidade nao muda, mas o trabalho por volta dobra. Uma unica travessia que devolvesse distancias e predecessores juntos resolveria.

### C++: O(k · V²) na malha do projeto

O laco ([vizinho_mais_proximo.cpp:133](../aed/unidade2/cpp/vizinho_mais_proximo.cpp#L133)) tambem roda no maximo k vezes. Em cada volta:

- um Dijkstra: O(V²) na malha (secao 2);
- para cada pendente, busca linear no vetor de distancias ([vizinho_mais_proximo.cpp:155](../aed/unidade2/cpp/vizinho_mais_proximo.cpp#L155)): O(k · V).

Total: O(k · (V² + k · V)). Como k <= V, **O(k · V²)**.

A versao em C++ roda o Dijkstra uma so vez por volta, mas devolve apenas a ordem de visita e o custo de cada trecho, sem o caminho vertice a vertice que a versao Java monta.

**Limite da heuristica (as duas versoes).** O vizinho mais proximo nao garante o roteiro de menor custo total: resolver o problema de forma exata e o problema do caixeiro-viajante, NP-dificil. O teste `VizinhoMaisProximoTest` registra um caso em que a escolha gulosa sai mais cara que o otimo.

---

## 4. Compatibilidade ABO/Rh por matriz

### Java: O(1)

Duas tabelas, hemacias e plasma, em `EnumMap<GrupoSanguineo, EnumSet<GrupoSanguineo>>` ([MatrizCompatibilidade.java:50](../src/main/java/com/rotavital/compatibilidade/MatrizCompatibilidade.java#L50)). `EnumSet` guarda o conjunto como um vetor de bits indexado pela ordem da constante.

| Operacao | Complexidade | Justificativa |
|---|---:|---|
| `compativel(doador, receptor, tipo)` | O(1) | acesso direto a linha do receptor e teste de um bit, [linha 117](../src/main/java/com/rotavital/compatibilidade/MatrizCompatibilidade.java#L117) |
| `doadoresPara(receptor, tipo)` | O(G) = O(1) | copia de um conjunto de no maximo 8 grupos |
| Espaco | O(G²) = O(1) | 2 tabelas de 8 x 8 bits |

### C++: O(1)

As tabelas sao vetores de 8 bytes, um bit por doador ([matriz_compatibilidade.cpp:29](../aed/unidade2/cpp/matriz_compatibilidade.cpp#L29)). Testar compatibilidade e um `&` de bits: O(1). `doadoresPara` percorre os 8 bits da linha: O(G) = O(1).

As duas versoes sao equivalentes em complexidade; a diferenca e so a forma de guardar o vetor de bits.

**Por que matriz e nao regras.** A compatibilidade e um conjunto fechado: 8 grupos x 8 grupos x 2 regras. Tabelar custa espaco constante e transforma a regra clinica em dado conferivel linha a linha, sem desvios condicionais que poderiam esconder um caso errado.

---

## 5. Fila FEFO por insercao ordenada

**Java e C++:** lista simplesmente encadeada, mantida ordenada por validade na insercao. Nao e heap: nao ha `PriorityQueue` na fila FEFO ([FilaPrioridadeFefo.java](../src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java)).

| Operacao | Complexidade | Justificativa |
|---|---:|---|
| Adicionar bolsa | **O(n)** | percorre a lista ate achar a posicao, [FilaPrioridadeFefo.java:139](../src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java#L139) e [fila_fefo.cpp:71](../aed/unidade2/cpp/fila_fefo.cpp#L71) |
| Consultar proxima bolsa | O(1) | a de menor validade e sempre a primeira |
| Retirar proxima bolsa | O(1) | remove o primeiro no, [FilaPrioridadeFefo.java:174](../src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java#L174) |
| Descartar vencidas | O(d) | d bolsas vencidas, todas no inicio da lista |
| Tamanho | O(1) | contador mantido a cada insercao e remocao |

**Por que nao heap.** A Unidade 1 usava um heap (`PriorityQueue`, PI3-47), com insercao e retirada em O(log n). A Unidade 2 trocou pela insercao ordenada, e o javadoc da classe registra o motivo: a fila guarda as bolsas de uma combinacao de grupo e componente, que sao poucas por unidade, entao o O(n) da insercao e aceitavel; em troca, consultar e retirar, que e o que a alocacao faz a cada requisicao, passaram a O(1). A estrutura ficou mais cara onde o estoque e abastecido e mais barata onde ele e consumido. O desempate pelo codigo de rastreio mantem o resultado deterministico.

---

## 6. Indice de estoque por hash

**Java:** `HashMap<ChaveEstoque, List<Bolsa>>`, chave = grupo sanguineo + componente. **C++:** vetor de baldes com encadeamento.

As 32 combinacoes possiveis caem em baldes distintos (hash perfeito por `ordinal`, PI3-132): localizar o grupo e O(1) exato, sem colisao e sem redimensionamento. Detalhes em [W10-indice-estoque-hash.md](W10-indice-estoque-hash.md).

| Operacao | Complexidade | Justificativa |
|---|---:|---|
| Adicionar bolsa | **O(m)** | antes de inserir, procura a mesma bolsa no grupo para substituir em vez de duplicar: `indexOf` em [IndiceEstoque.java:124](../src/main/java/com/rotavital/estruturas/IndiceEstoque.java#L124), laco em [indice_estoque.cpp:51](../aed/unidade2/cpp/indice_estoque.cpp#L51) |
| Buscar por grupo + componente | O(1) para localizar; O(m) para percorrer | a chave leva direto ao grupo |
| Remover bolsa | O(m) | encontra o grupo em O(1) e procura a bolsa nele |
| Tamanho | Java: O(G·C) = O(1); C++: O(1) | Java soma os grupos a cada chamada ([linha 185](../src/main/java/com/rotavital/estruturas/IndiceEstoque.java#L185)), no maximo 32; C++ mantem contador |

---

## 7. Divergencias entre Java e C++

| Algoritmo | Java | C++ | Por que diferem |
|---|---|---|---|
| Dijkstra | O((V + E) log V) | O(V² + E·V); O(V²) na malha | Java usa `PriorityQueue`; C++ usa lista encadeada com busca linear, de proposito, para que o encadeamento de ponteiros fique visivel |
| Vizinho mais proximo | O(k · (V + E) log V), 2 Dijkstras por volta | O(k · V²), 1 Dijkstra por volta | herda o Dijkstra de cada versao; C++ nao reconstroi o caminho completo |
| Matriz ABO/Rh | O(1) | O(1) | equivalentes; `EnumSet` e vetor de bytes sao dois jeitos de guardar bits |
| Fila FEFO | O(n) insercao, O(1) retirada | igual | mesma estrutura |
| Indice hash | O(m) insercao, O(1) localizar | igual | mesma estrutura |

A divergencia que importa e a do Dijkstra. `Dijkstra.java`, o [W02-escopo-grafo-rede-distribuicao.md](W02-escopo-grafo-rede-distribuicao.md) e o [W10-roteirizacao-vizinho-mais-proximo.md](W10-roteirizacao-vizinho-mais-proximo.md) afirmam O((V + E) log V) e falam da versao Java. Trocar a tabela em lista da versao C++ por `std::priority_queue` recuperaria a mesma complexidade, ao custo de apagar o encadeamento manual que e o objeto de estudo da Unidade 2. A traducao comentada em [U2-traducao-c-java.md](U2-traducao-c-java.md), secao 1.4, mostra os dois trechos lado a lado.

Com a malha real (V = 12, E = 64) as duas versoes respondem em microssegundos: a diferenca assintotica so aparece em redes bem maiores, como mostra a medicao da secao 2.

---

## 8. Correcoes em relacao a versao anterior deste documento

A versao de 19/10 trazia dois erros, corrigidos aqui a partir do codigo:

- **Fila FEFO:** descrita como `PriorityQueue` (heap), com insercao e retirada em O(log n). Era a implementacao da Unidade 1 (PI3-47); na Unidade 2 ela foi trocada por lista encadeada com insercao ordenada, e o documento nao acompanhou. Hoje: insercao O(n), retirada O(1).
- **Indice hash, adicionar:** descrito como O(1) amortizado. O codigo verifica se a bolsa ja esta no grupo antes de inserir, entao a insercao e O(m).

---

## 9. Relacao com os criterios de aceite

- Complexidade de tempo e de espaco de cada um dos quatro algoritmos, com o trecho de codigo que a justifica (PI3-191, PI3-192).
- Divergencia entre Java e C++ registrada, dizendo a qual versao cada complexidade se refere (PI3-193).
- Pesos negativos sao rejeitados na insercao da aresta, condicao exigida pela corretude do Dijkstra (PI3-19).
- O indice localiza o grupo em O(1), sem colisao por construcao (PI3-132).
