package com.rotavital.dominio;

import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.rotavital.dominio.enums.GrupoSanguineo.O_NEG;
import static com.rotavital.dominio.enums.TipoHemocomponente.CONCENTRADO_HEMACIAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A Rota como agregado (PI3-247): embarque, saida, chegada e cancelamento
 * passam por ela, e ela arrasta o status das bolsas embarcadas.
 */
@DisplayName("Rota")
class RotaTest {

    private static final LocalDateTime SAIDA = LocalDateTime.of(2026, 11, 24, 8, 0);
    private static final LocalDate COLETA = SAIDA.toLocalDate().minusDays(1);

    private Hemocentro hemope;
    private Rota rota;

    @BeforeEach
    void preparar() {
        hemope = new Hemocentro("HC01", "Hemope", "(81) 0000-0000", null, "00.000.000/0001-00");
        Hospital imip = new Hospital("HO05", "IMIP", "(81) 0000-0001", null, "0000005");
        rota = new Rota("R-1", hemope, imip, SAIDA, SAIDA.plusHours(1));
    }

    private Bolsa bolsaReservada(String codigo, Hemocentro origem) {
        Bolsa bolsa = new Bolsa(codigo, CONCENTRADO_HEMACIAS, O_NEG, 450, COLETA, origem);
        bolsa.reservar(SAIDA.toLocalDate());
        return bolsa;
    }

    @Nested
    @DisplayName("embarque")
    class Embarque {

        @Test
        @DisplayName("Dado uma bolsa reservada no hemocentro de origem, quando embarcar, entao entra na carga")
        void embarcaReservada() {
            Bolsa bolsa = bolsaReservada("B-1", hemope);

            rota.embarcar(bolsa);

            assertThat(rota.getBolsas()).containsExactly(bolsa);
        }

        @Test
        @DisplayName("Dado uma bolsa ainda disponivel, quando embarcar, entao recusa: so sai o que foi reservado")
        void recusaDisponivel() {
            Bolsa livre = new Bolsa("B-1", CONCENTRADO_HEMACIAS, O_NEG, 450, COLETA, hemope);

            assertThatThrownBy(() -> rota.embarcar(livre))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageContaining("RESERVADA");
        }

        @Test
        @DisplayName("Dado uma bolsa de outro hemocentro, quando embarcar, entao recusa")
        void recusaOutroHemocentro() {
            Hemocentro outro = new Hemocentro("HC02", "Outro", "(81) 0000-0002", null, "00.000.000/0002-00");

            assertThatThrownBy(() -> rota.embarcar(bolsaReservada("B-1", outro)))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageContaining("outro hemocentro");
        }

        @Test
        @DisplayName("Dado uma bolsa ja embarcada, quando embarcar de novo, entao recusa")
        void recusaDuplicada() {
            Bolsa bolsa = bolsaReservada("B-1", hemope);
            rota.embarcar(bolsa);

            assertThatThrownBy(() -> rota.embarcar(bolsa))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessage("Bolsa ja embarcada nesta rota");
        }

        @Test
        @DisplayName("Dado uma bolsa embarcada, quando desembarcar, entao sai da carga")
        void desembarca() {
            rota.embarcar(bolsaReservada("B-1", hemope));

            rota.desembarcar("B-1");

            assertThat(rota.getBolsas()).isEmpty();
        }

        @Test
        @DisplayName("Dado um codigo que nao esta na rota, quando desembarcar, entao nao encontra")
        void desembarcaInexistente() {
            assertThatThrownBy(() -> rota.desembarcar("B-9"))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        @DisplayName("Dado uma rota que ja saiu, quando embarcar, entao recusa")
        void naoEmbarcaDepoisDeSair() {
            rota.embarcar(bolsaReservada("B-1", hemope));
            rota.sair(SAIDA);

            assertThatThrownBy(() -> rota.embarcar(bolsaReservada("B-2", hemope)))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageContaining("PLANEJADA");
        }
    }

    @Nested
    @DisplayName("viagem")
    class Viagem {

        @Test
        @DisplayName("Dado uma rota carregada, quando sair e concluir, entao as bolsas vao de EM_TRANSITO a ENTREGUE")
        void saiEConclui() {
            Bolsa bolsa = bolsaReservada("B-1", hemope);
            rota.embarcar(bolsa);

            rota.sair(SAIDA);
            assertThat(rota.getStatus()).isEqualTo(StatusRota.EM_TRANSITO);
            assertThat(rota.getSaidaReal()).isEqualTo(SAIDA);
            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.EM_TRANSITO);

            rota.concluir(SAIDA.plusMinutes(40));
            assertThat(rota.getStatus()).isEqualTo(StatusRota.CONCLUIDA);
            assertThat(rota.getChegadaReal()).isEqualTo(SAIDA.plusMinutes(40));
            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.ENTREGUE);
        }

        @Test
        @DisplayName("Dado uma rota vazia, quando sair, entao recusa")
        void naoSaiVazia() {
            assertThatThrownBy(() -> rota.sair(SAIDA))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessage("Rota sem bolsas embarcadas nao pode sair");
        }

        @Test
        @DisplayName("Dado uma rota em transito, quando cancelar, entao a carga volta ao hemocentro reservada")
        void cancelarDevolveCarga() {
            Bolsa bolsa = bolsaReservada("B-1", hemope);
            rota.embarcar(bolsa);
            rota.sair(SAIDA);

            rota.cancelar();

            assertThat(rota.getStatus()).isEqualTo(StatusRota.CANCELADA);
            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.RESERVADA);
        }

        @Test
        @DisplayName("Dado uma rota planejada, quando concluir sem sair, entao recusa a transicao")
        void naoConcluiSemSair() {
            assertThatThrownBy(() -> rota.concluir(SAIDA))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessage("Transicao invalida: PLANEJADA -> CONCLUIDA");
        }

        @Test
        @DisplayName("Dado uma rota concluida, quando cancelar, entao recusa: estado final")
        void concluidaEhFinal() {
            rota.embarcar(bolsaReservada("B-1", hemope));
            rota.sair(SAIDA);
            rota.concluir(SAIDA.plusHours(1));

            assertThatThrownBy(rota::cancelar).isInstanceOf(OperacaoInvalidaException.class);
        }
    }
}
