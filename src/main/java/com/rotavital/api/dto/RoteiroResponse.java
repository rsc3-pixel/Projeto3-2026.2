package com.rotavital.api.dto;

import com.rotavital.api.dto.RotaCalculadaResponse.Parada;
import com.rotavital.dominio.Local;
import com.rotavital.estruturas.ResultadoRoteiro;

import java.util.List;
import java.util.Map;

/**
 * Roteiro de entregas montado pelo vizinho mais proximo (PI3-227).
 *
 * <p>As paradas usam o mesmo formato do calculo de rota da HU-05, para que
 * quem consome a API trate as duas respostas do mesmo jeito.</p>
 *
 * <p>Destino sem trecho na malha nao e erro: ele vai para
 * {@code inalcancaveis}, os outros sao visitados normalmente, e a
 * {@code mensagem} explica. A heuristica escolhe sempre o pendente mais
 * proximo, entao o roteiro e bom mas nao necessariamente o de menor tempo
 * total.</p>
 *
 * @param origemId          unidade de partida
 * @param alcancouTodos     se todos os destinos pedidos foram visitados
 * @param ordemDeVisita     destinos na ordem em que o roteiro passa por eles
 * @param caminho           todas as unidades atravessadas, da origem ao ultimo destino
 * @param tempoTotalMinutos soma do tempo estimado de cada trecho
 * @param inalcancaveis     destinos sem trecho que os ligue ao roteiro
 * @param mensagem          presente so quando algum destino ficou de fora
 */
public record RoteiroResponse(
        String origemId,
        boolean alcancouTodos,
        List<Parada> ordemDeVisita,
        List<Parada> caminho,
        double tempoTotalMinutos,
        List<Parada> inalcancaveis,
        String mensagem) {

    public static final String DESTINOS_DE_FORA =
            "Alguns destinos nao tem trecho na malha e ficaram fora do roteiro";

    public static RoteiroResponse de(Local origem, ResultadoRoteiro<String> roteiro,
                                     Map<String, Local> unidades) {
        return new RoteiroResponse(
                origem.getId(),
                roteiro.alcancouTodos(),
                paradas(roteiro.ordemDeVisita(), unidades),
                paradas(roteiro.caminhoCompleto(), unidades),
                roteiro.custoTotalMinutos(),
                paradas(roteiro.pontosInalcancaveis(), unidades),
                roteiro.alcancouTodos() ? null : DESTINOS_DE_FORA);
    }

    private static List<Parada> paradas(List<String> ids, Map<String, Local> unidades) {
        return ids.stream().map(id -> Parada.de(unidades.get(id))).toList();
    }
}
