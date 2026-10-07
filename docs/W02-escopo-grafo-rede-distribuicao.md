# Documento de escopo — Modelagem do grafo da rede de distribuição de hemocomponentes

> **Status do documento:** Especificação original da **W02**, revisada e atualizada na **W08** como evidência de alinhamento com as estruturas de dados efetivamente implementadas no sistema Rota Vital (Unidade 1 de AED).
> **Implementação de referência:** [`com.rotavital.estruturas.Grafo`](../src/main/java/com/rotavital/estruturas/Grafo.java) e [`com.rotavital.estruturas.Dijkstra`](../src/main/java/com/rotavital/estruturas/Dijkstra.java).
> **Documento consolidado de evidências:** [`docs/evidencias-unidade-1-aed.md`](evidencias-unidade-1-aed.md).

---

## Contexto do projeto

A rede de distribuição de sangue precisa entregar o componente certo (compatível e dentro da validade), no lugar certo, no tempo certo e na temperatura certa. Este documento define a estrutura de grafo sobre a qual o cálculo de rota vai rodar, para que a plataforma possa alocar bolsas e planejar entregas entre hemocentros e hospitais.

## Objetivo

Definir como a rede de distribuição de hemocomponentes vira um grafo, para que o cálculo de rota tenha uma estrutura sobre a qual rodar.

---

## Decisões de Modelagem e Alinhamento com a Implementação

### 1. Vértice

**Definição:** unidade da rede (hemocentro, banco de sangue regional ou hospital/ponto de entrega).

Cada unidade física da operação é representada por exatamente um vértice. A representação interna da unidade (atributos, identificadores) é tratada em PI3-12; este documento assume que cada unidade já chega com um identificador único a ser usado como chave do vértice.

**Implementação no código (W04–W08):**
- A classe de estrutura [`Grafo<T>`](../src/main/java/com/rotavital/estruturas/Grafo.java) é genérica sobre o tipo do vértice `T`.
- Na aplicação real e no carregador da malha ([`CarregadorMalha`](../src/main/java/com/rotavital/rede/CarregadorMalha.java)), o vértice é instanciado como `String`, utilizando o código identificador da unidade (ex.: `"HC01"`, `"HC06"`, `"HC12"`).
- No modelo de domínio JPA, as unidades correspondem às entidades [`Hemocentro`](../src/main/java/com/rotavital/dominio/Hemocentro.java) e [`Hospital`](../src/main/java/com/rotavital/dominio/Hospital.java) (subclasses de [`Local`](../src/main/java/com/rotavital/dominio/Local.java)), e no pacote de rede à classe [`UnidadeRede`](../src/main/java/com/rotavital/rede/UnidadeRede.java).
- Operações de vértice implementadas: `inserirVertice(T vertice)`, `contemVertice(T vertice)`, `listarVertices()` e `quantidadeVertices()`.

### 2. Aresta

**Definição:** trecho viário direto entre duas unidades — um caminho que não passa por nenhuma outra unidade da rede no meio do percurso.

Se existe uma rota física entre a unidade A e a unidade B que passa por uma terceira unidade C, isso é modelado como duas arestas (A–C e C–B), não uma aresta única A–B.

**Implementação no código (W04–W08):**
- No [`Grafo<T>`](../src/main/java/com/rotavital/estruturas/Grafo.java), a aresta conecta um par `(origem, destino)` armazenando o tempo em minutos associado no mapa interno de adjacências.
- Operações de aresta implementadas: `inserirAresta(T origem, T destino, double pesoMinutos)`, `contemAresta(T origem, T destino)`, `pesoAresta(T origem, T destino)`, `listarVizinhos(T vertice)` e `vizinhosComPeso(T vertice)`.
- A inserção de aresta cria automaticamente os vértices de origem e destino caso ainda não estejam presentes no grafo (`adjacencias.putIfAbsent`).

### 3. Peso da aresta

**Proposta: tempo estimado de percurso (em minutos).**

