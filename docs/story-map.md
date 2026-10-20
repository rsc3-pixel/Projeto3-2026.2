# Story map — Rota Vital

Mapa de histórias do projeto, organizado pelo caminho que o sangue percorre:
da coleta no hemocentro até a entrega no hospital.

Entregável do **E2 · TDD** do desafio de Infraestrutura de Software.

---

## Backbone

A espinha dorsal são as cinco etapas da operação, na ordem em que acontecem.
Cada uma responde a uma pergunta que alguém faz no dia a dia da rede:

| | Etapa | A pergunta que responde |
|---|---|---|
| 1 | **Receber** | o que entrou no estoque, e até quando vale? |
| 2 | **Solicitar** | o hospital precisa de quê, e com que urgência? |
| 3 | **Alocar** | qual bolsa separar para este pedido? |
| 4 | **Transportar** | como chega lá, e a cadeia fria aguentou? |
| 5 | **Acompanhar** | onde vai faltar sangue antes de virar desabastecimento? |

```
RECEBER ──── SOLICITAR ──── ALOCAR ──── TRANSPORTAR ──── ACOMPANHAR
   │             │             │             │                │
 HU-01         HU-02        HU-03         HU-05            HU-06
 cadastrar     requisitar   FEFO          rota             indicadores
   │             │             │             │                │
   │             │          HU-08         HU-07            HU-04
   │             │          ABO/Rh        temperatura      bolsas em risco
```

---

## Release 1 — entregue

Tudo abaixo está implementado, em produção e coberto por testes.

### Receber

**HU-01 — Cadastrar bolsa coletada**

| Tarefa | Teste que nasceu primeiro |
|---|---|
| Calcular a validade a partir da coleta e do componente | `BolsaTest` · validade de plaquetas é 5 dias, de hemácias 42 |
| Recusar data de coleta futura | `BolsaControllerTest` · 400 com o campo apontado |
| Nascer com status `DISPONIVEL`, sem aceitar status do cliente | `BolsaServicoTest` |

*Por que esta regra ganhou teste primeiro:* validade errada significa
transfundir bolsa vencida ou descartar bolsa boa. É a regra onde o erro custa
mais caro.

### Solicitar

**HU-02 — Solicitar hemocomponentes**

| Tarefa | Teste |
|---|---|
| Exigir ao menos um item na requisição | `RequisicaoServicoTest` |
| Recalcular o status conforme os itens são atendidos | `FluxoRequisicaoTest` · PENDENTE → PARCIALMENTE_ATENDIDA → ATENDIDA |
| Impedir alocação em requisição já fechada | `RequisicaoServicoTest` · 409 |

### Alocar

**HU-03 — Alocar bolsa priorizando validade (FEFO)**

| Tarefa | Teste |
|---|---|
| Ordenar por validade crescente | `SelecaoFefoTest` |
| **Filtrar vencidas antes de ordenar** | `SelecaoFefoTest` · a vencida nunca chega ao topo |
| Resolver empate de forma determinística | `SelecaoFefoTest` · desempate pelo código de rastreio |
| Tratar atendimento parcial como sucesso, não falha | `CriteriosAceiteAlocacaoTest` |

*A ordem do filtro é a decisão mais importante do projeto.* O FEFO põe no topo
quem vence primeiro, e uma bolsa vencida vence antes de todas as válidas. Se a
fila fosse montada com todas as candidatas para só depois descartar as
inválidas, a vencida ocuparia o topo em toda seleção.

**HU-08 — Alocar respeitando compatibilidade ABO/Rh**

| Tarefa | Teste |
|---|---|
| Modelar a matriz de compatibilidade | `MatrizCompatibilidadeTest` · 21 testes |
| Tratar a relação como assimétrica | O− doa para A+, mas A+ não doa para O− |
| Inverter a regra para plasma | AB é doador universal de plasma, O− de hemácias |
| Aplicar a compatibilidade antes da ordenação | `CompatibilidadeAlocacaoTest` |

