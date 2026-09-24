# Documento de escopo — Modelagem do grafo da rede de distribuição de hemocomponentes

## Contexto do projeto

A rede de distribuição de sangue precisa entregar o componente certo (compatível e dentro da validade), no lugar certo, no tempo certo e na temperatura certa. Este documento define a estrutura de grafo sobre a qual o cálculo de rota vai rodar, para que a plataforma possa alocar bolsas e planejar entregas entre hemocentros e hospitais.

## Objetivo

Definir como a rede de distribuição de hemocomponentes vira um grafo, para que o cálculo de rota tenha uma estrutura sobre a qual rodar.

## Decisões

### 1. Vértice

**Definição:** unidade da rede (hemocentro, banco de sangue regional ou hospital/ponto de entrega).

Cada unidade física da operação é representada por exatamente um vértice. A representação interna da unidade (atributos, identificadores) é tratada em PI3-12; este documento assume que cada unidade já chega com um identificador único a ser usado como chave do vértice.

### 2. Aresta

**Definição:** trecho viário direto entre duas unidades — um caminho que não passa por nenhuma outra unidade da rede no meio do percurso.

Se existe uma rota física entre a unidade A e a unidade B que passa por uma terceira unidade C, isso é modelado como duas arestas (A–C e C–B), não uma aresta única A–B.

### 3. Peso da aresta

**Proposta: tempo estimado de percurso (em minutos).**

Justificativa:
- Hemocomponentes têm validade curta e exigem manutenção de cadeia fria — o que importa minimizar é o tempo até a entrega, não a distância percorrida. Tempo é a métrica que conecta diretamente o cálculo de rota ao risco de descarte por vencimento e ao risco ao paciente.
- Distância pura é um proxy fraco: dois trechos com a mesma distância podem ter tempos de percurso bem diferentes dependendo do tipo de via, trânsito ou restrições para veículos.
- Custo (combustível, pedágio) é uma dimensão de otimização distinta de tempo. Combinar tempo e custo em um único peso escalar exige normalizar unidades diferentes de forma arbitrária, o que enfraquece a justificativa da rota escolhida.
- **Recomendação:** registrar custo como um atributo adicional da aresta (não como peso), permitindo no futuro uma função de otimização multi-objetivo, sem misturar as métricas nesta sprint.

> O peso é o que o algoritmo minimiza. Escolher distância ou tempo muda qual rota o sistema considera melhor — por isso a justificativa importa mais que a escolha, especialmente quando o que está em jogo é a validade de uma bolsa de sangue.

### 4. Grafo dirigido ou não dirigido

**Decisão: dirigido (digrafo).**

Justificativa: o tempo (e o custo) de ida e de volta entre duas unidades não é necessariamente simétrico — vias de mão única, sentido de tráfego, restrições de horário, ou retorno vazio vs. carregado. Um grafo não dirigido assumiria uma simetria que não existe na prática operacional.

### 5. Representação escolhida

**Decisão: lista de adjacência.**

Justificativa pela densidade esperada: a rede de hemocentros e hospitais é esparsa — cada unidade se conecta diretamente a poucas outras unidades vizinhas geograficamente, não a todas as unidades da rede. Para grafos esparsos, a lista de adjacência ocupa O(V + E) de espaço, contra O(V²) da matriz de adjacência. Além disso, o algoritmo de caminho mínimo escolhido (Dijkstra com fila de prioridade) itera sobre os vizinhos diretos de cada vértice, o que a lista de adjacência favorece naturalmente. Isso também está alinhado com a complexidade controlada definida para o projeto (poucos componentes, grafo limitado).

### 6. Algoritmo de caminho mínimo

**Decisão: Dijkstra, com fila de prioridade (heap binário).**

Justificativa: os pesos das arestas (tempo estimado) são sempre não negativos — condição necessária e suficiente para a corretude do Dijkstra. Com heap binário, a complexidade é O((V + E) log V), adequada ao tamanho esperado da rede. Não há necessidade de Bellman-Ford, que resolve o caso de pesos negativos, inexistente neste domínio.

### 7. Janelas de tempo e cadeia fria

**Decisão: registrar como fora de escopo desta sprint, dependência crítica para W06.**

Justificativa: o Dijkstra clássico assume pesos estáticos por aresta. Janela de tempo (chegar dentro de um intervalo) e cadeia fria (não exceder tempo máximo fora de refrigeração) são restrições de viabilidade sobre o caminho — uma camada de filtragem ou penalização aplicada depois (ou durante) o cálculo do caminho mínimo, não um peso simples de aresta. Incorporar isso agora ampliaria o escopo desta sprint sem necessidade.

Vale registrar que, dado o domínio (hemocomponentes), essa não é uma restrição secundária: cadeia fria rompida ou janela de tempo perdida pode inviabilizar a bolsa mesmo que o caminho seja o mais rápido em tempo puro. A modelagem completa dessas restrições (provavelmente como validação pós-Dijkstra ou como penalização de custo) fica marcada como dependência prioritária para a W06, já que se conecta diretamente com a competência de arquitetura de redes e telemetria (monitoramento de temperatura) do projeto.

## Esboço da rede — exemplo pequeno (5 unidades)

Grafo dirigido de exemplo com 5 vértices (Hemocentro, Hospital A, Hospital B, Hospital C, Hospital Destino) e pesos em tempo estimado (minutos):

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

