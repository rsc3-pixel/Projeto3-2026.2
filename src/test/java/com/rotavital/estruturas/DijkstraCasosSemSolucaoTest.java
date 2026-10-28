package com.rotavital.estruturas;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cobre o criterio de aceite da PI3-19: caso sem solucao devolve resposta
 * tratada, nao excecao.
 *
 * <p>Rota inexistente nao e falha de programa, e situacao normal da operacao:
 * unidade recem-cadastrada ainda sem trecho ligado, id digitado errado na
 * requisicao, trecho que so existe em um sentido. Quem chama precisa poder
 * checar {@code alcancavel()} e seguir, em vez de proteger cada chamada com
 * try/catch.</p>
 */
class DijkstraCasosSemSolucaoTest {

    /**
     * Rede de exemplo do escopo do grafo (PI3-13), usada como grafo valido de
     * referencia nos casos degenerados.
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
     * Resposta tratada de caso sem solucao: nao alcancavel, caminho vazio e
     * custo zero, equivalente a {@link ResultadoRota#semCaminho()}.
     */
    private void assertSemCaminho(ResultadoRota<String> rota) {
        assertNotNull(rota, "Caso sem solucao deve devolver resultado, nunca null");
        assertFalse(rota.alcancavel());
        assertTrue(rota.caminho().isEmpty());
        assertEquals(0.0, rota.custoTotal());
        assertEquals(ResultadoRota.semCaminho(), rota);
    }

