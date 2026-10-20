EVIDÊNCIAS DE ENTREGA — PI3-150 (Testes unitários e documentação da roteirização)

O que foi feito:

A entrega fechou as lacunas de teste da roteirização e produziu o documento da W10. O ponto de partida foi uma revisão de cobertura: cada comportamento prometido no javadoc de VizinhoMaisProximo e ResultadoRoteiro foi confrontado com os 16 testes que já existiam, e cinco apareceram sem teste algum — a heurística não ser ótima, o desempate determinístico pela ordem de entrada, a imutabilidade das listas devolvidas, o equals e hashCode considerando os inalcançáveis, e o caso de grafo dirigido que isola a assimetria de uma aresta de mão única. Os oito testes novos fecham exatamente essas lacunas, com grafos pequenos montados no próprio teste e custos conferidos à mão. Nenhuma divergência entre o comportamento real e o javadoc foi encontrada, então não houve necessidade de parar nem de tocar em código de produção. O documento docs/W10-roteirizacao-vizinho-mais-proximo.md registra objetivo, algoritmo em passos com pseudocódigo, equivalência com a versão clássica em C/C++, reaproveitamento da Unidade 1, decisões técnicas, complexidade com V e E reais da malha, a rota obtida, a limitação de não otimalidade e a rastreabilidade das três subtarefas.

Reuso, não reconstrução:

Da PI3-148 vieram VizinhoMaisProximo e ResultadoRoteiro, e da PI3-149 vieram os pontos inalcançáveis, as bordas e o teste da malha sintética; nada disso foi reescrito, apenas coberto por testes novos. Os 16 testes existentes não foram duplicados nem alterados. Da Unidade 1 continuam em uso Grafo, Dijkstra e ResultadoRota, sem alteração: o teste da não otimalidade, por exemplo, usa o próprio Dijkstra para calcular o custo da ordem alternativa, em vez de somar pesos à mão. Nenhum código de produção foi alterado nesta subtarefa, comprovado pelo comando "git diff --stat feat/vizinho-sem-caminho-pi3-149 -- src/main", cuja saída é vazia.

Pull Request #54:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/54

Commits:
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/a5fa9da850203b15ba85a0dba93846da0a77eb3b
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/bd7629ab44deeb938d4b936fd100cf67b516856d

Código e documentação:

VizinhoMaisProximoTest — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-testes-doc-pi3-150/src/test/java/com/rotavital/estruturas/VizinhoMaisProximoTest.java
W10-roteirizacao-vizinho-mais-proximo.md — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-testes-doc-pi3-150/docs/W10-roteirizacao-vizinho-mais-proximo.md

Critérios de aceite:

Testes unitários cobrindo rota normal e ponto inalcançável

Rota normal está coberta desde a PI3-148, em rotaNormalVisitaNaOrdemDoVizinhoMaisProximo, que confere ordem de visita, caminho vértice a vértice e custo total de 15 minutos calculado à mão. Ponto inalcançável está coberto desde a PI3-149, em pontoDesconectadoNaoImpedeOsDemais, que usa um vértice sem arestas, e em roteiroParcialEhPreservadoQuandoARotaTrava, que mostra o roteiro travando no meio com o percorrido preservado. Esta subtarefa reforçou o segundo caso com maoUnicaDeixaPontoInalcancavelDepoisDaParada, em que o ponto é alcançável a partir da origem mas deixa de ser depois que o veículo para em outro vértice, situação que nenhum teste anterior isolava.

Rota calculada para os pontos de entrega da malha sintética

Coberto na PI3-149 por roteiroNaMalhaRealAtendeTodosOsHospitais, que carrega CarregadorMalha.carregar(), deriva os pontos de UnidadeRede.TODAS filtrando pelo tipo AGENCIA_TRANSFUSIONAL e verifica alcancouTodos, visita única de cada ponto e continuidade do caminho; e por custoTotalEhASomaDosTrechos, que recalcula o custo perna a perna com o Dijkstra. A rota obtida sai do HC01, Hemocentro Recife (HEMOPE), visita na ordem HC04, HC05, HC06, HC09, HC12, HC11, HC10 e HC07, percorrendo HC01, HC04, HC05, HC04, HC06, HC09, HC12, HC11, HC10, HC05, HC02 e HC07, com custo total de 173,6 minutos. Está registrada na seção 7 do documento da W10.

Suíte completa com BUILD SUCCESS

De 241 para 249 testes, sem falhas nem erros.

