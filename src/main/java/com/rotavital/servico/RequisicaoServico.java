package com.rotavital.servico;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AlocacaoResponse;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.ItemRequisicaoResponse;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.api.dto.RequisicaoResponse;
import com.rotavital.alocacao.SelecaoFefo;
import com.rotavital.compatibilidade.MatrizCompatibilidade;
import com.rotavital.dominio.Alocacao;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRequisicao;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.RequisicaoRepository;
import com.rotavital.servico.evento.BolsaAlterada;
import com.rotavital.servico.excecao.OperacaoInvalidaException;
import com.rotavital.servico.excecao.RecursoNaoEncontradoException;
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

    @Transactional
    public RequisicaoResponse criar(RequisicaoRequest dados) {
        Hospital hospital = hospitalServico.buscar(dados.hospitalId());

        Requisicao requisicao = new Requisicao(
                UUID.randomUUID().toString(),
                hospital,
                dados.prioridade(),
                LocalDateTime.now(),
                dados.prazoEntrega());
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
        if (requisicao.getStatus() == StatusRequisicao.CANCELADA) {
            return RequisicaoResponse.de(requisicao);
        }

        // O que impede o cancelamento nao e o status da requisicao, e o das
        // bolsas: estar ATENDIDA so significa que tudo foi reservado, e
        // reserva se desfaz. Depois que a bolsa embarca, nao.
        boolean bolsaJaSaiu = requisicao.getItens().stream()
                .flatMap(item -> item.getAlocacoes().stream())
                .anyMatch(a -> a.getBolsa().getStatus() != StatusBolsa.RESERVADA);

        if (bolsaJaSaiu) {
            throw new OperacaoInvalidaException(
                    "Ha bolsas ja despachadas para esta requisicao. Cancele a rota antes.");
        }

        // Libera as bolsas que estavam presas a esta requisicao.
        for (ItemRequisicao item : requisicao.getItens()) {
            for (Alocacao alocacao : List.copyOf(item.getAlocacoes())) {
                alocacao.getBolsa().setStatus(StatusBolsa.DISPONIVEL);
                eventos.publishEvent(new BolsaAlterada(alocacao.getBolsa()));
                item.removerAlocacao(alocacao);
            }
        }

        requisicao.setStatus(StatusRequisicao.CANCELADA);
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
        exigirRequisicaoAberta(requisicao);

        ItemRequisicao item = novoItem(dados);
        requisicao.adicionarItem(item);
        requisicao.recalcularStatus();
        requisicoes.save(requisicao);
        return ItemRequisicaoResponse.de(item);
    }

    @Transactional
    public void removerItem(String requisicaoId, String itemId) {
        Requisicao requisicao = buscar(requisicaoId);
        exigirRequisicaoAberta(requisicao);

        ItemRequisicao item = itemDe(requisicao, itemId);
        if (item.getQuantidadeAlocada() > 0) {
            throw new OperacaoInvalidaException(
                    "Item possui bolsas alocadas. Desfaca as alocacoes antes de remover.");
        }

        requisicao.removerItem(item);
        requisicao.recalcularStatus();
        requisicoes.save(requisicao);
    }

    // -----------------------------------------------------------------------
    // Alocacoes
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AlocacaoResponse> listarAlocacoes(String requisicaoId, String itemId) {
        return itemDe(buscar(requisicaoId), itemId).getAlocacoes().stream()
                .map(AlocacaoResponse::de)
                .toList();
    }

    /**
     * Vincula uma bolsa a um item. E o ponto onde estoque e demanda se
     * encontram, entao concentra as regras de compatibilidade: bolsa
     * disponivel, dentro da validade, mesmo hemocomponente e grupo sanguineo
     * compativel.
     *
     * <p>Compativel, nao identico: a verificacao usa
     * {@link MatrizCompatibilidade}, entao um item que pede A+ aceita bolsa
     * O-, O+, A- ou A+. A regra varia por hemocomponente, porque plasma e
     * crioprecipitado seguem tabela invertida em relacao as hemacias.</p>
     */
    @Transactional
    public AlocacaoResponse alocar(String requisicaoId, String itemId,
                                   AlocacaoRequest dados) {
        Requisicao requisicao = buscar(requisicaoId);
        exigirRequisicaoAberta(requisicao);

        ItemRequisicao item = itemDe(requisicao, itemId);
        if (item.estaAtendido()) {
            throw new OperacaoInvalidaException("Item ja esta totalmente atendido");
        }

        Bolsa bolsa = bolsas.findById(dados.bolsaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bolsa", dados.bolsaId()));

        if (bolsa.getStatus() != StatusBolsa.DISPONIVEL) {
            throw new OperacaoInvalidaException(
                    "Bolsa nao esta disponivel. Status atual: " + bolsa.getStatus());
        }
        if (bolsa.estaVencida(LocalDate.now())) {
            throw new OperacaoInvalidaException(
                    "Bolsa vencida em " + bolsa.getDataValidade());
        }
        if (bolsa.getTipo() != item.getTipo()) {
            throw new OperacaoInvalidaException(
                    "Hemocomponente incompativel: item pede " + item.getTipo()
                    + " e a bolsa e " + bolsa.getTipo());
        }
        // A comparacao aqui e de compatibilidade transfusional, nao de
        // igualdade. Comparar os grupos com != recusaria doacao valida: um
        // paciente A+ pode receber de O-, O+, A- e A+, mas so A+ passaria,
        // deixando estoque utilizavel parado.
        //
        // A ordem dos argumentos importa, porque a relacao nao e simetrica: a
        // bolsa e a doadora, o item da requisicao e o receptor.
        if (!MatrizCompatibilidade.compativel(
                bolsa.getGrupoSanguineo(), item.getGrupoSanguineo(), item.getTipo())) {
            throw new OperacaoInvalidaException(
                    "Grupo sanguineo incompativel: item pede " + item.getGrupoSanguineo()
                    + " e a bolsa e " + bolsa.getGrupoSanguineo()
                    + ". Grupos aceitos para " + item.getTipo() + ": "
                    + MatrizCompatibilidade.doadoresPara(
                            item.getGrupoSanguineo(), item.getTipo()));
        }

        Alocacao alocacao = new Alocacao(
                UUID.randomUUID().toString(), bolsa, item, LocalDateTime.now());

        item.adicionarAlocacao(alocacao);
        bolsa.setStatus(StatusBolsa.RESERVADA);
        // A bolsa sai do indice de estoque, mas so depois do commit: se esta
        // transacao reverter, ela continua disponivel e continua indexada.
        eventos.publishEvent(new BolsaAlterada(bolsa));
        requisicao.recalcularStatus();
        requisicoes.save(requisicao);

        return AlocacaoResponse.de(alocacao);
    }

    /**
     * Aloca automaticamente a melhor bolsa disponivel para o item.
     *
     * <p>E o caminho que exercita o indice de estoque por hash: em vez de o
     * usuario escolher a bolsa e o sistema apenas validar, aqui o sistema
     * consulta o {@link IndiceEstoqueServico}, que resolve em O(1) por grupo
     * compativel quais bolsas existem, e aplica FEFO sobre o resultado.</p>
     *
     * <p>A compatibilidade ABO/Rh ja vem resolvida pelo indice, que consulta a
     * {@code MatrizCompatibilidade} para saber quais grupos procurar. Por isso
     * a selecao usa a sobrecarga de lista mais data da {@code SelecaoFefo}: a
     * bolsa escolhida e a de validade mais proxima entre as compativeis e
     * disponiveis, com desempate deterministico por codigo de rastreio.</p>
     *
     * <p>Depois de escolher, delega para
     * {@link #alocar(String, String, AlocacaoRequest)}. Todas as regras de
     * alocacao - status, validade, tipo, compatibilidade, troca de status da
     * bolsa e recalculo da requisicao - continuam num lugar so, e as duas
     * portas de entrada nao podem divergir.</p>
     *
     * @throws OperacaoInvalidaException se nao houver bolsa disponivel e
     *         compativel para a combinacao pedida
     */
    @Transactional
    public AlocacaoResponse alocarAutomaticamente(String requisicaoId, String itemId) {
        Requisicao requisicao = buscar(requisicaoId);
        exigirRequisicaoAberta(requisicao);

        ItemRequisicao item = itemDe(requisicao, itemId);
        if (item.estaAtendido()) {
            throw new OperacaoInvalidaException("Item ja esta totalmente atendido");
        }

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
        ItemRequisicao item = itemDe(requisicao, itemId);

        Alocacao alocacao = item.getAlocacoes().stream()
                .filter(a -> a.getId().equals(alocacaoId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alocacao", alocacaoId));

        if (alocacao.getBolsa().getStatus() != StatusBolsa.RESERVADA) {
            throw new OperacaoInvalidaException(
                    "A bolsa ja saiu para entrega e a alocacao nao pode ser desfeita. "
                    + "Status atual: " + alocacao.getBolsa().getStatus());
        }

        alocacao.getBolsa().setStatus(StatusBolsa.DISPONIVEL);
        eventos.publishEvent(new BolsaAlterada(alocacao.getBolsa()));
        item.removerAlocacao(alocacao);
        requisicao.recalcularStatus();
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

    private ItemRequisicao itemDe(Requisicao requisicao, String itemId) {
        return requisicao.getItens().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("ItemRequisicao", itemId));
    }

    private void exigirRequisicaoAberta(Requisicao requisicao) {
        if (requisicao.getStatus() == StatusRequisicao.CANCELADA) {
            throw new OperacaoInvalidaException("Requisicao cancelada nao aceita alteracoes");
        }
    }
}
