# Rota Vital — Histórias de usuário

Entrega 01 · Projeto Integrador 3P · Equipe E2

As histórias abaixo descrevem o que a plataforma precisa fazer, do ponto de vista
de quem usa. Cada uma traz os cenários de validação em BDD (Behavior Driven
Development), no formato **Dado / Quando / Então**.

Os números citados nos critérios vêm da análise estatística da base sintética
(`dados/analise.py`), documentada em `dados/crisp-dm-briefing.md`.

---

## Perfis de usuário

| Perfil | Quem é | O que faz na plataforma |
|---|---|---|
| **Técnico do hemocentro** | opera o banco de sangue | cadastra bolsas, atende requisições, despacha remessas |
| **Responsável da agência transfusional** | hospital com banco próprio | requisita bolsas, acompanha entrega, também estoca |
| **Coordenador da rede** | gestão | acompanha indicadores de estoque, descarte e cobertura |
| **Motorista** | transporte | executa a rota e registra saída e chegada |

---

## HU-01 — Cadastrar bolsa coletada

> **Como** técnico do hemocentro
> **quero** registrar uma bolsa recém-coletada informando componente, tipo
> sanguíneo e data de coleta
> **para que** ela entre no estoque disponível com a validade correta, sem que eu
> precise calcular prazo na mão.

**Valor de negócio:** cada componente tem validade própria (plaquetas 5 dias,
hemácias 42, plasma 365). Calcular isso manualmente é fonte de erro, e um prazo
errado significa transfundir bolsa vencida ou descartar bolsa boa.

### Cenário 1 — Validade calculada automaticamente

```gherkin
Dado que estou na tela de cadastro de bolsa
E que hoje é 31/08/2026
Quando eu registrar uma bolsa de concentrado de plaquetas coletada hoje
Então a bolsa deve ser criada com data de validade 05/09/2026
E o status deve ser DISPONIVEL
```

### Cenário 2 — Validade varia por componente

```gherkin
Dado que estou na tela de cadastro de bolsa
E que hoje é 31/08/2026
Quando eu registrar uma bolsa de concentrado de hemácias coletada hoje
Então a bolsa deve ser criada com data de validade 12/10/2026
```

### Cenário 3 — Campos obrigatórios

```gherkin
Dado que estou na tela de cadastro de bolsa
Quando eu tentar salvar sem informar o tipo sanguíneo
Então devo ver a mensagem "Informe o tipo sanguíneo"
E a bolsa não deve ser criada
```

**Critérios de aceite**
- [ ] A validade nunca é digitada, sempre calculada a partir da coleta
- [ ] Os quatro componentes têm prazos distintos e corretos
- [ ] Bolsa nova nasce com status DISPONIVEL
- [ ] Entrada inválida devolve mensagem legível, não erro técnico

---

## HU-02 — Solicitar hemocomponentes

> **Como** responsável de agência transfusional
> **quero** abrir uma requisição informando o que preciso e com que urgência
> **para que** o hemocentro saiba o que separar e em quanto tempo.

**Valor de negócio:** uma requisição de emergência e uma de rotina não podem ser
tratadas igual. A prioridade é o que permite ao sistema ordenar o atendimento.

### Cenário 1 — Requisição com múltiplos itens

```gherkin
Dado que sou responsável do Hospital Memorial Jaboatão
Quando eu criar uma requisição URGENTE pedindo
  | componente               | tipo | quantidade |
  | CONCENTRADO_HEMACIAS     | O-   | 2          |
  | PLASMA_FRESCO_CONGELADO  | O-   | 1          |
Então a requisição deve ser criada com status PENDENTE
E deve conter 2 itens somando 3 bolsas
```

### Cenário 2 — Prazo conforme a prioridade

```gherkin
Dado que estou criando uma requisição
Quando eu marcar a prioridade como EMERGENCIA
Então o prazo de atendimento deve ser de 2 horas
Quando eu marcar a prioridade como ROTINA
Então o prazo de atendimento deve ser de 72 horas
```

### Cenário 3 — Requisição precisa ter itens

```gherkin
Dado que estou criando uma requisição
Quando eu tentar salvar sem nenhum item
Então devo ver a mensagem "Informe ao menos um item"
E a requisição não deve ser criada
```

**Critérios de aceite**
- [ ] Requisição aceita múltiplos itens de componentes e tipos diferentes
- [ ] Prioridade define o prazo esperado de atendimento
- [ ] Requisição sem item é rejeitada
- [ ] Status inicial é PENDENTE

---

## HU-03 — Alocar bolsa priorizando validade (FEFO)

