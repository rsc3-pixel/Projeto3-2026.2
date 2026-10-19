# EST U2 — Interpretação e decisão: desabastecimento e descarte

**Rota Vital · Estatística e Probabilidade · 19/10/2026**

**Rubrica:** Interpretação e decisão (30%).

## Síntese para decisão

**Todos os dados deste relatório são sintéticos.** Não há registros de pacientes, doadores ou operação real. Os resultados descrevem um experimento didático do Rota Vital e não estabelecem protocolos clínicos ou metas reais de abastecimento.

A principal prioridade de estoque identificada é o concentrado de plaquetas: o estoque válido é pequeno diante da demanda, e todas as bolsas ainda válidas estão próximas do vencimento. Comprar ou acumular mais bolsas, isoladamente, não resolve essa combinação. A decisão proposta é testar reposições menores e mais frequentes, acompanhadas de saída por validade entre bolsas elegíveis.

Nas hemácias, a disponibilidade agregada esconde diferenças entre tipos sanguíneos. A− apresenta maior frequência de déficit que A+, mesmo com menor volume demandado. A gestão deve combinar probabilidade de falta, quantidade em falta e prioridade da requisição; a média da rede não basta para definir reposição.

Plasma e crioprecipitado não vencem na janela simulada, mas ainda apresentam cenários de déficit de demanda. Portanto, baixo risco de vencimento não significa estoque suficiente. As decisões de reposição e de prevenção de perdas precisam de indicadores distintos.

## Origem dos resultados e método

O repositório continha a [análise descritiva da U1](evidencias-unidade-1-est.md) e o [planejamento probabilístico no CRISP-DM](../dados/crisp-dm-briefing.md), mas não uma execução probabilística consolidada. Para fundamentar esta interpretação, foi produzida uma **análise complementar**, disponível em [probabilidades_est_u2.py](../dados/probabilidades_est_u2.py), com resultados completos em [JSON](../dados/resultados-probabilidades-est-u2.json). Não se atribuem esses novos resultados à entrega anterior.

### Desabastecimento: evento e denominador

São usadas as 2.000 bolsas e as 300 requisições dos geradores existentes, com referência fixa em **19/10/2026**. As datas são sorteadas de 20/08/2026 a 19/10/2026, inclusive, a partir de deslocamentos inteiros de 0 a 60 dias. As sementes são 2026 para bolsas e 2027 para requisições.

Em cada um dos **5.000 cenários**, sorteiam-se **300 requisições inteiras com reposição** da base sintética. Uma requisição pode aparecer várias vezes ou nenhuma vez. Seus itens e quantidades permanecem juntos, preservando a composição de cada pedido. A semente dessa reamostragem (*bootstrap*) é 20261019.

O estoque válido permanece fixo. Para cada par tipo sanguíneo × componente, compara-se a demanda reamostrada `D` com o estoque `S`:

- Evento de déficit: **`D > S`**. Igualdade não conta como falta.
- Probabilidade estimada: `cenários com D > S / 5.000`.
- Déficit médio: `soma de máximo(0, D − S) / 5.000`, incluindo os cenários sem falta.

Também são calculados totais por componente para diagnosticar insuficiência de volume. Eles agregam grupos sanguíneos, **não autorizam intercâmbio entre grupos** e podem esconder déficits específicos. O experimento não agrega componentes diferentes como substitutos.

Essas probabilidades são condicionadas ao estoque escolhido e à distribuição empírica de requisições sintéticas. Não estimam a chance de um hospital ficar sem sangue amanhã: não há fluxo diário, consumo sequencial, novos vencimentos, reposição, compatibilidade, rotas ou temperatura na reamostragem. A comparação do estoque pontual com uma janela acumulada é um teste estático de pressão de demanda.

### Descarte: o que é efetivamente medido

O gerador permite calcular a chance de uma bolsa sorteada estar **vencida na fotografia**, condicionada ao componente. Isso é um indicador de exposição ao descarte por vencimento, não observação de descarte realizado.

No modelo, a idade `A` é uniforme entre os 61 inteiros de 0 a 60 dias. Com validade `v`, a bolsa está vencida quando `A > v`; no dia do vencimento ainda é considerada válida. Assim, `P(vencida | componente) = máximo(0, 60 − v) / 61`. A proporção observada divide bolsas vencidas pelo total do respectivo componente. Já a exposição em até sete dias divide bolsas válidas com 0 a 7 dias restantes pelo total de bolsas válidas do componente.

## Achado 1 — Plaquetas precisam de reposição e giro coordenados

| Evidência de plaquetas | Resultado |
|---|---:|
| Estoque válido / demanda da base | 40 / 257 bolsas |
| Cenários com déficit de volume | 5.000 de 5.000 (100,0%) |
| Déficit médio nos cenários | 217,1 bolsas |
| Probabilidade teórica de estar vencida | 55/61 = 90,2% |
| Proporção vencida na base | 367/407 = 90,2% |
| Vencimento em até 7 dias, entre as válidas | 40/40 = 100,0% |

