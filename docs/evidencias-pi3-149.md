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
