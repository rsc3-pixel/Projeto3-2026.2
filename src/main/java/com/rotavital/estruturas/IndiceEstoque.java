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
 * <p><b>Tratamento de colisao.</b> Duas chaves diferentes podem cair no
 * mesmo balde do {@link HashMap} - por produzirem o mesmo {@code hashCode}
 * ou por os bits usados no indice coincidirem. O {@code HashMap} resolve
 * isso encadeando as entradas naquele balde numa lista ligada e desempatando
 * por {@code equals}; se um balde passar de {@code TREEIFY_THRESHOLD} = 8
 * entradas <b>e</b> a tabela tiver ao menos {@code MIN_TREEIFY_CAPACITY} =
 * 64 baldes, a lista vira arvore rubro-negra, e volta a ser lista quando cai
 * abaixo de {@code UNTREEIFY_THRESHOLD} = 6. Com tabela menor que 64, em vez
 * de transformar em arvore ele dobra a tabela. O indice do balde e
 * {@code (n - 1) & (h ^ (h >>> 16))}, com {@code n} a capacidade.</p>
 *
 * <p><b>Aqui nada disso e acionado, e isso e por construcao.</b> O espaco de
 * chaves e pequeno e fechado: {@link GrupoSanguineo} tem 8 valores e
 * {@link TipoHemocomponente} tem 4, logo existem no maximo 32 chaves
 * distintas. O {@link ChaveEstoque#hashCode()} e um <b>hash perfeito</b> -
 * {@code grupo.ordinal() * TipoHemocomponente.values().length +
 * tipo.ordinal()} - que mapeia as 32 combinacoes em 0..31 sem repetir, e o
 * mapa e criado com capacidade {@value #CAPACIDADE}. Com valores de 0 a 31,
 * {@code h >>> 16} e zero, entao o indice do balde e o proprio hash: 32
 * baldes distintos, nenhum compartilhado. E 32 fica abaixo do limite de
 * redimensionamento (64 x 0,75 = 48), entao a tabela nunca e dobrada e os
 * indices nunca mudam.</p>
 *
 * <p><b>Antes desta decisao a colisao acontecia por sorte.</b> O hashCode
 * vinha de {@code Objects.hash(grupo, tipo)}, que usa o hashCode dos enums -
 * o de identidade herdado de {@link Object}. Esse valor nao e especificado
 * entre execucoes e depende da ordem de alocacao e ate de flags da maquina
 * virtual: medindo as 32 combinacoes numa tabela de 64 baldes, o numero de
 * combinacoes caindo em balde ja ocupado variou de 6 a 31 conforme o modo de
 * hash de identidade da HotSpot. Eram colisoes inofensivas - cadeias curtas,
 * muito abaixo de 8 - mas o projeto nao controlava quantas seriam.</p>
 *
 * <p><b>Manutencao.</b> Se um dos enums ganhar valores novos, a formula
 * continua perfeita, porque usa {@code values().length} em vez de um numero
 * fixo; o que precisa ser revisto e a capacidade: o produto dos dois
 * tamanhos tem de caber em {@value #CAPACIDADE} x 0,75. O hash nao e
 * persistido em lugar nenhum - vive apenas em memoria, dentro deste mapa -
 * entao mudar a ordem dos valores de um enum nao corrompe dado gravado.</p>
 *
 * <p><b>O que nao e colisao.</b> Varias bolsas da mesma combinacao ficam na
 * mesma lista, que e o <i>valor</i> do mapa. Isso nao e colisao de hash: e o
 * agrupamento desejado, e a razao de o indice existir.</p>
 *
 * <p>Classe Java pura: sem Spring e sem JPA. Quem a mantem viva e sincronizada
 * com o banco e o {@code IndiceEstoqueServico}.</p>
 *
 * <p><b>Complexidade:</b> adicionar, remover e buscar O(1) amortizado no
 * numero de bolsas; a copia devolvida pelo {@link #buscar} e O(k) no tamanho
 * do balde.</p>
 */
public class IndiceEstoque {

    /**
     * Capacidade fixa do mapa, dimensionada para as 32 combinacoes possiveis
     * sem colisao e sem redimensionamento.
     *
     * <p>Precisa ser potencia de dois e maior que 31 para que o indice do
     * balde seja o proprio hash, e grande o bastante para que 32 fique
     * abaixo do limite de carga: 64 x 0,75 = 48.</p>
     */
    private static final int CAPACIDADE = 64;

    private final Map<ChaveEstoque, List<Bolsa>> indice = new HashMap<>(CAPACIDADE);

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
     * Calcula o hash de uma combinacao de grupo e tipo.
     *
     * <p>E um hash perfeito sobre o espaco de chaves: cada uma das
     * {@code 8 x 4 = 32} combinacoes recebe um valor distinto entre 0 e 31, e
     * o mesmo valor em toda execucao, porque {@code ordinal()} e a posicao
     * declarada do enum e nao depende da maquina virtual.</p>
     *
     * <p>Fica package-private e separado do {@code hashCode} para que o teste
     * verifique exatamente a formula que o indice usa, em vez de reimplementa-la
     * - e sem precisar de reflexao para alcancar a chave, que e privada.</p>
     */
    static int hashDaChave(GrupoSanguineo grupoSanguineo, TipoHemocomponente tipo) {
        return grupoSanguineo.ordinal() * TipoHemocomponente.values().length + tipo.ordinal();
    }

    /**
     * Chave composta do indice: grupo sanguineo e tipo de hemocomponente.
     *
     * <p>Imutavel e com os dois metodos de identidade implementados, pelo
     * motivo explicado no javadoc da classe. O {@code hashCode} delega para
     * {@link IndiceEstoque#hashDaChave}, que e perfeito sobre as 32
     * combinacoes possiveis: nenhuma colisao, e o mesmo resultado em toda
     * execucao.</p>
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
            return hashDaChave(grupoSanguineo, tipo);
        }
    }
}
