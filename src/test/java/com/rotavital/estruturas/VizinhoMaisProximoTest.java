package com.rotavital.estruturas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
