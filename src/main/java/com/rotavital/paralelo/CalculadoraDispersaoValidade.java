package com.rotavital.paralelo;

import com.rotavital.dominio.Bolsa;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class CalculadoraDispersaoValidade {

    public ResumoValidade calcularSequencial(List<Bolsa> bolsas, LocalDate hoje) {
        if (bolsas.isEmpty()) {
            return ResumoValidade.vazio();
        }
        FatiaParcial unica = processarFatia(bolsas, hoje);
        return combinar(List.of(unica));
    }

    public ResumoValidade calcularParalelo(List<Bolsa> bolsas, LocalDate hoje, int numThreads) {
        if (numThreads < 2) {
            throw new IllegalArgumentException(
                    "calcularParalelo exige 2 ou mais threads; para 1 thread use calcularSequencial");
        }
        if (bolsas.isEmpty()) {
            return ResumoValidade.vazio();
        }

        int threads = Math.min(numThreads, bolsas.size());
        List<List<Bolsa>> fatias = particionar(bolsas, threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<FatiaParcial>> futuros = new ArrayList<>(threads);
            for (List<Bolsa> fatia : fatias) {
                futuros.add(pool.submit(() -> processarFatia(fatia, hoje)));
            }
            return combinar(colher(futuros));
        } finally {
            pool.shutdown();
        }
    }

    private FatiaParcial processarFatia(List<Bolsa> fatia, LocalDate hoje) {
        List<Long> dias = new ArrayList<>(fatia.size());
        long soma = 0;
        long somaQuadrados = 0;

        for (Bolsa bolsa : fatia) {
            if (!bolsa.estaVencida(hoje)) {
                long d = ChronoUnit.DAYS.between(hoje, bolsa.getDataValidade());
                dias.add(d);
                soma += d;
                somaQuadrados += d * d;
            }
        }
        Collections.sort(dias);
        return new FatiaParcial(dias, soma, somaQuadrados);
    }

    private ResumoValidade combinar(List<FatiaParcial> fatias) {
        long n = 0;
        long soma = 0;
        long somaQuadrados = 0;
        for (FatiaParcial fatia : fatias) {
            n += fatia.dias().size();
            soma += fatia.soma();
            somaQuadrados += fatia.somaQuadrados();
        }
        if (n == 0) {
            return ResumoValidade.vazio();
        }

        double media = soma / (double) n;
        double desvio = 0.0;
        if (n >= 2) {
            double variancia = (somaQuadrados - (soma * (double) soma) / n) / (n - 1);
            desvio = Math.sqrt(Math.max(0.0, variancia));
        }

        long min = fatias.stream()
                .filter(f -> !f.dias().isEmpty())
                .mapToLong(f -> f.dias().get(0))
                .min().orElse(0);
        long max = fatias.stream()
                .filter(f -> !f.dias().isEmpty())
                .mapToLong(f -> f.dias().get(f.dias().size() - 1))
                .max().orElse(0);
        double mediana = medianaPorIntercalacao(fatias, n);

        return new ResumoValidade(n, media, mediana, desvio, min, max);
    }

    private double medianaPorIntercalacao(List<FatiaParcial> fatias, long n) {
        PriorityQueue<Cursor> fila = new PriorityQueue<>();
        for (FatiaParcial f : fatias) {
            if (!f.dias().isEmpty()) {
                fila.add(new Cursor(f.dias()));
            }
        }

        boolean par = n % 2 == 0;
        long posicaoImpar = (n + 1) / 2;
        long posicaoParEsquerda = n / 2;
        long posicaoAtual = 0;
        long valorEsquerda = 0;

        while (!fila.isEmpty()) {
            Cursor menor = fila.poll();
            posicaoAtual++;
            long valor = menor.valorAtual();

            if (!par && posicaoAtual == posicaoImpar) {
                return valor;
            }
            if (par && posicaoAtual == posicaoParEsquerda) {
                valorEsquerda = valor;
            }
            if (par && posicaoAtual == posicaoParEsquerda + 1) {
                return (valorEsquerda + valor) / 2.0;
            }

            menor.avancar();
            if (menor.temProximo()) {
                fila.add(menor);
            }
        }

        throw new IllegalStateException("intercalacao nao encontrou a mediana - fatias inconsistentes");
    }

    private List<List<Bolsa>> particionar(List<Bolsa> bolsas, int partes) {
        int total = bolsas.size();
        int tamanhoBase = total / partes;
        int resto = total % partes;

        List<List<Bolsa>> fatias = new ArrayList<>(partes);
        int inicio = 0;
        for (int i = 0; i < partes; i++) {
            int tamanho = tamanhoBase + (i < resto ? 1 : 0);
            int fim = inicio + tamanho;
            fatias.add(bolsas.subList(inicio, fim));
            inicio = fim;
        }
        return fatias;
    }

    private <T> List<T> colher(List<Future<T>> futuros) {
        List<T> resultado = new ArrayList<>(futuros.size());
        for (Future<T> futuro : futuros) {
            resultado.add(obter(futuro));
        }
        return resultado;
    }

    private <T> T obter(Future<T> futuro) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("thread de calculo interrompida", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("falha ao calcular fatia em paralelo", e.getCause());
        }
    }

    private record FatiaParcial(List<Long> dias, long soma, long somaQuadrados) {
    }

    private static final class Cursor implements Comparable<Cursor> {
        private final List<Long> lista;
        private int indice;

        private Cursor(List<Long> lista) {
            this.lista = lista;
            this.indice = 0;
        }

        long valorAtual() {
            return lista.get(indice);
        }

        void avancar() {
            indice++;
        }

        boolean temProximo() {
            return indice < lista.size();
        }

        @Override
        public int compareTo(Cursor outro) {
            return Long.compare(this.valorAtual(), outro.valorAtual());
        }
    }
}
