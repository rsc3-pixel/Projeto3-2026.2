package com.rotavital.paralelo;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculadoraDispersaoValidadeTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 21);
    private final CalculadoraDispersaoValidade calculadora = new CalculadoraDispersaoValidade();

    @Test
    void calculaMedidasCorretasParaMassaConhecida() {
        List<Bolsa> bolsas = List.of(
                bolsaComDiasAteVencer(10),
                bolsaComDiasAteVencer(20),
                bolsaComDiasAteVencer(30),
                bolsaComDiasAteVencer(40),
                bolsaComDiasAteVencer(50));

        ResumoValidade resultado = calculadora.calcularSequencial(bolsas, HOJE);

        assertEquals(5, resultado.n());
        assertEquals(30.0, resultado.media(), 1e-9);
        assertEquals(30.0, resultado.mediana(), 1e-9);
        assertEquals(Math.sqrt(250.0), resultado.desvioPadraoAmostral(), 1e-9);
        assertEquals(10, resultado.min());
        assertEquals(50, resultado.max());
    }

    @Test
    void medianaParEMediaDosDoisCentrais() {
        List<Bolsa> bolsas = List.of(
                bolsaComDiasAteVencer(40),
                bolsaComDiasAteVencer(10),
                bolsaComDiasAteVencer(30),
                bolsaComDiasAteVencer(20));

        ResumoValidade resultado = calculadora.calcularSequencial(bolsas, HOJE);

        assertEquals(25.0, resultado.mediana(), 1e-9);
    }

    @Test
    void ignoraBolsasVencidasNaEstatistica() {
        List<Bolsa> bolsas = new ArrayList<>(List.of(
                bolsaComDiasAteVencer(10),
                bolsaComDiasAteVencer(20),
                bolsaVencidaHa(35)));

        ResumoValidade resultado = calculadora.calcularSequencial(bolsas, HOJE);

        assertEquals(2, resultado.n());
        assertEquals(15.0, resultado.media(), 1e-9);
    }

    @Test
    void listaVaziaDevolveResumoVazio() {
        ResumoValidade resultado = calculadora.calcularSequencial(List.of(), HOJE);
        assertEquals(ResumoValidade.vazio(), resultado);
    }

    @Test
    void elementoUnicoTemDesvioZero() {
        List<Bolsa> bolsas = List.of(bolsaComDiasAteVencer(7));
        ResumoValidade resultado = calculadora.calcularSequencial(bolsas, HOJE);

        assertEquals(1, resultado.n());
        assertEquals(0.0, resultado.desvioPadraoAmostral());
        assertEquals(7, resultado.min());
        assertEquals(7, resultado.max());
    }

    @Test
    void calcularParaleloComUmaThreadNaoEPermitido() {
        List<Bolsa> bolsas = List.of(bolsaComDiasAteVencer(10));
        assertThrows(IllegalArgumentException.class,
                () -> calculadora.calcularParalelo(bolsas, HOJE, 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 7, 31, 1000, 50_000})
    void sequencialEParaleloDevolvemExatamenteAMesmaResposta(int tamanho) {
        List<Bolsa> bolsas = GeradorMassaBolsasSintetica.gerar(tamanho, 42L, HOJE);

        ResumoValidade sequencial = calculadora.calcularSequencial(bolsas, HOJE);

        for (int threads : new int[]{2, 4, 8}) {
            ResumoValidade paralelo = calculadora.calcularParalelo(bolsas, HOJE, threads);

            assertEquals(sequencial.n(), paralelo.n(), () -> "n divergiu com " + threads + " threads");
            assertEquals(sequencial.media(), paralelo.media(), () -> "media divergiu com " + threads + " threads");
            assertEquals(sequencial.mediana(), paralelo.mediana(), () -> "mediana divergiu com " + threads + " threads");
            assertEquals(sequencial.desvioPadraoAmostral(), paralelo.desvioPadraoAmostral(),
                    () -> "desvio-padrao divergiu com " + threads + " threads");
            assertEquals(sequencial.min(), paralelo.min(), () -> "min divergiu com " + threads + " threads");
            assertEquals(sequencial.max(), paralelo.max(), () -> "max divergiu com " + threads + " threads");
            assertEquals(sequencial, paralelo, () -> "ResumoValidade completo divergiu com " + threads + " threads");
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 7L, 42L, 12345L})
    void resultadoEEstavelEntreSementesDiferentes(long semente) {
        List<Bolsa> bolsas = GeradorMassaBolsasSintetica.gerar(10_000, semente, HOJE);

        ResumoValidade sequencial = calculadora.calcularSequencial(bolsas, HOJE);
        ResumoValidade paralelo8 = calculadora.calcularParalelo(bolsas, HOJE, 8);

        assertEquals(sequencial, paralelo8);
    }

    private static Bolsa bolsaComDiasAteVencer(int dias) {
        TipoHemocomponente tipo = TipoHemocomponente.PLASMA_FRESCO_CONGELADO;
        LocalDate dataColeta = LocalDate.of(2026, 9, 21).minusDays(tipo.getValidadeDias() - dias);
        return new Bolsa("TEST-" + dias + "-" + System.nanoTime(), tipo,
                GrupoSanguineo.O_NEG, 300, dataColeta, unidadeDeTeste());
    }

    private static Bolsa bolsaVencidaHa(int diasVencida) {
        TipoHemocomponente tipo = TipoHemocomponente.PLASMA_FRESCO_CONGELADO;
        LocalDate dataColeta = LocalDate.of(2026, 9, 21).minusDays(tipo.getValidadeDias() + diasVencida);
        return new Bolsa("TEST-VENCIDA-" + System.nanoTime(), tipo,
                GrupoSanguineo.O_NEG, 300, dataColeta, unidadeDeTeste());
    }

    private static Hemocentro unidadeDeTeste() {
        return new Hemocentro("HC-TESTE", "Hemocentro de teste", "00000000000",
                new Endereco("Rua Teste", "0", "Bairro Teste", "Cidade Teste", "PE", "00000-000", 0.0, 0.0),
                "00000000000000");
    }
}
