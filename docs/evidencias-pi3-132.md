EVIDÊNCIAS DE ENTREGA — PI3-132 (Explicar como colisões são tratadas no índice)

O que foi feito:

O card afirmava que a colisão no índice é rara por construção. A medição mostrou que era rara por sorte. A ChaveEstoque usava Objects.hash sobre os dois enums, e o hashCode de enum é o hash de identidade herdado de Object, que a especificação não garante entre execuções: depende da ordem de alocação e até de flags da máquina virtual. Com as 32 combinações possíveis numa tabela de 64 baldes, o número de combinações caindo em balde já ocupado foi 8 no modo padrão da HotSpot, mas variou de 6 a 31 conforme o modo de hash de identidade. No modo 2, que devolve hash constante, as 32 chaves caem todas no mesmo balde, com cadeia de 32. As cadeias reais eram curtas e inofensivas, muito abaixo das 8 entradas que acionariam a árvore, mas o projeto não controlava quantas seriam.

A correção foi pequena e testada. A chave passou a usar hash perfeito, grupo.ordinal() vezes a quantidade de tipos mais tipo.ordinal(), que mapeia as 32 combinações em valores distintos de 0 a 31, iguais em toda execução porque ordinal é a posição declarada do enum e não um endereço. O mapa passou a ser criado com capacidade 64: com valores abaixo de dois elevado a dezesseis o espalhamento do HashMap não altera nada, o índice do balde é o próprio hash, e 32 fica abaixo do limite de carga de 48, então a tabela nunca é redimensionada. O resultado é zero colisão por construção, verificável e determinística.

A documentação cobre os três pontos pedidos: como o HashMap do Java resolve colisão, por que esta chave distribui bem, e por que agora a ausência de colisão é por construção. O javadoc da classe e o documento docs/W10-indice-estoque-hash.md explicam encadeamento, transformação em árvore e volta a lista, com as constantes lidas do código-fonte do JDK 21 do próprio projeto.

Reuso, não reconstrução:

O IndiceEstoque entregue na PI3-131 foi mantido: a estrutura de mapa com chave composta, a semântica de adicionar, buscar e remover, a cópia imutável no retorno, o tratamento de argumento nulo e a ausência de duplicata continuam como estavam. Mudaram apenas duas coisas: o corpo do hashCode da ChaveEstoque e a capacidade inicial do mapa. A API pública é idêntica, e por isso o IndiceEstoqueServico e a alocação automática não foram tocados, o que foi comprovado pelo comando "git diff --stat feat/indice-estoque-alocacao-pi3-131 -- src/main/java/com/rotavital/servico src/main/java/com/rotavital/api", cuja saída é vazia. Os 14 testes que o IndiceEstoqueTest já tinha passam sem alteração de asserção, e os 11 do AlocacaoAutomaticaTest também. O único arquivo de produção alterado foi o IndiceEstoque.

Pull Request #67:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/67

Commits:
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/07f7e5ff16919c11c114c7ce3a4a76998920a68d
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/5d4e83e22ef8aaaf75856cf59154f3aea81d401a

Código e documentação:

IndiceEstoque — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/indice-hash-colisao-pi3-132/src/main/java/com/rotavital/estruturas/IndiceEstoque.java
IndiceEstoqueTest — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/indice-hash-colisao-pi3-132/src/test/java/com/rotavital/estruturas/IndiceEstoqueTest.java
W10-indice-estoque-hash.md — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/indice-hash-colisao-pi3-132/docs/W10-indice-estoque-hash.md
complexidade-estruturas.md — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/indice-hash-colisao-pi3-132/docs/complexidade-estruturas.md
W04-estruturas-base.md — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/indice-hash-colisao-pi3-132/docs/W04-estruturas-base.md
evidencias-unidade-1-aed.md — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/indice-hash-colisao-pi3-132/docs/evidencias-unidade-1-aed.md

Diagnóstico antes da mudança:

Medição feita com um programa temporário que reproduz a conta do HashMap: espalhamento h xor (h deslocado 16 bits à direita), índice igual a (n menos 1) e hash. O programa foi apagado e não versionado.

Com o modo padrão da HotSpot, as 32 combinações da chave antiga distribuíam assim:
capacidade 16: 13 baldes usados, 19 combinações em balde já ocupado, maior cadeia 5
capacidade 32: 18 baldes usados, 14 combinações em balde já ocupado, maior cadeia 4
capacidade 64: 24 baldes usados, 8 combinações em balde já ocupado, maior cadeia 3

Duas observações importantes sobre a estabilidade. Rodando o mesmo programa cinco vezes seguidas, o resultado foi sempre 8 colisões em capacidade 64, e o hashCode de O_NEG foi sempre 225534817: o valor parece estável. Porém, rodando um segundo programa na mesma máquina virtual e com o mesmo classpath, o hashCode de O_NEG foi 366712642 e o número de colisões caiu para 6. O hash de identidade depende da ordem de alocação, então qualquer mudança no que o programa faz antes altera os valores. A premissa de que ele muda a cada execução não se confirmou nesta JVM, mas a conclusão do card se mantém: o número de colisões não era controlado pelo código.

