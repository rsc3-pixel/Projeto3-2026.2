package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.Grafo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes automatizados dos criterios de aceite da integracao (PI3-59).
 *
 * <p>Um teste por criterio, seis no total, na mesma ordem do card:
 * o caminho de 23 minutos do escopo do grafo, FEFO vencendo proximidade,
 * bolsa vencida ignorada, empate estavel em N execucoes, sem caminho e sem
 * estoque.</p>
 */
class CriteriosAceiteAlocacaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 14);
    private static final int EXECUCOES_DO_EMPATE = 100;

    /**
     * Criterio 1: a rede de exemplo do escopo do grafo (PI3-13) devolve o
     * caminho minimo de 23 minutos calculado a mao no documento -
     * Hemocentro, Hospital A, Hospital B, Hospital Destino.
     */
    @Test
    void caminhoDoExemploDoEscopoCustaVinteETresMinutos() {
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malhaDoEscopo(), Map.of(
                "Hospital Destino", List.of(bolsaComValidade("B001", HOJE.plusDays(10)))));

        ResultadoAlocacao resultado = pedir(servico, "Hemocentro");

        assertTrue(resultado.sucesso());
        assertEquals(List.of("Hemocentro", "Hospital A", "Hospital B", "Hospital Destino"),
                resultado.rota().caminho());
        assertEquals(23.0, resultado.tempoMinutos());
    }

    /**
     * Criterio 2: entre uma bolsa nova na unidade vizinha e uma bolsa que
     * vence antes na unidade distante, ganha a que vence antes. FEFO decide a
     * bolsa; a rota apenas leva ate ela.
     */
    @Test
    void bolsaQueVencePrimeiroGanhaDaBolsaMaisProxima() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "Perto", 5.0);
        malha.inserirAresta("Perto", "Longe", 60.0);

        Bolsa novaPerto = bolsaComValidade("B001", HOJE.plusDays(30));
        Bolsa velhaLonge = bolsaComValidade("B002", HOJE.plusDays(2));

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, Map.of(
                "Perto", List.of(novaPerto),
                "Longe", List.of(velhaLonge)));

        ResultadoAlocacao resultado = pedir(servico, "A");

        assertSame(velhaLonge, resultado.bolsa());
        assertEquals("Longe", resultado.unidadeDaBolsa());
        assertEquals(65.0, resultado.tempoMinutos());
    }

    /**
     * Criterio 3: bolsa vencida e ignorada mesmo sendo a que vence primeiro -
     * ela seria o topo do FEFO, e por isso o filtro de validade precisa vir
     * antes da ordenacao.
     */
    @Test
    void bolsaVencidaEhIgnoradaMesmoVencendoPrimeiro() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);

        Bolsa vencida = bolsaComValidade("B001", HOJE.minusDays(1));
        Bolsa valida = bolsaComValidade("B002", HOJE.plusDays(10));

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, Map.of(
                "B", List.of(vencida, valida)));

        ResultadoAlocacao resultado = pedir(servico, "A");

        assertTrue(resultado.sucesso());
        assertSame(valida, resultado.bolsa());
    }

    /**
     * Criterio 4: empate de validade e resolvido sempre pela mesma bolsa (a
     * de menor codigo de rastreio), em N execucoes seguidas e mesmo variando
     * a ordem de montagem do estoque.
     */
    @Test
    void empateDeValidadeEhEstavelEmMuitasExecucoes() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);
        malha.inserirAresta("A", "C", 20.0);

        LocalDate mesmaValidade = HOJE.plusDays(7);

        for (int execucao = 0; execucao < EXECUCOES_DO_EMPATE; execucao++) {
            // Alterna a ordem das unidades no estoque: o desempate nao pode
            // depender de ordem de insercao, so do codigo de rastreio.
            Map<String, List<Bolsa>> estoque = new LinkedHashMap<>();
            if (execucao % 2 == 0) {
                estoque.put("B", List.of(bolsaComValidade("B002", mesmaValidade)));
                estoque.put("C", List.of(bolsaComValidade("B001", mesmaValidade)));
            } else {
                estoque.put("C", List.of(bolsaComValidade("B001", mesmaValidade)));
                estoque.put("B", List.of(bolsaComValidade("B002", mesmaValidade)));
            }

            ResultadoAlocacao resultado = pedir(new ServicoAlocacaoRota(malha, estoque), "A");

            assertEquals("B001", resultado.bolsa().getCodigoRastreio(),
                    "execucao " + execucao + " escolheu outra bolsa no empate");
            assertEquals("C", resultado.unidadeDaBolsa());
        }
    }

    /**
     * Criterio 5: bolsa alocavel sem caminho a partir da solicitante resulta
     * em falha SEM_CAMINHO, nunca em bolsa inalcancavel escolhida.
     */
    @Test
    void semCaminhoQuandoNenhumaBolsaEhAlcancavel() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);
        malha.inserirVertice("Isolada");

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, Map.of(
                "Isolada", List.of(bolsaComValidade("B001", HOJE.plusDays(10)))));

        ResultadoAlocacao resultado = pedir(servico, "A");

        assertFalse(resultado.sucesso());
        assertEquals(MotivoFalhaAlocacao.SEM_CAMINHO, resultado.motivo());
    }

    /**
     * Criterio 6: rede sem nenhuma bolsa da combinacao pedida resulta em
     * falha SEM_ESTOQUE, sem excecao.
     */
    @Test
    void semEstoqueQuandoNaoHaBolsaDaCombinacao() {
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malhaDoEscopo(), Map.of());

        ResultadoAlocacao resultado = assertDoesNotThrow(() -> pedir(servico, "Hemocentro"));

        assertFalse(resultado.sucesso());
        assertEquals(MotivoFalhaAlocacao.SEM_ESTOQUE, resultado.motivo());
    }

    /**
     * A rede de 5 unidades da secao "Esboco da rede" do escopo do grafo, com
     * os mesmos pesos em minutos.
     */
    private static Grafo<String> malhaDoEscopo() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("Hemocentro", "Hospital A", 10.0);
        malha.inserirAresta("Hemocentro", "Hospital B", 20.0);
        malha.inserirAresta("Hospital A", "Hospital B", 5.0);
        malha.inserirAresta("Hospital A", "Hospital C", 15.0);
        malha.inserirAresta("Hospital B", "Hospital Destino", 8.0);
        malha.inserirAresta("Hospital C", "Hospital Destino", 5.0);
        return malha;
    }

    private static ResultadoAlocacao pedir(ServicoAlocacaoRota servico, String solicitante) {
        return servico.alocar(solicitante, GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);
    }

    private static Bolsa bolsaComValidade(String codigo, LocalDate validade) {
        TipoHemocomponente tipo = TipoHemocomponente.CONCENTRADO_HEMACIAS;
        return new Bolsa(codigo, tipo, GrupoSanguineo.O_NEG, 450,
                validade.minusDays(tipo.getValidadeDias()), hemocentro());
    }

    private static Hemocentro hemocentro() {
        return new Hemocentro(
                "H1", "Hemocentro Teste", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100");
    }
}