Documento da W10 com equivalência C/C++, reaproveitamento, complexidade e limitação

O documento tem nove seções. A 3 trata da equivalência com a versão em C/C++ e registra explicitamente que não existe código de vizinho mais próximo em C ou C++ no repositório: o diretório aed contém apenas aed/unidade1/c e aed/unidade1/cpp, com lista encadeada, fila FIFO e pilha LIFO, não há aed/unidade2 e nenhuma busca encontrou matriz de distâncias ou vetor visitado. A comparação é feita com o algoritmo clássico, e isso está dito no próprio documento para não sugerir um arquivo inexistente. A tabela mapeia matriz de distâncias para Dijkstra.calcularDistancias, vetor visitado para o LinkedHashSet de pendentes, laço guloso para o while de calcularRoteiro e o par INF com melhor igual a menos um para a ausência no mapa somada à lista de inalcançáveis. A seção 4 trata do reaproveitamento da U1, a 6 da complexidade com V igual a 12 e E igual a 64 lidos do CarregadorMalha, e a 8 da limitação de não otimalidade com os dois custos.

Revisão de cobertura:

Heurística gulosa que vai ao pendente mais próximo — rotaNormalVisitaNaOrdemDoVizinhoMaisProximo.
Distância medida por caminho mínimo e não por aresta direta — escolhaUsaCaminhoMinimoENaoArestaDireta.
Caminho completo contínuo e sem junção repetida — caminhoCompletoEhContinuoESemJuncaoRepetida.
É heurística e não garante o ótimo — roteiroGulosoPodeCustarMaisQueAOrdemAlternativa.
Desempate determinístico pela ordem de entrada — empateVenceOPrimeiroInformado, empateInvertidoVenceOOutro e chamadasRepetidasDevolvemOMesmoResultado.
Ponto sem caminho vai para inalcançáveis — pontoDesconectadoNaoImpedeOsDemais e nenhumAlcancavelDevolveResultadoVazio.
Roteiro parcial preservado quando a rota trava — roteiroParcialEhPreservadoQuandoARotaTrava.
Grafo dirigido, ponto perdido após a parada — maoUnicaDeixaPontoInalcancavelDepoisDaParada.
Borda a, lista de pontos vazia — listaVazia.
Borda b, origem fora do grafo — origemForaDoGrafo.
Borda c, ponto de entrega fora do grafo — pontoDeEntregaForaDoGrafo e idInexistenteNaMalhaVaiParaInalcancaveis.
Borda d, ponto repetido — pontoRepetido.
Borda e, origem dentro da lista — origemDentroDaLista.
Borda f, elemento nulo na lista — elementoNuloNaLista.
Borda g, argumento nulo lança — argumentosNulosLancam.
Listas devolvidas são imutáveis — listasDevolvidasSaoImutaveis e alterarAListaDeEntradaNaoAfetaOResultado.
equals, hashCode e toString do ResultadoRoteiro — equalsConsideraOsInalcancaveis e chamadasRepetidasDevolvemOMesmoResultado.
alcancouTodos — listaVazia, origemForaDoGrafo, pontoDesconectadoNaoImpedeOsDemais e roteiroNaMalhaRealAtendeTodosOsHospitais.
Rota na malha sintética, critério da mãe — roteiroNaMalhaRealAtendeTodosOsHospitais e custoTotalEhASomaDosTrechos.
Rota normal e ponto inalcançável, critério da mãe — rotaNormalVisitaNaOrdemDoVizinhoMaisProximo e pontoDesconectadoNaoImpedeOsDemais.

Não restou nenhum item sem teste. Dois itens do javadoc não são verificáveis por asserção e seguem fora da lista: a complexidade O(k vezes V mais E log V), que é propriedade analítica, e o desacoplamento do framework, verificado por grep na PI3-148 e repetido aqui, sem import de org.springframework ou jakarta.persistence nos dois arquivos de produção.

Validação dos testes:

VizinhoMaisProximoTest: de 16 para 24 testes, 0 falhas
./mvnw test -Dtest=VizinhoMaisProximoTest

Suíte completa: BUILD SUCCESS — de 241 para 249 testes
./mvnw test

CI/CD no GitHub Actions: não executou neste PR. O workflow .github/workflows/ci.yml dispara em "pull_request: branches: [main]", e este PR tem base feat/vizinho-sem-caminho-pi3-149, porque os PRs #52 e #53 ainda não foram mergeados. A aba Checks fica vazia e "gh pr checks 54" responde que não há checks reportados. https://github.com/rsc3-pixel/Projeto3-2026.2/pull/54/checks
Validação local acima: 249 testes, BUILD SUCCESS.

