#include "lista_estoque.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

ListaEstoque* lista_criar(void) {
    ListaEstoque *lista = (ListaEstoque *) malloc(sizeof(ListaEstoque));
    if (lista == NULL) {
        /* Sem memoria: devolve NULL e deixa o chamador decidir o que fazer. */
        return NULL;
    }

    /* Lista nasce vazia: nenhum no, logo os dois ponteiros apontam para NULL. */
    lista->primeiro = NULL;
    lista->ultimo = NULL;
    lista->tamanho = 0;
    return lista;
}

int lista_inserir(ListaEstoque *lista, Bolsa bolsa) {
    if (lista == NULL) {
        return RV_ERRO;
    }

    NoEstoque *novo = (NoEstoque *) malloc(sizeof(NoEstoque));
    if (novo == NULL) {
        /* Falha de alocacao nao derruba o programa: a lista fica intacta. */
        return RV_ERRO;
    }

    novo->bolsa = bolsa;      /* copia da struct inteira para dentro do no */
    novo->proximo = NULL;     /* vai entrar no fim, entao nao tem proximo */

    if (lista->ultimo == NULL) {
        /*
         * Lista vazia: o no novo e, ao mesmo tempo, o primeiro e o ultimo.
         * Os dois ponteiros da lista passam a apontar para ele.
         */
        lista->primeiro = novo;
        lista->ultimo = novo;
    } else {
        /*
         * Lista com elementos: o antigo ultimo passa a apontar para o novo,
         * e so depois o ponteiro 'ultimo' da lista avanca. Nesta ordem, em
         * nenhum instante a cadeia fica interrompida.
         */
        lista->ultimo->proximo = novo;
        lista->ultimo = novo;
    }

    lista->tamanho++;
    return RV_OK;
}

int lista_remover(ListaEstoque *lista, const char *codigo) {
    if (lista == NULL || codigo == NULL) {
        return RV_ERRO;
    }

    /*
     * Percorre guardando o no anterior, porque em lista simplesmente
     * encadeada nao ha como voltar: para desligar um no do meio e preciso
     * ter em maos quem aponta para ele.
     */
    NoEstoque *atual = lista->primeiro;
    NoEstoque *anterior = NULL;

    while (atual != NULL
           && strcmp(atual->bolsa.codigo_rastreio, codigo) != 0) {
        anterior = atual;
        atual = atual->proximo;
    }

    if (atual == NULL) {
        return RV_ERRO;   /* codigo nao esta na lista */
    }

    if (anterior == NULL) {
        /*
         * CASO 1 - primeiro no: nao existe anterior para religar, entao o
         * inicio da lista passa a ser o no seguinte.
         */
        lista->primeiro = atual->proximo;
    } else {
        /*
         * CASO 2 - no do meio ou no final: o anterior passa a apontar para
         * o proximo do removido, saltando por cima dele e fechando a cadeia.
         */
        anterior->proximo = atual->proximo;
    }

    if (atual == lista->ultimo) {
        /*
         * CASO 3 - era o ultimo no: o ponteiro 'ultimo' precisa recuar para o
         * anterior, senao continuaria apontando para memoria liberada e a
         * proxima insercao escreveria em cima dela.
         *
         * Quando o removido era o unico no, 'anterior' e NULL e 'ultimo' fica
         * NULL, que e exatamente o estado de lista vazia.
         */
        lista->ultimo = anterior;
    }

    free(atual);        /* libera o no, nunca a lista */
    lista->tamanho--;
    return RV_OK;
}

Bolsa* lista_buscar(ListaEstoque *lista, const char *codigo) {
    if (lista == NULL || codigo == NULL) {
        return NULL;
    }

    /* Busca sequencial: O(n). Indexar por codigo seria tabela hash, Unidade 2. */
    for (NoEstoque *atual = lista->primeiro;
         atual != NULL;
         atual = atual->proximo) {
        if (strcmp(atual->bolsa.codigo_rastreio, codigo) == 0) {
            /* Devolve o endereco da bolsa dentro do no, sem copiar. */
            return &atual->bolsa;
        }
    }

    return NULL;
}

void lista_listar(const ListaEstoque *lista) {
    if (lista == NULL) {
        printf("  (lista inexistente)\n");
        return;
    }
    if (lista->primeiro == NULL) {
        printf("  (estoque vazio)\n");
        return;
    }

    int posicao = 1;
    for (const NoEstoque *atual = lista->primeiro;
         atual != NULL;
         atual = atual->proximo) {
        printf("  %d. %-8s %-3s %-22s %4d ml  coleta:%s  validade:%s  %s\n",
               posicao,
               atual->bolsa.codigo_rastreio,
               atual->bolsa.grupo_sanguineo,
               atual->bolsa.componente,
               atual->bolsa.volume_ml,
               atual->bolsa.data_coleta,
               atual->bolsa.data_validade,
               atual->bolsa.status);
        posicao++;
    }
}

int lista_tamanho(const ListaEstoque *lista) {
    if (lista == NULL) {
        return 0;
    }
    return lista->tamanho;
}

int lista_vazia(const ListaEstoque *lista) {
    if (lista == NULL) {
        return 1;   /* lista que nao existe nao tem elementos */
    }
    return lista->primeiro == NULL;
}

void lista_destruir(ListaEstoque *lista) {
    if (lista == NULL) {
        return;
    }

    NoEstoque *atual = lista->primeiro;
    while (atual != NULL) {
        /*
         * Guarda o proximo ANTES do free. Depois de liberar o no, ler
         * atual->proximo seria acesso a memoria ja devolvida ao sistema.
         */
        NoEstoque *proximo = atual->proximo;
        free(atual);
        atual = proximo;
    }

    /* Todos os nos liberados; por fim libera a estrutura da propria lista. */
    free(lista);
}
