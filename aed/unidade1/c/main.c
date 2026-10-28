/*
 * Rota Vital - AED Unidade 1
 *
 * Demonstracao das tres estruturas basicas construidas a mao, com alocacao
 * manual de memoria: lista encadeada de estoque, fila FIFO de requisicoes e
 * pilha LIFO de historico.
 *
 * Os dados sao sinteticos e seguem o dominio da aplicacao Java do projeto
 * (Bolsa, Requisicao e as operacoes de estoque).
 */

#include "dominio.h"
#include "lista_estoque.h"
#include "fila_requisicoes.h"
#include "pilha_historico.h"

#include <stdio.h>

static void etapa(int numero, const char *titulo) {
    printf("\n");
    printf("========================================"
           "========================================\n");
    printf(" ETAPA %d - %s\n", numero, titulo);
    printf("========================================"
           "========================================\n");
}

static void subtitulo(const char *texto) {
    printf("\n-- %s\n", texto);
}

/* Monta uma Bolsa campo por campo, sempre com copia segura de texto. */
static Bolsa montar_bolsa(const char *codigo, const char *grupo,
                          const char *componente, int volume_ml,
                          const char *coleta, const char *validade,
                          const char *status) {
    Bolsa bolsa;
    copiar_texto(bolsa.codigo_rastreio, sizeof bolsa.codigo_rastreio, codigo);
    copiar_texto(bolsa.grupo_sanguineo, sizeof bolsa.grupo_sanguineo, grupo);
    copiar_texto(bolsa.componente, sizeof bolsa.componente, componente);
    bolsa.volume_ml = volume_ml;
    copiar_texto(bolsa.data_coleta, sizeof bolsa.data_coleta, coleta);
    copiar_texto(bolsa.data_validade, sizeof bolsa.data_validade, validade);
    copiar_texto(bolsa.status, sizeof bolsa.status, status);
    return bolsa;
}

static Requisicao montar_requisicao(const char *id, const char *hospital,
                                    const char *prioridade, const char *grupo,
                                    const char *componente, int quantidade,
                                    const char *data_hora) {
    Requisicao requisicao;
    copiar_texto(requisicao.id, sizeof requisicao.id, id);
    copiar_texto(requisicao.hospital, sizeof requisicao.hospital, hospital);
    copiar_texto(requisicao.prioridade, sizeof requisicao.prioridade, prioridade);
    copiar_texto(requisicao.grupo_sanguineo, sizeof requisicao.grupo_sanguineo, grupo);
    copiar_texto(requisicao.componente, sizeof requisicao.componente, componente);
    requisicao.quantidade = quantidade;
    copiar_texto(requisicao.data_hora, sizeof requisicao.data_hora, data_hora);
    return requisicao;
}

static Operacao montar_operacao(const char *id, const char *tipo,
                                const char *descricao, const char *data_hora) {
    Operacao operacao;
    copiar_texto(operacao.id, sizeof operacao.id, id);
    copiar_texto(operacao.operacao, sizeof operacao.operacao, tipo);
    copiar_texto(operacao.descricao, sizeof operacao.descricao, descricao);
    copiar_texto(operacao.data_hora, sizeof operacao.data_hora, data_hora);
    return operacao;
}

static void mostrar_busca(ListaEstoque *estoque, const char *codigo) {
    Bolsa *encontrada = lista_buscar(estoque, codigo);
    if (encontrada == NULL) {
        printf("  buscar(\"%s\") -> NAO ENCONTRADA\n", codigo);
    } else {
        printf("  buscar(\"%s\") -> %s %s, %d ml, validade %s\n",
               codigo,
               encontrada->codigo_rastreio,
               encontrada->grupo_sanguineo,
               encontrada->volume_ml,
               encontrada->data_validade);
    }
}

static void remover_e_mostrar(ListaEstoque *estoque, const char *codigo,
                              const char *qual_caso) {
    printf("\n  removendo %s (%s)\n", codigo, qual_caso);
    if (lista_remover(estoque, codigo) == RV_OK) {
        printf("  removida. tamanho agora: %d\n", lista_tamanho(estoque));
    } else {
        printf("  falhou: codigo nao encontrado\n");
    }
    lista_listar(estoque);
}

