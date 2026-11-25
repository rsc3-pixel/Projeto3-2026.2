package com.rotavital.dominio;

import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
public class Rota {

    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "origem_id")
    private Hemocentro origem;

    @ManyToOne
    @JoinColumn(name = "destino_id")
    private Hospital destino;

    private LocalDateTime dataHoraSaida;
    private LocalDateTime dataHoraChegadaPrevista;
    private LocalDateTime saidaReal;
    private LocalDateTime chegadaReal;

    @Enumerated(EnumType.STRING)
    private StatusRota status;

    /**
     * Tempo estimado do caminho calculado, em minutos (HU-05). Nulo quando a
     * rota foi planejada com previsao de chegada informada pelo usuario e a
     * malha nao tinha caminho entre as unidades.
     */
    private Double tempoEstimadoMinutos;

    /**
     * Paradas do caminho calculado, da origem ao destino, como ids separados
     * por virgula. Uma coluna de texto em vez de uma tabela de juncao: o
     * caminho e um registro do que foi planejado, nunca editado parada a
     * parada, e a lista sai pronta em {@link #getCaminho()}.
     */
    private String caminhoIds;

    /**
     * Bolsas embarcadas. Tabela de juncao propria porque a bolsa ja tem
     * {@code @ManyToOne} para o hemocentro de origem: usar a mesma coluna
     * para duas relacoes distintas confundiria o mapeamento.
     */
    @ManyToMany
    @JoinTable(
            name = "rota_bolsa",
            joinColumns = @JoinColumn(name = "rota_id"),
            inverseJoinColumns = @JoinColumn(name = "bolsa_codigo_rastreio"))
    private final List<Bolsa> bolsas = new ArrayList<>();

    protected Rota() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public Rota(String id, Hemocentro origem, Hospital destino,
                LocalDateTime dataHoraSaida, LocalDateTime dataHoraChegadaPrevista) {
        this.id = id;
        this.origem = origem;
        this.destino = destino;
        this.dataHoraSaida = dataHoraSaida;
        this.dataHoraChegadaPrevista = dataHoraChegadaPrevista;
        this.status = StatusRota.PLANEJADA;
    }

    public String getId()                             { return id; }
    public Hemocentro getOrigem()                     { return origem; }
    public Hospital getDestino()                      { return destino; }
    public LocalDateTime getDataHoraSaida()           { return dataHoraSaida; }
    public LocalDateTime getDataHoraChegadaPrevista() { return dataHoraChegadaPrevista; }
    public LocalDateTime getSaidaReal()               { return saidaReal; }
    public LocalDateTime getChegadaReal()             { return chegadaReal; }
    public StatusRota getStatus()                     { return status; }

    public Double getTempoEstimadoMinutos() { return tempoEstimadoMinutos; }

    /** Paradas do caminho calculado, da origem ao destino; vazia se nao houve calculo. */
    public List<String> getCaminho() {
        if (caminhoIds == null || caminhoIds.isBlank()) {
            return List.of();
        }
        return List.of(caminhoIds.split(","));
    }

    /**
     * Registra o caminho calculado pela roteirizacao e o tempo estimado.
     *
     * @param paradas ids das unidades em ordem, da origem ao destino
     * @param minutos tempo total estimado do caminho
     */
    public void registrarCaminho(List<String> paradas, double minutos) {
        this.caminhoIds = String.join(",", paradas);
        this.tempoEstimadoMinutos = minutos;
    }

    public List<Bolsa> getBolsas() {
        return Collections.unmodifiableList(bolsas);
    }

    // -----------------------------------------------------------------------
    // Embarque: so em rota PLANEJADA, so bolsa reservada no hemocentro de origem
    // -----------------------------------------------------------------------

    public void embarcar(Bolsa bolsa) {
        exigirPlanejada("embarcar em");
        if (bolsas.contains(bolsa)) {
            throw new OperacaoInvalidaException("Bolsa ja embarcada nesta rota");
        }
        if (bolsa.getStatus() != StatusBolsa.RESERVADA) {
            throw new OperacaoInvalidaException(
                    "So bolsa RESERVADA pode ser embarcada. Status atual: " + bolsa.getStatus());
        }
        if (bolsa.getHemocentroOrigem() == null || !bolsa.getHemocentroOrigem().getId().equals(origem.getId())) {
            throw new OperacaoInvalidaException(
                    "A bolsa esta em outro hemocentro e nao pode sair nesta rota");
        }
        bolsas.add(bolsa);
    }

    public void desembarcar(String codigoRastreio) {
        exigirPlanejada("desembarcar de");
        Bolsa bolsa = bolsas.stream()
                .filter(b -> b.getCodigoRastreio().equals(codigoRastreio))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bolsa na rota", codigoRastreio));
        bolsas.remove(bolsa);
    }

    private void exigirPlanejada(String acao) {
        if (status != StatusRota.PLANEJADA) {
            throw new OperacaoInvalidaException(
                    "So e possivel " + acao + " rota PLANEJADA. Status atual: " + status);
        }
    }

    // -----------------------------------------------------------------------
    // Viagem: a rota arrasta o status das bolsas embarcadas
    // -----------------------------------------------------------------------

    public void sair(LocalDateTime quando) {
        if (bolsas.isEmpty()) {
            throw new OperacaoInvalidaException("Rota sem bolsas embarcadas nao pode sair");
        }
        mudarPara(StatusRota.EM_TRANSITO);
        this.saidaReal = quando;
        bolsas.forEach(Bolsa::despachar);
    }

    public void concluir(LocalDateTime quando) {
        mudarPara(StatusRota.CONCLUIDA);
        this.chegadaReal = quando;
        bolsas.forEach(Bolsa::entregar);
    }

    /** Rota cancelada nao consome bolsa: a carga volta ao hemocentro, ainda reservada. */
    public void cancelar() {
        mudarPara(StatusRota.CANCELADA);
        bolsas.forEach(Bolsa::retornarAoHemocentro);
    }

    private void mudarPara(StatusRota destino) {
        if (!status.podeIrPara(destino)) {
            throw new OperacaoInvalidaException("Transicao invalida: " + status + " -> " + destino);
        }
        this.status = destino;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rota)) return false;
        Rota rota = (Rota) o;
        return Objects.equals(id, rota.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
