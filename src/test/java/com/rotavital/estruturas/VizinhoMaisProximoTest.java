package com.rotavital.estruturas;

import com.rotavital.rede.CarregadorMalha;
import com.rotavital.rede.UnidadeRede;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VizinhoMaisProximoTest {

    /**
     * Malha de exemplo com 5 vertices, montada para reproduzir a esparsidade
     * da rede real: nem todo par de unidades tem aresta direta.
     *
     * <pre>
     *   CD --2--> P --3--> H1 --4--> H2 --6--> H3
     *    \                   \                 ^
     *     \--------8--------> H2                |
     *                         H1 -----20------->|
     * </pre>
     *
     * Pontos de entrega: H1, H2 e H3. P e apenas unidade de passagem.
     *
     * <p>Custos a partir do CD, por caminho minimo: P = 2, H1 = 5 (via P),
     * H2 = 8 (aresta direta, mais barata que os 9 por H1) e H3 = 14
     * (8 + 6 pela H2, mais barato que os 25 pela H1).</p>
     */
    private Grafo<String> malhaDeExemplo() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("CD", "P", 2.0);
        grafo.inserirAresta("P", "H1", 3.0);
        grafo.inserirAresta("CD", "H2", 8.0);
        grafo.inserirAresta("H1", "H2", 4.0);
        grafo.inserirAresta("H2", "H3", 6.0);
        grafo.inserirAresta("H1", "H3", 20.0);

        return grafo;
    }

    /**
     * Roteiro completo conferido a mao:
     * passo 1, do CD os pendentes custam H1 = 5, H2 = 8 e H3 = 14, entao sai
     * H1 pelo caminho CD-P-H1 (5);
     * passo 2, de H1 custam H2 = 4 e H3 = 10, entao sai H2 por H1-H2 (4);
     * passo 3, de H2 o unico pendente e H3 por H2-H3 (6).
     * Total 5 + 4 + 6 = 15 minutos.
     */
    @Test
    @DisplayName("Rota normal: visita os tres pontos na ordem do vizinho mais proximo")
    void rotaNormalVisitaNaOrdemDoVizinhoMaisProximo() {
        VizinhoMaisProximo<String> roteirizador = new VizinhoMaisProximo<>(malhaDeExemplo());

        ResultadoRoteiro<String> roteiro = roteirizador.calcularRoteiro(
                "CD", List.of("H1", "H2", "H3"));

        assertEquals(List.of("H1", "H2", "H3"), roteiro.ordemDeVisita());
        assertEquals(List.of("CD", "P", "H1", "H2", "H3"), roteiro.caminhoCompleto());
        assertEquals(15.0, roteiro.custoTotalMinutos());
        assertEquals(3, roteiro.quantidadeVisitada());
    }

    /**
     * O caso que justifica medir por caminho minimo e nao por aresta direta.
     *
     * <p>O CD nao tem aresta para H1, mas chega nele em 5 minutos passando
     * por P. Tem aresta direta para H2, que custa 8. Medindo por aresta
     * direta, H1 pareceria inalcancavel e H2 sairia primeiro; medindo por
     * caminho minimo, H1 sai primeiro porque esta mais perto de verdade.</p>
     */
    @Test
    @DisplayName("A escolha usa o caminho minimo, nao a aresta direta: H1 vence H2 sem ter ligacao com o CD")
    void escolhaUsaCaminhoMinimoENaoArestaDireta() {
        Grafo<String> grafo = malhaDeExemplo();

        // A premissa do teste: nao existe CD -> H1, e existe CD -> H2.
        assertFalse(grafo.contemAresta("CD", "H1"));
        assertEquals(8.0, grafo.pesoAresta("CD", "H2"));

        ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(grafo)
                .calcularRoteiro("CD", List.of("H2", "H1"));

        // H1 entra primeiro mesmo tendo sido informado depois de H2 na lista,
        // o que mostra que a ordem vem da distancia e nao da entrada.
        assertEquals("H1", roteiro.ordemDeVisita().get(0),
                "H1 custa 5 por caminho e H2 custa 8 pela aresta direta");
        assertEquals(List.of("H1", "H2"), roteiro.ordemDeVisita());
    }

    /**
     * O caminho completo precisa ser percorrivel de ponta a ponta: cada par
     * consecutivo e uma aresta do grafo, e o vertice onde um trecho termina e
     * o seguinte comeca aparece uma unica vez.
     */
    @Test
    @DisplayName("Caminho completo e continuo: todo par consecutivo e aresta e a juncao nao se repete")
    void caminhoCompletoEhContinuoESemJuncaoRepetida() {
        Grafo<String> grafo = malhaDeExemplo();

        ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(grafo)
                .calcularRoteiro("CD", List.of("H1", "H2", "H3"));

        List<String> caminho = roteiro.caminhoCompleto();

        assertEquals("CD", caminho.get(0), "o caminho comeca na origem");

        for (int i = 0; i < caminho.size() - 1; i++) {
            String de = caminho.get(i);
            String para = caminho.get(i + 1);

            assertNotEquals(de, para,
                    "a juncao entre trechos nao pode repetir o vertice na posicao " + i);
            assertTrue(grafo.contemAresta(de, para),
                    "o trecho " + de + " -> " + para + " precisa existir no grafo");
        }

        // H1 e H2 sao juncao entre trechos: cada um aparece exatamente uma vez.
        assertEquals(1, caminho.stream().filter("H1"::equals).count());
        assertEquals(1, caminho.stream().filter("H2"::equals).count());
        assertEquals(caminho.size(), caminho.stream().distinct().count(),
                "neste roteiro nenhum vertice precisa ser revisitado");
    }
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Pontos sem caminho")
    class PontosSemCaminho {

        /**
         * Vertice inserido sem nenhuma aresta forma um componente isolado:
         * existe no grafo, mas ninguem chega nele.
         */
        @Test
        @DisplayName("Ponto em componente desconectado vai para inalcancaveis e nao interrompe o resto")
        void pontoDesconectadoNaoImpedeOsDemais() {
            Grafo<String> grafo = malhaDeExemplo();
            grafo.inserirVertice("ILHA");

            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(grafo)
                    .calcularRoteiro("CD", List.of("H1", "ILHA", "H2"));

            assertEquals(List.of("H1", "H2"), roteiro.ordemDeVisita());
            assertEquals(List.of("ILHA"), roteiro.pontosInalcancaveis());
            assertFalse(roteiro.alcancouTodos());
            assertEquals(9.0, roteiro.custoTotalMinutos(), "5 ate H1 mais 4 ate H2");
        }

        /**
         * O grafo e dirigido: depois de entrar em B nao existe saida, entao o
         * roteiro trava no meio. O que ja foi percorrido tem de sobreviver.
         */
        @Test
        @DisplayName("Rota que trava no meio preserva o roteiro parcial: ordem, caminho e custo ate ali")
        void roteiroParcialEhPreservadoQuandoARotaTrava() {
            Grafo<String> grafo = new Grafo<>();
            grafo.inserirAresta("CD", "A", 1.0);
            grafo.inserirAresta("A", "B", 1.0);
            grafo.inserirAresta("CD", "C", 10.0);

            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(grafo)
                    .calcularRoteiro("CD", List.of("A", "B", "C"));

            // C so e alcancavel a partir do CD; depois de ir para A e B, o
            // veiculo nao tem mais como chegar nele.
            assertEquals(List.of("A", "B"), roteiro.ordemDeVisita());
            assertEquals(List.of("CD", "A", "B"), roteiro.caminhoCompleto());
            assertEquals(2.0, roteiro.custoTotalMinutos());
            assertEquals(List.of("C"), roteiro.pontosInalcancaveis());
            assertFalse(roteiro.alcancouTodos());
        }

        @Test
        @DisplayName("Nenhum ponto alcancavel ainda devolve resultado, nao excecao")
        void nenhumAlcancavelDevolveResultadoVazio() {
            Grafo<String> grafo = malhaDeExemplo();
            grafo.inserirVertice("ILHA1");
            grafo.inserirVertice("ILHA2");

            ResultadoRoteiro<String> roteiro = assertDoesNotThrow(() ->
                    new VizinhoMaisProximo<>(grafo)
                            .calcularRoteiro("CD", List.of("ILHA1", "ILHA2")));

            assertTrue(roteiro.ordemDeVisita().isEmpty());
            assertEquals(List.of("CD"), roteiro.caminhoCompleto());
            assertEquals(0.0, roteiro.custoTotalMinutos());
            assertEquals(List.of("ILHA1", "ILHA2"), roteiro.pontosInalcancaveis(),
                    "a ordem de entrada e preservada na lista de nao atendidos");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Bordas da entrada")
    class BordasDaEntrada {

        @Test
        @DisplayName("a) Lista de pontos vazia: so a origem no caminho, custo zero e alcancouTodos verdadeiro")
        void listaVazia() {
            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(malhaDeExemplo())
                    .calcularRoteiro("CD", List.of());

            assertTrue(roteiro.ordemDeVisita().isEmpty());
            assertEquals(List.of("CD"), roteiro.caminhoCompleto());
            assertEquals(0.0, roteiro.custoTotalMinutos());
            assertTrue(roteiro.pontosInalcancaveis().isEmpty());
            assertTrue(roteiro.alcancouTodos(),
                    "nao havia nada a atender, entao nada ficou por atender");
        }

        @Test
        @DisplayName("b) Origem fora do grafo: todos os pontos ficam inalcancaveis, sem excecao")
        void origemForaDoGrafo() {
            ResultadoRoteiro<String> roteiro = assertDoesNotThrow(() ->
                    new VizinhoMaisProximo<>(malhaDeExemplo())
                            .calcularRoteiro("FORA_DA_MALHA", List.of("H1", "H2")));

            assertTrue(roteiro.ordemDeVisita().isEmpty());
            assertEquals(List.of("FORA_DA_MALHA"), roteiro.caminhoCompleto());
            assertEquals(0.0, roteiro.custoTotalMinutos());
            assertEquals(List.of("H1", "H2"), roteiro.pontosInalcancaveis());
            assertFalse(roteiro.alcancouTodos());
        }

        @Test
        @DisplayName("c) Ponto de entrega fora do grafo: vai para inalcancaveis e os demais sao roteirizados")
        void pontoDeEntregaForaDoGrafo() {
            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(malhaDeExemplo())
                    .calcularRoteiro("CD", List.of("H1", "FANTASMA", "H2"));

            assertEquals(List.of("H1", "H2"), roteiro.ordemDeVisita());
            assertEquals(List.of("FANTASMA"), roteiro.pontosInalcancaveis());
            assertEquals(9.0, roteiro.custoTotalMinutos());
        }

        @Test
        @DisplayName("d) Ponto repetido: visitado uma vez e, se inalcancavel, listado uma vez")
        void pontoRepetido() {
            ResultadoRoteiro<String> visitado = new VizinhoMaisProximo<>(malhaDeExemplo())
                    .calcularRoteiro("CD", List.of("H1", "H1", "H2", "H1"));

            assertEquals(List.of("H1", "H2"), visitado.ordemDeVisita());
            assertTrue(visitado.alcancouTodos());

            ResultadoRoteiro<String> inalcancavel = new VizinhoMaisProximo<>(malhaDeExemplo())
                    .calcularRoteiro("CD", List.of("FANTASMA", "FANTASMA"));

            assertEquals(List.of("FANTASMA"), inalcancavel.pontosInalcancaveis(),
                    "repetido na entrada nao pode aparecer duas vezes na saida");
        }

        @Test
        @DisplayName("e) Origem dentro da lista de pontos: ignorada, porque o veiculo ja esta nela")
        void origemDentroDaLista() {
            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(malhaDeExemplo())
                    .calcularRoteiro("CD", List.of("CD", "H1"));

            assertEquals(List.of("H1"), roteiro.ordemDeVisita());
            assertFalse(roteiro.pontosInalcancaveis().contains("CD"));
            assertTrue(roteiro.alcancouTodos());
        }

        @Test
        @DisplayName("f) Elemento nulo na lista: descartado em silencio, sem NullPointerException")
        void elementoNuloNaLista() {
            // Arrays.asList aceita nulo; List.of nao aceitaria.
            List<String> comBuraco = Arrays.asList("H1", null, "H2", null);

            ResultadoRoteiro<String> roteiro = assertDoesNotThrow(() ->
                    new VizinhoMaisProximo<>(malhaDeExemplo()).calcularRoteiro("CD", comBuraco));

            assertEquals(List.of("H1", "H2"), roteiro.ordemDeVisita());
            assertTrue(roteiro.pontosInalcancaveis().isEmpty());
            assertTrue(roteiro.alcancouTodos());
        }

        @Test
        @DisplayName("g) Argumento nulo e erro de programa: lanca IllegalArgumentException")
        void argumentosNulosLancam() {
            VizinhoMaisProximo<String> roteirizador = new VizinhoMaisProximo<>(malhaDeExemplo());

            assertThrows(IllegalArgumentException.class,
                    () -> roteirizador.calcularRoteiro(null, List.of("H1")));
            assertThrows(IllegalArgumentException.class,
                    () -> roteirizador.calcularRoteiro("CD", null));
            assertThrows(IllegalArgumentException.class,
                    () -> new VizinhoMaisProximo<String>(null));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Aplicacao na malha sintetica das 12 unidades")
    class MalhaSintetica {

        private static final String ORIGEM = "HC01";

        /**
         * Os oito hospitais da malha, ou seja, as unidades do tipo
         * AGENCIA_TRANSFUSIONAL. Os ids saem de UnidadeRede.TODAS, nao de uma
         * lista escrita a mao, para o teste nao desalinhar se a malha mudar.
         */
        private List<String> hospitais() {
            List<String> ids = new ArrayList<>();
            for (UnidadeRede unidade : UnidadeRede.TODAS) {
                if ("AGENCIA_TRANSFUSIONAL".equals(unidade.tipo())) {
                    ids.add(unidade.id());
                }
            }
            return ids;
        }

        @Test
        @DisplayName("Roteiro a partir do HEMOPE atende todos os hospitais, com caminho continuo")
        void roteiroNaMalhaRealAtendeTodosOsHospitais() {
            Grafo<String> malha = CarregadorMalha.carregar();
            List<String> pontos = hospitais();

            ResultadoRoteiro<String> roteiro =
                    new VizinhoMaisProximo<>(malha).calcularRoteiro(ORIGEM, pontos);

            assertTrue(roteiro.alcancouTodos(),
                    "a malha e conexa, entao nenhum hospital pode ficar de fora");
            assertTrue(roteiro.pontosInalcancaveis().isEmpty());

            // Todos os pedidos, exatamente uma vez cada.
            assertEquals(pontos.size(), roteiro.ordemDeVisita().size());
            for (String ponto : pontos) {
                assertEquals(1, roteiro.ordemDeVisita().stream().filter(ponto::equals).count(),
                        "o hospital " + ponto + " precisa ser visitado uma unica vez");
            }

            // Caminho continuo: todo par consecutivo e aresta da malha.
            List<String> caminho = roteiro.caminhoCompleto();
            assertEquals(ORIGEM, caminho.get(0));
            for (int i = 0; i < caminho.size() - 1; i++) {
                assertTrue(malha.contemAresta(caminho.get(i), caminho.get(i + 1)),
                        "o trecho " + caminho.get(i) + " -> " + caminho.get(i + 1)
                                + " precisa existir na malha");
            }

            assertTrue(roteiro.custoTotalMinutos() > 0,
                    "um roteiro com oito paradas nao pode custar zero");
        }

        @Test
        @DisplayName("O custo total e a soma dos trechos, recalculada pelo Dijkstra")
        void custoTotalEhASomaDosTrechos() {
            Grafo<String> malha = CarregadorMalha.carregar();
            Dijkstra<String> dijkstra = new Dijkstra<>(malha);

            ResultadoRoteiro<String> roteiro =
                    new VizinhoMaisProximo<>(malha).calcularRoteiro(ORIGEM, hospitais());

            // Recalcula perna a perna, de forma independente do laco interno.
            double somaDosTrechos = 0.0;
            String de = ORIGEM;
            for (String para : roteiro.ordemDeVisita()) {
                somaDosTrechos += dijkstra.calcularRota(de, para).custoTotal();
                de = para;
            }

            assertEquals(somaDosTrechos, roteiro.custoTotalMinutos(), 0.0001);
        }

        @Test
        @DisplayName("Id inexistente na malha e devolvido como nao atendido, sem derrubar o roteiro")
        void idInexistenteNaMalhaVaiParaInalcancaveis() {
            Grafo<String> malha = CarregadorMalha.carregar();

            List<String> pontos = new ArrayList<>(List.of("HC04", "HC99", "HC05"));

            ResultadoRoteiro<String> roteiro =
                    new VizinhoMaisProximo<>(malha).calcularRoteiro(ORIGEM, pontos);

            assertEquals(List.of("HC99"), roteiro.pontosInalcancaveis());
            assertFalse(roteiro.alcancouTodos());
            assertEquals(2, roteiro.ordemDeVisita().size());
            assertTrue(roteiro.ordemDeVisita().containsAll(List.of("HC04", "HC05")));
        }
    }
    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Limitacao: a heuristica nao garante o roteiro otimo")
    class NaoOtimalidade {

        /**
         * Grafo armado para o guloso errar. Do CD, P1 esta a 1 e P2 a 2,
         * entao a heuristica vai a P1 primeiro; mas de P1 a volta ate P2 custa
         * 100, enquanto de P2 a ida ate P1 custa 2.
         *
         * <pre>
         *   CD --1--> P1 --100--> P2
         *   CD --2--> P2 ---2---> P1
         * </pre>
         */
        private Grafo<String> armadilhaParaOGuloso() {
            Grafo<String> grafo = new Grafo<>();
            grafo.inserirAresta("CD", "P1", 1.0);
            grafo.inserirAresta("CD", "P2", 2.0);
            grafo.inserirAresta("P1", "P2", 100.0);
            grafo.inserirAresta("P2", "P1", 2.0);
            return grafo;
        }

        /**
         * Documenta uma limitacao conhecida da heuristica, nao um defeito da
         * implementacao: decidindo so pelo passo seguinte, o vizinho mais
         * proximo pode fechar com custo maior que o de outra ordem possivel.
         * Encontrar a ordem otima e o problema do caixeiro viajante, de custo
         * exponencial.
         */
        @Test
        @DisplayName("O roteiro guloso pode custar mais que outra ordem: limitacao conhecida, nao defeito")
        void roteiroGulosoPodeCustarMaisQueAOrdemAlternativa() {
            Grafo<String> grafo = armadilhaParaOGuloso();

            ResultadoRoteiro<String> guloso = new VizinhoMaisProximo<>(grafo)
                    .calcularRoteiro("CD", List.of("P1", "P2"));

            // O guloso escolhe P1 (custa 1) antes de P2 (custa 2).
            assertEquals(List.of("P1", "P2"), guloso.ordemDeVisita());
            assertEquals(101.0, guloso.custoTotalMinutos(), "1 ate P1 mais 100 ate P2");

            // A ordem inversa, calculada pelo Dijkstra, sai bem mais barata.
            Dijkstra<String> dijkstra = new Dijkstra<>(grafo);
            double alternativa = dijkstra.calcularRota("CD", "P2").custoTotal()
                    + dijkstra.calcularRota("P2", "P1").custoTotal();

            assertEquals(4.0, alternativa, "2 ate P2 mais 2 ate P1");
            assertTrue(alternativa < guloso.custoTotalMinutos(),
                    "visitar P2 primeiro custaria " + alternativa
                            + " contra os " + guloso.custoTotalMinutos() + " do guloso");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Desempate deterministico entre pontos equidistantes")
    class DesempateDeterministico {

        /**
         * A e B estao exatamente a mesma distancia do CD, entao quem decide e
         * a ordem de entrada.
         */
        private Grafo<String> doisEquidistantes() {
            Grafo<String> grafo = new Grafo<>();
            grafo.inserirAresta("CD", "A", 5.0);
            grafo.inserirAresta("CD", "B", 5.0);
            grafo.inserirAresta("A", "B", 1.0);
            grafo.inserirAresta("B", "A", 1.0);
            return grafo;
        }

        @Test
        @DisplayName("Empate: vence o primeiro informado na lista de entrada")
        void empateVenceOPrimeiroInformado() {
            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(doisEquidistantes())
                    .calcularRoteiro("CD", List.of("A", "B"));

            assertEquals(List.of("A", "B"), roteiro.ordemDeVisita());
        }

        @Test
        @DisplayName("Empate: invertendo a ordem de entrada, vence o outro")
        void empateInvertidoVenceOOutro() {
            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(doisEquidistantes())
                    .calcularRoteiro("CD", List.of("B", "A"));

            assertEquals(List.of("B", "A"), roteiro.ordemDeVisita(),
                    "e a ordem de entrada que desempata, nao a ordem interna do grafo");
        }

        @Test
        @DisplayName("Chamadas repetidas com a mesma entrada devolvem resultados iguais")
        void chamadasRepetidasDevolvemOMesmoResultado() {
            VizinhoMaisProximo<String> roteirizador =
                    new VizinhoMaisProximo<>(doisEquidistantes());

            ResultadoRoteiro<String> primeira = roteirizador.calcularRoteiro("CD", List.of("A", "B"));

            for (int execucao = 0; execucao < 50; execucao++) {
                assertEquals(primeira, roteirizador.calcularRoteiro("CD", List.of("A", "B")),
                        "o resultado nao pode variar entre execucoes da mesma entrada");
            }

            assertEquals(primeira.hashCode(),
                    roteirizador.calcularRoteiro("CD", List.of("A", "B")).hashCode());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Grafo dirigido: aresta de mao unica")
    class GrafoDirigido {

        /**
         * CD alcanca X e Y. De Y, porem, nao ha volta para lugar nenhum: a
         * aresta CD -> X e de mao unica e o veiculo que parou em Y fica sem
         * como chegar em X.
         *
         * <pre>
         *   CD --1--> Y        (Y nao tem aresta de saida)
         *   CD --9--> X
         * </pre>
         */
        @Test
        @DisplayName("Ponto alcancavel da origem mas nao de onde o veiculo parou vai para inalcancaveis")
        void maoUnicaDeixaPontoInalcancavelDepoisDaParada() {
            Grafo<String> grafo = new Grafo<>(true);
            grafo.inserirAresta("CD", "Y", 1.0);
            grafo.inserirAresta("CD", "X", 9.0);

            assertTrue(grafo.isDirecionado());

            // A partir do CD, os dois sao alcancaveis.
            Dijkstra<String> dijkstra = new Dijkstra<>(grafo);
            assertTrue(dijkstra.calcularDistancias("CD").containsKey("X"));
            assertTrue(dijkstra.calcularDistancias("CD").containsKey("Y"));
            // A partir de Y, X nao e.
            assertFalse(dijkstra.calcularDistancias("Y").containsKey("X"));

            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(grafo)
                    .calcularRoteiro("CD", List.of("X", "Y"));

            // O guloso vai a Y primeiro, porque custa 1, e perde o acesso a X.
            assertEquals(List.of("Y"), roteiro.ordemDeVisita());
            assertEquals(List.of("CD", "Y"), roteiro.caminhoCompleto());
            assertEquals(1.0, roteiro.custoTotalMinutos());
            assertEquals(List.of("X"), roteiro.pontosInalcancaveis());
            assertFalse(roteiro.alcancouTodos());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Imutabilidade do ResultadoRoteiro")
    class Imutabilidade {

        @Test
        @DisplayName("As tres listas devolvidas recusam alteracao")
        void listasDevolvidasSaoImutaveis() {
            Grafo<String> grafo = malhaDeExemplo();
            grafo.inserirVertice("ILHA");

            ResultadoRoteiro<String> roteiro = new VizinhoMaisProximo<>(grafo)
                    .calcularRoteiro("CD", List.of("H1", "ILHA"));

            assertThrows(UnsupportedOperationException.class,
                    () -> roteiro.ordemDeVisita().add("X"));
            assertThrows(UnsupportedOperationException.class,
                    () -> roteiro.caminhoCompleto().add("X"));
            assertThrows(UnsupportedOperationException.class,
                    () -> roteiro.pontosInalcancaveis().add("X"));
        }

        @Test
        @DisplayName("Alterar a lista de entrada depois da chamada nao muda o resultado")
        void alterarAListaDeEntradaNaoAfetaOResultado() {
            Grafo<String> grafo = malhaDeExemplo();
            List<String> entrada = new ArrayList<>(List.of("H1", "H2"));

            ResultadoRoteiro<String> roteiro =
                    new VizinhoMaisProximo<>(grafo).calcularRoteiro("CD", entrada);

            List<String> ordemAntes = List.copyOf(roteiro.ordemDeVisita());

            entrada.clear();
            entrada.add("OUTRO");

            assertEquals(ordemAntes, roteiro.ordemDeVisita(),
                    "o resultado nao pode acompanhar mudancas na lista do chamador");
            assertEquals(List.of("H1", "H2"), roteiro.ordemDeVisita());
        }

        @Test
        @DisplayName("equals e hashCode distinguem roteiros com inalcancaveis diferentes")
        void equalsConsideraOsInalcancaveis() {
            Grafo<String> completo = malhaDeExemplo();
            Grafo<String> comIlha = malhaDeExemplo();
            comIlha.inserirVertice("ILHA");

            ResultadoRoteiro<String> semSobra = new VizinhoMaisProximo<>(completo)
                    .calcularRoteiro("CD", List.of("H1"));
            ResultadoRoteiro<String> comSobra = new VizinhoMaisProximo<>(comIlha)
                    .calcularRoteiro("CD", List.of("H1", "ILHA"));

            // Mesma ordem de visita, mesmo caminho e mesmo custo; o que separa
            // os dois e a lista de nao atendidos.
            assertEquals(semSobra.ordemDeVisita(), comSobra.ordemDeVisita());
            assertEquals(semSobra.custoTotalMinutos(), comSobra.custoTotalMinutos());
            assertNotEquals(semSobra, comSobra);

            assertTrue(comSobra.toString().contains("ILHA"),
                    "o toString precisa mostrar o que ficou por atender");
        }
    }
}
