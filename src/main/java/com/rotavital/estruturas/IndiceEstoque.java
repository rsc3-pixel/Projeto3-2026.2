package com.rotavital.estruturas;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Indice hash para localizar bolsas pela combinacao de grupo sanguineo e tipo
 * de hemocomponente.
 *
 * <p>Sem ele, achar "as bolsas O- de concentrado de hemacias" exigiria
 * percorrer o estoque inteiro, O(n) por consulta. Com a chave composta, a
 * consulta e O(1) no numero de bolsas, porque cai direto no balde da
 * combinacao pedida.</p>
 *
 * <p><b>A chave e um par de enums</b> - {@link GrupoSanguineo} e
 * {@link TipoHemocomponente} - e nao um texto concatenado. Enum compara por
 * identidade, nao tem variacao de maiuscula nem de espaco, e o conjunto de
 * valores possiveis e fechado: combinacao invalida nao chega a existir.</p>
 *
 * <p><b>Por que equals e hashCode precisam andar juntos.</b> O
 * {@link HashMap} localiza o balde pelo {@code hashCode} e so depois compara
 * as chaves daquele balde com {@code equals}. Implementar apenas
 * {@code equals} e o erro classico: duas chaves logicamente iguais recebem
 * {@code hashCode} diferentes do {@link Object}, caem em baldes diferentes, e
 * o {@code get} devolve {@code null} <b>sem lancar erro nenhum</b>. O estoque
 * pareceria vazio, a alocacao recusaria bolsa existente, e nada no log
 * apontaria a causa. O caminho inverso - so {@code hashCode} - e menos grave:
 * as chaves caem no mesmo balde e a comparacao falha, com o mesmo resultado
 * pratico. Por isso os dois estao implementados e cobertos por teste de
 * comportamento, e nao de implementacao: uma bolsa indexada com uma instancia
 * de chave precisa ser encontrada por uma busca que constroi outra.</p>
 *
 * <p><b>Colisao.</b> Chaves diferentes podem produzir o mesmo
 * {@code hashCode} e cair no mesmo balde; o {@code HashMap} resolve isso
 * encadeando as entradas e desempatando por {@code equals}. O tratamento
 * detalhado fica para a subtarefa de documentacao da PI3-96.</p>
 *
 * <p>Classe Java pura: sem Spring e sem JPA. Quem a mantem viva e sincronizada
 * com o banco e o {@code IndiceEstoqueServico}.</p>
 *
 * <p><b>Complexidade:</b> adicionar, remover e buscar O(1) amortizado no
 * numero de bolsas; a copia devolvida pelo {@link #buscar} e O(k) no tamanho
 * do balde.</p>
 */
public class IndiceEstoque {

    private final Map<ChaveEstoque, List<Bolsa>> indice = new HashMap<>();

    /**
     * Indexa a bolsa pela sua combinacao de grupo e tipo.
     *
     * <p>Nao duplica: se uma bolsa de mesmo codigo de rastreio ja estiver
     * indexada, a entrada antiga e substituida pela instancia nova. Sem isso,
     * uma bolsa reindexada depois de mudar de status apareceria duas vezes na
     * busca e poderia ser alocada em duplicidade.</p>
     *
     * @throws IllegalArgumentException se a bolsa for nula
     */
    public void adicionar(Bolsa bolsa) {
        if (bolsa == null) {
            throw new IllegalArgumentException("Bolsa nao pode ser nula");
        }

        ChaveEstoque chave = new ChaveEstoque(bolsa.getGrupoSanguineo(), bolsa.getTipo());
        List<Bolsa> bolsas = indice.computeIfAbsent(chave, ignorada -> new ArrayList<>());

        // Bolsa.equals compara pelo codigo de rastreio, que e o @Id: duas
        // instancias da mesma bolsa sao iguais mesmo com status diferente.
        int existente = bolsas.indexOf(bolsa);
        if (existente >= 0) {
            bolsas.set(existente, bolsa);
        } else {
            bolsas.add(bolsa);
        }
    }

    /**
     * Bolsas indexadas na combinacao pedida.
     *
     * <p>Devolve uma <b>copia imutavel</b>, nao uma visao da lista interna. O
     * indice e compartilhado entre requisicoes e pode ser atualizado enquanto
     * alguem percorre o resultado de uma busca anterior; com uma visao, isso
     * seria {@code ConcurrentModificationException} no meio da alocacao.</p>
     *
     * @return lista imutavel; vazia quando nao ha bolsa da combinacao ou
     *         quando algum argumento e nulo. <b>Nunca {@code null}</b>:
     *         ausencia de dado nao e erro, a mesma decisao que a
     *         {@code MatrizCompatibilidade} adota
     */
    public List<Bolsa> buscar(GrupoSanguineo grupoSanguineo, TipoHemocomponente tipo) {
        if (grupoSanguineo == null || tipo == null) {
            return List.of();
        }

        List<Bolsa> bolsas = indice.get(new ChaveEstoque(grupoSanguineo, tipo));
        return bolsas == null ? List.of() : List.copyOf(bolsas);
    }

    /**
     * Remove a bolsa do indice, comparando por codigo de rastreio.
     *
     * @return {@code true} se removeu; {@code false} se a bolsa era nula ou
     *         nao estava indexada
     */
    public boolean remover(Bolsa bolsa) {
        if (bolsa == null) {
            return false;
        }

        ChaveEstoque chave = new ChaveEstoque(bolsa.getGrupoSanguineo(), bolsa.getTipo());
        List<Bolsa> bolsas = indice.get(chave);
        if (bolsas == null) {
            return false;
        }

        boolean removida = bolsas.remove(bolsa);
        if (bolsas.isEmpty()) {
            // Balde vazio sai do mapa: sem isso o indice acumularia chaves
            // mortas a cada combinacao que ja teve estoque e nao tem mais.
            indice.remove(chave);
        }
        return removida;
    }

    /**
     * Quantidade total de bolsas indexadas, somando todos os baldes.
     */
    public int tamanho() {
        int total = 0;
        for (List<Bolsa> bolsas : indice.values()) {
            total += bolsas.size();
        }
        return total;
    }

    /**
     * Esvazia o indice. Usado na recarga completa a partir do banco.
     */
    public void limpar() {
        indice.clear();
    }

    /**
     * Chave composta do indice: grupo sanguineo e tipo de hemocomponente.
     *
     * <p>Imutavel e com os dois metodos de identidade implementados, pelo
     * motivo explicado no javadoc da classe.</p>
     */
    private static final class ChaveEstoque {
        private final GrupoSanguineo grupoSanguineo;
        private final TipoHemocomponente tipo;

        private ChaveEstoque(GrupoSanguineo grupoSanguineo, TipoHemocomponente tipo) {
            this.grupoSanguineo = Objects.requireNonNull(
                    grupoSanguineo, "Grupo sanguineo nao pode ser nulo");
            this.tipo = Objects.requireNonNull(
                    tipo, "Tipo de hemocomponente nao pode ser nulo");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChaveEstoque)) return false;
            ChaveEstoque that = (ChaveEstoque) o;
            return grupoSanguineo == that.grupoSanguineo && tipo == that.tipo;
        }

        @Override
        public int hashCode() {
            return Objects.hash(grupoSanguineo, tipo);
        }
    }
}
