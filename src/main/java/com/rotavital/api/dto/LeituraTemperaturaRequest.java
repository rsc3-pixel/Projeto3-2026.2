package com.rotavital.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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
 *
 * <p>Latitude e longitude sao opcionais (GPS simulado, PI3-112). Quando
 * vierem, vem as duas: meia coordenada nao localiza nada, e aceita-la
 * gravaria um ponto que o mapa nao consegue desenhar.</p>
 */
public record LeituraTemperaturaRequest(

        @NotNull(message = "temperaturaCelsius e obrigatoria")
        @DecimalMin(value = "-80.0", message = "temperatura abaixo do plausivel para um sensor")
        @DecimalMax(value = "60.0", message = "temperatura acima do plausivel para um sensor")
        Double temperaturaCelsius,

        @DecimalMin(value = "-90.0", message = "latitude deve estar entre -90 e 90")
        @DecimalMax(value = "90.0", message = "latitude deve estar entre -90 e 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "longitude deve estar entre -180 e 180")
        @DecimalMax(value = "180.0", message = "longitude deve estar entre -180 e 180")
        Double longitude) {

    /**
     * Regra "as duas ou nenhuma". Validada pelo Bean Validation como
     * propriedade; o {@code @JsonIgnore} impede que ela apareca como campo no
     * JSON quando o proprio record e serializado (nos testes, por exemplo).
     */
    @JsonIgnore
    @AssertTrue(message = "latitude e longitude devem vir juntas")
    public boolean isPosicaoCompleta() {
        return (latitude == null) == (longitude == null);
    }
}
