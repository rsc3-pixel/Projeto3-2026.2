package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.Grafo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do tratamento de "sem estoque" da PI3-57: cada motivo de falha e
 * identificavel na resposta, e nenhum deles vira excecao.
 */
class MotivoFalhaAlocacaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 14);

    @Test
    void semEstoqueQuandoNaoExisteBolsaDaCombinacao() {
        // Ha estoque de outro grupo, mas da combinacao pedida nao ha nenhuma
        // bolsa - nem vencida. E falta de estoque, nao de validade.
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malhaDeDoisVertices(), Map.of(
                "B", List.of(bolsa("B001", GrupoSanguineo.A_POS, HOJE.plusDays(10),
                        StatusBolsa.DISPONIVEL))));

        ResultadoAlocacao resultado = pedirONegativo(servico);

        assertFalse(resultado.sucesso());
        assertEquals(MotivoFalhaAlocacao.SEM_ESTOQUE, resultado.motivo());
    }

    @Test
    void todasVencidasQuandoExistemBolsasMasNenhumaAlocavel() {
        // A combinacao existe em duas unidades, mas uma bolsa esta vencida e a
        // outra reservada: nenhuma esta alocavel na data de referencia.
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malhaDeDoisVertices(), Map.of(
                "A", List.of(bolsa("B001", GrupoSanguineo.O_NEG, HOJE.minusDays(1),
                        StatusBolsa.DISPONIVEL)),
                "B", List.of(bolsa("B002", GrupoSanguineo.O_NEG, HOJE.plusDays(10),
                        StatusBolsa.RESERVADA))));

        ResultadoAlocacao resultado = pedirONegativo(servico);

        assertFalse(resultado.sucesso());
        assertEquals(MotivoFalhaAlocacao.TODAS_VENCIDAS, resultado.motivo());
    }

    @Test
    void semCaminhoQuandoAlocaveisExistemApenasEmUnidadesInalcancaveis() {
        // A bolsa esta perfeita, mas na unidade isolada: alcance e a ultima
        // barreira, avaliada depois de estoque e validade.
        Grafo<String> malha = malhaDeDoisVertices();
        malha.inserirVertice("Isolada");

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, Map.of(
                "Isolada", List.of(bolsa("B001", GrupoSanguineo.O_NEG, HOJE.plusDays(10),
                        StatusBolsa.DISPONIVEL))));

        ResultadoAlocacao resultado = pedirONegativo(servico);

        assertFalse(resultado.sucesso());
        assertEquals(MotivoFalhaAlocacao.SEM_CAMINHO, resultado.motivo());
    }

    @Test
    void solicitanteForaDaMalhaCaiEmSemCaminho() {
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malhaDeDoisVertices(), Map.of(
                "B", List.of(bolsa("B001", GrupoSanguineo.O_NEG, HOJE.plusDays(10),
                        StatusBolsa.DISPONIVEL))));

        ResultadoAlocacao resultado = servico.alocar("NaoExiste",
                GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);

        assertFalse(resultado.sucesso());
        assertEquals(MotivoFalhaAlocacao.SEM_CAMINHO, resultado.motivo());
    }

    @Test
    void validadeVemAntesDoAlcanceNaOrdemDosMotivos() {
        // Vencida em unidade alcancavel e valida em unidade isolada: os
        // motivos seguem a ordem estoque -> validade -> alcance, e como ainda
        // existe bolsa alocavel (a isolada), o que barra e o caminho.
        Grafo<String> malha = malhaDeDoisVertices();
        malha.inserirVertice("Isolada");

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, Map.of(
                "B", List.of(bolsa("B001", GrupoSanguineo.O_NEG, HOJE.minusDays(1),
                        StatusBolsa.DISPONIVEL)),
                "Isolada", List.of(bolsa("B002", GrupoSanguineo.O_NEG, HOJE.plusDays(10),
                        StatusBolsa.DISPONIVEL))));

        ResultadoAlocacao resultado = pedirONegativo(servico);

        assertEquals(MotivoFalhaAlocacao.SEM_CAMINHO, resultado.motivo());
    }

    @Test
    void nenhumMotivoDeFalhaLancaExcecao() {
        Grafo<String> malha = malhaDeDoisVertices();
        malha.inserirVertice("Isolada");

        ServicoAlocacaoRota semEstoque = new ServicoAlocacaoRota(malha, Map.of());
        ServicoAlocacaoRota todasVencidas = new ServicoAlocacaoRota(malha, Map.of(
                "B", List.of(bolsa("B001", GrupoSanguineo.O_NEG, HOJE.minusDays(1),
                        StatusBolsa.DISPONIVEL))));
        ServicoAlocacaoRota semCaminho = new ServicoAlocacaoRota(malha, Map.of(
                "Isolada", List.of(bolsa("B002", GrupoSanguineo.O_NEG, HOJE.plusDays(10),
                        StatusBolsa.DISPONIVEL))));

        assertDoesNotThrow(() -> pedirONegativo(semEstoque));
        assertDoesNotThrow(() -> pedirONegativo(todasVencidas));
        assertDoesNotThrow(() -> pedirONegativo(semCaminho));
    }

    private static ResultadoAlocacao pedirONegativo(ServicoAlocacaoRota servico) {
        return servico.alocar("A", GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);
    }

    private static Grafo<String> malhaDeDoisVertices() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);
        malha.inserirAresta("B", "A", 10.0);
        return malha;
    }

    private static Bolsa bolsa(String codigo, GrupoSanguineo grupo,
                               LocalDate validade, StatusBolsa status) {
        TipoHemocomponente tipo = TipoHemocomponente.CONCENTRADO_HEMACIAS;
        Bolsa criada = new Bolsa(codigo, tipo, grupo, 450,
                validade.minusDays(tipo.getValidadeDias()), hemocentro());
        criada.setStatus(status);
        return criada;
    }

    private static Hemocentro hemocentro() {
        return new Hemocentro(
                "H1", "Hemocentro Teste", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100");
    }
}
