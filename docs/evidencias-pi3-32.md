EVIDÊNCIAS DE ENTREGA — PI3-32 (W12-Painéis, EST)

O que foi feito:

O painel operacional (/painel/{id}, Thymeleaf, PI3-133) passou a ser o painel consolidado: reúne os indicadores da U1 (estoque por tipo, cobertura da demanda, bolsas críticas) e os da U2 (risco de desabastecimento do PI3-102, descarte por componente e por semana de coleta com a parcela evitável pela FEFO do PI3-103, e a dispersão da validade com média, mediana, desvio-padrão, mínimo e máximo). Nenhuma regra de negócio entrou no controller web: cada número sai do mesmo método de IndicadorServico que atende /api/v1/indicadores/*.

Toda seção declara a unidade e o período que considera, numa legenda logo abaixo do título: "bolsas válidas hoje ÷ bolsas pedidas, requisições de 19/07 a 17/09 (61 dias)", "% das bolsas coletadas que venceram sem uso, vencimento conferido em dd/mm/aaaa", "dias até o vencimento, só bolsas válidas". A legenda também diz qual é o limiar de alerta da seção.

A probabilidade de desabastecimento precisou de um endpoint novo, GET /api/v1/indicadores/desabastecimento, porque o critério de aceite exige que os números do painel batam com os endpoints e até então a modelagem só existia no módulo C++ do PI3-102. ModeloDemanda.java é a porta dessa modelagem: Binomial Negativa calibrada pela média e pela variância da série diária de requisições, Poisson devolvida como referência, somas em espaço logarítmico para não haver underflow com demanda na casa das centenas.

Critério de alerta sem depender só de cor: toda etiqueta tem texto (faltam 5, coberto, CRITICO, ALTO, MODERADO, BAIXO) e um símbolo (▼ ▲ ◆ ●) além da cor, e a linha abaixo do limiar recebe uma borda à esquerda. Os limiares (HU-06): déficit de cobertura maior que zero; bolsas que vencem em até 7 dias; risco de falta maior ou igual a 50%; descarte maior ou igual a 50%. Os títulos das colunas e as etiquetas trazem title com a explicação, e os símbolos ficam ocultos do leitor de tela (aria-hidden) porque o texto já diz o mesmo.

Validação de cada número contra o endpoint e contra a análise em Python:

O painel e os endpoints são a mesma chamada de serviço; a diferença entre eles é só a renderização. A comparação com o Python está em IndicadorServicoDadosSinteticosTest, que carrega as 2.000 bolsas e as 300 requisições de dados/dados_sinteticos.json no serviço e confere com dados/indicadores_u2_esperado.json (gerado por dados/validar_indicadores_u2.py) e com dados/resultados.json (análise original da U1):

| Métrica do painel | Endpoint | Referência em Python | Teste | Resultado |
|---|---|---|---|---|
| Estoque por tipo | /estoque-por-tipo | analise.estoque_por_tipo_sanguineo | IndicadorServicoTest.deveCalcularEstoquePorTipo | igual |
| Cobertura e déficit por tipo | /cobertura | resultados.json → cobertura | coberturaBateComAnaliseOriginalDaU1 | igual nos 8 tipos |
| Bolsas críticas (≤ 7 dias) | /criticas | analise.bolsas_proximas_do_vencimento | IndicadorServicoTest.deveFiltrarBolsasCriticas | igual |
| Validade: média, mediana, desvio, mín, máx | /validade | analise.dispersao_validade | IndicadorServicoTest.deveCalcularDispersaoValidade | igual |
| Descarte por componente e evitável FEFO | /descarte | validar_indicadores_u2.resumir_por_componente | descartePorComponenteBateComPython | igual nos 4 componentes |
| Descarte por semana e por mês de coleta | /descarte/por-periodo | validar_indicadores_u2.descarte_por_periodo | descartePorPeriodoBateComPython | igual nas 10 semanas e 3 meses |
| Risco de desabastecimento (BN, Poisson, var/média, risco) | /desabastecimento | validar_indicadores_u2.desabastecimento | desabastecimentoBateComPython | igual nos 8 tipos, inclusive a ordem |
| Binomial Negativa × módulo C++ (PI3-102) | /desabastecimento | dados/painel_consolidado_pi3_32.json | binomialNegativaBateComModuloCpp | igual a uma casa decimal nos 8 tipos |

Observação honesta sobre os dados: o banco da aplicação sobe com a carga de CargaInicial (100 bolsas, nenhuma vencida ao subir), diferente do dataset de 2.000 bolsas da análise. Por isso o painel no ar mostra números diferentes dos da análise em Python. A igualdade é provada sobre o mesmo dado de entrada, pelo teste; o painel aplica a mesma regra ao dado que tem.

Reuso, não reconstrução:

O layout, a identidade arcade e a atualização automática (PI3-137, PI3-138, PI3-141) não foram tocados; o painel só ganhou seções novas dentro do mesmo grid. As classes etiqueta ok/alerta/critico e metrica critico/alerta são as existentes em arcade.css. O endpoint /validade da U1 já devolvia as medidas de dispersão; o painel passou a exibi-las. A modelagem estatística é a do PI3-102, portada do C++ sem mudar a regra: o teste confere a Binomial Negativa contra o JSON gerado pelo módulo C++.

Pull Request #89:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/89

Código (branch feat/w12-est-descarte-e-painel):

Painel — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/src/main/resources/templates/painel/indicadores.html
PainelWebController — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/src/main/java/com/rotavital/web/PainelWebController.java
ModeloDemanda — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/src/main/java/com/rotavital/estatistica/ModeloDemanda.java
IndicadorServico.probabilidadeDesabastecimento — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/src/main/java/com/rotavital/servico/IndicadorServico.java
DesabastecimentoResponse — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/src/main/java/com/rotavital/api/dto/indicadores/DesabastecimentoResponse.java
Testes — ModeloDemandaTest (7), IndicadorServicoTest (11), IndicadorServicoDadosSinteticosTest (6), IndicadorControllerTest (8)
Fórmulas, unidades e período — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/w12-est-descarte-e-painel/docs/interpretacao_indicadores.md (seções 2.7 e 3.7)

Critérios de aceite:

Painel reúne os indicadores da U1 e os novos da U2: seis seções e quatro totais no topo (bolsas em estoque, vencem em 7 dias, tipos descobertos, tipos com risco de falta ≥ 50%).

Cada métrica exibe unidade e período considerado: legenda em toda seção, com data de referência e intervalo das requisições.

Alerta perceptível sem depender apenas de cor: texto e símbolo em toda etiqueta, borda na linha abaixo do limiar, title explicativo.

Números batem com os endpoints /api/v1/indicadores/*: mesma chamada de serviço; equivalência com o Python e com o C++ provada por teste.

Como reproduzir:

./mvnw spring-boot:run
abrir http://localhost:8080/painel/HC01
curl "http://localhost:8080/api/v1/indicadores/desabastecimento"
./mvnw test -Dtest=ModeloDemandaTest,IndicadorServicoTest,IndicadorServicoDadosSinteticosTest,IndicadorControllerTest
