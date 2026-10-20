#ifndef DOMINIO_H
#define DOMINIO_H

#include <stddef.h>
#include <string.h>

/*
 * Versao simplificada da Bolsa do Rota Vital, igual a usada na Unidade 1
 * (aed/unidade1/c/dominio.h), repetida aqui para que a Unidade 2 compile
 * sozinha.
 *
 * A data fica como texto no formato ISO "AAAA-MM-DD" porque C nao tem tipo de
 * data na biblioteca padrao. O formato nao e detalhe de exibicao: texto ISO
 * ordena corretamente em comparacao lexical, entao strcmp sobre duas datas
 * responde a mesma pergunta que compareTo sobre dois LocalDate no Java. E
 * disso que a insercao ordenada por validade depende.
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

/* Convencao unica de retorno, a mesma da Unidade 1. */
#define RV_OK   1
#define RV_ERRO 0

/*
 * Copia segura de texto para campo de tamanho fixo. Existe para que nenhum
 * arquivo precise chamar strcpy, que nao conhece o tamanho do destino. O
 * strncpy sozinho tambem nao basta: quando a origem enche o destino ele nao
 * grava o terminador, entao o terminador e escrito na mao, sempre.
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
