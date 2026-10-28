package com.rotavital.estruturas.basicas;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FilaRequisicoesTest {

    private FilaRequisicoes<String> comQuatroRequisicoes() {
        FilaRequisicoes<String> fila = new FilaRequisicoes<>();
        fila.enfileirar("RQ001");
        fila.enfileirar("RQ002");
        fila.enfileirar("RQ003");
        fila.enfileirar("RQ004");
        return fila;
    }

    @Test
    void filaNovaEstaVazia() {
        FilaRequisicoes<String> fila = new FilaRequisicoes<>();

        assertTrue(fila.estaVazia());
        assertEquals(0, fila.tamanho());
        assertTrue(fila.listar().isEmpty());
    }

    /**
     * FIFO: a ordem de listagem e a ordem de chegada, da frente para o tras.
     */
    @Test
    void enfileirarPreservaAOrdemDeChegada() {
        FilaRequisicoes<String> fila = comQuatroRequisicoes();

        assertEquals(List.of("RQ001", "RQ002", "RQ003", "RQ004"), fila.listar());
        assertEquals(4, fila.tamanho());
        assertFalse(fila.estaVazia());
    }

    @Test
    void frenteConsultaSemRetirar() {
        FilaRequisicoes<String> fila = comQuatroRequisicoes();

        assertEquals(Optional.of("RQ001"), fila.frente());
        assertEquals(Optional.of("RQ001"), fila.frente());
        assertEquals(4, fila.tamanho(), "consultar nao pode mudar o tamanho");
    }

    /**
     * Quem entrou primeiro sai primeiro, e o restante mantem a ordem.
     */
    @Test
    void desenfileirarRetiraNaOrdemDeChegada() {
        FilaRequisicoes<String> fila = comQuatroRequisicoes();

        assertEquals(Optional.of("RQ001"), fila.desenfileirar());
        assertEquals(Optional.of("RQ002"), fila.desenfileirar());

        assertEquals(List.of("RQ003", "RQ004"), fila.listar());
        assertEquals(2, fila.tamanho());
        assertEquals(Optional.of("RQ003"), fila.frente());
    }

    @Test
    void desenfileirarAteEsvaziarEDepoisReusar() {
        FilaRequisicoes<String> fila = comQuatroRequisicoes();

        for (int i = 0; i < 4; i++) {
            assertTrue(fila.desenfileirar().isPresent());
        }

        assertTrue(fila.estaVazia());
        assertEquals(0, fila.tamanho());

        // Prova que 'tras' foi zerado ao esvaziar: sem isso a insercao
        // seguinte entraria depois de um no que ja saiu da fila.
        fila.enfileirar("RQ005");
        assertEquals(List.of("RQ005"), fila.listar());
        assertEquals(Optional.of("RQ005"), fila.frente());
        assertEquals(1, fila.tamanho());
    }

    @Test
    void filaVaziaDevolveOptionalVazioSemExcecao() {
        FilaRequisicoes<String> fila = new FilaRequisicoes<>();

        assertTrue(fila.desenfileirar().isEmpty());
        assertTrue(fila.frente().isEmpty());
        assertDoesNotThrow(fila::desenfileirar);
        assertDoesNotThrow(fila::limpar);
    }

    @Test
    void elementoNuloEhRejeitado() {
        FilaRequisicoes<String> fila = new FilaRequisicoes<>();

        assertThrows(IllegalArgumentException.class, () -> fila.enfileirar(null));
        assertTrue(fila.estaVazia());
    }

    @Test
    void limparEsvaziaAFilaEPermiteReuso() {
        FilaRequisicoes<String> fila = comQuatroRequisicoes();

        fila.limpar();

        assertTrue(fila.estaVazia());
        assertEquals(0, fila.tamanho());
        assertTrue(fila.frente().isEmpty());

        fila.enfileirar("RQ006");
        assertEquals(List.of("RQ006"), fila.listar());
    }

    @Test
    void listarDevolveListaImutavel() {
        FilaRequisicoes<String> fila = comQuatroRequisicoes();

        List<String> itens = fila.listar();

        assertThrows(UnsupportedOperationException.class, () -> itens.add("RQ999"));
    }
}
