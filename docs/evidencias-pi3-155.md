EVIDÊNCIAS DE ENTREGA — PI3-155 (Implementar a inserção ordenada por validade na FilaPrioridadeFefo)

O que foi feito:

A técnica de priorização FEFO da FilaPrioridadeFefo passou de heap binário para inserção ordenada, que é o que a Unidade 2 de AED pede. O armazenamento virou uma lista encadeada simples mantida ordenada o tempo todo, com classe interna de nó própria contendo a bolsa e a referência para o próximo; não existe passo de ordenação depois, porque a ordem é invariante da estrutura. Nenhuma coleção do java.util é usada como armazenamento, pelo mesmo motivo da ListaEstoque: a entrega é demonstrar a técnica, e chamar TreeSet ou Collections.sort não demonstraria nada. O PriorityQueue e o Comparator foram removidos, e o critério de ordem — validade crescente e, no empate, código de rastreio crescente — passou a ser um método privado de comparação explícita, vemAntes, escrito campo a campo como a versão em C faria. A inserção trata os três casos em ramos separados e comentados: fila vazia, bolsa que vence antes da primeira, e percurso com anterior e atual até o ponto de encaixe no meio ou no fim; é estável, porque bolsa igual no critério entra depois das existentes. Foi acrescentado o método descartarVencidas, que aproveita a ordem como invariante: as vencidas estão necessariamente no início, então o descarte remove do início e para na primeira válida, sem percorrer o resto. O caso de estoque vazio continua devolvendo null em consultarProxima e retirarProxima, sem exceção, porque é o contrato que já existia.

Reuso, não reconstrução:

A API pública foi mantida integralmente: adicionar, consultarProxima, retirarProxima, estaVazia e tamanho continuam com as mesmas assinaturas e o mesmo contrato, e descartarVencidas é acréscimo, não substituição. É por isso que os dois consumidores de produção, SelecaoFefo e ServicoAlocacaoRota, não tiveram uma linha alterada, o que foi comprovado pelo comando "git diff --stat main -- src/main/java/com/rotavital/alocacao", cuja saída é vazia. O critério de ordem é o mesmo de antes, então nenhum resultado de alocação mudou. O padrão seguido é o da ListaEstoque, de com.rotavital.estruturas.basicas: classe interna de nó privada, referência ao primeiro, contador de tamanho e javadoc mapeando a estrutura contra a versão em C. Os quatro testes que a FilaPrioridadeFefoTest já tinha passam sem alteração de asserção, e o helper criarBolsa deles foi reaproveitado pelos testes novos.

Pull Request #60:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/60

Commit:
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/39a0613ccc7dc46c9d4f0d51fa17980b0c501883

Código:

FilaPrioridadeFefo — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/fefo-insercao-ordenada-pi3-155/src/main/java/com/rotavital/estruturas/FilaPrioridadeFefo.java
FilaPrioridadeFefoTest — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/fefo-insercao-ordenada-pi3-155/src/test/java/com/rotavital/estruturas/FilaPrioridadeFefoTest.java
SelecaoFefo (consumidor, não alterado) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/main/src/main/java/com/rotavital/alocacao/SelecaoFefo.java
ServicoAlocacaoRota (consumidor, não alterado) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/main/src/main/java/com/rotavital/alocacao/ServicoAlocacaoRota.java

Critérios de aceite:

A bolsa com validade mais próxima é sempre a primeira alocada

A ordem é invariante da estrutura: a inserção coloca cada bolsa direto na posição certa, então o primeiro nó é, por construção, o de menor validade. O teste proximaEhSempreADeMenorValidade verifica isso após cada inserção, e não apenas no fim: monta cinco bolsas, embaralha com semente fixa vinte vezes e, a cada bolsa inserida, compara consultarProxima com o mínimo recalculado de forma independente da estrutura, por validade e depois por código. São cem verificações intermediárias, em vinte ordens de entrada diferentes.

Testes cobrindo ordenação

