EVIDÊNCIAS DE ENTREGA — PI3-149 (Implementar a heurística do vizinho mais próximo em Java)

O que foi feito:

Foi implementada a heurística do vizinho mais próximo para roteirização, em duas classes novas no pacote com.rotavital.estruturas: VizinhoMaisProximo, com o núcleo do algoritmo, e ResultadoRoteiro, o objeto de resultado. A heurística é gulosa: partindo da origem, vai sempre ao ponto de entrega ainda não visitado que estiver mais perto da posição atual, e repete dali até esgotar os pendentes. A decisão central da entrega é como essa proximidade é medida. A malha da rede é esparsa por decisão de projeto — cada unidade se liga apenas às 4 geograficamente mais próximas, constante VIZINHOS_POR_UNIDADE em PremissasMalha, justificada na seção 5 do W02-escopo-grafo-rede-distribuicao.md — então entre dois pontos de entrega quaisquer muitas vezes não existe aresta direta, e quando existe ela não é necessariamente o trajeto mais rápido. Por isso a distância de cada passo é o custo do caminho mínimo, obtido do Dijkstra já existente, e não o peso de uma aresta: medir por aresta direta faria pontos perfeitamente alcançáveis parecerem inalcançáveis e os deixaria fora do roteiro. Caminho mínimo não foi reimplementado. As duas classes são Java puro e importam apenas java.util, sem Spring, sem JPA e sem nenhuma referência às camadas de API, serviço ou repositório, para que a POO possa consumi-las diretamente.

Reuso, não reconstrução:

Grafo, Dijkstra e ResultadoRota foram usados sem nenhuma alteração. O Grafo entra como dependência do construtor e é consultado nos testes por contemAresta e pesoAresta para confirmar as premissas do cenário. O Dijkstra entra de duas formas por passo do laço: calcularDistancias(atual) mede de uma vez o custo até todos os pendentes, e a ausência de um pendente nesse mapa é o próprio sinal de que não há caminho a partir da posição atual — contrato que o Dijkstra já estabelecia, devolvendo apenas os alcançáveis em vez de um valor sentinela de infinito; e calcularRota(atual, escolhido) reconstrói o trecho que é concatenado ao caminho total. O ResultadoRota entra como valor de retorno desse trecho e como molde do ResultadoRoteiro, que repete o mesmo padrão: classe final, campos finais, construtor privado, fábrica estática, List.copyOf na entrada e equals, hashCode e toString redefinidos. No código do repositório, o Dijkstra e o ResultadoRota são atribuídos à PI3-19 e a modelagem do grafo à PI3-13; o identificador PI3-53 citado no enunciado desta subtarefa não aparece em nenhum arquivo do repositório.

Pull Request #52:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/52

Commit:
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/95db07520a88d44b293c4f38e2766c9e420f0d31

Código:

VizinhoMaisProximo — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-mais-proximo-pi3-XXX/src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java
ResultadoRoteiro — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-mais-proximo-pi3-XXX/src/main/java/com/rotavital/estruturas/ResultadoRoteiro.java
VizinhoMaisProximoTest — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-mais-proximo-pi3-XXX/src/test/java/com/rotavital/estruturas/VizinhoMaisProximoTest.java
Dijkstra (reusado, atribuído à PI3-19 no próprio javadoc) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/main/src/main/java/com/rotavital/estruturas/Dijkstra.java
Grafo (reusado) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/main/src/main/java/com/rotavital/estruturas/Grafo.java
ResultadoRota (reusado, molde do ResultadoRoteiro) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/main/src/main/java/com/rotavital/estruturas/ResultadoRota.java

Critérios de aceite:

A rota visita todos os pontos alcançáveis, uma vez cada, começando pela origem

A lista de entrada é convertida em LinkedHashSet, o que descarta pontos repetidos sem perder a ordem informada, e a própria origem é removida desse conjunto porque o veículo já está nela. Cada ponto escolhido é retirado dos pendentes no mesmo passo em que é registrado na ordem de visita, então nenhum pode ser visitado duas vezes. O caminho completo é inicializado com a origem antes do laço, de modo que ele começa nela mesmo quando nenhum ponto é alcançado. Verificado em rotaNormalVisitaNaOrdemDoVizinhoMaisProximo, que confere a ordem de visita completa, o caminho vértice a vértice começando em CD e a contagem de visitados.

