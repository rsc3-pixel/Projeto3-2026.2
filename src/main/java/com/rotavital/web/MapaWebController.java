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

    /** Lado da area de desenho do SVG, em unidades do viewBox. */
    private static final double LADO = 1000;

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

        model.addAttribute("centros", pontosCentro);
        model.addAttribute("hospitais", pontosHospital);
        model.addAttribute("lado", LADO);
        model.addAttribute("agora", LocalDateTime.now());

        return "painel/mapa";
    }

    /**
     * Converte latitude e longitude para a caixa do SVG.
     *
     * <p>Usa a <b>mesma escala nos dois eixos</b>: escalas independentes
     * encheriam a tela, mas distorceriam as distancias, e a leitura de "quem
     * esta perto de quem" se perderia.
     */
    private record Escala(double latMin, double latMax, double lonMin,
                          double escala, double sobraX, double sobraY) {

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
                return new Escala(0, 0, 0, 1, 0, 0);
            }

            double util = LADO - 2 * MARGEM;
            double spanLat = Math.max(latMax - latMin, 1e-9);
            double spanLon = Math.max(lonMax - lonMin, 1e-9);
            double fator = util / Math.max(spanLat, spanLon);

            return new Escala(latMin, latMax, lonMin, fator,
                    (util - spanLon * fator) / 2,
                    (util - spanLat * fator) / 2);
        }

        PontoMapa posicionar(String id, String nome, Endereco e, String tipo) {
            // O eixo y inverte: latitude cresce para o norte, a coordenada do
            // SVG cresce para baixo.
            double x = MARGEM + sobraX + (e.getLongitude() - lonMin) * escala;
            double y = MARGEM + sobraY + (latMax - e.getLatitude()) * escala;
            return new PontoMapa(id, nome, e.getBairro(),
                    Math.round(x * 10) / 10.0, Math.round(y * 10) / 10.0, tipo);
        }
    }
}
