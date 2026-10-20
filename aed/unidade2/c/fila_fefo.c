#include "fila_fefo.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/*
 * Decide se 'a' deve sair antes de 'b': primeiro a validade mais proxima e,
 * no empate, o menor codigo de rastreio.
 *
 * A comparacao e explicita, campo a campo. As datas estao no formato ISO
 * "AAAA-MM-DD", entao strcmp responde a mesma pergunta que uma comparacao de
 * datas: texto ISO ordena na mesma ordem do calendario.
 */
static int vem_antes(const Bolsa *a, const Bolsa *b) {
    int comparacao_de_validade = strcmp(a->data_validade, b->data_validade);
    if (comparacao_de_validade != 0) {
        return comparacao_de_validade < 0;
    }
    return strcmp(a->codigo_rastreio, b->codigo_rastreio) < 0;
}

/* Vencida quando a data de referencia e POSTERIOR a validade. */
static int esta_vencida(const Bolsa *bolsa, const char *data_referencia) {
    return strcmp(data_referencia, bolsa->data_validade) > 0;
}

FilaFefo* fila_criar(void) {
    FilaFefo *fila = (FilaFefo *) malloc(sizeof(FilaFefo));
    if (fila == NULL) {
        return NULL;
    }
    fila->primeiro = NULL;
    fila->tamanho = 0;
    return fila;
}

int fila_adicionar(FilaFefo *fila, Bolsa bolsa) {
    if (fila == NULL) {
        return RV_ERRO;
    }

    NoFefo *novo = (NoFefo *) malloc(sizeof(NoFefo));
    if (novo == NULL) {
        return RV_ERRO;
    }
    novo->bolsa = bolsa;
    novo->proximo = NULL;

    if (fila->primeiro == NULL) {
        /* CASO 1 - fila vazia: o no novo e o unico, e tambem o primeiro. */
        fila->primeiro = novo;

    } else if (vem_antes(&bolsa, &fila->primeiro->bolsa)) {
        /*
         * CASO 2 - a nova bolsa vence antes da que esta no inicio: ela passa
         * a ser a primeira. O antigo primeiro e religado atras dela ANTES de
         * o ponteiro da fila mudar; na ordem inversa o resto da cadeia
         * ficaria inalcancavel e vazaria.
         */
        novo->proximo = fila->primeiro;
        fila->primeiro = novo;

    } else {
        /*
         * CASO 3 - meio ou fim: percorre com dois ponteiros, porque em lista
         * simplesmente encadeada nao ha como voltar para religar.
         *
         * A condicao avanca enquanto a bolsa de 'atual' ainda vem antes da
         * nova. Usando vem_antes(atual, nova) - e nao a negacao de
         * vem_antes(nova, atual) - o laco passa tambem pelos empates, e a
         * bolsa nova acaba depois das iguais: e o que torna a insercao
         * estavel.
         */
        NoFefo *anterior = fila->primeiro;
        NoFefo *atual = fila->primeiro->proximo;

        while (atual != NULL && vem_antes(&atual->bolsa, &bolsa)) {
            anterior = atual;
            atual = atual->proximo;
        }

        /*
         * Encaixa entre 'anterior' e 'atual'. Quando 'atual' e NULL, o no
         * entra no fim da lista: os dois casos se resolvem com as mesmas duas
         * linhas.
         */
        novo->proximo = atual;
        anterior->proximo = novo;
    }

    fila->tamanho++;
    return RV_OK;
}

const Bolsa* fila_consultar_proxima(const FilaFefo *fila) {
    if (fila == NULL || fila->primeiro == NULL) {
        return NULL;
    }
    return &fila->primeiro->bolsa;
}

int fila_retirar_proxima(FilaFefo *fila, Bolsa *saida) {
    if (fila == NULL || fila->primeiro == NULL) {
        return RV_ERRO;
    }

    NoFefo *removido = fila->primeiro;

    if (saida != NULL) {
        /* Copia antes do free: depois dele o conteudo do no nao existe mais. */
        *saida = removido->bolsa;
    }

    /*
     * Como a lista ja esta ordenada, retirar e apenas avancar o ponteiro do
     * inicio: nao ha reorganizacao, diferente do heap, que precisa
     * reequilibrar a arvore depois de remover a raiz.
     */
    fila->primeiro = removido->proximo;

    free(removido);
    fila->tamanho--;
    return RV_OK;
}

int fila_descartar_vencidas(FilaFefo *fila, const char *data_referencia,
                            Bolsa *descartadas, int capacidade) {
    if (fila == NULL || data_referencia == NULL) {
        return -1;
    }

    int quantidade = 0;

    /*
     * Para na primeira valida. A lista e ordenada por validade, entao toda
     * bolsa depois dela vence ainda mais tarde: nao ha o que procurar no
     * resto. No heap isso nao seria possivel, porque a ordem so vale para a
     * raiz e descobrir quais venceram exigiria varrer a arvore inteira.
     */
    while (fila->primeiro != NULL
           && esta_vencida(&fila->primeiro->bolsa, data_referencia)) {

        Bolsa removida;
        fila_retirar_proxima(fila, &removida);

        if (descartadas != NULL && quantidade < capacidade) {
            descartadas[quantidade] = removida;
        }
        quantidade++;
    }

    return quantidade;
}

void fila_listar(const FilaFefo *fila) {
    if (fila == NULL || fila->primeiro == NULL) {
        printf("  (fila vazia)\n");
        return;
    }

    int posicao = 1;
    for (const NoFefo *atual = fila->primeiro; atual != NULL; atual = atual->proximo) {
        printf("  %d. %-8s %-3s validade:%s  %s\n",
               posicao,
               atual->bolsa.codigo_rastreio,
               atual->bolsa.grupo_sanguineo,
               atual->bolsa.data_validade,
               atual->bolsa.status);
        posicao++;
    }
}

int fila_tamanho(const FilaFefo *fila) {
    return fila == NULL ? 0 : fila->tamanho;
}

int fila_vazia(const FilaFefo *fila) {
    return fila == NULL || fila->primeiro == NULL;
}

void fila_destruir(FilaFefo *fila) {
    if (fila == NULL) {
        return;
    }

    NoFefo *atual = fila->primeiro;
    while (atual != NULL) {
        /* Guarda o proximo ANTES do free: depois o no nao existe mais. */
        NoFefo *proximo = atual->proximo;
        free(atual);
        atual = proximo;
    }

    free(fila);
}