## Critérios de aceite

- [x] Vértice, aresta e peso definidos sem ambiguidade
- [x] Escolha entre lista e matriz justificada pela densidade esperada
- [x] Algoritmo escolhido e justificado
- [x] Exemplo pequeno (5 unidades) com caminho calculado a mão

## Depende de

- PI3-12 — representação da unidade da rede

## Fora de escopo

- Implementação do algoritmo (PI3-17 e PI3-19)

---

## Da modelagem à execução (PI3-57 a PI3-61)

As decisões acima foram implementadas e integradas: o `ServicoAlocacaoRota`
(pacote `com.rotavital.alocacao`) transforma uma requisição em bolsa + rota em
uma única chamada — levanta as candidatas por unidade, roda o Dijkstra a
partir da solicitante, descarta as inalcançáveis, escolhe por FEFO e devolve a
bolsa com o caminho e o tempo total (PI3-58). Esta seção registra as regras
que completam a modelagem.

### Regra de desempate

| Onde | Regra | Por quê |
|---|---|---|
| Seleção FEFO | 1º menor `dataValidade`; 2º menor `codigoRastreio` | O critério secundário torna o resultado determinístico: duas bolsas com a mesma validade sempre resolvem para a mesma escolha, em qualquer execução e em qualquer ordem de montagem do estoque (verificado em 100 execuções no `CriteriosAceiteAlocacaoTest`) |
| Geração da malha | vizinhos por menor distância; empate por menor `id` | Mantém a malha reproduzível: as arestas geradas são sempre as mesmas |

A regra FEFO vive em um único lugar — a `FilaPrioridadeFefo` — e é reusada
pela `SelecaoFefo` e pelo `ServicoAlocacaoRota`, para que as ordens nunca
divirjam.

### Motivos de falha (PI3-57)

Quando não há bolsa para entregar, a resposta carrega um motivo distinguível
(`MotivoFalhaAlocacao`) em vez de exceção. Os motivos são avaliados nesta
ordem, e vale o primeiro que ocorrer:

| Motivo | Significado |
|---|---|
| `SEM_ESTOQUE` | nenhuma unidade tem bolsa da combinação grupo + componente, nem sequer vencida |
| `TODAS_VENCIDAS` | existem bolsas da combinação, mas nenhuma alocável na data de referência (vencidas ou fora do status `DISPONIVEL`) |
| `SEM_CAMINHO` | existem bolsas alocáveis, mas todas em unidades sem caminho a partir da solicitante (inclui solicitante fora da malha) |

A ordem importa: estoque → validade → alcance. Uma bolsa vencida em unidade
alcançável e uma válida em unidade isolada resultam em `SEM_CAMINHO`, porque
bolsa alocável existe — o que barra é o caminho.

### Complexidade da chamada integrada

Para B bolsas no estoque, V unidades e E arestas:

| Passo | Custo |
|---|---:|
| Levantar e filtrar candidatas | O(B) |
| Dijkstra (heap binário) + reconstrução da rota | O((V + E) log V) |
| Fila FEFO das alcançáveis | O(B log B) pior caso |
| **Total por chamada** | **O(B log B + (V + E) log V)** |

### Tempos medidos (PI3-60)

Medição sobre a malha real das 12 unidades com a massa da carga inicial
(100 bolsas, semente 42), média de 200 execuções após 50 de aquecimento
descartadas, origem e combinação girando a cada execução
(`MedicaoDesempenhoTest`):

| Operação | Média por chamada |
|---|---:|
| Dijkstra (`calcularDistancias`) | ~11,5 µs |
| Alocação completa (`alocar`: candidatas + Dijkstra + filtro + FEFO + rota) | ~17,1 µs |

Medido em um MacBook (Apple Silicon, OpenJDK 26); os valores variam por
máquina e servem como ordem de grandeza — microssegundos, folga de sobra para
a operação. Para reproduzir: `./mvnw test -Dtest=MedicaoDesempenhoTest`
(os números saem no log do teste).

### Demonstração ponta a ponta (PI3-61)

```bash
./mvnw test -Dtest=DemoPontaAPontaTest
```

A demo carrega a malha real, monta um estoque de exemplo e mostra as quatro
saídas possíveis — a alocação com bolsa, caminho e tempo, e os três motivos de
falha. Saída de uma execução real:

```
Solicitante: HC06 (Hospital das Clínicas UFPE)

1) O- hemacias  -> bolsa BOLDEMO02 (validade 2026-09-19) em HC08 (Hemolab Laboratório)
   caminho: HC06 (Hospital das Clínicas UFPE) -> HC01 (Hemocentro Recife (HEMOPE)) -> HC08 (Hemolab Laboratório) | tempo total: 57.5 min
2) AB- crio     -> SEM_ESTOQUE (nenhuma bolsa da combinacao na rede)
3) A+ plaquetas -> TODAS_VENCIDAS (existe bolsa, mas nenhuma alocavel)
4) B- plasma    -> SEM_CAMINHO (bolsa valida, porem em unidade sem ligacao)
```

Os critérios de aceite da integração têm um teste cada no
`CriteriosAceiteAlocacaoTest` (PI3-59): o caminho de 23 minutos deste
documento, FEFO vencendo proximidade, vencida ignorada, empate estável em 100
execuções, sem caminho e sem estoque.
