package com.rotavital.api.dto;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Painel de estoque critico (HU-04): o que vence nos proximos dias, da bolsa
 * mais urgente para a menos, e o que ja venceu e ainda ocupa o estoque.
 *
 * <p>As duas listas vem separadas de proposito. Uma bolsa vencida nao e "a
 * mais critica da fila": ela nao pode mais ser usada, e mistura-la com as
 * que ainda valem esconderia a diferenca entre "despachar hoje" e
 * "descartar". E o quarto criterio de aceite da historia.</p>
 *
 * @param dias           janela consultada, em dias a partir da data de referencia
 * @param dataReferencia o "hoje" usado na contagem
 * @param hemocentroId   filtro aplicado, ou nulo quando a consulta e da rede inteira
 * @param totalEmRisco   quantas bolsas vencem dentro da janela
 * @param totalVencidas  quantas ja venceram e continuam em estoque
 * @param mensagem       texto para o estado vazio, nulo quando ha bolsa em risco
 * @param emRisco        bolsas validas que vencem dentro da janela, da mais
 *                       proxima do vencimento para a mais distante
 * @param vencidas       bolsas vencidas ainda em estoque, da que venceu ha mais
 *                       tempo para a mais recente
 */
public record BolsasEmRiscoResponse(
        int dias,
        LocalDate dataReferencia,
        String hemocentroId,
        long totalEmRisco,
        long totalVencidas,
        String mensagem,
        List<BolsaEmRisco> emRisco,
        List<BolsaEmRisco> vencidas) {

    /**
     * Uma bolsa do painel, com os dias que faltam para vencer ja contados.
     *
     * <p>{@code diasRestantes} e negativo para bolsa vencida: -2 significa
     * que venceu ha dois dias. Zero significa que vence hoje e ainda pode
     * ser usada, porque a bolsa so vence no dia seguinte a validade.</p>
     */
    public record BolsaEmRisco(
            String id,
            String hemocentroId,
            TipoHemocomponente tipoHemocomponente,
            GrupoSanguineo grupoSanguineo,
            LocalDate dataValidade,
            StatusBolsa status,
            long diasRestantes) {

        public static BolsaEmRisco de(Bolsa bolsa, LocalDate dataReferencia) {
            return new BolsaEmRisco(
                    bolsa.getCodigoRastreio(),
                    bolsa.getHemocentroOrigem() == null ? null : bolsa.getHemocentroOrigem().getId(),
                    bolsa.getTipo(),
                    bolsa.getGrupoSanguineo(),
                    bolsa.getDataValidade(),
                    bolsa.getStatus(),
                    ChronoUnit.DAYS.between(dataReferencia, bolsa.getDataValidade()));
        }
    }
}
