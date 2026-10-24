package com.rotavital.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.Hospital;
import com.rotavital.servico.HemocentroServico;
import com.rotavital.servico.HospitalServico;
import com.rotavital.web.dto.PontoMapa;

/**
 * Mapa da rede: a tela de entrada do painel operacional (PI3-139).
 *
 * <p>Mostra hemocentros e hospitais na posicao geografica real, ligados por
 * linhas tracejadas. A ligacao e sempre de um hemocentro para um hospital,
 * porque e assim que o sangue caminha: o centro coleta e armazena, o hospital
 * consome. Hospital nao abastece hospital.
 *
 * <p>As coordenadas geograficas viram posicoes do SVG aqui, e nao no navegador:
 * a conta fica num lugar testavel e o template so desenha.
 */
@Controller
public class MapaWebController {

    /** Largura da area de desenho do SVG, em unidades do viewBox. */
    private static final double LARGURA = 1600;

    /** Altura da area de desenho, numa proporcao proxima da tela. */
    private static final double ALTURA = 900;

    /** Margem interna, para os pontos da borda nao ficarem colados. */
    private static final double MARGEM = 90;

    private final HemocentroServico hemocentroServico;
    private final HospitalServico hospitalServico;

    public MapaWebController(HemocentroServico hemocentroServico,
                             HospitalServico hospitalServico) {
        this.hemocentroServico = hemocentroServico;
        this.hospitalServico = hospitalServico;
    }

    @GetMapping("/")
    public String mapa(Model model) {
        List<Hemocentro> centros = hemocentroServico.listar(null, null);
        List<Hospital> hospitais = hospitalServico.listar(null, null);

        // A escala sai do conjunto inteiro: se cada tipo fosse normalizado
        // pelo seu proprio intervalo, as distancias entre centro e hospital
        // ficariam erradas, que e justamente o que o mapa precisa mostrar.
        Escala escala = Escala.de(centros, hospitais);

        List<PontoMapa> pontosCentro = new ArrayList<>();
        for (Hemocentro c : centros) {
            pontosCentro.add(escala.posicionar(c.getId(), c.getNome(),
                    c.getEndereco(), "HEMOCENTRO"));
        }

        List<PontoMapa> pontosHospital = new ArrayList<>();
        for (Hospital h : hospitais) {
            pontosHospital.add(escala.posicionar(h.getId(), h.getNome(),
                    h.getEndereco(), "HOSPITAL"));
        }

        // Tres hemocentros ficam a poucas centenas de metros um do outro, no
        // bairro das Gracas. Na escala do mapa eles caem quase no mesmo pixel e
        // os rotulos viram um borrao. Afastar o minimo necessario preserva a
        // leitura sem mentir sobre onde cada um fica.
        afastarSobrepostos(pontosCentro, pontosHospital);

        model.addAttribute("centros", pontosCentro);
        model.addAttribute("hospitais", pontosHospital);
        model.addAttribute("caixa", enquadrar(pontosCentro, pontosHospital));
        model.addAttribute("agora", LocalDateTime.now());

        return "painel/mapa";
    }

