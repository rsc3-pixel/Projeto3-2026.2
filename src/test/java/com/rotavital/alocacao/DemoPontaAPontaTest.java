package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.Grafo;
import com.rotavital.rede.CarregadorMalha;
import com.rotavital.rede.UnidadeRede;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstracao ponta a ponta da PI3-61: a malha real das 12 unidades, um
 * estoque de exemplo e o servico de integracao respondendo o caso de sucesso
 * e os tres motivos de falha.
 *
 * <p>Roda com um comando:</p>
 * <pre>./mvnw test -Dtest=DemoPontaAPontaTest</pre>
 *
 * <p>E um teste de verdade, nao so um roteiro: cada cena tem assercao, entao
 * a demonstracao quebra junto com o comportamento que ela demonstra. A saida
 * narrada vai para o log do teste.</p>
 */
class DemoPontaAPontaTest {

    private static final LocalDate HOJE = LocalDate.now();

    /** Solicitante da demo: Hospital das Clinicas UFPE. */
    private static final String SOLICITANTE = "HC06";

    /** Unidade fora da malha, sem nenhuma aresta, para a cena de SEM_CAMINHO. */
    private static final String DEPOSITO_ISOLADO = "DEPOSITO";

    @Test
    void demonstraSucessoEOsTresMotivosDeFalha() {
        Grafo<String> malha = CarregadorMalha.carregar();
        malha.inserirVertice(DEPOSITO_ISOLADO);
        Map<String, UnidadeRede> unidades = CarregadorMalha.indicePorId();

        Bolsa oNegNova = bolsa("BOLDEMO01", GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE.plusDays(20));
        Bolsa oNegVenceAntes = bolsa("BOLDEMO02", GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE.plusDays(5));
        Bolsa oNegVencida = bolsa("BOLDEMO03", GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE.minusDays(2));
        Bolsa plaquetasVencida = bolsa("BOLDEMO04", GrupoSanguineo.A_POS,
                TipoHemocomponente.CONCENTRADO_PLAQUETAS, HOJE.minusDays(1));
        Bolsa plasmaIsolado = bolsa("BOLDEMO05", GrupoSanguineo.B_NEG,
                TipoHemocomponente.PLASMA_FRESCO_CONGELADO, HOJE.plusDays(90));

        Map<String, List<Bolsa>> estoque = new LinkedHashMap<>();
        estoque.put("HC01", List.of(oNegNova));
        estoque.put("HC02", List.of(oNegVencida));
        estoque.put("HC03", List.of(plaquetasVencida));
        estoque.put("HC08", List.of(oNegVenceAntes));
        estoque.put(DEPOSITO_ISOLADO, List.of(plasmaIsolado));

        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, estoque);
        StringBuilder narrativa = new StringBuilder("\n[PI3-61] Demo ponta a ponta\n");
        narrativa.append(String.format(Locale.ROOT,
                "Solicitante: %s (%s)%n%n", SOLICITANTE, nome(unidades, SOLICITANTE)));

        // Cena 1 - sucesso: FEFO escolhe a O- que vence antes (HC08, Olinda),
        // ignorando a vencida de HC02 e preterindo a mais nova de HC01.
        ResultadoAlocacao sucesso = servico.alocar(SOLICITANTE, GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);
        assertTrue(sucesso.sucesso());
        assertSame(oNegVenceAntes, sucesso.bolsa());
        assertEquals("HC08", sucesso.unidadeDaBolsa());
        narrativa.append(String.format(Locale.ROOT,
                "1) O- hemacias  -> bolsa %s (validade %s) em %s (%s)%n"
                        + "   caminho: %s | tempo total: %.1f min%n",
                sucesso.bolsa().getCodigoRastreio(), sucesso.bolsa().getDataValidade(),
                sucesso.unidadeDaBolsa(), nome(unidades, sucesso.unidadeDaBolsa()),
                caminhoLegivel(sucesso, unidades), sucesso.tempoMinutos()));

        // Cena 2 - SEM_ESTOQUE: nenhuma bolsa AB- de crioprecipitado na rede.
        ResultadoAlocacao semEstoque = servico.alocar(SOLICITANTE, GrupoSanguineo.AB_NEG,
                TipoHemocomponente.CRIOPRECIPITADO, HOJE);
        assertEquals(MotivoFalhaAlocacao.SEM_ESTOQUE, semEstoque.motivo());
        narrativa.append(String.format(Locale.ROOT,
                "2) AB- crio     -> %s (nenhuma bolsa da combinacao na rede)%n",
                semEstoque.motivo()));

        // Cena 3 - TODAS_VENCIDAS: a unica A+ de plaquetas ja venceu.
        ResultadoAlocacao todasVencidas = servico.alocar(SOLICITANTE, GrupoSanguineo.A_POS,
                TipoHemocomponente.CONCENTRADO_PLAQUETAS, HOJE);
        assertEquals(MotivoFalhaAlocacao.TODAS_VENCIDAS, todasVencidas.motivo());
        narrativa.append(String.format(Locale.ROOT,
                "3) A+ plaquetas -> %s (existe bolsa, mas nenhuma alocavel)%n",
                todasVencidas.motivo()));

        // Cena 4 - SEM_CAMINHO: o plasma B- existe e esta valido, mas o
        // deposito nao tem ligacao com a malha.
        ResultadoAlocacao semCaminho = servico.alocar(SOLICITANTE, GrupoSanguineo.B_NEG,
                TipoHemocomponente.PLASMA_FRESCO_CONGELADO, HOJE);
        assertEquals(MotivoFalhaAlocacao.SEM_CAMINHO, semCaminho.motivo());
        narrativa.append(String.format(Locale.ROOT,
                "4) B- plasma    -> %s (bolsa valida, porem em unidade sem ligacao)%n",
                semCaminho.motivo()));

        System.out.println(narrativa);
    }

    private static String caminhoLegivel(ResultadoAlocacao resultado,
                                         Map<String, UnidadeRede> unidades) {
        StringJoiner juncao = new StringJoiner(" -> ");
        for (String id : resultado.rota().caminho()) {
            juncao.add(id + " (" + nome(unidades, id) + ")");
        }
        return juncao.toString();
    }

    private static String nome(Map<String, UnidadeRede> unidades, String id) {
        UnidadeRede unidade = unidades.get(id);
        return unidade == null ? "fora da malha" : unidade.nome();
    }

    private static Bolsa bolsa(String codigo, GrupoSanguineo grupo,
                               TipoHemocomponente tipo, LocalDate validade) {
        return new Bolsa(codigo, tipo, grupo, 450,
                validade.minusDays(tipo.getValidadeDias()), hemocentroDemo());
    }

    private static Hemocentro hemocentroDemo() {
        return new Hemocentro(
                "H1", "Hemocentro Demo", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100");
    }
}
