package com.rotavital.estruturas.basicas;

import java.util.List;
import java.util.Optional;

/**
 * Pilha LIFO (Last In, First Out), construida a mao.
 *
 * <p>Espelha {@code aed/unidade1/c/pilha_historico.c}. O encadeamento e feito
 * com a classe interna {@link No}, sem {@code Stack} nem {@code ArrayDeque}:
 * a entrega e demonstrar a estrutura, nao consumir uma pronta.</p>
 *
 * <p>LIFO combina com historico porque o que interessa consultar primeiro e a
 * operacao mais recente, e porque desempilhar e exatamente o movimento de
 * desfazer a ultima acao registrada.</p>
 *
 * <p>Equivalencia com a versao em C, ponteiro por referencia:</p>
 * <ul>
 *   <li>{@code NoPilha *topo} (pilha_historico.h:24) vira o campo
 *       {@link #topo}. Uma referencia so basta: insercao e remocao acontecem
 *       na mesma ponta, as duas em O(1), e o fundo da pilha nao e usado;</li>
 *   <li>{@code struct NoPilha *abaixo} (pilha_historico.h:20) vira
 *       {@code No.proximo}. O nome muda porque as tres estruturas deste pacote
 *       usam o mesmo campo; o papel e identico - apontar para o no que estava
 *       no topo antes deste;</li>
 *   <li>{@code novo->abaixo = pilha->topo} seguido de
 *       {@code pilha->topo = novo} (pilha_historico.c:40-41) vira
 *       {@code novo.proximo = topo} e {@code topo = novo}. A ordem importa: o
 *       no novo precisa apontar para o antigo topo antes que a referencia
 *       suba, senao o endereco de tudo o que estava embaixo se perderia;</li>
 *   <li>{@code pilha->topo = removido->abaixo} (pilha_historico.c:63) vira
 *       {@code topo = removido.proximo};</li>
 *   <li>{@code free(removido)} (pilha_historico.c:65) nao tem equivalente: o
 *       coletor de lixo recolhe o no quando ninguem mais o referencia.</li>
 * </ul>
 *
 * <p>Note que a pilha nao precisa de tratamento especial para o caso vazio:
 * quando {@link #topo} e nulo, o no novo fica com {@code proximo} nulo e passa
 * a ser o fundo. E a mesma propriedade da versao em C.</p>
 */
public class PilhaHistorico<T> {

    private No<T> topo;
    private int tamanho;

    /**
     * No do encadeamento. Equivale ao {@code struct NoPilha} do C, cujo campo
     * de ligacao se chama {@code abaixo}.
     */
    private static final class No<E> {
        private final E elemento;
        private No<E> proximo;

        private No(E elemento) {
            this.elemento = elemento;
            this.proximo = null;
        }
    }

    /**
     * Empilha no topo, como {@code pilha_empilhar}.
     *
     * @throws IllegalArgumentException se o elemento for nulo
     */
    public void empilhar(T elemento) {
        if (elemento == null) {
            throw new IllegalArgumentException("Elemento nao pode ser nulo");
        }

        No<T> novo = new No<>(elemento);

        // O no novo aponta para o antigo topo e so depois a referencia 'topo'
        // sobe. Nesta ordem o resto da pilha permanece alcancavel durante toda
        // a operacao. Com a pilha vazia, 'topo' e nulo e o novo no se torna o
        // fundo, sem precisar de caso separado.
        novo.proximo = topo;
        topo = novo;

        tamanho++;
    }

    /**
     * Retira do topo, como {@code pilha_desempilhar}.
     *
     * <p>Pilha vazia devolve {@link Optional#empty()}, nunca excecao:
     * historico sem operacao a desfazer e situacao normal.</p>
     */
    public Optional<T> desempilhar() {
        if (topo == null) {
            return Optional.empty();
        }

        No<T> removido = topo;

        // O topo desce para o no que estava abaixo. Se era o unico no,
        // 'proximo' e nulo e a pilha volta ao estado vazio.
        topo = removido.proximo;

        // Solta a ligacao do no retirado para que ele nao segure a pilha viva.
        removido.proximo = null;

        tamanho--;
        return Optional.of(removido.elemento);
    }

    /**
     * Consulta a operacao do topo, sem retirar. Equivale ao
     * {@code pilha_topo}.
     */
    public Optional<T> topo() {
        if (topo == null) {
            return Optional.empty();
        }
        return Optional.of(topo.elemento);
    }

    /**
     * Elementos do topo para a base, que e a ordem do mais recente ao mais
     * antigo. Equivale ao {@code pilha_listar}.
     *
     * @return lista imutavel
     */
    @SuppressWarnings("unchecked")
    public List<T> listar() {
        // Vetor do tamanho exato, sem colecao pronta como acumulador.
        T[] elementos = (T[]) new Object[tamanho];

        int posicao = 0;
        for (No<T> atual = topo; atual != null; atual = atual.proximo) {
            elementos[posicao] = atual.elemento;
            posicao++;
        }

        return List.of(elementos);
    }

    public int tamanho() {
        return tamanho;
    }

    public boolean estaVazia() {
        return topo == null;
    }

    /**
     * Esvazia a pilha, equivalente ao {@code pilha_destruir}.
     *
     * <p>O percurso espelha o laco do C, que precisa liberar no por no.
     * Aqui soltar {@link #topo} bastaria.</p>
     */
    public void limpar() {
        No<T> atual = topo;
        while (atual != null) {
            No<T> proximo = atual.proximo;
            atual.proximo = null;
            atual = proximo;
        }

        topo = null;
        tamanho = 0;
    }
}