A cada passo é escolhido o ponto não visitado de menor custo

Um único calcularDistancias por passo mede o custo da posição atual até todos os pendentes. O método privado maisProximoEntre percorre os pendentes na ordem do LinkedHashSet e só troca o candidato com comparação estrita menor que, nunca menor ou igual, de forma que um empate preserve o primeiro informado — desempate determinístico, pelo mesmo motivo registrado para a regra de desempate da seleção FEFO no W02: sem critério secundário estável, a mesma entrada poderia produzir roteiros diferentes entre execuções e não haveria como auditar a escolha. Pendente ausente do mapa de distâncias é ignorado, porque ausência significa sem caminho. Verificado em escolhaUsaCaminhoMinimoENaoArestaDireta, onde H1 é informado depois de H2 na lista e ainda assim é visitado primeiro, por custar 5 contra 8.

Caminho completo contínuo

O caminho de cada trecho devolvido pelo calcularRota começa no vértice onde o roteiro já está, então a concatenação descarta o primeiro elemento do trecho, via subList(1, tamanho), e o vértice de junção não aparece duas vezes. Verificado em caminhoCompletoEhContinuoESemJuncaoRepetida: o teste assere que o caminho começa na origem, percorre todos os pares consecutivos confirmando com grafo.contemAresta que cada trecho existe de verdade no grafo, assere que dois vértices consecutivos nunca são iguais, e confere que H1 e H2, que são as junções entre trechos neste roteiro, aparecem exatamente uma vez cada.

Desacoplado do framework web

Comando executado na raiz do projeto:

grep -nE 'org\.springframework|jakarta\.persistence|com\.rotavital\.(api|servico|repositorio)' src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java src/main/java/com/rotavital/estruturas/ResultadoRoteiro.java

Saída vazia, sem nenhuma ocorrência. Os imports dos dois arquivos são apenas java.util.ArrayList, java.util.LinkedHashSet, java.util.List, java.util.Map e java.util.Set no VizinhoMaisProximo, e java.util.List e java.util.Objects no ResultadoRoteiro.

Grafo, Dijkstra e ResultadoRota não alterados

Comando executado:

git diff --stat main -- src/main/java/com/rotavital/estruturas/Grafo.java src/main/java/com/rotavital/estruturas/Dijkstra.java src/main/java/com/rotavital/estruturas/ResultadoRota.java

Saída vazia: nenhum dos três arquivos difere da main.

Validação dos testes:

VizinhoMaisProximoTest: 3 testes, 0 falhas
./mvnw test -Dtest=VizinhoMaisProximoTest

Suíte completa: BUILD SUCCESS — de 225 para 228 testes
./mvnw test

CI/CD no GitHub Actions: executou e passou.
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/52/checks
Os jobs Build, Testes e Empacotamento passam, e o Deploy na VM aparece como skipping porque o deploy só roda em push para a main. O link acima é o da aba Checks do PR, que mostra sempre a execução correspondente ao estado atual da branch; a execução não é fixada aqui por identificador porque o histórico da branch foi reescrito uma vez, para corrigir o número do card nas mensagens de commit, e um identificador fixo passaria a apontar para commits que não existem mais.

Os testes novos:

Teste

O que prova

rotaNormalVisitaNaOrdemDoVizinhoMaisProximo

Com três pontos de entrega, a ordem de visita, o caminho vértice a vértice e o custo total de 15 minutos saem exatamente como calculado à mão (5 + 4 + 6).

escolhaUsaCaminhoMinimoENaoArestaDireta

H1 não tem aresta direta com a origem e ainda assim é visitado primeiro, porque custa 5 por caminho contra os 8 da aresta direta até H2: a decisão vem do caminho mínimo, não da adjacência.

caminhoCompletoEhContinuoESemJuncaoRepetida

Todo par consecutivo do caminho é uma aresta existente no grafo, o caminho começa na origem e o vértice de junção entre trechos aparece uma única vez.

Complexidade:

