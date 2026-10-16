package com.rotavital.estruturas;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Roteirizacao por heuristica do vizinho mais proximo (nearest neighbour).
 *
 * <p>Dado um ponto de partida e um conjunto de pontos de entrega, monta o
 * roteiro de forma gulosa: a cada passo vai para o ponto de entrega ainda nao
 * visitado que estiver mais perto da posicao atual, e repete dali. E a mesma
 * logica da versao em C/C++ vista em sala, com a diferenca de que aqui a
 * "distancia" nao e um numero numa matriz pronta, e sim o custo do caminho
 * minimo no grafo.</p>
 *
 * <p><b>Por que a distancia vem do caminho minimo, e nao da aresta direta.</b>
 * A malha da rede e esparsa por decisao de projeto: cada unidade se liga
 * apenas as {@code VIZINHOS_POR_UNIDADE} mais proximas (secao 5 do escopo do
 * grafo). Entao, entre dois pontos de entrega quaisquer, muitas vezes
 * simplesmente nao existe aresta direta - e quando existe, ela nao e
 * necessariamente o trajeto mais rapido. Usar {@code pesoAresta} daria
 * "infinito" para pares sem ligacao direta e o roteiro deixaria de fora
 * pontos que sao perfeitamente alcancaveis. Por isso a distancia de cada
 * passo e obtida do {@link Dijkstra}, que ja existe desde a Unidade 1: esta
 * classe nao reimplementa caminho minimo.</p>
 *
 * <p><b>E heuristica, nao otimo.</b> O vizinho mais proximo decide olhando so
 * o passo seguinte, e por isso pode fechar o roteiro com um custo total maior
 * que o do melhor roteiro possivel - uma escolha barata agora pode obrigar a
 * um trecho caro depois. Encontrar a ordem otima e o problema do caixeiro
 * viajante, de custo exponencial. A heuristica entrega uma ordem boa em tempo
 * polinomial, o que e o que a operacao precisa.</p>
 *
 * <p><b>Desempate deterministico.</b> Quando dois pendentes estao a mesma
 * distancia, vence o que veio primeiro na lista de entrada. Os pendentes sao
 * guardados em {@link LinkedHashSet}, que preserva a ordem de insercao, e a
 * comparacao usa {@code <} estrito, nunca {@code <=}: assim o primeiro
 * encontrado no percurso se mantem. Sem isso o roteiro poderia variar entre
 * execucoes para a mesma entrada, e nao haveria como auditar a escolha - o
 * mesmo cuidado que a regra de desempate da selecao FEFO ja adota.</p>
 *
 * <p><b>Complexidade:</b> O(k · (V + E) log V) para k pontos de entrega. Cada
 * passo do laco roda dois Dijkstra - um para medir as distancias de todos os
 * pendentes e um para reconstruir o caminho ate o escolhido - e cada Dijkstra
 * custa O((V + E) log V). A constante 2 nao muda a ordem de grandeza. A
 * escolha do menor entre os pendentes e O(k) por passo, dominado pelo
 * Dijkstra.</p>
 *
 * <p>Classe Java pura: nao depende de Spring, de JPA nem de nenhuma camada
 * web, para que a POO possa consumi-la diretamente.</p>
 */
public class VizinhoMaisProximo<T> {

    private final Grafo<T> grafo;
    private final Dijkstra<T> dijkstra;

    public VizinhoMaisProximo(Grafo<T> grafo) {
        if (grafo == null) {
            throw new IllegalArgumentException("Grafo nao pode ser nulo");
        }
        this.grafo = grafo;
        // O Dijkstra e criado uma vez e reusado em todos os passos: ele nao
        // guarda estado entre chamadas, so a referencia ao grafo.
        this.dijkstra = new Dijkstra<>(grafo);
    }

