package com.rotavital.rede;

/**
 * Premissas usadas para gerar as arestas da malha das 12 unidades reais.
 *
 * <p>Reune em um lugar so os numeros registrados em
 * {@code docs/premissas-malha-rede.md}. Nenhum deles e um dado medido: sao
 * decisoes assumidas para esta sprint, porque o grupo nao levantou tempos
 * reais entre as unidades e consultar uma API de rotas exigiria chave e acesso
 * a rede durante os testes, o que quebraria o CI.</p>
 *
 * <p>Estao nomeados e isolados justamente para poderem ser questionados e
 * trocados: alterar uma premissa e mudar uma linha aqui e rodar os testes.</p>
 */
public final class PremissasMalha {

    /**
     * Raio medio da Terra em quilometros, usado na formula de Haversine.
     *
     * <p>Unico valor deste arquivo que nao e uma decisao do grupo: e a
     * constante padrao adotada para calculo de distancia geodesica.</p>
     */
    public static final double RAIO_TERRA_KM = 6371.0;

    /**
     * Velocidade media de percurso em km/h.
     *
     * <p>Velocidade efetiva porta a porta de um veiculo em deslocamento urbano
     * na Regiao Metropolitana do Recife, ja considerando semaforos,
     * congestionamento e trechos de via local. Nao e velocidade de via livre
     * nem limite legal, porque o que interessa e estimar tempo de entrega.</p>
     *
     * <p>E a premissa mais fragil da malha e a primeira a ser substituida caso
     * o grupo consiga tempos reais.</p>
     */
    public static final double VELOCIDADE_MEDIA_KMH = 25.0;

    /**
     * Fator de conversao de distancia em linha reta para distancia de via.
     *
     * <p>A distancia calculada das coordenadas e geodesica, e veiculo nao anda
     * em linha reta. O valor 1,4 (percurso 40% maior que a reta) e a faixa
     * usualmente adotada para malha urbana densa; no Recife, com rios, pontes
     * e canais, o desvio real tende a ficar nessa ordem ou acima.</p>
     */
    public static final double FATOR_SINUOSIDADE = 1.4;

    /**
     * Quantidade de unidades mais proximas as quais cada unidade se liga.
     *
     * <p>Mantem a rede esparsa, como decidido na secao 5 do escopo do grafo:
     * ligar todas com todas (132 arestas dirigidas) contrariaria a modelagem
     * ja aprovada e tornaria o Dijkstra um calculo trivial de aresta direta,
     * sem caminho intermediario.</p>
     *
     * <p>Com 4 vizinhos e arestas reciprocas, a malha fecha em 64 arestas
     * dirigidas. A reciprocidade faz algumas unidades terminarem com mais de 4
     * vizinhos, e e o que garante que o grafo fique conectado.</p>
     */
    public static final int VIZINHOS_POR_UNIDADE = 4;

    private PremissasMalha() {
        throw new AssertionError("Classe de constantes, nao deve ser instanciada");
    }
}