> **Como** técnico do hemocentro
> **quero** que o sistema me indique qual bolsa separar para cada item da
> requisição
> **para que** eu use primeiro a que vence antes e reduza o descarte.

**Valor de negócio:** a análise da base sintética mostrou **90,2% das plaquetas
vencendo** num cenário sem gestão de estoque. FEFO (First Expired, First Out) é
a regra que ataca diretamente esse desperdício.

### Cenário 1 — A bolsa que vence primeiro é escolhida

```gherkin
Dado que existem três bolsas O- de hemácias disponíveis
  | código  | vence em |
  | BOL0001 | 30 dias  |
  | BOL0002 | 3 dias   |
  | BOL0003 | 15 dias  |
Quando eu alocar uma bolsa O- de hemácias para uma requisição
Então a bolsa BOL0002 deve ser selecionada
E seu status deve mudar para RESERVADA
```

### Cenário 2 — Bolsa vencida nunca é alocada

```gherkin
Dado que a única bolsa O- de hemácias em estoque venceu ontem
Quando eu tentar alocar uma bolsa O- de hemácias
Então nenhuma bolsa deve ser selecionada
E devo ver a mensagem "Sem estoque disponível para O- hemácias"
```

### Cenário 3 — Atendimento parcial

```gherkin
Dado que uma requisição pede 3 bolsas O- de hemácias
E que existe apenas 1 bolsa O- de hemácias disponível
Quando eu alocar o que houver
Então 1 bolsa deve ser alocada
E o item deve ficar com status PARCIALMENTE_ALOCADO
E a requisição deve ficar com status PARCIALMENTE_ATENDIDA
```

**Critérios de aceite**
- [ ] Entre bolsas equivalentes, sempre sai a de validade mais próxima
- [ ] Bolsa vencida nunca entra na seleção
- [ ] Empate de validade é resolvido de forma determinística
- [ ] Atendimento parcial é registrado, não tratado como falha

---

## HU-04 — Ver bolsas em risco de vencimento

> **Como** técnico do hemocentro
> **quero** ver quais bolsas vencem nos próximos dias, ordenadas pelas mais
> críticas
> **para que** eu possa priorizar o despacho antes que virem descarte.

**Valor de negócio:** a análise identificou **187 bolsas (13,9% do estoque
válido) vencendo em até 7 dias**. Sem visibilidade, elas viram perda silenciosa.

### Cenário 1 — Painel mostra apenas o que está em risco

```gherkin
Dado que o estoque tem bolsas vencendo em 3, 10 e 40 dias
Quando eu abrir o painel de estoque crítico com filtro de 7 dias
Então devo ver apenas a bolsa que vence em 3 dias
```

### Cenário 2 — Ordenação por urgência

```gherkin
Dado que existem bolsas vencendo em 6, 2 e 4 dias
Quando eu abrir o painel de estoque crítico
Então as bolsas devem aparecer na ordem 2, 4 e 6 dias
```

### Cenário 3 — Estoque saudável

```gherkin
Dado que nenhuma bolsa vence nos próximos 7 dias
Quando eu abrir o painel de estoque crítico
Então devo ver a mensagem "Nenhuma bolsa em risco nos próximos 7 dias"
```

**Critérios de aceite**
- [ ] O prazo do filtro é configurável (7, 15, 30 dias)
- [ ] Resultado ordenado da mais crítica para a menos
- [ ] Estado vazio tem mensagem própria, não tabela em branco
- [ ] Bolsas já vencidas aparecem separadas das que ainda podem ser usadas

---

## HU-05 — Calcular rota de entrega

> **Como** técnico do hemocentro
> **quero** que o sistema calcule o caminho mais rápido até o hospital de destino
> **para que** a remessa chegue dentro da janela de tempo e da cadeia fria.

**Valor de negócio:** a análise mostrou **déficit de 350 bolsas em Jaboatão
convivendo com folga de 157 no Recife**. O problema não é só de volume, é de
distribuição: o sangue precisa atravessar a região metropolitana a tempo.

### Cenário 1 — Caminho mais rápido, não o mais curto

```gherkin
Dado que a rede tem os trechos
  | origem      | destino     | tempo |
  | Hemocentro  | Hospital A  | 10min |
  | Hemocentro  | Hospital B  | 20min |
  | Hospital A  | Hospital B  | 5min  |
  | Hospital B  | Destino     | 8min  |
Quando eu calcular a rota do Hemocentro até o Destino
Então a rota deve ser Hemocentro > Hospital A > Hospital B > Destino
E o tempo estimado deve ser de 23 minutos
```

