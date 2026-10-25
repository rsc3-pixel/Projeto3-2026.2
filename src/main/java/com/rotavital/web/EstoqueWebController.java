package com.rotavital.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.rotavital.alocacao.SelecaoFefo;
import com.rotavital.dominio.Bolsa;
import com.rotavital.servico.BolsaServico;
import com.rotavital.servico.HemocentroServico;
import com.rotavital.servico.RequisicaoServico;

/**
 * Telas internas de um hemocentro: estoque, fila FEFO e requisicoes (PI3-140).
 *
 * <p>As tres respondem perguntas diferentes de quem opera. O estoque mostra o
 * que existe; a fila mostra em que ordem sairia; as requisicoes mostram o que
 * foi pedido e quanto ja foi atendido.
 *
 * <p>Tudo vem dos mesmos servicos que a API REST usa, inclusive a ordenacao da
 * fila, que e a mesma classe usada na alocacao real. Reimplementar a ordem aqui
 * abriria espaco para a tela mostrar uma fila diferente da que o sistema segue.
 */
@Controller
public class EstoqueWebController {

    private final BolsaServico bolsaServico;
    private final RequisicaoServico requisicaoServico;
    private final HemocentroServico hemocentroServico;

    public EstoqueWebController(BolsaServico bolsaServico,
                                RequisicaoServico requisicaoServico,
                                HemocentroServico hemocentroServico) {
        this.bolsaServico = bolsaServico;
        this.requisicaoServico = requisicaoServico;
        this.hemocentroServico = hemocentroServico;
    }

    /** Todas as bolsas do hemocentro, com validade e status. */
    @GetMapping("/painel/{id}/estoque")
    public String estoque(@PathVariable String id, Model model) {
        List<Bolsa> bolsas = bolsaServico.listarPorHemocentro(id, null, null, null);

        model.addAttribute("bolsas", bolsas);
        model.addAttribute("hoje", LocalDate.now());
        comum(id, model);
        model.addAttribute("aba", "estoque");
        return "painel/estoque";
    }

    /**
     * A fila de saida, na ordem em que as bolsas seriam alocadas.
     *
     * <p>A ordenacao vem de {@link SelecaoFefo}, a mesma classe que a alocacao
     * usa. Ela descarta a bolsa vencida <b>antes</b> de ordenar: o FEFO poe no
     * topo quem vence primeiro, e uma bolsa vencida vence antes de todas as
     * validas, entao filtrar depois a deixaria em primeiro lugar sempre.
     */
    @GetMapping("/painel/{id}/fila")
    public String fila(@PathVariable String id, Model model) {
        LocalDate hoje = LocalDate.now();
        List<Bolsa> todas = bolsaServico.listarPorHemocentro(id, null, null, null);
        List<Bolsa> fila = SelecaoFefo.ordenar(todas, hoje);

        model.addAttribute("fila", fila);
        model.addAttribute("foraDaFila", todas.size() - fila.size());
        model.addAttribute("hoje", hoje);
        comum(id, model);
        model.addAttribute("aba", "fila");
        return "painel/fila";
    }

    /** As requisicoes da rede, com o quanto de cada item ja foi atendido. */
    @GetMapping("/painel/{id}/requisicoes")
    public String requisicoes(@PathVariable String id, Model model) {
        model.addAttribute("requisicoes", requisicaoServico.listar(null, null, null));
        comum(id, model);
        model.addAttribute("aba", "requisicoes");
        return "painel/requisicoes";
    }

    /** Dados que todas as telas do painel precisam. */
    private void comum(String id, Model model) {
        model.addAttribute("centroAtual", hemocentroServico.buscar(id));
        model.addAttribute("centroId", id);
        model.addAttribute("agora", LocalDateTime.now());
    }
}