**Interpretação.** Falta de estoque utilizável e excesso de bolsas vencidas coexistem. A validade de cinco dias adotada pelo projeto é muito menor que a janela de geração. O cenário sem giro concentra bolsas que já não podem suprir a demanda. Os 100% da reamostragem indicam déficit em todos os cenários executados, não certeza universal sobre futuras demandas.

**Decisão de estoque.** Priorizar um experimento de reposição de plaquetas em lotes menores e mais frequentes, por tipo e unidade, em vez de elevar indiscriminadamente o estoque acumulado. Os 217,1 representam déficit médio do cenário inteiro, não quantidade a comprar diariamente nem ponto de pedido validado.

**Decisão de priorização.** Entre bolsas elegíveis para uma requisição, aplicar FEFO. Priorizar atendimento conforme urgência e prazo do pedido; uma bolsa perto de vencer não deve fazer um pedido de rotina ultrapassar automaticamente uma emergência. Redistribuição só faz sentido quando há demanda elegível e condições de transporte.

**Como verificar a decisão.** Comparar a política proposta com a atual em simulação temporal usando a mesma demanda e acompanhar atendimento por tipo/componente, faltas por prioridade e bolsas efetivamente descartadas. A análise presente não mede o benefício dessa política.

## Achado 2 — A folga total de hemácias não protege todos os tipos

O total de hemácias apresenta 840 bolsas válidas para 807 demandadas na base, mas há déficit agregado em **1.020/5.000 cenários (20,4%)**. O detalhamento muda a ordem de atenção:

| Tipo de hemácia | Estoque válido | Demanda da base | Cenários com déficit | Probabilidade estimada | Déficit médio (bolsas) |
|---|---:|---:|---:|---:|---:|
| A− | 69 | 85 | 4.184 | 83,7% | 17,3 |
| AB+ | 19 | 24 | 3.360 | 67,2% | 6,5 |
| A+ | 290 | 294 | 2.729 | 54,6% | 12,7 |
| B+ | 60 | 57 | 1.838 | 36,8% | 3,6 |
| AB− | 5 | 4 | 1.308 | 26,2% | 1,2 |
| O+ | 303 | 273 | 660 | 13,2% | 2,0 |
| B− | 20 | 13 | 636 | 12,7% | 0,6 |
| O− | 74 | 57 | 407 | 8,1% | 0,6 |

**Interpretação.** A− combina a maior frequência de falta com o maior déficit médio entre as hemácias. AB+ falta com mais frequência que A+, mas A+ tem maior déficit médio em bolsas. Logo, ordenar apenas por probabilidade pode deixar de atender um volume importante; ordenar apenas por volume pode ocultar a recorrência de falta em grupos menores. AB− não deve herdar automaticamente a prioridade baseada na cobertura agregada da U1: aqui o recorte é exclusivamente hemácias e o evento é outro.

**Decisão de estoque.** Dar atenção inicial à reposição de hemácias A− e avaliar AB+ e A+ combinando frequência e magnitude da falta. Detalhar por unidade antes de reservar ou transferir bolsas. O relatório não fixa estoque de segurança: isso requer prazo de reposição, nível de serviço e evolução temporal, ausentes no experimento.

**Decisão de priorização.** Avaliar alternativas compatíveis pelo mecanismo do Rota Vital, por componente, antes de concluir que um pedido não pode ser atendido. A sobra aritmética de O− não deve ser tratada como reserva livre para qualquer hemocomponente. Esta análise usa igualdade de grupo e não mede o efeito da [matriz de compatibilidade](../src/main/java/com/rotavital/compatibilidade/MatrizCompatibilidade.java).

**Como verificar a decisão.** Comparar falta e atendimento por tipo/componente antes e depois de uma política explícita de reposição e alocação compatível, registrando também o consumo das reservas e as prioridades atendidas.

## Achado 3 — O alerta de validade deve separar proporção e volume

| Componente | Vencidas / total | Proporção vencida | Probabilidade teórica no gerador | Válidas vencendo em até 7 dias | Proporção entre válidas |
|---|---:|---:|---:|---:|---:|
| Hemácias | 288/1.128 | 25,5% | 29,5% | 147/840 | 17,5% |
| Plaquetas | 367/407 | 90,2% | 90,2% | 40/40 | 100,0% |
| Plasma | 0/369 | 0,0% | 0,0% | 0/369 | 0,0% |
| Crioprecipitado | 0/96 | 0,0% | 0,0% | 0/96 | 0,0% |

