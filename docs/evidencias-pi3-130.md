EVIDÊNCIAS DE ENTREGA — PI3-130 (Testar os casos de borda da compatibilidade)

O que foi feito:

Os seis casos de borda do card foram cobertos no caminho da seleção de bolsas, em três níveis: na lista de candidatas (SelecaoFefo com receptor), na busca pelo índice de estoque (IndiceEstoque) e no estado da requisição (integração MockMvc). A matriz sozinha não bastava porque a MatrizCompatibilidadeTest prova apenas que a tabela responde certo, não que a resposta certa chega inteira até a bolsa escolhida. Entre a tabela e a escolha existem dois outros filtros (validade e status) e uma ordenação FEFO, e é nessa junção que mora o erro de uma implementação apressada: matriz correta com seleção que a ignora, ou que a aplica depois de ordenar, devolve bolsa incompatível no topo. Os testes novos são 18 métodos que rendem 110 execuções (parametrizados) no arquivo de unidade, mais 2 testes de integração. Toda data é fixa (HOJE = 2026-09-10); nenhum teste usa LocalDate.now(), que faria um teste de validade passar hoje e falhar amanhã sem que o código tivesse mudado.

Reuso, não reconstrução:

Nada foi duplicado da PI3-84 nem da PI3-129. A MatrizCompatibilidadeTest já cobre os casos 1 a 4 no nível da tabela (doadorUniversal, receptorUniversal, naoSimetrica, rhPositivoNaoDoaParaNegativo) e não foi tocada. A SelecaoFefoTest já cobre vencida sem receptor, empate sem receptor, a fronteira do isAfter e os dois primeiros casos de compatibilidade da 129, e também não foi tocada. Reaproveitados: a construção de Bolsa calculando dataColeta de trás para frente a partir da validade desejada, com asserção de que a fixture produziu a validade pedida; a constante HOJE fixa em 2026-09-10 da SelecaoFefoTest; o estilo @Nested + @DisplayName em português da MatrizCompatibilidadeTest; e as fixtures MockMvc criarBolsa, criarRequisicao, alocarEsperando, idDe e endereco da CompatibilidadeAlocacaoTest, onde os testes de integração entraram como @Nested para herdar o @BeforeEach que cria hemocentro e hospital. Nenhum código de produção foi alterado: git diff --stat -- src/main está vazio.

Pull Request #50:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/50

Commit:
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/3e92932dcccfa426fd0384c64317822116602765

Código:

CasosBordaCompatibilidadeTest — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/test/casos-borda-compatibilidade-pi3-130/src/test/java/com/rotavital/alocacao/CasosBordaCompatibilidadeTest.java
CompatibilidadeAlocacaoTest (@Nested AtendimentoParcial) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/test/casos-borda-compatibilidade-pi3-130/src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java
SelecaoFefo (testada, não alterada) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/test/casos-borda-compatibilidade-pi3-130/src/main/java/com/rotavital/alocacao/SelecaoFefo.java
MatrizCompatibilidade (testada, não alterada) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/test/casos-borda-compatibilidade-pi3-130/src/main/java/com/rotavital/compatibilidade/MatrizCompatibilidade.java

Critérios de aceite:

Os seis casos cobertos por teste

Caso 1, O- doa para cada um dos oito grupos: bolsaONegativoServeParaQualquerReceptor e indiceEncontraBolsaONegativoParaQualquerReceptor, ambos parametrizados por @EnumSource com os 8 receptores (16 execuções). Prova que o doador universal é aceito por todo receptor tanto na lista quanto quando a bolsa está indexada sob O_NEG e o chamador pede outro grupo — ou seja, que a seleção consulta doadoresPara em vez de casar a chave do índice.

Caso 2, AB+ recebe de cada um dos oito: receptorAbPositivoAceitaQualquerDoador parametrizado pelos 8 doadores, mais ordenarDevolveAsOitoParaReceptorAbPositivo (9 execuções). O segundo prova que nenhuma das oito é descartada e que a ordem FEFO se mantém.

