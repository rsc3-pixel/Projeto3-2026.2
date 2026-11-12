package com.rotavital.api.dto;

import com.rotavital.dominio.Local;
import com.rotavital.estruturas.ResultadoRota;
import com.rotavital.servico.RoteirizacaoServico;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resposta de {@code GET /api/v1/rotas/calcular} (HU-05): o caminho mais
 * rapido entre duas unidades, ou o aviso de que nao ha caminho.
 *
 * <p>Sucesso e falha saem pelo mesmo tipo, como no {@link ResultadoRota}:
 * destino inalcancavel e um estado legitimo da operacao, e a resposta traz a
 * mensagem em vez de um erro HTTP. E o terceiro criterio de aceite da
 * historia ("ausencia de rota devolve resposta tratada, nao erro").</p>
 *
 * @param origemId      unidade de partida
 * @param destinoId     unidade de chegada
 * @param alcancavel    se existe trecho ligando as duas
 * @param tempoMinutos  tempo estimado do caminho; nulo quando inalcancavel
 * @param caminho       paradas em ordem, da origem ao destino; vazia quando
 *                      inalcancavel
 * @param mensagem      explicacao quando nao ha caminho; nula no sucesso
 */
public record RotaCalculadaResponse(
        String origemId,
        String destinoId,
        boolean alcancavel,
        Double tempoMinutos,
        List<Parada> caminho,
        String mensagem) {

    public static final String SEM_ROTA = "Nao ha rota disponivel para este destino";

    /** Uma unidade do caminho, nomeada para a tela nao precisar de outra consulta. */
    public record Parada(String id, String nome, String tipo) {

        public static Parada de(Local local) {
            return new Parada(local.getId(), local.getNome(), RoteirizacaoServico.tipoDe(local));
        }
    }

    public static RotaCalculadaResponse de(Local origem, Local destino,
                                           ResultadoRota<String> rota,
                                           Map<String, Local> unidades) {
        if (!rota.alcancavel()) {
            return new RotaCalculadaResponse(origem.getId(), destino.getId(),
                    false, null, List.of(), SEM_ROTA);
        }

        List<Parada> paradas = new ArrayList<>();
        for (String id : rota.caminho()) {
            paradas.add(Parada.de(unidades.get(id)));
        }
        return new RotaCalculadaResponse(origem.getId(), destino.getId(),
                true, rota.custoTotal(), List.copyOf(paradas), null);
    }
}