    /**
     * Requisicao que chega sem unidade de origem preenchida.
     */
    @Test
    void origemNulaDevolveRespostaTratada() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        assertSemCaminho(dijkstra.calcularRota(null, "Hospital Destino"));
    }

    /**
     * Requisicao que chega sem unidade de destino preenchida.
     */
    @Test
    void destinoNuloDevolveRespostaTratada() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        assertSemCaminho(dijkstra.calcularRota("Hemocentro", null));
    }

    /**
     * Requisicao vazia, sem origem nem destino.
     */
    @Test
    void origemEDestinoNulosDevolvemRespostaTratada() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        assertSemCaminho(dijkstra.calcularRota(null, null));
    }

    /**
     * Id de origem que nao corresponde a nenhuma unidade da rede, tipicamente
     * um codigo digitado errado.
     */
    @Test
    void origemInexistenteNoGrafoDevolveRespostaTratada() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        assertSemCaminho(dijkstra.calcularRota("Hospital Fantasma", "Hospital Destino"));
    }

    /**
     * Id de destino que nao corresponde a nenhuma unidade da rede.
     */
    @Test
    void destinoInexistenteNoGrafoDevolveRespostaTratada() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        assertSemCaminho(dijkstra.calcularRota("Hemocentro", "Hospital Fantasma"));
    }

    /**
     * Unidade recem-cadastrada que ainda nao teve nenhum trecho de chegada
     * mapeado, entao existe na rede mas ninguem consegue chegar nela.
     */
    @Test
    void destinoIsoladoSemArestaDeChegadaDevolveRespostaTratada() {
        Grafo<String> grafo = redeDeExemplo();
        grafo.inserirVertice("Hospital Isolado");

        ResultadoRota<String> rota = new Dijkstra<>(grafo)
                .calcularRota("Hemocentro", "Hospital Isolado");

        assertTrue(grafo.contemVertice("Hospital Isolado"));
        assertSemCaminho(rota);
    }

    /**
     * Consulta feita antes de a malha ser carregada, com o grafo ainda vazio.
     */
    @Test
    void grafoVazioDevolveRespostaTratada() {
        Dijkstra<String> dijkstra = new Dijkstra<>(new Grafo<>());

        assertEquals(0, new Grafo<String>().quantidadeVertices());
        assertSemCaminho(dijkstra.calcularRota("Hemocentro", "Hospital Destino"));
    }

    /**
     * Trecho de mao unica: existe rota de ida, mas nao de volta, confirmando
     * que o algoritmo respeita o sentido das arestas decidido na secao 4 do
     * escopo do grafo.
     */
    @Test
    void grafoDirigidoNaoPermiteRotaNoSentidoContrario() {
        Grafo<String> grafo = new Grafo<>();
        grafo.inserirAresta("A", "B", 7.0);

        Dijkstra<String> dijkstra = new Dijkstra<>(grafo);

        ResultadoRota<String> ida = dijkstra.calcularRota("A", "B");
        assertTrue(ida.alcancavel());
        assertEquals(List.of("A", "B"), ida.caminho());
        assertEquals(7.0, ida.custoTotal());

        // B existe no grafo, mas nao alcanca A: a aresta so vale de A para B.
        assertTrue(grafo.contemVertice("B"));
        assertSemCaminho(dijkstra.calcularRota("B", "A"));
    }

    /**
     * Unidade sozinha na rede consultando rota para ela mesma: nao e caso sem
     * solucao, e o caminho trivial de custo zero.
     */
    @Test
    void grafoComUnicoVerticeTemRotaTrivialParaEleMesmo() {
        Grafo<String> grafo = new Grafo<>();
        grafo.inserirVertice("Hemocentro");

        ResultadoRota<String> rota = new Dijkstra<>(grafo).calcularRota("Hemocentro", "Hemocentro");

        assertTrue(rota.alcancavel());
        assertEquals(List.of("Hemocentro"), rota.caminho());
        assertEquals(0.0, rota.custoTotal());
    }

    /**
     * O mesmo contrato vale para o calculo de distancias: origem invalida
     * devolve mapa vazio em vez de estourar.
     */
    @Test
    void calcularDistanciasComOrigemInvalidaDevolveMapaVazio() {
        Dijkstra<String> dijkstra = new Dijkstra<>(redeDeExemplo());

        Map<String, Double> comOrigemNula = dijkstra.calcularDistancias(null);
        Map<String, Double> comOrigemInexistente = dijkstra.calcularDistancias("Hospital Fantasma");

        assertTrue(comOrigemNula.isEmpty());
        assertTrue(comOrigemInexistente.isEmpty());
    }

    /**
     * Rede de seguranca: varre de uma vez todas as combinacoes degeneradas,
     * para que nenhuma delas volte a lancar excecao numa mudanca futura.
     */
    @Test
    void nenhumaCombinacaoDegeneradaLancaExcecao() {
        Grafo<String> comIsolado = redeDeExemplo();
        comIsolado.inserirVertice("Hospital Isolado");

        // Grafo com ids no formato da malha real, para exercitar as mesmas
        // combinacoes sem que o pacote estruturas dependa do pacote rede.
        Grafo<String> comIdsDeUnidade = new Grafo<>();
        comIdsDeUnidade.inserirAresta("HC01", "HC02", 2.9);

        List<Grafo<String>> grafos = List.of(new Grafo<>(), comIsolado, comIdsDeUnidade);
        List<String> vertices = java.util.Arrays.asList(
                null, "", "Hemocentro", "Hospital Isolado", "Hospital Fantasma", "HC01", "HC99");

        assertDoesNotThrow(() -> {
            for (Grafo<String> grafo : grafos) {
                Dijkstra<String> dijkstra = new Dijkstra<>(grafo);
                for (String origem : vertices) {
                    assertNotNull(dijkstra.calcularDistancias(origem));
                    for (String destino : vertices) {
                        ResultadoRota<String> rota = dijkstra.calcularRota(origem, destino);
                        assertNotNull(rota);
                        assertNotNull(rota.caminho());
                        // Ou ha rota com caminho, ou nao ha rota e o caminho e
                        // vazio: nunca um estado intermediario.
                        assertEquals(rota.alcancavel(), !rota.caminho().isEmpty());
                    }
                }
            }
        });
    }

    /**
     * Unico caso que deve mesmo lancar: construir o Dijkstra sem grafo e erro
     * de programacao, nao situacao do dominio. Devolver um resultado tratado
     * aqui esconderia o defeito e faria toda consulta seguinte responder
     * "sem caminho" sem explicar o motivo.
     */
    @Test
    void construtorComGrafoNuloLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> new Dijkstra<String>(null));
    }
}
