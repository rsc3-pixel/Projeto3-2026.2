#ifndef PILHA_HISTORICO_H
#define PILHA_HISTORICO_H

#include "dominio.h"

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

typedef struct NoPilha {
    Operacao operacao;          /* copia da operacao dentro do no */
    struct NoPilha *abaixo;     /* no que estava no topo antes deste */
} NoPilha;

typedef struct {
    NoPilha *topo;              /* NULL quando a pilha esta vazia */
    int tamanho;
} PilhaHistorico;

/* Devolve a pilha vazia alocada, ou NULL se o malloc falhar. */
PilhaHistorico* pilha_criar(void);

/* Empilha no topo. Devolve RV_OK ou RV_ERRO. */
int pilha_empilhar(PilhaHistorico *pilha, Operacao operacao);

/*
 * Retira do topo e copia a operacao removida para 'saida', se nao for NULL.
 * Copia em vez de devolver ponteiro porque o no e liberado em seguida.
 *
 * Devolve RV_OK, ou RV_ERRO se a pilha estiver vazia.
 */
int pilha_desempilhar(PilhaHistorico *pilha, Operacao *saida);

/*
 * Consulta a operacao do topo sem retirar. Devolve NULL se vazia.
 * O ponteiro aponta para dentro do no e deixa de valer quando ele sai.
 */
Operacao* pilha_topo(PilhaHistorico *pilha);

void pilha_listar(const PilhaHistorico *pilha);
int  pilha_tamanho(const PilhaHistorico *pilha);
int  pilha_vazia(const PilhaHistorico *pilha);

/* Libera todos os nos e a propria pilha. */
void pilha_destruir(PilhaHistorico *pilha);

#endif /* PILHA_HISTORICO_H */
