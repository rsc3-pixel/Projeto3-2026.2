package com.rotavital.api.dto;

import com.rotavital.dominio.enums.TipoHemocomponente;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Resultado de uma leitura de temperatura, ja avaliada contra a faixa de cada
 * componente embarcado.
 *
 * @param alertas componentes cuja faixa foi violada; lista vazia significa
 *                leitura dentro da faixa de todos
 */
public record LeituraTemperaturaResponse(
        String id,
        String rotaId,
        double temperaturaCelsius,
        LocalDateTime dataHora,
        boolean dentroDaFaixa,
        List<AlertaCadeiaFria> alertas) {

    /**
     * Alerta de um componente especifico.
     *
     * <p>A faixa viola e exibida junto, porque "5 graus gerou alerta" nao
     * informa nada sem saber que plaquetas exigem de 20 a 24.</p>
     */
    public record AlertaCadeiaFria(
            TipoHemocomponente componente,
            double faixaMinima,
            double faixaMaxima,
            int bolsasAfetadas) {

        public static AlertaCadeiaFria de(TipoHemocomponente tipo, int bolsasAfetadas) {
            return new AlertaCadeiaFria(
                    tipo,
                    tipo.getTempMinCelsius(),
                    tipo.getTempMaxCelsius(),
                    bolsasAfetadas);
        }
    }
}
