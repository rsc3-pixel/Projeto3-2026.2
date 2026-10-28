package com.rotavital.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Leitura reportada pelo sensor da remessa.
 *
 * <p>Os limites de -80 e 60 graus nao sao faixa aceitavel de transporte: sao
 * o intervalo fisicamente plausivel para um sensor. Leitura fora disso indica
 * defeito do equipamento, nao excursao de temperatura, e por isso e recusada
 * como entrada invalida (400) em vez de virar alerta de cadeia fria.</p>
 */
public record LeituraTemperaturaRequest(

        @NotNull(message = "temperaturaCelsius e obrigatoria")
        @DecimalMin(value = "-80.0", message = "temperatura abaixo do plausivel para um sensor")
        @DecimalMax(value = "60.0", message = "temperatura acima do plausivel para um sensor")
        Double temperaturaCelsius) {
}