A prova mais direta veio de variar o modo de hash de identidade da HotSpot, com -XX:+UnlockExperimentalVMOptions -XX:hashCode=N, sempre em capacidade 64:
modo 0: 9 colisões, maior cadeia 3
modo 1: 10 colisões, maior cadeia 2
modo 2: 31 colisões, maior cadeia 32
modo 3: 6 colisões, maior cadeia 2
modo 4: 24 colisões, maior cadeia 5
modo 5, o padrão: 8 colisões, maior cadeia 3

O modo 2 devolve hash constante e põe as 32 chaves no mesmo balde, transformando a busca em O(n). É modo de diagnóstico e não de produção, mas demonstra que o número de colisões dependia de algo fora do código da aplicação.

Depois da mudança, as 32 combinações produzem hashes distintos de 0 a 31 e caem em 32 baldes distintos numa tabela de 64, em qualquer execução e com qualquer flag.

Critérios de aceite:

Javadoc explicando o tratamento de colisão

No javadoc da classe IndiceEstoque, em quatro parágrafos. O primeiro descreve como o HashMap resolve colisão em geral: encadeamento no balde, transformação em árvore rubro-negra quando o balde passa de TREEIFY_THRESHOLD igual a 8 entradas e a tabela tem ao menos MIN_TREEIFY_CAPACITY igual a 64 baldes, redimensionamento em vez de árvore quando a tabela é menor, e volta a lista abaixo de UNTREEIFY_THRESHOLD igual a 6, com a fórmula do índice do balde. O segundo explica por que nada disso é acionado aqui: espaço de chaves pequeno e fechado, hash perfeito por ordinal e capacidade dimensionada. O terceiro registra o estado anterior e os números medidos. O quarto trata de manutenção: a fórmula continua perfeita se um enum ganhar valores porque usa values().length, mas a capacidade precisa ser revista, e o hash não é persistido, então mudar a ordem dos valores não corrompe dado gravado. Um quinto parágrafo distingue colisão de agrupamento. O javadoc da ChaveEstoque e do hashDaChave complementam.

Documentação em docs/ atualizada

Documento novo: docs/W10-indice-estoque-hash.md, com dez seções, no estilo dos demais W0x do projeto. Três documentos existentes foram atualizados. O complexidade-estruturas.md dizia custo médio O(1) para localizar a chave; passou a dizer O(1), ganhou um parágrafo sobre o hash perfeito e a capacidade, e um apontamento para o documento novo. O W04-estruturas-base.md também dizia custo médio e ganhou nota de atualização com apontamento. O evidencias-unidade-1-aed.md tinha um erro factual: afirmava que a ChaveEstoque era um record, quando ela sempre foi uma classe interna privada, o que foi conferido no commit original da W04, c5f76e0. Também dizia que a busca devolvia visão não modificável, o que deixou de valer na PI3-131, quando passou a devolver cópia. As duas correções entraram com nota de atualização, sem reescrever o registro histórico, e a contagem de testes foi atualizada de 4 para 21.

Como o HashMap resolve colisão, com encadeamento e árvore acima do limite

Seção 2 do documento novo. As constantes foram lidas do código-fonte do JDK 21 usado pelo projeto, em lib/src.zip, java.base/java/util/HashMap.java, com a linha de cada uma: DEFAULT_INITIAL_CAPACITY igual a 16 na linha 238, DEFAULT_LOAD_FACTOR igual a 0,75 na linha 250, TREEIFY_THRESHOLD igual a 8 na linha 260, UNTREEIFY_THRESHOLD igual a 6 na linha 267 e MIN_TREEIFY_CAPACITY igual a 64 na linha 275. O espalhamento está na linha 338 e o cálculo do índice na linha 636. A condição que dispara a árvore está na linha 649 e o desvio para redimensionamento quando a tabela é pequena está na linha 763. Nada foi afirmado sem conferência na fonte.

Por que a chave distribui bem

Seção 5 do documento. O mérito não é da função de hash, é do espaço de chaves: pequeno, com 8 grupos vezes 4 tipos igual a 32 combinações; fechado, porque enum tem conjunto finito conhecido em tempo de compilação, sem combinação inválida nem variação de maiúscula ou espaço que uma chave de texto concatenado traria; e previsível, porque o número de chaves não cresce com o estoque. É justamente por ser pequeno e fechado que o hash perfeito é possível, o que não valeria para uma chave aberta como um código de rastreio.

Colisão rara por construção, e não por sorte