int main(void) {
    printf("\n");
    printf("  ROTA VITAL - AED Unidade 1\n");
    printf("  Lista encadeada, fila FIFO e pilha LIFO com alocacao manual\n");

    /* ------------------------------------------------------------------ */
    etapa(1, "Criacao das tres estruturas");

    ListaEstoque *estoque = lista_criar();
    FilaRequisicoes *fila = fila_criar();
    PilhaHistorico *historico = pilha_criar();

    if (estoque == NULL || fila == NULL || historico == NULL) {
        /*
         * Se qualquer malloc falhou, libera o que porventura foi alocado e
         * sai com erro. Chamar destruir com NULL e seguro por contrato.
         */
        printf("  ERRO: memoria insuficiente para criar as estruturas\n");
        lista_destruir(estoque);
        fila_destruir(fila);
        pilha_destruir(historico);
        return 1;
    }

    printf("  estoque   criado - vazio: %s, tamanho: %d\n",
           lista_vazia(estoque) ? "sim" : "nao", lista_tamanho(estoque));
    printf("  fila      criada - vazia: %s, tamanho: %d\n",
           fila_vazia(fila) ? "sim" : "nao", fila_tamanho(fila));
    printf("  historico criado - vazio: %s, tamanho: %d\n",
           pilha_vazia(historico) ? "sim" : "nao", pilha_tamanho(historico));

    /* ------------------------------------------------------------------ */
    etapa(2, "Estoque: insercao, listagem e busca");

    lista_inserir(estoque, montar_bolsa("BS0001", "O-", "CONCENTRADO_HEMACIAS",
                                        450, "2026-09-20", "2026-11-01", "DISPONIVEL"));
    lista_inserir(estoque, montar_bolsa("BS0002", "A+", "CONCENTRADO_PLAQUETAS",
                                        300, "2026-10-03", "2026-10-08", "DISPONIVEL"));
    lista_inserir(estoque, montar_bolsa("BS0003", "O+", "CONCENTRADO_HEMACIAS",
                                        450, "2026-09-28", "2026-11-09", "DISPONIVEL"));
    lista_inserir(estoque, montar_bolsa("BS0004", "AB-", "PLASMA_FRESCO_CONGELADO",
                                        600, "2026-08-15", "2027-08-15", "DISPONIVEL"));
    lista_inserir(estoque, montar_bolsa("BS0005", "B+", "CRIOPRECIPITADO",
                                        250, "2026-09-01", "2027-09-01", "RESERVADA"));

    subtitulo("estoque apos 5 insercoes no fim da lista");
    lista_listar(estoque);
    printf("  tamanho: %d\n", lista_tamanho(estoque));

    subtitulo("buscas");
    mostrar_busca(estoque, "BS0003");   /* existe */
    mostrar_busca(estoque, "BS9999");   /* nao existe */

    /* ------------------------------------------------------------------ */
    etapa(3, "Estoque: remocao nos tres casos de ponteiro");

    /* Os tres casos que a remocao em lista encadeada precisa tratar. */
    remover_e_mostrar(estoque, "BS0001", "PRIMEIRO no - move o ponteiro primeiro");
    remover_e_mostrar(estoque, "BS0003", "no do MEIO - religa o anterior ao proximo");
    remover_e_mostrar(estoque, "BS0005", "ULTIMO no - recua o ponteiro ultimo");

    subtitulo("tentativa de remover codigo inexistente");
    remover_e_mostrar(estoque, "BS9999", "nao esta na lista");

    /* ------------------------------------------------------------------ */
    etapa(4, "Fila de requisicoes (FIFO)");

    fila_enfileirar(fila, montar_requisicao("RQ001", "Real Hospital Portugues",
                                            "EMERGENCIA", "O-", "CONCENTRADO_HEMACIAS",
                                            4, "2026-10-07 08:15"));
    fila_enfileirar(fila, montar_requisicao("RQ002", "IMIP",
                                            "URGENTE", "A+", "CONCENTRADO_PLAQUETAS",
                                            2, "2026-10-07 08:40"));
    fila_enfileirar(fila, montar_requisicao("RQ003", "Hospital das Clinicas UFPE",
                                            "ROTINA", "O+", "CONCENTRADO_HEMACIAS",
                                            6, "2026-10-07 09:05"));
    fila_enfileirar(fila, montar_requisicao("RQ004", "Hospital do Tricentenario",
                                            "URGENTE", "AB-", "PLASMA_FRESCO_CONGELADO",
                                            1, "2026-10-07 09:30"));

    subtitulo("fila apos 4 chegadas");
    fila_listar(fila);
    printf("  tamanho: %d\n", fila_tamanho(fila));

    subtitulo("quem esta na frente (consulta, sem retirar)");
    Requisicao *proxima = fila_frente(fila);
    if (proxima != NULL) {
        printf("  %s - %s (%s)\n",
               proxima->id, proxima->hospital, proxima->prioridade);
    }
    printf("  tamanho segue: %d\n", fila_tamanho(fila));

    subtitulo("atendendo 2 requisicoes (desenfileirar)");
    for (int i = 0; i < 2; i++) {
        Requisicao atendida;
        if (fila_desenfileirar(fila, &atendida) == RV_OK) {
            printf("  atendida: %s - %s (%s), %d bolsa(s) de %s\n",
                   atendida.id, atendida.hospital, atendida.prioridade,
                   atendida.quantidade, atendida.componente);
        }
    }

    subtitulo("fila restante");
    fila_listar(fila);
    printf("  tamanho: %d\n", fila_tamanho(fila));

    /* ------------------------------------------------------------------ */
    etapa(5, "Pilha de historico (LIFO)");

    pilha_empilhar(historico, montar_operacao("OP001", "ENTRADA_BOLSA",
                   "BS0001 recebida do HEMOPE", "2026-10-07 07:50"));
    pilha_empilhar(historico, montar_operacao("OP002", "ENTRADA_BOLSA",
                   "BS0002 recebida do GSH Hemato", "2026-10-07 07:55"));
    pilha_empilhar(historico, montar_operacao("OP003", "RESERVA_BOLSA",
                   "BS0005 reservada para RQ002", "2026-10-07 08:42"));
    pilha_empilhar(historico, montar_operacao("OP004", "SAIDA_BOLSA",
                   "BS0001 despachada para RQ001", "2026-10-07 08:20"));
    pilha_empilhar(historico, montar_operacao("OP005", "DESCARTE_BOLSA",
                   "BS0003 descartada por vencimento", "2026-10-07 09:10"));

    subtitulo("historico apos 5 operacoes (topo = mais recente)");
    pilha_listar(historico);
    printf("  tamanho: %d\n", pilha_tamanho(historico));

    subtitulo("operacao no topo (consulta, sem retirar)");
    Operacao *ultima = pilha_topo(historico);
    if (ultima != NULL) {
        printf("  %s - %s: %s\n",
               ultima->id, ultima->operacao, ultima->descricao);
    }
    printf("  tamanho segue: %d\n", pilha_tamanho(historico));

    subtitulo("desfazendo as 2 ultimas operacoes (desempilhar)");
    for (int i = 0; i < 2; i++) {
        Operacao desfeita;
        if (pilha_desempilhar(historico, &desfeita) == RV_OK) {
            printf("  desfeita: %s - %s (%s)\n",
                   desfeita.id, desfeita.operacao, desfeita.data_hora);
        }
    }

    subtitulo("historico restante");
    pilha_listar(historico);
    printf("  tamanho: %d\n", pilha_tamanho(historico));

    /* ------------------------------------------------------------------ */
    etapa(6, "Liberacao da memoria");

    printf("  antes de destruir: estoque=%d, fila=%d, historico=%d nos\n",
           lista_tamanho(estoque), fila_tamanho(fila), pilha_tamanho(historico));

    /*
     * Cada destruir percorre a sua estrutura liberando no por no e, no fim,
     * libera o descritor. Depois disso os tres ponteiros ficam pendentes
     * (dangling) e sao zerados logo abaixo para que nenhum uso acidental
     * passe batido.
     */
    lista_destruir(estoque);
    fila_destruir(fila);
    pilha_destruir(historico);

    estoque = NULL;
    fila = NULL;
    historico = NULL;

    printf("  estoque   destruido (nos + descritor liberados)\n");
    printf("  fila      destruida (nos + descritor liberados)\n");
    printf("  historico destruido (nos + descritor liberados)\n");
    printf("\n  todas as alocacoes foram liberadas: nenhum vazamento\n\n");

    return 0;
}
