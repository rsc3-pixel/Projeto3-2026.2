# Rota Vital — CRISP-DM aplicado ao recorte estatístico

Documento de apoio ao pitch de 29/08/2026 (Estatística e Probabilidade).
Responde às perguntas norteadoras de cada fase do ciclo.

Todos os números vêm de `analise.py`, executado sobre a base gerada por
`gerar_bolsas.py` e `gerar_requisicoes.py` com semente fixa (2026).

---

## Fase 1 — Entendimento do negócio

**Qual é o problema de negócio?**

A rede de distribuição de hemocomponentes precisa entregar o componente certo,
compatível e dentro da validade, no lugar certo, no tempo certo e na temperatura
certa. Quando isso falha, dois problemas acontecem ao mesmo tempo: hospital sem
sangue disponível e hemocentro descartando bolsa vencida.

**Por que é difícil?**

Três restrições simultâneas que não existem em estoque comum:

- **Validade curta e desigual:** plaquetas duram 5 dias, hemácias 42, plasma 365.
  Um único estoque com prazos que variam em duas ordens de grandeza.
- **Cadeia fria:** cada componente exige faixa de temperatura própria (hemácias
  1 a 6 °C, plaquetas 20 a 24 °C, plasma −25 a −18 °C). Excursão de temperatura
  inviabiliza a bolsa.
- **Compatibilidade ABO/Rh:** nem toda bolsa serve para todo paciente.

**Qual o objetivo do projeto?**

Uma plataforma que gerencia estoque, aloca bolsas compatíveis priorizando
validade (FEFO) e calcula rotas respeitando janela de tempo e cadeia fria.

**Qual o objetivo específico da análise estatística?**

Responder, antes de implementar: onde está o desperdício, onde está a falta, e
se as duas coisas acontecem no mesmo lugar. Sem essa resposta, o algoritmo seria
otimizado no escuro.

**Critério de sucesso**

Do lado do negócio: reduzir descarte por vencimento e aumentar cobertura de
demanda. Do lado da análise: produzir indicadores que justifiquem, com número,
as decisões de projeto (FEFO, compatibilidade, roteirização).

---

## Fase 2 — Entendimento dos dados

**Que dados existem?**

Nenhum dado real. A LGPD e a natureza sensível de informação de doador e
paciente impedem usar base real, e isso está declarado como restrição do projeto
desde o README.

**Como isso foi resolvido?**

Base sintética gerada por script, com premissas explícitas e documentadas em
`premissas.py`:

| Dimensão | Valor | Origem da premissa |
|---|---|---|
| Tipos sanguíneos | O+ 36%, A+ 34%, O− 9%, A− 8%, B+ 8%, AB+ 2,5%, B− 2%, AB− 0,5% | Frequência aproximada na população brasileira |
| Componentes | Hemácias 55%, plaquetas 20%, plasma 20%, crio 5% | Proporção típica de coleta e transfusão |
| Validade | 42 / 5 / 365 / 365 dias | Mesma do domínio (`TipoHemocomponente.java`) |
| Prioridade | Rotina 65%, urgente 28%, emergência 7% | Predominância de pedido programado |
| Janela | 60 dias de coleta | Garante bolsas em todos os estágios de validade |

**Volume gerado**

2.000 bolsas, 300 requisições (1.487 bolsas demandadas), 12 unidades reais da
Região Metropolitana do Recife: 4 hemocentros e 8 agências transfusionais em
Recife, Olinda e Jaboatão dos Guararapes.

**Por que dado sintético ainda ensina?**

Porque as premissas são declaradas e os resultados emergem delas, em vez de
serem escolhidos. Não decidimos a taxa de descarte: ela sai do encontro entre a
validade de cada componente e a distribuição das datas de coleta. Se tivéssemos
sorteado os oito tipos sanguíneos com igual probabilidade, a escassez de tipos
raros simplesmente não apareceria, e a conclusão seria falsa.

**Validação do gerador**

Percentuais gerados vs. premissa declarada: O+ 37,4% (36%), A+ 32,6% (34%),
O− 9,2% (9%), AB− 0,3% (0,5%). Os desvios são a variação aleatória esperada em
2.000 sorteios.

**Semente fixa (2026)**

Qualquer pessoa do time roda os scripts e obtém exatamente os mesmos números.
Sem isso, os valores do slide não bateriam com os do relatório.

### Achados

**1. O desperdício tem endereço**

