package com.rotavital.api.dto.indicadores;

import java.time.LocalDate;
import java.util.List;

/**
 * Taxa de descarte por vencimento das bolsas coletadas num periodo (PI3-103).
 *
 * <p>As bolsas sao agrupadas pela data de coleta: a taxa de um periodo diz quanto do que entrou
 * naquela semana ou mes venceu sem uso ate a data de referencia. Ler os periodos em sequencia
 * mostra se o descarte esta concentrado nos lotes antigos ou se continua nos recentes.</p>
 *
 * @param periodo rotulo do periodo: {@code 2026-W33} para semana ISO, {@code 2026-08} para mes
 * @param inicio  primeiro dia do periodo
 * @param fim     ultimo dia do periodo
 */
public record DescartePeriodoResponse(
        String periodo,
        LocalDate inicio,
        LocalDate fim,
        long total,
        long vencidas,
        double taxaDescarte,
        List<DescarteComponenteResponse> porComponente) {
}
