#ifndef GRAFO_HPP
#define GRAFO_HPP

#include "dominio.hpp"

#include <optional>
#include <string>
#include <vector>

/*
 * Grafo ponderado e dirigido em memoria, com lista de adjacencia feita a mao.
 *
 * Vertice e uma unidade da rede (hemocentro ou hospital); aresta e um trecho
 * direto entre duas unidades; peso e o tempo estimado de percurso em minutos.
 * O peso e tempo e nao distancia porque hemocomponente tem validade curta e
 * cadeia fria: o que importa minimizar e o tempo ate a entrega.
 *
 * O equivalente em Java e com.rotavital.estruturas.Grafo, e a roteirizacao que
 * o consome esta documentada em docs/W10-roteirizacao-vizinho-mais-proximo.md.
 *
 * A representacao e lista de adjacencia porque a rede e esparsa: cada unidade
 * se liga a poucas vizinhas, entao o espaco fica em O(V + E) em vez dos O(V^2)
 * de uma matriz.
 *
 *
 * DIFERENCA PARA A VERSAO EM JAVA
 *
 * 1. Armazenamento. O Java guarda a adjacencia em
 *    Map<T, Map<T, Double>>, dois HashMap aninhados. Aqui sao duas cadeias de
 *    nos ligadas por ponteiro: uma lista de vertices, e cada vertice com a sua
 *    lista de arestas. Trocar por std::map resolveria em poucas linhas, mas
 *    apagaria o que este exercicio quer mostrar - o encadeamento e o
 *    religamento de ponteiros. O custo muda junto: o Java acha um vertice em
 *    O(1) pelo hash, e aqui a busca e linear, O(V).
 *
 * 2. Tipo do vertice. O Java e generico em <T>. Aqui o vertice e
 *    std::string fixo, e nao um template. O motivo e pratico: a implementacao
 *    mora em grafo.cpp, e classe template precisaria ficar inteira no
 *    cabecalho para o compilador instanciar. Como a rede identifica unidade por
 *    texto, o tipo fixo nao custa nada e mantem a separacao entre cabecalho e
 *    implementacao.
 *
 * 3. Memoria. O Java confia no coletor de lixo: ao perder a referencia, o mapa
 *    e recolhido sozinho. Aqui cada no e alocado com new, e o destrutor
 *    percorre as duas cadeias liberando com delete. E o RAII: o construtor faz
 *    o papel do grafo_criar e o destrutor o do grafo_destruir, chamado sozinho
 *    ao sair do escopo.
 *
 * 4. Retornos de consulta. O Java devolve Map e Set imutaveis, visoes sobre a
 *    estrutura interna. Aqui as consultas devolvem std::vector por copia, e
 *    pesoAresta devolve std::optional em vez de Double que pode ser nulo -
 *    a ausencia fica explicita no tipo, e nao em um ponteiro nulo que o
 *    chamador pode esquecer de testar.
 */

/* Um vizinho e o peso da aresta que leva ate ele. */
struct Vizinho {
    std::string destino;
    double      pesoMinutos = 0.0;
};

class Grafo {
public:
    /* Dirigido por padrao: ida e volta entre duas unidades nao levam o mesmo tempo. */
    explicit Grafo(bool direcionado = true) : direcionado_(direcionado) {}
    ~Grafo();

    /*
     * Copiar o grafo exigiria duplicar as duas cadeias de nos. Sem isso, dois
     * grafos apontariam para os mesmos nos e cada destrutor tentaria liberar
     * os mesmos enderecos. Como o exercicio nao precisa de copia, ela e
     * proibida: o compilador recusa em vez de gerar codigo quebrado em
     * silencio.
     */
    Grafo(const Grafo&) = delete;
    Grafo& operator=(const Grafo&) = delete;

    /* Insere o vertice se ainda nao existir. Vertice vazio e recusado. */
    Resultado inserirVertice(const std::string& vertice);

    /*
     * Insere a aresta, criando os vertices se preciso.
     *
     * Peso negativo e recusado: e a condicao que garante a corretude do
     * caminho minimo usado pela roteirizacao. Reinserir a mesma aresta
     * atualiza o peso, como no Java.
     */
    Resultado inserirAresta(const std::string& origem,
                            const std::string& destino,
                            double pesoMinutos);

    /* Vizinhos diretos com o peso de cada aresta; vazio se o vertice nao existir. */
    std::vector<Vizinho> vizinhosComPeso(const std::string& vertice) const;

    /* Todos os vertices, na ordem de insercao. */
    std::vector<std::string> listarVertices() const;

    /*
     * Peso da aresta, ou std::nullopt se ela nao existir.
     *
     * DIFERENCA PARA A VERSAO EM JAVA: la o retorno e Double, que pode vir
     * nulo e exige o chamador lembrar de testar. Aqui o optional obriga a
     * abrir o valor antes de usar.
     */
    std::optional<double> pesoAresta(const std::string& origem,
                                     const std::string& destino) const;

    bool contemVertice(const std::string& vertice) const;
    bool contemAresta(const std::string& origem, const std::string& destino) const;

    int  quantidadeVertices() const { return quantidadeVertices_; }
    bool direcionado() const        { return direcionado_; }

private:
    /* No da lista de arestas de um vertice. */
    struct NoAresta {
        std::string destino;
        double      peso    = 0.0;
        NoAresta*   proxima = nullptr;   /* nullptr quando e a ultima aresta */
    };

    /* No da lista de vertices; cada um carrega a sua propria lista de arestas. */
    struct NoVertice {
        std::string id;
        NoAresta*   primeiraAresta = nullptr;
        NoVertice*  proximo        = nullptr;  /* nullptr quando e o ultimo vertice */
    };

    /* Busca linear: sem hash, percorrer a cadeia e a unica forma. O(V). */
    NoVertice* buscar(const std::string& vertice) const;

    /* Garante o vertice e devolve o no, ou nullptr se nao foi possivel criar. */
    NoVertice* obterOuCriar(const std::string& vertice);

    /* Liga ou atualiza uma aresta num unico sentido. */
    void ligar(NoVertice* origem, const std::string& destino, double peso);

    NoVertice* primeiro_          = nullptr;  /* nullptr quando o grafo esta vazio */
    NoVertice* ultimo_            = nullptr;  /* preserva a ordem de insercao em O(1) */
    int        quantidadeVertices_ = 0;
    bool       direcionado_;
};

#endif /* GRAFO_HPP */
