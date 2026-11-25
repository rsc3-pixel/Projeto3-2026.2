package com.rotavital.aplicacao;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AlocacaoResponse;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.ItemRequisicaoResponse;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.api.dto.RequisicaoResponse;
import com.rotavital.alocacao.SelecaoFefo;
import com.rotavital.dominio.Alocacao;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusRequisicao;
import com.rotavital.dominio.repositorio.BolsaRepository;
import com.rotavital.dominio.repositorio.RequisicaoRepository;
import com.rotavital.aplicacao.evento.BolsaAlterada;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RequisicaoServico {

    private final RequisicaoRepository requisicoes;
    private final BolsaRepository bolsas;
    private final HospitalServico hospitalServico;
    private final IndiceEstoqueServico indiceEstoque;
    private final ApplicationEventPublisher eventos;

    public RequisicaoServico(RequisicaoRepository requisicoes,
                             BolsaRepository bolsas,
                             HospitalServico hospitalServico,
                             IndiceEstoqueServico indiceEstoque,
                             ApplicationEventPublisher eventos) {
        this.requisicoes = requisicoes;
        this.bolsas = bolsas;
        this.hospitalServico = hospitalServico;
        this.indiceEstoque = indiceEstoque;
        this.eventos = eventos;
    }

    // -----------------------------------------------------------------------
    // Requisicao
    // -----------------------------------------------------------------------

    /**
     * Lista ja convertida para DTO.
     *
     * <p>A conversao acontece aqui dentro, e nao no controlador, porque
     * {@code RequisicaoResponse} percorre a lista de itens. Essa lista e
     * carregada por demanda: fora da transacao, o Hibernate ja fechou a sessao
     * e o acesso lanca {@code LazyInitializationException}.</p>
     */
    @Transactional(readOnly = true)
    public List<RequisicaoResponse> listar(StatusRequisicao status, String hospitalId,
                                           PrioridadeRequisicao prioridade) {
        return requisicoes.findAll().stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> prioridade == null || r.getPrioridade() == prioridade)
                .filter(r -> hospitalId == null
                        || (r.getHospital() != null && hospitalId.equals(r.getHospital().getId())))
                .map(RequisicaoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RequisicaoResponse> listarPorHospital(String hospitalId, StatusRequisicao status) {
        hospitalServico.buscar(hospitalId);
        return listar(status, hospitalId, null);
    }

    @Transactional(readOnly = true)
    public RequisicaoResponse detalhar(String id) {
        return RequisicaoResponse.de(buscar(id));
    }

    /**
     * Devolve a entidade. Uso interno dos demais metodos deste servico, que ja
     * rodam dentro de transacao. O controlador usa {@link #detalhar(String)}.
     */
    @Transactional(readOnly = true)
    public Requisicao buscar(String id) {
        return requisicoes.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Requisicao", id));
    }

    /**
     * Cria a requisicao com os itens pedidos.
     *
     * <p>O prazo de entrega e opcional no corpo. Quando o hospital nao
     * informa, ele e derivado da prioridade (HU-02, cenario 2): EMERGENCIA
     * em 2 horas, URGENTE em 12 e ROTINA em 72, contadas da criacao. A tabela
     * esta em {@link PrioridadeRequisicao#getPrazoHoras()}, e nao aqui, para
     * que a carga inicial e qualquer outro consumidor leiam o mesmo numero.</p>
     */
    @Transactional
    public RequisicaoResponse criar(RequisicaoRequest dados) {
        Hospital hospital = hospitalServico.buscar(dados.hospitalId());

        LocalDateTime criacao = LocalDateTime.now();
        LocalDateTime prazo = dados.prazoEntrega() != null
                ? dados.prazoEntrega()
                : criacao.plusHours(dados.prioridade().getPrazoHoras());

        Requisicao requisicao = new Requisicao(
                UUID.randomUUID().toString(),
                hospital,
                dados.prioridade(),
                criacao,
                prazo);
        requisicao.setObservacao(dados.observacao());

        for (ItemRequisicaoRequest item : dados.itens()) {
            requisicao.adicionarItem(novoItem(item));
        }

        return RequisicaoResponse.de(requisicoes.save(requisicao));
    }

    /**
     * O contrato so preve o cancelamento pelo PATCH. Os demais status sao
     * calculados a partir das alocacoes, entao aceitar qualquer valor aqui
     * deixaria o estado inconsistente com os itens.
     */
    @Transactional
    public RequisicaoResponse atualizarStatus(String id, StatusRequisicao novoStatus) {
        Requisicao requisicao = buscar(id);

        if (novoStatus != StatusRequisicao.CANCELADA) {
            throw new OperacaoInvalidaException(
                    "Somente o cancelamento pode ser feito manualmente. "
                    + "Os demais status derivam das alocacoes.");
        }

        requisicao.cancelar().forEach(this::avisarEstoque);
        return RequisicaoResponse.de(requisicoes.save(requisicao));
    }

    // -----------------------------------------------------------------------
    // Itens
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ItemRequisicaoResponse> listarItens(String requisicaoId) {
        return buscar(requisicaoId).getItens().stream()
                .map(ItemRequisicaoResponse::de)
                .toList();
    }

    @Transactional
    public ItemRequisicaoResponse adicionarItem(String requisicaoId, ItemRequisicaoRequest dados) {
        Requisicao requisicao = buscar(requisicaoId);
        ItemRequisicao item = novoItem(dados);
        requisicao.adicionarItem(item);
        requisicoes.save(requisicao);
        return ItemRequisicaoResponse.de(item);
    }

    @Transactional
    public void removerItem(String requisicaoId, String itemId) {
        Requisicao requisicao = buscar(requisicaoId);
        requisicao.removerItem(itemId);
        requisicoes.save(requisicao);
    }

    // -----------------------------------------------------------------------
    // Alocacoes
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AlocacaoResponse> listarAlocacoes(String requisicaoId, String itemId) {
        return buscar(requisicaoId).item(itemId).getAlocacoes().stream()
                .map(AlocacaoResponse::de)
                .toList();
    }

    /**
     * Vincula a bolsa escolhida pelo usuario a um item. As regras (bolsa
     * disponivel, na validade, mesmo hemocomponente, grupo compativel) ficam
     * no agregado {@link Requisicao#alocar} e na {@link Bolsa}; este metodo so
     * carrega, delega, salva e avisa o indice de estoque.
     */
    @Transactional
    public AlocacaoResponse alocar(String requisicaoId, String itemId,
                                   AlocacaoRequest dados) {
        Requisicao requisicao = buscar(requisicaoId);
        Bolsa bolsa = bolsas.findById(dados.bolsaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bolsa", dados.bolsaId()));

        Alocacao alocacao = requisicao.alocar(itemId, bolsa, LocalDateTime.now());
        // A bolsa sai do indice de estoque, mas so depois do commit: se esta
        // transacao reverter, ela continua disponivel e continua indexada.
        avisarEstoque(bolsa);
        requisicoes.save(requisicao);
        return AlocacaoResponse.de(alocacao);
    }

    /**
     * Aloca automaticamente a melhor bolsa disponivel para o item.
     *
     * <p>O {@link IndiceEstoqueServico} resolve em O(1) por grupo compativel
     * quais bolsas existem, e a {@link SelecaoFefo} escolhe a de validade mais
     * proxima. Depois de escolher, delega para
     * {@link #alocar(String, String, AlocacaoRequest)}: as duas portas de
     * entrada passam pela mesma regra do agregado.</p>
     *
     * @throws OperacaoInvalidaException se nao houver bolsa disponivel e
     *         compativel para a combinacao pedida
     */
    @Transactional
    public AlocacaoResponse alocarAutomaticamente(String requisicaoId, String itemId) {
        ItemRequisicao item = buscar(requisicaoId).item(itemId);
        item.exigirPendente();

        List<Bolsa> candidatas = indiceEstoque.buscarCompativeis(
                item.getGrupoSanguineo(), item.getTipo());

        Bolsa escolhida = SelecaoFefo.escolher(candidatas, LocalDate.now())
                .orElseThrow(() -> new OperacaoInvalidaException(
                        "Sem estoque disponivel para " + item.getGrupoSanguineo()
                        + " " + item.getTipo()));

        return alocar(requisicaoId, itemId, new AlocacaoRequest(escolhida.getCodigoRastreio()));
    }

    @Transactional
    public void desfazerAlocacao(String requisicaoId, String itemId, String alocacaoId) {
        Requisicao requisicao = buscar(requisicaoId);
        avisarEstoque(requisicao.desfazerAlocacao(itemId, alocacaoId));
        requisicoes.save(requisicao);
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private ItemRequisicao novoItem(ItemRequisicaoRequest dados) {
        return new ItemRequisicao(
                UUID.randomUUID().toString(),
                dados.tipoHemocomponente(),
                dados.grupoSanguineo(),
                dados.quantidadeSolicitada());
    }

    private void avisarEstoque(Bolsa bolsa) {
        eventos.publishEvent(new BolsaAlterada(bolsa));
    }
}
