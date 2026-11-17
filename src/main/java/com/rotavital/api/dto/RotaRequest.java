package com.rotavital.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Corpo do POST de rota.
 *
 * <p>A previsao de chegada e opcional desde a HU-05: quando nao vem, o
 * sistema calcula o caminho mais rapido entre origem e destino na malha e
 * soma o tempo estimado a previsao de saida. Quando vem, vale o que o
 * usuario informou, e o caminho calculado fica registrado so como
 * referencia.</p>
 */
public record RotaRequest(
        @NotNull(message = "hemocentroOrigemId e obrigatorio")
        String hemocentroOrigemId,

        @NotNull(message = "hospitalDestinoId e obrigatorio")
        String hospitalDestinoId,

        @NotNull(message = "previsaoSaida e obrigatoria")
        LocalDateTime previsaoSaida,

        LocalDateTime previsaoChegada) {
}