Caso 3, a relação não é simétrica: trocarDoadorEReceptorMudaOResultado verifica, no mesmo teste, que a bolsa O- serve ao receptor A+ e que a bolsa A+ não serve ao receptor O-, com os mesmos dois grupos trocados de lado. indiceSoComAPositivoNaoAtendeReceptorONegativo repete pelo índice e confirma que o mesmo estoque atende A+ e não atende O-.

Caso 4, positivo não doa para o negativo do mesmo grupo: positivoNaoDoaParaNegativo parametrizado por @MethodSource com os 4 pares (O+→O-, A+→A-, B+→B-, AB+→AB-). Antes de assertar a recusa, o teste assere que alocavel sem receptor é true e que o status é DISPONIVEL, para provar que o único motivo da recusa é o Rh, e não validade ou status.

Caso 5, estoque só com bolsa incompatível: escolherNaoLancaEDevolveVazio (assertDoesNotThrow + Optional.empty), ordenarDevolveListaVazia (lista vazia, não lista com as incompatíveis no fim), indiceSoComIncompativelDevolveVazio e compativelEntreIncompativeisEhEncontrada. O último prova que as incompatíveis, mesmo vencendo antes, não atrapalham a escolha da compatível.

Caso 6, compatível porém vencida não é selecionada: vencidaNaoGanhaDaValida usa uma bolsa O- vencida ontem contra uma A+ válida por 20 dias, para um receptor A+ — as duas são compatíveis, e a vencida é a de validade mais próxima, então estaria no topo se o filtro viesse depois da ordenação. soCompativelVencidaDevolveVazio cobre o estoque só com vencida. compativelQueVenceHojeAindaEhSelecionada fixa a fronteira: Bolsa.estaVencida usa isAfter, então a bolsa só vence no dia seguinte à validade e a que vence na própria data de referência ainda é selecionada, sendo inclusive a mais urgente. indiceDescartaCompativelVencida repete pelo índice.

Atendimento parcial tratado como sucesso parcial, não falha

No nível unitário, o @Nested do caso 5 garante que ausência de bolsa compatível é Optional.empty e lista vazia, com assertDoesNotThrow, e nunca exceção. No nível de integração, o @Nested AtendimentoParcial da CompatibilidadeAlocacaoTest verifica o efeito no estado. Em compativelEhAlocadaEIncompativelRecusadaSemDesfazerAAlocacao, uma requisição de hemácias A+ com quantidade 2 e estoque de 1 bolsa O- e 1 bolsa B+ aloca a O- com 201, recebe 409 na B+, e em seguida a requisição está PARCIALMENTE_ATENDIDA, o item PARCIALMENTE_ALOCADO com quantidadeAlocada 1 de 2, a bolsa O- está RESERVADA e a B+ segue DISPONIVEL — a recusa não desfez a alocação anterior. Em requisicaoSobreviveARecusaEAceitaAlocacaoPosterior, o estoque só tem bolsa incompatível: a tentativa devolve 409, a requisição continua PENDENTE (não CANCELADA e não erro 500), e quando chega uma bolsa compatível a mesma requisição é atendida, chegando a ATENDIDA com o item ALOCADO.

Empate de validade determinístico

escolhaIndependeDaOrdemDeEntrada usa receptor AB+ e três bolsas compatíveis de grupos diferentes (B001 O-, B002 A+, B003 B-) com a mesma data de validade, parametrizado pelas 6 permutações de ordem de entrada. Em todas, escolher devolve B001 e ordenar devolve a sequência inteira B001, B002, B003. O desempate vem da FilaPrioridadeFefo, que ordena por data de validade e, no empate, por código de rastreio — regra que não foi reimplementada aqui. ordemDeInsercaoNoIndiceNaoMudaAEscolhida cobre o mesmo pelo índice, inserindo as duas bolsas em ordem crescente e decrescente de código e obtendo a mesma escolhida, o que importa porque a iteração do índice não tem ordem garantida.

Além dos critérios, alocavelConcordaComAMatriz percorre as 64 combinações doador/receptor e assere que SelecaoFefo.alocavel(bolsa, HOJE, receptor, HEMACIAS) responde exatamente o mesmo que MatrizCompatibilidade.compativel(doador, receptor, HEMACIAS) para bolsa válida e disponível. É a rede que pega qualquer divergência futura entre a tabela e a seleção, e não só nos casos de borda escolhidos à mão.

