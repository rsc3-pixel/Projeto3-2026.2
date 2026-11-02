#ifndef DIJKSTRA_HPP
#define DIJKSTRA_HPP

#include "grafo.hpp"

#include <string>
#include <vector>

/*
 * Caminho minimo sobre o Grafo, por Dijkstra.
 *
 * O equivalente em Java e com.rotavital.estruturas.Dijkstra, e as decisoes de
 * modelagem estao em docs/W02-escopo-grafo-rede-distribuicao.md.
 *
 * PRE-CONDICAO: todos os pesos precisam ser nao negativos. E o que garante a
 * corretude: uma vez que um vertice e fechado com a menor distancia conhecida,
 * nenhum caminho posterior pode melhora-la. O Grafo ja recusa peso negativo na
 * insercao da aresta, entao a condicao vale por construcao.
 *
 *
 * COMPLEXIDADE: O(V^2) NESTA VERSAO, CONTRA O((V + E) log V) NO JAVA
 *
 * Esta e uma diferenca real, e nao uma simplificacao de escrita - por isso
 * esta registrada aqui em vez de ficar implicita.
 *
 * O Java usa PriorityQueue, um heap binario, para escolher o proximo vertice
 * a fechar, e HashMap para a tabela de distancias: cada operacao custa
 * O(log V) ou O(1), e o total fica O((V + E) log V). E esse o numero
 * declarado no javadoc de Dijkstra.java, na secao 6 do
 * docs/W02-escopo-grafo-rede-distribuicao.md e no
 * docs/premissas-malha-rede.md.
 *
 * Aqui a tabela de distancias e a marcacao de fechados sao cadeias de nos
 * feitas a mao, percorridas linearmente. Escolher o proximo vertice custa
 * O(V), cada consulta a tabela custa O(V), e o algoritmo inteiro fica O(V^2).
 * E a versao classica por varredura, anterior ao uso de heap.
 *
 * A escolha e aceitavel aqui por dois motivos. O primeiro e de escala: a malha
 * tem 12 unidades e 64 arestas, entao V^2 e 144 contra cerca de 270 para
 * (V + E) log V - nesse tamanho a versao "pior" chega a fazer menos trabalho,
 * e a diferenca assintotica so apareceria com uma rede muito maior. O segundo
 * e o proprio objetivo do exercicio: trocar as cadeias por std::map e
 * std::priority_queue recuperaria o O((V + E) log V) em poucas linhas, mas
 * apagaria o encadeamento e o religamento de ponteiros, que e o que a Unidade
 * 2 quer mostrar.
 *
 * Em resumo: quando a documentacao do projeto diz O((V + E) log V), ela fala
 * da versao Java, que e a que roda em producao. Esta versao em C++ e didatica
 * e assume O(V^2) conscientemente.
 *
 *
 * DIFERENCA PARA A VERSAO EM JAVA
 *
 * 1. Tipo do vertice: la generico em <T>, aqui std::string fixo, pela mesma
 *    razao explicada em grafo.hpp - a implementacao mora no .cpp.
 *
 * 2. Estruturas de apoio: la HashMap e PriorityQueue; aqui cadeias de nos
 *    alocadas com new e liberadas no destrutor da propria tabela.
 *
 * 3. Ausencia significa "sem caminho": nas duas versoes, vertice inalcancavel
 *    simplesmente nao aparece na tabela de distancias, em vez de receber um
 *    valor sentinela de infinito. Quem consome checa a presenca, e nao
 *    compara com um numero magico.
 */

/*
 * Resultado de um calculo de rota: o caminho percorrido e o custo dele.
 *
 * DIFERENCA PARA A VERSAO EM JAVA: la e a classe ResultadoRota, imutavel, com
 * as fabricas estaticas alcancado e semCaminho. Aqui e um struct simples com
 * os mesmos tres dados. Rota inexistente e representada por caminho vazio,
 * custo zero e alcancavel falso - nunca excecao e nunca valor sentinela,
 * exatamente como no Java.
 */
struct ResultadoRota {
    std::vector<std::string> caminho;          /* da origem ao destino, em ordem */
    double                   custoTotal = 0.0;
    bool                     alcancavel = false;
};

/* Uma distancia conhecida ate um vertice, para devolver a tabela ao chamador. */
struct DistanciaAte {
    std::string vertice;
    double      distancia = 0.0;
};

class Dijkstra {
public:
    /*
     * Recebe o grafo por referencia constante: o algoritmo le e nunca altera.
     *
     * DIFERENCA PARA A VERSAO EM JAVA: la o construtor rejeita grafo nulo com
     * IllegalArgumentException. Aqui uma referencia nao pode ser nula, entao a
     * checagem deixa de existir - o compilador a dispensa.
     */
    explicit Dijkstra(const Grafo& grafo) : grafo_(grafo) {}

    /*
     * Tempo minimo da origem ate cada vertice ALCANCAVEL.
     *
     * Vertice sem caminho simplesmente nao aparece no resultado. Origem fora
     * do grafo devolve vetor vazio, sem erro.
     */
    std::vector<DistanciaAte> calcularDistancias(const std::string& origem) const;

    /*
     * Caminho minimo da origem ate o destino, com os vertices percorridos.
     *
     * Origem igual ao destino devolve caminho de um unico vertice e custo
     * zero. Destino inalcancavel devolve alcancavel falso, com caminho vazio.
     */
    ResultadoRota calcularRota(const std::string& origem,
                               const std::string& destino) const;

    const Grafo& grafo() const { return grafo_; }

private:
    const Grafo& grafo_;
};

#endif /* DIJKSTRA_HPP */