| Componente | Total | Vencidas | Taxa |
|---|---:|---:|---:|
| Concentrado de plaquetas | 407 | 367 | 90,2% |
| Concentrado de hemácias | 1.128 | 288 | 25,5% |
| Plasma fresco congelado | 369 | 0 | 0% |
| Crioprecipitado | 96 | 0 | 0% |
| **Geral** | **2.000** | **655** | **32,8%** |

Plaqueta vale 5 dias; numa janela de 60 dias sem gestão de estoque, quase toda
plaqueta coletada vence. Plasma vale um ano, nenhuma vence no período.

Ressalva importante: 32,8% de descarte geral não é a realidade de um hemocentro,
que gira o estoque continuamente. É o cenário sem gestão, que é justamente o que
a plataforma existe para evitar.

**2. A média esconde o risco**

Dias até o vencimento, considerando apenas as 1.345 bolsas válidas:

- média 129,2 dias
- mediana 32,0 dias
- desvio padrão 150,7 dias
- faixa de 0 a 365 dias

O desvio padrão maior que a própria média indica distribuição fortemente
assimétrica. O plasma (335 dias em média) puxa a média para cima e esconde o que
importa: metade do estoque válido vence em 32 dias ou menos.

Por componente:

| Componente | n | Média | Mediana | Desvio |
|---|---:|---:|---:|---:|
| Concentrado de plaquetas | 40 | 2,3 | 3,0 | 1,7 |
| Concentrado de hemácias | 840 | 21,0 | 21,0 | 12,3 |
| Plasma fresco congelado | 369 | 335,1 | 335,0 | 17,4 |
| Crioprecipitado | 96 | 336,5 | 337,5 | 18,1 |

**187 bolsas (13,9% do estoque válido) vencem em até 7 dias.** É o número
acionável: são elas que o FEFO precisa despachar primeiro.

**3. A folga de O− é ilusória**

Cobertura = estoque válido dividido pela demanda, por tipo sanguíneo:

| Tipo | Disponível | Demandado | Cobertura | Déficit |
|---|---:|---:|---:|---:|
| AB− | 5 | 10 | 50,0% | 5 |
| AB+ | 30 | 44 | 68,2% | 14 |
| A+ | 454 | 549 | 82,7% | 95 |
| B+ | 105 | 118 | 89,0% | 13 |
| A− | 110 | 122 | 90,2% | 12 |
| O+ | 492 | 518 | 95,0% | 26 |
| B− | 30 | 29 | 103,4% | 0 |
| O− | 119 | 97 | 122,7% | 0 |

Os tipos raros faltam, como se espera. Mas O− aparece com 122% de cobertura, e
esse é o achado mais interessante: a folga só existe porque a análise trata cada
tipo isoladamente. O− é doador universal. Quando a regra de compatibilidade
ABO/Rh entrar (PI3-29), esse excedente será consumido para cobrir AB−, B− e os
demais.

É a análise estatística justificando a ordem de implementação: não se pode
concluir que há sobra antes de aplicar compatibilidade.

**4. O problema é de distribuição, não só de volume**

| Cidade | Estoque | Demanda | Saldo |
|---|---:|---:|---:|
| Recife | 684 | 527 | +157 |
| Olinda | 217 | 166 | +51 |
| Jaboatão dos Guararapes | 444 | 794 | −350 |

Estoque concentrado no Recife (onde estão os hemocentros), demanda concentrada
em Jaboatão (onde estão as agências transfusionais). Não basta existir sangue na
rede: ele precisa atravessar a região metropolitana dentro da janela de tempo e
da cadeia fria.

**Achado sobre a modelagem do domínio**

Ao cadastrar unidades reais, apareceu um tipo que o modelo atual não representa:
a **agência transfusional**, hospital com banco de sangue próprio. Ela requisita
e estoca ao mesmo tempo. O domínio hoje tem apenas `HEMOCENTRO` (estoca) e
`HOSPITAL` (requisita). É um ajuste a levar para a próxima revisão do modelo.

---

## Fase 3 — Preparação dos dados

**O que foi preparado?**

- `premissas.py` reúne todas as distribuições e volumes num arquivo só, para que
  as premissas fiquem explícitas e possam ser questionadas
- `unidades.py` cadastra as 12 unidades com coordenadas geográficas reais
- `gerar_bolsas.py` e `gerar_requisicoes.py` produzem a base
- `analise.py` calcula os indicadores
- `resultados.json` exporta os números para a apresentação

