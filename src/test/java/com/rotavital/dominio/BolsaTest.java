package com.rotavital.dominio;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.rotavital.dominio.enums.GrupoSanguineo.A_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.AB_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.O_NEG;
import static com.rotavital.dominio.enums.TipoHemocomponente.CONCENTRADO_HEMACIAS;
import static com.rotavital.dominio.enums.TipoHemocomponente.PLASMA_FRESCO_CONGELADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comportamento da Bolsa como entidade rica (PI3-247): o status so muda pelas
 * operacoes do dominio, e a propria bolsa recusa a transicao proibida.
 */
@DisplayName("Bolsa")
class BolsaTest {

    private static final LocalDate COLETA = LocalDate.of(2026, 11, 1);
    private static final LocalDate HOJE = COLETA.plusDays(1);

    private static Bolsa bolsa(GrupoSanguineo grupo, TipoHemocomponente tipo) {
        Hemocentro hemocentro = new Hemocentro("HC01", "Hemope", "(81) 0000-0000", null, "00.000.000/0001-00");
        return new Bolsa("B-1", tipo, grupo, 450, COLETA, hemocentro);
    }

    private static Bolsa hemacias(GrupoSanguineo grupo) {
        return bolsa(grupo, CONCENTRADO_HEMACIAS);
    }

    @Nested
    @DisplayName("ciclo de vida")
    class CicloDeVida {

        @Test
        @DisplayName("Dado uma bolsa nova, entao ela nasce DISPONIVEL")
        void nasceDisponivel() {
            assertThat(hemacias(O_NEG).getStatus()).isEqualTo(StatusBolsa.DISPONIVEL);
        }

        @Test
        @DisplayName("Dado uma bolsa disponivel, quando reservar, despachar e entregar, entao percorre o caminho feliz")
        void caminhoFeliz() {
            Bolsa bolsa = hemacias(O_NEG);

            bolsa.reservar(HOJE);
            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.RESERVADA);
            bolsa.despachar();
            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.EM_TRANSITO);
            bolsa.entregar();
            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.ENTREGUE);
        }

        @Test
        @DisplayName("Dado uma bolsa reservada, quando liberar, entao volta a DISPONIVEL")
        void liberarDesfazReserva() {
            Bolsa bolsa = hemacias(O_NEG);
            bolsa.reservar(HOJE);

            bolsa.liberar();

            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.DISPONIVEL);
        }

        @Test
        @DisplayName("Dado uma bolsa em transito, quando a rota volta, entao ela retorna a RESERVADA")
        void retornarDaRota() {
            Bolsa bolsa = hemacias(O_NEG);
            bolsa.reservar(HOJE);
            bolsa.despachar();

            bolsa.retornarAoHemocentro();

            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.RESERVADA);
        }

        @Test
        @DisplayName("Dado uma bolsa vencida, quando reservar, entao recusa")
        void naoReservaVencida() {
            Bolsa bolsa = hemacias(O_NEG);
            LocalDate depoisDaValidade = bolsa.getDataValidade().plusDays(1);

            assertThatThrownBy(() -> bolsa.reservar(depoisDaValidade))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageContaining("vencida");
            assertThat(bolsa.getStatus()).isEqualTo(StatusBolsa.DISPONIVEL);
        }

        @Test
        @DisplayName("Dado uma bolsa disponivel, quando despachar sem reservar, entao recusa a transicao")
        void naoPulaAReserva() {
            Bolsa bolsa = hemacias(O_NEG);

            assertThatThrownBy(bolsa::despachar)
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessage("Transicao invalida: DISPONIVEL -> EM_TRANSITO");
        }

        @Test
        @DisplayName("Dado uma bolsa entregue, quando descartar, entao recusa: estado final")
        void entregueEhFinal() {
            Bolsa bolsa = hemacias(O_NEG);
            bolsa.reservar(HOJE);
            bolsa.despachar();
            bolsa.entregar();

            assertThatThrownBy(bolsa::descartar).isInstanceOf(OperacaoInvalidaException.class);
        }

        @Test
        @DisplayName("Dado uma bolsa descartada, quando reservar, entao recusa: estado final")
        void descartadaEhFinal() {
            Bolsa bolsa = hemacias(O_NEG);
            bolsa.descartar();

            assertThatThrownBy(() -> bolsa.reservar(HOJE)).isInstanceOf(OperacaoInvalidaException.class);
        }
    }

    @Nested
    @DisplayName("elegibilidade para um pedido")
    class Elegibilidade {

        @Test
        @DisplayName("Dado hemacias O-, quando o receptor e A+, entao pode atender: O- e doador universal")
        void compativelNaoPrecisaSerIdentico() {
            assertThat(hemacias(O_NEG).podeAtender(A_POS, CONCENTRADO_HEMACIAS, HOJE)).isTrue();
        }

        @Test
        @DisplayName("Dado hemacias AB+, quando o receptor e O-, entao nao pode atender")
        void incompativelNaoAtende() {
            Bolsa bolsa = hemacias(AB_POS);

            assertThat(bolsa.podeAtender(O_NEG, CONCENTRADO_HEMACIAS, HOJE)).isFalse();
            assertThat(bolsa.motivoDeRecusa(O_NEG, CONCENTRADO_HEMACIAS, HOJE))
                    .hasValueSatisfying(m -> assertThat(m).startsWith("Grupo sanguineo incompativel"));
        }

        @Test
        @DisplayName("Dado plasma AB+, quando o receptor e O-, entao pode atender: a tabela do plasma e invertida")
        void plasmaUsaTabelaInvertida() {
            Bolsa plasma = bolsa(AB_POS, PLASMA_FRESCO_CONGELADO);

            assertThat(plasma.podeAtender(O_NEG, PLASMA_FRESCO_CONGELADO, HOJE)).isTrue();
        }

        @Test
        @DisplayName("Dado hemacias, quando o pedido e de plasma, entao nao pode atender")
        void hemocomponenteDiferente() {
            assertThat(hemacias(O_NEG).motivoDeRecusa(O_NEG, PLASMA_FRESCO_CONGELADO, HOJE))
                    .hasValueSatisfying(m -> assertThat(m).startsWith("Hemocomponente incompativel"));
        }

        @Test
        @DisplayName("Dado uma bolsa reservada, entao nao pode atender outro pedido")
        void reservadaNaoAtende() {
            Bolsa bolsa = hemacias(O_NEG);
            bolsa.reservar(HOJE);

            assertThat(bolsa.motivoDeRecusa(O_NEG, CONCENTRADO_HEMACIAS, HOJE))
                    .hasValue("Bolsa nao esta disponivel. Status atual: RESERVADA");
        }

        @Test
        @DisplayName("Dado uma bolsa no ultimo dia de validade, entao ainda pode atender")
        void ultimoDiaAindaVale() {
            Bolsa bolsa = hemacias(O_NEG);

            assertThat(bolsa.podeAtender(O_NEG, CONCENTRADO_HEMACIAS, bolsa.getDataValidade())).isTrue();
            assertThat(bolsa.podeAtender(O_NEG, CONCENTRADO_HEMACIAS, bolsa.getDataValidade().plusDays(1))).isFalse();
        }

        @Test
        @DisplayName("Dado receptor ou hemocomponente ausente, entao nao pode atender")
        void ausenciaDeDadoNaoAutoriza() {
            Bolsa bolsa = hemacias(O_NEG);

            assertThat(bolsa.podeAtender(null, CONCENTRADO_HEMACIAS, HOJE)).isFalse();
            assertThat(bolsa.podeAtender(O_NEG, null, HOJE)).isFalse();
        }
    }
}
