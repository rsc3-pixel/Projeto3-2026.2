package com.rotavital.estatistica;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModeloDemandaTest {

    @Test
    @DisplayName("CDF da Poisson bate com o valor calculado a mao: P(X <= 1 | lambda = 2) = 3e^-2")
    void poissonCdfValorDeReferencia() {
        assertThat(ModeloDemanda.poissonCdf(2.0, 1)).isCloseTo(3 * Math.exp(-2), within(1e-12));
        assertThat(ModeloDemanda.poissonCdf(2.0, -1)).isZero();
        assertThat(ModeloDemanda.poissonCdf(0.0, 5)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Poisson com lambda na casa dos milhares nao sofre underflow")
    void poissonCdfLambdaGrande() {
        // exp(-1500) e zero em double; a soma em espaco logaritmico continua certa
        assertThat(ModeloDemanda.poissonCdf(1500.0, 1500)).isBetween(0.49, 0.52);
        assertThat(ModeloDemanda.poissonCdf(1500.0, 1700)).isGreaterThan(0.999);
    }

    @Test
    @DisplayName("Sem sobredispersao a Binomial Negativa e a propria Poisson")
    void binomialNegativaReduzAPoisson() {
        for (long k : new long[] {0, 3, 10, 25}) {
            assertThat(ModeloDemanda.binomialNegativaCdf(10.0, 10.0, k))
                    .isCloseTo(ModeloDemanda.poissonCdf(10.0, k), within(1e-12));
            assertThat(ModeloDemanda.binomialNegativaCdf(10.0, 4.0, k))
                    .isCloseTo(ModeloDemanda.poissonCdf(10.0, k), within(1e-12));
        }
    }

    @Test
    @DisplayName("Binomial Negativa com media 4 e variancia 8: r = 4, p = 0,5, P(0) = 0,0625 e P(<= 2) = 0,34375")
    void binomialNegativaValoresDeReferencia() {
        assertThat(ModeloDemanda.binomialNegativaCdf(4.0, 8.0, 0)).isCloseTo(0.0625, within(1e-12));
        assertThat(ModeloDemanda.binomialNegativaCdf(4.0, 8.0, 2)).isCloseTo(0.34375, within(1e-12));
    }

    @Test
    @DisplayName("Binomial Negativa com media alta nao sofre underflow")
    void binomialNegativaMediaAlta() {
        double cdf = ModeloDemanda.binomialNegativaCdf(818.0, 2500.0, 2000);
        assertThat(cdf).isGreaterThan(0.999).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("Com estoque acima da media, mais variancia significa mais risco de faltar")
    void sobredispersaoAumentaORisco() {
        double poisson = ModeloDemanda.probabilidadeExcedentePoisson(97.0, 119);
        double binomialNegativa = ModeloDemanda.probabilidadeExcedenteBinomialNegativa(97.0, 3.3 * 97.0, 119);
        assertThat(binomialNegativa).isGreaterThan(3 * poisson);
        assertThat(ModeloDemanda.probabilidadeExcedentePoisson(10.0, -1)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Variancia do horizonte e N vezes a variancia amostral da serie diaria")
    void varianciaDoHorizonte() {
        // serie {0, 2, 4}: media 2, s^2 = 4, N = 3 -> 12
        assertThat(ModeloDemanda.varianciaDoHorizonte(List.of(0L, 2L, 4L))).isCloseTo(12.0, within(1e-12));
        assertThat(ModeloDemanda.varianciaDoHorizonte(List.of(5L))).isNegative();
        assertThat(ModeloDemanda.varianciaDoHorizonte(List.of())).isNegative();
    }
}
