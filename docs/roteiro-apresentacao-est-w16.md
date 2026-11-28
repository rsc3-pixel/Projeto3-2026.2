# Roteiro de apresentação — EST W16

**Apresentação:** 04/12/2026. **Duração proposta:** 4 minutos, ainda não cronometrada com o grupo. **PI3-234:** material preparado. **PI3-236:** ensaio pendente de registro.

Usar este roteiro como aprofundamento da passagem de EST do [roteiro integrado](roteiro-demonstracao.md). Se houver somente os 40 segundos reservados naquele roteiro, usar a versão curta ao final. Os sete integrantes previstos estão listados no [registro W16](W16-entrega-final-est.md); o grupo deve atribuir nomes aos blocos antes de ensaiar. Fernando confirmou em 18/11/2026 que o ensaio ainda não ocorreu.

## Preparação

Abrir `/painel/HC01` no Thymeleaf, `/api/v1/indicadores/desabastecimento`, `/api/v1/indicadores/validade` e o [relatório W14](W14-relatorio-achados-est.md). Conferir disponibilidade, data/horizonte e ausência de deploy em andamento. Usar os gráficos do repositório para os resultados da base histórica, e a tela para demonstrar integração. Os números da tela variam; o ID selecionado não filtra os indicadores globais da rede.

Ter o repositório local com relatório, SVGs e [captura HTTP datada](evidencias/w16-est/captura-http.json). Se a rede falhar, apresentar esses arquivos, identificando a data e dizendo que são evidências salvas, não uma demonstração ao vivo. Não inventar dados, ensaio ou resultado de teste.

## Fala de quatro minutos

| Tempo proposto | Exibição | Fala sugerida |
|---|---|---|
| 0:00–0:30 | Título e aviso do relatório | “Nossa análise usa dados sintéticos. Queremos entender onde o estoque pode faltar e onde a validade cria exposição a perdas. A base histórica tem 2.000 bolsas e 300 requisições; o painel trabalha com o banco atual da aplicação.” |
| 0:30–1:05 | Painel: validade e cobertura | “Média sozinha esconde diferenças entre componentes. Na análise U1, a média de validade era 129,2 dias, a mediana 32 e o desvio padrão 150,7. Isso não significa 129 dias de abastecimento garantido. Na tela, unidade, data e dispersão acompanham os valores atuais.” |
| 1:05–1:55 | Gráfico W14 e endpoint de desabastecimento | “A cobertura compara estoque e demanda média; a probabilidade considera a variabilidade. Na base histórica, B− cobre 103,4% da demanda e ainda tem risco de 40,3%. A+ combina risco de 99,8% com déficit pontual de 95 bolsas. A decisão é revisar reposição por tipo, componente e unidade, sem substituir a prioridade de cada pedido.” |
| 1:55–2:40 | Tabela/gráfico de descarte e oportunidades | “Plaquetas concentram 90,2% de vencimento na fotografia. Das 655 bolsas vencidas, 620 tiveram algum pedido compatível durante a validade. Isso é um limite superior de oportunidade: o cálculo não verifica se havia quantidade de demanda para todas nem transporte viável. Não comprovamos 620 perdas evitadas pelo FEFO.” |
| 2:40–3:10 | Gráfico por mês de coleta | “A proporção vencida cai de 76,6% em julho para 12% em setembro, mas todos os lotes são avaliados na mesma data. Os mais novos tiveram menos tempo para vencer. Essa queda não prova melhoria do sistema.” |
| 3:10–3:40 | Matriz de decisões W14 | “Propomos avaliar reposição e reserva conforme risco e prazo, usar FEFO nas bolsas elegíveis e revisar lotes de curta validade. Para medir ganho, ainda seria preciso comparar políticas com a mesma demanda e respeitar quantidade, compatibilidade e logística.” |
| 3:40–4:00 | Índice de evidências W16 | “Os resultados estão no repositório, na API e no painel. Os scripts conferem a consistência na mesma base. As limitações e pendências estão registradas: os dados são sintéticos e as decisões são propostas a avaliar.” |

O horizonte de **61 dias** se refere à base histórica, não a um risco diário. A captura de produção de 18/11 tem somente **3 dias** de demanda. Sempre apontar o horizonte da fonte exibida. A comparação entre implementações foi feita com a mesma base de entrada, não esperando os mesmos números entre bancos diferentes.

## Perguntas para treinar

- **Por que Binomial Negativa?** A série sintética tem sobredispersão; o modelo acomoda variância maior que média. A hipótese de independência dos dias e a base sintética limitam a extrapolação.
- **Cobertura acima de 100% garante atendimento?** Não. A demanda varia e ainda existem restrições de componente, compatibilidade, validade, localização e prazo.
- **FEFO evitou 94,7% das perdas?** Não. 620/655 é a fração com algum pedido compatível, não um efeito causal nem suficiência de demanda.
- **Zero descarte na tela contradiz 32,75% no relatório?** Não. São bases e datas diferentes; a captura atual não é a massa histórica de 2.000 bolsas.
- **Os meses mostram melhora?** Não é possível concluir: os lotes têm idades e composição diferentes.
- **O desvio é margem de erro?** Não. Descreve dispersão dos valores; não é intervalo de confiança.

## Versão de 40 segundos para a demonstração integrada

“Aqui estão os indicadores de EST, com unidade, período e dispersão. Usamos dados sintéticos. Na análise histórica, B− tem cobertura de 103,4%, mas risco de falta de 40,3%: cobertura não garante atendimento. No descarte, a parcela com pedido compatível é uma oportunidade, não perda evitada comprovada por FEFO. E a queda entre meses de coleta não prova melhoria, pois os lotes recentes tiveram menos tempo para vencer. O relatório liga esses achados a decisões de reposição e priorização, com limites explícitos.”

## Ensaio e aceite

Ensaiar com todos os integrantes, medir o tempo real de cada bloco, treinar as perguntas e a alternativa sem Internet. Preencher o [registro de ensaio da W16](W16-entrega-final-est.md#5-pi3-236--registro-de-ensaio-pendente) com data, participantes, duração, problemas e ajustes. A duração proposta acima não conta como ensaio e não permite concluir a PI3-236.
