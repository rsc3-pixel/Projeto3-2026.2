package com.rotavital.estruturas;

import com.rotavital.dominio.Bolsa;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fila de prioridade FEFO (First Expired, First Out) por insercao ordenada.
 * A bolsa com a data de validade mais proxima sempre fica no inicio.
 *
 * <p><b>A tecnica.</b> A lista e mantida ordenada o tempo todo: cada bolsa
 * nova e colocada direto na posicao certa, percorrendo a partir do inicio ate
 * encontrar o ponto de encaixe. Nao existe passo de ordenacao depois - a
 * ordem e invariante da estrutura. E a insercao ordenada pedida na Unidade 2
 * de AED, e substitui o heap binario que a Unidade 1 usava (PI3-47) via
 * {@code PriorityQueue} com {@code Comparator}.</p>
 *
 * <p>O criterio de ordem e exatamente o de antes - data de validade
 * crescente, e no empate codigo de rastreio crescente - entao a troca e
 * transparente para quem consome. O desempate pelo codigo e o que torna o
 * resultado deterministico: duas bolsas de mesma validade sempre resolvem
 * para a mesma escolha, em qualquer ordem de montagem do estoque.</p>
 *
 * <p>O armazenamento e uma lista encadeada simples construida a mao, com a
 * classe interna {@link No}. Nenhuma colecao do {@code java.util} e usada
 * como armazenamento, pelo mesmo motivo da
 * {@link com.rotavital.estruturas.basicas.ListaEstoque}: a entrega e
 * demonstrar a estrutura, e chamar {@code TreeSet} ou {@code Collections.sort}
 * nao demonstraria nada.</p>
 *
 * <p><b>Complexidade, comparada com o heap da Unidade 1:</b></p>
 * <ul>
 *   <li>{@link #adicionar(Bolsa)}: O(n), contra O(log n) do heap - e o preco
 *       da tecnica, porque a posicao certa e encontrada percorrendo;</li>
 *   <li>{@link #consultarProxima()}: O(1), igual ao heap, que tambem olha so
 *       a raiz;</li>
 *   <li>{@link #retirarProxima()}: O(1), contra O(log n) do heap, que precisa
 *       reorganizar a arvore depois de remover a raiz;</li>
 *   <li>{@link #descartarVencidas(LocalDate)}: O(k) para k bolsas vencidas,
 *       porque elas estao todas no inicio;</li>
 *   <li>{@link #tamanho()} e {@link #estaVazia()}: O(1), por contador.</li>
 * </ul>
 *
 * <p>O O(n) da insercao e aceitavel neste dominio: a fila guarda as bolsas de
 * uma combinacao de grupo sanguineo e hemocomponente, que sao poucas por
 * unidade. Em troca, consultar e retirar - que e o que a alocacao faz a cada
 * requisicao - passaram a ser O(1). A estrutura ficou mais cara onde o
 * estoque e abastecido e mais barata onde ele e consumido.</p>
 *
 * <p><b>Equivalencia com a versao em C.</b> Nao existe neste repositorio
 * codigo em C ou C++ de insercao ordenada: o diretorio {@code aed/} contem
 * apenas a Unidade 1, com lista encadeada, fila e pilha. O mapa abaixo e,
 * portanto, com o algoritmo classico, e nao com um arquivo do projeto:</p>
 * <ul>
 *   <li>{@code struct No { Bolsa bolsa; struct No *proximo; }} vira a classe
 *       interna {@link No};</li>
 *   <li>{@code struct No *proximo} vira a referencia {@code No.proximo};</li>
 *   <li>{@code NULL} vira {@code null}, tanto no fim da cadeia quanto na fila
 *       vazia;</li>
 *   <li>{@code lista->primeiro} vira o campo {@link #primeiro};</li>
 *   <li>{@code malloc(sizeof(No))} vira {@code new No(bolsa)}, e o
 *       {@code free} nao tem equivalente: o no desligado deixa de ser
 *       alcancavel e o coletor de lixo o recolhe;</li>
 *   <li>o caso do inicio, {@code novo->proximo = lista->primeiro;
 *       lista->primeiro = novo;}, vira as duas atribuicoes de mesmo nome, na
 *       mesma ordem;</li>
 *   <li>o percurso com dois ponteiros, {@code anterior} e {@code atual}, vira
 *       duas variaveis locais de mesmo nome, porque em lista simplesmente
 *       encadeada nao ha como voltar;</li>
 *   <li>a funcao de comparacao do C, que devolve inteiro negativo, zero ou
 *       positivo, vira o metodo privado {@link #vemAntes(Bolsa, Bolsa)}, que
 *       devolve booleano. Nao e um {@code Comparator}, de proposito: a versao
 *       em C compara campo a campo, e manter a comparacao explicita deixa as
 *       duas versoes lado a lado.</li>
 * </ul>
 */
public class FilaPrioridadeFefo {

    private No primeiro;
    private int tamanho;

    /**
     * No do encadeamento: a bolsa e a referencia para o proximo.
     *
     * <p>Equivale ao {@code struct No} da versao em C. A bolsa e final porque
     * o no nunca troca de conteudo: para mudar a fila, nos entram e saem.</p>
     */
    private static final class No {
        private final Bolsa bolsa;
        private No proximo;

        private No(Bolsa bolsa) {
            this.bolsa = bolsa;
            this.proximo = null;
        }
    }

    /**
     * Insere a bolsa na posicao que mantem a lista ordenada.
     *
     * <p>Insercao estavel: bolsa igual as existentes no criterio - mesma
     * validade e mesmo codigo - entra depois delas, nao antes.</p>
     *
     * @throws IllegalArgumentException se a bolsa for nula
     */
    public void adicionar(Bolsa bolsa) {
        if (bolsa == null) {
            throw new IllegalArgumentException("Bolsa nao pode ser nula");
        }

        No novo = new No(bolsa);

        if (primeiro == null) {
            // CASO 1 - fila vazia: o no novo e o unico, e tambem o primeiro.
            primeiro = novo;

        } else if (vemAntes(bolsa, primeiro.bolsa)) {
            // CASO 2 - a nova bolsa vence antes da que esta no inicio: ela
            // passa a ser o primeiro. O antigo primeiro e religado atras dela
            // ANTES de a referencia da fila mudar, senao o resto da cadeia
            // ficaria inalcancavel.
            novo.proximo = primeiro;
            primeiro = novo;

        } else {
            // CASO 3 - meio ou fim: percorre com dois ponteiros, porque em
            // lista simplesmente encadeada nao ha como voltar para religar.
            //
            // A condicao avanca enquanto a bolsa do 'atual' ainda vem antes
            // da nova. Usando vemAntes(atual, nova) - e nao a negacao de
            // vemAntes(nova, atual) - o laco passa tambem pelos empates, e a
            // nova bolsa acaba depois dos iguais: e o que torna a insercao
            // estavel.
            No anterior = primeiro;
            No atual = primeiro.proximo;

            while (atual != null && vemAntes(atual.bolsa, bolsa)) {
                anterior = atual;
                atual = atual.proximo;
            }

            // Encaixa entre 'anterior' e 'atual'. Quando 'atual' e nulo, o no
            // entra no fim da lista, e os dois casos se resolvem com as
            // mesmas duas linhas.
            novo.proximo = atual;
            anterior.proximo = novo;
        }

        tamanho++;
    }

    /**
     * A proxima bolsa a sair, sem retirar da fila.
     *
     * @return a bolsa de validade mais proxima, ou {@code null} se a fila
     *         estiver vazia. Estoque vazio nao e erro: e situacao normal da
     *         operacao, e quem consome converte em resposta tratada
     */
    public Bolsa consultarProxima() {
        return primeiro == null ? null : primeiro.bolsa;
    }

    /**
     * Retira e devolve a proxima bolsa.
     *
     * <p>Como a lista ja esta ordenada, retirar e apenas avancar a referencia
     * do inicio: nao ha reorganizacao, diferente do heap, que precisa
     * reequilibrar a arvore depois de remover a raiz.</p>
     *
     * @return a bolsa retirada, ou {@code null} se a fila estiver vazia
     */
    public Bolsa retirarProxima() {
        if (primeiro == null) {
            return null;
        }

        No removido = primeiro;
        primeiro = removido.proximo;

        // Solta a ligacao do no retirado para que ele nao segure a fila viva.
        removido.proximo = null;

        tamanho--;
        return removido.bolsa;
    }

    /**
     * Remove do inicio todas as bolsas vencidas na data de referencia.
     *
     * <p>Como a lista e ordenada por validade, as vencidas estao
     * necessariamente no inicio: basta remover enquanto a primeira estiver
     * vencida e parar na primeira valida. Nao e preciso percorrer o resto,
     * porque toda bolsa depois dela vence ainda mais tarde. E a vantagem de
     * manter a ordem como invariante - no heap, saber quais venceram exigiria
     * olhar a arvore inteira.</p>
     *
     * <p>A bolsa cuja validade e exatamente a data de referencia <b>nao</b> e
     * descartada. Quem decide isso e o {@link Bolsa#estaVencida(LocalDate)},
     * que usa {@code isAfter}: a bolsa so vence no dia seguinte a validade.</p>
     *
     * @param dataReferencia data usada para decidir o vencimento
     * @return as bolsas removidas, em ordem de validade; lista imutavel,
     *         vazia quando nada venceu
     * @throws IllegalArgumentException se a data de referencia for nula
     */
    public List<Bolsa> descartarVencidas(LocalDate dataReferencia) {
        if (dataReferencia == null) {
            throw new IllegalArgumentException("Data de referencia nao pode ser nula");
        }

        List<Bolsa> descartadas = new ArrayList<>();

        while (primeiro != null && primeiro.bolsa.estaVencida(dataReferencia)) {
            descartadas.add(retirarProxima());
        }

        return List.copyOf(descartadas);
    }

    public boolean estaVazia() {
        return primeiro == null;
    }

    /**
     * Quantidade de bolsas na fila. O contador e mantido em
     * {@link #adicionar(Bolsa)} e {@link #retirarProxima()}, entao a consulta
     * e O(1) em vez de percorrer a lista.
     */
    public int tamanho() {
        return tamanho;
    }

    /**
     * Decide se {@code a} deve sair antes de {@code b}: primeiro a validade
     * mais proxima, e no empate o menor codigo de rastreio.
     *
     * <p>E a comparacao explicita que a versao em C faria campo a campo, e nao
     * um {@code Comparator}, para que as duas versoes possam ser lidas lado a
     * lado. O criterio e o mesmo que a fila usava com o heap.</p>
     */
    private boolean vemAntes(Bolsa a, Bolsa b) {
        int comparacaoDeValidade = a.getDataValidade().compareTo(b.getDataValidade());
        if (comparacaoDeValidade != 0) {
            return comparacaoDeValidade < 0;
        }
        return a.getCodigoRastreio().compareTo(b.getCodigoRastreio()) < 0;
    }
}
