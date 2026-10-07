package com.rotavital.estruturas.basicas;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PilhaHistoricoTest {

    private PilhaHistorico<String> comCincoOperacoes() {
        PilhaHistorico<String> pilha = new PilhaHistorico<>();
        pilha.empilhar("OP001");
        pilha.empilhar("OP002");
        pilha.empilhar("OP003");
        pilha.empilhar("OP004");
        pilha.empilhar("OP005");
        return pilha;
    }

    @Test
    void pilhaNovaEstaVazia() {
        PilhaHistorico<String> pilha = new PilhaHistorico<>();

        assertTrue(pilha.estaVazia());
        assertEquals(0, pilha.tamanho());
        assertTrue(pilha.listar().isEmpty());
    }

    /**
     * LIFO: a listagem vai do topo para a base, ou seja, da operacao mais
     * recente para a mais antiga - o inverso da ordem de entrada.
     */
    @Test
    void listagemVaiDoTopoParaABase() {
        PilhaHistorico<String> pilha = comCincoOperacoes();

        assertEquals(List.of("OP005", "OP004", "OP003", "OP002", "OP001"),
                pilha.listar());
        assertEquals(5, pilha.tamanho());
        assertFalse(pilha.estaVazia());
    }

    @Test
    void topoConsultaSemRetirar() {
        PilhaHistorico<String> pilha = comCincoOperacoes();

        assertEquals(Optional.of("OP005"), pilha.topo());
        assertEquals(Optional.of("OP005"), pilha.topo());
        assertEquals(5, pilha.tamanho(), "consultar nao pode mudar o tamanho");
    }

    /**
     * Desempilhar desfaz a ultima operacao registrada, na ordem inversa da
     * entrada.
     */
    @Test
    void desempilharRetiraDoMaisRecenteParaOMaisAntigo() {
        PilhaHistorico<String> pilha = comCincoOperacoes();

        assertEquals(Optional.of("OP005"), pilha.desempilhar());
        assertEquals(Optional.of("OP004"), pilha.desempilhar());

        assertEquals(List.of("OP003", "OP002", "OP001"), pilha.listar());
        assertEquals(3, pilha.tamanho());
        assertEquals(Optional.of("OP003"), pilha.topo());
    }

    @Test
    void desempilharAteEsvaziarEDepoisReusar() {
        PilhaHistorico<String> pilha = comCincoOperacoes();

        for (int i = 0; i < 5; i++) {
            assertTrue(pilha.desempilhar().isPresent());
        }

        assertTrue(pilha.estaVazia());
        assertEquals(0, pilha.tamanho());

        pilha.empilhar("OP006");
        assertEquals(List.of("OP006"), pilha.listar());
        assertEquals(Optional.of("OP006"), pilha.topo());
        assertEquals(1, pilha.tamanho());
    }

    @Test
    void pilhaVaziaDevolveOptionalVazioSemExcecao() {
        PilhaHistorico<String> pilha = new PilhaHistorico<>();

        assertTrue(pilha.desempilhar().isEmpty());
        assertTrue(pilha.topo().isEmpty());
        assertDoesNotThrow(pilha::desempilhar);
        assertDoesNotThrow(pilha::limpar);
    }

    @Test
    void elementoNuloEhRejeitado() {
        PilhaHistorico<String> pilha = new PilhaHistorico<>();

        assertThrows(IllegalArgumentException.class, () -> pilha.empilhar(null));
        assertTrue(pilha.estaVazia());
    }

    @Test
    void limparEsvaziaAPilhaEPermiteReuso() {
        PilhaHistorico<String> pilha = comCincoOperacoes();

        pilha.limpar();

        assertTrue(pilha.estaVazia());
        assertEquals(0, pilha.tamanho());
        assertTrue(pilha.topo().isEmpty());

        pilha.empilhar("OP007");
        assertEquals(List.of("OP007"), pilha.listar());
    }

    @Test
    void listarDevolveListaImutavel() {
        PilhaHistorico<String> pilha = comCincoOperacoes();

        List<String> itens = pilha.listar();

        assertThrows(UnsupportedOperationException.class, () -> itens.add("OP999"));
    }
}
