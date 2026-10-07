package com.rotavital.estruturas.basicas;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ListaEstoqueTest {

    private ListaEstoque<String> comCincoBolsas() {
        ListaEstoque<String> lista = new ListaEstoque<>();
        lista.inserir("BS0001");
        lista.inserir("BS0002");
        lista.inserir("BS0003");
        lista.inserir("BS0004");
        lista.inserir("BS0005");
        return lista;
    }

    @Test
    void listaNovaEstaVazia() {
        ListaEstoque<String> lista = new ListaEstoque<>();

        assertTrue(lista.estaVazia());
        assertEquals(0, lista.tamanho());
        assertTrue(lista.listar().isEmpty());
    }

    /**
     * A insercao e no fim, entao a ordem de listagem tem de ser a ordem de
     * entrada.
     */
    @Test
    void insercaoNoFimPreservaAOrdemDeEntrada() {
        ListaEstoque<String> lista = comCincoBolsas();

        assertEquals(List.of("BS0001", "BS0002", "BS0003", "BS0004", "BS0005"),
                lista.listar());
        assertEquals(5, lista.tamanho());
        assertFalse(lista.estaVazia());
    }

    @Test
    void buscaEncontraPeloCriterioEIgnoraOQueNaoBate() {
        ListaEstoque<String> lista = comCincoBolsas();

        Optional<String> encontrada = lista.buscar(codigo -> codigo.equals("BS0003"));
        Optional<String> inexistente = lista.buscar(codigo -> codigo.equals("BS9999"));

        assertEquals(Optional.of("BS0003"), encontrada);
        assertTrue(inexistente.isEmpty());
        assertTrue(lista.buscar(null).isEmpty());
    }

    @Test
    void contemRespondePorIgualdade() {
        ListaEstoque<String> lista = comCincoBolsas();

        assertTrue(lista.contem("BS0001"));
        assertTrue(lista.contem("BS0005"));
        assertFalse(lista.contem("BS9999"));
        assertFalse(lista.contem(null));
    }

    /**
     * CASO 1 da remocao: primeiro no. A referencia primeiro avanca, e a lista
     * continua inteira a partir do segundo elemento.
     */
    @Test
    void removerOPrimeiroNoMoveOInicioDaLista() {
        ListaEstoque<String> lista = comCincoBolsas();

        assertTrue(lista.remover("BS0001"));

        assertEquals(List.of("BS0002", "BS0003", "BS0004", "BS0005"), lista.listar());
        assertEquals(4, lista.tamanho());
        assertFalse(lista.contem("BS0001"));
    }

    /**
     * CASO 2 da remocao: no do meio. O anterior passa a apontar para o
     * proximo do removido, fechando a cadeia sem perder os vizinhos.
     */
    @Test
    void removerNoDoMeioRelgaOAnteriorAoProximo() {
        ListaEstoque<String> lista = comCincoBolsas();

        assertTrue(lista.remover("BS0003"));

        assertEquals(List.of("BS0001", "BS0002", "BS0004", "BS0005"), lista.listar());
        assertEquals(4, lista.tamanho());
    }

    /**
     * CASO 3 da remocao: ultimo no. A referencia ultimo precisa recuar, senao
     * a proxima insercao entraria depois de um no que ja saiu da lista.
     */
    @Test
    void removerOUltimoNoRecuaAReferenciaUltimo() {
        ListaEstoque<String> lista = comCincoBolsas();

        assertTrue(lista.remover("BS0005"));

        assertEquals(List.of("BS0001", "BS0002", "BS0003", "BS0004"), lista.listar());
        assertEquals(4, lista.tamanho());

        // Prova que 'ultimo' recuou: o novo elemento tem de entrar no fim.
        lista.inserir("BS0006");
        assertEquals(List.of("BS0001", "BS0002", "BS0003", "BS0004", "BS0006"),
                lista.listar());
    }

    /**
     * Remover o unico elemento zera as duas pontas de uma vez, e a lista tem
     * de voltar a aceitar insercao normalmente.
     */
    @Test
    void removerOUnicoElementoEsvaziaEPermiteReinsercao() {
        ListaEstoque<String> lista = new ListaEstoque<>();
        lista.inserir("UNICA");

        assertTrue(lista.remover("UNICA"));
        assertTrue(lista.estaVazia());
        assertEquals(0, lista.tamanho());

        lista.inserir("DEPOIS");
        assertEquals(List.of("DEPOIS"), lista.listar());
        assertEquals(1, lista.tamanho());
    }

    @Test
    void removerElementoInexistenteNaoAlteraALista() {
        ListaEstoque<String> lista = comCincoBolsas();

        assertFalse(lista.remover("BS9999"));
        assertFalse(lista.remover(null));

        assertEquals(5, lista.tamanho());
        assertEquals(List.of("BS0001", "BS0002", "BS0003", "BS0004", "BS0005"),
                lista.listar());
    }

    @Test
    void operacoesEmListaVaziaNaoLancamExcecao() {
        ListaEstoque<String> lista = new ListaEstoque<>();

        assertFalse(lista.remover("BS0001"));
        assertFalse(lista.contem("BS0001"));
        assertTrue(lista.buscar(codigo -> true).isEmpty());
        assertTrue(lista.listar().isEmpty());
        assertDoesNotThrow(lista::limpar);
    }

    @Test
    void elementoNuloEhRejeitado() {
        ListaEstoque<String> lista = new ListaEstoque<>();

        assertThrows(IllegalArgumentException.class, () -> lista.inserir(null));
        assertTrue(lista.estaVazia());
    }

    @Test
    void limparEsvaziaAListaEPermiteReuso() {
        ListaEstoque<String> lista = comCincoBolsas();

        lista.limpar();

        assertTrue(lista.estaVazia());
        assertEquals(0, lista.tamanho());
        assertTrue(lista.listar().isEmpty());

        lista.inserir("BS0007");
        assertEquals(List.of("BS0007"), lista.listar());
    }

    @Test
    void listarDevolveListaImutavel() {
        ListaEstoque<String> lista = comCincoBolsas();

        List<String> itens = lista.listar();

        assertThrows(UnsupportedOperationException.class, () -> itens.add("BS9999"));
    }
}
