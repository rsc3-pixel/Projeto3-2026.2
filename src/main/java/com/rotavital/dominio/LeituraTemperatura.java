package com.rotavital.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Leitura do sensor de temperatura de uma remessa em transito.
 *
 * <p><strong>A telemetria e simulada nesta fase do projeto</strong>, como o
 * README declara. Esta entidade registra o que um sensor reportaria; nao ha
 * integracao com hardware.</p>
 *
 * <p>A leitura pertence a rota, nao a bolsa. O sensor fica no compartimento
 * refrigerado do veiculo e mede o ambiente, nao cada bolsa individualmente.
 * Isso tem uma consequencia que o dominio precisa tratar: uma remessa pode
 * levar hemacias e plaquetas juntas, e a mesma leitura de 5 graus C esta
 * correta para as hemacias e e excursao para as plaquetas. Por isso o alerta
 * e avaliado por componente embarcado, nao por um valor unico de remessa.</p>
 */
@Entity
public class LeituraTemperatura {

    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "rota_id")
    private Rota rota;

    private double temperaturaCelsius;

    private LocalDateTime dataHora;

    protected LeituraTemperatura() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public LeituraTemperatura(String id, Rota rota, double temperaturaCelsius,
                              LocalDateTime dataHora) {
        this.id = id;
        this.rota = rota;
        this.temperaturaCelsius = temperaturaCelsius;
        this.dataHora = dataHora;
    }

    public String getId()                  { return id; }
    public Rota getRota()                  { return rota; }
    public double getTemperaturaCelsius()  { return temperaturaCelsius; }
    public LocalDateTime getDataHora()     { return dataHora; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeituraTemperatura outra)) return false;
        return Objects.equals(id, outra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
