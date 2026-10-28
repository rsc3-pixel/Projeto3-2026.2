package com.rotavital.compatibilidade;

import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.rotavital.dominio.enums.GrupoSanguineo.AB_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.AB_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.A_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.A_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.B_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.B_POS;
import static com.rotavital.dominio.enums.GrupoSanguineo.O_NEG;
import static com.rotavital.dominio.enums.GrupoSanguineo.O_POS;
import static com.rotavital.dominio.enums.TipoHemocomponente.CONCENTRADO_HEMACIAS;
import static com.rotavital.dominio.enums.TipoHemocomponente.CONCENTRADO_PLAQUETAS;
import static com.rotavital.dominio.enums.TipoHemocomponente.CRIOPRECIPITADO;
import static com.rotavital.dominio.enums.TipoHemocomponente.PLASMA_FRESCO_CONGELADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrizCompatibilidadeTest {

    @Nested
    @DisplayName("Hemacias")
    class Hemacias {

        @Test
        @DisplayName("O- doa para todos os oito grupos")
        void doadorUniversal() {
            for (GrupoSanguineo receptor : GrupoSanguineo.values()) {
                assertTrue(
                        MatrizCompatibilidade.compativel(O_NEG, receptor, CONCENTRADO_HEMACIAS),
                        "O- deveria doar para " + receptor);
            }
        }

        @Test
        @DisplayName("AB+ recebe de todos os oito grupos")
        void receptorUniversal() {
            for (GrupoSanguineo doador : GrupoSanguineo.values()) {
                assertTrue(
                        MatrizCompatibilidade.compativel(doador, AB_POS, CONCENTRADO_HEMACIAS),
                        "AB+ deveria receber de " + doador);
            }
        }

        @Test
        @DisplayName("a relacao nao e simetrica: O- doa para A+, mas A+ nao doa para O-")
        void naoSimetrica() {
            assertTrue(MatrizCompatibilidade.compativel(O_NEG, A_POS, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(A_POS, O_NEG, CONCENTRADO_HEMACIAS));
        }

        @Test
        @DisplayName("positivo nunca doa para o negativo do mesmo grupo ABO")
        void rhPositivoNaoDoaParaNegativo() {
            assertFalse(MatrizCompatibilidade.compativel(A_POS, A_NEG, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(B_POS, B_NEG, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(AB_POS, AB_NEG, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(O_POS, O_NEG, CONCENTRADO_HEMACIAS));
        }

        @Test
        @DisplayName("negativo doa para o positivo do mesmo grupo ABO")
        void rhNegativoDoaParaPositivo() {
            assertTrue(MatrizCompatibilidade.compativel(A_NEG, A_POS, CONCENTRADO_HEMACIAS));
            assertTrue(MatrizCompatibilidade.compativel(B_NEG, B_POS, CONCENTRADO_HEMACIAS));
            assertTrue(MatrizCompatibilidade.compativel(O_NEG, O_POS, CONCENTRADO_HEMACIAS));
        }

        @Test
        @DisplayName("todo grupo e compativel consigo mesmo")
        void reflexiva() {
            for (GrupoSanguineo g : GrupoSanguineo.values()) {
                assertTrue(MatrizCompatibilidade.compativel(g, g, CONCENTRADO_HEMACIAS),
                        g + " deveria ser compativel consigo mesmo");
            }
        }

        @Test
        @DisplayName("O- so recebe de O-")
        void oNegativoSoRecebeDeONegativo() {
            for (GrupoSanguineo doador : GrupoSanguineo.values()) {
                boolean esperado = doador == O_NEG;
                assertEquals(esperado,
                        MatrizCompatibilidade.compativel(doador, O_NEG, CONCENTRADO_HEMACIAS),
                        "O- recebendo de " + doador);
            }
        }

        @Test
        @DisplayName("A nao doa para B nem B para A")
        void gruposAeBsaoIncompativeis() {
            assertFalse(MatrizCompatibilidade.compativel(A_POS, B_POS, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(B_POS, A_POS, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(A_NEG, B_NEG, CONCENTRADO_HEMACIAS));
        }
    }

    @Nested
    @DisplayName("Plaquetas seguem a mesma regra das hemacias")
    class Plaquetas {

        @Test
        @DisplayName("O- doa plaquetas para A+, e A+ nao doa para O-")
        void mesmaRegraDasHemacias() {
            assertTrue(MatrizCompatibilidade.compativel(O_NEG, A_POS, CONCENTRADO_PLAQUETAS));
            assertFalse(MatrizCompatibilidade.compativel(A_POS, O_NEG, CONCENTRADO_PLAQUETAS));
        }
    }

    @Nested
    @DisplayName("Plasma segue a regra invertida")
    class Plasma {

        @Test
        @DisplayName("AB e doador universal de plasma, o oposto das hemacias")
        void abEDoadorUniversalDePlasma() {
            for (GrupoSanguineo receptor : GrupoSanguineo.values()) {
                assertTrue(
                        MatrizCompatibilidade.compativel(AB_POS, receptor, PLASMA_FRESCO_CONGELADO),
                        "plasma AB+ deveria servir a " + receptor);
            }
        }

        @Test
        @DisplayName("O e receptor universal de plasma, o oposto das hemacias")
        void oEReceptorUniversalDePlasma() {
            for (GrupoSanguineo doador : GrupoSanguineo.values()) {
                assertTrue(
                        MatrizCompatibilidade.compativel(doador, O_NEG, PLASMA_FRESCO_CONGELADO),
                        "O- deveria receber plasma de " + doador);
            }
        }

        @Test
        @DisplayName("a inversao e real: O- nao doa plasma para A+")
        void inversaoEmRelacaoAsHemacias() {
            // Em hemacias isto seria verdadeiro; em plasma, nao.
            assertTrue(MatrizCompatibilidade.compativel(O_NEG, A_POS, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(O_NEG, A_POS, PLASMA_FRESCO_CONGELADO));
        }

        @Test
        @DisplayName("crioprecipitado segue a regra do plasma")
        void crioprecipitadoSegueOPlasma() {
            assertTrue(MatrizCompatibilidade.compativel(AB_POS, O_NEG, CRIOPRECIPITADO));
            assertFalse(MatrizCompatibilidade.compativel(O_NEG, AB_POS, CRIOPRECIPITADO));
        }

        @Test
        @DisplayName("o fator Rh nao restringe a transfusao de plasma")
        void rhNaoRestringePlasma() {
            assertTrue(MatrizCompatibilidade.compativel(A_POS, A_NEG, PLASMA_FRESCO_CONGELADO));
            assertTrue(MatrizCompatibilidade.compativel(A_NEG, A_POS, PLASMA_FRESCO_CONGELADO));
        }
    }

    @Nested
    @DisplayName("doadoresPara")
    class DoadoresPara {

        @Test
        @DisplayName("A+ pode receber hemacias de quatro grupos")
        void conjuntoDeDoadoresDeAPositivo() {
            Set<GrupoSanguineo> doadores =
                    MatrizCompatibilidade.doadoresPara(A_POS, CONCENTRADO_HEMACIAS);
            assertEquals(Set.of(O_NEG, O_POS, A_NEG, A_POS), doadores);
        }

        @Test
        @DisplayName("O- so pode receber hemacias de O-")
        void conjuntoDeDoadoresDeONegativo() {
            Set<GrupoSanguineo> doadores =
                    MatrizCompatibilidade.doadoresPara(O_NEG, CONCENTRADO_HEMACIAS);
            assertEquals(Set.of(O_NEG), doadores);
        }

        @Test
        @DisplayName("alterar o conjunto devolvido nao muda a tabela")
        void devolveCopiaDefensiva() {
            Set<GrupoSanguineo> doadores =
                    MatrizCompatibilidade.doadoresPara(O_NEG, CONCENTRADO_HEMACIAS);
            doadores.add(A_POS);

            // A tabela original segue intacta: a consulta seguinte nao enxerga
            // a alteracao feita acima.
            assertFalse(MatrizCompatibilidade.compativel(A_POS, O_NEG, CONCENTRADO_HEMACIAS));
            assertEquals(Set.of(O_NEG),
                    MatrizCompatibilidade.doadoresPara(O_NEG, CONCENTRADO_HEMACIAS));
        }

        @Test
        @DisplayName("o conjunto e coerente com o metodo compativel")
        void coerenteComCompativel() {
            for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
                for (GrupoSanguineo receptor : GrupoSanguineo.values()) {
                    Set<GrupoSanguineo> doadores =
                            MatrizCompatibilidade.doadoresPara(receptor, tipo);
                    for (GrupoSanguineo doador : GrupoSanguineo.values()) {
                        assertEquals(
                                doadores.contains(doador),
                                MatrizCompatibilidade.compativel(doador, receptor, tipo),
                                tipo + ": " + doador + " -> " + receptor);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Entrada nula")
    class EntradaNula {

        @Test
        @DisplayName("null devolve incompativel, nao excecao")
        void nuloNaoAutorizaTransfusao() {
            assertFalse(MatrizCompatibilidade.compativel(null, A_POS, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(O_NEG, null, CONCENTRADO_HEMACIAS));
            assertFalse(MatrizCompatibilidade.compativel(O_NEG, A_POS, null));
        }

        @Test
        @DisplayName("doadoresPara com null devolve conjunto vazio")
        void doadoresParaNulo() {
            assertTrue(MatrizCompatibilidade.doadoresPara(null, CONCENTRADO_HEMACIAS).isEmpty());
            assertTrue(MatrizCompatibilidade.doadoresPara(A_POS, null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Cobertura da tabela")
    class CoberturaDaTabela {

        @Test
        @DisplayName("todo grupo tem entrada em todo hemocomponente")
        void nenhumaCombinacaoFaltando() {
            for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
                for (GrupoSanguineo receptor : GrupoSanguineo.values()) {
                    Set<GrupoSanguineo> doadores =
                            MatrizCompatibilidade.doadoresPara(receptor, tipo);
                    assertFalse(doadores.isEmpty(),
                            "sem doadores para " + receptor + " em " + tipo);
                }
            }
        }
    }
}
