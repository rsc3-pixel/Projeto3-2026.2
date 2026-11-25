package com.rotavital.dominio.enums;

/**
 * Ciclo de vida da rota. A tabela de transicoes mora aqui (PI3-247) e e a
 * Rota que a consulta antes de mudar de status.
 */
public enum StatusRota {
    PLANEJADA,
    EM_TRANSITO,
    CONCLUIDA,
    CANCELADA;

    public boolean podeIrPara(StatusRota destino) {
        return switch (this) {
            case PLANEJADA -> destino == EM_TRANSITO || destino == CANCELADA;
            case EM_TRANSITO -> destino == CONCLUIDA || destino == CANCELADA;
            case CONCLUIDA, CANCELADA -> false;
        };
    }
}
