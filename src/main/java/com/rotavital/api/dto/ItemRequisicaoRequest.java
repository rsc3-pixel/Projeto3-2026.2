package com.rotavital.api.dto;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemRequisicaoRequest(
        @NotNull(message = "tipoHemocomponente e obrigatorio")
        TipoHemocomponente tipoHemocomponente,

        @NotNull(message = "grupoSanguineo e obrigatorio")
        GrupoSanguineo grupoSanguineo,

        @NotNull(message = "quantidadeSolicitada e obrigatoria")
        @Positive(message = "quantidadeSolicitada deve ser maior que zero")
        Integer quantidadeSolicitada) {
}