O(k · (V + E) log V) para k pontos de entrega, V vértices e E arestas. O laço executa no máximo k vezes, uma por ponto visitado, e cada iteração roda dois Dijkstra: um calcularDistancias para medir todos os pendentes de uma vez e um calcularRota para reconstruir o trecho até o escolhido. Cada Dijkstra com heap binário custa O((V + E) log V), o mesmo valor já registrado para o algoritmo no W02 e nas evidências da Unidade 1, e a constante 2 não altera a ordem de grandeza. A busca do menor entre os pendentes é O(k) por passo e fica dominada pelo custo do Dijkstra, já que k é no máximo V. O Dijkstra é instanciado uma única vez no construtor e reusado em todos os passos, porque não guarda estado entre chamadas além da referência ao grafo.

Ficou para as próximas subtarefas:

Subtarefa 2: tratamento formal do caso sem caminho, com a lista de pontos não atendidos e o alcancouTodos() no ResultadoRoteiro, as bordas e a aplicação na malha sintética. O ponto exato onde esse registro entra está marcado no código com o comentário PI3-subtarefa-2, dentro do if que interrompe o laço: os pendentes que sobram naquele instante são justamente a lista de inalcançáveis. O ResultadoRoteiro foi desenhado para receber o campo sem mudança estrutural, bastando um parâmetro a mais na fábrica estática.

Subtarefa 3: bateria completa de testes e documento da W10. Esta subtarefa trouxe apenas os três testes necessários para provar o núcleo, conforme combinado. Ficaram sem teste, por pertencerem às próximas: a validação de argumentos nulos no construtor e no calcularRoteiro, que já está implementada e lança IllegalArgumentException; o cenário de ponto de entrega inalcançável; o roteiro sobre a malha sintética das 12 unidades; e a demonstração de que a heurística pode não ser ótima.

Decisão técnica:

A distância é medida por caminho mínimo e não por aresta direta porque a malha é esparsa por decisão registrada na seção 5 do W02: cada unidade liga apenas às 4 mais próximas. Numa matriz de distâncias completa, como a dos exemplos de sala, "o mais próximo" é uma consulta direta; aqui, consultar pesoAresta entre dois pontos de entrega devolve nulo na maioria dos pares, e o roteiro descartaria pontos que têm caminho perfeitamente válido por duas ou três pernas. O teste escolhaUsaCaminhoMinimoENaoArestaDireta existe exatamente para fixar essa diferença: no cenário dele, a decisão por aresta direta inverteria a ordem de visita.

Os pendentes ficam em LinkedHashSet por dois motivos somados. Como conjunto, descarta pontos repetidos na entrada sem código extra e remove a origem em uma linha. Como estrutura que preserva a ordem de inserção, define o critério de desempate sem precisar de um comparador: percorrendo nessa ordem e trocando o candidato apenas com comparação estrita, o primeiro ponto informado vence o empate. Um HashSet comum resolveria a parte de conjunto, mas a ordem de iteração dele não é garantida e o roteiro poderia variar entre execuções para a mesma entrada.

O objeto de resultado é imutável no padrão do ResultadoRota porque o roteiro circula entre camadas e não deve poder ser alterado por quem o recebe: a fábrica aplica List.copyOf nas duas listas e os getters devolvem as cópias imutáveis. Seguir o padrão já existente, em vez de inventar outro, também mantém previsível o que a próxima pessoa encontra ao abrir o arquivo. O ResultadoRoteiro separa ordemDeVisita de caminhoCompleto porque são perguntas diferentes: a primeira responde em que sequência as entregas acontecem, a segunda por quais unidades o veículo passa — e numa malha esparsa o caminho inclui unidades de passagem que não são ponto de entrega, como o vértice P do grafo de teste.

Quando nenhum pendente é alcançável a partir da posição atual, o laço é interrompido em vez de lançar exceção. Lançar transformaria uma situação normal da operação — ponto de entrega sem caminho a partir de onde o veículo parou — em erro, e descartaria o roteiro parcial já calculado, que é justamente a informação útil. Interromper preserva o que foi possível entregar e deixa os pendentes disponíveis para o registro formal da subtarefa 2. É também a condição que garante o término do laço: sem ela, a iteração reexecutaria o mesmo Dijkstra indefinidamente, já que nada mudaria entre as voltas.


================================================================================


