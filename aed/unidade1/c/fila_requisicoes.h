#ifndef FILA_REQUISICOES_H
#define FILA_REQUISICOES_H

#include "dominio.h"

/*
 * Fila FIFO (First In, First Out) de requisicoes de hemocomponentes.
 *
 * FIFO e a ordem de atendimento por chegada: a requisicao que entrou primeiro
 * e atendida primeiro. Nesta unidade a prioridade da requisicao e apenas um
 * campo informativo, impresso junto com os dados; usa-la para reordenar o
 * atendimento exigiria fila de prioridade, que e Unidade 2.
 *
 * Dois ponteiros, 'frente' e 'tras', para que as duas pontas custem O(1):
 * entra pelo tras, sai pela frente. Com apenas um deles, uma das operacoes
 * teria de percorrer a fila inteira.
 */

typedef struct NoFila {
    Requisicao requisicao;      /* copia da requisicao dentro do no */
    struct NoFila *proximo;     /* NULL quando este e o ultimo da fila */
} NoFila;

typedef struct {
    NoFila *frente;             /* de onde sai: NULL se a fila esta vazia */
    NoFila *tras;               /* por onde entra: NULL se a fila esta vazia */
    int tamanho;
} FilaRequisicoes;

/* Devolve a fila vazia alocada, ou NULL se o malloc falhar. */
FilaRequisicoes* fila_criar(void);

/* Insere no fim (tras). Devolve RV_OK ou RV_ERRO. */
int fila_enfileirar(FilaRequisicoes *fila, Requisicao requisicao);

/*
 * Remove da frente e copia a requisicao removida para 'saida', se 'saida' nao
 * for NULL. A copia e necessaria porque o no e liberado em seguida: devolver
 * um ponteiro para ele entregaria endereco invalido.
 *
 * Devolve RV_OK, ou RV_ERRO se a fila estiver vazia.
 */
int fila_desenfileirar(FilaRequisicoes *fila, Requisicao *saida);

/*
 * Consulta quem sera atendido sem retirar da fila. Devolve NULL se vazia.
 * O ponteiro aponta para dentro do no e deixa de valer quando ele sai.
 */
Requisicao* fila_frente(FilaRequisicoes *fila);

void fila_listar(const FilaRequisicoes *fila);
int  fila_tamanho(const FilaRequisicoes *fila);
int  fila_vazia(const FilaRequisicoes *fila);

/* Libera todos os nos e a propria fila. */
void fila_destruir(FilaRequisicoes *fila);

#endif /* FILA_REQUISICOES_H */
