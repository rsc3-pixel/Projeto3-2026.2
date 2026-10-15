package com.rotavital.alocacao;

import com.rotavital.compatibilidade.MatrizCompatibilidade;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.FilaPrioridadeFefo;
import com.rotavital.estruturas.IndiceEstoque;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Selecao FEFO (First Expired, First Out) da bolsa a ser alocada em uma
 * requisicao.
 *
 * <p>Atende os criterios de aceite da PI3-19 sobre alocacao: a bolsa escolhida
 * e sempre a de validade mais proxima entre as que podem ser alocadas, bolsa
 * vencida ou indisponivel nunca e escolhida, e o desempate e deterministico.</p>
 *
 * <p>FEFO existe para reduzir descarte por vencimento: consumindo primeiro o
 * que vence antes, sobra no estoque o que tem mais prazo. E o mesmo motivo
 * pelo qual o peso do grafo e tempo e nao distancia (secao 3 do escopo do
 * grafo) - o que esta em jogo e a validade da bolsa.</p>
 *
 * <p>Desde a PI3-129 a selecao tambem consulta a
 * {@link MatrizCompatibilidade}: as sobrecargas que recebem o grupo do
 * receptor e o hemocomponente descartam bolsa incompativel junto com a
 * vencida e a indisponivel. As sobrecargas sem esses argumentos seguem
 * valendo para quem ja filtrou a compatibilidade antes.</p>
 *
 * <p>A ordenacao reusa a {@link FilaPrioridadeFefo}, que ja ordena por data de
 * validade com desempate por codigo de rastreio. Escrever um comparador novo
 * aqui abriria espaco para as duas ordens divergirem.</p>
 *
 * <p><b>Complexidade:</b> O(n log n) para n bolsas candidatas, custo de
 * alimentar a fila de prioridade. O filtro de alocaveis e O(n) e nao altera a
 * ordem de grandeza.</p>
 */
public final class SelecaoFefo {

    private SelecaoFefo() {
        throw new AssertionError("Classe utilitaria, nao deve ser instanciada");
    }

    /**
     * Indica se a bolsa pode ser alocada na data de referencia.
     *
     * <p>Sao duas condicoes independentes: a bolsa nao pode estar vencida e
     * precisa estar {@link StatusBolsa#DISPONIVEL}. Bolsa ja reservada, em
     * transito, entregue ou descartada esta comprometida com outra requisicao
     * ou fora de circulacao, e por isso nao entra na selecao mesmo que ainda
     * esteja dentro da validade.</p>
     *
     * <p>Bolsa nula nao e alocavel, em vez de lancar excecao: lista de
     * candidatas com buraco e situacao de dado, nao erro de programa.</p>
     *
     * <p>Vale registrar a borda, porque e regra de negocio: bolsa cuja
     * validade e exatamente a data de referencia ainda e alocavel. Quem decide
     * isso e o {@link Bolsa#estaVencida(LocalDate)}, que usa
     * {@code isAfter} - a bolsa so vence no dia seguinte a validade.</p>
     */
    public static boolean alocavel(Bolsa bolsa, LocalDate dataReferencia) {
        if (bolsa == null) {
            return false;
        }
        return !bolsa.estaVencida(dataReferencia)
                && bolsa.getStatus() == StatusBolsa.DISPONIVEL;
    }

    /**
     * Indica se a bolsa pode ser alocada para um receptor especifico.
     *
     * <p>Soma a compatibilidade transfusional as duas condicoes da sobrecarga
     * de dois argumentos: a bolsa precisa estar dentro da validade,
     * {@link StatusBolsa#DISPONIVEL} e ter grupo que possa doar para o
     * receptor naquele hemocomponente.</p>
     *
     * <p><b>A ordem dos argumentos importa, porque a relacao nao e
     * simetrica:</b> a bolsa e a doadora e o {@code receptor} e o paciente.
     * Invertidos, uma bolsa A+ passaria a ser aceita por um receptor O-, que
     * e exatamente a transfusao que mata. Por isso o parametro se chama
     * {@code receptor} e nao {@code grupo}.</p>
     *
     * <p>A compatibilidade e delegada a {@link MatrizCompatibilidade} (PI3-84)
     * em vez de recalculada aqui: a tabela de hemacias e a de plasma, que e
     * invertida, vivem em um lugar so.</p>
     *
     * @param receptor grupo sanguineo do paciente
     * @param tipo     hemocomponente, que decide qual tabela vale
     * @return {@code false} tambem quando {@code receptor} ou {@code tipo}
     *         forem nulos: ausencia de dado nao autoriza transfusao
     */
    public static boolean alocavel(Bolsa bolsa, LocalDate dataReferencia,
                                   GrupoSanguineo receptor, TipoHemocomponente tipo) {
        // A sobrecarga de dois argumentos ja rejeita bolsa nula, entao o
        // curto-circuito protege o getGrupoSanguineo() abaixo.
        return alocavel(bolsa, dataReferencia)
                && MatrizCompatibilidade.compativel(
                        bolsa.getGrupoSanguineo(), receptor, tipo);
    }