Os testes novos:

Teste

O que prova

roteiroGulosoPodeCustarMaisQueAOrdemAlternativa

O roteiro guloso custa 101 minutos onde a ordem inversa custaria 4: a heurística não garante o ótimo.

empateVenceOPrimeiroInformado

Entre dois pontos equidistantes, vence o primeiro informado na lista de entrada.

empateInvertidoVenceOOutro

Invertendo a ordem de entrada, vence o outro: quem desempata é a entrada, não a ordem interna do grafo.

chamadasRepetidasDevolvemOMesmoResultado

Cinquenta chamadas com a mesma entrada devolvem resultados iguais por equals e hashCode.

maoUnicaDeixaPontoInalcancavelDepoisDaParada

Em grafo dirigido, ponto alcançável da origem mas não de onde o veículo parou vai para inalcançáveis, com o parcial preservado.

listasDevolvidasSaoImutaveis

As três listas do resultado lançam UnsupportedOperationException ao tentar alterar.

alterarAListaDeEntradaNaoAfetaOResultado

Esvaziar a lista do chamador depois da chamada não muda o roteiro já devolvido.

equalsConsideraOsInalcancaveis

Dois roteiros com mesma ordem, caminho e custo são diferentes quando os não atendidos diferem.

Fechamento da PI3-94:

Critério 1, rota calculada para os pontos de entrega da malha sintética — atendido na PI3-149, pelos testes roteiroNaMalhaRealAtendeTodosOsHospitais e custoTotalEhASomaDosTrechos, com a rota registrada na seção 7 do documento da W10.

Critério 2, testes unitários cobrindo rota normal e ponto inalcançável — rota normal atendida na PI3-148 por rotaNormalVisitaNaOrdemDoVizinhoMaisProximo; ponto inalcançável atendido na PI3-149 por pontoDesconectadoNaoImpedeOsDemais e roteiroParcialEhPreservadoQuandoARotaTrava, e reforçado nesta subtarefa por maoUnicaDeixaPontoInalcancavelDepoisDaParada.

Além dos dois critérios, a mãe pedia implementação com lógica equivalente à versão em C/C++, desacoplada do framework web e tratando o caso sem caminho. A equivalência está na seção 3 do documento da W10, o desacoplamento foi verificado por ausência de imports de framework nos dois arquivos de produção, e o tratamento do caso sem caminho foi entregue na PI3-149.

Decisão técnica:

A não otimalidade foi demonstrada em teste, e não apenas afirmada no javadoc, porque é o tipo de limitação que alguém tende a confundir com defeito ao ler o código meses depois. Um teste que mostra o guloso fechando em 101 onde existe caminho de 4 transforma a frase em fato verificável, e o DisplayName registra que o comportamento é esperado. O custo da ordem alternativa é calculado pelo Dijkstra dentro do próprio teste, em vez de escrito como constante, para que a comparação continue válida se os pesos do grafo de teste mudarem.

O desempate é testado invertendo a entrada porque um único teste não distinguiria a causa: se o roteiro escolhe A antes de B, isso tanto pode vir da ordem de entrada quanto da ordem de iteração interna do grafo ou do mapa de distâncias. Com os dois sentidos, a única explicação que sobra é a ordem informada pelo chamador. O terceiro teste, com cinquenta repetições da mesma entrada, cobre o risco complementar: ordem de HashMap variando entre execuções.

O grafo dirigido entrou porque o caso de mão única é o único em que um ponto é alcançável no começo do roteiro e deixa de ser no meio, sem que nada no grafo tenha mudado. O teste anterior que travava a rota usava grafo dirigido, mas sem isolar essa assimetria: ali o ponto já era inalcançável de qualquer vértice que não fosse a origem. Aqui a afirmação do javadoc é verificada diretamente, com assertivas sobre o mapa de distâncias a partir da origem e a partir do ponto de parada.

A comparação com C/C++ é com o algoritmo clássico porque não existe outra opção honesta: a busca por arquivos .c, .cpp, .h e .hpp em aed encontrou apenas a Unidade 1, com lista encadeada, fila e pilha, e nenhuma ocorrência de matriz de distâncias ou vetor visitado. Inventar um trecho e apresentá-lo como o código visto em sala produziria um documento que não resiste a conferência. O documento declara a ausência e descreve a versão clássica como referência, deixando claro o que é literatura e o que é código do repositório.
