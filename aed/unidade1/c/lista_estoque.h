#ifndef LISTA_ESTOQUE_H
#define LISTA_ESTOQUE_H

#include "dominio.h"

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
 */

typedef struct NoEstoque {
    Bolsa bolsa;                  /* copia da bolsa, armazenada no proprio no */
    struct NoEstoque *proximo;    /* NULL quando este e o ultimo no */
} NoEstoque;

typedef struct {
    NoEstoque *primeiro;          /* NULL quando a lista esta vazia */
    NoEstoque *ultimo;            /* NULL quando a lista esta vazia */
    int tamanho;
} ListaEstoque;

/* Devolve a lista vazia alocada, ou NULL se o malloc falhar. */
ListaEstoque* lista_criar(void);

/* Insere no FIM da lista. Devolve RV_OK ou RV_ERRO. */
int lista_inserir(ListaEstoque *lista, Bolsa bolsa);

/* Remove a bolsa de codigo informado. Devolve RV_OK, ou RV_ERRO se nao achar. */
int lista_remover(ListaEstoque *lista, const char *codigo);

/*
 * Devolve ponteiro para a bolsa dentro do no, ou NULL se nao existir.
 * O ponteiro aponta para dentro da lista: deixa de ser valido se aquele no
 * for removido ou se a lista for destruida.
 */
Bolsa* lista_buscar(ListaEstoque *lista, const char *codigo);

void lista_listar(const ListaEstoque *lista);
int  lista_tamanho(const ListaEstoque *lista);
int  lista_vazia(const ListaEstoque *lista);

/* Libera todos os nos e a propria lista. */
void lista_destruir(ListaEstoque *lista);

#endif /* LISTA_ESTOQUE_H */