    /**
     * Candidatas alocaveis em ordem FEFO, da validade mais proxima para a mais
     * distante.
     *
     * <p>As nao alocaveis sao descartadas, nao apenas rebaixadas: uma bolsa
     * vencida nao e uma opcao pior, e uma opcao invalida.</p>
     *
     * @return lista imutavel; vazia se a entrada for nula, vazia ou se nenhuma
     *         candidata for alocavel
     */
    public static List<Bolsa> ordenar(List<Bolsa> candidatas, LocalDate dataReferencia) {
        return drenar(filaDeAlocaveis(candidatas,
                candidata -> alocavel(candidata, dataReferencia)));
    }

    /**
     * Candidatas alocaveis para um receptor especifico, em ordem FEFO.
     *
     * <p>Descarta, alem das vencidas e das indisponiveis, as incompativeis com
     * o receptor naquele hemocomponente. Bolsa incompativel nao e uma opcao
     * pior: e uma opcao que nao pode ser transfundida.</p>
     *
     * @return lista imutavel; vazia se nenhuma candidata servir
     */
    public static List<Bolsa> ordenar(List<Bolsa> candidatas, LocalDate dataReferencia,
                                      GrupoSanguineo receptor, TipoHemocomponente tipo) {
        return drenar(filaDeAlocaveis(candidatas,
                candidata -> alocavel(candidata, dataReferencia, receptor, tipo)));
    }

    /**
     * A bolsa a ser alocada: a primeira da ordem FEFO entre as alocaveis.
     *
     * <p>Devolve {@link Optional#empty()} quando nenhuma candidata serve, em
     * vez de {@code null} ou excecao. Estoque sem bolsa utilizavel e situacao
     * normal da operacao, e quem chama decide o que fazer com isso.</p>
     */
    public static Optional<Bolsa> escolher(List<Bolsa> candidatas, LocalDate dataReferencia) {
        // consultarProxima() ja devolve o topo da fila, entao nao ha por que
        // materializar a ordem inteira so para ler o primeiro elemento.
        return Optional.ofNullable(filaDeAlocaveis(candidatas,
                candidata -> alocavel(candidata, dataReferencia)).consultarProxima());
    }

    /**
     * A bolsa a ser alocada para um receptor especifico: a primeira da ordem
     * FEFO entre as que sao compativeis, estao na validade e estao
     * disponiveis.
     *
     * <p>Lembrando que a ordem dos argumentos nao e intercambiavel: o
     * {@code receptor} e o paciente, e as bolsas da lista sao as doadoras.</p>
     *
     * @return {@link Optional#empty()} quando nenhuma candidata serve, o que
     *         inclui o caso de existir estoque na validade mas todo
     *         incompativel com o receptor
     */
    public static Optional<Bolsa> escolher(List<Bolsa> candidatas, LocalDate dataReferencia,
                                           GrupoSanguineo receptor, TipoHemocomponente tipo) {
        return Optional.ofNullable(filaDeAlocaveis(candidatas,
                candidata -> alocavel(candidata, dataReferencia, receptor, tipo))
                .consultarProxima());
    }

