package com.rotavital.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.Hospital;
import com.rotavital.servico.HemocentroServico;
import com.rotavital.servico.HospitalServico;
import com.rotavital.web.dto.PontoMapa;

/**
 * Mapa da rede: a tela de entrada do painel operacional (PI3-139).
 *
 * <p>O desenho privilegia a leitura, nao a geografia: os hemocentros ficam numa
 * faixa em cima e os hospitais numa faixa embaixo, espacados por igual. Quem
 * abre a tela ve de imediato quem abastece quem, porque todas as linhas correm
 * no mesmo sentido.
 *
 * <p>Posicionar pelas coordenadas reais foi a primeira tentativa e nao se
 * sustentou: tres hemocentros ficam a poucas centenas de metros um do outro no
 * bairro das Gracas e caiam praticamente no mesmo ponto, com os nomes
 * ilegiveis. O endereco de cada local continua no painel de gestao, onde ele
 * importa de fato.
 */
@Controller
public class MapaWebController {

    /** Largura da area de desenho do SVG, em unidades do viewBox. */
    private static final double LARGURA = 1600;

    /** Altura da area de desenho. */
    private static final double ALTURA = 760;

    /** Altura da faixa dos hemocentros. */
    private static final double LINHA_CENTROS = 215;

    /** Altura da faixa dos hospitais. */
    private static final double LINHA_HOSPITAIS = 580;

    /** Espaco lateral, para o rotulo das pontas nao sair da area. */
    private static final double MARGEM_LATERAL = 150;

    private final HemocentroServico hemocentroServico;
    private final HospitalServico hospitalServico;

    public MapaWebController(HemocentroServico hemocentroServico,
                             HospitalServico hospitalServico) {
        this.hemocentroServico = hemocentroServico;
        this.hospitalServico = hospitalServico;
    }

    @GetMapping("/")
    public String mapa(Model model) {
        List<Hemocentro> centros = new ArrayList<>(hemocentroServico.listar(null, null));
        List<Hospital> hospitais = new ArrayList<>(hospitalServico.listar(null, null));

        // A ordem por nome mantem o mapa estavel entre uma visita e outra: sem
        // isso os pontos trocariam de lugar conforme o banco devolvesse.
        centros.sort(Comparator.comparing(Hemocentro::getNome));
        hospitais.sort(Comparator.comparing(Hospital::getNome));

        List<PontoMapa> brutosCentro = new ArrayList<>();
        for (Hemocentro c : centros) {
            brutosCentro.add(new PontoMapa(c.getId(), c.getNome(),
                    c.getEndereco().getBairro(), 0, 0, "HEMOCENTRO"));
        }
        List<PontoMapa> brutosHospital = new ArrayList<>();
        for (Hospital h : hospitais) {
            brutosHospital.add(new PontoMapa(h.getId(), h.getNome(),
                    h.getEndereco().getBairro(), 0, 0, "HOSPITAL"));
        }

        model.addAttribute("centros", distribuir(brutosCentro, LINHA_CENTROS));
        model.addAttribute("hospitais", distribuir(brutosHospital, LINHA_HOSPITAIS));
        model.addAttribute("caixa",
                String.format(java.util.Locale.ROOT, "0 0 %.0f %.0f", LARGURA, ALTURA));
        model.addAttribute("proporcao", (int) LARGURA + " / " + (int) ALTURA);
        model.addAttribute("agora", LocalDateTime.now());

        return "painel/mapa";
    }

    /**
     * Espalha os pontos por igual ao longo de uma faixa horizontal.
     *
     * <p>Com um unico ponto ele vai ao centro; com varios, o espacamento e o
     * mesmo entre todos, e sobra a margem lateral nas pontas.
     */
    private List<PontoMapa> distribuir(List<PontoMapa> pontos, double altura) {
        List<PontoMapa> saida = new ArrayList<>();
        int total = pontos.size();
        if (total == 0) {
            return saida;
        }

        double util = LARGURA - 2 * MARGEM_LATERAL;
        for (int i = 0; i < total; i++) {
            double x = total == 1
                    ? LARGURA / 2
                    : MARGEM_LATERAL + util * i / (total - 1.0);

            PontoMapa p = pontos.get(i);
            saida.add(new PontoMapa(p.id(), p.nome(), p.bairro(),
                    Math.round(x * 10) / 10.0, altura, p.tipo(),
                    Math.round(x / LARGURA * 10000) / 100.0,
                    Math.round(altura / ALTURA * 10000) / 100.0));
        }
        return saida;
    }
}