Justificativa:
- Hemocomponentes têm validade curta e exigem manutenção de cadeia fria — o que importa minimizar é o tempo até a entrega, não a distância percorrida. Tempo é a métrica que conecta diretamente o cálculo de rota ao risco de descarte por vencimento e ao risco ao paciente.
- Distância pura é um proxy fraco: dois trechos com a mesma distância podem ter tempos de percurso bem diferentes dependendo do tipo de via, trânsito ou restrições para veículos.
- Custo (combustível, pedágio) é uma dimensão de otimização distinta de tempo. Combinar tempo e custo em um único peso escalar exige normalizar unidades diferentes de forma arbitrária, o que enfraquece a justificativa da rota escolhida.
- **Recomendação:** registrar custo como um atributo adicional da aresta (não como peso), permitindo no futuro uma função de otimização multi-objetivo, sem misturar as métricas nesta sprint.

> O peso é o que o algoritmo minimiza. Escolher distância ou tempo muda qual rota o sistema considera melhor — por isso a justificativa importa mais que a escolha, especialmente quando o que está em jogo é a validade de uma bolsa de sangue.

**Implementação no código (W04–W08):**
- Implementado como `double pesoMinutos` em [`Grafo.java`](../src/main/java/com/rotavital/estruturas/Grafo.java).
- **Validação de invariante:** O método `inserirAresta` lança `IllegalArgumentException("Peso nao pode ser negativo: " + pesoMinutos)` se o peso for menor que zero. Essa garantia formal impede pesos negativos, pré-condição indispensável para a corretude do algoritmo de Dijkstra.
- Na malha viária real ([`CarregadorMalha.java`](../src/main/java/com/rotavital/rede/CarregadorMalha.java) e [`PremissasMalha.java`](../src/main/java/com/rotavital/rede/PremissasMalha.java)), os tempos são calculados através da distância geográfica (fórmula de Haversine) convertida com velocidade operacional média de 40 km/h para veículos de transporte de sangue, refletindo com precisão o tráfego urbano/metropolitano.

### 4. Grafo dirigido ou não dirigido

**Decisão: dirigido (digrafo).**

Justificativa: o tempo (e o custo) de ida e de volta entre duas unidades não é necessariamente simétrico — vias de mão única, sentido de tráfego, restrições de horário, ou retorno vazio vs. carregado. Um grafo não dirigido assumiria uma simetria que não existe na prática operacional.

**Implementação no código (W04–W08):**
- O construtor padrão `public Grafo()` inicializa o grafo com `direcionado = true`.
- É suportada a criação de grafos não-direcionados via `public Grafo(boolean direcionado)`, onde a inserção de aresta replica automaticamente a ligação simétrica `(destino, origem)` com mesmo peso.
- O estado de direcionamento pode ser verificado via `isDirecionado()`.
- A rede logística padrão do Rota Vital opera estritamente como digrafo.

### 5. Representação escolhida

**Decisão: lista de adjacência.**

Justificativa pela densidade esperada: a rede de hemocentros e hospitais é esparsa — cada unidade se conecta diretamente a poucas outras unidades vizinhas geograficamente, não a todas as unidades da rede. Para grafos esparsos, a lista de adjacência ocupa O(V + E) de espaço, contra O(V²) da matriz de adjacência. Além disso, o algoritmo de caminho mínimo escolhido (Dijkstra com fila de prioridade) itera sobre os vizinhos diretos de cada vértice, o que a lista de adjacência favorece naturalmente. Isso também está alinhado com a complexidade controlada definida para o projeto (poucos componentes, grafo limitado).

**Implementação no código (W04–W08):**
- A estrutura utiliza mapas aninhados em memória:
  ```java
  private final Map<T, Map<T, Double>> adjacencias = new HashMap<>();
  ```
