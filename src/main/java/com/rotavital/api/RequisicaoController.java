package com.rotavital.api;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AlocacaoResponse;
import com.rotavital.api.dto.AtualizarStatusRequisicaoRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.ItemRequisicaoResponse;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.api.dto.RequisicaoResponse;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusRequisicao;
import com.rotavital.servico.AlocacaoComRepeticao;
import com.rotavital.servico.RequisicaoServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Endpoints de requisicao, item e alocacao, conforme a secao 2.4 do
 * docs/contrato_api.md.
 *
 * <p>Item e alocacao sao sub-recursos: so existem dentro de uma requisicao,
 * por isso a URL aninhada em vez de {@code /api/v1/itens}.</p>
 */
@Tag(name = "Requisições")
@RestController
@RequestMapping("/api/v1/requisicoes")
public class RequisicaoController {

    private final RequisicaoServico requisicoes;

    /** Repete a alocacao quando outra operacao chega primeiro (PI3-108). */
    private final AlocacaoComRepeticao alocacaoComRepeticao;

    public RequisicaoController(RequisicaoServico requisicoes,
                                AlocacaoComRepeticao alocacaoComRepeticao) {
        this.requisicoes = requisicoes;
        this.alocacaoComRepeticao = alocacaoComRepeticao;
    }

    // -----------------------------------------------------------------------
    // Requisicao
    // -----------------------------------------------------------------------

    @GetMapping
    public List<RequisicaoResponse> listar(
            @RequestParam(required = false) StatusRequisicao status,
            @RequestParam(required = false) String hospitalId,
            @RequestParam(required = false) PrioridadeRequisicao prioridade) {
        return requisicoes.listar(status, hospitalId, prioridade);
    }

    @GetMapping("/{id}")
    public RequisicaoResponse buscar(@PathVariable String id) {
        return requisicoes.detalhar(id);
    }

    @PostMapping
    public ResponseEntity<RequisicaoResponse> criar(@Valid @RequestBody RequisicaoRequest dados) {
        RequisicaoResponse criada = requisicoes.criar(dados);
        return ResponseEntity
                .created(URI.create("/api/v1/requisicoes/" + criada.id()))
                .body(criada);
    }

    @PatchMapping("/{id}")
    public RequisicaoResponse atualizarStatus(
            @PathVariable String id,
            @Valid @RequestBody AtualizarStatusRequisicaoRequest dados) {
        return requisicoes.atualizarStatus(id, dados.status());
    }

    // -----------------------------------------------------------------------
    // Itens
    // -----------------------------------------------------------------------

    @GetMapping("/{id}/itens")
    public List<ItemRequisicaoResponse> listarItens(@PathVariable String id) {
        return requisicoes.listarItens(id);
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<ItemRequisicaoResponse> adicionarItem(
            @PathVariable String id,
            @Valid @RequestBody ItemRequisicaoRequest dados) {
        ItemRequisicaoResponse item = requisicoes.adicionarItem(id, dados);
        return ResponseEntity
                .created(URI.create("/api/v1/requisicoes/" + id + "/itens/" + item.id()))
                .body(item);
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    public ResponseEntity<Void> removerItem(@PathVariable String id,
                                            @PathVariable String itemId) {
        requisicoes.removerItem(id, itemId);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Alocacoes
    // -----------------------------------------------------------------------

    @GetMapping("/{id}/itens/{itemId}/alocacoes")
    public List<AlocacaoResponse> listarAlocacoes(@PathVariable String id,
                                                  @PathVariable String itemId) {
        return requisicoes.listarAlocacoes(id, itemId);
    }

    @PostMapping("/{id}/itens/{itemId}/alocacoes")
    public ResponseEntity<AlocacaoResponse> alocar(@PathVariable String id,
                                                   @PathVariable String itemId,
                                                   @Valid @RequestBody AlocacaoRequest dados) {
        AlocacaoResponse alocacao = alocacaoComRepeticao.alocar(id, itemId, dados);
        return ResponseEntity
                .created(URI.create("/api/v1/requisicoes/" + id + "/itens/" + itemId
                        + "/alocacoes/" + alocacao.id()))
                .body(alocacao);
    }

    @Operation(summary = "Aloca automaticamente a melhor bolsa para o item",
            description = "Consulta o indice de estoque por hash, filtra por compatibilidade "
                    + "ABO/Rh e escolhe por FEFO a bolsa de validade mais proxima. "
                    + "Nao recebe corpo: a bolsa e escolhida pelo sistema.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bolsa alocada"),
            @ApiResponse(responseCode = "404", description = "Requisicao ou item nao encontrado"),
            @ApiResponse(responseCode = "409",
                    description = "Sem estoque disponivel, item ja atendido ou requisicao fechada")
    })
    @PostMapping("/{id}/itens/{itemId}/alocacoes/automatica")
    public ResponseEntity<AlocacaoResponse> alocarAutomaticamente(@PathVariable String id,
                                                                  @PathVariable String itemId) {
        AlocacaoResponse alocacao = requisicoes.alocarAutomaticamente(id, itemId);
        return ResponseEntity
                .created(URI.create("/api/v1/requisicoes/" + id + "/itens/" + itemId
                        + "/alocacoes/" + alocacao.id()))
                .body(alocacao);
    }

    @DeleteMapping("/{id}/itens/{itemId}/alocacoes/{alocacaoId}")
    public ResponseEntity<Void> desfazerAlocacao(@PathVariable String id,
                                                 @PathVariable String itemId,
                                                 @PathVariable String alocacaoId) {
        requisicoes.desfazerAlocacao(id, itemId, alocacaoId);
        return ResponseEntity.noContent().build();
    }
}