A fórmula é grupo.ordinal() vezes TipoHemocomponente.values().length mais tipo.ordinal(), e a capacidade é 64. Três testes provam. O hashPerfeitoSobreAsTrintaEDuasCombinacoes percorre as 32 combinações e verifica que todos os hashes são distintos e estão entre 0 e 31. O nenhumBaldeCompartilhadoComCapacidadeSessentaEQuatro reproduz a conta do HashMap, espalhamento e máscara, e verifica que os 32 baldes são distintos, além de assertar que o espalhamento não altera o valor por ele estar abaixo de dois elevado a dezesseis. O hashEhEstavelEDeterministico fixa o valor pela fórmula e ancora as pontas do intervalo em 0 e 31, sem depender da máquina virtual.

Validação dos testes:

IndiceEstoqueTest: de 14 para 21 testes, 0 falhas
./mvnw test -Dtest=IndiceEstoqueTest

AlocacaoAutomaticaTest: 11 testes, 0 falhas, sem alteração
./mvnw test -Dtest=AlocacaoAutomaticaTest

Suíte completa: BUILD SUCCESS — de 246 para 253 testes
./mvnw test

CI/CD no GitHub Actions: não executou neste PR. O workflow .github/workflows/ci.yml dispara em "pull_request: branches: [main]", e este PR tem base feat/indice-estoque-alocacao-pi3-131, porque o PR #65 ainda não foi mergeado. A aba Checks fica vazia. https://github.com/rsc3-pixel/Projeto3-2026.2/pull/67/checks
Validação local acima: 253 testes, BUILD SUCCESS.

Os testes novos:

Teste

O que prova

espacoDeChavesEhFechadoEPequeno

São 8 grupos e 4 tipos, logo 32 combinações: o espaço de chaves é fechado e pequeno.

hashPerfeitoSobreAsTrintaEDuasCombinacoes

As 32 combinações produzem hashes distintos, todos entre 0 e 31.

nenhumBaldeCompartilhadoComCapacidadeSessentaEQuatro

Reproduzindo a conta do HashMap em tabela de 64, as 32 caem em baldes distintos: zero colisão.

hashEhEstavelEDeterministico

O hash é ordinal vezes 4 mais ordinal, igual em toda execução, sem depender da máquina virtual.

contratoEqualsHashCode

Nas comparações de todas contra todas, hash igual acontece se e somente se a combinação é igual.

cadaCombinacaoDevolveApenasASuaBolsa

Com as 32 combinações indexadas, cada busca devolve exatamente a sua bolsa e nenhuma de outra.

bolsasDaMesmaChaveFicamJuntas

Três bolsas da mesma combinação ficam na mesma lista: agrupamento desejado, não colisão de hash.

Decisão técnica:

O hashCode foi mudado em vez de apenas documentado porque o critério de aceite pedia explicar que a colisão é rara por construção, e a medição mostrou que ela era rara por sorte. Documentar a afirmação sem corrigir o código produziria um documento que não resiste a conferência: bastaria alguém rodar a medição para encontrar as 8 colisões. A mudança é pequena, cabe em duas linhas de código, não altera a API pública e passou a ser verificável por teste.

O hash perfeito por ordinal foi escolhido porque o espaço de chaves permite. Com 8 grupos e 4 tipos, existem no máximo 32 combinações, e a aritmética posicional, bloco vezes tamanho mais posição, é a forma canônica de enumerar um produto cartesiano pequeno. Em uma chave aberta isso não seria possível e o encadeamento do HashMap seria a resposta certa.

A capacidade foi fixada em 64, e não deixada no padrão de 16, porque o padrão redimensionaria a tabela duas vezes ao chegar nas 32 entradas, e durante o processo os índices de balde mudariam. Com 64, o índice do balde é o próprio hash desde a primeira inserção, e a propriedade de zero colisão vale do início ao fim, sem depender de quantas chaves já foram inseridas.

A fórmula ficou em um método package-private, hashDaChave, em vez de embutida no hashCode, para que o teste verifique exatamente a fórmula que o índice usa. A alternativa seria o teste reimplementar a conta, e aí os dois poderiam divergir sem ninguém notar, ou usar reflexão para alcançar a ChaveEstoque, que é privada, o que tornaria o teste frágil a qualquer renomeação.

O erro factual encontrado no evidencias-unidade-1-aed.md foi corrigido em vez de apenas sinalizado porque a afirmação de que a ChaveEstoque era um record nunca foi verdadeira, conferido no commit original da W04. Não é registro de um estado histórico que mudou: é descrição errada de um código que já era diferente. A correção entrou com nota explícita, para que fique claro o que foi ajustado e quando.

Ficou para as próximas subtarefas da PI3-96:

A equivalência entre o índice hash em Java e a versão em C ou C++, que exigiria escrever a tabela hash à mão com tratamento explícito de colisão por encadeamento. Nenhum código em C foi escrito nesta subtarefa.