    /**
     * A bolsa a ser alocada para um receptor, buscando no indice de estoque.
     *
     * <p>E a forma usada pelo fluxo de requisicao: o indice resolve "quais
     * bolsas existem desta combinacao" em O(1), e a selecao FEFO decide qual
     * delas sai.</p>
     *
     * <p><b>Desde a PI3-129 o grupo recebido e tratado como RECEPTOR, nao
     * como filtro exato.</b> Antes esta sobrecarga procurava literalmente o
     * grupo pedido, o que recusava doacao valida e deixava estoque utilizavel
     * parado: um paciente A+ so receberia bolsa A+, ignorando O-, O+ e A-.
     * Agora a {@link MatrizCompatibilidade#doadoresPara} diz quais grupos
     * procurar, as candidatas de todos eles sao reunidas e a selecao FEFO
     * roda sobre o conjunto. E a mesma regra que o
     * {@code RequisicaoServico.alocar} ja aplicava na validacao.</p>
     *
     * <p>Consultar a matriz tambem evita percorrer os oito grupos: para
     * hemacias de receptor O- ha uma unica busca no indice, nao oito.</p>
     *
     * @param receptor grupo sanguineo do paciente
     * @return {@link Optional#empty()} se o indice for nulo, se o receptor ou
     *         o tipo forem nulos, se nao houver bolsa de nenhum grupo
     *         compativel ou se nenhuma das encontradas for alocavel
     */
    public static Optional<Bolsa> escolher(IndiceEstoque estoque,
                                           GrupoSanguineo receptor,
                                           TipoHemocomponente tipo,
                                           LocalDate dataReferencia) {
        if (estoque == null) {
            return Optional.empty();
        }

        // Conjunto vazio quando receptor ou tipo sao nulos, o que faz o laco
        // nao executar e dispensa checagem de nulo aqui. De quebra, o indice
        // nunca e consultado com chave nula.
        Set<GrupoSanguineo> doadores = MatrizCompatibilidade.doadoresPara(receptor, tipo);

        List<Bolsa> candidatas = new ArrayList<>();
        for (GrupoSanguineo doador : doadores) {
            candidatas.addAll(estoque.buscar(doador, tipo));
        }

        // Reaplica a compatibilidade na selecao, e nao apenas confia nos
        // grupos que foram buscados: assim a regra continua verificada em um
        // unico lugar, mesmo que o indice venha a ser alimentado por outro
        // caminho.
        return escolher(candidatas, dataReferencia, receptor, tipo);
    }

    /**
     * Fila FEFO contendo apenas as candidatas que satisfazem o criterio.
     *
     * <p>E o unico lugar onde o filtro e a ordenacao se encontram. As cinco
     * entradas publicas - as duas de {@code ordenar} e as tres de
     * {@code escolher} - passam por aqui, cada uma informando o seu criterio,
     * para que nenhuma possa divergir na regra.</p>
     *
     * <p>O criterio chega como {@link Predicate} em vez de um sinalizador de
     * "checar compatibilidade ou nao": assim as variantes com e sem receptor
     * compartilham o laco sem que ele precise saber qual delas o chamou.</p>
     */
    private static FilaPrioridadeFefo filaDeAlocaveis(List<Bolsa> candidatas,
                                                      Predicate<Bolsa> criterio) {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();

        if (candidatas == null || candidatas.isEmpty()) {
            return fila;
        }

        // ATENCAO: o filtro vem ANTES da ordenacao, e essa ordem nao pode ser
        // invertida. FEFO poe no topo quem vence primeiro, e uma bolsa vencida
        // vence antes de todas as validas. Se a fila fosse montada com todas as
        // candidatas para so depois descartar as nao alocaveis, a vencida
        // ocuparia o topo em toda selecao. Filtrando aqui, ela nunca entra.
        //
        // O mesmo raciocinio vale para a compatibilidade (PI3-129), e por isso
        // ela entra neste filtro e nao depois dele: uma bolsa incompativel que
        // vence amanha e, pelo criterio FEFO, melhor que qualquer bolsa
        // compativel com mais prazo. Filtrada depois da ordenacao, ela
        // ocuparia o topo de toda selecao e a escolha teria de ser descartada
        // e refeita. Filtrada aqui, ela nunca chega a competir.
        for (Bolsa candidata : candidatas) {
            if (criterio.test(candidata)) {
                fila.adicionar(candidata);
            }
        }

        return fila;
    }

    /**
     * Esvazia a fila na ordem de prioridade, produzindo a lista imutavel.
     *
     * <p>Fica separado para que as duas sobrecargas de
     * {@code ordenar} nao repitam o laco de retirada.</p>
     */
    private static List<Bolsa> drenar(FilaPrioridadeFefo fila) {
        List<Bolsa> ordenadas = new ArrayList<>(fila.tamanho());
        while (!fila.estaVazia()) {
            ordenadas.add(fila.retirarProxima());
        }
        return List.copyOf(ordenadas);
    }
}