EVIDÊNCIAS DE ENTREGA — PI3-149 (Tratar o caso "sem caminho" e aplicar na malha sintética)

Esta é a segunda entrega registrada sob a mesma chave PI3-149. A primeira, acima, cobriu o núcleo da heurística e o objeto de resultado. Esta cobre o tratamento dos pontos sem caminho, as bordas de entrada e a aplicação na malha sintética das 12 unidades.

O que foi feito:

O roteiro passou a tratar formalmente o caso em que um ponto de entrega não pode ser atendido. O ResultadoRoteiro ganhou dois membros, pontosInalcancaveis() e alcancouTodos(), e o VizinhoMaisProximo passou a preencher a lista no exato ponto em que o laço era interrompido: quando nenhum pendente é alcançável a partir da posição atual, o que sobrou no conjunto de pendentes é, por definição, o conjunto dos não atendidos, já na ordem em que foram informados, porque o LinkedHashSet preserva a ordem de inserção. O roteiro parcial calculado até ali é devolvido junto, em vez de descartado. Nenhuma dessas situações lança exceção. Além disso, as sete bordas de entrada foram implementadas e documentadas no javadoc de calcularRoteiro: lista de pontos vazia, origem fora do grafo, ponto de entrega fora do grafo, ponto repetido na lista, origem dentro da lista de pontos e elemento nulo na lista produzem resultado tratado; apenas origem nula, lista nula e grafo nulo lançam IllegalArgumentException, por serem erro de programa de quem chamou. Por fim, a heurística foi aplicada à malha real: o VizinhoMaisProximo continua genérico em T e não ganhou nenhuma dependência do pacote rede — a malha entra somente no teste, que é o consumidor.

Durante a implementação foi corrigido um defeito que vinha da entrega anterior: elemento nulo dentro da lista de pontos era aceito pelo LinkedHashSet sem reclamação e só estourava NullPointerException no List.copyOf da fábrica do resultado, longe da causa real. Agora o nulo é descartado na montagem dos pendentes, um a um.

Reuso, não reconstrução:

Da entrega anterior vieram o VizinhoMaisProximo e o ResultadoRoteiro, que foram estendidos e não reescritos: o laço da heurística, a medição por caminho mínimo e o desempate por ordem de entrada continuam como estavam. Da Unidade 1 vieram Grafo, Dijkstra e ResultadoRota, usados sem alteração; o contrato do calcularDistancias, que devolve mapa vazio para origem inexistente e omite os inalcançáveis em vez de usar valor sentinela, é o que faz as bordas b) e c) funcionarem sem caso especial no código. O precedente seguido para as decisões de erro é o ServicoAlocacaoRota: ele trata solicitante fora da malha como falta de caminho, com o comentário "solicitante fora da malha devolve mapa vazio e cai em SEM_CAMINHO, sem caso especial", e descarta bolsa nula na lista de estoque enquanto lança IllegalArgumentException para chave ou lista nulas. A mesma assimetria foi adotada aqui. A representação de ausência como estado e não como erro segue o ResultadoRota.semCaminho() e o MotivoFalhaAlocacao (PI3-57). Não foram alterados: Grafo, Dijkstra, ResultadoRota, CarregadorMalha, UnidadeRede e PremissasMalha.

Pull Request #53:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/53

Commit:
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/0189fc1c11dbb5ecc4feffc089b6d4be2b504d6a

Código:

VizinhoMaisProximo — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-sem-caminho-pi3-149/src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java
ResultadoRoteiro — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-sem-caminho-pi3-149/src/main/java/com/rotavital/estruturas/ResultadoRoteiro.java
VizinhoMaisProximoTest — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-sem-caminho-pi3-149/src/test/java/com/rotavital/estruturas/VizinhoMaisProximoTest.java
CarregadorMalha (usado, não alterado) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/vizinho-sem-caminho-pi3-149/src/main/java/com/rotavital/rede/CarregadorMalha.java

Critérios de aceite:

Ponto inalcançável não interrompe o roteiro e é listado como não atendido, sem exceção

