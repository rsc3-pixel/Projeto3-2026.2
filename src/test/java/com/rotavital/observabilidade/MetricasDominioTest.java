package com.rotavital.observabilidade;

import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.observabilidade.MetricasDominio.ModoAlocacao;
import com.rotavital.observabilidade.MetricasDominio.ResultadoAlocacao;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contadores de negocio publicados para o Prometheus.
 *
 * <p>Usa o {@link SimpleMeterRegistry}, em memoria: o que se testa e a
 * contagem e as tags, nao o formato de exportacao.</p>
 */
class MetricasDominioTest {

    private SimpleMeterRegistry registro;
    private MetricasDominio metricas;

    @BeforeEach
    void preparar() {
        registro = new SimpleMeterRegistry();
        metricas = new MetricasDominio(registro);
    }

    private double contador(String nome, String... tags) {
        return registro.get(nome).tags(tags).counter().count();
    }

    @Test
    @DisplayName("Todas as series nascem zeradas, para o painel mostrar 0 em vez de 'No data'")
    void seriesNascemZeradas() {
        assertThat(contador(MetricasDominio.LEITURAS, "resultado", "dentro_da_faixa")).isZero();
        assertThat(contador(MetricasDominio.LEITURAS, "resultado", "excursao")).isZero();
        for (TipoHemocomponente componente : TipoHemocomponente.values()) {
            assertThat(contador(MetricasDominio.EXCURSOES, "componente", componente.name())).isZero();
        }
        for (ModoAlocacao modo : ModoAlocacao.values()) {
            for (ResultadoAlocacao resultado : ResultadoAlocacao.values()) {
                assertThat(contador(MetricasDominio.ALOCACOES,
                        "modo", modo.tag(), "resultado", resultado.tag())).isZero();
                assertThat(registro.get(MetricasDominio.DURACAO)
                        .tags("modo", modo.tag(), "resultado", resultado.tag())
                        .timer().count()).isZero();
            }
        }
        assertThat(contador(MetricasDominio.REPETICOES)).isZero();
    }

    @Test
    @DisplayName("Leitura sem alerta conta como dentro da faixa e nao gera excursao")
    void leituraDentroDaFaixa() {
        metricas.leituraTemperatura(List.of());

        assertThat(contador(MetricasDominio.LEITURAS, "resultado", "dentro_da_faixa")).isEqualTo(1);
        assertThat(contador(MetricasDominio.LEITURAS, "resultado", "excursao")).isZero();
        assertThat(registro.get(MetricasDominio.EXCURSOES).counters())
                .allSatisfy(c -> assertThat(c.count()).isZero());
    }

    @Test
    @DisplayName("Remessa mista fora da faixa: uma leitura, uma excursao por componente afetado")
    void remessaMistaContaCadaComponente() {
        metricas.leituraTemperatura(List.of(
                TipoHemocomponente.CONCENTRADO_HEMACIAS,
                TipoHemocomponente.CONCENTRADO_PLAQUETAS));

        assertThat(contador(MetricasDominio.LEITURAS, "resultado", "excursao")).isEqualTo(1);
        assertThat(contador(MetricasDominio.EXCURSOES, "componente", "CONCENTRADO_HEMACIAS")).isEqualTo(1);
        assertThat(contador(MetricasDominio.EXCURSOES, "componente", "CONCENTRADO_PLAQUETAS")).isEqualTo(1);
        assertThat(contador(MetricasDominio.EXCURSOES, "componente", "CRIOPRECIPITADO")).isZero();
    }

    @Test
    @DisplayName("Alocacao e contada por modo e resultado")
    void alocacaoPorModoEResultado() {
        metricas.alocacao(ModoAlocacao.AUTOMATICA, ResultadoAlocacao.SUCESSO);
        metricas.alocacao(ModoAlocacao.AUTOMATICA, ResultadoAlocacao.SUCESSO);
        metricas.alocacao(ModoAlocacao.MANUAL, ResultadoAlocacao.RECUSADA);

        assertThat(contador(MetricasDominio.ALOCACOES, "modo", "automatica", "resultado", "sucesso"))
                .isEqualTo(2);
        assertThat(contador(MetricasDominio.ALOCACOES, "modo", "manual", "resultado", "recusada"))
                .isEqualTo(1);
        assertThat(contador(MetricasDominio.ALOCACOES, "modo", "manual", "resultado", "sucesso"))
                .isZero();
    }

    @Test
    @DisplayName("Timer de alocacao registra uma duracao por alocacao concluida")
    void timerDeAlocacao() {
        Timer.Sample inicio = metricas.iniciarAlocacao();
        metricas.alocacao(ModoAlocacao.AUTOMATICA, ResultadoAlocacao.SUCESSO, inicio);

        Timer timer = registro.get(MetricasDominio.DURACAO)
                .tags("modo", "automatica", "resultado", "sucesso").timer();
        assertThat(timer.count()).isEqualTo(1);
        // O contador anda junto com o timer.
        assertThat(contador(MetricasDominio.ALOCACOES, "modo", "automatica", "resultado", "sucesso"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Conflito de concorrencia e separado de recusa por regra de negocio")
    void classificacaoDoErro() {
        assertThat(MetricasDominio.classificar(new OptimisticLockingFailureException("disputa")))
                .isEqualTo(ResultadoAlocacao.CONFLITO_CONCORRENCIA);
        assertThat(MetricasDominio.classificar(new IllegalStateException("sem estoque")))
                .isEqualTo(ResultadoAlocacao.RECUSADA);
    }
}
