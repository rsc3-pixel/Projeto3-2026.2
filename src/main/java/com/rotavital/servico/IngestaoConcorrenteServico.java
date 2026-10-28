package com.rotavital.servico;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.dominio.Bolsa;

/**
 * Entrada concorrente de bolsas no estoque (PI3-30).
 *
 * <p>Uma coleta traz dezenas de bolsas de uma vez, e cadastrar uma a uma
 * desperdica o tempo em que a aplicacao fica esperando o banco. Processar em
 * paralelo aproveita essa espera.
 *
 * <p>O ganho aqui e de entrada e saida, nao de processamento: a maior parte do
 * tempo de cada cadastro e esperar o banco responder, e nao calcular. Por isso
 * o paralelismo vale a pena mesmo com poucos nucleos, diferente do calculo de
 * dispersao de validade da Unidade 1, onde o limite era a CPU.
 *
 * <p>Cada bolsa entra na propria transacao: uma recusada, por data de coleta
 * futura ou codigo repetido, nao desfaz as outras. O resultado diz quantas
 * entraram e o que aconteceu com as que ficaram de fora.
 */
@Service
public class IngestaoConcorrenteServico {

    private static final Logger log = LoggerFactory.getLogger(IngestaoConcorrenteServico.class);

    /**
     * Quantas bolsas sao cadastradas ao mesmo tempo.
     *
     * <p>Quatro e um meio-termo deliberado: o suficiente para aproveitar a
     * espera do banco, e pouco o bastante para nao esgotar o pool de conexoes,
     * que tem dez por padrao e e compartilhado com o resto da aplicacao.
     */
    private static final int PARALELISMO = 4;

    /** Tempo maximo esperando o lote inteiro terminar. */
    private static final int TIMEOUT_SEGUNDOS = 60;

    private final BolsaServico bolsaServico;

    public IngestaoConcorrenteServico(BolsaServico bolsaServico) {
        this.bolsaServico = bolsaServico;
    }

    /**
     * Cadastra um lote de bolsas em paralelo.
     *
     * @return o que entrou e o que foi recusado, com o motivo de cada recusa
     */
    public ResultadoIngestao ingerir(List<BolsaRequest> lote) {
        if (lote == null || lote.isEmpty()) {
            return new ResultadoIngestao(List.of(), List.of(), 0);
        }

        long inicio = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(PARALELISMO, lote.size()));

        List<String> aceitas = new ArrayList<>();
        List<String> recusadas = new ArrayList<>();
        AtomicInteger indice = new AtomicInteger();

        try {
            List<Callable<String>> tarefas = lote.stream()
                    .map(dados -> (Callable<String>) () -> {
                        int posicao = indice.getAndIncrement();
                        try {
                            Bolsa criada = bolsaServico.criar(dados);
                            return "OK:" + criada.getCodigoRastreio();
                        } catch (RuntimeException e) {
                            // A recusa de uma bolsa nao derruba o lote: o
                            // motivo volta no resultado para quem enviou
                            // corrigir so aquela.
                            return "ERRO:" + posicao + ":" + e.getMessage();
                        }
                    })
                    .toList();

            List<Future<String>> resultados =
                    pool.invokeAll(tarefas, TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);

            for (Future<String> f : resultados) {
                try {
                    String r = f.get();
                    if (r.startsWith("OK:")) {
                        aceitas.add(r.substring(3));
                    } else {
                        recusadas.add(r.substring(5));
                    }
                } catch (Exception e) {
                    recusadas.add("nao concluiu: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            // Restaura a marca de interrupcao: engolir isso faria a thread
            // perder o sinal de parada que alguem enviou.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ingestao interrompida", e);
        } finally {
            pool.shutdownNow();
        }

        long ms = (System.nanoTime() - inicio) / 1_000_000;
        log.info("ingestao concorrente: {} aceitas, {} recusadas, {} ms, {} threads",
                aceitas.size(), recusadas.size(), ms, PARALELISMO);

        return new ResultadoIngestao(aceitas, recusadas, ms);
    }

    /** O que aconteceu com um lote de ingestao. */
    public record ResultadoIngestao(
            List<String> aceitas,
            List<String> recusadas,
            long duracaoMs) {

        public int total() {
            return aceitas.size() + recusadas.size();
        }
    }
}
