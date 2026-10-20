#include "fila_fefo.hpp"

#include <iomanip>
#include <iostream>

namespace {

/*
 * Decide se 'a' deve sair antes de 'b': primeiro a validade mais proxima e,
 * no empate, o menor codigo de rastreio.
 *
 * DIFERENCA PARA A VERSAO EM C: la a comparacao era strcmp(a, b) < 0, que
 * devolve um inteiro negativo, zero ou positivo e se le ao contrario da
 * intuicao. Com std::string os operadores < e == fazem o esperado, igual ao
 * compareTo e ao equals do Java.
 *
 * O formato ISO continua sendo o que torna isso possivel: "2026-11-01" é
 * menor que "2026-11-09" tanto como texto quanto como data.
 */
bool vemAntes(const Bolsa& a, const Bolsa& b) {
    if (a.data_validade != b.data_validade) {
        return a.data_validade < b.data_validade;
    }
    return a.codigo_rastreio < b.codigo_rastreio;
}

/* Vencida quando a data de referencia e POSTERIOR a validade. */
bool estaVencida(const Bolsa& bolsa, const std::string& dataReferencia) {
    return dataReferencia > bolsa.data_validade;
}

}  // namespace

Resultado FilaFefo::adicionar(const Bolsa& bolsa) {
    /*
     * DIFERENCA PARA A VERSAO EM C: la o malloc podia devolver NULL e cada
     * insercao verificava isso, devolvendo RV_ERRO. Aqui o new lanca
     * std::bad_alloc quando falta memoria, entao a verificacao desaparece do
     * caminho feliz. E o mesmo que Java faz com OutOfMemoryError.
     */
    No* novo = new No{bolsa, nullptr};

    if (primeiro_ == nullptr) {
        /* CASO 1 - fila vazia: o no novo e o unico, e tambem o primeiro. */
        primeiro_ = novo;

    } else if (vemAntes(bolsa, primeiro_->bolsa)) {
        /*
         * CASO 2 - a nova bolsa vence antes da que esta no inicio: ela passa
         * a ser a primeira. O antigo primeiro e religado atras dela ANTES de
         * o ponteiro da fila mudar; na ordem inversa o resto da cadeia
         * ficaria inalcancavel e vazaria.
         */
        novo->proximo = primeiro_;
        primeiro_ = novo;

    } else {
        /*
         * CASO 3 - meio ou fim: percorre com dois ponteiros, porque em lista
         * simplesmente encadeada nao ha como voltar para religar.
         *
         * A condicao avanca enquanto a bolsa de 'atual' ainda vem antes da
         * nova. Usando vemAntes(atual, nova) - e nao a negacao de
         * vemAntes(nova, atual) - o laco passa tambem pelos empates, e a
         * bolsa nova acaba depois das iguais: e o que torna a insercao
         * estavel.
         */
        No* anterior = primeiro_;
        No* atual = primeiro_->proximo;

        while (atual != nullptr && vemAntes(atual->bolsa, bolsa)) {
            anterior = atual;
            atual = atual->proximo;
        }

        /*
         * Encaixa entre 'anterior' e 'atual'. Quando 'atual' e nullptr, o no
         * entra no fim da lista: os dois casos se resolvem com as mesmas duas
         * linhas.
         */
        novo->proximo = atual;
        anterior->proximo = novo;
    }

    tamanho_++;
    return Resultado::Ok;
}

const Bolsa* FilaFefo::consultarProxima() const {
    if (primeiro_ == nullptr) {
        return nullptr;
    }
    return &primeiro_->bolsa;
}

std::optional<Bolsa> FilaFefo::retirarProxima() {
    if (primeiro_ == nullptr) {
        return std::nullopt;
    }

    No* removido = primeiro_;

    /* Copia antes do delete: depois dele o conteudo do no nao existe mais. */
    Bolsa saida = removido->bolsa;

    /*
     * Como a lista ja esta ordenada, retirar e apenas avancar o ponteiro do
     * inicio: nao ha reorganizacao, diferente do heap, que precisa
     * reequilibrar a arvore depois de remover a raiz.
     */
    primeiro_ = removido->proximo;

    delete removido;
    tamanho_--;
    return saida;
}

std::vector<Bolsa> FilaFefo::descartarVencidas(const std::string& dataReferencia) {
    std::vector<Bolsa> descartadas;

    /*
     * Para na primeira valida. A lista e ordenada por validade, entao toda
     * bolsa depois dela vence ainda mais tarde: nao ha o que procurar no
     * resto. No heap isso nao seria possivel, porque a ordem so vale para a
     * raiz e descobrir quais venceram exigiria varrer a arvore inteira.
     */
    while (primeiro_ != nullptr && estaVencida(primeiro_->bolsa, dataReferencia)) {
        auto removida = retirarProxima();
        if (removida) {
            descartadas.push_back(*removida);
        }
    }

    return descartadas;
}

void FilaFefo::listar() const {
    if (primeiro_ == nullptr) {
        std::cout << "  (fila vazia)\n";
        return;
    }

    /*
     * DIFERENCA PARA A VERSAO EM C: la o printf com "%-8s %-3s" alinhava as
     * colunas pela propria string de formato. Aqui os manipuladores std::left
     * e std::setw fazem o mesmo papel, com a vantagem de o compilador
     * conferir os tipos, o que o printf nao faz.
     */
    int posicao = 1;
    for (const No* atual = primeiro_; atual != nullptr; atual = atual->proximo) {
        const Bolsa& b = atual->bolsa;
        std::cout << "  " << posicao << ". "
                  << std::left
                  << std::setw(9) << b.codigo_rastreio
                  << std::setw(4) << b.grupo_sanguineo
                  << "validade:" << b.data_validade
                  << "  " << b.status
                  << '\n';
        posicao++;
    }
}

FilaFefo::~FilaFefo() {
    /*
     * DIFERENCA PARA A VERSAO EM C: la isto era fila_destruir, e quem usava a
     * fila precisava lembrar de chamar. Aqui o destrutor roda sozinho quando
     * o objeto sai de escopo, mesmo que uma excecao interrompa o caminho.
     * Esquecer de liberar deixou de ser possivel.
     */
    No* atual = primeiro_;
    while (atual != nullptr) {
        /* Guarda o proximo ANTES do delete: depois o no nao existe mais. */
        No* proximo = atual->proximo;
        delete atual;
        atual = proximo;
    }

    primeiro_ = nullptr;
    tamanho_  = 0;
}
