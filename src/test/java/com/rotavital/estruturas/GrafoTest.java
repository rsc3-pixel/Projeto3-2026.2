package com.rotavital.estruturas;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GrafoTest {

    @Test
    void grafoVazioNaoPossuiVertices() {
        Grafo<String> grafo = new Grafo<>();

        assertEquals(0, grafo.quantidadeVertices());
        assertTrue(grafo.listarVizinhos("A").isEmpty());
    }

    @Test
    void permiteInserirVerticeIsolado() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirVertice("Hemocentro Recife");

        assertTrue(grafo.contemVertice("Hemocentro Recife"));
        assertTrue(grafo.listarVizinhos("Hemocentro Recife").isEmpty());
    }

    @Test
    void permiteInserirArestaEListarVizinhos() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("Hemocentro", "Hospital A");

        assertEquals(Set.of("Hospital A"), grafo.listarVizinhos("Hemocentro"));
        assertEquals(Set.of("Hemocentro"), grafo.listarVizinhos("Hospital A"));
    }

    @Test
    void naoDuplicaArestasEntreOsMesmosVertices() {
        Grafo<String> grafo = new Grafo<>();

        grafo.inserirAresta("A", "B");
        grafo.inserirAresta("A", "B");

        assertEquals(1, grafo.listarVizinhos("A").size());
    }

    @Test
    void suportaGrafoDirecionadoQuandoConfigurado() {
        Grafo<String> grafo = new Grafo<>(true);

        grafo.inserirAresta("A", "B");

        assertEquals(Set.of("B"), grafo.listarVizinhos("A"));
        assertTrue(grafo.listarVizinhos("B").isEmpty());
    }
}
