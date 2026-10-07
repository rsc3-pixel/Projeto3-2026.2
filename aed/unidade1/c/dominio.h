#ifndef DOMINIO_H
#define DOMINIO_H

#include <stddef.h>
#include <string.h>

/*
 * Versoes simplificadas das entidades de dominio do Rota Vital.
 *
 * Sao apenas dados: nada de JPA, nada de comportamento. As datas ficam como
 * texto no formato ISO ("AAAA-MM-DD") porque C nao tem tipo de data na
 * biblioteca padrao, e texto ISO ordena corretamente na comparacao lexical,
 * caso isso seja necessario mais adiante.
 *
 * Todos os campos de texto sao vetores de tamanho fixo dentro da struct, nao
 * ponteiros. Isso e deliberado: a struct inteira pode ser copiada por valor
 * para dentro de um no da lista, da fila ou da pilha, sem que seja preciso
 * alocar memoria separada para cada string nem controlar o tempo de vida
 * dela. A unica memoria dinamica do programa e a dos nos.
 */

typedef struct {
    char codigo_rastreio[16];
    char grupo_sanguineo[4];      /* "A+", "O-" */
    char componente[32];          /* "CONCENTRADO_HEMACIAS" */
    int  volume_ml;
    char data_coleta[11];         /* "AAAA-MM-DD" */
    char data_validade[11];
    char status[16];              /* "DISPONIVEL" */
} Bolsa;

typedef struct {
    char id[16];
    char hospital[64];
    char prioridade[16];          /* "ROTINA", "URGENTE", "EMERGENCIA" */
    char grupo_sanguineo[4];
    char componente[32];
    int  quantidade;
    char data_hora[20];
} Requisicao;

typedef struct {
    char id[16];
    char operacao[32];            /* "ENTRADA_BOLSA", "SAIDA_BOLSA", ... */
    char descricao[96];
    char data_hora[20];
} Operacao;

/*
 * Codigos de retorno usados por todas as operacoes que podem falhar.
 * Convencao unica no projeto para que o chamador nunca precise lembrar se
 * zero significa sucesso ou falha.
 */
#define RV_OK   1
#define RV_ERRO 0

/*
 * Copia segura de texto para campo de tamanho fixo.
 *
 * Existe para que nenhum arquivo precise chamar strcpy, que nao conhece o
 * tamanho do destino e sobrescreve o que vem depois dele na memoria. O
 * strncpy sozinho tambem nao basta: quando a origem e maior ou igual ao
 * limite, ele preenche todo o destino e NAO coloca o terminador, deixando
 * uma string sem fim que faria printf e strcmp lerem memoria vizinha. Por
 * isso o terminador e gravado na mao na ultima posicao, sempre.
 *
 * Fica como static inline no cabecalho para ser compartilhada pelos tres
 * modulos sem exigir um arquivo .c proprio.
 */
static inline void copiar_texto(char *destino, size_t tamanho_destino,
                                const char *origem) {
    if (destino == NULL || tamanho_destino == 0) {
        return;
    }
    if (origem == NULL) {
        destino[0] = '\0';
        return;
    }
    strncpy(destino, origem, tamanho_destino - 1);
    destino[tamanho_destino - 1] = '\0';
}

#endif /* DOMINIO_H */