**Interpretação.** Plaquetas têm a maior proporção de exposição, mas hemácias concentram **147 das 187 bolsas válidas** que vencem em até sete dias. Olhar só percentuais esconderia o maior volume acionável. A proporção observada de hemácias vencidas (25,5%) não é igual à probabilidade teórica do gerador (29,5%); a primeira descreve esta realização finita, a segunda decorre das premissas. Não há evidência de melhora operacional nessa diferença.

**Decisão de estoque e priorização.** Apresentar nos alertas tanto quantidade quanto percentual por componente e unidade. Revisar as 147 hemácias e as 40 plaquetas próximas de vencer, verificar demanda compatível e aplicar FEFO entre bolsas elegíveis. Evitar reposição automática baseada no total cadastrado, pois bolsas vencidas não compõem o estoque utilizável.

**Como verificar a decisão.** Rastrear quantas dessas bolsas foram alocadas antes do vencimento e quantas venceram sem uso. Estar próxima do vencimento não significa descarte inevitável, e o alerta sozinho não comprova prevenção de perdas.

## Achado 4 — Ausência de vencimento não elimina falta de estoque

| Componente | Estoque válido / demanda da base | Cenários com déficit agregado | Probabilidade estimada | Déficit médio (bolsas) |
|---|---:|---:|---:|---:|
| Plasma | 369 / 344 | 980 / 5.000 | 19,6% | 3,4 |
| Crioprecipitado | 96 / 79 | 661 / 5.000 | 13,2% | 1,2 |

**Interpretação.** A validade de 365 dias assumida para esses componentes ultrapassa a idade máxima de 60 dias do gerador. Por isso a probabilidade de já estarem vencidos é zero por construção; não é evidência de risco operacional nulo. A demanda reamostrada ainda pode ultrapassar a disponibilidade, mesmo quando todos os grupos são somados.

**Decisão de estoque.** Manter acompanhamento de demanda por tipo e componente e investigar reposição quando houver déficit; não retirar plasma e crioprecipitado do planejamento só porque não aparecem no alerta de vencimento. Diferenciar indisponibilidade por quantidade, validade e transporte.

**Decisão de priorização.** Usar as regras do componente para compatibilidade e os alertas da [telemetria de temperatura](../src/main/java/com/rotavital/servico/TemperaturaServico.java) para tratar ocorrências de transporte. Esta análise não calcula probabilidade de perda por temperatura e não permite declarar que as bolsas estão livres de outras causas de descarte.

**Como verificar a decisão.** Acompanhar atendimento e motivos de indisponibilidade separadamente, sem interpretar ausência de vencimento como sucesso global da operação.

## Limites para usar os achados

- Os resultados são do gerador Python, não da carga inicial ou do estoque atual da API. Não inferem frequência populacional ou risco real de um serviço de saúde.
- A reamostragem depende das 300 requisições sintéticas disponíveis. Não cria tipos de pedido ausentes e não representa sazonalidade, dependência entre dias ou demanda de outras redes.
- O estoque é fixo: não se estima a variabilidade de coleta nem se mede o tempo até esgotamento. Os percentuais não são probabilidades diárias nem taxas de pacientes não atendidos.
- O erro padrão de Monte Carlo de uma frequência é aproximadamente `√[p(1−p)/5.000]`, no máximo **0,71 ponto percentual**. Isso descreve apenas a precisão numérica da reamostragem, não a validade das premissas. Frequência de 0% ou 100% não comprova impossibilidade ou certeza fora dos cenários executados.
- Nenhuma comparação com/sem FEFO, compatibilidade ou reposição foi executada aqui. As decisões são propostas fundamentadas para avaliação posterior, não benefícios já demonstrados nem funcionalidades novas implementadas.

## Evidências, reprodução e aceite

Na raiz do repositório, com Python 3, sem dependências externas:

```powershell
python dados/probabilidades_est_u2.py
python -m unittest discover -s dados -p test_probabilidades_est_u2.py
```

O primeiro comando reproduz o JSON com as 32 combinações tipo × componente, quatro totais por componente, probabilidades de vencimento e metadados. O segundo verifica o limite `D > S`, soma de itens repetidos, exclusão de vencidas, fronteiras de validade, reprodutibilidade e um caso de probabilidade conhecida.

| Critério da task | Evidência |
|---|---|
| Relatório claro, com interpretação além dos números | Quatro achados com interpretação, decisão e forma de avaliar o resultado |
| Ligação com o domínio do Rota Vital | Plaquetas, hemácias por grupo, validade, FEFO, compatibilidade, prioridade de requisição e transporte; tabelas específicas da rede sintética |
| Declaração de dados sintéticos | Declaração na abertura, método, limites e metadados do JSON |

As tabelas são a evidência quantitativa desta entrega; não foram incluídos gráficos genéricos. O objetivo da rubrica é explicitar por que cada resultado muda uma decisão de estoque ou priorização.
