#include "pilha_historico.h"

#include <stdio.h>
#include <stdlib.h>

PilhaHistorico* pilha_criar(void) {
    PilhaHistorico *pilha = (PilhaHistorico *) malloc(sizeof(PilhaHistorico));
    if (pilha == NULL) {
        return NULL;
    }

    /* Pilha vazia: nao ha topo. */
    pilha->topo = NULL;
    pilha->tamanho = 0;
    return pilha;
}

int pilha_empilhar(PilhaHistorico *pilha, Operacao operacao) {
    if (pilha == NULL) {
        return RV_ERRO;
    }

    NoPilha *novo = (NoPilha *) malloc(sizeof(NoPilha));
    if (novo == NULL) {
        return RV_ERRO;
    }

    novo->operacao = operacao;

    /*
     * O no novo passa a apontar para o antigo topo, e so depois o ponteiro
     * 'topo' da pilha sobe para ele. Nesta ordem o resto da pilha permanece
     * alcancavel durante toda a operacao; na ordem inversa, mover o topo
     * primeiro perderia o endereco de tudo o que estava embaixo.
     *
     * Quando a pilha esta vazia, 'topo' e NULL e o novo no fica com
     * abaixo = NULL, marcando o fundo da pilha. O caso vazio nao precisa de
     * tratamento proprio.
     */
    novo->abaixo = pilha->topo;
    pilha->topo = novo;

    pilha->tamanho++;
    return RV_OK;
}

int pilha_desempilhar(PilhaHistorico *pilha, Operacao *saida) {
    if (pilha == NULL || pilha->topo == NULL) {
        return RV_ERRO;   /* pilha inexistente ou vazia */
    }

    NoPilha *removido = pilha->topo;

    if (saida != NULL) {
        /* Copia antes do free, senao o dado iria embora com o no. */
        *saida = removido->operacao;
    }

    /*
     * O topo desce para o no que estava abaixo. Se era o unico no, 'abaixo'
     * e NULL e a pilha volta ao estado vazio.
     */
    pilha->topo = removido->abaixo;

    free(removido);
    pilha->tamanho--;
    return RV_OK;
}

Operacao* pilha_topo(PilhaHistorico *pilha) {
    if (pilha == NULL || pilha->topo == NULL) {
        return NULL;
    }
    return &pilha->topo->operacao;
}

void pilha_listar(const PilhaHistorico *pilha) {
    if (pilha == NULL) {
        printf("  (pilha inexistente)\n");
        return;
    }
    if (pilha->topo == NULL) {
        printf("  (historico vazio)\n");
        return;
    }

    /* Percorre do topo para o fundo, que e a ordem do mais recente ao mais antigo. */
    int posicao = 1;
    for (const NoPilha *atual = pilha->topo;
         atual != NULL;
         atual = atual->abaixo) {
        printf("  %d. %-8s %-16s %-40s %s\n",
               posicao,
               atual->operacao.id,
               atual->operacao.operacao,
               atual->operacao.descricao,
               atual->operacao.data_hora);
        posicao++;
    }
}

int pilha_tamanho(const PilhaHistorico *pilha) {
    if (pilha == NULL) {
        return 0;
    }
    return pilha->tamanho;
}

int pilha_vazia(const PilhaHistorico *pilha) {
    if (pilha == NULL) {
        return 1;
    }
    return pilha->topo == NULL;
}

void pilha_destruir(PilhaHistorico *pilha) {
    if (pilha == NULL) {
        return;
    }

    NoPilha *atual = pilha->topo;
    while (atual != NULL) {
        /* Guarda o de baixo antes de liberar o atual. */
        NoPilha *abaixo = atual->abaixo;
        free(atual);
        atual = abaixo;
    }

    free(pilha);
}
