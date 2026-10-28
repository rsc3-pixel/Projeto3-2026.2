package com.rotavital.servico;

import com.rotavital.api.dto.ResultadoBenchmarkResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.paralelo.CalculadoraDispersaoValidade;
import com.rotavital.paralelo.GeradorMassaBolsasSintetica;
import com.rotavital.paralelo.ResumoValidade;
import com.rotavital.servico.excecao.OperacaoInvalidaException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BenchmarkParaleloServico {

    private static final int TAMANHO_MAXIMO_AMOSTRA = 2_000_000;

    private final CalculadoraDispersaoValidade calculadora = new CalculadoraDispersaoValidade();
    private final Map<String, List<Bolsa>> massasCacheadas = new ConcurrentHashMap<>();

    public ResultadoBenchmarkResponse medirDispersaoValidade(
            int tamanhoAmostra, int threads, long semente, LocalDate dataReferencia) {

        if (tamanhoAmostra <= 0) {
            throw new OperacaoInvalidaException("tamanhoAmostra deve ser maior que zero");
        }
        if (tamanhoAmostra > TAMANHO_MAXIMO_AMOSTRA) {
            throw new OperacaoInvalidaException(
                    "tamanhoAmostra nao pode passar de " + TAMANHO_MAXIMO_AMOSTRA);
        }
        if (threads < 1) {
            throw new OperacaoInvalidaException("threads deve ser 1 (sequencial) ou maior (paralelo)");
        }

        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> massa = massasCacheadas.computeIfAbsent(
                tamanhoAmostra + ":" + semente,
                chave -> GeradorMassaBolsasSintetica.gerar(tamanhoAmostra, semente, hoje));

        long inicio = System.nanoTime();
        ResumoValidade resultado = threads == 1
                ? calculadora.calcularSequencial(massa, hoje)
                : calculadora.calcularParalelo(massa, hoje, threads);
        double tempoMs = (System.nanoTime() - inicio) / 1_000_000.0;

        String modo = threads == 1 ? "SEQUENCIAL" : "PARALELO";
        return new ResultadoBenchmarkResponse(tamanhoAmostra, modo, threads, tempoMs, resultado);
    }
}
