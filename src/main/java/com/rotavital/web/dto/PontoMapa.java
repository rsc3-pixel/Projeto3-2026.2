package com.rotavital.web.dto;

/**
 * Um local da rede ja posicionado na area de desenho do mapa.
 *
 * <p>{@code x} e {@code y} sao coordenadas do SVG, nao geograficas: a conversao
 * acontece no controller, entao o template so desenha.
 *
 * <p>{@code tipo} e HEMOCENTRO ou HOSPITAL, e decide a forma do ponto no mapa.
 */
public record PontoMapa(
        String id,
        String nome,
        String bairro,
        double x,
        double y,
        String tipo) {

    public boolean isHemocentro() {
        return "HEMOCENTRO".equals(tipo);
    }
}