### Cenário 2 — Destino inalcançável

```gherkin
Dado que não existe trecho ligando o Hemocentro ao Hospital X
Quando eu calcular a rota do Hemocentro até o Hospital X
Então devo ver a mensagem "Não há rota disponível para este destino"
```

### Cenário 3 — Rota registra origem, destino e previsão

```gherkin
Dado que calculei uma rota de 23 minutos
Quando eu confirmar o despacho
Então a rota deve ser criada com status PLANEJADA
E deve conter a previsão de saída e de chegada
E as bolsas embarcadas devem mudar para EM_TRANSITO
```

**Critérios de aceite**
- [ ] O peso considerado é tempo estimado, não distância
- [ ] Caminho calculado confere com verificação manual em rede pequena
- [ ] Ausência de rota devolve resposta tratada, não erro
- [ ] Bolsas embarcadas mudam de status ao despachar

---

## HU-06 — Acompanhar indicadores da rede

> **Como** coordenador da rede
> **quero** ver estoque, descarte e cobertura de demanda por tipo sanguíneo
> **para que** eu identifique onde falta sangue antes que vire desabastecimento.

**Valor de negócio:** a análise revelou que a cobertura varia de **50% (AB−) a
122% (O−)**. Sem esse painel, a falta só aparece quando um pedido não pode ser
atendido.

### Cenário 1 — Estoque por tipo sanguíneo

```gherkin
Dado que o estoque tem 492 bolsas O+ e 5 bolsas AB- disponíveis
Quando eu abrir o painel de estoque
Então devo ver O+ com 492 bolsas
E devo ver AB- com 5 bolsas
E AB- deve estar sinalizado como crítico
```

### Cenário 2 — Cobertura de demanda

```gherkin
Dado que há 5 bolsas AB- disponíveis e 10 bolsas AB- demandadas
Quando eu abrir o painel de cobertura
Então AB- deve mostrar cobertura de 50%
E deve indicar déficit de 5 bolsas
```

### Cenário 3 — Taxa de descarte por componente

```gherkin
Dado que 367 das 407 bolsas de plaquetas venceram
Quando eu abrir o painel de descarte
Então plaquetas devem mostrar taxa de 90,2%
E deve ser o componente com maior taxa da lista
```

**Critérios de aceite**
- [ ] Indicadores calculados a partir do estoque real, não valores fixos
- [ ] Tipos com cobertura abaixo de 100% aparecem destacados
- [ ] Painel mostra medidas de dispersão, não só média
- [ ] Cada indicador exibe a unidade e o período considerado

---

## HU-07 — Monitorar temperatura durante o transporte

> **Como** técnico do hemocentro
> **quero** ser avisado quando a temperatura de uma remessa sair da faixa exigida
> **para que** eu possa agir antes que as bolsas se tornem inviáveis.

**Valor de negócio:** cada componente tem faixa própria (hemácias 1 a 6 °C,
plaquetas 20 a 24 °C, plasma −25 a −18 °C). Excursão de temperatura inviabiliza
a bolsa mesmo dentro da validade.

### Cenário 1 — Leitura dentro da faixa

```gherkin
Dado que uma remessa de hemácias está em trânsito
Quando o sensor registrar 4°C
Então a remessa deve permanecer com status normal
E nenhum alerta deve ser gerado
```

### Cenário 2 — Excursão de temperatura

```gherkin
Dado que uma remessa de hemácias está em trânsito
Quando o sensor registrar 9°C
Então um alerta de cadeia fria deve ser gerado
E a remessa deve ser sinalizada para inspeção na chegada
```

### Cenário 3 — Faixa varia por componente

```gherkin
Dado que uma remessa de plaquetas está em trânsito
Quando o sensor registrar 22°C
Então nenhum alerta deve ser gerado
Dado que uma remessa de hemácias está em trânsito
Quando o sensor registrar 22°C
Então um alerta de cadeia fria deve ser gerado
```

### Cenário 4 — Remessa com componentes de faixas diferentes

```gherkin
Dado que uma remessa transporta hemácias e plaquetas juntas
Quando o sensor registrar 5°C
Então um alerta deve ser gerado para as plaquetas
E nenhum alerta deve ser gerado para as hemácias
```

**Critérios de aceite**
- [ ] A faixa aceita é a do componente transportado, não um valor único
- [ ] Leitura fora da faixa gera alerta imediato
- [ ] Histórico de leituras fica registrado por remessa
- [ ] Remessa com componentes de faixas diferentes avalia cada um
- [ ] A telemetria é simulada nesta fase do projeto

**Faixas por componente**