- Cada vértice aponta para um `LinkedHashMap<T, Double>` contendo seus vizinhos imediatos e os respectivos pesos.
- Complexidade espacial: **$O(V + E)$**.
- Obter vizinhos com peso ([`vizinhosComPeso`](../src/main/java/com/rotavital/estruturas/Grafo.java#L87)): **$O(1)$** para retornar a coleção não modificável, permitindo ao Dijkstra iterar apenas sobre os graus efetivos do vértice atual ($O(\text{grau}(v))$).

### 6. Algoritmo de caminho mínimo

**Decisão: Dijkstra, com fila de prioridade (heap binário).**

Justificativa: os pesos das arestas (tempo estimado) são sempre não negativos — condição necessária e suficiente para a corretude do Dijkstra. Com heap binário, a complexidade é O((V + E) log V), adequada ao tamanho esperado da rede. Não há necessidade de Bellman-Ford, que resolve o caso de pesos negativos, inexistente neste domínio.

**Implementação no código (W04–W08):**
- Implementado em [`com.rotavital.estruturas.Dijkstra<T>`](../src/main/java/com/rotavital/estruturas/Dijkstra.java) com as seguintes características:
  1. Utiliza `java.util.PriorityQueue<VerticeDistancia<T>>` (heap binário) ordenada pelo menor tempo acumulado.
  2. Mantém conjunto de visitados/fechados (`Set<T> visitados = new HashSet<>()`), garantindo que cada vértice saia da fila com a menor distância ótima conhecida e sem reprocessamento.
  3. Mantém mapa de predecessores (`Map<T, T> predecessores`) para reconstrução direta do caminho da rota percorrida.
  4. Método `calcularDistancias(T origem)` devolve `Map<T, Double>` contendo apenas os nós alcançáveis (ausência de chave indica inalcançabilidade, eliminando o uso de sentinelas arbitrárias).
  5. Método `calcularRota(T origem, T destino)` devolve [`ResultadoRota<T>`](../src/main/java/com/rotavital/estruturas/ResultadoRota.java) com caminho completo e custo total acumulado. Se inalcançável ou argumentos inválidos, retorna `ResultadoRota.semCaminho()`.
- Complexidade temporal: **$O((V + E) \log V)$**; complexidade espacial auxiliar: **$O(V)$**.

### 7. Janelas de tempo e cadeia fria

**Decisão: registrar como fora de escopo desta sprint, dependência crítica para W06.**

Justificativa: o Dijkstra clássico assume pesos estáticos por aresta. Janela de tempo (chegar dentro de um intervalo) e cadeia fria (não exceder tempo máximo fora de refrigeração) são restrições de viabilidade sobre o caminho — uma camada de filtragem ou penalização aplicada depois (ou durante) o cálculo do caminho mínimo, não um peso simples de aresta. Incorporar isso agora ampliaria o escopo desta sprint sem necessidade.

Vale registrar que, dado o domínio (hemocomponentes), essa não é uma restrição secundária: cadeia fria rompida ou janela de tempo perdida pode inviabilizar a bolsa mesmo que o caminho seja o mais rápido em tempo puro. A modelagem completa dessas restrições (provavelmente como validação pós-Dijkstra ou como penalização de custo) fica marcada como dependência prioritária para a W06, já que se conecta diretamente com a competência de arquitetura de redes e telemetria (monitoramento de temperatura) do projeto.

---

## Esboço da rede — exemplo pequeno (5 unidades)

Abaixo está o diagrama do grafo dirigido de 5 vértices modelado na W02:

![Grafo da rede de distribuição de hemocomponentes](img/W02-grafo-rede-distribuicao.png)

Tabela de arestas do exemplo com 5 vértices (Hemocentro, Hospital A, Hospital B, Hospital C, Hospital Destino) e pesos em tempo estimado (minutos):

| Origem | Destino | Peso (min) |
|--------|---------|------------|
| Hemocentro | Hospital A | 10 |
| Hemocentro | Hospital B | 20 |
| Hospital A | Hospital B | 5 |
| Hospital A | Hospital C | 15 |
| Hospital B | Hospital Destino | 8 |
| Hospital C | Hospital Destino | 5 |

### Caminho mínimo calculado à mão (Dijkstra a partir do Hemocentro)

- **Hemocentro → Hospital A → Hospital B → Hospital Destino = 10 + 5 + 8 = 23** ✅ (caminho mínimo)
- Hemocentro → Hospital B → Hospital Destino = 20 + 8 = 28
- Hemocentro → Hospital A → Hospital C → Hospital Destino = 10 + 15 + 5 = 30

O caminho de menor custo passa por Hospital A e Hospital B mesmo parecendo um desvio geométrico, porque o peso é tempo, não distância — o que reforça por que a escolha do peso (seção 3) é a decisão mais crítica deste documento, sobretudo com hemocomponentes envolvidos.

> **Validação automatizada por teste de unidade:** Esse mesmo grafo de 5 unidades e o cálculo de 23 minutos estão implementados e testados em [`DijkstraTest.java#redeDeExemplo()`](../src/test/java/com/rotavital/estruturas/DijkstraTest.java#L23-L34) e [`CriteriosAceiteAlocacaoTest.java#caminhoMinimoW02PreservadoNaIntegracao()`](../src/test/java/com/rotavital/alocacao/CriteriosAceiteAlocacaoTest.java), garantindo que o algoritmo de código real produz exatamente o caminho calculado manualmente.

---

## Critérios de aceite e Rastreabilidade de Testes

| Critério de aceite | Status | Onde está coberto |
|---|:---:|---|
| Vértice, aresta e peso definidos sem ambiguidade | Concluído | [`Grafo.java`](../src/main/java/com/rotavital/estruturas/Grafo.java) e [`GrafoTest.java`](../src/test/java/com/rotavital/estruturas/GrafoTest.java) |
| Escolha entre lista e matriz justificada pela densidade | Concluído | Justificado formalmente neste documento e medido em memória |
| Algoritmo escolhido e justificado | Concluído | [`Dijkstra.java`](../src/main/java/com/rotavital/estruturas/Dijkstra.java) e [`DijkstraTest.java`](../src/test/java/com/rotavital/estruturas/DijkstraTest.java) |
| Exemplo pequeno (5 unidades) com caminho calculado à mão | Concluído | Validado matematicamente e testado em [`DijkstraTest.java`](../src/test/java/com/rotavital/estruturas/DijkstraTest.java) |
| Rejeição de pesos negativos na inserção | Concluído | [`GrafoTest.java#rejeitaPesoNegativo`](../src/test/java/com/rotavital/estruturas/GrafoTest.java) |
| Casos de grafo desconexo / sem caminho | Concluído | [`DijkstraCasosSemSolucaoTest.java`](../src/test/java/com/rotavital/estruturas/DijkstraCasosSemSolucaoTest.java) |

## Dependências

- **PI3-12** — representação da unidade da rede (concluído em [`Local`](../src/main/java/com/rotavital/dominio/Local.java), [`Hemocentro`](../src/main/java/com/rotavital/dominio/Hemocentro.java), [`Hospital`](../src/main/java/com/rotavital/dominio/Hospital.java)).

## Fora de escopo na W02 vs. Situação Atual

- **Implementação do algoritmo (PI3-17 e PI3-19):** Na época da W02, a codificação das estruturas estava fora de escopo. Nas semanas seguintes (**W04 a W08**), o algoritmo foi integralmente implementado no pacote `com.rotavital.estruturas` e integrado ao fluxo de negócio em `com.rotavital.alocacao`, acompanhado de 100% de aprovação nos testes automatizados.

---

## Da modelagem à execução (PI3-57 a PI3-61)

As decisões acima foram implementadas e integradas: o [`ServicoAlocacaoRota`](../src/main/java/com/rotavital/alocacao/ServicoAlocacaoRota.java) (pacote `com.rotavital.alocacao`) transforma uma requisição em bolsa + rota em uma única chamada — levanta as candidatas por unidade, roda o Dijkstra a partir da solicitante, descarta as inalcançáveis, escolhe por FEFO e devolve a bolsa com o caminho e o tempo total (PI3-58). Esta seção registra as regras que completam a modelagem.

### Regra de desempate

| Onde | Regra | Por quê |
|---|---|---|
| Seleção FEFO | 1º menor `dataValidade`; 2º menor `codigoRastreio` | O critério secundário torna o resultado determinístico: duas bolsas com a mesma validade sempre resolvem para a mesma escolha, em qualquer execução e em qualquer ordem de montagem do estoque (verificado em 100 execuções no `CriteriosAceiteAlocacaoTest`) |
| Geração da malha | vizinhos por menor distância; empate por menor `id` | Mantém a malha reproduzível: as arestas geradas são sempre as mesmas |

A regra FEFO vive em um único lugar — a [`FilaPrioridadeFefo`](../src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java) — e é reusada pela `SelecaoFefo` e pelo `ServicoAlocacaoRota`, para que as ordens nunca divirjam.

### Motivos de falha (PI3-57)

Quando não há bolsa para entregar, a resposta carrega um motivo distinguível ([`MotivoFalhaAlocacao`](../src/main/java/com/rotavital/alocacao/MotivoFalhaAlocacao.java)) em vez de exceção. Os motivos são avaliados nesta ordem, e vale o primeiro que ocorrer:

| Motivo | Significado |
|---|---|
| `SEM_ESTOQUE` | nenhuma unidade tem bolsa da combinação grupo + componente, nem sequer vencida |
| `TODAS_VENCIDAS` | existem bolsas da combinação, mas nenhuma alocável na data de referência (vencidas ou fora do status `DISPONIVEL`) |
| `SEM_CAMINHO` | existem bolsas alocáveis, mas todas em unidades sem caminho a partir da solicitante (inclui solicitante fora da malha) |

A ordem importa: estoque → validade → alcance. Uma bolsa vencida em unidade alcançável e uma válida em unidade isolada resultam em `SEM_CAMINHO`, porque bolsa alocável existe — o que barra é o caminho.

### Complexidade da chamada integrada

Para B bolsas no estoque, V unidades e E arestas:

| Passo | Custo |
|---|---:|
| Levantar e filtrar candidatas ([`IndiceEstoque`](../src/main/java/com/rotavital/estruturas/IndiceEstoque.java)) | O(B) |
| Dijkstra (heap binário) + reconstrução da rota ([`Dijkstra`](../src/main/java/com/rotavital/estruturas/Dijkstra.java)) | O((V + E) log V) |
| Fila FEFO das alcançáveis ([`FilaPrioridadeFefo`](../src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java)) | O(B log B) pior caso |
| **Total por chamada** | **O(B log B + (V + E) log V)** |

### Tempos medidos (PI3-60)

Medição sobre a malha real das 12 unidades com a massa da carga inicial (100 bolsas, semente 42), média de 200 execuções após 50 de aquecimento descartadas, origem e combinação girando a cada execução ([`MedicaoDesempenhoTest`](../src/test/java/com/rotavital/alocacao/MedicaoDesempenhoTest.java)):

| Operação | Média por chamada |
|---|---:|
| Dijkstra (`calcularDistancias`) | ~11,5 µs |
| Alocação completa (`alocar`: candidatas + Dijkstra + filtro + FEFO + rota) | ~17,1 µs |

Para reproduzir localmente:
```bash
./mvnw test -Dtest=MedicaoDesempenhoTest
```

### Demonstração ponta a ponta (PI3-61)

```bash
./mvnw test -Dtest=DemoPontaAPontaTest
```

A demo carrega a malha real, monta um estoque de exemplo e mostra as quatro saídas possíveis — a alocação com bolsa, caminho e tempo, e os três motivos de falha. Saída de uma execução real:

```text
Solicitante: HC06 (Hospital das Clínicas UFPE)

1) O- hemacias  -> bolsa BOLDEMO02 (validade 2026-09-19) em HC08 (Hemolab Laboratório)
   caminho: HC06 (Hospital das Clínicas UFPE) -> HC01 (Hemocentro Recife (HEMOPE)) -> HC08 (Hemolab Laboratório) | tempo total: 57.5 min
2) AB- crio     -> SEM_ESTOQUE (nenhuma bolsa da combinacao na rede)
3) A+ plaquetas -> TODAS_VENCIDAS (existe bolsa, mas nenhuma alocavel)
4) B- plasma    -> SEM_CAMINHO (bolsa valida, porem em unidade sem ligacao)
```

Os critérios de aceite da integração têm um teste cada no [`CriteriosAceiteAlocacaoTest`](../src/test/java/com/rotavital/alocacao/CriteriosAceiteAlocacaoTest.java) (PI3-59): o caminho de 23 minutos deste documento, FEFO vencendo proximidade, vencida ignorada, empate estável em 100 execuções, sem caminho e sem estoque.
