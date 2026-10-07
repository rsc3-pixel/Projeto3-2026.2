#include "fila_requisicoes.h"

#include <stdio.h>
#include <stdlib.h>

FilaRequisicoes* fila_criar(void) {
    FilaRequisicoes *fila = (FilaRequisicoes *) malloc(sizeof(FilaRequisicoes));
    if (fila == NULL) {
        return NULL;
    }

    /* Fila vazia: nao ha de onde sair nem por onde entrar. */
    fila->frente = NULL;
    fila->tras = NULL;
    fila->tamanho = 0;
    return fila;
}

int fila_enfileirar(FilaRequisicoes *fila, Requisicao requisicao) {
    if (fila == NULL) {
        return RV_ERRO;
    }

    NoFila *novo = (NoFila *) malloc(sizeof(NoFila));
    if (novo == NULL) {
        return RV_ERRO;
    }

    novo->requisicao = requisicao;
    novo->proximo = NULL;     /* entra no fim, logo nada vem depois dele */

    if (fila->tras == NULL) {
        /*
         * Fila vazia: o no novo e simultaneamente a frente e o tras.
         * Checar 'tras' em vez de 'frente' aqui e proposital: e o ponteiro
         * que a insercao precisa manipular.
         */
        fila->frente = novo;
        fila->tras = novo;
    } else {
        /*
         * Fila com elementos: liga o ultimo no ao novo e so depois move o
         * ponteiro 'tras'. Invertendo a ordem, o antigo ultimo ficaria
         * desconectado e os nos anteriores se perderiam.
         */
        fila->tras->proximo = novo;
        fila->tras = novo;
    }

    fila->tamanho++;
    return RV_OK;
}

int fila_desenfileirar(FilaRequisicoes *fila, Requisicao *saida) {
    if (fila == NULL || fila->frente == NULL) {
        return RV_ERRO;   /* fila inexistente ou vazia */
    }

    NoFila *removido = fila->frente;

    if (saida != NULL) {
        /* Copia antes do free: depois dele o conteudo do no nao existe mais. */
        *saida = removido->requisicao;
    }

    /* A frente avanca para o no seguinte, que pode ser NULL. */
    fila->frente = removido->proximo;

    if (fila->frente == NULL) {
        /*
         * A fila esvaziou. Sem zerar o 'tras' ele continuaria apontando para
         * o no que esta sendo liberado, e a proxima insercao faria
         * 'tras->proximo = novo' sobre memoria invalida.
         */
        fila->tras = NULL;
    }

    free(removido);
    fila->tamanho--;
    return RV_OK;
}

Requisicao* fila_frente(FilaRequisicoes *fila) {
    if (fila == NULL || fila->frente == NULL) {
        return NULL;
    }
    return &fila->frente->requisicao;
}

void fila_listar(const FilaRequisicoes *fila) {
    if (fila == NULL) {
        printf("  (fila inexistente)\n");
        return;
    }
    if (fila->frente == NULL) {
        printf("  (nenhuma requisicao na fila)\n");
        return;
    }

    int posicao = 1;
    for (const NoFila *atual = fila->frente;
         atual != NULL;
         atual = atual->proximo) {
        printf("  %d. %-8s %-28s %-11s %-3s %-22s qtd:%d  %s\n",
               posicao,
               atual->requisicao.id,
               atual->requisicao.hospital,
               atual->requisicao.prioridade,
               atual->requisicao.grupo_sanguineo,
               atual->requisicao.componente,
               atual->requisicao.quantidade,
               atual->requisicao.data_hora);
        posicao++;
    }
}

int fila_tamanho(const FilaRequisicoes *fila) {
    if (fila == NULL) {
        return 0;
    }
    return fila->tamanho;
}

int fila_vazia(const FilaRequisicoes *fila) {
    if (fila == NULL) {
        return 1;
    }
    return fila->frente == NULL;
}

void fila_destruir(FilaRequisicoes *fila) {
    if (fila == NULL) {
        return;
    }

    NoFila *atual = fila->frente;
    while (atual != NULL) {
        /* Guarda o proximo antes de liberar o no atual. */
        NoFila *proximo = atual->proximo;
        free(atual);
        atual = proximo;
    }

    free(fila);
}