Validação dos testes:

CasosBordaCompatibilidadeTest: 18 testes (110 execuções contando parametrizados), 0 falhas
./mvnw test -Dtest=CasosBordaCompatibilidadeTest

CompatibilidadeAlocacaoTest: 11 testes (9 existentes + 2 novos), 0 falhas
./mvnw test -Dtest=CompatibilidadeAlocacaoTest

Suíte completa: BUILD SUCCESS — de 231 para 343 testes
./mvnw test

CI/CD no GitHub Actions: não executou neste PR, e não é falha de configuração deste card.
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/50/checks
O workflow .github/workflows/ci.yml dispara em "pull_request: branches: [main]", e este PR tem base feat/compatibilidade-selecao-pi3-129, não main. Logo a aba Checks está vazia e "gh pr checks 50" responde "no checks reported". O pipeline rodará quando a 129 for mergeada em main e este PR for reapontado para main, ou quando o encadeamento for resolvido. Validação local acima: 343 testes, BUILD SUCCESS.

Os testes novos:

Teste

O que prova

bolsaONegativoServeParaQualquerReceptor

O- é aceito pelos 8 receptores na lista de candidatas.

indiceEncontraBolsaONegativoParaQualquerReceptor

A busca pelo índice encontra a bolsa O- para os 8 receptores, mesmo indexada sob O_NEG.

receptorAbPositivoAceitaQualquerDoador

AB+ aceita bolsa de cada um dos 8 grupos.

ordenarDevolveAsOitoParaReceptorAbPositivo

Nenhuma das 8 é descartada e a ordem FEFO se mantém.

trocarDoadorEReceptorMudaOResultado

O- doa para A+, A+ não doa para O-: inverter os lados muda a resposta.

indiceSoComAPositivoNaoAtendeReceptorONegativo

O mesmo estoque atende A+ e não atende O-, pela via do índice.

positivoNaoDoaParaNegativo

Nos 4 pares, o Rh é o único motivo da recusa: a bolsa está válida e DISPONIVEL.

escolherNaoLancaEDevolveVazio

Estoque só incompatível devolve Optional.empty sem exceção.

ordenarDevolveListaVazia

Incompatível não é rebaixada para o fim da lista: é descartada.

indiceSoComIncompativelDevolveVazio

O mesmo vale na busca pelo índice.

compativelEntreIncompativeisEhEncontrada

Incompatíveis que vencem antes não impedem a escolha da compatível.

vencidaNaoGanhaDaValida

A compatível vencida é a de validade mais próxima e mesmo assim perde: o filtro vem antes da ordenação.

soCompativelVencidaDevolveVazio

Estoque só com compatível vencida devolve vazio, sem exceção.

compativelQueVenceHojeAindaEhSelecionada

Fronteira do isAfter: a bolsa só vence no dia seguinte à validade.

indiceDescartaCompativelVencida

A vencida também é descartada na via do índice.

escolhaIndependeDaOrdemDeEntrada

Nas 6 permutações, escolher e ordenar devolvem sempre o mesmo resultado.

ordemDeInsercaoNoIndiceNaoMudaAEscolhida

A iteração do índice não tem ordem garantida, mas a escolha não varia com ela.

alocavelConcordaComAMatriz

Nas 64 combinações, a seleção nunca discorda da matriz.

compativelEhAlocadaEIncompativelRecusadaSemDesfazerAAlocacao

409 na incompatível não desfaz a alocação anterior: requisição PARCIALMENTE_ATENDIDA, item PARCIALMENTE_ALOCADO, bolsas RESERVADA e DISPONIVEL.

requisicaoSobreviveARecusaEAceitaAlocacaoPosterior

Após o 409, a requisição segue PENDENTE e é atendida quando chega bolsa compatível.

Achados:

