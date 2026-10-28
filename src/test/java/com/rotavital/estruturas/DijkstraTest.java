package com.rotavital.estruturas;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DijkstraTest {

    /**
     * Rede de exemplo com 5 unidades da secao "Esboco da rede" do escopo do
     * grafo (PI3-13), com os pesos em minutos.
     */
    private Grafo<String> redeDeExemplo() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("Hemocentro", "Hospital A", 10.0);
        grafo.inserirAresta("Hemocentro", "Hospital B", 20.0);
        grafo.inserirAresta("Hospital A", "Hospital B", 5.0);
        grafo.inserirAresta("Hospital A", "Hospital C", 15.0);
        grafo.inserirAresta("Hospital B", "Hospital Destino", 8.0);
        grafo.inserirAresta("Hospital C", "Hospital Destino", 5.0);

        return grafo;
    }

    /**
     * O caminho minimo ate o destino e 23 minutos, passando por Hospital A e
     * Hospital B, e nao os 28 da aresta direta Hemocentro -> Hospital B nem os
     * 30 do desvio por Hospital C.
     */
    @Test
    void distanciaDoHemocentroAteODestinoEhVinteETres() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        Map<String, Double> distancias = dijkstra.calcularDistancias("Hemocentro");

        assertEquals(23.0, distancias.get("Hospital Destino"));
    }

    /**
     * Hospital B custa 15 pelo desvio via Hospital A (10 + 5), e nao os 20 da
     * aresta direta: e o caso que mostra o Dijkstra relaxando uma distancia ja
     * conhecida.
     */
    @Test
    void distanciasIntermediariasUsamOMenorCaminho() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        Map<String, Double> distancias = dijkstra.calcularDistancias("Hemocentro");

        assertEquals(10.0, distancias.get("Hospital A"));
        assertEquals(15.0, distancias.get("Hospital B"));
        assertEquals(0.0, distancias.get("Hemocentro"));
    }

    /**
     * Vertice sem nenhuma aresta de chegada e inalcancavel, entao nao aparece
     * como chave do mapa. Ausencia significa "sem caminho", sem valor
     * sentinela de infinito.
     */
    @Test
    void verticeIsoladoNaoApareceNoMapaDeDistancias() {
        Grafo<String> grafo = redeDeExemplo();
        grafo.inserirVertice("Hospital Isolado");

        Map<String, Double> distancias = new Dijkstra<>(grafo).calcularDistancias("Hemocentro");

        assertTrue(grafo.contemVertice("Hospital Isolado"));
        assertFalse(distancias.containsKey("Hospital Isolado"));
        assertNull(distancias.get("Hospital Isolado"));
        assertEquals(5, distancias.size());
    }

    /**
     * Origem fora do grafo, ou nula, devolve mapa vazio em vez de excecao.
     */
    @Test
    void origemInexistenteDevolveMapaVazio() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        assertTrue(dijkstra.calcularDistancias("Hospital Fantasma").isEmpty());
        assertTrue(dijkstra.calcularDistancias(null).isEmpty());
    }
    /**
     * A reconstrucao tem de devolver o caminho inteiro na ordem do percurso,
     * nao so o custo: e por onde o veiculo passa que interessa para despachar
     * a entrega.
     */
    @Test
    void rotaAteODestinoPassaPorHospitalAEHospitalB() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        ResultadoRota<String> rota = dijkstra.calcularRota("Hemocentro", "Hospital Destino");

        assertTrue(rota.alcancavel());
        assertEquals(List.of("Hemocentro", "Hospital A", "Hospital B", "Hospital Destino"),
                rota.caminho());
        assertEquals(23.0, rota.custoTotal());
    }

    /**
     * Origem igual ao destino e caminho de um vertice so, custo zero. Sai
     * naturalmente da reconstrucao: a origem nao tem predecessor.
     */
    @Test
    void rotaDaOrigemParaElaMesmaTemUmUnicoVertice() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        ResultadoRota<String> rota = dijkstra.calcularRota("Hemocentro", "Hemocentro");

        assertTrue(rota.alcancavel());
        assertEquals(List.of("Hemocentro"), rota.caminho());
        assertEquals(0.0, rota.custoTotal());
    }

    /**
     * Sem caminho ate o destino, o resultado e vazio e nao nulo: quem consome
     * checa {@code alcancavel()} em vez de se defender de ponteiro nulo.
     */
    @Test
    void rotaAteVerticeIsoladoNaoEhAlcancavel() {
        Grafo<String> grafo = redeDeExemplo();
        grafo.inserirVertice("Hospital Isolado");

        ResultadoRota<String> rota = new Dijkstra<>(grafo)
                .calcularRota("Hemocentro", "Hospital Isolado");

        assertFalse(rota.alcancavel());
        assertTrue(rota.caminho().isEmpty());
        assertEquals(0.0, rota.custoTotal());
    }
}
