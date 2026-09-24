package com.rotavital.dominio;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;
import java.util.Objects;

@Entity
public class Bolsa {

    @Id
    private String codigoRastreio;

    @Enumerated(EnumType.STRING)
    private TipoHemocomponente tipo;

    @Enumerated(EnumType.STRING)
    private GrupoSanguineo grupoSanguineo;

    private int volumeMl;
    private LocalDate dataColeta;
    private LocalDate dataValidade;

    @ManyToOne
    @JoinColumn(name = "hemocentro_origem_id")
    private Hemocentro hemocentroOrigem;

    @Enumerated(EnumType.STRING)
    private StatusBolsa status;

    protected Bolsa() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public Bolsa(String codigoRastreio, TipoHemocomponente tipo,
                 GrupoSanguineo grupoSanguineo, int volumeMl,
                 LocalDate dataColeta, Hemocentro hemocentroOrigem) {
        this.codigoRastreio = codigoRastreio;
        this.tipo = tipo;
        this.grupoSanguineo = grupoSanguineo;
        this.volumeMl = volumeMl;
        this.dataColeta = dataColeta;
        this.dataValidade = dataColeta.plusDays(tipo.getValidadeDias());
        this.hemocentroOrigem = hemocentroOrigem;
        this.status = StatusBolsa.DISPONIVEL;
    }

    public String getCodigoRastreio()         { return codigoRastreio; }
    public TipoHemocomponente getTipo()       { return tipo; }
    public GrupoSanguineo getGrupoSanguineo() { return grupoSanguineo; }
    public int getVolumeMl()                  { return volumeMl; }
    public LocalDate getDataColeta()          { return dataColeta; }
    public LocalDate getDataValidade()        { return dataValidade; }
    public Hemocentro getHemocentroOrigem()   { return hemocentroOrigem; }
    public StatusBolsa getStatus()            { return status; }

    public void setStatus(StatusBolsa status) { this.status = status; }

    public boolean estaVencida(LocalDate dataReferencia) {
        return dataReferencia.isAfter(dataValidade);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bolsa)) return false;
        Bolsa bolsa = (Bolsa) o;
        return Objects.equals(codigoRastreio, bolsa.codigoRastreio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigoRastreio);
    }

    @Override
    public String toString() {
        return "Bolsa[" + codigoRastreio + "] "
                + tipo.getDescricao() + " " + grupoSanguineo.getDescricao()
                + " val:" + dataValidade + " status:" + status;
    }
}
