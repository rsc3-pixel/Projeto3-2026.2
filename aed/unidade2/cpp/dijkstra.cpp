#include "dijkstra.hpp"

#include <algorithm>

namespace {

/*
 * Tabela de trabalho do Dijkstra, feita a mao.
 *
 * Guarda, por vertice alcancado, a menor distancia conhecida, de qual vertice
 * se chegou nele e se ele ja foi fechado. E uma cadeia de nos percorrida
 * linearmente: e dela que vem o O(V^2) registrado no cabecalho.
 *
 * DIFERENCA PARA A VERSAO EM JAVA: la sao dois HashMap, distancias e
 * predecessores, mais uma PriorityQueue para a ordem de fechamento. Aqui os
 * tres papeis cabem em um no so, e a "fila de prioridade" vira a varredura do
 * menorAberto.
 */
class Tabela {
public:
    Tabela() = default;
    ~Tabela() {
        No* atual = primeiro_;
        while (atual != nullptr) {
            /* Guarda o proximo antes do delete: depois dele o no nao existe. */
            No* proximo = atual->proximo;
            delete atual;
            atual = proximo;
        }
    }

    Tabela(const Tabela&) = delete;
    Tabela& operator=(const Tabela&) = delete;

    /*
     * Registra ou melhora a distancia do vertice, anotando de onde se chegou.
     *
     * O predecessor so muda junto com a distancia: se fosse atualizado fora
     * desse teste, um relaxamento pior sobrescreveria o predecessor bom e o
     * caminho reconstruido divergiria do custo devolvido.
     */
    void relaxar(const std::string& vertice, double distancia,
                 const std::string& predecessor) {
        if (No* existente = buscar(vertice)) {
            if (distancia < existente->distancia) {
                existente->distancia = distancia;
                existente->predecessor = predecessor;
            }
            return;
        }
        primeiro_ = new No{vertice, distancia, predecessor, false, primeiro_};
    }

    bool alcancado(const std::string& vertice) const {
        return buscar(vertice) != nullptr;
    }

    double distanciaDe(const std::string& vertice) const {
        const No* no = buscar(vertice);
        return no == nullptr ? 0.0 : no->distancia;
    }

    /* Predecessor do vertice, ou string vazia se for a origem ou nao existir. */
    std::string predecessorDe(const std::string& vertice) const {
        const No* no = buscar(vertice);
        return no == nullptr ? std::string{} : no->predecessor;
    }

    void fechar(const std::string& vertice) {
        if (No* no = buscar(vertice)) {
            no->fechado = true;
        }
    }

    /*
     * O vertice alcancado de menor distancia que ainda nao foi fechado, ou
     * string vazia se nao houver nenhum. E a varredura que substitui o heap -
     * O(V) por chamada, contra O(log V) do Java.
     */
    std::string menorAberto() const {
        const No* escolhido = nullptr;
        for (const No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
            if (atual->fechado) {
                continue;
            }
            /* Comparacao estrita: o primeiro encontrado vence o empate. */
            if (escolhido == nullptr || atual->distancia < escolhido->distancia) {
                escolhido = atual;
            }
        }
        return escolhido == nullptr ? std::string{} : escolhido->vertice;
    }

    /* Copia a tabela para devolver ao chamador, so com os alcancaveis. */
    std::vector<DistanciaAte> paraVetor() const {
        std::vector<DistanciaAte> saida;
        for (const No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
            saida.push_back(DistanciaAte{atual->vertice, atual->distancia});
        }
        return saida;
    }

private:
    struct No {
        std::string vertice;
        double      distancia = 0.0;
        std::string predecessor;          /* vazio na origem */
        bool        fechado   = false;
        No*         proximo   = nullptr;
    };

    No* buscar(const std::string& vertice) const {
        for (No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
            if (atual->vertice == vertice) {
                return atual;
            }
        }
        return nullptr;
    }

    No* primeiro_ = nullptr;
};

/* Executa a travessia a partir da origem, preenchendo a tabela. */
void percorrer(const Grafo& grafo, const std::string& origem, Tabela& tabela) {
    if (origem.empty() || !grafo.contemVertice(origem)) {
        /*
         * Origem inexistente deixa a tabela vazia, e nao e erro: e o mesmo
         * contrato do Java, onde calcularDistancias devolve mapa vazio.
         */
        return;
    }

    tabela.relaxar(origem, 0.0, std::string{});

    while (true) {
        const std::string atual = tabela.menorAberto();
        if (atual.empty()) {
            break;   /* nada mais a fechar */
        }
        tabela.fechar(atual);

        const double distanciaAtual = tabela.distanciaDe(atual);
        for (const Vizinho& vizinho : grafo.vizinhosComPeso(atual)) {
            tabela.relaxar(vizinho.destino, distanciaAtual + vizinho.pesoMinutos, atual);
        }
    }
}

}  // namespace

std::vector<DistanciaAte> Dijkstra::calcularDistancias(const std::string& origem) const {
    Tabela tabela;
    percorrer(grafo_, origem, tabela);
    return tabela.paraVetor();
}

ResultadoRota Dijkstra::calcularRota(const std::string& origem,
                                     const std::string& destino) const {
    ResultadoRota resultado;

    Tabela tabela;
    percorrer(grafo_, origem, tabela);

    if (!tabela.alcancado(destino)) {
        /*
         * Ausencia na tabela e o proprio sinal de inalcancavel, entao a
         * checagem de existencia do destino vem de graca. Devolve alcancavel
         * falso, com caminho vazio e custo zero - nunca excecao.
         */
        return resultado;
    }

    /* Reconstroi voltando do destino pelos predecessores ate a origem. */
    for (std::string atual = destino; !atual.empty();
         atual = tabela.predecessorDe(atual)) {
        resultado.caminho.push_back(atual);
    }
    std::reverse(resultado.caminho.begin(), resultado.caminho.end());

    resultado.custoTotal = tabela.distanciaDe(destino);
    resultado.alcancavel = true;
    return resultado;
}
