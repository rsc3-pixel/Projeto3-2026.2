package com.rotavital.alocacao;

import com.rotavital.compatibilidade.MatrizCompatibilidade;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.IndiceEstoque;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.rotavital.dominio.enums.GrupoSanguineo.AB_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.AB_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.A_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.A_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.B_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.B_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.O_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.O_POS;
import static com.rotavital.dominio.enums.TipoHemocomponente.CONCENTRADO_HEMACIAS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Casos de borda da compatibilidade ABO/Rh no caminho da selecao de bolsas
 * (PI3-130).
 *
 * <p>A {@code MatrizCompatibilidadeTest} ja prova que a tabela responde certo,
 * e a {@code SelecaoFefoTest} ja cobre o fluxo principal. O que falta, e e o
 * que este arquivo cobre, e provar que a resposta certa da tabela chega
 * inteira ate a bolsa escolhida: pela lista de candidatas, pela busca no
 * {@link IndiceEstoque} e combinada com os outros dois filtros (validade e
 * status). Matriz correta com selecao que a ignora, ou que a aplica na ordem
 * errada, produz exatamente o erro que estes testes pegam.</p>
 *
 * <p>A data de referencia e fixa. Com {@code LocalDate.now()} um teste de
 * validade passaria hoje e falharia amanha, e a causa da falha nao estaria no
 * codigo.</p>
 */