Quando o método privado maisProximoEntre devolve null, nenhum pendente aparece no mapa de distâncias da posição atual. O laço é interrompido ali, e os pendentes restantes são copiados para a lista de não atendidos, preservando a ordem de entrada. O roteiro parcial segue intacto: ordem de visita, caminho completo e custo acumulado até aquele ponto entram no resultado. Verificado em pontoDesconectadoNaoImpedeOsDemais, que insere um vértice sem arestas e confere que os dois alcançáveis são roteirizados enquanto o isolado aparece em pontosInalcancaveis; em roteiroParcialEhPreservadoQuandoARotaTrava, que usa um grafo dirigido onde C só é alcançável a partir do CD e confirma que, depois de ir para A e B, o roteiro devolve ordem [A, B], caminho [CD, A, B], custo 2 e inalcançáveis [C]; e em nenhumAlcancavelDevolveResultadoVazio, com assertDoesNotThrow.

Comportamento das bordas definido e documentado

a) Lista de pontos vazia: ordem de visita vazia, caminho contendo só a origem, custo zero e alcancouTodos() verdadeiro, porque não havia nada a atender. Teste listaVazia.

b) Origem fora do grafo: nenhum ponto visitado, todos em pontosInalcancaveis, caminho contendo só a origem, custo zero e nenhuma exceção. Teste origemForaDoGrafo, com assertDoesNotThrow.

c) Ponto de entrega fora do grafo: nunca aparece no mapa de distâncias, então vai para a lista de não atendidos enquanto os demais são roteirizados normalmente. Testes pontoDeEntregaForaDoGrafo e idInexistenteNaMalhaVaiParaInalcancaveis, este na malha real com o id HC99.

d) Ponto repetido na lista: visitado uma única vez e, se inalcançável, listado uma única vez. Teste pontoRepetido, que verifica os dois lados.

e) Origem dentro da lista de pontos: ignorada, porque o veículo já está nela. Teste origemDentroDaLista.

f) Elemento nulo dentro da lista: descartado em silêncio, sem NullPointerException. Teste elementoNuloNaLista, que monta a entrada com Arrays.asList porque List.of não aceitaria nulo.

g) Origem nula, lista nula ou grafo nulo: IllegalArgumentException, por ser erro de programa. Teste argumentosNulosLancam, com três assertThrows.

Rota calculada para os pontos de entrega da malha sintética

O teste carrega CarregadorMalha.carregar(), usa o HC01 como origem e deriva os pontos de entrega de UnidadeRede.TODAS, filtrando pelo tipo AGENCIA_TRANSFUSIONAL em vez de escrever os ids à mão, para não desalinhar se a malha mudar. Verifica alcancouTodos() verdadeiro, lista de inalcançáveis vazia, todos os oito hospitais na ordem de visita exatamente uma vez cada, caminho começando na origem com todo par consecutivo confirmado por grafo.contemAresta, e custo total maior que zero. Um segundo teste recalcula o custo perna a perna com o Dijkstra, de forma independente do laço interno, e compara com o custo devolvido. Testes roteiroNaMalhaRealAtendeTodosOsHospitais e custoTotalEhASomaDosTrechos.

Rota obtida na malha sintética:
Origem: HC01 - Hemocentro Recife (HEMOPE)
Pontos pedidos: HC04, HC05, HC06, HC07, HC09, HC10, HC11, HC12
Ordem de visita: HC04, HC05, HC06, HC09, HC12, HC11, HC10, HC07
Caminho completo: HC01, HC04, HC05, HC04, HC06, HC09, HC12, HC11, HC10, HC05, HC02, HC07
Custo total: 173,6 minutos

O caminho tem 12 vértices e repete HC04 e HC05, além de passar por HC02, que não é ponto de entrega. Isso é esperado numa malha esparsa, onde cada unidade se liga apenas às quatro mais próximas: ir de um hospital a outro atravessa unidades intermediárias. É exatamente por isso que ordemDeVisita e caminhoCompleto são listas separadas.

Validação dos testes:

VizinhoMaisProximoTest: de 3 para 16 testes, 0 falhas
./mvnw test -Dtest=VizinhoMaisProximoTest

Suíte completa: BUILD SUCCESS — de 228 para 241 testes
./mvnw test

