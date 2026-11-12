package com.rotavital.api.dto;

import com.rotavital.dominio.Rota;
import com.rotavital.dominio.enums.StatusRota;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @param tempoEstimadoMinutos tempo do caminho calculado na malha (HU-05);
 *                             nulo quando nao houve caminho
 * @param caminho              ids das unidades do caminho, da origem ao
 *                             destino; vazio quando nao houve caminho
 */
public record RotaResponse(
        String id,
        String hemocentroOrigemId,
        String hospitalDestinoId,
        LocalDateTime previsaoSaida,
        LocalDateTime previsaoChegada,
        LocalDateTime saidaReal,
        LocalDateTime chegadaReal,
        StatusRota status,
        Double tempoEstimadoMinutos,
        List<String> caminho,
        List<BolsaResponse> bolsas) {

    public static RotaResponse de(Rota rota) {
        return new RotaResponse(
                rota.getId(),
                rota.getOrigem() == null ? null : rota.getOrigem().getId(),
                rota.getDestino() == null ? null : rota.getDestino().getId(),
                rota.getDataHoraSaida(),
                rota.getDataHoraChegadaPrevista(),
                rota.getSaidaReal(),
                rota.getChegadaReal(),
                rota.getStatus(),
                rota.getTempoEstimadoMinutos(),
                rota.getCaminho(),
                rota.getBolsas().stream().map(BolsaResponse::de).toList());
    }
}
