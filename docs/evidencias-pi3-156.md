EVIDÊNCIAS DE ENTREGA — PI3-156 (Tradução comentada e documento da W10 — FEFO por inserção ordenada)

O que foi feito:

Foi produzido o documento docs/W10-fefo-insercao-ordenada.md, com a tradução comentada entre a versão em C e a versão em Java da fila FEFO por inserção ordenada, seguindo a estrutura e o tom do docs/U1-traducao-c-java.md: trecho em C, trecho em Java e o comentário do que muda e por quê. Antes de escrever, foi confirmado que não existia no repositório nenhum código em C ou C++ de inserção ordenada ou FEFO — o diretório aed continha apenas unidade1, com lista encadeada, fila e pilha, e a busca por "fefo" e "insercao ordenada" não retornou nenhuma ocorrência. Em vez de comparar o Java com um trecho de referência solto no texto, foi escrita a contraparte real em C, em aed/unidade2/c, no mesmo estilo e com as mesmas convenções da Unidade 1: fila_fefo.h, fila_fefo.c, dominio.h, main.c, Makefile e .gitignore. Com isso a tradução cita linhas reais e verificáveis dos dois lados, e as dezessete citações do documento foram conferidas uma a uma contra os arquivos. O documento registra explicitamente que o código em C foi escrito neste repositório como contraparte da versão Java e não é cópia de código mostrado em sala. Além da tradução, o documento traz a comparação entre o heap da Unidade 1 e a inserção ordenada da Unidade 2 com a complexidade de cada operação, o tratamento de estoque vazio e de bolsas vencidas, a comprovação de que os consumidores não mudaram, os vinte testes agrupados pelos critérios da task mãe e a rastreabilidade das duas subtarefas.

Reuso, não reconstrução:

O modelo de estilo e estrutura veio do docs/U1-traducao-c-java.md, inclusive o formato das tabelas de correspondência entre arquivos e da tabela final que separa tradução direta de divergência real. O estilo do código em C veio do aed/unidade1/c/lista_estoque.c: mesma convenção de retorno com RV_OK e RV_ERRO, mesma função copiar_texto para campos de tamanho fixo, mesmo formato de Makefile e de .gitignore, e mesmo padrão de comentários em português sem acento. A estrutura documentada é a da PI3-155, que não foi tocada aqui. Nenhum código Java ou de teste foi alterado nesta subtarefa, comprovado pelo comando "git diff --stat feat/fefo-insercao-ordenada-pi3-155 -- src", cuja saída é vazia.

Pull Request #63:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/63

Commit:
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/8dc1b426e0cf30b2c0993c7f7f99927b5f1267e3

Documentação:

W10-fefo-insercao-ordenada.md — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/fefo-traducao-comentada-pi3-156/docs/W10-fefo-insercao-ordenada.md
aed/unidade2/c/fila_fefo.h — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/fefo-traducao-comentada-pi3-156/aed/unidade2/c/fila_fefo.h
aed/unidade2/c/fila_fefo.c — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/fefo-traducao-comentada-pi3-156/aed/unidade2/c/fila_fefo.c
aed/unidade2/c/main.c — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/docs/fefo-traducao-comentada-pi3-156/aed/unidade2/c/main.c
Commit do código em C: https://github.com/rsc3-pixel/Projeto3-2026.2/commit/5d26f660ccf4f4e63b281e05e1e9911f0458f616

Critérios de aceite:

Tradução comentada C/C++ → Java

Seção 3 do documento, dividida em nove subseções: correspondência entre os arquivos, o nó, a comparação, a alocação do nó, os três casos da inserção em subseções separadas, o percurso com anterior e atual, a retirada e o resumo das diferenças. Cada subseção traz o trecho em C, o trecho em Java equivalente e o comentário do que muda. A referência em C não é a versão clássica escrita no texto: é o arquivo aed/unidade2/c/fila_fefo.c, criado nesta subtarefa e versionado no repositório, com citação de linha real em todas as ocorrências. O documento deixa explícito que esse arquivo foi escrito como contraparte da versão Java e não é cópia de código de sala. As dezessete citações de linha, nove do lado C e oito do lado Java, foram verificadas uma a uma contra os arquivos.

Comparação heap × inserção ordenada com complexidade

Seção 4. Traz o trecho da versão anterior com PriorityQueue e Comparator, obtido de "git show main", e a tabela de complexidade de cada operação nas duas técnicas: adicionar passa de O(log n) para O(n), retirarProxima passa de O(log n) para O(1), consultarProxima e tamanho seguem O(1), e descartarVencidas, que não existia, é O(k). A seção explica por que a troca é aceitável, já que a fila guarda apenas as bolsas de uma combinação de grupo sanguíneo e hemocomponente, e por que a ordem de saída não muda, já que o critério é o mesmo nas duas versões.

Tratamento de sem estoque e vencidas documentado

Seção 5, em três partes. A primeira explica o contrato de devolver nulo em estrutura vazia e como a SelecaoFefo o converte em Optional.empty, e registra o equivalente em C com NULL e RV_ERRO. A segunda mostra os trechos lado a lado do laço de descarte e explica por que as vencidas estão necessariamente no início e por que o laço para na primeira válida, com o contraste do heap, em que descobrir as vencidas exigiria varrer a árvore. A terceira fixa a borda do Bolsa.estaVencida, que usa isAfter, e mostra que a versão em C reproduz a mesma regra com comparação estritamente maior.

Equivalência com o C/C++ justificada, como pede a PI3-95

