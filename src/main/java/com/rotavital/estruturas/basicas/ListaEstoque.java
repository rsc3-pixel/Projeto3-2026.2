package com.rotavital.estruturas.basicas;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Lista encadeada simples, construida a mao.
 *
 * <p>Espelha {@code aed/unidade1/c/lista_estoque.c}. O encadeamento e feito
 * com a classe interna {@link No}, sem nenhuma colecao do {@code java.util}
 * como armazenamento: a entrega e demonstrar a estrutura, e chamar
 * {@code LinkedList} nao demonstraria nada.</p>
 *
 * <p>Equivalencia com a versao em C, ponteiro por referencia:</p>
 * <ul>
 *   <li>{@code NoEstoque *primeiro} (lista_estoque.h:25) vira o campo
 *       {@link #primeiro};</li>
 *   <li>{@code NoEstoque *ultimo} (lista_estoque.h:26) vira o campo
 *       {@link #ultimo}, mantido pelo mesmo motivo do C: sem ele a insercao
 *       no fim teria de percorrer a lista toda, O(n) em vez de O(1);</li>
 *   <li>{@code struct NoEstoque *proximo} (lista_estoque.h:21) vira
 *       {@code No.proximo};</li>
 *   <li>{@code novo->proximo = NULL} (lista_estoque.c:33) vira o
 *       {@code proximo} nulo do no recem-criado;</li>
 *   <li>{@code lista->ultimo->proximo = novo} seguido de
 *       {@code lista->ultimo = novo} (lista_estoque.c:48-49) vira
 *       {@code ultimo.proximo = novo} e {@code ultimo = novo}, na mesma ordem
 *       e pelo mesmo motivo: invertida, a cadeia ficaria interrompida;</li>
 *   <li>{@code lista->primeiro = atual->proximo} (lista_estoque.c:84) vira
 *       {@code primeiro = atual.proximo}, o caso do primeiro no;</li>
 *   <li>{@code anterior->proximo = atual->proximo} (lista_estoque.c:90) vira
 *       {@code anterior.proximo = atual.proximo}, o caso do no do meio;</li>
 *   <li>{@code lista->ultimo = anterior} (lista_estoque.c:102) vira
 *       {@code ultimo = anterior}, o caso do ultimo no;</li>
 *   <li>{@code free(atual)} (lista_estoque.c:105) nao tem equivalente: o no
 *       desligado deixa de ser alcancavel e o coletor de lixo o recolhe. E a
 *       unica diferenca real entre as duas versoes.</li>
 * </ul>
 *
 * <p>Generica em {@code T} e sem dependencia do dominio, para poder ser
 * consumida pela aplicacao sem amarrar a estrutura a uma entidade.</p>
 *
 * <p><b>Complexidade:</b> inserir O(1); remover, buscar e contem O(n);
 * tamanho e estaVazia O(1), porque o tamanho e mantido em um contador em vez
 * de recontado a cada chamada.</p>
 */
public class ListaEstoque<T> {

    private No<T> primeiro;
    private No<T> ultimo;
    private int tamanho;

    /**
     * No do encadeamento: o elemento e a referencia para o proximo.
     *
     * <p>Equivale ao {@code struct NoEstoque} do C. O elemento e final porque
     * o no nunca troca de conteudo: para mudar a lista, nos entram e saem.</p>
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
     * Insere no fim da lista, como {@code lista_inserir}.
     *
     * @throws IllegalArgumentException se o elemento for nulo
     */
    public void inserir(T elemento) {
        exigirNaoNulo(elemento);

        No<T> novo = new No<>(elemento);

        if (ultimo == null) {
            // Lista vazia: o no novo e ao mesmo tempo o primeiro e o ultimo.
            primeiro = novo;
            ultimo = novo;
        } else {
            // Liga o antigo ultimo ao novo ANTES de mover a referencia
            // 'ultimo'. Na ordem inversa, o no anterior ficaria desligado.
            ultimo.proximo = novo;
            ultimo = novo;
        }

        tamanho++;
    }

    /**
     * Remove a primeira ocorrencia do elemento, comparando por
     * {@code equals}, como {@code lista_remover} compara por {@code strcmp}.
     *
     * <p>Trata os tres casos de encadeamento: primeiro no, no do meio e
     * ultimo no, ajustando {@link #ultimo} quando necessario.</p>
     *
     * @return {@code true} se removeu, {@code false} se o elemento nao estava
     *         na lista ou era nulo
     */
    public boolean remover(T elemento) {
        if (elemento == null) {
            return false;
        }

        // Percorre guardando o anterior: em lista simplesmente encadeada nao
        // ha como voltar, e desligar um no exige ter em maos quem o aponta.
        No<T> atual = primeiro;
        No<T> anterior = null;

        while (atual != null && !Objects.equals(atual.elemento, elemento)) {
            anterior = atual;
            atual = atual.proximo;
        }

        if (atual == null) {
            return false;
        }

        if (anterior == null) {
            // CASO 1 - primeiro no: nao existe anterior para religar, entao o
            // inicio da lista passa a ser o no seguinte.
            primeiro = atual.proximo;
        } else {
            // CASO 2 - no do meio ou no final: o anterior salta por cima do
            // removido, fechando a cadeia.
            anterior.proximo = atual.proximo;
        }

        if (atual == ultimo) {
            // CASO 3 - era o ultimo no: a referencia 'ultimo' recua para o
            // anterior. Quando o removido era o unico no, 'anterior' e nulo e
            // 'ultimo' fica nulo, que e o estado de lista vazia.
            ultimo = anterior;
        }

        // Sem free: ao sair do encadeamento o no fica inalcancavel. Soltar o
        // 'proximo' dele evita que um no removido continue apontando para a
        // lista viva, o que seguraria a cadeia inteira se alguem guardasse
        // uma referencia ao no removido.
        atual.proximo = null;

        tamanho--;
        return true;
    }

    /**
     * Primeiro elemento que satisfaz o criterio, percorrendo do primeiro ao
     * ultimo. Equivale ao {@code lista_buscar}, que devolve ponteiro para a
     * bolsa ou {@code NULL}.
     *
     * <p>Recebe um {@link Predicate} em vez de uma chave de texto porque a
     * estrutura e generica e nao conhece qual campo identifica o elemento.
     * Busca sequencial O(n): indexar por chave seria tabela hash, Unidade 2.</p>
     *
     * @return o elemento encontrado, ou {@link Optional#empty()} se nenhum
     *         satisfizer o criterio ou se o criterio for nulo
     */
    public Optional<T> buscar(Predicate<T> criterio) {
        if (criterio == null) {
            return Optional.empty();
        }

        for (No<T> atual = primeiro; atual != null; atual = atual.proximo) {
            if (criterio.test(atual.elemento)) {
                return Optional.of(atual.elemento);
            }
        }

        return Optional.empty();
    }

    /**
     * Indica se o elemento esta na lista, comparando por {@code equals}.
     */
    public boolean contem(T elemento) {
        if (elemento == null) {
            return false;
        }

        for (No<T> atual = primeiro; atual != null; atual = atual.proximo) {
            if (Objects.equals(atual.elemento, elemento)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Elementos na ordem da lista, do primeiro ao ultimo. Equivale ao
     * {@code lista_listar}, que percorre imprimindo.
     *
     * @return lista imutavel
     */
    @SuppressWarnings("unchecked")
    public List<T> listar() {
        // Vetor do tamanho exato, que o contador ja conhece. Nao usa
        // ArrayList nem nenhuma colecao do java.util como acumulador: a
        // entrega proibe colecao pronta, e List.of devolve imutavel direto.
        T[] elementos = (T[]) new Object[tamanho];

        int posicao = 0;
        for (No<T> atual = primeiro; atual != null; atual = atual.proximo) {
            elementos[posicao] = atual.elemento;
            posicao++;
        }

        return List.of(elementos);
    }

    public int tamanho() {
        return tamanho;
    }

    public boolean estaVazia() {
        return primeiro == null;
    }

    /**
     * Esvazia a lista, equivalente ao {@code lista_destruir}.
     *
     * <p>Em C o percurso liberando no por no e obrigatorio, senao cada no
     * vazaria. Aqui bastaria soltar {@link #primeiro}, porque a cadeia inteira
     * ficaria inalcancavel de uma vez. O percurso foi mantido de proposito,
     * para espelhar o laco do C e para que nenhum no removido continue
     * apontando para outro.</p>
     */
    public void limpar() {
        No<T> atual = primeiro;
        while (atual != null) {
            // Guarda o proximo antes de soltar a ligacao, como o C guarda
            // antes do free.
            No<T> proximo = atual.proximo;
            atual.proximo = null;
            atual = proximo;
        }

        primeiro = null;
        ultimo = null;
        tamanho = 0;
    }

    private static void exigirNaoNulo(Object elemento) {
        if (elemento == null) {
            throw new IllegalArgumentException("Elemento nao pode ser nulo");
        }
    }

}
