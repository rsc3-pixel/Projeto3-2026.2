package com.rotavital.dominio;

import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRequisicao;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
public class Requisicao {

    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @Enumerated(EnumType.STRING)
    private PrioridadeRequisicao prioridade;

    private LocalDateTime dataHoraCriacao;
    private LocalDateTime prazoEntrega;
    private String observacao;

    @Enumerated(EnumType.STRING)
    private StatusRequisicao status;

    @OneToMany(mappedBy = "requisicao", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ItemRequisicao> itens = new ArrayList<>();

    protected Requisicao() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public Requisicao(String id, Hospital hospital, PrioridadeRequisicao prioridade,
                      LocalDateTime dataHoraCriacao, LocalDateTime prazoEntrega) {
        this.id = id;
        this.hospital = hospital;
        this.prioridade = prioridade;
        this.dataHoraCriacao = dataHoraCriacao;
        this.prazoEntrega = prazoEntrega;
        this.status = StatusRequisicao.PENDENTE;
    }

    public String getId()                       { return id; }
    public Hospital getHospital()               { return hospital; }
    public PrioridadeRequisicao getPrioridade() { return prioridade; }
    public LocalDateTime getDataHoraCriacao()   { return dataHoraCriacao; }
    public LocalDateTime getPrazoEntrega()      { return prazoEntrega; }
    public String getObservacao()               { return observacao; }
    public StatusRequisicao getStatus()         { return status; }

    public void setPrazoEntrega(LocalDateTime prazoEntrega) { this.prazoEntrega = prazoEntrega; }
    public void setObservacao(String observacao)            { this.observacao = observacao; }

    public List<ItemRequisicao> getItens() {
        return Collections.unmodifiableList(itens);
    }

    // -----------------------------------------------------------------------
    // Itens
    // -----------------------------------------------------------------------

    /**
     * Adiciona o item e amarra os dois lados da relacao. Sem o
     * {@code item.setRequisicao(this)} o lado dono ficaria nulo e o JPA
     * gravaria a linha sem a chave estrangeira.
     */
    public void adicionarItem(ItemRequisicao item) {
        exigirAberta();
        itens.add(item);
        item.setRequisicao(this);
        recalcularStatus();
    }

    public void removerItem(String itemId) {
        exigirAberta();
        ItemRequisicao item = item(itemId);
        if (item.getQuantidadeAlocada() > 0) {
            throw new OperacaoInvalidaException(
                    "Item possui bolsas alocadas. Desfaca as alocacoes antes de remover.");
        }
        itens.remove(item);
        recalcularStatus();
    }

    public ItemRequisicao item(String itemId) {
        return itens.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("ItemRequisicao", itemId));
    }

    // -----------------------------------------------------------------------
    // Alocacoes: o ponto onde estoque e demanda se encontram
    // -----------------------------------------------------------------------

    /**
     * Vincula a bolsa ao item e a reserva. Quem decide se a bolsa serve e a
     * propria Bolsa ({@link Bolsa#exigirQuePodeAtender}), a mesma regra usada
     * pela selecao automatica: as duas portas de entrada nao podem divergir.
     */
    public Alocacao alocar(String itemId, Bolsa bolsa, LocalDateTime agora) {
        exigirAberta();
        ItemRequisicao item = item(itemId);
        item.exigirPendente();
        LocalDate hoje = agora.toLocalDate();
        bolsa.exigirQuePodeAtender(item.getGrupoSanguineo(), item.getTipo(), hoje);
        bolsa.reservar(hoje);

        Alocacao alocacao = new Alocacao(UUID.randomUUID().toString(), bolsa, item, agora);
        item.adicionarAlocacao(alocacao);
        recalcularStatus();
        return alocacao;
    }

    /**
     * Desfaz a alocacao enquanto a bolsa so esta reservada. Depois que ela
     * embarca, a reserva virou entrega e nao se desfaz por aqui.
     *
     * @return a bolsa liberada, para quem precisar avisar o estoque
     */
    public Bolsa desfazerAlocacao(String itemId, String alocacaoId) {
        ItemRequisicao item = item(itemId);
        Alocacao alocacao = item.getAlocacoes().stream()
                .filter(a -> a.getId().equals(alocacaoId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alocacao", alocacaoId));

        Bolsa bolsa = alocacao.getBolsa();
        if (bolsa.getStatus() != StatusBolsa.RESERVADA) {
            throw new OperacaoInvalidaException(
                    "A bolsa ja saiu para entrega e a alocacao nao pode ser desfeita. "
                    + "Status atual: " + bolsa.getStatus());
        }
        bolsa.liberar();
        item.removerAlocacao(alocacao);
        recalcularStatus();
        return bolsa;
    }

    /**
     * Cancela a requisicao e libera as bolsas reservadas. O que impede o
     * cancelamento nao e o status da requisicao, e o das bolsas: ATENDIDA so
     * significa que tudo foi reservado, e reserva se desfaz. Depois que a
     * bolsa embarca, nao.
     *
     * @return as bolsas liberadas; vazia se a requisicao ja estava cancelada
     */
    public List<Bolsa> cancelar() {
        if (status == StatusRequisicao.CANCELADA) {
            return List.of();
        }
        boolean bolsaJaSaiu = itens.stream()
                .flatMap(item -> item.getAlocacoes().stream())
                .anyMatch(a -> a.getBolsa().getStatus() != StatusBolsa.RESERVADA);
        if (bolsaJaSaiu) {
            throw new OperacaoInvalidaException(
                    "Ha bolsas ja despachadas para esta requisicao. Cancele a rota antes.");
        }

        List<Bolsa> liberadas = new ArrayList<>();
        for (ItemRequisicao item : itens) {
            for (Alocacao alocacao : List.copyOf(item.getAlocacoes())) {
                alocacao.getBolsa().liberar();
                liberadas.add(alocacao.getBolsa());
                item.removerAlocacao(alocacao);
            }
        }
        status = StatusRequisicao.CANCELADA;
        return liberadas;
    }

    public boolean estaCompleta() {
        return !itens.isEmpty() && itens.stream().allMatch(ItemRequisicao::estaAtendido);
    }

    private void exigirAberta() {
        if (status == StatusRequisicao.CANCELADA) {
            throw new OperacaoInvalidaException("Requisicao cancelada nao aceita alteracoes");
        }
    }

    /**
     * O status deriva das alocacoes; por isso e privado e nao existe
     * setStatus. Requisicao cancelada nao muda.
     */
    private void recalcularStatus() {
        if (status == StatusRequisicao.CANCELADA) {
            return;
        }
        if (estaCompleta()) {
            status = StatusRequisicao.ATENDIDA;
        } else if (itens.stream().anyMatch(i -> i.getQuantidadeAlocada() > 0)) {
            status = StatusRequisicao.PARCIALMENTE_ATENDIDA;
        } else {
            status = StatusRequisicao.PENDENTE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Requisicao)) return false;
        Requisicao requisicao = (Requisicao) o;
        return Objects.equals(id, requisicao.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
