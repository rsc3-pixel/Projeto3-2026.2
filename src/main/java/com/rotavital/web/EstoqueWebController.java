package com.rotavital.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.rotavital.api.dto.BolsasEmRiscoResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.servico.BolsaServico;
import com.rotavital.servico.BolsaServico.FilaFefo;
import com.rotavital.servico.HemocentroServico;
import com.rotavital.servico.RequisicaoServico;

/**
 * Telas internas de um hemocentro: estoque, fila FEFO, estoque critico e
 * requisicoes (PI3-140, HU-04).
 *
 * <p>Cada tela responde uma pergunta diferente de quem opera. O estoque mostra
 * o que existe; a fila mostra em que ordem sairia; o estoque critico mostra o
 * que vence antes de sair se ninguem agir; as requisicoes mostram o que foi
 * pedido e quanto ja foi atendido.
 *
 * <p>Tudo vem dos mesmos servicos que a API REST usa, inclusive a ordenacao da
 * fila e o recorte de risco. Este controlador so escolhe a tela e entrega o
 * que o servico calculou: regra de negocio nenhuma mora aqui.
 */
@Controller
public class EstoqueWebController {

    /** Janelas que o painel de estoque critico oferece, em dias (HU-04). */
    static final List<Integer> JANELAS_DE_RISCO = List.of(7, 15, 30);

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
     * A fila de saida, na ordem em que as bolsas seriam alocadas. A ordem e
     * calculada por {@link BolsaServico#filaFefo}, a mesma regra da alocacao.
     */
    @GetMapping("/painel/{id}/fila")
    public String fila(@PathVariable String id, Model model) {
        LocalDate hoje = LocalDate.now();
        FilaFefo resultado = bolsaServico.filaFefo(id, hoje);

        model.addAttribute("fila", resultado.fila());
        model.addAttribute("foraDaFila", resultado.foraDaFila());
        model.addAttribute("hoje", hoje);
        comum(id, model);
        model.addAttribute("aba", "fila");
        return "painel/fila";
    }

    /**
     * Estoque critico (HU-04): o que vence nos proximos dias, separado do que
     * ja venceu. A janela vem da URL e e uma das {@link #JANELAS_DE_RISCO};
     * qualquer outro valor cai na primeira, para a tela nunca abrir com um
     * filtro que ela nao oferece.
     */
    @GetMapping("/painel/{id}/risco")
    public String risco(@PathVariable String id,
                        @RequestParam(defaultValue = "7") int dias,
                        Model model) {
        int janela = JANELAS_DE_RISCO.contains(dias) ? dias : JANELAS_DE_RISCO.get(0);
        BolsasEmRiscoResponse painel = bolsaServico.emRisco(janela, id, LocalDate.now());

        model.addAttribute("painelRisco", painel);
        model.addAttribute("janelas", JANELAS_DE_RISCO);
        comum(id, model);
        model.addAttribute("aba", "risco");
        return "painel/risco";
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