A task mãe permitia adequar à técnica pedida ou justificar a equivalência na tradução comentada. Foram feitas as duas coisas: a PI3-155 adequou a técnica, trocando o heap pela inserção ordenada, e esta subtarefa documenta a equivalência linha a linha. A tabela da seção 3.9 classifica cada aspecto como tradução direta ou divergência real, e aponta como divergências reais apenas a alocação, com malloc e checagem contra new, e a devolução de memória, com free contra coletor de lixo. A lógica da inserção, a ordem das atribuições no encaixe, o percurso com dois ponteiros e os três casos são idênticos nas duas versões.

Validação:

Suíte completa: BUILD SUCCESS — 241 testes, igual à PI3-155, porque nenhum código foi alterado
./mvnw test

Compilação do código em C:
make
gcc -Wall -Wextra -std=c11 -c main.c -o main.o
gcc -Wall -Wextra -std=c11 -c fila_fefo.c -o fila_fefo.o
gcc -Wall -Wextra -std=c11 -o rota_vital_u2 main.o fila_fefo.o
Zero avisos. A execução de ./rota_vital_u2 percorre as seis etapas e reproduz o comportamento da versão Java, inclusive a borda da validade: com data de referência 2026-10-01, apenas a bolsa vencida em 2026-09-05 é descartada, e as três que vencem exatamente em 2026-10-01 permanecem na fila.

Verificação de memória: "leaks --atExit" reporta 189 nodes malloced e 0 leaks for 0 total leaked bytes. A compilação com AddressSanitizer e UndefinedBehaviorSanitizer executa sem nenhum erro de memória nem comportamento indefinido.

Objetos e executável não foram versionados: o .gitignore do diretório cobre "*.o" e "rota_vital_u2", confirmado por "git check-ignore -v".

CI/CD no GitHub Actions: não executou neste PR. O workflow .github/workflows/ci.yml dispara em "pull_request: branches: [main]", e este PR tem base feat/fefo-insercao-ordenada-pi3-155, porque o PR #60 ainda não foi mergeado. A aba Checks fica vazia. https://github.com/rsc3-pixel/Projeto3-2026.2/pull/63/checks
Validação local acima: 241 testes, BUILD SUCCESS.

Fechamento da PI3-95:

A bolsa com validade mais próxima é sempre a primeira alocada — atendido na PI3-155, pelo teste proximaEhSempreADeMenorValidade do FilaPrioridadeFefoTest, que verifica a propriedade após cada inserção em vinte ordens de entrada, comparando com o mínimo recalculado de forma independente da estrutura. A garantia estrutural está em FilaPrioridadeFefo, onde a ordem é invariante da lista.

Testes cobrindo ordenação, empate de validade e estoque vazio — atendido na PI3-155, com os vinte testes do FilaPrioridadeFefoTest. Ordenação em insercaoNoInicio, insercaoNoMeio, insercaoNoFim, entradaEmbaralhadaSaiOrdenada e devolveSempreABolsaQueVencePrimeiro. Empate em empateMaiorCodigoInseridoPrimeiro, empateMenorCodigoInseridoPrimeiro e empateDeValidadeEhResolvidoDeFormaDeterministicaPeloCodigo. Estoque vazio em filaVaziaNaoTemProximaBolsa, filaNovaDevolveNull e depoisDeEsvaziarVoltaAoEstadoVazio. O agrupamento está na seção 7 do documento.

Inserção ordenada com lógica equivalente ao C/C++ — atendido nesta subtarefa, na seção 3 do documento, com a tradução trecho a trecho contra aed/unidade2/c/fila_fefo.c, e na seção 3.9, que separa tradução direta de divergência real.

Sem estoque e bolsas vencidas tratados — atendido na PI3-155, pelo método descartarVencidas e pelo contrato de nulo em fila vazia, com os testes descartaSoAsVencidasDoInicio, bolsaQueVenceNaDataDeReferenciaEhMantida, filaTodaVencidaFicaVazia, filaSemVencidasNaoMuda e listaImutavelEDataNulaLanca. Documentado na seção 5.

Decisão técnica:

A task mãe permitia justificar a equivalência em vez de trocar a técnica, mas trocar foi a escolha melhor por dois motivos. O primeiro é que a Unidade 2 pede a inserção ordenada como objeto de estudo, e manter o PriorityQueue deixaria a entrega sendo um texto sobre uma técnica que o código não usa. O segundo é que a troca produziu ganho real e mensurável: retirarProxima caiu de O(log n) para O(1) e o descarte das vencidas passou a ser O(k) em vez de exigir varredura, porque a ordem virou invariante da estrutura.

A ordem de saída não muda porque o critério é o mesmo nas duas técnicas, validade crescente e depois código de rastreio. O heap e a lista ordenada apenas mantêm esse critério de formas diferentes, um por propriedade da árvore e outro por invariante da lista. É por isso que os vinte e nove testes dos consumidores passaram sem nenhum ajuste: se a ordem tivesse mudado, algum deles teria quebrado.

O O(n) da inserção é aceitável porque a fila não guarda o estoque inteiro, e sim as bolsas de uma combinação de grupo sanguíneo e hemocomponente, que são poucas por unidade. O custo maior recai sobre o abastecimento do estoque, que é esporádico, enquanto consultar e retirar, que a alocação executa a cada requisição, ficaram O(1).

A referência em C é um arquivo do repositório, e não a versão clássica escrita no texto, porque o enunciado oferecia as duas opções e a primeira é verificável. Com o arquivo versionado, cada trecho da tradução cita arquivo e linha que qualquer pessoa pode abrir e conferir, e o próprio código compila e roda. A alternativa produziria um documento cuja correção dependeria de confiança no autor. O documento registra, ainda assim, que esse código foi escrito aqui como contraparte e não é cópia do que foi mostrado em sala, para não sugerir uma origem que ele não tem.
