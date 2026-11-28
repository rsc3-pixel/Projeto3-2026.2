package com.rotavital.observabilidade;

import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.dominio.repositorio.BolsaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Gauges do estoque de hemocomponentes: quantas bolsas existem por tipo e
 * status, quantas disponiveis vencem nos proximos dias e quantas ja venceram
 * sem sair do estoque disponivel.
 *
 * <p><strong>Por que um cache.</strong> O valor de um gauge e calculado na hora
 * em que o Prometheus le o {@code /actuator/prometheus}. Sao 28 series; sem
 * cache, cada leitura faria 28 consultas ao banco. Aqui, tres consultas
 * agrupadas ({@code GROUP BY}) alimentam todas as series, e o resultado vale
 * por {@link #VALIDADE}. Com o Prometheus lendo a cada 15 s, o banco recebe no
 * maximo tres consultas a cada 30 s.</p>
 *
 * <p>Se a consulta falhar, o gauge continua mostrando o ultimo valor bom em vez
 * de derrubar a leitura das metricas inteira.</p>
 */
@Component
public class MetricasEstoque {

    static final String BOLSAS = "rotavital.estoque.bolsas";
    static final String VENCENDO = "rotavital.estoque.vencendo";
    static final String VENCIDAS_DISPONIVEIS = "rotavital.estoque.vencidas.disponiveis";

    /** Quanto tempo uma contagem vale antes de consultar o banco de novo. */
    static final Duration VALIDADE = Duration.ofSeconds(30);

    /**
     * Janela de "vencendo em breve". Tres dias cobrem o tempo de reagir
     * (remanejar para outro hemocentro) mesmo para plaquetas, que vivem 5 dias.
     */
    static final int JANELA_VENCIMENTO_DIAS = 3;

    private static final Logger log = LoggerFactory.getLogger(MetricasEstoque.class);

    private final BolsaRepository bolsas;
    private final Clock relogio;

    private Retrato retrato = Retrato.VAZIO;
    private Instant consultadoEm = Instant.MIN;

    @Autowired
    public MetricasEstoque(MeterRegistry registro, BolsaRepository bolsas) {
        this(registro, bolsas, Clock.systemDefaultZone());
    }

    MetricasEstoque(MeterRegistry registro, BolsaRepository bolsas, Clock relogio) {
        this.bolsas = bolsas;
        this.relogio = relogio;
        registrarGauges(registro);
    }

    private void registrarGauges(MeterRegistry registro) {
        for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
            for (StatusBolsa status : StatusBolsa.values()) {
                Gauge.builder(BOLSAS, this, m -> m.atual().quantidade(tipo, status))
                        .description("Bolsas no estoque por tipo de hemocomponente e status")
                        .tag("tipo", tipo.name())
                        .tag("status", status.name())
                        .strongReference(true)
                        .register(registro);
            }
            Gauge.builder(VENCENDO, this, m -> m.atual().vencendo(tipo))
                    .description("Bolsas DISPONIVEIS que vencem nos proximos "
                            + JANELA_VENCIMENTO_DIAS + " dias")
                    .tag("tipo", tipo.name())
                    .tag("janela_dias", String.valueOf(JANELA_VENCIMENTO_DIAS))
                    .strongReference(true)
                    .register(registro);
            Gauge.builder(VENCIDAS_DISPONIVEIS, this, m -> m.atual().vencidas(tipo))
                    .description("Bolsas com validade expirada ainda marcadas como DISPONIVEL")
                    .tag("tipo", tipo.name())
                    .strongReference(true)
                    .register(registro);
        }
    }

    /** Devolve a contagem em cache, consultando o banco se ela expirou. */
    synchronized Retrato atual() {
        Instant agora = relogio.instant();
        if (consultadoEm.plus(VALIDADE).isAfter(agora)) {
            return retrato;
        }
        try {
            retrato = consultar(LocalDate.now(relogio));
            consultadoEm = agora;
        } catch (RuntimeException e) {
            // Mantem o ultimo retrato e tenta de novo na proxima leitura.
            log.warn("Falha ao atualizar metricas de estoque: {}", e.getMessage());
        }
        return retrato;
    }

    /** Faz a proxima leitura consultar o banco. Usado pelos testes de integracao. */
    synchronized void expirarCache() {
        consultadoEm = Instant.MIN;
    }

    private Retrato consultar(LocalDate hoje) {
        Map<TipoHemocomponente, Map<StatusBolsa, Long>> porTipoStatus =
                new EnumMap<>(TipoHemocomponente.class);
        for (Object[] linha : bolsas.contarPorTipoEStatus()) {
            porTipoStatus
                    .computeIfAbsent((TipoHemocomponente) linha[0],
                            t -> new EnumMap<>(StatusBolsa.class))
                    .put((StatusBolsa) linha[1], ((Number) linha[2]).longValue());
        }

        Map<TipoHemocomponente, Long> vencendo = porTipo(bolsas.contarPorTipoComValidadeEntre(
                StatusBolsa.DISPONIVEL, hoje, hoje.plusDays(JANELA_VENCIMENTO_DIAS)));

        // Mesmo criterio de Bolsa.estaVencida: vencida quando hoje ja passou
        // da data de validade.
        Map<TipoHemocomponente, Long> vencidas = porTipo(bolsas.contarPorTipoComValidadeAntesDe(
                StatusBolsa.DISPONIVEL, hoje));

        return new Retrato(porTipoStatus, vencendo, vencidas);
    }

    private static Map<TipoHemocomponente, Long> porTipo(List<Object[]> linhas) {
        Map<TipoHemocomponente, Long> resultado = new EnumMap<>(TipoHemocomponente.class);
        for (Object[] linha : linhas) {
            resultado.put((TipoHemocomponente) linha[0], ((Number) linha[1]).longValue());
        }
        return resultado;
    }

    /** Contagens de um instante. Tipo ou status ausente vale zero. */
    record Retrato(Map<TipoHemocomponente, Map<StatusBolsa, Long>> porTipoStatus,
                   Map<TipoHemocomponente, Long> porTipoVencendo,
                   Map<TipoHemocomponente, Long> porTipoVencidas) {

        static final Retrato VAZIO = new Retrato(Map.of(), Map.of(), Map.of());

        double quantidade(TipoHemocomponente tipo, StatusBolsa status) {
            return porTipoStatus.getOrDefault(tipo, Map.of()).getOrDefault(status, 0L);
        }

        double vencendo(TipoHemocomponente tipo) {
            return porTipoVencendo.getOrDefault(tipo, 0L);
        }

        double vencidas(TipoHemocomponente tipo) {
            return porTipoVencidas.getOrDefault(tipo, 0L);
        }
    }
}
