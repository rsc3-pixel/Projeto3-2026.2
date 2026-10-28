package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.estruturas.ResultadoRota;

import java.util.Objects;

/**
 * Resultado de uma alocacao com rota: ou a bolsa escolhida com o caminho ate
 * ela, ou o motivo de nao haver bolsa para entregar.
 *
 * <p>E a resposta unica do {@link ServicoAlocacaoRota}: sucesso e falha saem
 * pelo mesmo tipo, e a falha carrega um {@link MotivoFalhaAlocacao}
 * distinguivel em vez de excecao (PI3-57). Segue a mesma decisao do
 * {@link ResultadoRota}: ausencia de solucao e um estado legitimo do dominio,
 * nao um erro.</p>
 *
 * <p>Os acessores sao guardados: ler {@link #bolsa()} de uma falha ou
 * {@link #motivo()} de um sucesso lanca {@link IllegalStateException}, porque
 * ai sim ha um erro de programa - quem consome precisa checar
 * {@link #sucesso()} antes. A classe e imutavel e pode circular pelo sistema
 * sem risco de ser alterada.</p>
 */
public final class ResultadoAlocacao {

    private final Bolsa bolsa;
    private final String unidadeDaBolsa;
    private final ResultadoRota<String> rota;
    private final MotivoFalhaAlocacao motivo;

    private ResultadoAlocacao(Bolsa bolsa, String unidadeDaBolsa,
                              ResultadoRota<String> rota, MotivoFalhaAlocacao motivo) {
        this.bolsa = bolsa;
        this.unidadeDaBolsa = unidadeDaBolsa;
        this.rota = rota;
        this.motivo = motivo;
    }

    /**
     * Alocacao bem-sucedida.
     *
     * @param bolsa          a bolsa escolhida pela ordem FEFO
     * @param unidadeDaBolsa unidade da malha onde a bolsa esta
     * @param rota           caminho da solicitante ate a unidade da bolsa;
     *                       precisa ser alcancavel, senao a alocacao nao teria
     *                       como ser cumprida
     */
    public static ResultadoAlocacao alocada(Bolsa bolsa, String unidadeDaBolsa,
                                            ResultadoRota<String> rota) {
        Objects.requireNonNull(bolsa, "Bolsa nao pode ser nula");
        Objects.requireNonNull(unidadeDaBolsa, "Unidade da bolsa nao pode ser nula");
        Objects.requireNonNull(rota, "Rota nao pode ser nula");
        if (!rota.alcancavel()) {
            throw new IllegalArgumentException(
                    "Alocacao bem-sucedida exige rota alcancavel ate a bolsa");
        }
        return new ResultadoAlocacao(bolsa, unidadeDaBolsa, rota, null);
    }

    /**
     * Alocacao sem bolsa para entregar, com o motivo distinguivel.
     */
    public static ResultadoAlocacao falha(MotivoFalhaAlocacao motivo) {
        Objects.requireNonNull(motivo, "Motivo da falha nao pode ser nulo");
        return new ResultadoAlocacao(null, null, null, motivo);
    }

    /**
     * Indica se uma bolsa foi escolhida. Quando falso, o motivo esta em
     * {@link #motivo()}.
     */
    public boolean sucesso() {
        return motivo == null;
    }

    /**
     * A bolsa escolhida pela ordem FEFO entre as alcancaveis.
     *
     * @throws IllegalStateException se a alocacao falhou
     */
    public Bolsa bolsa() {
        exigirSucesso("bolsa");
        return bolsa;
    }

    /**
     * Unidade da malha onde a bolsa escolhida esta. E o ultimo vertice do
     * caminho da rota.
     *
     * @throws IllegalStateException se a alocacao falhou
     */
    public String unidadeDaBolsa() {
        exigirSucesso("unidade da bolsa");
        return unidadeDaBolsa;
    }

    /**
     * Caminho minimo da unidade solicitante ate a unidade da bolsa.
     *
     * @throws IllegalStateException se a alocacao falhou
     */
    public ResultadoRota<String> rota() {
        exigirSucesso("rota");
        return rota;
    }

    /**
     * Tempo total do caminho ate a bolsa, em minutos. Zero quando a bolsa
     * esta na propria unidade solicitante.
     *
     * @throws IllegalStateException se a alocacao falhou
     */
    public double tempoMinutos() {
        exigirSucesso("tempo");
        return rota.custoTotal();
    }

    /**
     * Motivo da falha.
     *
     * @throws IllegalStateException se a alocacao teve sucesso
     */
    public MotivoFalhaAlocacao motivo() {
        if (sucesso()) {
            throw new IllegalStateException(
                    "Alocacao teve sucesso, nao ha motivo de falha; cheque sucesso() antes");
        }
        return motivo;
    }

    private void exigirSucesso(String oQueFoiPedido) {
        if (!sucesso()) {
            throw new IllegalStateException("Alocacao falhou (" + motivo + "), nao ha "
                    + oQueFoiPedido + "; cheque sucesso() antes");
        }
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ResultadoAlocacao that)) {
            return false;
        }
        return Objects.equals(bolsa, that.bolsa)
                && Objects.equals(unidadeDaBolsa, that.unidadeDaBolsa)
                && Objects.equals(rota, that.rota)
                && motivo == that.motivo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bolsa, unidadeDaBolsa, rota, motivo);
    }

    @Override
    public String toString() {
        if (!sucesso()) {
            return "ResultadoAlocacao{falha=" + motivo + "}";
        }
        return "ResultadoAlocacao{bolsa=" + bolsa.getCodigoRastreio()
                + ", unidade=" + unidadeDaBolsa
                + ", tempoMinutos=" + rota.custoTotal() + "}";
    }
}
