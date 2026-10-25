package com.rotavital.dominio;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
public class ItemRequisicao {

    @Id
    private String id;

    /**
     * Versao da linha, para bloqueio otimista (PI3-108).
     *
     * <p>Duas alocacoes simultaneas para o mesmo item liam quantidadeAlocada,
     * somavam um e gravavam de volta. As duas liam o mesmo valor, e a segunda
     * escrita apagava a primeira: o item ficava marcado como menos atendido do
     * que estava, e o sistema alocava sangue a mais do que o hospital pediu.
     *
     * <p>Com a versao, a segunda transacao descobre que a linha mudou desde que
     * ela leu e falha com OptimisticLockingFailureException, em vez de
     * sobrescrever em silencio.
     */
    @Version
    private Long versao;

    @ManyToOne
    @JoinColumn(name = "requisicao_id")
    private Requisicao requisicao;

    @Enumerated(EnumType.STRING)
    private TipoHemocomponente tipo;

    @Enumerated(EnumType.STRING)
    private GrupoSanguineo grupoSanguineo;

    private int quantidadeSolicitada;
    private int quantidadeAlocada;

    @OneToMany(mappedBy = "itemRequisicao", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<Alocacao> alocacoes = new ArrayList<>();

    protected ItemRequisicao() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public ItemRequisicao(String id, TipoHemocomponente tipo,
                          GrupoSanguineo grupoSanguineo, int quantidadeSolicitada) {
        this.id = id;
        this.tipo = tipo;
        this.grupoSanguineo = grupoSanguineo;
        this.quantidadeSolicitada = quantidadeSolicitada;
        this.quantidadeAlocada = 0;
    }

    public String getId()                     { return id; }
    public Requisicao getRequisicao()         { return requisicao; }
    public TipoHemocomponente getTipo()       { return tipo; }
    public GrupoSanguineo getGrupoSanguineo() { return grupoSanguineo; }
    public int getQuantidadeSolicitada()      { return quantidadeSolicitada; }
    public int getQuantidadeAlocada()         { return quantidadeAlocada; }

    void setRequisicao(Requisicao requisicao) { this.requisicao = requisicao; }

    public List<Alocacao> getAlocacoes() {
        return Collections.unmodifiableList(alocacoes);
    }

    public void adicionarAlocacao(Alocacao alocacao) {
        alocacoes.add(alocacao);
        quantidadeAlocada++;
    }

    public boolean removerAlocacao(Alocacao alocacao) {
        boolean removeu = alocacoes.remove(alocacao);
        if (removeu) {
            quantidadeAlocada--;
        }
        return removeu;
    }

    public boolean estaAtendido() {
        return quantidadeAlocada >= quantidadeSolicitada;
    }

    public int quantidadePendente() {
        return quantidadeSolicitada - quantidadeAlocada;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemRequisicao)) return false;
        ItemRequisicao item = (ItemRequisicao) o;
        return Objects.equals(id, item.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
