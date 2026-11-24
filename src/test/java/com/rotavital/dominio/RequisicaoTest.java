package com.rotavital.dominio;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRequisicao;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.rotavital.dominio.enums.GrupoSanguineo.A_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.AB_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.O_NEG;
import static com.rotavital.dominio.enums.TipoHemocomponente.CONCENTRADO_HEMACIAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A Requisicao como raiz do agregado (PI3-247): itens e alocacoes so mudam
 * por ela, e o status deriva das alocacoes sem ninguem de fora atribui-lo.
 */
@DisplayName("Requisicao")
class RequisicaoTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 24, 10, 0);
    private static final LocalDate COLETA = AGORA.toLocalDate().minusDays(1);

    private Hemocentro hemope;
    private Requisicao requisicao;

    @BeforeEach
    void preparar() {
        hemope = new Hemocentro("HC01", "Hemope", "(81) 0000-0000", null, "00.000.000/0001-00");
        Hospital imip = new Hospital("HC05", "IMIP", "(81) 0000-0001", null, "0000005");
        requisicao = new Requisicao("REQ-1", imip, PrioridadeRequisicao.URGENTE, AGORA, AGORA.plusHours(12));
        requisicao.adicionarItem(new ItemRequisicao("I-1", CONCENTRADO_HEMACIAS, A_POS, 2));
    }

    private Bolsa bolsa(String codigo, GrupoSanguineo grupo) {
        return new Bolsa(codigo, CONCENTRADO_HEMACIAS, grupo, 450, COLETA, hemope);
    }

    @Nested
    @DisplayName("alocacao")
    class AlocacaoDeBolsa {

        @Test
        @DisplayName("Dado um item que pede 2, quando alocar 1 bolsa compativel, entao reserva e fica PARCIALMENTE_ATENDIDA")
        void alocacaoParcial() {
            Bolsa o = bolsa("B-1", O_NEG);

            Alocacao alocacao = requisicao.alocar("I-1", o, AGORA);

            assertThat(alocacao.getBolsa()).isEqualTo(o);
            assertThat(o.getStatus()).isEqualTo(StatusBolsa.RESERVADA);
            assertThat(requisicao.getStatus()).isEqualTo(StatusRequisicao.PARCIALMENTE_ATENDIDA);
        }

        @Test
        @DisplayName("Dado um item que pede 2, quando alocar 2 bolsas, entao fica ATENDIDA e recusa a terceira")
        void alocacaoCompleta() {
            requisicao.alocar("I-1", bolsa("B-1", O_NEG), AGORA);
            requisicao.alocar("I-1", bolsa("B-2", A_POS), AGORA);

            assertThat(requisicao.getStatus()).isEqualTo(StatusRequisicao.ATENDIDA);
            assertThatThrownBy(() -> requisicao.alocar("I-1", bolsa("B-3", O_NEG), AGORA))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessage("Item ja esta totalmente atendido");
        }

        @Test
        @DisplayName("Dado uma bolsa incompativel, quando alocar, entao recusa e a bolsa continua disponivel")
        void recusaIncompativel() {
            Bolsa ab = bolsa("B-1", AB_POS);

            assertThatThrownBy(() -> requisicao.alocar("I-1", ab, AGORA))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageStartingWith("Grupo sanguineo incompativel");
            assertThat(ab.getStatus()).isEqualTo(StatusBolsa.DISPONIVEL);
            assertThat(requisicao.getStatus()).isEqualTo(StatusRequisicao.PENDENTE);
        }

        @Test
        @DisplayName("Dado um item inexistente, quando alocar, entao nao encontra")
        void itemInexistente() {
            assertThatThrownBy(() -> requisicao.alocar("I-9", bolsa("B-1", O_NEG), AGORA))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        @DisplayName("Dado uma alocacao feita, quando desfazer, entao a bolsa volta a DISPONIVEL e a requisicao a PENDENTE")
        void desfazer() {
            Bolsa o = bolsa("B-1", O_NEG);
            Alocacao alocacao = requisicao.alocar("I-1", o, AGORA);

            Bolsa liberada = requisicao.desfazerAlocacao("I-1", alocacao.getId());

            assertThat(liberada).isEqualTo(o);
            assertThat(o.getStatus()).isEqualTo(StatusBolsa.DISPONIVEL);
            assertThat(requisicao.getStatus()).isEqualTo(StatusRequisicao.PENDENTE);
        }

        @Test
        @DisplayName("Dado uma bolsa que ja saiu na rota, quando desfazer a alocacao, entao recusa")
        void naoDesfazDespachada() {
            Bolsa o = bolsa("B-1", O_NEG);
            Alocacao alocacao = requisicao.alocar("I-1", o, AGORA);
            o.despachar();

            assertThatThrownBy(() -> requisicao.desfazerAlocacao("I-1", alocacao.getId()))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageStartingWith("A bolsa ja saiu para entrega");
        }
    }

    @Nested
    @DisplayName("cancelamento")
    class Cancelamento {

        @Test
        @DisplayName("Dado bolsas reservadas, quando cancelar, entao as bolsas sao liberadas e nada mais e aceito")
        void cancelarLiberaBolsas() {
            Bolsa o = bolsa("B-1", O_NEG);
            requisicao.alocar("I-1", o, AGORA);

            List<Bolsa> liberadas = requisicao.cancelar();

            assertThat(liberadas).containsExactly(o);
            assertThat(o.getStatus()).isEqualTo(StatusBolsa.DISPONIVEL);
            assertThat(requisicao.getStatus()).isEqualTo(StatusRequisicao.CANCELADA);
            assertThatThrownBy(() -> requisicao.adicionarItem(new ItemRequisicao("I-2", CONCENTRADO_HEMACIAS, O_NEG, 1)))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessage("Requisicao cancelada nao aceita alteracoes");
        }

        @Test
        @DisplayName("Dado uma bolsa ja despachada, quando cancelar, entao recusa")
        void naoCancelaComBolsaNaRua() {
            Bolsa o = bolsa("B-1", O_NEG);
            requisicao.alocar("I-1", o, AGORA);
            o.despachar();

            assertThatThrownBy(requisicao::cancelar)
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageStartingWith("Ha bolsas ja despachadas");
        }

        @Test
        @DisplayName("Dado uma requisicao ja cancelada, quando cancelar de novo, entao nao faz nada")
        void cancelarEhIdempotente() {
            requisicao.cancelar();

            assertThat(requisicao.cancelar()).isEmpty();
        }
    }

    @Nested
    @DisplayName("itens")
    class Itens {

        @Test
        @DisplayName("Dado um item com bolsa alocada, quando remover, entao recusa")
        void naoRemoveItemAlocado() {
            requisicao.alocar("I-1", bolsa("B-1", O_NEG), AGORA);

            assertThatThrownBy(() -> requisicao.removerItem("I-1"))
                    .isInstanceOf(OperacaoInvalidaException.class)
                    .hasMessageStartingWith("Item possui bolsas alocadas");
        }

        @Test
        @DisplayName("Dado um item atendido, quando adicionar outro item, entao a requisicao volta a PARCIALMENTE_ATENDIDA")
        void novoItemReabre() {
            requisicao.alocar("I-1", bolsa("B-1", O_NEG), AGORA);
            requisicao.alocar("I-1", bolsa("B-2", O_NEG), AGORA);

            requisicao.adicionarItem(new ItemRequisicao("I-2", CONCENTRADO_HEMACIAS, O_NEG, 1));

            assertThat(requisicao.getStatus()).isEqualTo(StatusRequisicao.PARCIALMENTE_ATENDIDA);
        }

        @Test
        @DisplayName("Dado um item sem alocacao, quando remover, entao sai da requisicao")
        void removeItemLivre() {
            requisicao.removerItem("I-1");

            assertThat(requisicao.getItens()).isEmpty();
            assertThat(requisicao.getStatus()).isEqualTo(StatusRequisicao.PENDENTE);
        }
    }
}