    /**
     * Monta o roteiro a partir da origem, visitando os pontos de entrega pela
     * heuristica do vizinho mais proximo.
     *
     * <p>A lista de entrada e tratada como conjunto ordenado: pontos repetidos
     * contam uma vez e a origem, se aparecer entre os pontos de entrega, e
     * descartada, porque o veiculo ja esta nela.</p>
     *
     * @param origem          vertice de partida
     * @param pontosDeEntrega pontos a visitar, na ordem que define o desempate
     * @return o roteiro calculado; se nenhum ponto for alcancavel, devolve
     *         ordem de visita vazia, caminho contendo somente a origem e custo
     *         zero
     * @throws IllegalArgumentException se a origem ou a lista forem nulas
     */
    public ResultadoRoteiro<T> calcularRoteiro(T origem, List<T> pontosDeEntrega) {
        if (origem == null) {
            throw new IllegalArgumentException("Origem nao pode ser nula");
        }
        if (pontosDeEntrega == null) {
            throw new IllegalArgumentException("Lista de pontos de entrega nao pode ser nula");
        }

        // LinkedHashSet para preservar a ordem de entrada (que decide o
        // desempate) e, de graca, ignorar pontos repetidos.
        Set<T> pendentes = new LinkedHashSet<>(pontosDeEntrega);
        // O veiculo ja esta na origem: visitar nao faria sentido.
        pendentes.remove(origem);

        List<T> ordemDeVisita = new ArrayList<>();
        List<T> caminhoCompleto = new ArrayList<>();
        caminhoCompleto.add(origem);
        double custoTotal = 0.0;

        T atual = origem;

        while (!pendentes.isEmpty()) {
            // Um unico Dijkstra mede a distancia da posicao atual para todos
            // os pendentes de uma vez. Quem nao aparece no mapa e inalcancavel
            // a partir daqui.
            Map<T, Double> distancias = dijkstra.calcularDistancias(atual);

            T escolhido = maisProximoEntre(pendentes, distancias);

            if (escolhido == null) {
                // Nenhum pendente e alcancavel a partir de 'atual': insistir
                // reexecutaria o mesmo Dijkstra para sempre. Interromper aqui
                // e o que garante o termino do laco.
                //
                // PI3-subtarefa-2: e exatamente neste ponto que entra o
                // registro formal dos nao atendidos - os 'pendentes' que
                // sobraram sao a lista de inalcancaveis, e o ResultadoRoteiro
                // ganhara esse campo e o alcancouTodos().
                break;
            }

            ResultadoRota<T> trecho = dijkstra.calcularRota(atual, escolhido);

            // O caminho do trecho comeca no vertice onde o roteiro ja esta,
            // entao a juncao e descartada para nao aparecer duas vezes.
            List<T> vertices = trecho.caminho();
            caminhoCompleto.addAll(vertices.subList(1, vertices.size()));

            custoTotal += trecho.custoTotal();
            ordemDeVisita.add(escolhido);
            pendentes.remove(escolhido);
            atual = escolhido;
        }

        return ResultadoRoteiro.de(ordemDeVisita, caminhoCompleto, custoTotal);
    }

    /**
     * O pendente de menor distancia entre os que aparecem no mapa.
     *
     * <p>Percorre na ordem do {@link LinkedHashSet} e troca o candidato
     * apenas com {@code <} estrito, de modo que um empate preserve o que veio
     * primeiro na entrada.</p>
     *
     * @return o escolhido, ou {@code null} se nenhum pendente for alcancavel
     */
    private T maisProximoEntre(Set<T> pendentes, Map<T, Double> distancias) {
        T escolhido = null;
        double menorDistancia = 0.0;

        for (T pendente : pendentes) {
            Double distancia = distancias.get(pendente);
            if (distancia == null) {
                // Ausencia no mapa significa "sem caminho", nao distancia
                // infinita: o Dijkstra so devolve os alcancaveis.
                continue;
            }
            if (escolhido == null || distancia < menorDistancia) {
                escolhido = pendente;
                menorDistancia = distancia;
            }
        }

        return escolhido;
    }

    /**
     * O grafo sobre o qual o roteiro e calculado.
     */
    public Grafo<T> grafo() {
        return grafo;
    }
}
