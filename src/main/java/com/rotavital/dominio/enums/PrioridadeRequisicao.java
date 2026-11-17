package com.rotavital.dominio.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Prioridade de uma requisicao e o prazo de atendimento que ela implica.
 *
 * <p>O prazo mora aqui, junto da prioridade, porque e dela que ele deriva
 * (HU-02, cenario 2): uma emergencia precisa ser atendida em 2 horas e uma
 * rotina em 72. Antes o numero vivia na carga inicial, e um servico que
 * precisasse dele teria de copiar a tabela. Agora ha um lugar so.</p>
 */
public enum PrioridadeRequisicao {

    ROTINA("Rotina", 72),
    URGENTE("Urgente", 12),
    EMERGENCIA("Emergencia", 2);

    private final String descricao;
    private final int prazoHoras;

    PrioridadeRequisicao(String descricao, int prazoHoras) {
        this.descricao = descricao;
        this.prazoHoras = prazoHoras;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Horas que o hemocentro tem para atender uma requisicao desta
     * prioridade, contadas a partir da criacao.
     */
    public int getPrazoHoras() {
        return prazoHoras;
    }

    /**
     * O contrato da API usa o nome da constante ({@code URGENTE}), nao a
     * descricao. Sem isto o Jackson seguiria o toString() abaixo, que existe
     * para exibicao, e o JSON sairia com texto legivel em vez do enum.
     */
    @JsonValue
    public String comoJson() {
        return name();
    }

    @Override
    public String toString() {
        return descricao;
    }
}
