EVIDÊNCIAS DE ENTREGA — PI3-131 (Revisar o índice hash e garantir o uso pela alocação)

O que foi feito:

O diagnóstico do card se confirmou: o IndiceEstoque existia desde a Unidade 1 e tinha testes, mas nenhum serviço o consultava. O grep pedido no card respondia apenas a própria classe e a SelecaoFefo, e nenhum serviço Spring chamava a SelecaoFefo nem o ServicoAlocacaoRota. O caminho real de alocação, POST em /requisicoes/{id}/itens/{itemId}/alocacoes, recebia o identificador da bolsa já escolhido pelo usuário e apenas validava: não havia seleção automática e, portanto, não havia consulta por hash. O índice era estrutura morta.

A entrega tem três partes. Primeiro, a revisão do próprio IndiceEstoque: a chave composta por grupo sanguíneo e tipo de hemocomponente foi mantida, assim como o equals e o hashCode da ChaveEstoque, mas o javadoc passou a registrar por que os dois precisam andar juntos; a busca passou a devolver cópia imutável em vez de visão da lista interna; argumento nulo passou a devolver lista vazia em vez de NullPointerException; e adicionar deixou de duplicar, substituindo a instância antiga quando a mesma bolsa é reindexada. Segundo, o IndiceEstoqueServico, que mantém o índice vivo contendo apenas as bolsas DISPONIVEL, sincronizado com o banco por eventos processados depois do commit, com acesso protegido por ReentrantReadWriteLock. Terceiro, a alocação automática, que é o caminho real que consulta o índice: busca as bolsas compatíveis por hash, aplica FEFO e delega a alocação propriamente dita para o método que já existia.

Reuso, não reconstrução:

O IndiceEstoque da Unidade 1 foi revisado, não reescrito: a estrutura de mapa com chave composta, o equals e o hashCode da ChaveEstoque e a semântica de adicionar, buscar e remover continuam as mesmas. A SelecaoFefo foi reusada sem alteração, na sobrecarga que recebe lista e data, porque a compatibilidade já vem resolvida pelo índice. A MatrizCompatibilidade foi reusada sem alteração para decidir quais grupos procurar. O alocarAutomaticamente delega para o alocar existente em vez de duplicar as validações: status da bolsa, validade, tipo do hemocomponente, compatibilidade ABO/Rh, troca de status e recálculo da requisição continuam num único lugar, e as duas portas de entrada não podem divergir. Nenhuma asserção de teste existente foi alterada.

Pull Request #65:
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/65

Commits:
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/2c813dc7e8ff998bcceadb412259cad9a36cfc95
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/e1100cc842915dfd7b01cf5030583310dd392481
https://github.com/rsc3-pixel/Projeto3-2026.2/commit/d4162b2eeb46c8742848b7393c11780f3c004538

Código:

IndiceEstoque — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/indice-estoque-alocacao-pi3-131/src/main/java/com/rotavital/estruturas/IndiceEstoque.java
IndiceEstoqueServico — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/indice-estoque-alocacao-pi3-131/src/main/java/com/rotavital/servico/IndiceEstoqueServico.java
RequisicaoServico (alocarAutomaticamente) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/indice-estoque-alocacao-pi3-131/src/main/java/com/rotavital/servico/RequisicaoServico.java
RequisicaoController (endpoint) — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/indice-estoque-alocacao-pi3-131/src/main/java/com/rotavital/api/RequisicaoController.java
contrato_api.md — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/indice-estoque-alocacao-pi3-131/contrato_api.md
AlocacaoAutomaticaTest — https://github.com/rsc3-pixel/Projeto3-2026.2/blob/feat/indice-estoque-alocacao-pi3-131/src/test/java/com/rotavital/api/AlocacaoAutomaticaTest.java

Como verificar se está em uso:

