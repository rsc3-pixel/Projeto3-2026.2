package com.rotavital.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.rotavital.api.dto.indicadores.BolsaCriticaResponse;
import com.rotavital.api.dto.indicadores.CoberturaResponse;
import com.rotavital.api.dto.indicadores.EstoquePorTipoResponse;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.servico.HemocentroServico;
import com.rotavital.servico.IndicadorServico;

/**
 * Telas do painel operacional, renderizadas pelo servidor (PI3-133).
 *
 * <p>Diferente dos controllers em {@code com.rotavital.api}, que devolvem JSON,
 * este devolve o nome de um template. Os dados vem dos mesmos servicos, entao a
 * regra de negocio nao existe em dois lugares: o painel e a API enxergam o mesmo
 * estoque, as mesmas rotas e os mesmos alertas.
 *
 * <p>O alvo e quem opera um hemocentro: a tela mostra onde ele esta, o que esta
 * em transito agora e o que vence antes do que deveria.
 */
@Controller
public class PainelWebController {

    /** Uma bolsa a menos de 7 dias do vencimento ja entra como alerta. */
    private static final int DIAS_PARA_ALERTA = 7;

    private final IndicadorServico indicadorServico;
    private final HemocentroServico hemocentroServico;

    public PainelWebController(IndicadorServico indicadorServico,
                               HemocentroServico hemocentroServico) {
        this.indicadorServico = indicadorServico;
        this.hemocentroServico = hemocentroServico;
    }

    /**
     * Painel principal: o estado do estoque do ponto de vista de quem opera.
     */
    @GetMapping("/painel/{id}")
    public String painel(@PathVariable String id, Model model) {
        LocalDate hoje = LocalDate.now();

        List<EstoquePorTipoResponse> estoque = indicadorServico.estoquePorTipoSanguineo();
        List<CoberturaResponse> cobertura = indicadorServico.coberturaPorTipo(hoje);
        BolsaCriticaResponse criticas =
                indicadorServico.bolsasProximasDoVencimento(DIAS_PARA_ALERTA, hoje);

        model.addAttribute("estoque", estoque);
        model.addAttribute("cobertura", cobertura);
        model.addAttribute("criticas", criticas);
        model.addAttribute("centroAtual", hemocentroServico.buscar(id));
        model.addAttribute("agora", LocalDateTime.now());
        model.addAttribute("centroId", id);
        model.addAttribute("aba", "painel");

        return "painel/indicadores";
    }

}
