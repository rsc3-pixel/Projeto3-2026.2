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
     * <p>Ponto que nao pode ser atendido vai para
     * {@link ResultadoRoteiro#pontosInalcancaveis()} e o roteiro parcial ja
     * calculado e devolvido assim mesmo. Nenhuma dessas situacoes lanca
     * excecao, porque nenhuma delas e erro de programa: sao o que a malha
     * permitiu entregar.</p>
     *
     * <p>Comportamento nas bordas:</p>
     * <ul>
     *   <li><b>lista de pontos vazia:</b> ordem de visita vazia, caminho
     *       contendo somente a origem, custo zero e
     *       {@link ResultadoRoteiro#alcancouTodos()} verdadeiro - nao havia
     *       nada a atender, entao nada ficou por atender;</li>
     *   <li><b>origem fora do grafo:</b> nenhum ponto e visitado e todos vao
     *       para inalcancaveis, com caminho contendo so a origem e custo zero.
     *       Nao e excecao: o {@code calcularDistancias} devolve mapa vazio
     *       para origem inexistente, e o {@code ServicoAlocacaoRota} ja trata
     *       solicitante fora da malha como falta de caminho, nao como erro;</li>
     *   <li><b>ponto de entrega fora do grafo:</b> nunca aparece no mapa de
     *       distancias, entao vai para inalcancaveis enquanto os demais sao
     *       roteirizados normalmente;</li>
     *   <li><b>ponto repetido na lista:</b> visitado uma unica vez e, se for
     *       inalcancavel, listado uma unica vez;</li>
     *   <li><b>origem dentro da lista de pontos:</b> ignorada, porque o
     *       veiculo ja esta nela;</li>
     *   <li><b>elemento nulo dentro da lista:</b> descartado em silencio, como
     *       o {@code ServicoAlocacaoRota} faz com bolsa nula no estoque. Lista
     *       com buraco e defeito do dado de entrada, nao do algoritmo;</li>
     *   <li><b>origem nula ou lista nula:</b> ai sim
     *       {@link IllegalArgumentException}, porque e erro de programa de
     *       quem chamou, nao situacao da operacao.</li>
     * </ul>
     *
     * @param origem          vertice de partida
     * @param pontosDeEntrega pontos a visitar, na ordem que define o desempate
     *                        e a ordem da lista de inalcancaveis
     * @return o roteiro calculado, sempre com ao menos a origem no caminho
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
        // desempate e a ordem dos inalcancaveis) e, de graca, ignorar pontos
        // repetidos.
        //
        // Os nulos sao descartados aqui, um a um, em vez de copiar a colecao
        // inteira de uma vez: o LinkedHashSet aceitaria null sem reclamar, e o
        // List.copyOf do resultado so estouraria no fim, com um
        // NullPointerException longe da causa.
        Set<T> pendentes = new LinkedHashSet<>();
        for (T ponto : pontosDeEntrega) {
            if (ponto != null) {
                pendentes.add(ponto);
            }
        }
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
                // Nenhum pendente e alcancavel a partir de 'atual'. Insistir
                // reexecutaria o mesmo Dijkstra indefinidamente, porque nada
                // mudaria entre as voltas: interromper aqui e o que garante o
                // termino do laco.
                //
                // O que sobrou em 'pendentes' e, por definicao, o conjunto dos
                // nao atendidos - e ja esta na ordem de entrada, porque o
                // LinkedHashSet a preserva. O roteiro parcial calculado ate
                // aqui e preservado e devolvido junto: descartar o que ja foi
                // possivel entregar seria jogar fora a informacao util.
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

        // Os pendentes restantes sao os inalcancaveis. Quando o laco terminou
        // por esgotamento, o conjunto esta vazio e alcancouTodos() e true.
        List<T> inalcancaveis = new ArrayList<>(pendentes);

        return ResultadoRoteiro.de(ordemDeVisita, caminhoCompleto, inalcancaveis, custoTotal);
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
