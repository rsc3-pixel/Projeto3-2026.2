package com.rotavital.estruturas;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de um roteiro com varias paradas: em que ordem os pontos de
 * entrega foram visitados, o caminho percorrido vertice a vertice e o tempo
 * total.
 *
 * <p>Complementa o {@link ResultadoRota}, que responde por um unico par
 * origem-destino. Um roteiro encadeia varios trechos, entao precisa separar
 * duas coisas que a rota simples nao distingue: a sequencia de paradas
 * ({@link #ordemDeVisita()}) e a sequencia de unidades atravessadas
 * ({@link #caminhoCompleto()}). Na malha esparsa da rede, ir de uma parada a
 * outra costuma passar por unidades que nao sao ponto de entrega, e elas
 * aparecem no caminho sem aparecer na ordem de visita.</p>
 *
 * <p>Carrega tambem os pontos que nao puderam ser atendidos
 * ({@link #pontosInalcancaveis()}). Ponto sem caminho e estado legitimo do
 * dominio, nao erro: a mesma decisao que o {@link ResultadoRota#semCaminho()}
 * ja adota para a rota simples e que o {@code MotivoFalhaAlocacao} (PI3-57)
 * adota para a alocacao, devolvendo um motivo distinguivel em vez de excecao.
 * Quem consome pergunta por {@link #alcancouTodos()} e decide o que fazer com
 * o que sobrou, em vez de se defender de um lancamento.</p>
 *
 * <p>A classe e imutavel e as tres listas sao imutaveis, entao o resultado
 * pode circular pelo sistema sem risco de ser alterado por quem o recebe.
 * Segue o mesmo padrao do {@link ResultadoRota}: construtor privado, fabrica
 * estatica e copia defensiva na entrada.</p>
 */
public final class ResultadoRoteiro<T> {

    private final List<T> ordemDeVisita;
    private final List<T> caminhoCompleto;
    private final List<T> pontosInalcancaveis;
    private final double custoTotalMinutos;

    private ResultadoRoteiro(List<T> ordemDeVisita,
                             List<T> caminhoCompleto,
                             List<T> pontosInalcancaveis,
                             double custoTotalMinutos) {
        this.ordemDeVisita = ordemDeVisita;
        this.caminhoCompleto = caminhoCompleto;
        this.pontosInalcancaveis = pontosInalcancaveis;
        this.custoTotalMinutos = custoTotalMinutos;
    }

    /**
     * Roteiro calculado.
     *
     * @param ordemDeVisita       pontos de entrega na ordem em que foram
     *                            visitados; pode ser vazia, quando nenhum
     *                            ponto foi alcancado
     * @param caminhoCompleto     vertices atravessados, em ordem, comecando na
     *                            origem; precisa conter ao menos a origem
     * @param pontosInalcancaveis pontos pedidos que nao puderam ser atendidos,
     *                            na ordem em que foram informados; vazia
     *                            quando o roteiro atendeu a todos
     * @param custoTotalMinutos   soma dos tempos dos trechos, em minutos
     */
    public static <T> ResultadoRoteiro<T> de(List<T> ordemDeVisita,
                                             List<T> caminhoCompleto,
                                             List<T> pontosInalcancaveis,
                                             double custoTotalMinutos) {
        Objects.requireNonNull(ordemDeVisita, "Ordem de visita nao pode ser nula");
        Objects.requireNonNull(caminhoCompleto, "Caminho completo nao pode ser nulo");
        Objects.requireNonNull(pontosInalcancaveis,
                "Lista de inalcancaveis nao pode ser nula; use lista vazia");
        if (caminhoCompleto.isEmpty()) {
            throw new IllegalArgumentException(
                    "Caminho completo precisa conter ao menos a origem");
        }
        return new ResultadoRoteiro<>(
                List.copyOf(ordemDeVisita),
                List.copyOf(caminhoCompleto),
                List.copyOf(pontosInalcancaveis),
                custoTotalMinutos);
    }

    /**
     * Pontos de entrega na ordem em que foram visitados. Nao inclui a origem,
     * a menos que ela tambem seja ponto de entrega, nem as unidades de
     * passagem.
     *
     * @return lista imutavel
     */
    public List<T> ordemDeVisita() {
        return ordemDeVisita;
    }

    /**
     * Unidades atravessadas, vertice a vertice, da origem ao ultimo ponto
     * visitado. Inclui as unidades de passagem e nunca repete o vertice de
     * juncao entre dois trechos.
     *
     * @return lista imutavel
     */
    public List<T> caminhoCompleto() {
        return caminhoCompleto;
    }

    /**
     * Tempo total do roteiro, em minutos: a soma dos trechos percorridos.
     */
    public double custoTotalMinutos() {
        return custoTotalMinutos;
    }

    /**
     * Pontos pedidos que nao puderam ser atendidos, na ordem em que foram
     * informados.
     *
     * <p>Um ponto cai aqui quando nao ha caminho ate ele a partir de onde o
     * roteiro parou, ou quando ele nem existe no grafo. A lista vazia e o
     * caso normal; lista com itens nao indica falha do calculo, e sim o que
     * a malha nao permitiu entregar.</p>
     *
     * @return lista imutavel
     */
    public List<T> pontosInalcancaveis() {
        return pontosInalcancaveis;
    }

    /**
     * Indica se todos os pontos pedidos foram atendidos.
     *
     * @return {@code true} quando nao sobrou nenhum ponto inalcancavel
     */
    public boolean alcancouTodos() {
        return pontosInalcancaveis.isEmpty();
    }

    /**
     * Quantidade de pontos de entrega visitados.
     */
    public int quantidadeVisitada() {
        return ordemDeVisita.size();
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ResultadoRoteiro<?> that)) {
            return false;
        }
        return Double.compare(custoTotalMinutos, that.custoTotalMinutos) == 0
                && ordemDeVisita.equals(that.ordemDeVisita)
                && caminhoCompleto.equals(that.caminhoCompleto)
                && pontosInalcancaveis.equals(that.pontosInalcancaveis);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ordemDeVisita, caminhoCompleto, pontosInalcancaveis, custoTotalMinutos);
    }

    @Override
    public String toString() {
        return "ResultadoRoteiro{ordemDeVisita=" + ordemDeVisita
                + ", caminhoCompleto=" + caminhoCompleto
                + ", pontosInalcancaveis=" + pontosInalcancaveis
                + ", custoTotalMinutos=" + custoTotalMinutos + "}";
    }
}
