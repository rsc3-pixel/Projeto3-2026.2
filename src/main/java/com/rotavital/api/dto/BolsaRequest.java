package com.rotavital.api.dto;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Corpo do POST de bolsa.
 *
 * <p>Nao recebe dataValidade nem status: a validade e calculada pelo dominio
 * a partir do tipo, e o status nasce sempre DISPONIVEL. Aceitar esses campos
 * do cliente permitiria burlar a regra.</p>
 */
public record BolsaRequest(
        @NotNull(message = "hemocentroId e obrigatorio")
        String hemocentroId,

        @NotNull(message = "tipoHemocomponente e obrigatorio")
        TipoHemocomponente tipoHemocomponente,

        @NotNull(message = "grupoSanguineo e obrigatorio")
        GrupoSanguineo grupoSanguineo,

        @NotNull(message = "volumeMl e obrigatorio")
        @Positive(message = "volumeMl deve ser maior que zero")
        Integer volumeMl,

        @NotNull(message = "dataColeta e obrigatoria")
        @PastOrPresent(message = "dataColeta nao pode ser futura")
        LocalDate dataColeta) {
}
