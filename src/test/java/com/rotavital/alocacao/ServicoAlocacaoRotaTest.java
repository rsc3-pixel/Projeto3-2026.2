package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.Grafo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do servico de integracao da PI3-58: uma chamada devolve bolsa mais
 * rota, e bolsa em unidade inalcancavel nunca e escolhida.
 */
class ServicoAlocacaoRotaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 14);

    @Test
    void umaChamadaDevolveBolsaCaminhoETempo() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);
        malha.inserirAresta("B", "C", 5.0);

        Bolsa bolsa = bolsaComValidade("B001", HOJE.plusDays(10));
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(
                malha, Map.of("C", List.of(bolsa)));

        ResultadoAlocacao resultado = servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);

        assertTrue(resultado.sucesso());
        assertSame(bolsa, resultado.bolsa());
        assertEquals("C", resultado.unidadeDaBolsa());
        assertEquals(List.of("A", "B", "C"), resultado.rota().caminho());
        assertEquals(15.0, resultado.tempoMinutos());
    }

    @Test
    void bolsaEmUnidadeInalcancavelNuncaEscolhida() {
        // "Isolada" nao tem nenhuma aresta: a bolsa de la vence primeiro e
        // ganharia o FEFO, mas sem caminho ela nao e uma opcao.
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);
        malha.inserirVertice("Isolada");

        Bolsa inalcancavel = bolsaComValidade("B001", HOJE.plusDays(1));
        Bolsa alcancavel = bolsaComValidade("B002", HOJE.plusDays(30));

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, Map.of(
                "Isolada", List.of(inalcancavel),
                "B", List.of(alcancavel)));

        ResultadoAlocacao resultado = servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);

        assertTrue(resultado.sucesso());
        assertSame(alcancavel, resultado.bolsa());
        assertEquals("B", resultado.unidadeDaBolsa());
    }

    @Test
    void bolsaNaPropriaUnidadeSolicitanteTemTempoZero() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);

        Bolsa bolsa = bolsaComValidade("B001", HOJE.plusDays(10));
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(
                malha, Map.of("A", List.of(bolsa)));

        ResultadoAlocacao resultado = servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);

        assertTrue(resultado.sucesso());
        assertEquals(List.of("A"), resultado.rota().caminho());
        assertEquals(0.0, resultado.tempoMinutos());
    }

    @Test
    void grupoETipoSaoCasadosDeFormaIdentica() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirVertice("A");

        // O+ disponivel nao serve para quem pediu O-: o casamento e identico,
        // sem tabela de compatibilidade.
        Bolsa outroGrupo = new Bolsa("B001", TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_POS, 450, HOJE.minusDays(1), hemocentro());
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(
                malha, Map.of("A", List.of(outroGrupo)));

        ResultadoAlocacao resultado = servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);

        assertFalse(resultado.sucesso());
        assertEquals(MotivoFalhaAlocacao.SEM_ESTOQUE, resultado.motivo());
    }

    @Test
    void estoqueEhCopiadoNaConstrucao() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirVertice("A");

        List<Bolsa> lista = new ArrayList<>();
        lista.add(bolsaComValidade("B001", HOJE.plusDays(10)));
        Map<String, List<Bolsa>> estoque = new LinkedHashMap<>();
        estoque.put("A", lista);

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, estoque);

        // Esvaziar as estruturas do chamador nao pode afetar o servico.
        lista.clear();
        estoque.clear();

        assertTrue(servico.alocar("A", GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE).sucesso());
    }

    @Test
    void buracoNaListaDeBolsasEhDescartadoSemErro() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirVertice("A");

        List<Bolsa> comBuraco = new ArrayList<>();
        comBuraco.add(null);
        comBuraco.add(bolsaComValidade("B001", HOJE.plusDays(10)));

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(
                malha, Map.of("A", comBuraco));

        assertTrue(servico.alocar("A", GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE).sucesso());
    }

    @Test
    void unidadeDeEstoqueQueNaoExisteNaMalhaEhTratadaComoInalcancavel() {
        // "Deposito" tem bolsa no estoque mas nem vertice na malha: e o caso
        // de dado desalinhado entre estoque e rede, e cai no mesmo filtro de
        // alcance - nunca em bolsa escolhida sem rota possivel.
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);

        Bolsa foraDaMalha = bolsaComValidade("B001", HOJE.plusDays(1));
        Bolsa naMalha = bolsaComValidade("B002", HOJE.plusDays(30));

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, Map.of(
                "Deposito", List.of(foraDaMalha),
                "B", List.of(naMalha)));

        ResultadoAlocacao resultado = servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);
        assertSame(naMalha, resultado.bolsa());

        ServicoAlocacaoRota soForaDaMalha = new ServicoAlocacaoRota(malha, Map.of(
                "Deposito", List.of(foraDaMalha)));
        assertEquals(MotivoFalhaAlocacao.SEM_CAMINHO, soForaDaMalha.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE).motivo());
    }

    @Test
    void codigoDeRastreioDuplicadoResolveNaPrimeiraOcorrenciaDoEstoque() {
        // Mesmo codigo em duas unidades e erro de dado; a politica documentada
        // e deterministica: vale a primeira ocorrencia na ordem do estoque.
        Grafo<String> malha = new Grafo<>();
        malha.inserirAresta("A", "B", 10.0);
        malha.inserirAresta("A", "C", 20.0);

        LocalDate mesmaValidade = HOJE.plusDays(7);
        Map<String, List<Bolsa>> estoque = new LinkedHashMap<>();
        estoque.put("B", List.of(bolsaComValidade("B001", mesmaValidade)));
        estoque.put("C", List.of(bolsaComValidade("B001", mesmaValidade)));

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, estoque);

        for (int chamada = 0; chamada < 10; chamada++) {
            ResultadoAlocacao resultado = servico.alocar(
                    "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);
            assertEquals("B", resultado.unidadeDaBolsa(),
                    "chamada " + chamada + " nao respeitou a primeira ocorrencia");
        }
    }

    @Test
    void construtorRejeitaNulos() {
        Grafo<String> malha = new Grafo<>();

        assertThrows(IllegalArgumentException.class,
                () -> new ServicoAlocacaoRota(null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ServicoAlocacaoRota(malha, null));

        Map<String, List<Bolsa>> listaNula = new LinkedHashMap<>();
        listaNula.put("A", null);
        assertThrows(IllegalArgumentException.class,
                () -> new ServicoAlocacaoRota(malha, listaNula));
    }

    @Test
    void alocarRejeitaArgumentoNulo() {
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(new Grafo<>(), Map.of());

        assertThrows(IllegalArgumentException.class, () -> servico.alocar(
                null, GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE));
        assertThrows(IllegalArgumentException.class, () -> servico.alocar(
                "A", null, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE));
        assertThrows(IllegalArgumentException.class, () -> servico.alocar(
                "A", GrupoSanguineo.O_NEG, null, HOJE));
        assertThrows(IllegalArgumentException.class, () -> servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, null));
    }

    @Test
    void acessoresSaoGuardadosPeloLadoDoResultado() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirVertice("A");

        Bolsa bolsa = bolsaComValidade("B001", HOJE.plusDays(10));
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(
                malha, Map.of("A", List.of(bolsa)));

        ResultadoAlocacao sucesso = servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);
        assertThrows(IllegalStateException.class, sucesso::motivo);

        ResultadoAlocacao falha = servico.alocar(
                "A", GrupoSanguineo.AB_NEG, TipoHemocomponente.CRIOPRECIPITADO, HOJE);
        assertThrows(IllegalStateException.class, falha::bolsa);
        assertThrows(IllegalStateException.class, falha::rota);
        assertThrows(IllegalStateException.class, falha::tempoMinutos);
        assertThrows(IllegalStateException.class, falha::unidadeDaBolsa);
    }

    @Test
    void servicoNaoMudaOStatusDaBolsaEscolhida() {
        Grafo<String> malha = new Grafo<>();
        malha.inserirVertice("A");

        Bolsa bolsa = bolsaComValidade("B001", HOJE.plusDays(10));
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(
                malha, Map.of("A", List.of(bolsa)));

        ResultadoAlocacao primeira = servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);
        ResultadoAlocacao segunda = servico.alocar(
                "A", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);

        // E uma consulta: mesmo estado, mesmo resultado.
        assertEquals(primeira, segunda);
    }

    /**
     * Bolsa O- de hemacias com a validade desejada. A validade e derivada de
     * coleta + 42 dias, entao a coleta e recuada na mesma medida.
     */
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
