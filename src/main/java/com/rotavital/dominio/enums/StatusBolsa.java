package com.rotavital.dominio.enums;

/**
 * Ciclo de vida da bolsa e as transicoes que ele permite.
 *
 * <p>A tabela mora no proprio status (PI3-247). Antes ela ficava no
 * BolsaServico e os outros servicos trocavam o status sem consulta-la; agora
 * a Bolsa pergunta aqui antes de qualquer mudanca, e nao ha segundo caminho.</p>
 */
public enum StatusBolsa {
    DISPONIVEL,
    RESERVADA,
    EM_TRANSITO,
    ENTREGUE,
    DESCARTADA;

    /**
     * EM_TRANSITO volta a RESERVADA quando a rota e cancelada: a carga
     * continua comprometida com a requisicao e retorna ao hemocentro.
     */
    public boolean podeIrPara(StatusBolsa destino) {
        return switch (this) {
            case DISPONIVEL -> destino == RESERVADA || destino == DESCARTADA;
            case RESERVADA -> destino == DISPONIVEL || destino == EM_TRANSITO || destino == DESCARTADA;
            case EM_TRANSITO -> destino == ENTREGUE || destino == RESERVADA || destino == DESCARTADA;
            case ENTREGUE, DESCARTADA -> false;
        };
    }

    /** Bolsa que ainda ocupa o estoque do hemocentro: livre ou reservada, mas nao embarcada. */
    public boolean estaNoEstoque() {
        return this == DISPONIVEL || this == RESERVADA;
    }
}
