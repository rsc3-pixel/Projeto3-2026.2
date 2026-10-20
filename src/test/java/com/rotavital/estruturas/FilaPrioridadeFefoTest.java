package com.rotavital.estruturas;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FilaPrioridadeFefoTest {

    @Test
    void filaVaziaNaoTemProximaBolsa() {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();

        assertTrue(fila.estaVazia());
        assertNull(fila.consultarProxima());
        assertNull(fila.retirarProxima());
    }

    @Test
    void elementoUnicoEhRetornado() {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
        Bolsa bolsa = criarBolsa("B001", LocalDate.of(2026, 8, 1));

        fila.adicionar(bolsa);

        assertSame(bolsa, fila.retirarProxima());
        assertTrue(fila.estaVazia());
    }

    @Test
    void devolveSempreABolsaQueVencePrimeiro() {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
        Bolsa venceDepois = criarBolsa("B002", LocalDate.of(2026, 8, 10));
        Bolsa vencePrimeiro = criarBolsa("B001", LocalDate.of(2026, 8, 1));

        fila.adicionar(venceDepois);
        fila.adicionar(vencePrimeiro);

        assertSame(vencePrimeiro, fila.retirarProxima());
        assertSame(venceDepois, fila.retirarProxima());
    }

    @Test
    void empateDeValidadeEhResolvidoDeFormaDeterministicaPeloCodigo() {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
        LocalDate mesmaColeta = LocalDate.of(2026, 8, 1);
        Bolsa b002 = criarBolsa("B002", mesmaColeta);
        Bolsa b001 = criarBolsa("B001", mesmaColeta);

        fila.adicionar(b002);
        fila.adicionar(b001);

        assertEquals("B001", fila.retirarProxima().getCodigoRastreio());
        assertEquals("B002", fila.retirarProxima().getCodigoRastreio());
    }

    /**
     * Data de referencia fixa. Com LocalDate.now(), "vence ontem" e "vence
     * hoje" mudariam de significado a cada dia e um teste verde hoje falharia
     * amanha sem que nada no codigo tivesse mudado.
     */
    private static final LocalDate REFERENCIA = LocalDate.of(2026, 9, 10);

    /**
     * A validade nao e informada ao construtor: a Bolsa a deriva de
     * dataColeta mais a validade do tipo. A coleta e calculada de tras para
     * frente para que o teste possa fixar a validade que quer.
     */
    private Bolsa bolsaQueVenceEm(String codigo, LocalDate dataValidade) {
        int validadeDias = TipoHemocomponente.CONCENTRADO_HEMACIAS.getValidadeDias();
        Bolsa bolsa = criarBolsa(codigo, dataValidade.minusDays(validadeDias));
        assertEquals(dataValidade, bolsa.getDataValidade(),
                "Fixture nao produziu a validade pedida");
        return bolsa;
    }

    /** Codigos das bolsas retiradas, na ordem em que saem. */
    private List<String> drenar(FilaPrioridadeFefo fila) {
        List<String> saida = new ArrayList<>();
        Bolsa bolsa;
        while ((bolsa = fila.retirarProxima()) != null) {
            saida.add(bolsa.getCodigoRastreio());
        }
        return saida;
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Ordenacao por insercao")
    class Ordenacao {

        @Test
        @DisplayName("Insercao no inicio: a bolsa que vence antes da primeira assume o inicio")
        void insercaoNoInicio() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA.plusDays(10)));
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.plusDays(1)));

            assertEquals("B001", fila.consultarProxima().getCodigoRastreio());
            assertEquals(List.of("B001", "B002"), drenar(fila));
        }

        @Test
        @DisplayName("Insercao no meio: a bolsa encaixa entre as duas existentes")
        void insercaoNoMeio() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.plusDays(1)));
            fila.adicionar(bolsaQueVenceEm("B003", REFERENCIA.plusDays(30)));
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA.plusDays(10)));

            assertEquals(List.of("B001", "B002", "B003"), drenar(fila));
        }

        @Test
        @DisplayName("Insercao no fim: a bolsa de validade mais distante vai para o final")
        void insercaoNoFim() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.plusDays(1)));
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA.plusDays(10)));
            fila.adicionar(bolsaQueVenceEm("B003", REFERENCIA.plusDays(30)));

            assertEquals(List.of("B001", "B002", "B003"), drenar(fila));
        }

        @Test
        @DisplayName("Entrada embaralhada com semente fixa sai inteira na ordem de validade")
        void entradaEmbaralhadaSaiOrdenada() {
            List<Bolsa> bolsas = new ArrayList<>();
            for (int dia = 1; dia <= 8; dia++) {
                bolsas.add(bolsaQueVenceEm("B00" + dia, REFERENCIA.plusDays(dia * 3L)));
            }

            // Semente fixa: o embaralhamento e sempre o mesmo, entao a falha e
            // reproduzivel.
            Collections.shuffle(bolsas, new Random(42));

            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            bolsas.forEach(fila::adicionar);

            assertEquals(
                    List.of("B001", "B002", "B003", "B004", "B005", "B006", "B007", "B008"),
                    drenar(fila));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Empate de validade")
    class EmpateDeValidade {

        @Test
        @DisplayName("Mesma validade: sai o menor codigo, inserindo o maior primeiro")
        void empateMaiorCodigoInseridoPrimeiro() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            LocalDate mesmaValidade = REFERENCIA.plusDays(7);
            fila.adicionar(bolsaQueVenceEm("B002", mesmaValidade));
            fila.adicionar(bolsaQueVenceEm("B001", mesmaValidade));

            assertEquals(List.of("B001", "B002"), drenar(fila));
        }

        @Test
        @DisplayName("Mesma validade: sai o menor codigo tambem inserindo o menor primeiro")
        void empateMenorCodigoInseridoPrimeiro() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            LocalDate mesmaValidade = REFERENCIA.plusDays(7);
            fila.adicionar(bolsaQueVenceEm("B001", mesmaValidade));
            fila.adicionar(bolsaQueVenceEm("B002", mesmaValidade));

            assertEquals(List.of("B001", "B002"), drenar(fila),
                    "a ordem de insercao nao pode influenciar o resultado");
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Estoque vazio")
    class EstoqueVazio {

        @Test
        @DisplayName("Fila nova devolve null sem lancar excecao")
        void filaNovaDevolveNull() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();

            assertDoesNotThrow(() -> {
                assertNull(fila.consultarProxima());
                assertNull(fila.retirarProxima());
            });
            assertTrue(fila.estaVazia());
            assertEquals(0, fila.tamanho());
        }

        @Test
        @DisplayName("Depois de esvaziar, a fila volta ao mesmo estado de vazia")
        void depoisDeEsvaziarVoltaAoEstadoVazio() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.plusDays(5)));
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA.plusDays(9)));

            fila.retirarProxima();
            fila.retirarProxima();

            assertTrue(fila.estaVazia());
            assertEquals(0, fila.tamanho());
            assertNull(fila.consultarProxima());
            assertNull(fila.retirarProxima());

            // E volta a aceitar insercao normalmente.
            fila.adicionar(bolsaQueVenceEm("B003", REFERENCIA.plusDays(2)));
            assertEquals("B003", fila.consultarProxima().getCodigoRastreio());
            assertEquals(1, fila.tamanho());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Descarte de bolsas vencidas")
    class Vencidas {

        @Test
        @DisplayName("Remove so as vencidas do inicio e devolve-as em ordem de validade")
        void descartaSoAsVencidasDoInicio() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B003", REFERENCIA.plusDays(5)));
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.minusDays(10)));
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA.minusDays(1)));
            fila.adicionar(bolsaQueVenceEm("B004", REFERENCIA.plusDays(20)));

            List<Bolsa> descartadas = fila.descartarVencidas(REFERENCIA);

            assertEquals(List.of("B001", "B002"),
                    descartadas.stream().map(Bolsa::getCodigoRastreio).toList());
            assertEquals(List.of("B003", "B004"), drenar(fila));
        }

        @Test
        @DisplayName("A bolsa que vence na propria data de referencia nao e descartada")
        void bolsaQueVenceNaDataDeReferenciaEhMantida() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.minusDays(1)));
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA));

            List<Bolsa> descartadas = fila.descartarVencidas(REFERENCIA);

            // estaVencida usa isAfter: a bolsa so vence no dia seguinte.
            assertEquals(List.of("B001"),
                    descartadas.stream().map(Bolsa::getCodigoRastreio).toList());
            assertEquals("B002", fila.consultarProxima().getCodigoRastreio());
            assertEquals(1, fila.tamanho());
        }

        @Test
        @DisplayName("Fila inteiramente vencida fica vazia")
        void filaTodaVencidaFicaVazia() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.minusDays(3)));
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA.minusDays(1)));

            List<Bolsa> descartadas = fila.descartarVencidas(REFERENCIA);

            assertEquals(2, descartadas.size());
            assertTrue(fila.estaVazia());
            assertEquals(0, fila.tamanho());
        }

        @Test
        @DisplayName("Fila sem vencidas nao muda e devolve lista vazia")
        void filaSemVencidasNaoMuda() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.plusDays(1)));
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA.plusDays(9)));

            List<Bolsa> descartadas = fila.descartarVencidas(REFERENCIA);

            assertTrue(descartadas.isEmpty());
            assertEquals(2, fila.tamanho());
            assertEquals(List.of("B001", "B002"), drenar(fila));
        }

        @Test
        @DisplayName("A lista devolvida e imutavel e data nula lanca IllegalArgumentException")
        void listaImutavelEDataNulaLanca() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.minusDays(1)));

            List<Bolsa> descartadas = fila.descartarVencidas(REFERENCIA);
            assertThrows(UnsupportedOperationException.class, () -> descartadas.clear());

            assertThrows(IllegalArgumentException.class, () -> fila.descartarVencidas(null));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Contrato da fila")
    class ContratoDaFila {

        @Test
        @DisplayName("O tamanho acompanha adicionar, retirar e descartar")
        void tamanhoAcompanhaAsOperacoes() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
            assertEquals(0, fila.tamanho());

            fila.adicionar(bolsaQueVenceEm("B001", REFERENCIA.minusDays(2)));
            assertEquals(1, fila.tamanho());
            fila.adicionar(bolsaQueVenceEm("B002", REFERENCIA.plusDays(5)));
            fila.adicionar(bolsaQueVenceEm("B003", REFERENCIA.plusDays(9)));
            assertEquals(3, fila.tamanho());

            fila.retirarProxima();
            assertEquals(2, fila.tamanho());

            fila.adicionar(bolsaQueVenceEm("B004", REFERENCIA.minusDays(1)));
            assertEquals(3, fila.tamanho());

            fila.descartarVencidas(REFERENCIA);
            assertEquals(2, fila.tamanho(), "so a B004 estava vencida");
        }

        @Test
        @DisplayName("Bolsa nula lanca IllegalArgumentException e nao entra na fila")
        void bolsaNulaLanca() {
            FilaPrioridadeFefo fila = new FilaPrioridadeFefo();

            assertThrows(IllegalArgumentException.class, () -> fila.adicionar(null));
            assertTrue(fila.estaVazia());
            assertEquals(0, fila.tamanho());
        }

        /**
         * Criterio de aceite da task mae: a bolsa com validade mais proxima e
         * sempre a primeira a sair. Verificado apos cada insercao, em varias
         * ordens de entrada, comparando com o minimo recalculado de forma
         * independente da estrutura.
         */
        @Test
        @DisplayName("Criterio da mae: a proxima e sempre a de menor validade, e no empate o menor codigo")
        void proximaEhSempreADeMenorValidade() {
            List<Bolsa> bolsas = new ArrayList<>();
            bolsas.add(bolsaQueVenceEm("B005", REFERENCIA.plusDays(4)));
            bolsas.add(bolsaQueVenceEm("B001", REFERENCIA.plusDays(12)));
            bolsas.add(bolsaQueVenceEm("B003", REFERENCIA.plusDays(4)));
            bolsas.add(bolsaQueVenceEm("B002", REFERENCIA.plusDays(30)));
            bolsas.add(bolsaQueVenceEm("B004", REFERENCIA.plusDays(1)));

            Random embaralhador = new Random(7);
            for (int execucao = 0; execucao < 20; execucao++) {
                Collections.shuffle(bolsas, embaralhador);

                FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
                List<Bolsa> inseridas = new ArrayList<>();

                for (Bolsa bolsa : bolsas) {
                    fila.adicionar(bolsa);
                    inseridas.add(bolsa);

                    Bolsa esperada = inseridas.stream()
                            .min(java.util.Comparator
                                    .comparing(Bolsa::getDataValidade)
                                    .thenComparing(Bolsa::getCodigoRastreio))
                            .orElseThrow();

                    assertEquals(esperada.getCodigoRastreio(),
                            fila.consultarProxima().getCodigoRastreio(),
                            "apos inserir " + bolsa.getCodigoRastreio()
                                    + " a proxima deveria ser " + esperada.getCodigoRastreio());
                }
            }
        }
    }

    private Bolsa criarBolsa(String codigo, LocalDate dataColeta) {
        Hemocentro hemocentro = new Hemocentro(
                "H1", "Hemocentro Teste", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100"
        );

        return new Bolsa(
                codigo,
                TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_POS,
                450,
                dataColeta,
                hemocentro
        );
    }
}
