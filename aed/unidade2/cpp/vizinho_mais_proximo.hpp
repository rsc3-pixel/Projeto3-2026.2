#ifndef VIZINHO_MAIS_PROXIMO_HPP
#define VIZINHO_MAIS_PROXIMO_HPP

#include "dijkstra.hpp"
#include "grafo.hpp"

#include <string>
#include <vector>

/*
 * Roteirizacao por heuristica do vizinho mais proximo (nearest neighbour).
 *
 * Dado um ponto de partida e um conjunto de pontos de entrega, monta o roteiro
 * de forma gulosa: a cada passo vai para o ponto de entrega ainda nao visitado
 * que estiver mais perto da posicao atual, e repete dali.
 *
 * O equivalente em Java e com.rotavital.estruturas.VizinhoMaisProximo, e a
 * decisao de projeto esta em docs/W10-roteirizacao-vizinho-mais-proximo.md.
 *
 *
 * A DISTANCIA VEM DO CAMINHO MINIMO, NAO DA ARESTA DIRETA
 *
 * A malha da rede e esparsa de proposito: cada unidade se liga apenas as mais
 * proximas. Entao, entre dois pontos de entrega quaisquer, muitas vezes nao
 * existe aresta direta - e quando existe, ela nao e necessariamente o trajeto
 * mais rapido. Medir por pesoAresta daria "sem caminho" para pares
 * perfeitamente alcancaveis por duas ou tres pernas, e o roteiro deixaria de
 * fora pontos que tem caminho. Por isso cada passo roda um caminho minimo.
 *
 * E HEURISTICA, NAO OTIMO. O vizinho mais proximo decide olhando so o passo
 * seguinte, entao pode fechar o roteiro com custo maior que o do melhor
 * roteiro possivel. Encontrar a ordem otima e o problema do caixeiro viajante,
 * de custo exponencial.
 *
 * DESEMPATE DETERMINISTICO. Quando dois pendentes estao a mesma distancia,
 * vence o que veio primeiro na lista de entrada. Os pendentes sao guardados na
 * ordem informada e a comparacao usa menor estrito, nunca menor ou igual:
 * assim o primeiro encontrado se mantem. Sem isso o roteiro poderia variar
 * entre execucoes para a mesma entrada, e nao haveria como auditar a escolha.
 *
 *
 * DIFERENCA PARA A VERSAO EM JAVA
 *
 * 1. Onde mora o caminho minimo. Em nenhuma das duas versoes ele e um helper
 *    incidental: e um colaborador explicito. O Java guarda
 *    "private final Dijkstra<T> dijkstra", criado uma vez no construtor, e usa
 *    os dois metodos dele - calcularDistancias para medir todos os pendentes
 *    de uma vez e calcularRota para reconstruir o trecho escolhido. Aqui a
 *    classe Dijkstra vive em dijkstra.hpp e e guardada do mesmo jeito, como
 *    membro criado uma vez no construtor e reusado em todos os passos. Ela nao
 *    guarda estado entre chamadas, so a referencia ao grafo.
 *
 * 2. Estruturas de apoio. O Java usa LinkedHashSet para os pendentes, que
 *    preserva a ordem de entrada e descarta repetidos de graca. Aqui e uma
 *    cadeia de nos feita a mao, com o descarte de repetido explicito. O mesmo
 *    vale dentro do Dijkstra, cuja complexidade esta registrada em
 *    dijkstra.hpp: O(V^2) aqui, contra O((V+E) log V) no Java.
 *
 * 3. Tipo do vertice. O Java e generico em <T>; aqui e std::string fixo, pela
 *    mesma razao explicada em grafo.hpp - a implementacao mora no .cpp.
 *
 * 4. Memoria. O Java confia no coletor de lixo. Aqui as cadeias de apoio sao
 *    alocadas com new e liberadas antes de cada retorno, inclusive no caminho
 *    em que o laco e interrompido por nao haver mais ponto alcancavel.
 *
 * 5. O que o resultado carrega. O Java devolve ResultadoRoteiro com a ordem de
 *    visita, o caminho completo vertice a vertice, os inalcancaveis e o custo
 *    total. Aqui o caminho completo ficou de fora, porque nao e pedido nesta
 *    entrega; em troca, o custo de cada trecho vem detalhado, o que o Java so
 *    expoe recalculando.
 */

/* Um trecho do roteiro: para onde foi e quanto custou chegar. */
struct TrechoRoteiro {
    std::string destino;
    double      custoMinutos = 0.0;
};

/*
 * Resultado do roteiro.
 *
 * Ponto inalcancavel e estado legitimo do dominio, nao erro: o roteiro parcial
 * ja calculado e devolvido junto, e quem consome pergunta por alcancouTodos em
 * vez de se defender de uma excecao. E a mesma decisao do Java.
 */
struct ResultadoRoteiro {
    std::vector<std::string>   ordemDeVisita;        /* so os pontos de entrega visitados */
    std::vector<TrechoRoteiro> trechos;              /* um por perna percorrida */
    std::vector<std::string>   pontosInalcancaveis;  /* na ordem de entrada */
    double                     custoTotalMinutos = 0.0;

    bool alcancouTodos() const { return pontosInalcancaveis.empty(); }
    std::size_t quantidadeVisitada() const { return ordemDeVisita.size(); }
};

class VizinhoMaisProximo {
public:
    /*
     * Recebe o grafo por referencia constante: a roteirizacao le o grafo e
     * nunca o altera, e a referencia deixa claro que este objeto nao e dono
     * dele - quem criou o grafo continua responsavel por destrui-lo.
     *
     * DIFERENCA PARA A VERSAO EM JAVA: la o construtor recebe o Grafo e rejeita
     * nulo com IllegalArgumentException. Aqui uma referencia nao pode ser nula,
     * entao a checagem deixa de existir: o compilador a dispensa.
     */
    explicit VizinhoMaisProximo(const Grafo& grafo)
        : grafo_(grafo), dijkstra_(grafo) {}

    /*
     * Monta o roteiro a partir da origem.
     *
     * A lista de entrada e tratada como conjunto ordenado: pontos repetidos
     * contam uma vez, e a origem, se aparecer entre os pontos, e descartada
     * porque o veiculo ja esta nela. Ponto vazio e ignorado, equivalente ao
     * descarte de nulo que o Java faz.
     *
     * Origem fora do grafo nao e erro: nenhum ponto e alcancavel a partir dela,
     * entao todos vao para pontosInalcancaveis e o custo fica zero.
     */
    ResultadoRoteiro calcularRoteiro(const std::string& origem,
                                     const std::vector<std::string>& pontosDeEntrega) const;

    const Grafo& grafo() const { return grafo_; }

private:
    const Grafo& grafo_;

    /*
     * Criado uma vez e reusado em todos os passos do roteiro, como no Java:
     * nao guarda estado entre chamadas, so a referencia ao grafo.
     */
    Dijkstra dijkstra_;
};

#endif /* VIZINHO_MAIS_PROXIMO_HPP */
