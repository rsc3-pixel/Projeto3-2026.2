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
 * <p>A classe e imutavel e as duas listas sao imutaveis, entao o resultado
 * pode circular pelo sistema sem risco de ser alterado por quem o recebe.
 * Segue o mesmo padrao do {@link ResultadoRota}: construtor privado, fabrica
 * estatica e copia defensiva na entrada.</p>
 */
public final class ResultadoRoteiro<T> {

    private final List<T> ordemDeVisita;
    private final List<T> caminhoCompleto;
    private final double custoTotalMinutos;

    private ResultadoRoteiro(List<T> ordemDeVisita,
                             List<T> caminhoCompleto,
                             double custoTotalMinutos) {
        this.ordemDeVisita = ordemDeVisita;
        this.caminhoCompleto = caminhoCompleto;
        this.custoTotalMinutos = custoTotalMinutos;
    }

    /**
     * Roteiro calculado.
     *
     * @param ordemDeVisita    pontos de entrega na ordem em que foram
     *                         visitados; pode ser vazia, quando nenhum ponto
     *                         foi alcancado
     * @param caminhoCompleto  vertices atravessados, em ordem, comecando na
     *                         origem; precisa conter ao menos a origem
     * @param custoTotalMinutos soma dos tempos dos trechos, em minutos
     */
    public static <T> ResultadoRoteiro<T> de(List<T> ordemDeVisita,
                                             List<T> caminhoCompleto,
                                             double custoTotalMinutos) {
        Objects.requireNonNull(ordemDeVisita, "Ordem de visita nao pode ser nula");
        Objects.requireNonNull(caminhoCompleto, "Caminho completo nao pode ser nulo");
        if (caminhoCompleto.isEmpty()) {
            throw new IllegalArgumentException(
                    "Caminho completo precisa conter ao menos a origem");
        }
        return new ResultadoRoteiro<>(
                List.copyOf(ordemDeVisita), List.copyOf(caminhoCompleto), custoTotalMinutos);
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
                && caminhoCompleto.equals(that.caminhoCompleto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ordemDeVisita, caminhoCompleto, custoTotalMinutos);
    }

    @Override
    public String toString() {
        return "ResultadoRoteiro{ordemDeVisita=" + ordemDeVisita
                + ", caminhoCompleto=" + caminhoCompleto
                + ", custoTotalMinutos=" + custoTotalMinutos + "}";
    }
}
