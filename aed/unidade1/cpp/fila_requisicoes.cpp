#include "fila_requisicoes.hpp"

#include <iomanip>
#include <iostream>

Resultado FilaRequisicoes::enfileirar(const Requisicao& requisicao) {
    No* novo = new No{requisicao, nullptr};

    if (tras_ == nullptr) {
        /*
         * Fila vazia: o no novo e simultaneamente a frente e o tras.
         * Checar 'tras' em vez de 'frente' aqui e proposital: e o ponteiro
         * que a insercao precisa manipular.
         */
        frente_ = novo;
        tras_   = novo;
    } else {
        /*
         * Fila com elementos: liga o ultimo no ao novo e so depois move o
         * ponteiro 'tras'. Invertendo a ordem, o antigo ultimo ficaria
         * desconectado e os nos anteriores se perderiam.
         */
        tras_->proximo = novo;
        tras_ = novo;
    }

    tamanho_++;
    return Resultado::Ok;
}

std::optional<Requisicao> FilaRequisicoes::desenfileirar() {
    if (frente_ == nullptr) {
        return std::nullopt;   /* fila vazia */
    }

    No* removido = frente_;

    /* Copia antes do delete: depois dele o conteudo do no nao existe mais. */
    Requisicao atendida = removido->requisicao;

    /* A frente avanca para o no seguinte, que pode ser nullptr. */
    frente_ = removido->proximo;

    if (frente_ == nullptr) {
        /*
         * A fila esvaziou. Sem zerar o 'tras' ele continuaria apontando para
         * o no que esta sendo liberado, e a proxima insercao faria
         * 'tras->proximo = novo' sobre memoria invalida.
         */
        tras_ = nullptr;
    }

    delete removido;
    tamanho_--;
    return atendida;
}

Requisicao* FilaRequisicoes::frente() {
    if (frente_ == nullptr) {
        return nullptr;
    }
    return &frente_->requisicao;
}

void FilaRequisicoes::listar() const {
    if (frente_ == nullptr) {
        std::cout << "  (nenhuma requisicao na fila)\n";
        return;
    }

    int posicao = 1;
    for (const No* atual = frente_; atual != nullptr; atual = atual->proximo) {
        const Requisicao& r = atual->requisicao;
        std::cout << "  " << posicao << ". "
                  << std::left
                  << std::setw(8)  << r.id
                  << std::setw(30) << r.hospital
                  << std::setw(12) << r.prioridade
                  << std::setw(4)  << r.grupo_sanguineo
                  << std::setw(26) << r.componente
                  << "qtd:" << r.quantidade
                  << "  "   << r.data_hora
                  << '\n';
        posicao++;
    }
}

FilaRequisicoes::~FilaRequisicoes() {
    No* atual = frente_;
    while (atual != nullptr) {
        /* Guarda o proximo antes de liberar o no atual. */
        No* proximo = atual->proximo;
        delete atual;
        atual = proximo;
    }

    frente_  = nullptr;
    tras_    = nullptr;
    tamanho_ = 0;
}