Um teste para cada caso de inserção: insercaoNoInicio, insercaoNoMeio e insercaoNoFim, cada um conferindo a sequência inteira de saída. E entradaEmbaralhadaSaiOrdenada, com oito bolsas embaralhadas por Random de semente fixa, conferindo a sequência completa de retirarProxima.

Testes cobrindo empate de validade

empateMaiorCodigoInseridoPrimeiro e empateMenorCodigoInseridoPrimeiro cobrem as duas ordens de inserção para duas bolsas de mesma validade e códigos diferentes. Nos dois casos sai o menor código, o que prova que a ordem de inserção não influencia o resultado.

Testes cobrindo estoque vazio

filaNovaDevolveNull verifica que consultarProxima e retirarProxima devolvem null sem exceção, com estaVazia verdadeiro e tamanho zero. depoisDeEsvaziarVoltaAoEstadoVazio cobre o mesmo estado depois de retirar todas as bolsas de uma fila que tinha conteúdo, e confirma que a fila volta a aceitar inserção normalmente.

Bolsas vencidas tratadas

descartarVencidas remove do início enquanto a primeira estiver vencida e para na primeira válida. descartaSoAsVencidasDoInicio verifica que apenas as vencidas saem, em ordem de validade, e que as demais permanecem na fila. bolsaQueVenceNaDataDeReferenciaEhMantida fixa a borda do isAfter: a bolsa cuja validade é exatamente a data de referência não é descartada, porque só vence no dia seguinte. filaTodaVencidaFicaVazia e filaSemVencidasNaoMuda cobrem os dois extremos, e listaImutavelEDataNulaLanca verifica que a lista devolvida é imutável e que data nula lança IllegalArgumentException.

Consumidores não perceberam a troca

O comando "git diff --stat main -- src/main/java/com/rotavital/alocacao" devolve saída vazia: SelecaoFefo e ServicoAlocacaoRota não foram tocados. Os testes deles rodaram sem nenhuma alteração: SelecaoFefoTest com 9, ServicoAlocacaoRotaTest com 12, CriteriosAceiteAlocacaoTest com 6, PropriedadesAlocacaoTest com 1 e DemoPontaAPontaTest com 1, somando 29 testes e nenhuma falha. A única alteração em src/main foi o próprio arquivo FilaPrioridadeFefo.java.

Validação dos testes:

FilaPrioridadeFefoTest: de 4 para 20 testes, 0 falhas
./mvnw test -Dtest=FilaPrioridadeFefoTest

Testes dos consumidores: 29 testes, 0 falhas
./mvnw test -Dtest='SelecaoFefoTest,ServicoAlocacaoRotaTest,CriteriosAceiteAlocacaoTest,PropriedadesAlocacaoTest,DemoPontaAPontaTest'

Suíte completa: BUILD SUCCESS — de 225 para 241 testes
./mvnw test

CI/CD no GitHub Actions: executou e passou. Build pass, Testes pass, Empacotamento pass, Deploy na VM skipping, porque o deploy só roda em push para a main.
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/60/checks

Os testes novos:

Teste

O que prova

insercaoNoInicio

Bolsa que vence antes da primeira assume o início da lista.

insercaoNoMeio

Bolsa encaixa entre duas existentes, sem quebrar a cadeia.

insercaoNoFim

Bolsa de validade mais distante vai para o final.

entradaEmbaralhadaSaiOrdenada

Oito bolsas embaralhadas com semente fixa saem inteiras na ordem de validade.

empateMaiorCodigoInseridoPrimeiro

Com mesma validade, sai o menor código mesmo inserindo o maior antes.

empateMenorCodigoInseridoPrimeiro

O mesmo resultado inserindo na ordem inversa: a ordem de entrada não influencia.

filaNovaDevolveNull

Fila vazia devolve null em consultar e retirar, sem exceção, com tamanho zero.

depoisDeEsvaziarVoltaAoEstadoVazio

Depois de esvaziar, a fila volta ao estado inicial e aceita inserção de novo.