@DisplayName("Casos de borda da compatibilidade na selecao (PI3-130)")
class CasosBordaCompatibilidadeTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 10);

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Caso 1: O- doa para cada um dos oito grupos")
    class DoadorUniversal {

        @ParameterizedTest(name = "receptor {0} aceita bolsa O-")
        @EnumSource(GrupoSanguineo.class)
        @DisplayName("a bolsa O- e selecionada para qualquer receptor")
        void bolsaONegativoServeParaQualquerReceptor(GrupoSanguineo receptor) {
            Bolsa universal = bolsa("B001", HOJE.plusDays(20), O_NEG);

            assertTrue(SelecaoFefo.alocavel(universal, HOJE, receptor, CONCENTRADO_HEMACIAS));

            Optional<Bolsa> escolhida = SelecaoFefo.escolher(
                    List.of(universal), HOJE, receptor, CONCENTRADO_HEMACIAS);

            assertEquals("B001", escolhida.orElseThrow().getCodigoRastreio());
        }

        @ParameterizedTest(name = "receptor {0} encontra a bolsa O- no indice")
        @EnumSource(GrupoSanguineo.class)
        @DisplayName("a busca no indice encontra a bolsa O- para qualquer receptor")
        void indiceEncontraBolsaONegativoParaQualquerReceptor(GrupoSanguineo receptor) {
            IndiceEstoque estoque = new IndiceEstoque();
            estoque.adicionar(bolsa("B001", HOJE.plusDays(20), O_NEG));

            Optional<Bolsa> escolhida = SelecaoFefo.escolher(
                    estoque, receptor, CONCENTRADO_HEMACIAS, HOJE);

            assertEquals("B001", escolhida.orElseThrow().getCodigoRastreio(),
                    "a bolsa esta indexada sob O_NEG e o receptor e " + receptor);
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Caso 2: AB+ recebe de cada um dos oito grupos")
    class ReceptorUniversal {

        @ParameterizedTest(name = "AB+ aceita bolsa {0}")
        @EnumSource(GrupoSanguineo.class)
        @DisplayName("o receptor AB+ aceita bolsa de qualquer grupo")
        void receptorAbPositivoAceitaQualquerDoador(GrupoSanguineo doador) {
            Bolsa candidata = bolsa("B001", HOJE.plusDays(20), doador);

            assertTrue(SelecaoFefo.alocavel(candidata, HOJE, AB_POS, CONCENTRADO_HEMACIAS));

            Optional<Bolsa> escolhida = SelecaoFefo.escolher(
                    List.of(candidata), HOJE, AB_POS, CONCENTRADO_HEMACIAS);

            assertEquals("B001", escolhida.orElseThrow().getCodigoRastreio());
        }

        @Test
        @DisplayName("ordenar devolve as oito bolsas quando o receptor e AB+")
        void ordenarDevolveAsOitoParaReceptorAbPositivo() {
            List<Bolsa> candidatas = new ArrayList<>();
            int dias = 10;
            for (GrupoSanguineo doador : GrupoSanguineo.values()) {
                candidatas.add(bolsa("B" + doador.name(), HOJE.plusDays(dias), doador));
                dias++;
            }

            List<Bolsa> ordenadas = SelecaoFefo.ordenar(
                    candidatas, HOJE, AB_POS, CONCENTRADO_HEMACIAS);

            assertEquals(8, ordenadas.size(), "nenhuma das oito pode ser descartada");
            assertEquals(candidatas.stream().map(Bolsa::getCodigoRastreio).toList(),
                    ordenadas.stream().map(Bolsa::getCodigoRastreio).toList(),
                    "as validades foram criadas em ordem crescente, entao a ordem se mantem");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Caso 3: a relacao nao e simetrica")
    class NaoSimetria {

        @Test
        @DisplayName("O- doa para A+, mas A+ nao doa para O-: trocar os lados muda a resposta")
        void trocarDoadorEReceptorMudaOResultado() {
            // Sentido que funciona: a bolsa O- serve ao receptor A+.
            Bolsa bolsaONegativo = bolsa("B001", HOJE.plusDays(20), O_NEG);
            assertTrue(SelecaoFefo.alocavel(bolsaONegativo, HOJE, A_POS, CONCENTRADO_HEMACIAS));
            assertEquals("B001", SelecaoFefo.escolher(
                            List.of(bolsaONegativo), HOJE, A_POS, CONCENTRADO_HEMACIAS)
                    .orElseThrow().getCodigoRastreio());

            // Mesmos dois grupos, lados trocados: a bolsa A+ nao serve ao O-.
            Bolsa bolsaAPositivo = bolsa("B002", HOJE.plusDays(20), A_POS);
            assertFalse(SelecaoFefo.alocavel(bolsaAPositivo, HOJE, O_NEG, CONCENTRADO_HEMACIAS));
            assertTrue(SelecaoFefo.escolher(
                    List.of(bolsaAPositivo), HOJE, O_NEG, CONCENTRADO_HEMACIAS).isEmpty());
        }

        @Test
        @DisplayName("indice so com bolsa A+ nao atende receptor O-")
        void indiceSoComAPositivoNaoAtendeReceptorONegativo() {
            IndiceEstoque estoque = new IndiceEstoque();
            estoque.adicionar(bolsa("B001", HOJE.plusDays(20), A_POS));

            assertTrue(SelecaoFefo.escolher(estoque, O_NEG, CONCENTRADO_HEMACIAS, HOJE).isEmpty());

            // O caminho inverso, com o mesmo estoque, atende: A+ recebe de A+.
            assertEquals("B001", SelecaoFefo.escolher(estoque, A_POS, CONCENTRADO_HEMACIAS, HOJE)
                    .orElseThrow().getCodigoRastreio());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Caso 4: positivo nao doa para o negativo do mesmo grupo ABO")
    class PositivoParaNegativoDoMesmoGrupo {

        static Stream<Arguments> paresPositivoParaNegativo() {
            return Stream.of(
                    Arguments.of(O_POS, O_NEG),
                    Arguments.of(A_POS, A_NEG),
                    Arguments.of(B_POS, B_NEG),
                    Arguments.of(AB_POS, AB_NEG));
        }

        @ParameterizedTest(name = "bolsa {0} nao serve ao receptor {1}")
        @MethodSource("paresPositivoParaNegativo")
        @DisplayName("o unico motivo da recusa e o Rh: a bolsa esta valida e disponivel")
        void positivoNaoDoaParaNegativo(GrupoSanguineo doador, GrupoSanguineo receptor) {
            Bolsa positiva = bolsa("B001", HOJE.plusDays(20), doador);

            // Prova que validade e status estao em ordem: sem o receptor, passa.
            assertTrue(SelecaoFefo.alocavel(positiva, HOJE),
                    "a bolsa precisa estar valida e DISPONIVEL para o teste ter sentido");
            assertEquals(StatusBolsa.DISPONIVEL, positiva.getStatus());

            // Com o receptor, a compatibilidade e o unico filtro que a barra.
            assertFalse(SelecaoFefo.alocavel(positiva, HOJE, receptor, CONCENTRADO_HEMACIAS));
            assertTrue(SelecaoFefo.escolher(
                    List.of(positiva), HOJE, receptor, CONCENTRADO_HEMACIAS).isEmpty());
            assertTrue(SelecaoFefo.ordenar(
                    List.of(positiva), HOJE, receptor, CONCENTRADO_HEMACIAS).isEmpty());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Caso 5: estoque so com bolsa incompativel e ausencia de opcao, nao erro")
    class EstoqueSoComIncompativel {

        private List<Bolsa> tresIncompativeisComONegativo() {
            return List.of(
                    bolsa("B001", HOJE.plusDays(5), A_POS),
                    bolsa("B002", HOJE.plusDays(10), B_POS),
                    bolsa("B003", HOJE.plusDays(15), AB_POS));
        }

        @Test
        @DisplayName("escolher devolve vazio sem lancar excecao")
        void escolherNaoLancaEDevolveVazio() {
            List<Bolsa> incompativeis = tresIncompativeisComONegativo();

            Optional<Bolsa> escolhida = assertDoesNotThrow(() -> SelecaoFefo.escolher(
                    incompativeis, HOJE, O_NEG, CONCENTRADO_HEMACIAS));

            assertTrue(escolhida.isEmpty());

            // As tres estao validas e disponiveis: o estoque existe, so nao serve.
            incompativeis.forEach(b -> assertTrue(SelecaoFefo.alocavel(b, HOJE)));
        }

        @Test
        @DisplayName("ordenar devolve lista vazia, nao lista com as incompativeis no fim")
        void ordenarDevolveListaVazia() {
            List<Bolsa> ordenadas = assertDoesNotThrow(() -> SelecaoFefo.ordenar(
                    tresIncompativeisComONegativo(), HOJE, O_NEG, CONCENTRADO_HEMACIAS));

            assertTrue(ordenadas.isEmpty(),
                    "bolsa incompativel nao e opcao pior, e opcao invalida");
        }

        @Test
        @DisplayName("a busca no indice tambem devolve vazio sem excecao")
        void indiceSoComIncompativelDevolveVazio() {
            IndiceEstoque estoque = new IndiceEstoque();
            tresIncompativeisComONegativo().forEach(estoque::adicionar);

            Optional<Bolsa> escolhida = assertDoesNotThrow(() -> SelecaoFefo.escolher(
                    estoque, O_NEG, CONCENTRADO_HEMACIAS, HOJE));

            assertTrue(escolhida.isEmpty());
        }

        @Test
        @DisplayName("uma compativel no meio das incompativeis e encontrada: o resto nao atrapalha")
        void compativelEntreIncompativeisEhEncontrada() {
            List<Bolsa> candidatas = new ArrayList<>(tresIncompativeisComONegativo());
            candidatas.add(bolsa("B004", HOJE.plusDays(30), O_NEG));

            Optional<Bolsa> escolhida = SelecaoFefo.escolher(
                    candidatas, HOJE, O_NEG, CONCENTRADO_HEMACIAS);

            assertEquals("B004", escolhida.orElseThrow().getCodigoRastreio(),
                    "as tres incompativeis vencem antes, e nenhuma pode ganhar");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Caso 6: bolsa compativel porem vencida nao e selecionada")
    class CompativelPoremVencida {

        @Test
        @DisplayName("a vencida nao ganha da valida, mesmo vencendo antes de todas")
        void vencidaNaoGanhaDaValida() {
            Bolsa compativelVencida = bolsa("B001", HOJE.minusDays(1), O_NEG);
            Bolsa compativelValida = bolsa("B002", HOJE.plusDays(20), A_POS);

            List<Bolsa> candidatas = List.of(compativelVencida, compativelValida);

            // As duas servem ao receptor A+; a diferenca e so a validade.
            assertTrue(MatrizCompatibilidade.compativel(O_NEG, A_POS, CONCENTRADO_HEMACIAS));
            assertTrue(MatrizCompatibilidade.compativel(A_POS, A_POS, CONCENTRADO_HEMACIAS));

            Optional<Bolsa> escolhida = SelecaoFefo.escolher(
                    candidatas, HOJE, A_POS, CONCENTRADO_HEMACIAS);

            assertEquals("B002", escolhida.orElseThrow().getCodigoRastreio(),
                    "a vencida e a de validade mais proxima: estaria no topo se o "
                            + "filtro viesse depois da ordenacao");
            assertEquals(List.of("B002"), SelecaoFefo.ordenar(
                            candidatas, HOJE, A_POS, CONCENTRADO_HEMACIAS)
                    .stream().map(Bolsa::getCodigoRastreio).toList());
        }

        @Test
        @DisplayName("estoque so com compativel vencida devolve vazio, sem excecao")
        void soCompativelVencidaDevolveVazio() {
            List<Bolsa> candidatas = List.of(bolsa("B001", HOJE.minusDays(1), O_NEG));

            Optional<Bolsa> escolhida = assertDoesNotThrow(() -> SelecaoFefo.escolher(
                    candidatas, HOJE, A_POS, CONCENTRADO_HEMACIAS));

            assertTrue(escolhida.isEmpty());
        }

        @Test
        @DisplayName("fronteira: a bolsa so vence no dia seguinte, entao a que vence HOJE ainda serve")
        void compativelQueVenceHojeAindaEhSelecionada() {
            // Bolsa.estaVencida usa isAfter, entao validade igual a data de
            // referencia ainda nao esta vencida. A regra e de negocio, nao
            // detalhe de implementacao.
            Bolsa venceHoje = bolsa("B001", HOJE, O_NEG);
            Bolsa venceDepois = bolsa("B002", HOJE.plusDays(5), O_NEG);
            Bolsa venceuOntem = bolsa("B003", HOJE.minusDays(1), O_NEG);

            assertTrue(SelecaoFefo.alocavel(venceHoje, HOJE, A_POS, CONCENTRADO_HEMACIAS));
            assertFalse(SelecaoFefo.alocavel(venceuOntem, HOJE, A_POS, CONCENTRADO_HEMACIAS));

            Optional<Bolsa> escolhida = SelecaoFefo.escolher(
                    List.of(venceDepois, venceHoje, venceuOntem), HOJE, A_POS, CONCENTRADO_HEMACIAS);

            assertEquals("B001", escolhida.orElseThrow().getCodigoRastreio(),
                    "vence hoje, logo e a mais urgente entre as ainda validas");
        }

        @Test
        @DisplayName("a busca no indice tambem descarta a compativel vencida")
        void indiceDescartaCompativelVencida() {
            IndiceEstoque estoque = new IndiceEstoque();
            estoque.adicionar(bolsa("B001", HOJE.minusDays(1), O_NEG));
            estoque.adicionar(bolsa("B002", HOJE.plusDays(20), A_POS));

            assertEquals("B002", SelecaoFefo.escolher(estoque, A_POS, CONCENTRADO_HEMACIAS, HOJE)
                    .orElseThrow().getCodigoRastreio());

            IndiceEstoque soVencida = new IndiceEstoque();
            soVencida.adicionar(bolsa("B003", HOJE.minusDays(1), O_NEG));

            assertTrue(SelecaoFefo.escolher(soVencida, A_POS, CONCENTRADO_HEMACIAS, HOJE).isEmpty());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Criterio: empate de validade e deterministico com receptor")
    class EmpateDeterministico {

        /**
         * As seis permutacoes de tres candidatas. Se a escolha dependesse da
         * ordem de entrada, alguma delas devolveria codigo diferente.
         */
        static Stream<Arguments> permutacoesDeTres() {
            return Stream.of(
                    Arguments.of(List.of(0, 1, 2)),
                    Arguments.of(List.of(0, 2, 1)),
                    Arguments.of(List.of(1, 0, 2)),
                    Arguments.of(List.of(1, 2, 0)),
                    Arguments.of(List.of(2, 0, 1)),
                    Arguments.of(List.of(2, 1, 0)));
        }

        @ParameterizedTest(name = "ordem de entrada {0}")
        @MethodSource("permutacoesDeTres")
        @DisplayName("a escolhida e sempre a de menor codigo de rastreio, em qualquer ordem")
        void escolhaIndependeDaOrdemDeEntrada(List<Integer> ordem) {
            LocalDate mesmaValidade = HOJE.plusDays(7);
            List<Bolsa> base = List.of(
                    bolsa("B001", mesmaValidade, O_NEG),
                    bolsa("B002", mesmaValidade, A_POS),
                    bolsa("B003", mesmaValidade, B_NEG));

            List<Bolsa> candidatas = ordem.stream().map(base::get).toList();

            Optional<Bolsa> escolhida = SelecaoFefo.escolher(
                    candidatas, HOJE, AB_POS, CONCENTRADO_HEMACIAS);

            assertEquals("B001", escolhida.orElseThrow().getCodigoRastreio(),
                    "as tres empatam na validade e o desempate e por codigo");
            assertEquals(List.of("B001", "B002", "B003"), SelecaoFefo.ordenar(
                            candidatas, HOJE, AB_POS, CONCENTRADO_HEMACIAS)
                    .stream().map(Bolsa::getCodigoRastreio).toList(),
                    "a sequencia inteira tambem precisa ser estavel");
        }

        @Test
        @DisplayName("a ordem de insercao no indice nao muda a escolhida")
        void ordemDeInsercaoNoIndiceNaoMudaAEscolhida() {
            LocalDate mesmaValidade = HOJE.plusDays(7);

            IndiceEstoque crescente = new IndiceEstoque();
            crescente.adicionar(bolsa("B001", mesmaValidade, O_NEG));
            crescente.adicionar(bolsa("B002", mesmaValidade, A_POS));

            IndiceEstoque decrescente = new IndiceEstoque();
            decrescente.adicionar(bolsa("B002", mesmaValidade, A_POS));
            decrescente.adicionar(bolsa("B001", mesmaValidade, O_NEG));

            assertEquals("B001",
                    SelecaoFefo.escolher(crescente, AB_POS, CONCENTRADO_HEMACIAS, HOJE)
                            .orElseThrow().getCodigoRastreio());
            assertEquals("B001",
                    SelecaoFefo.escolher(decrescente, AB_POS, CONCENTRADO_HEMACIAS, HOJE)
                            .orElseThrow().getCodigoRastreio(),
                    "a iteracao do indice varia, a escolha nao pode variar com ela");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Coerencia: as 64 combinacoes concordam com a matriz")
    class CoerenciaComAMatriz {

        static Stream<Arguments> todasAsCombinacoes() {
            List<Arguments> combinacoes = new ArrayList<>();
            for (GrupoSanguineo doador : GrupoSanguineo.values()) {
                for (GrupoSanguineo receptor : GrupoSanguineo.values()) {
                    combinacoes.add(Arguments.of(doador, receptor));
                }
            }
            return combinacoes.stream();
        }

        @ParameterizedTest(name = "bolsa {0} para receptor {1}")
        @MethodSource("todasAsCombinacoes")
        @DisplayName("para bolsa valida e disponivel, alocavel responde o mesmo que compativel")
        void alocavelConcordaComAMatriz(GrupoSanguineo doador, GrupoSanguineo receptor) {
            Bolsa valida = bolsa("B001", HOJE.plusDays(20), doador);

            assertEquals(
                    MatrizCompatibilidade.compativel(doador, receptor, CONCENTRADO_HEMACIAS),
                    SelecaoFefo.alocavel(valida, HOJE, receptor, CONCENTRADO_HEMACIAS),
                    "a selecao nao pode discordar da matriz para " + doador + " -> " + receptor);
        }
    }

    // ------------------------------------------------------------------

    /**
     * Mesma construcao usada na {@code SelecaoFefoTest}: a validade nao e
     * informada ao construtor, a {@link Bolsa} a deriva de
     * {@code dataColeta + validade do tipo}, entao a coleta e calculada de
     * tras para frente.
     */
    private static Bolsa bolsa(String codigo, LocalDate dataValidade, GrupoSanguineo grupo) {
        Hemocentro hemocentro = new Hemocentro(
                "H1", "Hemocentro Teste", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100");

        LocalDate dataColeta = dataValidade.minusDays(CONCENTRADO_HEMACIAS.getValidadeDias());

        Bolsa bolsa = new Bolsa(codigo, CONCENTRADO_HEMACIAS, grupo, 450, dataColeta, hemocentro);
        assertEquals(dataValidade, bolsa.getDataValidade(),
                "Fixture nao produziu a validade pedida");
        return bolsa;
    }
}
