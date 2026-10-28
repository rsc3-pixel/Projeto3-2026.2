package com.rotavital.compatibilidade;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.rotavital.dominio.enums.GrupoSanguineo.AB_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.AB_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.A_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.A_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.B_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.B_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.O_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.O_POS;

/**
 * Responde quais grupos sanguineos podem doar para quais, por tipo de
 * hemocomponente.
 *
 * <p><strong>A compatibilidade aqui e didatica e nao substitui protocolo
 * clinico.</strong> Na pratica transfusional entram prova cruzada, pesquisa
 * de anticorpos irregulares e outros fatores que este projeto nao modela.</p>
 *
 * <p><strong>A relacao nao e simetrica.</strong> {@code compativel(O_NEG,
 * A_POS)} e verdadeiro, porque O- doa para A+; {@code compativel(A_POS,
 * O_NEG)} e falso, porque A+ nao doa para O-. Trocar a ordem dos argumentos
 * muda a resposta, e por isso os parametros se chamam {@code doador} e
 * {@code receptor} em vez de {@code a} e {@code b}.</p>
 *
 * <p>A estrutura e um {@link EnumMap} de receptor para o conjunto de doadores
 * aceitos. {@code EnumMap} e um vetor indexado pelo {@code ordinal} do enum,
 * entao a consulta e O(1) e nao sofre colisao, diferente de um
 * {@code HashMap}. O custo de memoria e fixo: oito posicoes.</p>
 */
public final class MatrizCompatibilidade {

    /**
     * Regra das hemacias, que e a mais restritiva: o receptor nao pode receber
     * antigeno que nao possui, senao seus anticorpos atacam as hemacias
     * transfundidas.
     *
     * <p>Duas leituras saltam da tabela: <strong>O- e doador universal</strong>,
     * porque aparece em todas as linhas, e <strong>AB+ e receptor
     * universal</strong>, porque aceita os oito.</p>
     */
    private static final Map<GrupoSanguineo, Set<GrupoSanguineo>> HEMACIAS =
            new EnumMap<>(GrupoSanguineo.class);

    /**
     * Regra do plasma, que e <strong>invertida</strong> em relacao as hemacias.
     *
     * <p>O plasma carrega anticorpos, nao antigenos. Quem doa hemacia para
     * todos (O-) tem plasma com anticorpos anti-A e anti-B, e por isso seu
     * plasma so serve para receptores O. Ja AB, que nao tem esses anticorpos,
     * e o <strong>doador universal de plasma</strong>.</p>
     *
     * <p>Ignorar essa inversao e o erro mais comum ao modelar compatibilidade,
     * porque a intuicao construida com hemacias leva para o lado errado.</p>
     */
    private static final Map<GrupoSanguineo, Set<GrupoSanguineo>> PLASMA =
            new EnumMap<>(GrupoSanguineo.class);

    static {
        // --- Hemacias: receptor -> de quem pode receber ---
        HEMACIAS.put(O_NEG,  EnumSet.of(O_NEG));
        HEMACIAS.put(O_POS,  EnumSet.of(O_NEG, O_POS));
        HEMACIAS.put(A_NEG,  EnumSet.of(O_NEG, A_NEG));
        HEMACIAS.put(A_POS,  EnumSet.of(O_NEG, O_POS, A_NEG, A_POS));
        HEMACIAS.put(B_NEG,  EnumSet.of(O_NEG, B_NEG));
        HEMACIAS.put(B_POS,  EnumSet.of(O_NEG, O_POS, B_NEG, B_POS));
        HEMACIAS.put(AB_NEG, EnumSet.of(O_NEG, A_NEG, B_NEG, AB_NEG));
        HEMACIAS.put(AB_POS, EnumSet.allOf(GrupoSanguineo.class));

        // --- Plasma: receptor -> de quem pode receber (regra invertida) ---
        //
        // O fator Rh nao restringe a transfusao de plasma, entao cada linha
        // aceita as duas variantes do grupo ABO correspondente.
        PLASMA.put(O_NEG,  EnumSet.allOf(GrupoSanguineo.class));
        PLASMA.put(O_POS,  EnumSet.allOf(GrupoSanguineo.class));
        PLASMA.put(A_NEG,  EnumSet.of(A_NEG, A_POS, AB_NEG, AB_POS));
        PLASMA.put(A_POS,  EnumSet.of(A_NEG, A_POS, AB_NEG, AB_POS));
        PLASMA.put(B_NEG,  EnumSet.of(B_NEG, B_POS, AB_NEG, AB_POS));
        PLASMA.put(B_POS,  EnumSet.of(B_NEG, B_POS, AB_NEG, AB_POS));
        PLASMA.put(AB_NEG, EnumSet.of(AB_NEG, AB_POS));
        PLASMA.put(AB_POS, EnumSet.of(AB_NEG, AB_POS));
    }

    private MatrizCompatibilidade() {
        // Classe utilitaria: a tabela e constante e nao ha estado a guardar.
    }

    /**
     * Diz se uma bolsa do grupo {@code doador} pode ser transfundida em um
     * paciente do grupo {@code receptor}, considerando o hemocomponente.
     *
     * <p>A ordem dos dois primeiros argumentos importa: a relacao nao e
     * simetrica.</p>
     *
     * @param doador   grupo sanguineo da bolsa
     * @param receptor grupo sanguineo do paciente
     * @param tipo     hemocomponente, porque plasma e crioprecipitado seguem
     *                 regra invertida em relacao as hemacias
     * @return {@code false} se qualquer argumento for {@code null}, em vez de
     *         lancar excecao: ausencia de dado nao e autorizacao para
     *         transfundir
     */
    public static boolean compativel(GrupoSanguineo doador,
                                     GrupoSanguineo receptor,
                                     TipoHemocomponente tipo) {
        if (doador == null || receptor == null || tipo == null) {
            return false;
        }
        return tabelaDe(tipo).get(receptor).contains(doador);
    }

    /**
     * Conjunto de grupos que podem doar para {@code receptor}, naquele
     * hemocomponente.
     *
     * <p>Serve a selecao de bolsas: em vez de testar bolsa por bolsa, o
     * chamador obtem de uma vez quais grupos procurar no estoque.</p>
     *
     * @return conjunto imutavel; vazio se algum argumento for {@code null}
     */
    public static Set<GrupoSanguineo> doadoresPara(GrupoSanguineo receptor,
                                                   TipoHemocomponente tipo) {
        if (receptor == null || tipo == null) {
            return EnumSet.noneOf(GrupoSanguineo.class);
        }
        // Copia defensiva: sem ela, quem chamasse poderia alterar a tabela
        // estatica e mudar a regra para a aplicacao inteira.
        return EnumSet.copyOf(tabelaDe(tipo).get(receptor));
    }

    /**
     * Escolhe a tabela conforme o hemocomponente.
     *
     * <p>Plasma e crioprecipitado sao derivados do plasma e seguem a regra
     * invertida. Hemacias e plaquetas seguem a regra das hemacias.</p>
     */
    private static Map<GrupoSanguineo, Set<GrupoSanguineo>> tabelaDe(TipoHemocomponente tipo) {
        return switch (tipo) {
            case PLASMA_FRESCO_CONGELADO, CRIOPRECIPITADO -> PLASMA;
            case CONCENTRADO_HEMACIAS, CONCENTRADO_PLAQUETAS -> HEMACIAS;
        };
    }
}
