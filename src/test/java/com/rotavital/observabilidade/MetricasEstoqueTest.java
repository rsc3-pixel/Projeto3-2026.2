package com.rotavital.observabilidade;

import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.BolsaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gauges de estoque: valores vindos das consultas agrupadas, cache de 30 s e
 * tolerancia a falha do banco.
 */
class MetricasEstoqueTest {

    private SimpleMeterRegistry registro;
    private BolsaRepository bolsas;
    private RelogioAjustavel relogio;

    @BeforeEach
    void preparar() {
        registro = new SimpleMeterRegistry();
        bolsas = mock(BolsaRepository.class);
        relogio = new RelogioAjustavel(Instant.parse("2026-11-15T12:00:00Z"));

        when(bolsas.contarPorTipoEStatus()).thenReturn(List.of(
                linha(TipoHemocomponente.CONCENTRADO_HEMACIAS, StatusBolsa.DISPONIVEL, 12L),
                linha(TipoHemocomponente.CONCENTRADO_HEMACIAS, StatusBolsa.RESERVADA, 3L),
                linha(TipoHemocomponente.CONCENTRADO_PLAQUETAS, StatusBolsa.DISPONIVEL, 4L)));
        when(bolsas.contarPorTipoComValidadeEntre(eq(StatusBolsa.DISPONIVEL), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {TipoHemocomponente.CONCENTRADO_PLAQUETAS, 2L}));
        when(bolsas.contarPorTipoComValidadeAntesDe(eq(StatusBolsa.DISPONIVEL), any()))
                .thenReturn(List.<Object[]>of(new Object[] {TipoHemocomponente.CONCENTRADO_HEMACIAS, 1L}));

        new MetricasEstoque(registro, bolsas, relogio);
    }

    private static Object[] linha(TipoHemocomponente tipo, StatusBolsa status, long quantidade) {
        return new Object[] {tipo, status, quantidade};
    }

    private double bolsas(TipoHemocomponente tipo, StatusBolsa status) {
        return registro.get(MetricasEstoque.BOLSAS)
                .tag("tipo", tipo.name()).tag("status", status.name())
                .gauge().value();
    }

    private double gauge(String nome, TipoHemocomponente tipo) {
        return registro.get(nome).tag("tipo", tipo.name()).gauge().value();
    }

    @Test
    @DisplayName("Uma serie por tipo e status, com zero onde a consulta nao trouxe linha")
    void contagemPorTipoEStatus() {
        assertThat(bolsas(TipoHemocomponente.CONCENTRADO_HEMACIAS, StatusBolsa.DISPONIVEL)).isEqualTo(12);
        assertThat(bolsas(TipoHemocomponente.CONCENTRADO_HEMACIAS, StatusBolsa.RESERVADA)).isEqualTo(3);
        assertThat(bolsas(TipoHemocomponente.CONCENTRADO_PLAQUETAS, StatusBolsa.DISPONIVEL)).isEqualTo(4);
        assertThat(bolsas(TipoHemocomponente.CRIOPRECIPITADO, StatusBolsa.DISPONIVEL)).isZero();

        int esperado = TipoHemocomponente.values().length * StatusBolsa.values().length;
        assertThat(registro.get(MetricasEstoque.BOLSAS).gauges()).hasSize(esperado);
    }

    @Test
    @DisplayName("Vencendo em breve e vencidas ainda disponiveis, por tipo")
    void vencimento() {
        assertThat(gauge(MetricasEstoque.VENCENDO, TipoHemocomponente.CONCENTRADO_PLAQUETAS)).isEqualTo(2);
        assertThat(gauge(MetricasEstoque.VENCENDO, TipoHemocomponente.CONCENTRADO_HEMACIAS)).isZero();
        assertThat(gauge(MetricasEstoque.VENCIDAS_DISPONIVEIS, TipoHemocomponente.CONCENTRADO_HEMACIAS))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Ler todas as series dentro de 30 s consulta o banco uma vez so")
    void cacheEvitaConsultaPorSerie() {
        registro.get(MetricasEstoque.BOLSAS).gauges().forEach(g -> g.value());
        registro.get(MetricasEstoque.VENCENDO).gauges().forEach(g -> g.value());
        relogio.avancar(Duration.ofSeconds(29));
        registro.get(MetricasEstoque.BOLSAS).gauges().forEach(g -> g.value());

        verify(bolsas, times(1)).contarPorTipoEStatus();

        relogio.avancar(Duration.ofSeconds(2));
        bolsas(TipoHemocomponente.CONCENTRADO_HEMACIAS, StatusBolsa.DISPONIVEL);

        verify(bolsas, times(2)).contarPorTipoEStatus();
    }

    @Test
    @DisplayName("Se o banco falhar, o gauge mantem o ultimo valor em vez de quebrar a leitura")
    void falhaDoBancoMantemUltimoValor() {
        assertThat(bolsas(TipoHemocomponente.CONCENTRADO_HEMACIAS, StatusBolsa.DISPONIVEL)).isEqualTo(12);

        when(bolsas.contarPorTipoEStatus()).thenThrow(new IllegalStateException("banco fora"));
        relogio.avancar(Duration.ofMinutes(1));

        assertThat(bolsas(TipoHemocomponente.CONCENTRADO_HEMACIAS, StatusBolsa.DISPONIVEL)).isEqualTo(12);
    }

    /** Relogio que o teste avanca a mao, para exercitar a expiracao do cache. */
    private static final class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
