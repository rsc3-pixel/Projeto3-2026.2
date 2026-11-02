package com.rotavital.api.dto.indicadores;

import com.rotavital.dominio.enums.GrupoSanguineo;

import java.time.LocalDate;
import java.util.List;

/**
 * Probabilidade de desabastecimento por tipo sanguineo (PI3-102, consumida pelo painel PI3-32).
 *
 * <p>A demanda e tratada como variavel aleatoria: a pergunta respondida e "com o estoque valido de
 * hoje, qual a chance de nao atender uma demanda do tamanho da observada no periodo?". O periodo e
 * o intervalo entre a primeira e a ultima requisicao registrada.</p>
 *
 * @param diasObservados  dias entre a primeira e a ultima requisicao, inclusive; zero sem requisicoes
 * @param distribuicao    distribuicao adotada e por que
 */
public record DesabastecimentoResponse(
        LocalDate dataReferencia,
        LocalDate inicioDemanda,
        LocalDate fimDemanda,
        int diasObservados,
        String distribuicao,
        List<DesabastecimentoTipoDto> porTipo) {

    /**
     * @param demandaEsperada          bolsas pedidas no periodo observado (media da variavel aleatoria)
     * @param varianciaDemanda         variancia da demanda no horizonte, medida na serie diaria
     * @param indiceDispersao          variancia / media; acima de 1 ha sobredispersao e a Poisson nao serve
     * @param probabilidadePercentual  P(demanda &gt; estoque valido) pela Binomial Negativa, em %
     * @param probabilidadePoissonPercentual  a mesma probabilidade pela Poisson, so para comparacao
     * @param nivelRisco               CRITICO, ALTO, MODERADO ou BAIXO
     */
    public record DesabastecimentoTipoDto(
            GrupoSanguineo grupoSanguineo,
            long estoqueValido,
            long demandaEsperada,
            double varianciaDemanda,
            double indiceDispersao,
            Double coberturaPercentual,
            long deficit,
            double probabilidadePercentual,
            double probabilidadePoissonPercentual,
            String nivelRisco) {
    }
}