descartaSoAsVencidasDoInicio

O descarte remove apenas as vencidas, em ordem, e preserva as válidas.

bolsaQueVenceNaDataDeReferenciaEhMantida

Borda do isAfter: a bolsa que vence na própria data não é descartada.

filaTodaVencidaFicaVazia

Fila inteiramente vencida fica vazia após o descarte.

filaSemVencidasNaoMuda

Fila sem vencidas devolve lista vazia e não é alterada.

listaImutavelEDataNulaLanca

A lista devolvida recusa alteração e data nula lança IllegalArgumentException.

tamanhoAcompanhaAsOperacoes

O contador acompanha adicionar, retirar e descartar.

bolsaNulaLanca

Bolsa nula lança IllegalArgumentException e não entra na fila.

proximaEhSempreADeMenorValidade

Critério da mãe: após cada inserção, em vinte ordens de entrada, a próxima é sempre a de menor validade e, no empate, a de menor código.

Complexidade:

adicionar — antes O(log n) no heap — agora O(n), porque a posição certa é encontrada percorrendo a lista a partir do início.
consultarProxima — antes O(1) — agora O(1), já que o heap também olhava apenas a raiz.
retirarProxima — antes O(log n) — agora O(1), porque a lista já está ordenada e basta avançar a referência do início, enquanto o heap precisava reequilibrar a árvore após remover a raiz.
descartarVencidas — não existia antes — agora O(k) para k bolsas vencidas, porque elas estão todas no início e o laço para na primeira válida.
tamanho e estaVazia — antes O(1) — agora O(1), por contador mantido nas operações.

A justificativa da troca é o perfil de uso: a fila guarda as bolsas de uma combinação de grupo sanguíneo e hemocomponente, que são poucas por unidade, então o O(n) da inserção é barato em valor absoluto. Em troca, consultar e retirar, que é o que a alocação faz a cada requisição, passaram a O(1). A estrutura ficou mais cara onde o estoque é abastecido e mais barata onde é consumido.

Ficou para a PI3-156:

O documento da W10 com a tradução comentada de C/C++ para Java e a comparação entre heap e inserção ordenada. Nenhum documento foi escrito nesta subtarefa.

Decisão técnica:

O interior foi trocado e a API mantida porque é isso que torna a mudança verificável: se os consumidores precisassem ser ajustados, não haveria como separar o que mudou por causa da técnica do que mudou por causa da interface. Com a assinatura preservada, o git diff vazio em com.rotavital.alocacao e os 29 testes dos consumidores passando sem alteração são a prova de que a troca é transparente.

A lista encadeada foi feita à mão, e não com Collections.sort ou TreeSet, porque a técnica é o objeto da entrega. Um TreeSet resolveria a ordenação, mas esconderia exatamente o que a Unidade 2 pede para demonstrar: os três casos de inserção, o percurso com dois ponteiros e o religamento das referências. Também traria um efeito indesejado, já que TreeSet descarta elementos considerados iguais pelo comparador.

A comparação ficou explícita em vemAntes, e não como Comparator, para que a versão Java e a versão em C possam ser lidas lado a lado: em C a comparação é uma função que olha campo a campo, e manter a mesma forma aqui torna o mapa de equivalência direto. O critério em si não mudou em relação ao que o Comparator fazia.

As vencidas ficam no início porque a lista é ordenada por validade crescente, e é isso que permite o descarte parar na primeira válida: toda bolsa depois dela vence ainda mais tarde. No heap isso não seria possível, porque a ordem só vale para a raiz e descobrir quais venceram exigiria varrer a árvore inteira. É um ganho que veio junto com a técnica.

Estoque vazio devolve null em vez de lançar porque era o contrato que já existia, e mudá-lo quebraria a SelecaoFefo, que converte o null em Optional.empty(). Além disso, estoque sem bolsa é situação normal da operação, não erro de programa; lançar obrigaria todo chamador a se defender com try-catch de algo previsível.
