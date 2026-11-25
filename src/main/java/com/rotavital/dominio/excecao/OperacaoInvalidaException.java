package com.rotavital.dominio.excecao;

/**
 * A operacao viola uma regra de negocio: o recurso existe, mas o estado atual
 * nao permite o que foi pedido. Vira HTTP 409 (conflito) no manipulador global.
 *
 * <p>Exemplos: alocar bolsa ja reservada, apagar hemocentro com estoque,
 * cancelar requisicao ja atendida.</p>
 */
public class OperacaoInvalidaException extends RuntimeException {

    public OperacaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
