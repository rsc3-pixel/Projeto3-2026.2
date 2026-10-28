package com.rotavital.estruturas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Calculo de caminho minimo sobre o {@link Grafo}, usando Dijkstra com fila
 * de prioridade (heap binario).
 *
 * <p>Implementa a decisao registrada no escopo do grafo (PI3-13): o peso de
 * cada aresta e o tempo estimado de percurso em minutos, entao a distancia
 * acumulada aqui e sempre tempo, nunca quilometragem.</p>
 *
 * <p><b>Pre-condicao:</b> todos os pesos precisam ser nao negativos. E o que
 * garante a corretude do Dijkstra: uma vez que um vertice sai da fila com a
 * menor distancia conhecida, nenhum caminho posterior pode melhora-la, e por
 * isso ele pode ser fechado sem reprocessamento. O {@link Grafo} ja rejeita
 * peso negativo na insercao da aresta, entao a condicao vale por construcao e
 * nao ha necessidade de Bellman-Ford neste dominio.</p>
 *
 * <p><b>Complexidade:</b> O((V + E) log V). Cada vertice entra e sai da fila
 * um numero limitado de vezes e cada aresta e relaxada uma vez; as operacoes
 * de heap custam log V.</p>
 *
 * <p>O grafo e consumido apenas por {@link Grafo#listarVertices()} e
 * {@link Grafo#vizinhosComPeso(Object)}, sem depender da representacao
 * interna da lista de adjacencia.</p>
 */
public class Dijkstra<T> {

    private final Grafo<T> grafo;

    public Dijkstra(Grafo<T> grafo) {
        if (grafo == null) {
            throw new IllegalArgumentException("Grafo nao pode ser nulo");
        }
        this.grafo = grafo;
    }

    /**
     * Tempo minimo da origem ate cada vertice alcancavel.
     *
     * <p>O mapa devolvido contem somente os vertices realmente alcancaveis a
     * partir da origem, sempre incluindo a propria origem com distancia zero.
     * Vertice inalcancavel simplesmente nao aparece como chave: ausencia
     * significa "sem caminho", em vez de um valor sentinela como
     * {@code Double.POSITIVE_INFINITY}, que so adiaria a verificacao para
     * quem consome o resultado.</p>
     *
     * @param origem vertice de partida
     * @return mapa de vertice para tempo acumulado em minutos; mapa vazio se a
     *         origem for nula ou nao existir no grafo, sem lancar excecao
     */
    public Map<T, Double> calcularDistancias(T origem) {
        return percorrer(origem).distancias();
    }

    /**
     * Caminho minimo da origem ate o destino, com os vertices percorridos.
     *
     * <p>Roda a mesma travessia do {@link #calcularDistancias(Object)} e
     * reconstroi o caminho voltando do destino pelos predecessores ate a
     * origem, invertendo a lista no fim. Guardar o predecessor de cada vertice
     * custa O(V) de memoria e evita ter de reprocessar o grafo para descobrir
     * por onde a rota passou.</p>
     *
     * <p><b>Complexidade:</b> O((V + E) log V), a mesma da travessia. A
     * reconstrucao e O(V) e nao altera a ordem de grandeza.</p>
     *
     * @param origem  vertice de partida
     * @param destino vertice de chegada
     * @return a rota encontrada; {@link ResultadoRota#semCaminho()} se origem
     *         ou destino forem nulos, nao existirem no grafo, ou se nao houver
     *         caminho entre eles. Origem igual ao destino devolve um caminho de
     *         um unico vertice e custo zero
     */
    public ResultadoRota<T> calcularRota(T origem, T destino) {
        if (origem == null || destino == null) {
            return ResultadoRota.semCaminho();
        }

        Travessia<T> travessia = percorrer(origem);
        Double custo = travessia.distancias().get(destino);

        // Ausencia no mapa de distancias e o proprio sinal de inalcancavel,
        // entao a checagem de existencia do destino vem de graca aqui.
        if (custo == null) {
            return ResultadoRota.semCaminho();
        }

        List<T> caminho = new ArrayList<>();
        for (T atual = destino; atual != null; atual = travessia.predecessores().get(atual)) {
            caminho.add(atual);
        }
        Collections.reverse(caminho);

        return ResultadoRota.alcancado(caminho, custo);
    }

    /**
     * Executa o Dijkstra a partir da origem, produzindo as distancias minimas
     * e o predecessor de cada vertice alcancado.
     *
     * <p>E o unico lugar onde o algoritmo existe: tanto
     * {@link #calcularDistancias(Object)} quanto
     * {@link #calcularRota(Object, Object)} consomem este metodo, cada um
     * usando a parte do resultado que lhe interessa. Duplicar o laco para
     * acrescentar predecessores abriria espaco para as duas copias divergirem
     * com o tempo.</p>
     *
     * <p>Se a origem for nula ou nao existir no grafo, devolve os dois mapas
     * vazios, sem lancar excecao.</p>
     */
    private Travessia<T> percorrer(T origem) {
        Map<T, Double> distancias = new HashMap<>();
        Map<T, T> predecessores = new HashMap<>();

        if (origem == null || !grafo.listarVertices().contains(origem)) {
            return new Travessia<>(distancias, predecessores);
        }

        // Vertices ja fechados: a menor distancia deles e definitiva.
        Set<T> visitados = new HashSet<>();
        PriorityQueue<Candidato<T>> fila =
                new PriorityQueue<>(Comparator.comparingDouble(Candidato::distancia));

        distancias.put(origem, 0.0);
        fila.add(new Candidato<>(origem, 0.0));

        while (!fila.isEmpty()) {
            Candidato<T> atual = fila.poll();

            // Ao melhorar a distancia de um vertice inserimos uma entrada nova
            // em vez de remexer na fila, entao sobram entradas obsoletas do
            // mesmo vertice. Fechar o vertice na primeira vez que ele sai da
            // fila descarta essas sobras e evita reprocessa-lo.
            if (!visitados.add(atual.vertice())) {
                continue;
            }

            for (Map.Entry<T, Double> vizinho : grafo.vizinhosComPeso(atual.vertice()).entrySet()) {
                T destino = vizinho.getKey();
                if (visitados.contains(destino)) {
                    continue;
                }

                double candidata = atual.distancia() + vizinho.getValue();
                Double conhecida = distancias.get(destino);

                if (conhecida == null || candidata < conhecida) {
                    distancias.put(destino, candidata);
                    // O predecessor acompanha a distancia: so muda quando o
                    // caminho ate o destino de fato melhorou.
                    predecessores.put(destino, atual.vertice());
                    fila.add(new Candidato<>(destino, candidata));
                }
            }
        }

        return new Travessia<>(distancias, predecessores);
    }

    /**
     * Entrada da fila de prioridade: um vertice com a distancia acumulada que
     * o colocou nessa posicao.
     */
    private record Candidato<V>(V vertice, double distancia) {
    }

    /**
     * Resultado bruto de uma travessia: distancia minima conhecida de cada
     * vertice alcancado e de qual vertice se chegou nele.
     *
     * <p>A origem aparece em {@code distancias} com zero e nao aparece em
     * {@code predecessores}, o que encerra naturalmente a reconstrucao do
     * caminho.</p>
     */
    private record Travessia<V>(Map<V, Double> distancias, Map<V, V> predecessores) {
    }
}