*Por que esta regra ganhou teste primeiro:* um erro aqui põe um paciente em
risco. E o defeito que ela corrigiu era real: a alocação comparava os grupos
por igualdade e recusava doação válida.

### Transportar

**HU-05 — Calcular rota de entrega**

| Tarefa | Teste |
|---|---|
| Caminho mínimo entre origem e destino | `DijkstraTest` |
| Tratar ponto inalcançável sem lançar exceção | `DijkstraTest` · devolve `semCaminho()` |
| Exigir bolsa reservada para embarcar | `RotaServicoTest` · 409 |

**HU-07 — Monitorar temperatura durante o transporte**

| Tarefa | Teste |
|---|---|
| Comparar a leitura com a faixa do componente | `TemperaturaRemessaTest` |
| **Avaliar cada componente embarcado, não a remessa inteira** | 5 °C é normal para hemácias e excursão para plaquetas |
| Recusar leitura fora do plausível para um sensor | 400, porque é defeito de equipamento e não excursão |
| Registrar histórico por remessa | `TemperaturaRemessaTest` |

### Acompanhar

**HU-04 — Ver bolsas em risco de vencimento**
**HU-06 — Acompanhar indicadores da rede**

| Tarefa | Teste |
|---|---|
| Calcular estoque, descarte e cobertura por tipo | `IndicadorServicoTest` |
| Usar desvio-padrão amostral, dividindo por n−1 | confere com o `statistics.stdev` da análise em Python |
| Destacar cobertura abaixo de 100% | `IndicadorControllerTest` |

---

## Release 2 — planejada

| Etapa | História | Estado |
|---|---|---|
| Transportar | Roteirização por vizinho mais próximo | em andamento (PI3-94) |
| Acompanhar | Probabilidade de desabastecimento por tipo | planejada (PI3-102) |
| Acompanhar | Taxa de descarte por vencimento | planejada (PI3-103) |
| Acompanhar | Painel consolidado | planejada (PI3-32) |
| Receber | Ingestão concorrente | planejada (PI3-30) |

---

## O que ficou sem teste, e por quê

Transparência vale mais que cobertura inflada:

| Item | Por que não tem teste |
|---|---|
| `CargaInicial` | dados sintéticos de desenvolvimento; não é regra de negócio |
| Controllers, isoladamente | cobertos pelos testes de integração, que exercitam o caminho real |
| Telemetria contra hardware | a telemetria é simulada nesta fase do projeto |
| Front-ends | ainda não implementados |

---

## Como o TDD foi aplicado

O histórico do Git mostra o padrão `test:` antes de `feat:` nas histórias mais
recentes. Exemplos:

```
test(alocacao): prova o uso do indice no caminho real de alocacao (PI3-131)
test(alocacao): cobre casos de borda da compatibilidade (PI3-130)
test(estruturas): completa a cobertura do roteiro por vizinho mais proximo
```

**Onde o TDD não foi seguido à risca:** nas histórias da Unidade 1, o código
veio primeiro e os testes depois. A disciplina só passou a ser aplicada de
forma consistente a partir da Unidade 2. Registrar isso é mais honesto que
reescrever o histórico.

---

## Onde o teste mudou a implementação

Três casos em que o teste derrubou uma decisão, que é o sinal de que o TDD
estava funcionando:

**Cancelamento de requisição.** A regra escrita proibia cancelar requisição
`ATENDIDA`. O teste mostrou que a trava certa é o status das bolsas: se estão
`RESERVADA`, o cancelamento desfaz; se já saíram em trânsito, não.

**Remessa vazia.** O teste de temperatura sobre remessa sem bolsas falhou, e
revelou que `RotaServico` recusa a transição para `EM_TRANSITO` sem carga. A
regra fazia sentido e o teste foi reescrito para documentá-la.

**Colisão no índice de estoque.** A documentação afirmava que a colisão era
"rara por construção". A medição mostrou o contrário: `Objects.hash` sobre
enum usa o hash de identidade, que varia entre execuções. A chave passou a usar
`ordinal()`, que é determinístico.
