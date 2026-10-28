#include "pilha_historico.hpp"

#include <iomanip>
#include <iostream>

Resultado PilhaHistorico::empilhar(const Operacao& operacao) {
    /*
     * O no novo passa a apontar para o antigo topo, e so depois o ponteiro
     * 'topo' da pilha sobe para ele. Nesta ordem o resto da pilha permanece
     * alcancavel durante toda a operacao; na ordem inversa, mover o topo
     * primeiro perderia o endereco de tudo o que estava embaixo.
     *
     * Quando a pilha esta vazia, 'topo' e nullptr e o novo no fica com
     * abaixo = nullptr, marcando o fundo da pilha. O caso vazio nao precisa
     * de tratamento proprio.
     */
    No* novo = new No{operacao, topo_};
    topo_ = novo;

    tamanho_++;
    return Resultado::Ok;
}

std::optional<Operacao> PilhaHistorico::desempilhar() {
    if (topo_ == nullptr) {
        return std::nullopt;   /* pilha vazia */
    }

    No* removido = topo_;

    /* Copia antes do delete, senao o dado iria embora com o no. */
    Operacao desfeita = removido->operacao;

    /*
     * O topo desce para o no que estava abaixo. Se era o unico no, 'abaixo'
     * e nullptr e a pilha volta ao estado vazio.
     */
    topo_ = removido->abaixo;

    delete removido;
    tamanho_--;
    return desfeita;
}

Operacao* PilhaHistorico::topo() {
    if (topo_ == nullptr) {
        return nullptr;
    }
    return &topo_->operacao;
}

void PilhaHistorico::listar() const {
    if (topo_ == nullptr) {
        std::cout << "  (historico vazio)\n";
        return;
    }

    /* Percorre do topo para o fundo: do mais recente ao mais antigo. */
    int posicao = 1;
    for (const No* atual = topo_; atual != nullptr; atual = atual->abaixo) {
        const Operacao& o = atual->operacao;
        std::cout << "  " << posicao << ". "
                  << std::left
                  << std::setw(8)  << o.id
                  << std::setw(18) << o.operacao
                  << std::setw(42) << o.descricao
                  << o.data_hora
                  << '\n';
        posicao++;
    }
}

PilhaHistorico::~PilhaHistorico() {
    No* atual = topo_;
    while (atual != nullptr) {
        /* Guarda o de baixo antes de liberar o atual. */
        No* abaixo = atual->abaixo;
        delete atual;
        atual = abaixo;
    }

    topo_    = nullptr;
    tamanho_ = 0;
}
