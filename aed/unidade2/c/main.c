/*
 * Rota Vital - AED Unidade 2
 *
 * Demonstracao da fila FEFO por insercao ordenada: a bolsa que vence primeiro
 * esta sempre no inicio, sem nenhum passo de ordenacao depois.
 */

#include "fila_fefo.h"

#include <stdio.h>

static Bolsa montar(const char *codigo, const char *grupo, const char *validade) {
    Bolsa bolsa;
    copiar_texto(bolsa.codigo_rastreio, sizeof bolsa.codigo_rastreio, codigo);
    copiar_texto(bolsa.grupo_sanguineo, sizeof bolsa.grupo_sanguineo, grupo);
    copiar_texto(bolsa.componente, sizeof bolsa.componente, "CONCENTRADO_HEMACIAS");
    bolsa.volume_ml = 450;
    copiar_texto(bolsa.data_coleta, sizeof bolsa.data_coleta, "2026-08-01");
    copiar_texto(bolsa.data_validade, sizeof bolsa.data_validade, validade);
    copiar_texto(bolsa.status, sizeof bolsa.status, "DISPONIVEL");
    return bolsa;
}

static void etapa(const char *titulo) {
    printf("\n----------------------------------------------------------------\n");
    printf(" %s\n", titulo);
    printf("----------------------------------------------------------------\n");
}

int main(void) {
    printf("\n  ROTA VITAL - AED Unidade 2: FEFO por insercao ordenada\n");

    FilaFefo *fila = fila_criar();
    if (fila == NULL) {
        printf("  ERRO: memoria insuficiente\n");
        return 1;
    }

    etapa("1. Insercao fora de ordem (a fila se mantem ordenada)");
    /* Entram embaralhadas de proposito; cada uma cai na posicao certa. */
    fila_adicionar(fila, montar("B003", "O+", "2026-10-20"));  /* fim    */
    fila_adicionar(fila, montar("B001", "O-", "2026-09-05"));  /* inicio */
    fila_adicionar(fila, montar("B004", "A+", "2026-12-01"));  /* fim    */
    fila_adicionar(fila, montar("B002", "B-", "2026-10-01"));  /* meio   */
    fila_listar(fila);
    printf("  tamanho: %d\n", fila_tamanho(fila));

    etapa("2. Empate de validade resolvido pelo codigo de rastreio");
    fila_adicionar(fila, montar("B006", "A-", "2026-10-01"));
    fila_adicionar(fila, montar("B005", "AB+", "2026-10-01"));
    fila_listar(fila);
    printf("  as tres de 2026-10-01 saem na ordem B002, B005, B006\n");

    etapa("3. Proxima a sair (consulta, sem retirar)");
    const Bolsa *proxima = fila_consultar_proxima(fila);
    printf("  %s, validade %s\n", proxima->codigo_rastreio, proxima->data_validade);
    printf("  tamanho segue: %d\n", fila_tamanho(fila));

    etapa("4. Descarte das vencidas em 2026-10-01");
    /*
     * A B001 venceu em 2026-09-05. A B002, B005 e B006 vencem exatamente na
     * data de referencia: NAO sao descartadas, porque so vencem no dia
     * seguinte.
     */
    Bolsa descartadas[8];
    int quantidade = fila_descartar_vencidas(fila, "2026-10-01", descartadas, 8);
    printf("  descartadas: %d\n", quantidade);
    for (int i = 0; i < quantidade; i++) {
        printf("    %s (validade %s)\n",
               descartadas[i].codigo_rastreio, descartadas[i].data_validade);
    }
    printf("  restantes:\n");
    fila_listar(fila);

    etapa("5. Retirada ate esvaziar");
    Bolsa retirada;
    while (fila_retirar_proxima(fila, &retirada) == RV_OK) {
        printf("  sai %s (validade %s)\n",
               retirada.codigo_rastreio, retirada.data_validade);
    }
    printf("  vazia: %s, tamanho: %d\n",
           fila_vazia(fila) ? "sim" : "nao", fila_tamanho(fila));
    printf("  consultar em fila vazia devolve NULL: %s\n",
           fila_consultar_proxima(fila) == NULL ? "sim" : "nao");

    etapa("6. Liberacao da memoria");
    fila_destruir(fila);
    fila = NULL;
    printf("  fila destruida: nos e descritor liberados\n\n");

    return 0;
}
