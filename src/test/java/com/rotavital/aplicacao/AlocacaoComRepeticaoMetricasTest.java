package com.rotavital.aplicacao;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AlocacaoResponse;
import com.rotavital.observabilidade.MetricasDominio;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * O que a repeticao de alocacao (PI3-108) publica como metrica: sucesso de
 * primeira, sucesso depois de perder disputas, conflito que nao se resolveu e
 * recusa por regra de negocio.
 */
class AlocacaoComRepeticaoMetricasTest {

    private static final AlocacaoRequest PEDIDO = new AlocacaoRequest("BOLSA-SINTETICA-1");

    private SimpleMeterRegistry registro;
    private RequisicaoServico servico;
    private AlocacaoComRepeticao alocacao;

    @BeforeEach
    void preparar() {
        registro = new SimpleMeterRegistry();
        servico = mock(RequisicaoServico.class);
        alocacao = new AlocacaoComRepeticao(servico, new MetricasDominio(registro));
    }

    private double alocacoes(String resultado) {
        return registro.get("rotavital.alocacoes")
                .tags("modo", "manual", "resultado", resultado)
                .counter().count();
    }

    private long duracoes(String resultado) {
        return registro.get("rotavital.alocacoes.duracao")
                .tags("modo", "manual", "resultado", resultado)
                .timer().count();
    }

    private double repeticoes() {
        return registro.get("rotavital.alocacoes.repeticoes").counter().count();
    }

    private static OptimisticLockingFailureException disputa() {
        return new OptimisticLockingFailureException("outra alocacao chegou antes");
    }

    @Test
    @DisplayName("Alocou de primeira: um sucesso, nenhuma repeticao")
    void sucessoDePrimeira() {
        when(servico.alocar(any(), any(), any())).thenReturn(mock(AlocacaoResponse.class));

        alocacao.alocar("req", "item", PEDIDO);

        assertThat(alocacoes("sucesso")).isEqualTo(1);
        assertThat(repeticoes()).isZero();
        assertThat(duracoes("sucesso")).isEqualTo(1);
    }

    @Test
    @DisplayName("Perdeu uma disputa e alocou na segunda: sucesso apos repeticao")
    void sucessoAposRepeticao() {
        when(servico.alocar(any(), any(), any()))
                .thenThrow(disputa())
                .thenReturn(mock(AlocacaoResponse.class));

        alocacao.alocar("req", "item", PEDIDO);

        assertThat(alocacoes("sucesso_apos_repeticao")).isEqualTo(1);
        assertThat(alocacoes("sucesso")).isZero();
        assertThat(repeticoes()).isEqualTo(1);
        // Um tempo so, cobrindo as duas tentativas: e o que o usuario esperou.
        assertThat(duracoes("sucesso_apos_repeticao")).isEqualTo(1);
    }

    @Test
    @DisplayName("Perdeu as tres disputas: um conflito e duas repeticoes")
    void conflitoNaoResolvido() {
        when(servico.alocar(any(), any(), any())).thenThrow(disputa());

        assertThatThrownBy(() -> alocacao.alocar("req", "item", PEDIDO))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(alocacoes("conflito_concorrencia")).isEqualTo(1);
        // A terceira falha nao e repetida: so as duas primeiras contam.
        assertThat(repeticoes()).isEqualTo(2);
    }

    @Test
    @DisplayName("Regra de negocio negou: recusa, sem repetir")
    void recusaPorRegraDeNegocio() {
        when(servico.alocar(any(), any(), any()))
                .thenThrow(new OperacaoInvalidaException("Bolsa incompativel"));

        assertThatThrownBy(() -> alocacao.alocar("req", "item", PEDIDO))
                .isInstanceOf(OperacaoInvalidaException.class);

        assertThat(alocacoes("recusada")).isEqualTo(1);
        assertThat(repeticoes()).isZero();
        assertThat(duracoes("recusada")).isEqualTo(1);
    }
}