1. ServicoAlocacaoRota ainda filtra por grupo idêntico. Em src/main/java/com/rotavital/alocacao/ServicoAlocacaoRota.java, linha 126, a condição é "bolsa.getGrupoSanguineo() != grupo || bolsa.getTipo() != tipo", ou seja, igualdade de grupo e não compatibilidade transfusional. O javadoc nas linhas 54 e 55 reforça: "A busca e por grupo sanguineo identico, sem tabela de compatibilidade ABO/Rh - mesma decisao ja registrada na SelecaoFefo". Essa afirmação ficou desatualizada com a PI3-129, que passou a fazer a SelecaoFefo consultar a MatrizCompatibilidade. O efeito prático é que a alocação com rota recusa doação válida — um paciente A+ não recebe bolsa O- por esse caminho — enquanto o endpoint de alocação direta aceita, deixando dois comportamentos diferentes para a mesma regra de negócio. Confirmado e deliberadamente não corrigido nesta subtarefa. Sugestão de subtarefa: "Aplicar compatibilidade ABO/Rh na alocação com rota (ServicoAlocacaoRota)", trocando o filtro por MatrizCompatibilidade.doadoresPara ou pela sobrecarga de SelecaoFefo com receptor, e atualizando o javadoc.

2. O CI não roda em PR cujo destino não é main. O workflow só escuta "pull_request: branches: [main]", então PRs encadeados entre branches de feature, como este, ficam sem verificação automática justamente no momento em que a revisão acontece. Sugestão de subtarefa: "Disparar o CI em pull request para qualquer branch", trocando o gatilho por "pull_request:" sem filtro de branches, ou acrescentando os prefixos feat/ e test/.

3. Divergência entre a matriz e o enunciado da PI3-129 quanto ao plasma, já registrada na entrega da 129 e repetida aqui porque continua aberta: MatrizCompatibilidade define PLASMA.put(O_NEG, EnumSet.allOf(...)), ou seja, receptor O- aceita plasma dos oito grupos, porque o fator Rh não restringe plasma. Quem espera que O- recuse plasma Rh positivo precisa mudar a matriz, não a seleção. Esta subtarefa testa hemácias, conforme o card, então não toca nesse ponto.

Decisão técnica:

Os testes ficaram na seleção e não na matriz porque o nível da matriz já estava coberto e repetir ali não acrescentaria garantia: a pergunta em aberto não era "a tabela responde certo", era "a resposta certa sobrevive ao resto do caminho". Entre a tabela e a bolsa escolhida existem o filtro de validade, o filtro de status e a ordenação FEFO, e é a interação deles que uma implementação apressada erra — principalmente aplicando a compatibilidade depois de ordenar, quando uma bolsa incompatível que vence amanhã ocupa o topo. Por isso os casos 4 e 6 asseram explicitamente que alocavel sem receptor é true, isolando a compatibilidade como única causa da recusa, e o caso 6 escolhe de propósito uma vencida que é a mais urgente de todas.

Os testes de "cada um dos oito" ficaram parametrizados com @EnumSource e @MethodSource em vez de laços dentro de um único @Test porque, com laço, a primeira falha interrompe o teste e esconde as demais, e o relatório aponta um teste falhando sem dizer qual grupo. Parametrizado, cada combinação é uma execução nomeada: 110 execuções a partir de 18 métodos, e uma falha identifica o par doador/receptor exato. O mesmo raciocínio levou o empate a ser parametrizado pelas 6 permutações, em vez de embaralhar com semente fixa — permutação exaustiva de três elementos cobre todos os casos e não depende da implementação do Random.

A data é fixa em 2026-09-10 porque validade é o eixo do FEFO. Com LocalDate.now(), "vence ontem" e "vence hoje" mudariam de significado a cada dia e um teste verde hoje falharia amanhã sem que nada no código tivesse mudado. A fixture calcula dataColeta de trás para frente a partir da validade desejada, porque a Bolsa deriva dataValidade de dataColeta mais a validade do tipo, e assere que a validade produzida é a pedida — sem isso, uma mudança futura em getValidadeDias faria os testes passarem testando datas diferentes das pretendidas.

O teste de integração ficou na CompatibilidadeAlocacaoTest, como @Nested AtendimentoParcial, e não em arquivo novo, porque é lá que vivem o @BeforeEach que cria hemocentro e hospital e os helpers de MockMvc. Um arquivo novo exigiria duplicar toda essa montagem e, sendo @SpringBootTest, pagaria outro contexto Spring no tempo de suíte. O @Nested mantém o assunto agrupado e sinalizado pelo @DisplayName, deixando claro no relatório o que pertence à PI3-130.
