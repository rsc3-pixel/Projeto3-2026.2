#ifndef FILA_FEFO_H
#define FILA_FEFO_H

#include "dominio.h"

/*
 * Fila de prioridade FEFO (First Expired, First Out) por INSERCAO ORDENADA.
 *
 * A lista e mantida ordenada o tempo todo: cada bolsa nova e colocada direto
 * na posicao certa, percorrendo a partir do inicio ate achar o ponto de
 * encaixe. Nao existe passo de ordenacao depois - a ordem e invariante da
 * estrutura, e por isso a bolsa que vence primeiro esta sempre no inicio.
 *
 * E a tecnica pedida na Unidade 2 de AED, em contraste com o heap binario
 * usado na Unidade 1. O equivalente em Java e a classe
 * com.rotavital.estruturas.FilaPrioridadeFefo, e a correspondencia entre as
 * duas esta documentada em docs/W10-fefo-insercao-ordenada.md.
 *
 * Criterio de ordem: data de validade crescente e, no empate, codigo de
 * rastreio crescente. O desempate pelo codigo e o que torna o resultado
 * deterministico: duas bolsas de mesma validade sempre resolvem para a mesma
 * escolha, em qualquer ordem de montagem do estoque.
 *
 * Complexidade: adicionar O(n), consultar O(1), retirar O(1),
 * descartar_vencidas O(k) para k vencidas, tamanho O(1).
 */

typedef struct NoFefo {
    Bolsa bolsa;                  /* copia da bolsa, armazenada no proprio no */
    struct NoFefo *proximo;       /* NULL quando este e o ultimo no */
} NoFefo;

typedef struct {
    NoFefo *primeiro;             /* NULL quando a fila esta vazia */
    int tamanho;
} FilaFefo;

/* Devolve a fila vazia alocada, ou NULL se o malloc falhar. */
FilaFefo* fila_criar(void);

/* Insere mantendo a ordem. Devolve RV_OK ou RV_ERRO. */
int fila_adicionar(FilaFefo *fila, Bolsa bolsa);

/*
 * A proxima bolsa a sair, sem retirar. Devolve NULL se a fila estiver vazia:
 * estoque vazio nao e erro, e situacao normal da operacao.
 */
const Bolsa* fila_consultar_proxima(const FilaFefo *fila);

/*
 * Retira a primeira bolsa e copia para 'saida', se 'saida' nao for NULL.
 * Devolve RV_OK, ou RV_ERRO se a fila estiver vazia.
 */
int fila_retirar_proxima(FilaFefo *fila, Bolsa *saida);

/*
 * Remove do inicio todas as bolsas vencidas na data de referencia, copiando
 * as removidas para 'descartadas' ate o limite 'capacidade'.
 *
 * Como a lista e ordenada por validade, as vencidas estao todas no inicio:
 * o laco para na primeira valida, sem percorrer o resto.
 *
 * A bolsa cuja validade e exatamente a data de referencia NAO e descartada -
 * ela so vence no dia seguinte.
 *
 * Devolve a quantidade descartada, ou -1 se a data for nula.
 */
int fila_descartar_vencidas(FilaFefo *fila, const char *data_referencia,
                            Bolsa *descartadas, int capacidade);

void fila_listar(const FilaFefo *fila);
int  fila_tamanho(const FilaFefo *fila);
int  fila_vazia(const FilaFefo *fila);

/* Libera todos os nos e a propria fila. */
void fila_destruir(FilaFefo *fila);

#endif /* FILA_FEFO_H */
