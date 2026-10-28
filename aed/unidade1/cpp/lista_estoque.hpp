#ifndef LISTA_ESTOQUE_HPP
#define LISTA_ESTOQUE_HPP

#include "dominio.hpp"

/*
 * Lista encadeada simples de bolsas, representando o estoque de um
 * hemocentro.
 *
 * Encadeada e nao vetor porque o estoque cresce e diminui a cada entrada e
 * saida de bolsa, e a lista encadeada insere e remove sem precisar
 * redimensionar nem deslocar os elementos vizinhos.
 *
 * A lista guarda ponteiro para o ultimo no, alem do primeiro. Sem ele, a
 * insercao no fim teria de percorrer a lista inteira para achar o final, o
 * que seria O(n); com ele a insercao e O(1).
 *
 * DIFERENCA PARA A VERSAO EM C: la a lista era uma struct manipulada por
 * funcoes soltas (lista_criar, lista_inserir, lista_destruir), e quem usava
 * precisava lembrar de chamar lista_destruir no fim. Aqui e uma classe: o
 * construtor faz o papel de lista_criar e o destrutor faz o de
 * lista_destruir, chamado sozinho quando o objeto sai de escopo. Esse e o
 * RAII, e e o que mais aproxima o C++ do Java neste codigo, com a diferenca
 * de que o momento da liberacao e deterministico, enquanto em Java depende
 * do coletor de lixo.
 *
 * Os nos continuam alocados a mao com new e liberados com delete, como no C
 * original: trocar por std::list apagaria justamente o que este exercicio
 * quer mostrar.
 */

class ListaEstoque {
public:
    ListaEstoque() = default;
    ~ListaEstoque();

    /*
     * Copiar uma lista exigiria duplicar toda a cadeia de nos. Sem isso, duas
     * listas apontariam para os mesmos nos e cada destrutor tentaria liberar
     * os mesmos enderecos, liberando duas vezes. Como o exercicio nao precisa
     * de copia, ela e proibida explicitamente: o compilador recusa em vez de
     * gerar codigo quebrado em silencio.
     *
     * Em C esse risco existia igual, mas nada no codigo impedia a copia.
     */
    ListaEstoque(const ListaEstoque&) = delete;
    ListaEstoque& operator=(const ListaEstoque&) = delete;

    /* Insere no FIM da lista. */
    Resultado inserir(const Bolsa& bolsa);

    /* Remove a bolsa de codigo informado. Devolve Erro se nao achar. */
    Resultado remover(const std::string& codigo);

    /*
     * Devolve ponteiro para a bolsa dentro do no, ou nullptr se nao existir.
     * O ponteiro aponta para dentro da lista: deixa de ser valido se aquele
     * no for removido ou se a lista for destruida.
     */
    Bolsa* buscar(const std::string& codigo);

    void listar() const;
    int  tamanho() const { return tamanho_; }
    bool vazia() const   { return primeiro_ == nullptr; }

private:
    struct No {
        Bolsa bolsa;              /* copia da bolsa, armazenada no proprio no */
        No*   proximo = nullptr;  /* nullptr quando este e o ultimo no */
    };

    No* primeiro_ = nullptr;      /* nullptr quando a lista esta vazia */
    No* ultimo_   = nullptr;      /* nullptr quando a lista esta vazia */
    int tamanho_  = 0;
};

#endif /* LISTA_ESTOQUE_HPP */