    /**
     * Calcula o viewBox que enquadra os pontos, com folga para os rotulos.
     *
     * <p>A area util do mapa raramente e quadrada: no Recife os locais se
     * espalham mais no sentido norte-sul do que leste-oeste. Um viewBox fixo de
     * 1000x1000 deixaria metade da largura vazia, entao ele e recortado em
     * volta do que realmente existe.
     */
    private String enquadrar(List<PontoMapa> centros, List<PontoMapa> hospitais) {
        List<PontoMapa> todos = new ArrayList<>(centros);
        todos.addAll(hospitais);
        if (todos.isEmpty()) {
            return "0 0 " + (int) LARGURA + " " + (int) ALTURA;
        }

        double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE;
        double yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        for (PontoMapa p : todos) {
            xMin = Math.min(xMin, p.x());
            xMax = Math.max(xMax, p.x());
            yMin = Math.min(yMin, p.y());
            yMax = Math.max(yMax, p.y());
        }

        // Folga para caber o rotulo. Na vertical ela depende do nome mais
        // longo: um nome de tres linhas sobe bem acima do ponto, e sem esta
        // conta o primeiro e o ultimo ponto saem cortados pela borda.
        int maisLinhas = 1;
        for (PontoMapa p : todos) {
            maisLinhas = Math.max(maisLinhas, p.linhasDoNome().size());
        }
        double folgaX = 230;
        double folgaY = 90 + maisLinhas * 26;
        double x = xMin - folgaX;
        double y = yMin - folgaY;
        double largura = (xMax - xMin) + 2 * folgaX;
        double altura = (yMax - yMin) + 2 * folgaY;

        return String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f %.0f",
                x, y, largura, altura);
    }

    /** Distancia minima entre dois pontos para os rotulos nao colidirem. */
    private static final double DISTANCIA_MINIMA = 260;

    /**
     * Afasta pontos que cairiam praticamente no mesmo lugar.
     *
     * <p>Empurra os dois para lados opostos, ao longo da reta que os liga, ate
     * completarem a distancia minima. O deslocamento e o menor possivel, entao
     * a posicao relativa continua dizendo a verdade: quem fica ao norte segue
     * ao norte, so que legivel.
     *
     * <p>Repete algumas vezes porque separar um par pode aproximar outro.
     */
    private void afastarSobrepostos(List<PontoMapa> centros, List<PontoMapa> hospitais) {
        List<PontoMapa> todos = new ArrayList<>(centros);
        todos.addAll(hospitais);

        for (int passada = 0; passada < 60; passada++) {
            boolean mexeu = false;

            for (int i = 0; i < todos.size(); i++) {
                for (int j = i + 1; j < todos.size(); j++) {
                    PontoMapa a = todos.get(i);
                    PontoMapa b = todos.get(j);

                    double dx = b.x() - a.x();
                    double dy = b.y() - a.y();
                    double dist = Math.hypot(dx, dy);
                    if (dist >= DISTANCIA_MINIMA) {
                        continue;
                    }

                    // Pontos exatamente no mesmo lugar nao tem direcao: usa uma
                    // qualquer para o primeiro empurrao.
                    if (dist < 0.01) {
                        dx = 1;
                        dy = 0;
                        dist = 1;
                    }

                    double empurrao = (DISTANCIA_MINIMA - dist) / 2;
                    double ux = dx / dist * empurrao;
                    double uy = dy / dist * empurrao;

                    todos.set(i, mover(a, -ux, -uy));
                    todos.set(j, mover(b, ux, uy));
                    mexeu = true;
                }
            }
            if (!mexeu) {
                break;
            }
        }

        for (int i = 0; i < centros.size(); i++) {
            centros.set(i, todos.get(i));
        }
        for (int i = 0; i < hospitais.size(); i++) {
            hospitais.set(i, todos.get(centros.size() + i));
        }
    }

    /** Move o ponto, mantendo-o dentro da area de desenho. */
    private PontoMapa mover(PontoMapa p, double dx, double dy) {
        double x = Math.max(MARGEM, Math.min(LARGURA - MARGEM, p.x() + dx));
        double y = Math.max(MARGEM, Math.min(ALTURA - MARGEM, p.y() + dy));
        return new PontoMapa(p.id(), p.nome(), p.bairro(),
                Math.round(x * 10) / 10.0, Math.round(y * 10) / 10.0, p.tipo());
    }

    /**
     * Converte latitude e longitude para a caixa do SVG.
     *
     * <p>Usa a <b>mesma escala nos dois eixos</b>: escalas independentes
     * encheriam a tela, mas distorceriam as distancias, e a leitura de "quem
     * esta perto de quem" se perderia.
     */
    private record Escala(double latMin, double latMax, double lonMin,
                          double fatorX, double fatorY) {

        static Escala de(List<Hemocentro> centros, List<Hospital> hospitais) {
            double latMin = Double.MAX_VALUE, latMax = -Double.MAX_VALUE;
            double lonMin = Double.MAX_VALUE, lonMax = -Double.MAX_VALUE;

            List<Endereco> todos = new ArrayList<>();
            centros.forEach(c -> todos.add(c.getEndereco()));
            hospitais.forEach(h -> todos.add(h.getEndereco()));

            for (Endereco e : todos) {
                latMin = Math.min(latMin, e.getLatitude());
                latMax = Math.max(latMax, e.getLatitude());
                lonMin = Math.min(lonMin, e.getLongitude());
                lonMax = Math.max(lonMax, e.getLongitude());
            }
            if (todos.isEmpty()) {
                return new Escala(0, 0, 0, 1, 1);
            }

            double spanLat = Math.max(latMax - latMin, 1e-9);
            double spanLon = Math.max(lonMax - lonMin, 1e-9);

            // Cada eixo tem a propria escala, para os pontos ocuparem a area
            // inteira. Isso distorce as distancias, e e uma troca deliberada:
            // num mapa de rede operacional interessa quem se liga a quem e em
            // que direcao, nao quantos quilometros separam os pontos. Com
            // escala unica a mancha de pontos fica estreita e os rotulos ficam
            // ilegiveis, que e o oposto do que a tela existe para fazer.
            double fatorX = (LARGURA - 2 * MARGEM) / spanLon;
            double fatorY = (ALTURA - 2 * MARGEM) / spanLat;

            return new Escala(latMin, latMax, lonMin, fatorX, fatorY);
        }

        PontoMapa posicionar(String id, String nome, Endereco e, String tipo) {
            // O eixo y inverte: latitude cresce para o norte, a coordenada do
            // SVG cresce para baixo.
            double x = MARGEM + (e.getLongitude() - lonMin) * fatorX;
            double y = MARGEM + (latMax - e.getLatitude()) * fatorY;
            return new PontoMapa(id, nome, e.getBairro(),
                    Math.round(x * 10) / 10.0, Math.round(y * 10) / 10.0, tipo);
        }
    }
}
