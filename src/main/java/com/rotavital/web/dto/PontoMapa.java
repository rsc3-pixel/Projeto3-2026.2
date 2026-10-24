package com.rotavital.web.dto;

import java.util.ArrayList;
import java.util.List;

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

    /** Caracteres por linha antes de quebrar o rotulo. */
    private static final int LARGURA_ROTULO = 18;

    public boolean isHemocentro() {
        return "HEMOCENTRO".equals(tipo);
    }

    /**
     * O nome quebrado em linhas curtas.
     *
     * <p>SVG nao quebra texto sozinho: cada linha precisa de um elemento
     * proprio. A quebra acontece entre palavras, para o nome continuar legivel.
     */
    public List<String> linhasDoNome() {
        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();

        for (String palavra : nome.split("\s+")) {
            if (atual.length() > 0 && atual.length() + palavra.length() + 1 > LARGURA_ROTULO) {
                linhas.add(atual.toString());
                atual = new StringBuilder();
            }
            if (atual.length() > 0) {
                atual.append(' ');
            }
            atual.append(palavra);
        }
        if (atual.length() > 0) {
            linhas.add(atual.toString());
        }
        return linhas;
    }
}
