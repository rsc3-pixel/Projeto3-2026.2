package com.rotavital.web.dto;

/**
 * Um local da rede ja posicionado na area de desenho do mapa.
 *
 * <p>{@code x} e {@code y} sao coordenadas do SVG; {@code percentualX} e
 * {@code percentualY} sao a mesma posicao em porcentagem da caixa, para o
 * rotulo em HTML ancorar exatamente sobre a forma desenhada.
 *
 * <p>{@code tipo} e HEMOCENTRO ou HOSPITAL, e decide a forma do ponto no mapa.
 */
public record PontoMapa(
        String id,
        String nome,
        String bairro,
        double x,
        double y,
        String tipo,
        double percentualX,
        double percentualY) {

    /** Construtor usado antes de a caixa do mapa ser conhecida. */
    public PontoMapa(String id, String nome, String bairro,
                     double x, double y, String tipo) {
        this(id, nome, bairro, x, y, tipo, 0, 0);
    }

    /** Caracteres por linha antes de quebrar o rotulo. */
    private static final int LARGURA_ROTULO = 14;

    /**
     * O nome quebrado em linhas curtas.
     *
     * <p>O rotulo e HTML e quebra sozinho, mas o controller precisa saber em
     * quantas linhas ele cabe para reservar a folga certa no alto do mapa.
     */
    public java.util.List<String> linhasDoNome() {
        java.util.List<String> linhas = new java.util.ArrayList<>();
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

    public boolean isHemocentro() {
        return "HEMOCENTRO".equals(tipo);
    }

    /** O mesmo ponto, com a posicao relativa a caixa ja calculada. */
    public PontoMapa comPercentuais(double caixaX, double caixaY,
                                    double largura, double altura) {
        double px = (x - caixaX) / largura * 100;
        double py = (y - caixaY) / altura * 100;
        return new PontoMapa(id, nome, bairro, x, y, tipo,
                Math.round(px * 100) / 100.0, Math.round(py * 100) / 100.0);
    }
}