| Componente | Faixa aceita |
|---|---|
| Hemácias | 1 a 6 °C |
| Plaquetas | 20 a 24 °C |
| Plasma | −25 a −18 °C |

> O cenário 4 é o caso que uma implementação apressada erra: a rota já carrega
> `List<Bolsa>`, e cada bolsa tem seu componente. Uma única faixa por remessa
> não resolve, porque 5 °C é correto para hemácias e excursão para plaquetas.

---

## HU-08 — Alocar respeitando compatibilidade ABO/Rh

> **Como** técnico do hemocentro
> **quero** que o sistema só me ofereça bolsas compatíveis com o grupo sanguíneo
> do paciente
> **para que** eu não dependa de consultar a tabela de compatibilidade de cabeça
> a cada atendimento.

**Valor de negócio:** hoje a alocação compara o grupo sanguíneo por igualdade, o
que recusa doação válida. Um paciente A+ pode receber de O−, A−, O+ e A+, mas o
sistema só oferece A+. Isso deixa estoque utilizável parado e faz o atendimento
parecer impossível quando não é.

A compatibilidade também não é simétrica: O− doa para todos, mas só recebe de
O−. Tratar a relação como igualdade erra nos dois sentidos.

### Cenário 1 — Doador universal é aceito

```gherkin
Dado que há uma requisição de hemácias para um paciente A+
E o estoque tem apenas bolsas O− dentro da validade
Quando eu solicitar a alocação
Então a bolsa O− deve ser oferecida
E a alocação deve ser registrada
```

### Cenário 2 — Bolsa incompatível nunca aparece

```gherkin
Dado que há uma requisição de hemácias para um paciente O−
E o estoque tem apenas bolsas A+ dentro da validade
Quando eu solicitar a alocação
Então nenhuma bolsa deve ser oferecida
E a requisição deve ser registrada como não atendida
```

### Cenário 3 — Compatibilidade antes da validade

```gherkin
Dado que há uma requisição de hemácias para um paciente B+
E o estoque tem uma bolsa AB+ que vence amanhã
E uma bolsa O− que vence em trinta dias
Quando eu solicitar a alocação
Então a bolsa O− deve ser oferecida
E a bolsa AB+ não deve aparecer, por ser incompatível
```

### Cenário 4 — Entre compatíveis, vale o FEFO

```gherkin
Dado que há uma requisição de hemácias para um paciente AB+
E o estoque tem uma bolsa A+ que vence em dez dias
E uma bolsa B+ que vence em trinta dias
Quando eu solicitar a alocação
Então a bolsa A+ deve ser oferecida primeiro
```

**Critérios de aceite**
- [ ] Bolsa compatível de grupo diferente é oferecida, não apenas a de grupo
      idêntico
- [ ] Bolsa incompatível nunca entra na seleção, mesmo sendo a única disponível
- [ ] A compatibilidade é aplicada **antes** da ordenação por validade
- [ ] Entre bolsas compatíveis, a regra FEFO continua valendo
- [ ] A relação não é tratada como simétrica
- [ ] A compatibilidade é didática e não substitui protocolo clínico

> O terceiro critério importa pelo mesmo motivo que o filtro de vencidas vem
> antes da ordenação em `SelecaoFefo`: o FEFO põe no topo quem vence primeiro, e
> uma bolsa incompatível que vença logo ocuparia o topo de toda seleção.

---

## Rastreabilidade

| História | Stories do Jira | Endpoints do contrato |
|---|---|---|
| HU-01 | PI3-12, PI3-18 | `POST /api/v1/bolsas` |
| HU-02 | PI3-18 | `POST /api/v1/requisicoes` |
| HU-03 | PI3-17, PI3-19, PI3-29 | `POST /requisicoes/{id}/itens/{itemId}/alocacoes` |
| HU-04 | PI3-19, PI3-20 | `GET /api/v1/bolsas?sort=dataValidade,asc` |
| HU-05 | PI3-13, PI3-17, PI3-19 | `POST /api/v1/rotas` |
| HU-06 | PI3-20, PI3-32 | `GET /api/v1/indicadores/*` |
| HU-07 | PI3-16, PI3-30, PI3-84 | telemetria (contrato a definir) |
| HU-08 | PI3-29, PI3-84 | `POST /requisicoes/{id}/itens/{itemId}/alocacoes` |

---

## Restrições que valem para todas as histórias

- Apenas dados sintéticos, sem informação real de doador ou paciente (LGPD)
- Telemetria de temperatura e GPS é simulada
- A compatibilidade ABO/Rh implementada é didática e não substitui protocolo
  clínico
