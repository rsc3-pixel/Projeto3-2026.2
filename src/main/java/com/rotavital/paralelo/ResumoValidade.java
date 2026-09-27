package com.rotavital.paralelo;

public record ResumoValidade(
        long n,
        double media,
        double mediana,
        double desvioPadraoAmostral,
        long min,
        long max) {

    private static final ResumoValidade VAZIO = new ResumoValidade(0, 0.0, 0.0, 0.0, 0, 0);

    public static ResumoValidade vazio() {
        return VAZIO;
    }
}
