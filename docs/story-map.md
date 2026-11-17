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

| Tarefa | Teste |
|---|---|
| Calcular a validade a partir da coleta e do componente | `CenariosAceitacaoTest.CadastrarBolsa` · plaquetas 5 dias, hemácias 42 |
| Recusar entrada inválida com o campo apontado | `CenariosAceitacaoTest.CadastrarBolsa.camposObrigatorios` · 400 com `campos.grupoSanguineo` |
| Recusar bolsa que já nasceria vencida | `CenariosAceitacaoTest.AlocarPorValidade.bolsaVencidaNemChegaAoEstoque` · 409 |
| Nascer com status `DISPONIVEL`, sem aceitar status do cliente | `CenariosAceitacaoTest.CadastrarBolsa.validadeCalculadaPeloComponente`; o `BolsaRequest` não tem campo de status |

*Por que esta regra ganhou teste primeiro:* validade errada significa
transfundir bolsa vencida ou descartar bolsa boa. É a regra onde o erro custa
mais caro.

### Solicitar

**HU-02 — Solicitar hemocomponentes**

| Tarefa | Teste |
|---|---|
| Exigir ao menos um item na requisição | `FluxoRequisicaoTest.recusaRequisicaoSemItens` · 400 |
| Aceitar vários itens de componentes e tipos diferentes | `CenariosAceitacaoTest.SolicitarHemocomponentes.requisicaoComMultiplosItens` |
| Derivar o prazo da prioridade quando o hospital não informa (E04) | `CenariosAceitacaoTest.SolicitarHemocomponentes.prazoConformeAPrioridade` · 2 h, 12 h, 72 h |
| Recalcular o status conforme os itens são atendidos | `FluxoRequisicaoTest` · PENDENTE → PARCIALMENTE_ATENDIDA → ATENDIDA |
| Impedir alocação em requisição já fechada | `FluxoRequisicaoBolsaRotaTest` · 409 na terceira bolsa de um pedido de duas |

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
| Caminho mínimo entre origem e destino, por tempo | `DijkstraTest`; `CenariosAceitacaoTest.CalcularRota.caminhoMaisRapidoNaoOMaisCurto` · 23 min |
| Tratar ponto inalcançável sem lançar exceção | `DijkstraTest` · devolve `semCaminho()`; `CalculoRotaTest.destinoInalcancavel` · 200 com mensagem |
| Montar a malha com as unidades do banco e alcance máximo (E04) | `CarregadorMalhaTest.unidadeAlemDoAlcanceMaximoFicaIsolada` |
| Calcular a previsão de chegada ao planejar a rota (E04) | `CalculoRotaTest.PlanejarComPrevisaoCalculada` |
| Exigir bolsa reservada para embarcar | `RotaControllerTest.naoEmbarcaBolsaDisponivel` · 409 |

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
| Painel de estoque crítico com janela configurável (E04) | `BolsasEmRiscoTest.JanelaConfiguravel` · 7, 15, 30 |
| Ordenar da mais crítica para a menos; vencidas à parte (E04) | `BolsasEmRiscoTest.OrdenacaoPorUrgencia`, `VencidasSeparadas` |
| Estado vazio com mensagem própria (E04) | `BolsasEmRiscoTest.EstadoVazio` |

---

## Release 2 — entregue na Unidade 2

| Etapa | História | Estado |
|---|---|---|
| Transportar | Roteirização por vizinho mais próximo | entregue (PI3-149, PI3-150) |
| Transportar | Cálculo de rota e previsão de chegada pela API (HU-05) | entregue na E04 (PI3-90) |
| Acompanhar | Probabilidade de desabastecimento por tipo | entregue (PI3-102) |
| Acompanhar | Taxa de descarte por vencimento | entregue (PI3-103) |
| Acompanhar | Painel consolidado | entregue (PI3-32) |
| Acompanhar | Bolsas em risco com janela configurável (HU-04) | entregue na E04 (PI3-90) |
| Receber | Ingestão concorrente | entregue (PI3-30) |
| Solicitar | Prazo derivado da prioridade (HU-02) | entregue na E04 (PI3-90) |

O que **não** entrou, e por quê, está na seção "O que ficou em aberto" da
Entrega 04 no README.

---

## O que ficou sem teste, e por quê

Transparência vale mais que cobertura inflada:

| Item | Por que não tem teste |
|---|---|
| `CargaInicial` | dados sintéticos de desenvolvimento; não é regra de negócio |
| Controllers, isoladamente | cobertos pelos testes de integração, que exercitam o caminho real |
| Telemetria contra hardware | a telemetria é simulada nesta fase do projeto |
| Telas Thymeleaf | só exibem o que os serviços calculam; a regra que elas mostram (fila FEFO, estoque crítico) tem teste no serviço e na API |
| Front-end Angular | tem os próprios testes (Vitest) em `frontend/angular`, fora da suíte Java |
| `Veiculo` | modelado e não integrado: nenhum fluxo o usa ainda |

---

## Como o TDD foi aplicado

Os testes de cada história entram no mesmo PR que a funcionalidade, e em
quatro casos o teste mudou a implementação (seção seguinte). A verificação
commit a commit, registrada em [tdd-no-projeto.md](tdd-no-projeto.md), mostra
que **a ordem dos commits não prova a ordem do trabalho**: nos exemplos abaixo
o commit de teste é filho do commit de funcionalidade, às vezes criado no
mesmo segundo.

```
test(alocacao): prova o uso do indice no caminho real de alocacao (PI3-131)
test(alocacao): cobre casos de borda da compatibilidade (PI3-130)
test(estruturas): completa a cobertura do roteiro por vizinho mais proximo
```

A evidência de "vermelho antes do verde" está nas mensagens de commit, nos
comentários dos testes e na reprodução descrita no documento de TDD, não no
`git log`.

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
