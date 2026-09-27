package com.rotavital.servico;

import com.rotavital.api.dto.ResultadoBenchmarkResponse;
import com.rotavital.servico.excecao.OperacaoInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkParaleloServicoTest {

    private final BenchmarkParaleloServico servico = new BenchmarkParaleloServico();
    private final LocalDate hoje = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("threads=1 roda a versao sequencial e threads=4 a versao paralela, com o mesmo resultado")
    void sequencialEParaleloDevolvemOMesmoResultado() {
        ResultadoBenchmarkResponse sequencial =
                servico.medirDispersaoValidade(5_000, 1, 42L, hoje);
        ResultadoBenchmarkResponse paralelo =
                servico.medirDispersaoValidade(5_000, 4, 42L, hoje);

        assertThat(sequencial.modo()).isEqualTo("SEQUENCIAL");
        assertThat(paralelo.modo()).isEqualTo("PARALELO");
        assertThat(sequencial.resultado()).isEqualTo(paralelo.resultado());
        assertThat(sequencial.tempoMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(paralelo.tempoMs()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("Reaproveita a massa cacheada para a mesma amostra e semente")
    void reaproveitaMassaCacheada() {
        ResultadoBenchmarkResponse primeira = servico.medirDispersaoValidade(1_000, 1, 7L, hoje);
        ResultadoBenchmarkResponse segunda = servico.medirDispersaoValidade(1_000, 2, 7L, hoje);

        assertThat(primeira.resultado()).isEqualTo(segunda.resultado());
    }

    @Test
    @DisplayName("Rejeita tamanhoAmostra menor ou igual a zero")
    void rejeitaTamanhoAmostraInvalido() {
        assertThatThrownBy(() -> servico.medirDispersaoValidade(0, 1, 42L, hoje))
                .isInstanceOf(OperacaoInvalidaException.class);
        assertThatThrownBy(() -> servico.medirDispersaoValidade(-10, 1, 42L, hoje))
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    @DisplayName("Rejeita tamanhoAmostra acima do limite de seguranca")
    void rejeitaTamanhoAmostraAcimaDoLimite() {
        assertThatThrownBy(() -> servico.medirDispersaoValidade(10_000_000, 1, 42L, hoje))
                .isInstanceOf(OperacaoInvalidaException.class);
    }

    @Test
    @DisplayName("Rejeita numero de threads menor que 1")
    void rejeitaThreadsInvalido() {
        assertThatThrownBy(() -> servico.medirDispersaoValidade(1_000, 0, 42L, hoje))
                .isInstanceOf(OperacaoInvalidaException.class);
    }
}
