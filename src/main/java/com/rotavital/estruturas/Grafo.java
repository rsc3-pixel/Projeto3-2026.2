package com.rotavital.estruturas;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

/**
 * Grafo em memoria usando lista de adjacencia.
 *
 * <p>A estrutura pode representar um grafo direcionado ou nao direcionado.
 * A representacao adotada nesta entrega e uma lista de adjacencia.</p>
 */
public class Grafo<T> {

    private final Map<T, Set<T>> adjacencias = new HashMap<>();
    private final boolean direcionado;

    public Grafo() {
        this(false);
    }

    public Grafo(boolean direcionado) {
        this.direcionado = direcionado;
    }

    public void inserirVertice(T vertice) {
        if (vertice == null) {
            throw new IllegalArgumentException("Vertice nao pode ser nulo");
        }
        adjacencias.putIfAbsent(vertice, new LinkedHashSet<>());
    }

    public void inserirAresta(T origem, T destino) {
        if (origem == null || destino == null) {
            throw new IllegalArgumentException("Origem e destino nao podem ser nulos");
        }

        inserirVertice(origem);
        inserirVertice(destino);

        adjacencias.get(origem).add(destino);
        if (!direcionado) {
            adjacencias.get(destino).add(origem);
        }
    }

    public Set<T> listarVizinhos(T vertice) {
        Set<T> vizinhos = adjacencias.get(vertice);
        if (vizinhos == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(vizinhos);
    }

    public boolean contemVertice(T vertice) {
        return adjacencias.containsKey(vertice);
    }

    public int quantidadeVertices() {
        return adjacencias.size();
    }

    public boolean isDirecionado() {
        return direcionado;
    }
}
