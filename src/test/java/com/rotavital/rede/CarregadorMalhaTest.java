package com.rotavital.rede;

import com.rotavital.estruturas.Dijkstra;
import com.rotavital.estruturas.Grafo;
import com.rotavital.estruturas.ResultadoRota;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CarregadorMalhaTest {

    @Test
    void malhaTemAsDozeUnidadesComoVertices() {
        Grafo<String> grafo = CarregadorMalha.carregar();

        assertEquals(12, grafo.quantidadeVertices());
        assertTrue(grafo.listarVertices().contains("HC01"));
        assertTrue(grafo.listarVertices().contains("HC12"));
    }

    /**
     * 12 unidades escolhendo 4 vizinhas dao 48 escolhas; com as arestas
     * espelhadas e as escolhas mutuas contadas uma vez so, a malha fecha em 64
     * arestas dirigidas, como registrado nas premissas.
     */
    @Test
    void malhaTemSessentaEQuatroArestasDirigidas() {
        Grafo<String> grafo = CarregadorMalha.carregar();

        int arestas = 0;
        for (String unidade : grafo.listarVertices()) {
            arestas += grafo.vizinhosComPeso(unidade).size();
        }

        assertEquals(64, arestas);
    }

    /**
     * Cada unidade escolhe 4 vizinhas, e a reciprocidade so pode aumentar esse
     * numero. Nenhuma pode ficar abaixo de 4, senao a malha nao esta completa.
     */
    @Test
    void todaUnidadeTemPeloMenosQuatroVizinhos() {
        Grafo<String> grafo = CarregadorMalha.carregar();

        for (String unidade : grafo.listarVertices()) {
            assertTrue(grafo.vizinhosComPeso(unidade).size() >= PremissasMalha.VIZINHOS_POR_UNIDADE,
                    "Unidade " + unidade + " ficou com menos de "
                            + PremissasMalha.VIZINHOS_POR_UNIDADE + " vizinhos");
        }
    }

    /**
     * Ancora o Haversine em um par conhecido: HEMOPE (Recife, Gracas) ate
     * Hospital Guararapes (Jaboatao, Prazeres), cerca de 13,13 km em linha
     * reta. Sem esta verificacao, um erro na formula passaria despercebido e
     * so apareceria deformando todos os tempos.
     */
    @Test
    void distanciaEmLinhaRetaDeHc01AteHc11() {
        Map<String, UnidadeRede> indice = CarregadorMalha.indicePorId();

        double distancia = CarregadorMalha.distanciaKm(indice.get("HC01"), indice.get("HC11"));

        assertEquals(13.13, distancia, 0.05);
    }

    @Test
    void todasAsUnidadesSaoAlcancaveisAPartirDoHc01() {
        Grafo<String> grafo = CarregadorMalha.carregar();

        Map<String, Double> distancias = new Dijkstra<>(grafo).calcularDistancias("HC01");

        assertEquals(12, distancias.size());
        for (UnidadeRede unidade : UnidadeRede.TODAS) {
            assertTrue(distancias.containsKey(unidade.id()),
                    "Unidade " + unidade.id() + " ficou inalcancavel a partir do HC01");
        }
    }

    /**
     * O caso que mostra o Dijkstra fazendo o seu trabalho na malha real: a
     * aresta direta HC01 -> HC05 (HEMOPE -> IMIP) custa 6,0 min, mas o caminho
     * minimo e 5,9 min passando por HC02. E o mesmo efeito do exemplo manual
     * de 5 unidades do escopo do grafo, agora com dado real.
     */
    @Test
    void caminhoMinimoAteHc05PassaPorHc02EmVezDaArestaDireta() {
        Grafo<String> grafo = CarregadorMalha.carregar();

        Map<String, Double> distancias = new Dijkstra<>(grafo).calcularDistancias("HC01");

        double arestaDireta = grafo.pesoAresta("HC01", "HC05");
        assertEquals(6.0, arestaDireta, 0.1);

        assertEquals(5.9, distancias.get("HC05"), 0.1);
        assertTrue(distancias.get("HC05") < arestaDireta,
                "O caminho minimo deveria ser menor que a aresta direta");
    }

    /**
     * Travessia Recife -> Jaboatao, a unidade mais distante do HEMOPE na
     * malha: cerca de 50,1 min.
     */
    @Test
    void tempoMinimoDeHc01AteHc12() {
        Grafo<String> grafo = CarregadorMalha.carregar();

        Map<String, Double> distancias = new Dijkstra<>(grafo).calcularDistancias("HC01");

        assertEquals(50.1, distancias.get("HC12"), 0.1);
    }
    /**
     * Na malha real, o desvio mais rapido que a aresta direta aparece tambem
     * na rota reconstruida: HEMOPE -> GSH Hemato -> IMIP.
     */
    @Test
    void rotaDeHc01AteHc05PassaPorHc02() {
        Grafo<String> grafo = CarregadorMalha.carregar();

        ResultadoRota<String> rota = new Dijkstra<>(grafo).calcularRota("HC01", "HC05");

        assertTrue(rota.alcancavel());
        assertEquals(List.of("HC01", "HC02", "HC05"), rota.caminho());
        assertEquals(5.9, rota.custoTotal(), 0.1);
    }

    /**
     * Travessia Recife -> Jaboatao: a rota sai do HEMOPE, passa pelo Real
     * Portugues e por Nossa Senhora de Lourdes antes de chegar ao Memorial.
     */
    @Test
    void rotaDeHc01AteHc12AtravessaRecifeEJaboatao() {
        Grafo<String> grafo = CarregadorMalha.carregar();

        ResultadoRota<String> rota = new Dijkstra<>(grafo).calcularRota("HC01", "HC12");

        assertTrue(rota.alcancavel());
        assertEquals(List.of("HC01", "HC04", "HC09", "HC12"), rota.caminho());
        assertEquals(50.1, rota.custoTotal(), 0.1);
    }
    /**
     * Id fora da faixa HC01 a HC12, como um codigo digitado errado na
     * requisicao: a consulta devolve resposta tratada em vez de excecao,
     * criterio de aceite da PI3-19.
     */
    @Test
    void rotaAteIdInexistenteNaMalhaDevolveRespostaTratada() {
        Grafo<String> malha = CarregadorMalha.carregar();

        ResultadoRota<String> rota = new Dijkstra<>(malha).calcularRota("HC01", "HC99");

        assertFalse(malha.listarVertices().contains("HC99"));
        assertFalse(rota.alcancavel());
        assertTrue(rota.caminho().isEmpty());
        assertEquals(0.0, rota.custoTotal());
        assertEquals(ResultadoRota.semCaminho(), rota);
    }

    // -----------------------------------------------------------------------
    // HU-05: a malha montada para qualquer conjunto de unidades, com alcance
    // maximo. E o que a roteirizacao usa sobre as unidades do banco.
    // -----------------------------------------------------------------------

    /**
     * A sobrecarga com lista, alimentada com as 12 reais, produz a mesma
     * malha que o carregamento padrao: os dois caminhos nao podem divergir.
     */
    @Test
    void carregarComListaDasDozeReaisProduzAMesmaMalha() {
        Grafo<String> padrao = CarregadorMalha.carregar();
        Grafo<String> comLista = CarregadorMalha.carregar(UnidadeRede.TODAS);

        assertEquals(padrao.listarVertices(), comLista.listarVertices());
        for (String unidade : padrao.listarVertices()) {
            assertEquals(padrao.vizinhosComPeso(unidade), comLista.vizinhosComPeso(unidade),
                    "vizinhos de " + unidade + " diferem entre os dois carregamentos");
        }
    }

    /**
     * Uma unidade alem do alcance maximo de todas as outras entra como
     * vertice, mas sem aresta: e assim que "destino inalcancavel" acontece
     * de verdade na roteirizacao (cenario 2 da HU-05). Sem o limite de
     * alcance, a regra dos 4 vizinhos mais proximos ligaria qualquer unidade
     * a malha, por mais distante que fosse.
     */
    @Test
    void unidadeAlemDoAlcanceMaximoFicaIsolada() {
        UnidadeRede saoPaulo = new UnidadeRede("SP01", "Hospital em Sao Paulo",
                "AGENCIA_TRANSFUSIONAL", "Sao Paulo", "Bela Vista", -23.5614, -46.6559);
        List<UnidadeRede> unidades = new java.util.ArrayList<>(UnidadeRede.TODAS);
        unidades.add(saoPaulo);

        Grafo<String> malha = CarregadorMalha.carregar(unidades);

        assertTrue(malha.contemVertice("SP01"));
        assertTrue(malha.vizinhosComPeso("SP01").isEmpty(), "SP01 nao deveria ter trecho algum");
        assertTrue(CarregadorMalha.distanciaKm(saoPaulo, UnidadeRede.TODAS.get(0))
                > PremissasMalha.ALCANCE_MAXIMO_KM);

        ResultadoRota<String> rota = new Dijkstra<>(malha).calcularRota("HC01", "SP01");
        assertFalse(rota.alcancavel());

        // E as 12 reais continuam ligadas entre si como antes.
        assertEquals(12, new Dijkstra<>(malha).calcularDistancias("HC01").size());
    }

    /**
     * O alcance limita quem recebe aresta, nao quantas: uma unidade com
     * apenas duas vizinhas dentro do alcance liga-se a essas duas, e nao
     * forca uma terceira fora do limite.
     */
    @Test
    void dentroDoAlcanceLigaSoAsQueEstaoAoAlcance() {
        UnidadeRede a = new UnidadeRede("A", "A", "HEMOCENTRO", "X", "x", -8.00, -34.90);
        UnidadeRede b = new UnidadeRede("B", "B", "HEMOCENTRO", "X", "x", -8.01, -34.90);
        UnidadeRede c = new UnidadeRede("C", "C", "HEMOCENTRO", "X", "x", -8.02, -34.90);
        UnidadeRede longe = new UnidadeRede("L", "L", "HEMOCENTRO", "X", "x", -9.50, -34.90);

        Grafo<String> malha = CarregadorMalha.carregar(List.of(a, b, c, longe));

        assertEquals(java.util.Set.of("B", "C"), malha.listarVizinhos("A"));
        assertTrue(malha.listarVizinhos("L").isEmpty());
    }

    @Test
    void carregarRecusaListaNulaOuComNulo() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CarregadorMalha.carregar((List<UnidadeRede>) null));

        List<UnidadeRede> comNulo = new java.util.ArrayList<>();
        comNulo.add(null);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CarregadorMalha.carregar(comNulo));
    }
}
