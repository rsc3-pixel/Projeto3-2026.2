#ifndef PILHA_HISTORICO_HPP
#define PILHA_HISTORICO_HPP

#include "dominio.hpp"

#include <optional>

/*
 * Pilha LIFO (Last In, First Out) do historico de operacoes do estoque.
 *
 * LIFO combina com historico porque o que interessa consultar primeiro e a
 * operacao mais recente, e porque desempilhar e exatamente o movimento de
 * desfazer a ultima acao registrada.
 *
 * Um unico ponteiro, 'topo', basta: insercao e remocao acontecem na mesma
 * ponta, as duas em O(1). Guardar o fundo da pilha nao teria utilidade, ja
 * que nada entra nem sai por ali.
 */

class PilhaHistorico {
public:
    PilhaHistorico() = default;
    ~PilhaHistorico();

    PilhaHistorico(const PilhaHistorico&) = delete;
    PilhaHistorico& operator=(const PilhaHistorico&) = delete;

    /* Empilha no topo. */
    Resultado empilhar(const Operacao& operacao);

    /*
     * Retira do topo e devolve a operacao removida, ou std::nullopt se a
     * pilha estiver vazia. Copia em vez de devolver ponteiro porque o no e
     * liberado em seguida.
     */
    std::optional<Operacao> desempilhar();

    /*
     * Consulta a operacao do topo sem retirar. Devolve nullptr se vazia.
     * O ponteiro aponta para dentro do no e deixa de valer quando ele sai.
     */
    Operacao* topo();

    void listar() const;
    int  tamanho() const { return tamanho_; }
    bool vazia() const   { return topo_ == nullptr; }

private:
    struct No {
        Operacao operacao;        /* copia da operacao dentro do no */
        No*      abaixo = nullptr; /* no que estava no topo antes deste */
    };

    No* topo_    = nullptr;       /* nullptr quando a pilha esta vazia */
    int tamanho_ = 0;
};

#endif /* PILHA_HISTORICO_HPP */