Antes, na main:
src/main/java/com/rotavital/alocacao/SelecaoFefo.java:8:import com.rotavital.estruturas.IndiceEstoque;
src/main/java/com/rotavital/alocacao/SelecaoFefo.java:116:    public static Optional<Bolsa> escolher(IndiceEstoque estoque,
src/main/java/com/rotavital/estruturas/IndiceEstoque.java:18:public class IndiceEstoque {

Três ocorrências, todas na própria classe e na SelecaoFefo, que por sua vez não era chamada por nenhum serviço. O índice existia e não era usado.

Depois, na branch:
src/main/java/com/rotavital/servico/RequisicaoServico.java:39:    private final IndiceEstoqueServico indiceEstoque;
src/main/java/com/rotavital/servico/RequisicaoServico.java:45:                             IndiceEstoqueServico indiceEstoque,
src/main/java/com/rotavital/servico/RequisicaoServico.java:284:     * consulta o {@link IndiceEstoqueServico}, que resolve em O(1) por grupo
src/main/java/com/rotavital/servico/evento/BolsaAlterada.java:9: * {@code IndiceEstoqueServico} <b>depois do commit</b>. O evento carrega a
src/main/java/com/rotavital/alocacao/SelecaoFefo.java:8:import com.rotavital.estruturas.IndiceEstoque;
src/main/java/com/rotavital/alocacao/SelecaoFefo.java:116:    public static Optional<Bolsa> escolher(IndiceEstoque estoque,
src/main/java/com/rotavital/servico/IndiceEstoqueServico.java:8:import com.rotavital.estruturas.IndiceEstoque;
src/main/java/com/rotavital/servico/IndiceEstoqueServico.java:25: * Mantem o {@link IndiceEstoque} vivo e sincronizado com o banco, e e o ponto
src/main/java/com/rotavital/servico/IndiceEstoqueServico.java:52:public class IndiceEstoqueServico {
src/main/java/com/rotavital/servico/IndiceEstoqueServico.java:55:    private final IndiceEstoque indice = new IndiceEstoque();
src/main/java/com/rotavital/servico/IndiceEstoqueServico.java:58:    public IndiceEstoqueServico(BolsaRepository bolsas) {
src/main/java/com/rotavital/estruturas/IndiceEstoque.java:46: * com o banco e o {@code IndiceEstoqueServico}.</p>
src/main/java/com/rotavital/estruturas/IndiceEstoque.java:52:public class IndiceEstoque {

O índice agora alcança o RequisicaoServico, que é o serviço do caminho real de alocação.

Critérios de aceite:

Chave combina tipo de hemocomponente e tipagem ABO/Rh

A ChaveEstoque, classe interna privada do IndiceEstoque, é composta por GrupoSanguineo e TipoHemocomponente, e é construída tanto em adicionar quanto em buscar e remover. São dois enums, e não texto concatenado: enum compara por identidade, não tem variação de maiúscula nem de espaço, e o conjunto de valores possíveis é fechado. Verificado em chaveCombinaGrupoETipo, que indexa uma bolsa O- de concentrado de hemácias e confirma que ela não é encontrada nem por mesmo grupo com tipo diferente, nem por mesmo tipo com grupo diferente, apenas pelos dois corretos.

equals e hashCode implementados

Os dois estão na ChaveEstoque, e o javadoc da classe explica por que precisam andar juntos. O HashMap localiza o balde pelo hashCode e só depois compara as chaves daquele balde com equals; implementar apenas equals faz duas chaves logicamente iguais receberem hashCode diferentes do Object, caírem em baldes diferentes e o get devolver null sem lançar erro nenhum. O estoque pareceria vazio, a alocação recusaria bolsa existente, e nada no log apontaria a causa. Verificado em chavesIguaisCaemNoMesmoBalde, que é prova de comportamento e não de implementação: a bolsa é indexada com uma instância de chave construída dentro do adicionar e precisa ser encontrada por uma busca que constrói outra instância.

Índice consultado no caminho real de alocação, com teste que prova

O caminho é o endpoint POST /api/v1/requisicoes/{id}/itens/{itemId}/alocacoes/automatica, atendido por RequisicaoServico.alocarAutomaticamente, que chama indiceEstoque.buscarCompativeis e aplica SelecaoFefo sobre o resultado. A prova tem dois níveis. O direto, em alocacaoAutomaticaConsultaOIndice, usa MockitoSpyBean no IndiceEstoqueServico e verifica que buscarCompativeis foi chamado com o grupo do receptor e o tipo do item. O indireto, em bolsaForaDoIndiceNaoEhEscolhida, é mais forte: a bolsa existe no banco e está DISPONIVEL, confirmado por consulta ao endpoint de bolsas, mas foi retirada do índice sem tocar no banco; a alocação automática devolve 409, o que só acontece se a consulta vier do índice e não de uma varredura do banco.

Índice atualizado quando bolsa muda de status

A sincronização está ligada em todos os pontos levantados. Em BolsaServico, na criação, na atualização de status e na remoção. Em RequisicaoServico, na alocação, que leva a bolsa a RESERVADA, no desfazer alocação e no cancelamento da requisição, que a devolvem a DISPONIVEL. Em RotaServico, nos três status de rota, que levam a bolsa a EM_TRANSITO, ENTREGUE ou de volta a RESERVADA. A carga inicial do índice roda em ApplicationReadyEvent, que dispara depois dos CommandLineRunner, então a carga de dados já terminou quando o índice é montado.

Os testes cobrem ponto a ponto. Em criarAlocarEDesfazer, a bolsa recém-criada entra no índice, sai ao ficar RESERVADA e volta ao desfazer a alocação. Em cancelarRequisicaoDevolveAoIndice, o cancelamento da requisição devolve a bolsa ao índice. Em descartarTiraDoIndice, o PATCH de status para DESCARTADA a retira. Em removerTiraDoIndice, o DELETE a retira. Em duasChamadasEscolhemBolsasDiferentes, a segunda alocação escolhe outra bolsa porque a primeira saiu do índice ao ficar RESERVADA. Em rollbackNaoAlteraOIndice, uma alocação recusada por incompatibilidade reverte a transação e a bolsa continua no índice e DISPONIVEL.

Busca sem resultado devolve lista vazia, nunca null

A busca devolve List.of quando a chave não existe e quando algum argumento é nulo. Verificado em buscaNuncaDevolveNull, que confere o retorno não nulo com índice vazio e com chave inexistente, e em argumentoNuloDevolveListaVazia, que cobre grupo nulo, tipo nulo e ambos nulos dentro de assertDoesNotThrow. Antes, argumento nulo lançava NullPointerException pelo requireNonNull da ChaveEstoque. No nível do serviço, buscarCompativeis também devolve lista vazia quando a MatrizCompatibilidade não indica nenhum doador.

Validação dos testes:

IndiceEstoqueTest: de 4 para 14 testes, 0 falhas
./mvnw test -Dtest=IndiceEstoqueTest

AlocacaoAutomaticaTest: 11 testes, 0 falhas
./mvnw test -Dtest=AlocacaoAutomaticaTest

Suíte completa: BUILD SUCCESS — de 225 para 246 testes
./mvnw test

CI/CD no GitHub Actions: executou e passou. Build pass, Testes pass, Empacotamento pass, Deploy na VM skipping, porque o deploy só roda em push para a main.
https://github.com/rsc3-pixel/Projeto3-2026.2/pull/65/checks

Os testes novos:

Teste

O que prova

argumentoNuloDevolveListaVazia

Grupo ou tipo nulos devolvem lista vazia, sem NullPointerException.

buscaNuncaDevolveNull

A busca nunca devolve null, com índice vazio ou chave inexistente.

resultadoEhCopiaImutavel

O resultado é cópia: alterar o índice depois não muda a lista devolvida, que recusa alteração.

adicionarDuasVezesNaoDuplica

A mesma bolsa adicionada duas vezes não duplica; a instância nova substitui a antiga.

remocao

Remover tira a bolsa, devolve se encontrou, e bolsa nula devolve false.

removePorCodigoDeRastreio

A remoção funciona por outra instância de mesmo código de rastreio.

bolsaNulaLanca

Adicionar bolsa nula lança IllegalArgumentException e não altera o índice.

limparETamanho

limpar esvazia o índice e tamanho soma todos os baldes.

chavesIguaisCaemNoMesmoBalde

Chave construída na busca encontra a bolsa indexada por outra instância: equals e hashCode coerentes.

chaveCombinaGrupoETipo

Mudar grupo ou tipo muda o balde: a chave é composta pelos dois.

alocacaoAutomaticaConsultaOIndice

O caminho real chama buscarCompativeis no índice, verificado por spy.

bolsaForaDoIndiceNaoEhEscolhida

Bolsa disponível no banco mas fora do índice não é escolhida: a consulta vem do índice.

escolheAMaisProximaDoVencimento

Entre compatíveis, sai a de validade mais próxima, ainda que de outro grupo.

incompativelNuncaEhEscolhida

Bolsa A+ nunca é escolhida para paciente O-.

semEstoqueDevolve409

Sem estoque devolve 409 e a requisição continua aberta e pendente.

duasChamadasEscolhemBolsasDiferentes

A segunda alocação escolhe outra bolsa, porque a primeira saiu do índice ao ficar RESERVADA.

criarAlocarEDesfazer

Criar indexa, alocar retira, desfazer alocação devolve ao índice.

cancelarRequisicaoDevolveAoIndice

Cancelar a requisição libera a bolsa e ela volta ao índice.

descartarTiraDoIndice

PATCH de status para DESCARTADA tira a bolsa do índice.

removerTiraDoIndice

DELETE da bolsa tira do índice.

rollbackNaoAlteraOIndice

Transação revertida não altera o índice: a bolsa continua indexada e disponível.

Decisão técnica:

A alocação automática foi escolhida como caminho real porque o endpoint manual não tem como consultar o índice: ele recebe o identificador da bolsa já decidido pelo cliente e só valida. Forçar uma consulta ali seria encenação, não uso. Com a alocação automática, a pergunta que o índice responde em O(1) — quais bolsas disponíveis existem desta combinação — passa a ser a pergunta que o sistema realmente faz. O endpoint manual continua existindo e com o comportamento intacto.

O índice guarda apenas bolsas DISPONIVEL porque bolsa reservada, em trânsito, entregue ou descartada está comprometida com outra requisição ou fora de circulação. Mantê-las indexadas obrigaria todo consumidor a filtrar de novo por status, e o ganho do índice se perderia: a busca voltaria a devolver um conjunto que precisa ser varrido.

A atualização acontece apenas depois do commit, por TransactionalEventListener em AFTER_COMMIT. Se o índice fosse atualizado no meio da transação, um rollback deixaria memória e banco discordando: a bolsa voltaria a DISPONIVEL na tabela, mas continuaria fora do índice, e nenhuma alocação automática a encontraria de novo até o próximo reinício. O evento transacional foi preferido ao TransactionSynchronization manual porque o acoplamento fica menor: os serviços publicam o fato e não conhecem o índice.

A busca devolve cópia imutável porque o índice é compartilhado entre requisições e pode ser atualizado enquanto alguém percorre o resultado de uma busca anterior. Com a visão que existia antes, isso seria ConcurrentModificationException no meio da alocação. A cópia custa O(k) no tamanho do balde, que é pequeno por combinação.

Argumento nulo devolve lista vazia porque ausência de dado não é erro, a mesma decisão já tomada na MatrizCompatibilidade. Antes, a ChaveEstoque lançava NullPointerException pelo requireNonNull, e quem consome teria de se defender de algo previsível.

A thread-safety usa ReentrantReadWriteLock porque o componente é singleton e serve requisições em paralelo, enquanto o HashMap interno não é seguro para acesso concorrente. A escolha pelo ReadWriteLock em vez de synchronized vem do perfil de uso: a alocação lê com muito mais frequência do que o estoque muda, e leituras simultâneas não precisam se bloquear.

O alocarAutomaticamente delega para o alocar existente para que todas as regras continuem num lugar só. Duplicar as validações de status, validade, tipo e compatibilidade criaria duas cópias que divergiriam na primeira mudança de regra, e o endpoint automático poderia aceitar o que o manual recusa.

Ficou para as próximas subtarefas da PI3-96:

A explicação do tratamento de colisão, que o javadoc do IndiceEstoque menciona em uma linha mas não desenvolve, e a equivalência com a versão em C/C++ do índice hash. Nenhum código em C foi escrito nesta subtarefa.
