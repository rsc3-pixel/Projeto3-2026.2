package com.rotavital.dominio;

import com.rotavital.compatibilidade.MatrizCompatibilidade;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

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

    public boolean estaVencida(LocalDate dataReferencia) {
        return dataReferencia.isAfter(dataValidade);
    }

    // -----------------------------------------------------------------------
    // Ciclo de vida: o status so muda por estas operacoes (PI3-247)
    // -----------------------------------------------------------------------

    /** Compromete a bolsa com uma requisicao. Bolsa vencida nao pode ser reservada. */
    public void reservar(LocalDate dataReferencia) {
        if (estaVencida(dataReferencia)) {
            throw new OperacaoInvalidaException("Bolsa vencida em " + dataValidade);
        }
        mudarPara(StatusBolsa.RESERVADA);
    }

    /** Desfaz a reserva: a bolsa volta ao estoque livre. */
    public void liberar() {
        mudarPara(StatusBolsa.DISPONIVEL);
    }

    /** A rota saiu com a bolsa embarcada. */
    public void despachar() {
        mudarPara(StatusBolsa.EM_TRANSITO);
    }

    /** A rota chegou ao hospital. */
    public void entregar() {
        mudarPara(StatusBolsa.ENTREGUE);
    }

    /** A rota foi cancelada: a carga volta ao hemocentro, ainda reservada para a requisicao. */
    public void retornarAoHemocentro() {
        if (status == StatusBolsa.EM_TRANSITO) {
            mudarPara(StatusBolsa.RESERVADA);
        }
    }

    public void descartar() {
        mudarPara(StatusBolsa.DESCARTADA);
    }

    private void mudarPara(StatusBolsa destino) {
        if (!status.podeIrPara(destino)) {
            throw new OperacaoInvalidaException("Transicao invalida: " + status + " -> " + destino);
        }
        this.status = destino;
    }

    // -----------------------------------------------------------------------
    // Elegibilidade: a unica definicao de "esta bolsa serve para este pedido"
    // -----------------------------------------------------------------------

    /** Livre e dentro da validade na data informada. A validade vale ate o proprio dia. */
    public boolean estaDisponivelEm(LocalDate dataReferencia) {
        return status == StatusBolsa.DISPONIVEL && !estaVencida(dataReferencia);
    }

    public boolean podeAtender(GrupoSanguineo receptor, TipoHemocomponente tipoPedido,
                               LocalDate dataReferencia) {
        return motivoDeRecusa(receptor, tipoPedido, dataReferencia).isEmpty();
    }

    /**
     * Por que a bolsa nao serve para o pedido, ou vazio se serve.
     *
     * <p>Compativel, nao identico: a bolsa e a doadora e o {@code receptor} e o
     * paciente, e a {@link MatrizCompatibilidade} decide pela tabela do
     * hemocomponente (a de plasma e invertida em relacao a de hemacias).
     * Ausencia de receptor ou de tipo nao autoriza transfusao.</p>
     */
    public Optional<String> motivoDeRecusa(GrupoSanguineo receptor, TipoHemocomponente tipoPedido,
                                           LocalDate dataReferencia) {
        if (status != StatusBolsa.DISPONIVEL) {
            return Optional.of("Bolsa nao esta disponivel. Status atual: " + status);
        }
        if (estaVencida(dataReferencia)) {
            return Optional.of("Bolsa vencida em " + dataValidade);
        }
        if (tipo != tipoPedido) {
            return Optional.of("Hemocomponente incompativel: item pede " + tipoPedido
                    + " e a bolsa e " + tipo);
        }
        if (!MatrizCompatibilidade.compativel(grupoSanguineo, receptor, tipoPedido)) {
            return Optional.of("Grupo sanguineo incompativel: item pede " + receptor
                    + " e a bolsa e " + grupoSanguineo
                    + ". Grupos aceitos para " + tipoPedido + ": "
                    + MatrizCompatibilidade.doadoresPara(receptor, tipoPedido));
        }
        return Optional.empty();
    }

    /** Lanca {@link OperacaoInvalidaException} com o motivo se a bolsa nao servir. */
    public void exigirQuePodeAtender(GrupoSanguineo receptor, TipoHemocomponente tipoPedido,
                                     LocalDate dataReferencia) {
        motivoDeRecusa(receptor, tipoPedido, dataReferencia).ifPresent(motivo -> {
            throw new OperacaoInvalidaException(motivo);
        });
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
