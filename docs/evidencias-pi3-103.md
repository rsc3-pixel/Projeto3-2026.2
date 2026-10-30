EVIDÊNCIAS DE ENTREGA — PI3-103 (W12-Taxa de descarte por vencimento, EST)

O que foi feito:

A taxa de descarte por vencimento, que a U1 calculava só por hemocomponente, passou a ser calculada também por período de coleta (semana ISO ou mês) e ganhou a relação com a política FEFO: para cada componente, quantas das bolsas vencidas tiveram pedido compatível (mesmo componente e grupo compatível pela MatrizCompatibilidade) enquanto ainda valiam. Sob FEFO essas bolsas teriam saído antes de vencer; é a parcela do descarte atribuível à alocação, e não à validade. O restante é perda estrutural, que só a calibração da coleta resolve. O indicador é um limite superior, porque confere a existência de demanda compatível, não a sua suficiência.

Tudo foi calculado no IndicadorServico, o mesmo que atende /api/v1/indicadores/*, e exposto em GET /api/v1/indicadores/descarte/por-periodo (novo) e nos campos descarteEvitavelFefo e percentualEvitavelFefo de GET /api/v1/indicadores/descarte. O painel consolidado (PI3-32) lê desses mesmos métodos.

Resultado sobre os dados sintéticos (referência 17/09/2026):

Plaquetas: 407 coletadas, 367 vencidas (90,2%), 332 evitáveis pela FEFO (90,5% das vencidas). As outras 35 venceram em janelas de 5 dias sem pedido compatível.
Hemácias: 1.128 coletadas, 288 vencidas (25,5%), 288 evitáveis (100,0%): toda a perda é da alocação.
Plasma e crioprecipitado: sem descarte no horizonte (validade de 365 dias).
Por semana de coleta, o descarte cai de 86,2% (semana 29, 13 a 19/07) para 0,0% (semana 38, 14 a 20/09). Por mês: 76,6% em julho, 27,6% em agosto, 12,0% em setembro.

Reuso, não reconstrução:

A taxa por componente da U1 (taxaDescartePorComponente) foi mantida e estendida; o agrupamento por período reaproveita o mesmo resumo por componente dentro de cada período. A compatibilidade vem de MatrizCompatibilidade (PI3-84, PI3-128), não foi reescrita. A regra de vencimento é a de Bolsa.estaVencida (isAfter), a mesma da SelecaoFefo: a bolsa cuja validade é a própria data do pedido ainda conta como atendível, e há teste cobrindo essa fronteira.

Validação contra a análise em Python:

dados/validar_indicadores_u2.py calcula os mesmos indicadores sobre dados/dados_sinteticos.json, só com a biblioteca padrão, e grava dados/indicadores_u2_esperado.json. O teste IndicadorServicoDadosSinteticosTest carrega as 2.000 bolsas e as 300 requisições no serviço Java e compara, campo a campo, total, vencidas, taxa, evitável e percentual evitável por componente, e os mesmos campos em cada uma das 10 semanas e dos 3 meses. Igualdade exata após o arredondamento a uma casa (tolerância 1e-9).

Pull Request #89:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/89

Código (branch feat/w12-est-descarte-e-painel):

IndicadorServico — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/src/main/java/com/rotavital/servico/IndicadorServico.java
IndicadorController — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/src/main/java/com/rotavital/api/IndicadorController.java
DescartePeriodoResponse e DescarteComponenteResponse — https://github.com/rsc3-pixel/Projeto3-2026.2/tree/feat/w12-est-descarte-e-painel/src/main/java/com/rotavital/api/dto/indicadores
Validação em Python — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/dados/validar_indicadores_u2.py
Esperado gerado — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/dados/indicadores_u2_esperado.json
Testes — IndicadorServicoTest (deveCalcularDescartePorPeriodo, deveContarDescarteEvitavelPelaFefo), IndicadorServicoDadosSinteticosTest (descartePorComponenteBateComPython, descartePorPeriodoBateComPython), IndicadorControllerTest (deveConsultarTaxaDescarte, deveConsultarTaxaDescartePorPeriodo)
Fórmulas, unidades e período — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/docs/interpretacao_indicadores.md (seções 2.6 e 3.6)

Critérios de aceite:

Cálculo correto e reproduzível sobre os dados sintéticos do projeto: reproduzido por duas implementações independentes (Python e Java) com igualdade exata no teste de equivalência; o script roda com python dados/validar_indicadores_u2.py e o teste com ./mvnw test -Dtest=IndicadorServicoDadosSinteticosTest.

Resultado disponível para o painel consolidado (PI3-32): o painel /painel/{id} exibe as seções DESCARTE POR COMPONENTE (com a coluna evitável FEFO) e DESCARTE POR SEMANA DE COLETA, lendo dos mesmos métodos da API.

Como reproduzir:

python dados/validar_indicadores_u2.py
./mvnw test -Dtest=IndicadorServicoTest,IndicadorServicoDadosSinteticosTest,IndicadorControllerTest
curl "http://localhost:8080/api/v1/indicadores/descarte"
curl "http://localhost:8080/api/v1/indicadores/descarte/por-periodo?granularidade=MES"
