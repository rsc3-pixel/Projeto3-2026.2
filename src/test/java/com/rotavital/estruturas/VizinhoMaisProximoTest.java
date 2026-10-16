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
}