**Decisões de preparação**

- Sementes diferentes para bolsas (2026) e requisições (2027): usar a mesma
  criaria correlação artificial entre o que existe e o que é pedido
- Bolsas vencidas permanecem na base em vez de serem filtradas na geração; a
  filtragem acontece na análise. Isso permite medir descarte
- Volume calibrado em 2.000 bolsas depois de uma primeira rodada com 800, em que
  a cobertura ficou uniforme em todos os tipos (entre 29% e 48%). A uniformidade
  denunciava artefato do gerador, não fenômeno: a demanda havia sido construída
  maior que a oferta. Com oferta e demanda na mesma ordem de grandeza, a
  cobertura passou a variar por tipo

**Limitações declaradas**

- Coletas distribuídas uniformemente na janela; na prática há sazonalidade
  (campanhas, feriados, Carnaval)
- Demanda independente do estoque; na prática hospital ajusta pedido ao que sabe
  estar disponível
- Sem simulação de tempo: a base é um retrato, não uma série temporal

---

## Fase 4 — Modelagem

**Status de Execução:** Executada e consolidada em C++ (semana 12 / PI3-32).
Consulte a documentação completa em [`docs/modelagem_demanda_desabastecimento_cpp.md`](../docs/modelagem_demanda_desabastecimento_cpp.md)
e o código-fonte em [`cpp/`](../cpp/).

**Por que não é modelagem preditiva**

Com base sintética, um modelo de machine learning treinado sobre esses dados
aprenderia de volta as premissas que nós mesmos declaramos. O resultado teria
alta acurácia e nenhum valor: estaríamos medindo a capacidade do modelo de
recuperar a distribuição que escrevemos em `premissas.py`.

**O que foi modelado em C++ (Fase 4 - PI3-32)**

Probabilidade e simulação estocástica:
- **Distribuição de Poisson $D \sim \text{Poisson}(\lambda)$ com convergência Normal (TCL)** para a demanda por tipo sanguíneo e hemocomponente.
- **Probabilidade de desabastecimento por tipo sanguíneo e hemocomponente**: $P(\text{Desabastecimento}) = 1 - F_D(S_{\text{válido}})$, confrontando a demanda contra bolsas físicas válidas (excluindo vencidas).
- **Simulação de Monte Carlo ($100.000$ iterações)** validando os cálculos analíticos de Poisson e Normal.
- **Exportação para o Painel Consolidado**: arquivo `dados/painel_consolidado_pi3_32.json` integrando os conceitos de **Dados Acumulados (POO)** e **Saídas (AED)**.

---

## Fase 5 — Avaliação

**Como saber se funcionou?**

Prevista para a semana 14. Métricas definidas:

| Métrica | Como medir |
|---|---|
| Descarte evitado | Taxa de vencimento com FEFO vs. sem FEFO, mesma base |
| Cobertura atendida | % de itens de requisição atendidos, com e sem compatibilidade |
| Tempo de atendimento | Diferença entre criação da requisição e entrega, por prioridade |
| Cumprimento de prazo | % de emergências atendidas em até 2h, urgentes em 12h |

**Baseline**

Os quatro achados desta fase 2 são o baseline: 32,8% de descarte, cobertura de
50% a 122% por tipo, déficit de 350 bolsas em Jaboatão. A avaliação compara o
sistema contra esses números.

---

## Fase 6 — Implantação

**Como o resultado chega ao usuário?**

Prevista para a semana 16. Painéis na própria aplicação:

- Estoque por tipo, componente e unidade
- Bolsas por faixa de validade, com destaque para as que vencem em 7 dias
- Cobertura de demanda por tipo
- Monitoramento de temperatura (telemetria simulada)

**Arquitetura**

Back-end Java com Spring Boot, front-end React, PostgreSQL. Os indicadores são
expostos pela API em `/api/v1/indicadores` (contrato definido no PI3-14) e
consumidos pelos painéis.

---

## Restrições que valem para todo o ciclo

- Apenas dados sintéticos, nenhuma informação real de doador ou paciente (LGPD)
- Telemetria de temperatura e GPS simulada
- Compatibilidade ABO/Rh didática, não substitui protocolo clínico
- Percentuais populacionais são valores aproximados de referência; para uso além
  do didático, citar fonte primária (Ministério da Saúde / Hemobrás)

---

## Como reproduzir

```bash
cd dados
python analise.py
```

Saída idêntica em qualquer máquina, por causa da semente fixa.
