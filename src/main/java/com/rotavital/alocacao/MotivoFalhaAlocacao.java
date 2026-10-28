package com.rotavital.alocacao;

/**
 * Motivo pelo qual uma alocacao com rota nao encontrou bolsa para entregar.
 *
 * <p>Os tres motivos sao estados legitimos da operacao, nao erros de
 * programa: por isso a falha e devolvida em {@link ResultadoAlocacao}, nunca
 * lancada como excecao (PI3-57). Quem consome o resultado consegue distinguir
 * cada caso e reagir de forma diferente - repor estoque, descartar vencidas ou
 * revisar a malha.</p>
 *
 * <p>Os motivos sao avaliados nesta ordem, e vale o primeiro que ocorrer:
 * primeiro a existencia de estoque, depois a validade, por ultimo o
 * alcance.</p>
 */
public enum MotivoFalhaAlocacao {

    /**
     * Nenhuma unidade da rede possui bolsa da combinacao pedida de grupo
     * sanguineo e tipo de hemocomponente - nem sequer vencida ou reservada.
     */
    SEM_ESTOQUE,

    /**
     * Existem bolsas da combinacao pedida, mas nenhuma esta alocavel na data
     * de referencia: estao vencidas ou fora do status
     * {@code DISPONIVEL} (reservadas, em transito, entregues ou descartadas).
     */
    TODAS_VENCIDAS,

    /**
     * Existem bolsas alocaveis, mas todas em unidades sem caminho a partir da
     * unidade solicitante. Tambem cobre a solicitante que nao pertence a
     * malha: dela, nada e alcancavel.
     */
    SEM_CAMINHO
}