CI/CD no GitHub Actions: não executou neste PR. O workflow .github/workflows/ci.yml dispara em "pull_request: branches: [main]", e este PR tem base feat/vizinho-mais-proximo-pi3-XXX, porque o PR #52 ainda não foi mergeado. A aba Checks fica vazia e "gh pr checks 53" responde "no checks reported". https://github.com/rsc3-pixel/Projeto3-2026.2/pull/53/checks
Validação local acima: 241 testes, BUILD SUCCESS.

Os testes novos:

Teste

O que prova

pontoDesconectadoNaoImpedeOsDemais

Vértice isolado vai para inalcançáveis enquanto os demais são roteirizados normalmente.

roteiroParcialEhPreservadoQuandoARotaTrava

Quando a rota trava no meio, ordem, caminho e custo até ali sobrevivem no resultado.

nenhumAlcancavelDevolveResultadoVazio

Nenhum ponto alcançável ainda devolve resultado, com a ordem de entrada preservada nos não atendidos.

listaVazia

Lista de pontos vazia dá caminho só com a origem, custo zero e alcancouTodos() verdadeiro.

origemForaDoGrafo

Origem fora do grafo não lança: todos os pontos viram não atendidos.

pontoDeEntregaForaDoGrafo

Ponto inexistente vai para inalcançáveis sem impedir os demais.

pontoRepetido

Repetido na entrada é visitado uma vez e, se inalcançável, listado uma vez.

origemDentroDaLista

Origem informada entre os pontos é ignorada e não entra em nenhuma das listas.

elementoNuloNaLista

Nulo na lista é descartado em silêncio, sem NullPointerException.

argumentosNulosLancam

Origem nula, lista nula e grafo nulo lançam IllegalArgumentException.

roteiroNaMalhaRealAtendeTodosOsHospitais

Na malha das 12 unidades, os oito hospitais são atendidos uma vez cada por um caminho contínuo.

custoTotalEhASomaDosTrechos

O custo devolvido confere com a soma das pernas recalculada pelo Dijkstra.

idInexistenteNaMalhaVaiParaInalcancaveis

Id fora da faixa HC01 a HC12 é devolvido como não atendido sem derrubar o roteiro.

Ficou para a subtarefa 3:

Demonstração de que a heurística não é ótima, cobertura explícita do desempate com receptor de mesma distância, revisão geral da cobertura e o documento da W10 com a equivalência entre a versão Java e a versão em C/C++ vista em sala.

Decisão técnica:

Ponto inalcançável é resultado e não exceção porque é situação normal da operação, não defeito de programa. Lançar descartaria o roteiro parcial já calculado, que é justamente a informação útil para quem despacha: saber que seis das oito entregas são viáveis vale mais do que um erro que não diz nada. O projeto já havia tomado essa decisão duas vezes, no ResultadoRota.semCaminho() e no MotivoFalhaAlocacao, e repeti-la aqui mantém o mesmo contrato em toda a camada de estruturas.

Origem fora do grafo também não é exceção pelo mesmo motivo, com um reforço: o Dijkstra já devolve mapa vazio nesse caso, e o ServicoAlocacaoRota já registra em comentário que solicitante fora da malha cai em falta de caminho sem caso especial. Tratar como erro aqui criaria uma terceira resposta para a mesma situação, divergindo do que o resto do projeto faz. Na prática, o caso cai naturalmente no mesmo ramo dos inalcançáveis, sem código dedicado.

Nulo dentro da lista é descartado e nulo como argumento lança porque são defeitos de naturezas diferentes. Uma lista com buraco costuma vir de dado de entrada imperfeito, como um registro incompleto, e descartar o buraco preserva o resto do pedido; é o que o ServicoAlocacaoRota faz com bolsa nula no estoque. Já passar null como a própria lista, ou como a origem, é erro de quem chamou: não há pedido a preservar, e devolver um resultado vazio esconderia o defeito e faria toda chamada seguinte responder sem motivo aparente.

O módulo continua genérico em T e a malha entra só no teste porque acoplar VizinhoMaisProximo ao pacote rede o amarraria a String como tipo de vértice e a um carregador específico, sem ganho: o algoritmo não precisa saber o que o vértice representa. Mantendo a genericidade, a mesma classe serve para a malha real, para os grafos pequenos dos testes e para qualquer outro consumidor. A aplicação na malha é responsabilidade de quem consome, e no escopo desta subtarefa esse consumidor é o teste.
