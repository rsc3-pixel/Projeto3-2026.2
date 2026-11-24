package com.rotavital.dominio.excecao;

/**
 * Recurso pedido nao existe. Vira HTTP 404 no manipulador global.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, String id) {
        super(recurso + " nao encontrado: " + id);
    }
}
