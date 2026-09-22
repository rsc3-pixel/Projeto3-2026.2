package com.rotavital.paralelo;

import com.rotavital.dominio.Bolsa;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedicaoParalelismoTest {

    private static final LocalDate HOJE = LocalDate.now();
    private static final long SEMENTE = 42L;
    private static final int EXECUCOES_AQUECIMENTO = 5;
    private static final int EXECUCOES_MEDIDAS = 15;

    @Test
    void medeSequencialEParaleloParaCemMilEUmMilhaoDeBolsas() {
        CalculadoraDispersaoValidade calc = new CalculadoraDispersaoValidade();

        System.out.println();
        System.out.println("[PI3] tamanhoAmostra,modo,threads,tempoMedioMs,n");

        for (int tamanho : new int[]{100_000, 1_000_000}) {
            List<Bolsa> massa = GeradorMassaBolsasSintetica.gerar(tamanho, SEMENTE, HOJE);

            ResumoValidade sequencial = medir(tamanho, "SEQUENCIAL", 1,
                    () -> calc.calcularSequencial(massa, HOJE));

            for (int threads : new int[]{2, 4, 8}) {
                ResumoValidade paralelo = medir(tamanho, "PARALELO", threads,
                        () -> calc.calcularParalelo(massa, HOJE, threads));

                assertEquals(sequencial, paralelo,
                        "sequencial e paralelo(" + threads + ") divergiram para tamanho=" + tamanho);
            }
        }
    }

    private ResumoValidade medir(int tamanho, String modo, int threads,
                                  java.util.function.Supplier<ResumoValidade> operacao) {
        for (int i = 0; i < EXECUCOES_AQUECIMENTO; i++) {
            operacao.get();
        }

        long somaNanos = 0;
        ResumoValidade ultimo = null;
        for (int i = 0; i < EXECUCOES_MEDIDAS; i++) {
            long inicio = System.nanoTime();
            ultimo = operacao.get();
            somaNanos += System.nanoTime() - inicio;
        }

        double mediaMs = somaNanos / (double) EXECUCOES_MEDIDAS / 1_000_000.0;
        System.out.printf(Locale.ROOT, "[PI3] %d,%s,%d,%.3f,%d%n",
                tamanho, modo, threads, mediaMs, ultimo.n());

        assertTrue(mediaMs >= 0.0);
        return ultimo;
    }
}
