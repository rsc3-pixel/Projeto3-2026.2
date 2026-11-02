package com.rotavital.api.dto.indicadores;

import com.rotavital.dominio.enums.TipoHemocomponente;

/**
 * Taxa de descarte por vencimento de um hemocomponente (HU-06, PI3-103).
 *
 * <p>{@code descarteEvitavelFefo} conta, entre as vencidas, as bolsas que tiveram demanda compativel
 * (mesmo componente e grupo compativel) enquanto ainda valiam. Sob a politica FEFO essas bolsas
 * teriam saido antes de vencer: e a parcela do descarte que a alocacao poderia ter evitado.</p>
 *
 * @param taxaDescarte             percentual de vencidas sobre o total do componente
 * @param percentualEvitavelFefo   percentual de evitaveis sobre as vencidas
 */
public record DescarteComponenteResponse(
        TipoHemocomponente tipoHemocomponente,
        long total,
        long vencidas,
        double taxaDescarte,
        long descarteEvitavelFefo,
        double percentualEvitavelFefo) {
}
