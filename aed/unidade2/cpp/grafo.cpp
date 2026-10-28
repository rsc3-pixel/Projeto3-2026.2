#include "grafo.hpp"

Grafo::~Grafo() {
    /*
     * Percorre as duas cadeias liberando no por no. Em cada passo o proximo e
     * guardado ANTES do delete: depois dele, ler no->proximo seria acesso a
     * memoria ja devolvida.
     *
     * DIFERENCA PARA A VERSAO EM JAVA: la nao existe este metodo. Perdida a
     * referencia ao Grafo, o coletor de lixo recolhe os mapas sozinho. Aqui a
     * liberacao e explicita, e esquecer uma das duas cadeias vazaria memoria
     * em silencio.
     */
    NoVertice* vertice = primeiro_;
    while (vertice != nullptr) {
        NoAresta* aresta = vertice->primeiraAresta;
        while (aresta != nullptr) {
            NoAresta* proximaAresta = aresta->proxima;
            delete aresta;
            aresta = proximaAresta;
        }

        NoVertice* proximoVertice = vertice->proximo;
        delete vertice;
        vertice = proximoVertice;
    }

    primeiro_ = nullptr;
    ultimo_ = nullptr;
    quantidadeVertices_ = 0;
}

Grafo::NoVertice* Grafo::buscar(const std::string& vertice) const {
    for (NoVertice* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
        if (atual->id == vertice) {
            return atual;
        }
    }
    return nullptr;
}

Grafo::NoVertice* Grafo::obterOuCriar(const std::string& vertice) {
    if (vertice.empty()) {
        return nullptr;
    }

    if (NoVertice* existente = buscar(vertice)) {
        return existente;
    }

    NoVertice* novo = new NoVertice{vertice, nullptr, nullptr};

    if (ultimo_ == nullptr) {
        /* Grafo vazio: o no novo e o primeiro e tambem o ultimo. */
        primeiro_ = novo;
        ultimo_ = novo;
    } else {
        /*
         * Liga o antigo ultimo ao novo ANTES de mover o ponteiro de controle.
         * Na ordem inversa a cadeia ficaria interrompida e os vertices
         * anteriores, inalcancaveis.
         */
        ultimo_->proximo = novo;
        ultimo_ = novo;
    }

    quantidadeVertices_++;
    return novo;
}

Resultado Grafo::inserirVertice(const std::string& vertice) {
    return obterOuCriar(vertice) != nullptr ? Resultado::Ok : Resultado::Erro;
}

void Grafo::ligar(NoVertice* origem, const std::string& destino, double peso) {
    /* Aresta ja existente tem o peso atualizado, como no Java. */
    for (NoAresta* aresta = origem->primeiraAresta;
         aresta != nullptr;
         aresta = aresta->proxima) {
        if (aresta->destino == destino) {
            aresta->peso = peso;
            return;
        }
    }

    /*
     * Insercao no inicio da lista de arestas: e O(1) e a ordem entre vizinhos
     * do mesmo vertice nao tem significado. A ordem que importa - a dos
     * vertices - e preservada pelo ponteiro ultimo_.
     */
    origem->primeiraAresta = new NoAresta{destino, peso, origem->primeiraAresta};
}

Resultado Grafo::inserirAresta(const std::string& origem,
                               const std::string& destino,
                               double pesoMinutos) {
    if (origem.empty() || destino.empty()) {
        return Resultado::Erro;
    }
    if (pesoMinutos < 0.0) {
        /*
         * Peso negativo e recusado na entrada, e nao no algoritmo que consome
         * o grafo: e a condicao que garante a corretude do caminho minimo.
         * Barrando aqui, ela vale por construcao.
         */
        return Resultado::Erro;
    }

    NoVertice* noOrigem = obterOuCriar(origem);
    NoVertice* noDestino = obterOuCriar(destino);
    if (noOrigem == nullptr || noDestino == nullptr) {
        return Resultado::Erro;
    }

    ligar(noOrigem, destino, pesoMinutos);
    if (!direcionado_) {
        ligar(noDestino, origem, pesoMinutos);
    }

    return Resultado::Ok;
}

std::vector<Vizinho> Grafo::vizinhosComPeso(const std::string& vertice) const {
    std::vector<Vizinho> vizinhos;

    NoVertice* no = buscar(vertice);
    if (no == nullptr) {
        /* Vertice inexistente devolve vazio, nao erro: e o contrato do Java. */
        return vizinhos;
    }

    for (NoAresta* aresta = no->primeiraAresta;
         aresta != nullptr;
         aresta = aresta->proxima) {
        vizinhos.push_back(Vizinho{aresta->destino, aresta->peso});
    }
    return vizinhos;
}

std::vector<std::string> Grafo::listarVertices() const {
    std::vector<std::string> vertices;
    for (NoVertice* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
        vertices.push_back(atual->id);
    }
    return vertices;
}

std::optional<double> Grafo::pesoAresta(const std::string& origem,
                                        const std::string& destino) const {
    NoVertice* no = buscar(origem);
    if (no == nullptr) {
        return std::nullopt;
    }

    for (NoAresta* aresta = no->primeiraAresta;
         aresta != nullptr;
         aresta = aresta->proxima) {
        if (aresta->destino == destino) {
            return aresta->peso;
        }
    }
    return std::nullopt;
}

bool Grafo::contemVertice(const std::string& vertice) const {
    return buscar(vertice) != nullptr;
}

bool Grafo::contemAresta(const std::string& origem, const std::string& destino) const {
    return pesoAresta(origem, destino).has_value();
}
