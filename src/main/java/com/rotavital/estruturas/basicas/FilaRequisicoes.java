package com.rotavital.estruturas.basicas;

import java.util.List;
import java.util.Optional;

/**
 * Fila FIFO (First In, First Out), construida a mao.
 *
 * <p>Espelha {@code aed/unidade1/c/fila_requisicoes.c}. O encadeamento e feito
 * com a classe interna {@link No}, sem nenhuma colecao do {@code java.util}
 * como armazenamento: usar {@code ArrayDeque} esconderia justamente o que a
 * entrega precisa mostrar.</p>
 *
 * <p>FIFO e a ordem de atendimento por chegada: quem entrou primeiro sai
 * primeiro. A prioridade de uma requisicao, se existir no elemento, e apenas
 * dado carregado - reordenar por prioridade exigiria fila de prioridade, que
 * e Unidade 2.</p>
 *
 * <p>Equivalencia com a versao em C, ponteiro por referencia:</p>
 * <ul>
 *   <li>{@code NoFila *frente} (fila_requisicoes.h:25) vira o campo
 *       {@link #frente}, por onde sai;</li>
 *   <li>{@code NoFila *tras} (fila_requisicoes.h:26) vira o campo
 *       {@link #tras}, por onde entra. Manter as duas pontas e o que deixa
 *       entrada e saida em O(1);</li>
 *   <li>{@code struct NoFila *proximo} (fila_requisicoes.h:21) vira
 *       {@code No.proximo};</li>
 *   <li>{@code fila->tras->proximo = novo} seguido de
 *       {@code fila->tras = novo} (fila_requisicoes.c:46-47) vira
 *       {@code tras.proximo = novo} e {@code tras = novo}, nesta ordem;</li>
 *   <li>{@code fila->frente = removido->proximo} (fila_requisicoes.c:67) vira
 *       {@code frente = removido.proximo};</li>
 *   <li>{@code fila->tras = NULL} quando a fila esvazia
 *       (fila_requisicoes.c:75) vira {@code tras = null}. No C e obrigatorio
 *       para nao deixar ponteiro para memoria liberada; aqui continua
 *       necessario, senao o ultimo no nunca seria coletado e a proxima
 *       insercao escreveria em um no fora da fila;</li>
 *   <li>{@code free(removido)} (fila_requisicoes.c:78) nao tem equivalente: o
 *       coletor de lixo recolhe o no quando ninguem mais o referencia.</li>
 * </ul>
 *
 * <p>A versao em C copia a requisicao para um parametro de saida antes do
 * {@code free}, porque o no deixaria de existir. Em Java isso nao e preciso: o
 * {@link Optional} devolvido carrega a referencia do elemento, que sobrevive
 * ao descarte do no.</p>
 */
public class FilaRequisicoes<T> {

    private No<T> frente;
    private No<T> tras;
    private int tamanho;

    /**
     * No do encadeamento. Equivale ao {@code struct NoFila} do C.
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
     * Enfileira no fim, como {@code fila_enfileirar}.
     *
     * @throws IllegalArgumentException se o elemento for nulo
     */
    public void enfileirar(T elemento) {
        if (elemento == null) {
            throw new IllegalArgumentException("Elemento nao pode ser nulo");
        }

        No<T> novo = new No<>(elemento);

        if (tras == null) {
            // Fila vazia: o no novo e simultaneamente a frente e o tras.
            // Testar 'tras' e nao 'frente' e proposital: e a referencia que a
            // insercao manipula, igual ao C.
            frente = novo;
            tras = novo;
        } else {
            // Liga o ultimo no ao novo ANTES de mover 'tras'. Invertendo,
            // o antigo ultimo ficaria desconectado.
            tras.proximo = novo;
            tras = novo;
        }

        tamanho++;
    }

    /**
     * Retira da frente, como {@code fila_desenfileirar}.
     *
     * <p>Fila vazia devolve {@link Optional#empty()}, nunca excecao: fila sem
     * requisicao e situacao normal da operacao, nao erro de programa.</p>
     */
    public Optional<T> desenfileirar() {
        if (frente == null) {
            return Optional.empty();
        }

        No<T> removido = frente;

        // A frente avanca para o no seguinte, que pode ser nulo.
        frente = removido.proximo;

        if (frente == null) {
            // A fila esvaziou. Sem zerar 'tras', ele seguiria apontando para
            // o no que acabou de sair, e a proxima insercao faria
            // 'tras.proximo = novo' em um no que nao pertence mais a fila.
            tras = null;
        }

        // Solta a ligacao do no retirado para que ele nao segure a fila viva.
        removido.proximo = null;

        tamanho--;
        return Optional.of(removido.elemento);
    }

    /**
     * Consulta quem sera atendido, sem retirar. Equivale ao
     * {@code fila_frente}.
     */
    public Optional<T> frente() {
        if (frente == null) {
            return Optional.empty();
        }
        return Optional.of(frente.elemento);
    }

    /**
     * Elementos na ordem de atendimento, da frente para o tras. Equivale ao
     * {@code fila_listar}.
     *
     * @return lista imutavel
     */
    @SuppressWarnings("unchecked")
    public List<T> listar() {
        // Vetor do tamanho exato, sem colecao pronta como acumulador.
        T[] elementos = (T[]) new Object[tamanho];

        int posicao = 0;
        for (No<T> atual = frente; atual != null; atual = atual.proximo) {
            elementos[posicao] = atual.elemento;
            posicao++;
        }

        return List.of(elementos);
    }

    public int tamanho() {
        return tamanho;
    }

    public boolean estaVazia() {
        return frente == null;
    }

    /**
     * Esvazia a fila, equivalente ao {@code fila_destruir}.
     *
     * <p>O percurso espelha o laco do C, que precisa liberar no por no.
     * Aqui soltar {@link #frente} bastaria, porque a cadeia inteira ficaria
     * inalcancavel de uma vez.</p>
     */
    public void limpar() {
        No<T> atual = frente;
        while (atual != null) {
            No<T> proximo = atual.proximo;
            atual.proximo = null;
            atual = proximo;
        }

        frente = null;
        tras = null;
        tamanho = 0;
    }
}
