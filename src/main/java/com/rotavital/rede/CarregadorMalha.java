package com.rotavital.rede;

import com.rotavital.estruturas.Grafo;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta o grafo da rede real a partir das coordenadas das 12 unidades.
 *
 * <p>Resolve a lacuna registrada em {@code docs/premissas-malha-rede.md}: o
 * projeto ja tinha as unidades, mas nao tinha as arestas, e sem elas o
 * Dijkstra nao tem o que minimizar. Os tempos sao derivados da distancia
 * geodesica entre as unidades, aplicando as premissas de
 * {@link PremissasMalha}.</p>
 *
 * <p>O grafo gerado usa o id da unidade (HC01 a HC12) como vertice, e nao o
 * objeto inteiro: o {@link Grafo} so precisa de uma chave, e manter a chave
 * pequena evita depender de equals e hashCode do record. Para recuperar os
 * dados da unidade a partir do id existe o {@link #indicePorId()}.</p>
 *
 * <p><b>Complexidade:</b> O(V^2 log V) para montar a malha, porque cada
 * unidade compara sua distancia com todas as outras e ordena o resultado para
 * escolher as mais proximas. Com 12 unidades o custo e irrelevante, e o
 * carregamento roda uma vez.</p>
 */
public final class CarregadorMalha {

    private CarregadorMalha() {
        throw new AssertionError("Classe utilitaria, nao deve ser instanciada");
    }

    /**
     * Distancia em linha reta entre duas unidades, em quilometros, pela
     * formula de Haversine.
     *
     * <p>Haversine e a formula padrao de distancia entre dois pontos sobre a
     * superficie da Terra a partir de latitude e longitude. O resultado e
     * geodesico: nao considera o tracado das vias, o que fica a cargo do
     * fator de sinuosidade em {@link #tempoMinutos(double)}.</p>
     */
    public static double distanciaKm(UnidadeRede a, UnidadeRede b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Unidades nao podem ser nulas");
        }

        double latA = Math.toRadians(a.latitude());
        double latB = Math.toRadians(b.latitude());
        double deltaLat = Math.toRadians(b.latitude() - a.latitude());
        double deltaLon = Math.toRadians(b.longitude() - a.longitude());

        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(latA) * Math.cos(latB)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return 2 * PremissasMalha.RAIO_TERRA_KM * Math.asin(Math.sqrt(h));
    }

    /**
     * Converte distancia em linha reta para tempo estimado de percurso, em
     * minutos.
     *
     * <p>Aplica as duas premissas em sequencia: primeiro o fator de
     * sinuosidade, que transforma a reta em distancia de via, depois a
     * velocidade media, que transforma distancia em tempo. O resultado e o
     * peso da aresta, que e o que o Dijkstra minimiza (secao 3 do escopo do
     * grafo).</p>
     */
    public static double tempoMinutos(double distanciaKm) {
        double distanciaViaKm = distanciaKm * PremissasMalha.FATOR_SINUOSIDADE;
        return distanciaViaKm / PremissasMalha.VELOCIDADE_MEDIA_KMH * 60;
    }

    /**
     * Monta o grafo da malha completa.
     *
     * <p>Cada unidade se liga as {@link PremissasMalha#VIZINHOS_POR_UNIDADE}
     * geograficamente mais proximas, e cada aresta e inserida nos dois
     * sentidos com o mesmo peso. A reciprocidade e uma simplificacao assumida
     * nesta sprint: como o tempo e derivado da distancia, e distancia e
     * simetrica, ida e volta custam igual. A estrutura continua dirigida e
     * aceita pesos distintos por sentido quando houver dado real.</p>
     *
     * <p>Como a escolha dos vizinhos e feita por unidade e depois espelhada,
     * unidades muito procuradas terminam com mais de 4 vizinhos. E isso que
     * mantem a malha conectada.</p>
     *
     * <p>O peso e arredondado a uma casa decimal: a precisao do calculo nao
     * justifica mais casas, dado que a propria velocidade media e uma
     * estimativa.</p>
     */
    public static Grafo<String> carregar() {
        Grafo<String> grafo = new Grafo<>();

        // Insere todos os vertices antes das arestas, para que a malha tenha
        // as 12 unidades mesmo que alguma nao fosse escolhida por ninguem.
        for (UnidadeRede unidade : UnidadeRede.TODAS) {
            grafo.inserirVertice(unidade.id());
        }

        for (UnidadeRede origem : UnidadeRede.TODAS) {
            for (UnidadeRede destino : maisProximas(origem)) {
                double peso = arredondarUmaCasa(tempoMinutos(distanciaKm(origem, destino)));
                grafo.inserirAresta(origem.id(), destino.id(), peso);
                grafo.inserirAresta(destino.id(), origem.id(), peso);
            }
        }

        return grafo;
    }

    /**
     * Indice das unidades por id, para recuperar nome, tipo e coordenadas a
     * partir do vertice do grafo.
     *
     * @return mapa imutavel na ordem de {@link UnidadeRede#TODAS}
     */
    public static Map<String, UnidadeRede> indicePorId() {
        Map<String, UnidadeRede> indice = new LinkedHashMap<>();
        for (UnidadeRede unidade : UnidadeRede.TODAS) {
            indice.put(unidade.id(), unidade);
        }
        return Collections.unmodifiableMap(indice);
    }

    /**
     * As unidades mais proximas da informada, sem incluir ela mesma.
     *
     * <p>O desempate por id mantem a malha reproduzivel: sem ele, duas
     * unidades a mesma distancia poderiam entrar em ordem diferente entre
     * execucoes e mudar as arestas geradas.</p>
     */
    private static List<UnidadeRede> maisProximas(UnidadeRede origem) {
        return UnidadeRede.TODAS.stream()
                .filter(candidata -> !candidata.id().equals(origem.id()))
                .sorted(Comparator
                        .comparingDouble((UnidadeRede candidata) -> distanciaKm(origem, candidata))
                        .thenComparing(UnidadeRede::id))
                .limit(PremissasMalha.VIZINHOS_POR_UNIDADE)
                .toList();
    }

    /**
     * Arredonda para uma casa decimal. Os tempos sao sempre positivos, entao
     * {@link Math#round(double)} equivale a arredondar meio para cima.
     */
    private static double arredondarUmaCasa(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
