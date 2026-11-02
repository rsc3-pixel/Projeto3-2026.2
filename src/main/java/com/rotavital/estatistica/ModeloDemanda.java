package com.rotavital.estatistica;

import java.util.List;

/**
 * Distribuicoes usadas para tratar a demanda por bolsas como variavel aleatoria (PI3-102, PI3-32).
 *
 * <p>Porta em Java do modulo C++ ({@code cpp/src/ModeloDemanda.cpp}), para que o painel e a API
 * calculem a probabilidade de desabastecimento sobre o banco, e nao sobre um JSON gerado fora da
 * aplicacao. A distribuicao adotada e a <b>Binomial Negativa</b>: nos dados do projeto a variancia da
 * demanda diaria e 2 a 4 vezes a media, e a Poisson, que supoe variancia igual a media, subestima o
 * risco justamente nos tipos com estoque proximo da demanda.</p>
 *
 * <p>Todas as somas sao feitas em espaco logaritmico. Sem isso, {@code exp(-lambda)} vira zero para
 * demandas na casa das centenas e a probabilidade sairia errada.</p>
 */
public final class ModeloDemanda {

    private ModeloDemanda() {
        // Classe utilitaria: so funcoes puras, sem estado.
    }

    /**
     * P(D &lt;= k) para D ~ Poisson(lambda).
     *
     * <p>Usa a recorrencia P(i) = P(i-1) * lambda / i, entao nao precisa de fatorial nem de
     * aproximacao Normal para lambdas grandes.</p>
     */
    public static double poissonCdf(double lambda, long k) {
        if (k < 0) {
            return 0.0;
        }
        if (lambda <= 0.0) {
            return 1.0;
        }
        double logTermo = -lambda; // ln P(D = 0)
        double cdf = Math.exp(logTermo);
        for (long i = 1; i <= k; i++) {
            logTermo += Math.log(lambda) - Math.log(i);
            cdf += Math.exp(logTermo);
        }
        return Math.min(cdf, 1.0);
    }

    /**
     * P(D &lt;= k) para D ~ Binomial Negativa parametrizada por media e variancia.
     *
     * <p>Com {@code r = media^2 / (variancia - media)} e {@code p = r / (r + media)}, a recorrencia e
     * P(i) = P(i-1) * (i - 1 + r) / i * (1 - p). Quando a variancia nao supera a media nao ha
     * sobredispersao e o calculo cai na Poisson, que e o limite da Binomial Negativa.</p>
     */
    public static double binomialNegativaCdf(double media, double variancia, long k) {
        if (k < 0) {
            return 0.0;
        }
        if (media <= 0.0) {
            return 1.0;
        }
        if (variancia <= media) {
            return poissonCdf(media, k);
        }
        double r = (media * media) / (variancia - media);
        double p = r / (r + media);
        double log1MenosP = Math.log1p(-p);

        double logTermo = r * Math.log(p); // ln P(D = 0) = r ln p
        double cdf = Math.exp(logTermo);
        for (long i = 1; i <= k; i++) {
            logTermo += Math.log(i - 1 + r) - Math.log(i) + log1MenosP;
            cdf += Math.exp(logTermo);
        }
        return Math.min(cdf, 1.0);
    }

    /** P(D &gt; estoque) pela Poisson: probabilidade de a demanda superar o estoque valido. */
    public static double probabilidadeExcedentePoisson(double lambda, long estoque) {
        if (estoque < 0) {
            return 1.0;
        }
        return limitar(1.0 - poissonCdf(lambda, estoque));
    }

    /** P(D &gt; estoque) pela Binomial Negativa: a probabilidade adotada no painel. */
    public static double probabilidadeExcedenteBinomialNegativa(double media, double variancia, long estoque) {
        if (estoque < 0) {
            return 1.0;
        }
        return limitar(1.0 - binomialNegativaCdf(media, variancia, estoque));
    }

    /**
     * Variancia da demanda total no horizonte observado: N vezes a variancia amostral da serie
     * diaria, porque a demanda do horizonte e a soma de N dias independentes.
     *
     * @return {@code -1} quando a serie tem menos de dois dias e a variancia nao pode ser medida
     */
    public static double varianciaDoHorizonte(List<Long> serieDiaria) {
        int n = serieDiaria.size();
        if (n < 2) {
            return -1.0;
        }
        double soma = 0.0;
        for (long v : serieDiaria) {
            soma += v;
        }
        double mediaDia = soma / n;
        double somaQuadrados = 0.0;
        for (long v : serieDiaria) {
            double diferenca = v - mediaDia;
            somaQuadrados += diferenca * diferenca;
        }
        return n * (somaQuadrados / (n - 1));
    }

    private static double limitar(double probabilidade) {
        return Math.max(0.0, Math.min(1.0, probabilidade));
    }
}
