#include "vizinho_mais_proximo.hpp"


namespace {

/*
 * Lista de pendentes, feita a mao, preservando a ordem de entrada.
 *
 * DIFERENCA PARA A VERSAO EM JAVA: la e um LinkedHashSet, que preserva a ordem
 * de insercao e descarta repetidos sozinho. Aqui o descarte de repetido e
 * explicito, no proprio inserir.
 *
 * A ordem importa duas vezes: define quem vence o empate de distancia, e e a
 * ordem em que os nao atendidos sao devolvidos.
 */
class ListaPendentes {
public:
    ListaPendentes() = default;
    ~ListaPendentes() {
        No* atual = primeiro_;
        while (atual != nullptr) {
            No* proximo = atual->proximo;
            delete atual;
            atual = proximo;
        }
    }

    ListaPendentes(const ListaPendentes&) = delete;
    ListaPendentes& operator=(const ListaPendentes&) = delete;

    /* Ignora vazio e repetido, mantendo a ordem de chegada. */
    void inserir(const std::string& ponto) {
        if (ponto.empty() || contem(ponto)) {
            return;
        }
        No* novo = new No{ponto, nullptr};
        if (ultimo_ == nullptr) {
            primeiro_ = novo;
            ultimo_ = novo;
        } else {
            ultimo_->proximo = novo;
            ultimo_ = novo;
        }
        tamanho_++;
    }

    bool contem(const std::string& ponto) const {
        for (const No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
            if (atual->ponto == ponto) {
                return true;
            }
        }
        return false;
    }

    void remover(const std::string& ponto) {
        No* anterior = nullptr;
        No* atual = primeiro_;

        while (atual != nullptr && atual->ponto != ponto) {
            anterior = atual;
            atual = atual->proximo;
        }
        if (atual == nullptr) {
            return;
        }

        if (anterior == nullptr) {
            primeiro_ = atual->proximo;   /* era o primeiro */
        } else {
            anterior->proximo = atual->proximo;
        }
        if (atual == ultimo_) {
            ultimo_ = anterior;           /* era o ultimo: o controle recua */
        }

        delete atual;
        tamanho_--;
    }

    bool vazia() const { return primeiro_ == nullptr; }
    int  tamanho() const { return tamanho_; }

    /* Os pendentes restantes, na ordem de entrada. */
    std::vector<std::string> paraVetor() const {
        std::vector<std::string> restantes;
        for (const No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
            restantes.push_back(atual->ponto);
        }
        return restantes;
    }

    const std::string* primeiroPonto() const {
        return primeiro_ == nullptr ? nullptr : &primeiro_->ponto;
    }

    /* Percorre na ordem de entrada, para o desempate ficar estavel. */
    template <typename Visita>
    void paraCada(Visita visita) const {
        for (const No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
            visita(atual->ponto);
        }
    }

private:
    struct No {
        std::string ponto;
        No*         proximo = nullptr;
    };

    No* primeiro_ = nullptr;
    No* ultimo_   = nullptr;
    int tamanho_  = 0;
};

}  // namespace

ResultadoRoteiro VizinhoMaisProximo::calcularRoteiro(
        const std::string& origem,
        const std::vector<std::string>& pontosDeEntrega) const {

    ResultadoRoteiro resultado;

    /* Conjunto ordenado: sem vazios, sem repetidos e sem a propria origem. */
    ListaPendentes pendentes;
    for (const std::string& ponto : pontosDeEntrega) {
        pendentes.inserir(ponto);
    }
    pendentes.remover(origem);

    std::string atual = origem;

    while (!pendentes.vazia()) {
        /*
         * Um unico caminho minimo mede a distancia da posicao atual ate todos
         * os pendentes de uma vez. Quem nao aparece na tabela e inalcancavel a
         * partir daqui.
         */
        const std::vector<DistanciaAte> distancias = dijkstra_.calcularDistancias(atual);

        /*
         * Escolhe o pendente mais proximo percorrendo na ordem de entrada e
         * trocando o candidato apenas com menor estrito: o empate preserva o
         * que veio primeiro.
         */
        std::string escolhido;
        double menorDistancia = 0.0;

        pendentes.paraCada([&](const std::string& pendente) {
            /*
             * Ausencia na tabela significa sem caminho, e nao distancia
             * infinita: o Dijkstra devolve apenas os alcancaveis.
             */
            const DistanciaAte* entrada = nullptr;
            for (const DistanciaAte& d : distancias) {
                if (d.vertice == pendente) {
                    entrada = &d;
                    break;
                }
            }
            if (entrada == nullptr) {
                return;
            }
            if (escolhido.empty() || entrada->distancia < menorDistancia) {
                escolhido = pendente;
                menorDistancia = entrada->distancia;
            }
        });

        if (escolhido.empty()) {
            /*
             * Nenhum pendente e alcancavel a partir de 'atual'. Insistir
             * reexecutaria o mesmo calculo indefinidamente: interromper aqui e
             * o que garante o termino do laco. O que sobrou em 'pendentes' e,
             * por definicao, o conjunto dos nao atendidos, e o roteiro parcial
             * ja calculado e preservado.
             */
            break;
        }

        resultado.ordemDeVisita.push_back(escolhido);
        resultado.trechos.push_back(TrechoRoteiro{escolhido, menorDistancia});
        resultado.custoTotalMinutos += menorDistancia;

        pendentes.remover(escolhido);
        atual = escolhido;
    }

    resultado.pontosInalcancaveis = pendentes.paraVetor();
    return resultado;
}
