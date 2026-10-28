package com.rotavital.estruturas;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de um calculo de rota: o caminho minimo entre dois vertices e o
 * tempo total desse caminho.
 *
 * <p>Complementa o {@link Dijkstra#calcularDistancias(Object)}, que responde
 * apenas "quanto custa chegar". Para despachar uma entrega nao basta o custo:
 * e preciso saber por quais unidades o veiculo passa, e e isso que o
 * {@link #caminho()} carrega.</p>
 *
 * <p>A classe e imutavel e o caminho e uma lista imutavel, entao o resultado
 * pode circular pelo sistema sem risco de ser alterado por quem o recebe.</p>
 *
 * <p>Rota inexistente e representada por {@link #semCaminho()}, com
 * {@link #alcancavel()} falso e caminho vazio, nunca {@code null}. Segue a
 * mesma decisao do {@code calcularDistancias}: ausencia de caminho e um estado
 * legitimo do dominio, nao um erro, e quem consome nao precisa se defender de
 * ponteiro nulo.</p>
 */
public final class ResultadoRota<T> {

    private final List<T> caminho;
    private final double custoTotal;
    private final boolean alcancavel;

    private ResultadoRota(List<T> caminho, double custoTotal, boolean alcancavel) {
        this.caminho = caminho;
        this.custoTotal = custoTotal;
        this.alcancavel = alcancavel;
    }

    /**
     * Rota encontrada.
     *
     * @param caminho vertices em ordem, da origem ao destino; precisa conter
     *                ao menos a origem
     * @param custo   tempo acumulado do caminho, em minutos
     */
    public static <T> ResultadoRota<T> alcancado(List<T> caminho, double custo) {
        Objects.requireNonNull(caminho, "Caminho nao pode ser nulo");
        if (caminho.isEmpty()) {
            throw new IllegalArgumentException("Rota alcancada precisa de ao menos um vertice");
        }
        return new ResultadoRota<>(List.copyOf(caminho), custo, true);
    }

    /**
     * Destino inalcancavel a partir da origem.
     *
     * @return resultado com caminho vazio e custo zero
     */
    public static <T> ResultadoRota<T> semCaminho() {
        return new ResultadoRota<>(List.of(), 0.0, false);
    }

    /**
     * Vertices percorridos, em ordem, da origem ate o destino. Lista
     * imutavel; vazia quando nao ha caminho.
     */
    public List<T> caminho() {
        return caminho;
    }

    /**
     * Tempo acumulado do caminho, em minutos. Zero quando nao ha caminho.
     */
    public double custoTotal() {
        return custoTotal;
    }

    /**
     * Indica se existe caminho da origem ate o destino.
     */
    public boolean alcancavel() {
        return alcancavel;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ResultadoRota<?> that)) {
            return false;
        }
        return alcancavel == that.alcancavel
                && Double.compare(custoTotal, that.custoTotal) == 0
                && caminho.equals(that.caminho);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caminho, custoTotal, alcancavel);
    }

    @Override
    public String toString() {
        if (!alcancavel) {
            return "ResultadoRota{sem caminho}";
        }
        return "ResultadoRota{caminho=" + caminho + ", custoTotal=" + custoTotal + "}";
    }
}
