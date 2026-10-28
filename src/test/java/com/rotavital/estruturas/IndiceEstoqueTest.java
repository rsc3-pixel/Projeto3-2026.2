package com.rotavital.estruturas;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IndiceEstoqueTest {

    @Test
    void buscaEmIndiceVazioRetornaListaVazia() {
        IndiceEstoque indice = new IndiceEstoque();

        List<Bolsa> resultado = indice.buscar(
                GrupoSanguineo.O_NEG,
                TipoHemocomponente.CONCENTRADO_HEMACIAS
        );

        assertTrue(resultado.isEmpty());
    }

    @Test
    void encontraElementoUnicoPelaChave() {
        IndiceEstoque indice = new IndiceEstoque();
        Bolsa bolsa = criarBolsa(
                "B001",
                GrupoSanguineo.A_POS,
                TipoHemocomponente.PLASMA_FRESCO_CONGELADO
        );

        indice.adicionar(bolsa);

        assertEquals(
                List.of(bolsa),
                indice.buscar(GrupoSanguineo.A_POS, TipoHemocomponente.PLASMA_FRESCO_CONGELADO)
        );
    }

    @Test
    void separaBolsasPorGrupoETipo() {
        IndiceEstoque indice = new IndiceEstoque();
        Bolsa oPosHemacias = criarBolsa(
                "B001", GrupoSanguineo.O_POS, TipoHemocomponente.CONCENTRADO_HEMACIAS);
        Bolsa oPosPlasma = criarBolsa(
                "B002", GrupoSanguineo.O_POS, TipoHemocomponente.PLASMA_FRESCO_CONGELADO);
        Bolsa aPosHemacias = criarBolsa(
                "B003", GrupoSanguineo.A_POS, TipoHemocomponente.CONCENTRADO_HEMACIAS);

        indice.adicionar(oPosHemacias);
        indice.adicionar(oPosPlasma);
        indice.adicionar(aPosHemacias);

        assertEquals(
                List.of(oPosHemacias),
                indice.buscar(GrupoSanguineo.O_POS, TipoHemocomponente.CONCENTRADO_HEMACIAS)
        );
    }

    @Test
    void agrupaMaisDeUmaBolsaNaMesmaChave() {
        IndiceEstoque indice = new IndiceEstoque();
        Bolsa b1 = criarBolsa(
                "B001", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS);
        Bolsa b2 = criarBolsa(
                "B002", GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS);

        indice.adicionar(b1);
        indice.adicionar(b2);

        assertEquals(2,
                indice.buscar(GrupoSanguineo.O_NEG, TipoHemocomponente.CONCENTRADO_HEMACIAS).size());
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Contrato da busca")
    class ContratoDaBusca {

        @Test
        @DisplayName("Argumento nulo devolve lista vazia, nao NullPointerException")
        void argumentoNuloDevolveListaVazia() {
            IndiceEstoque indice = new IndiceEstoque();
            indice.adicionar(criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));

            // Ausencia de dado nao e erro: mesma decisao da MatrizCompatibilidade.
            assertDoesNotThrow(() -> {
                assertTrue(indice.buscar(null, TipoHemocomponente.CONCENTRADO_HEMACIAS).isEmpty());
                assertTrue(indice.buscar(GrupoSanguineo.O_NEG, null).isEmpty());
                assertTrue(indice.buscar(null, null).isEmpty());
            });
        }

        @Test
        @DisplayName("A busca nunca devolve null, nem com indice vazio nem com chave inexistente")
        void buscaNuncaDevolveNull() {
            IndiceEstoque indice = new IndiceEstoque();
            assertNotNull(indice.buscar(GrupoSanguineo.AB_NEG, TipoHemocomponente.CRIOPRECIPITADO));

            indice.adicionar(criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));
            assertNotNull(indice.buscar(GrupoSanguineo.AB_NEG, TipoHemocomponente.CRIOPRECIPITADO));
            assertTrue(indice.buscar(GrupoSanguineo.AB_NEG,
                    TipoHemocomponente.CRIOPRECIPITADO).isEmpty());
        }

        /**
         * O indice e compartilhado entre requisicoes e pode mudar enquanto
         * alguem percorre um resultado anterior. Com uma visao da lista
         * interna isso daria ConcurrentModificationException no meio da
         * alocacao; com copia, o resultado ja devolvido fica estavel.
         */
        @Test
        @DisplayName("O resultado e copia: alterar o indice depois nao muda a lista devolvida")
        void resultadoEhCopiaImutavel() {
            IndiceEstoque indice = new IndiceEstoque();
            indice.adicionar(criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));

            List<Bolsa> antes = indice.buscar(GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS);
            assertEquals(1, antes.size());

            indice.adicionar(criarBolsa("B002", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));

            assertEquals(1, antes.size(), "a lista ja devolvida nao pode acompanhar o indice");
            assertEquals(2, indice.buscar(GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS).size());

            assertThrows(UnsupportedOperationException.class,
                    () -> antes.add(criarBolsa("B003", GrupoSanguineo.O_NEG,
                            TipoHemocomponente.CONCENTRADO_HEMACIAS)));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Insercao e remocao")
    class InsercaoERemocao {

        @Test
        @DisplayName("Adicionar a mesma bolsa duas vezes nao duplica: substitui a instancia")
        void adicionarDuasVezesNaoDuplica() {
            IndiceEstoque indice = new IndiceEstoque();
            Bolsa primeira = criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS);
            Bolsa mesmaBolsaOutraInstancia = criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS);

            indice.adicionar(primeira);
            indice.adicionar(mesmaBolsaOutraInstancia);

            List<Bolsa> encontradas = indice.buscar(GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS);
            assertEquals(1, encontradas.size(),
                    "Bolsa.equals compara por codigo de rastreio: e a mesma bolsa");
            assertEquals(1, indice.tamanho());
            assertSame(mesmaBolsaOutraInstancia, encontradas.get(0),
                    "a instancia nova substitui a antiga");
        }

        @Test
        @DisplayName("Remover tira a bolsa e devolve se encontrou; bolsa nula devolve false")
        void remocao() {
            IndiceEstoque indice = new IndiceEstoque();
            Bolsa bolsa = criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS);
            indice.adicionar(bolsa);

            assertTrue(indice.remover(bolsa));
            assertTrue(indice.buscar(GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS).isEmpty());
            assertEquals(0, indice.tamanho());

            assertFalse(indice.remover(bolsa), "ja foi removida");
            assertFalse(indice.remover(null));
        }

        @Test
        @DisplayName("Remover por outra instancia de mesmo codigo tambem funciona")
        void removePorCodigoDeRastreio() {
            IndiceEstoque indice = new IndiceEstoque();
            indice.adicionar(criarBolsa("B001", GrupoSanguineo.A_POS,
                    TipoHemocomponente.CONCENTRADO_PLAQUETAS));

            Bolsa outraInstancia = criarBolsa("B001", GrupoSanguineo.A_POS,
                    TipoHemocomponente.CONCENTRADO_PLAQUETAS);

            assertTrue(indice.remover(outraInstancia));
            assertEquals(0, indice.tamanho());
        }

        @Test
        @DisplayName("Adicionar bolsa nula lanca IllegalArgumentException")
        void bolsaNulaLanca() {
            IndiceEstoque indice = new IndiceEstoque();
            assertThrows(IllegalArgumentException.class, () -> indice.adicionar(null));
            assertEquals(0, indice.tamanho());
        }

        @Test
        @DisplayName("limpar esvazia o indice e tamanho soma todos os baldes")
        void limparETamanho() {
            IndiceEstoque indice = new IndiceEstoque();
            indice.adicionar(criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));
            indice.adicionar(criarBolsa("B002", GrupoSanguineo.A_POS,
                    TipoHemocomponente.CONCENTRADO_PLAQUETAS));
            indice.adicionar(criarBolsa("B003", GrupoSanguineo.A_POS,
                    TipoHemocomponente.CONCENTRADO_PLAQUETAS));

            assertEquals(3, indice.tamanho(), "soma os dois baldes");

            indice.limpar();
            assertEquals(0, indice.tamanho());
            assertTrue(indice.buscar(GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS).isEmpty());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Chave composta: equals e hashCode")
    class ChaveComposta {

        /**
         * Prova de comportamento, nao de implementacao: a bolsa e indexada
         * com uma instancia de chave construida dentro do adicionar, e
         * encontrada por uma busca que constroi outra instancia. So funciona
         * se equals e hashCode estiverem ambos implementados - com apenas
         * equals, as duas chaves cairiam em baldes diferentes e o get
         * devolveria vazio, sem erro nenhum.
         */
        @Test
        @DisplayName("Chave construida na busca encontra a bolsa indexada por outra instancia")
        void chavesIguaisCaemNoMesmoBalde() {
            IndiceEstoque indice = new IndiceEstoque();
            indice.adicionar(criarBolsa("B001", GrupoSanguineo.B_NEG,
                    TipoHemocomponente.PLASMA_FRESCO_CONGELADO));

            List<Bolsa> encontradas = indice.buscar(GrupoSanguineo.B_NEG,
                    TipoHemocomponente.PLASMA_FRESCO_CONGELADO);

            assertEquals(1, encontradas.size());
            assertEquals("B001", encontradas.get(0).getCodigoRastreio());
        }

        @Test
        @DisplayName("A chave combina os dois campos: mudar grupo ou tipo muda o balde")
        void chaveCombinaGrupoETipo() {
            IndiceEstoque indice = new IndiceEstoque();
            indice.adicionar(criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));

            // Mesmo grupo, tipo diferente.
            assertTrue(indice.buscar(GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_PLAQUETAS).isEmpty());
            // Mesmo tipo, grupo diferente.
            assertTrue(indice.buscar(GrupoSanguineo.O_POS,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS).isEmpty());
            // Os dois corretos.
            assertEquals(1, indice.buscar(GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS).size());
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("Colisao")
    class Colisao {

        /**
         * O numero de combinacoes possiveis e fechado e pequeno: e o que
         * permite dimensionar a tabela e garantir hash perfeito.
         */
        @Test
        @DisplayName("O espaco de chaves e 8 grupos por 4 tipos, ou seja, 32 combinacoes")
        void espacoDeChavesEhFechadoEPequeno() {
            assertEquals(8, GrupoSanguineo.values().length);
            assertEquals(4, TipoHemocomponente.values().length);
            assertEquals(32, GrupoSanguineo.values().length * TipoHemocomponente.values().length);
        }

        @Test
        @DisplayName("As 32 combinacoes produzem hashCodes distintos, todos entre 0 e 31")
        void hashPerfeitoSobreAsTrintaEDuasCombinacoes() {
            Set<Integer> vistos = new HashSet<>();

            for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
                for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
                    int hash = IndiceEstoque.hashDaChave(grupo, tipo);

                    assertTrue(hash >= 0 && hash <= 31,
                            "hash de " + grupo + "/" + tipo + " fora de 0..31: " + hash);
                    assertTrue(vistos.add(hash),
                            "hash repetido em " + grupo + "/" + tipo + ": " + hash);
                }
            }

            assertEquals(32, vistos.size(), "nenhuma das 32 combinacoes pode repetir hash");
        }

        /**
         * Reproduz a conta que o HashMap faz: espalha com h ^ (h >>> 16) e
         * pega o indice com (n - 1) & hash. Com hash de 0 a 31 e n = 64, o
         * deslocamento de 16 bits e zero e o indice e o proprio hash.
         */
        @Test
        @DisplayName("Em tabela de 64 baldes, as 32 combinacoes caem em baldes distintos")
        void nenhumBaldeCompartilhadoComCapacidadeSessentaEQuatro() {
            final int capacidade = 64;
            Set<Integer> baldes = new HashSet<>();

            for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
                for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
                    int hash = IndiceEstoque.hashDaChave(grupo, tipo);
                    int espalhado = hash ^ (hash >>> 16);
                    int balde = espalhado & (capacidade - 1);

                    assertEquals(hash, espalhado,
                            "com hash abaixo de 2^16, o espalhamento nao muda nada");
                    assertTrue(baldes.add(balde),
                            "balde compartilhado por " + grupo + "/" + tipo + ": " + balde);
                }
            }

            assertEquals(32, baldes.size(), "zero colisao por construcao");
        }

        /**
         * O hash anterior vinha de Objects.hash sobre o hashCode de
         * identidade dos enums, que nao e especificado entre execucoes. Este
         * teste fixa o valor pela formula, que so depende da posicao
         * declarada do enum.
         */
        @Test
        @DisplayName("O hash e estavel: depende so do ordinal, nao da maquina virtual")
        void hashEhEstavelEDeterministico() {
            int tipos = TipoHemocomponente.values().length;

            for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
                for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
                    int esperado = grupo.ordinal() * tipos + tipo.ordinal();

                    assertEquals(esperado, IndiceEstoque.hashDaChave(grupo, tipo));
                    assertEquals(IndiceEstoque.hashDaChave(grupo, tipo),
                            IndiceEstoque.hashDaChave(grupo, tipo),
                            "a mesma combinacao sempre da o mesmo valor");
                }
            }

            // Ancoras explicitas nas pontas do intervalo.
            assertEquals(0, IndiceEstoque.hashDaChave(
                    GrupoSanguineo.values()[0], TipoHemocomponente.values()[0]));
            assertEquals(31, IndiceEstoque.hashDaChave(
                    GrupoSanguineo.values()[7], TipoHemocomponente.values()[3]));
        }

        @Test
        @DisplayName("Contrato equals e hashCode: combinacoes iguais tem hash igual, diferentes nao colidem")
        void contratoEqualsHashCode() {
            for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
                for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
                    // Mesma combinacao, calculada duas vezes: hash igual.
                    assertEquals(IndiceEstoque.hashDaChave(grupo, tipo),
                            IndiceEstoque.hashDaChave(grupo, tipo));

                    // Combinacao diferente: hash diferente, sem excecao.
                    for (GrupoSanguineo outroGrupo : GrupoSanguineo.values()) {
                        for (TipoHemocomponente outroTipo : TipoHemocomponente.values()) {
                            boolean mesmaChave = grupo == outroGrupo && tipo == outroTipo;
                            boolean mesmoHash = IndiceEstoque.hashDaChave(grupo, tipo)
                                    == IndiceEstoque.hashDaChave(outroGrupo, outroTipo);

                            assertEquals(mesmaChave, mesmoHash,
                                    grupo + "/" + tipo + " contra " + outroGrupo + "/" + outroTipo);
                        }
                    }
                }
            }
        }

        /**
         * Prova de comportamento: com uma bolsa em cada uma das 32
         * combinacoes, cada busca devolve exatamente a sua e nenhuma de
         * outra. Se duas chaves compartilhassem balde e o equals estivesse
         * errado, uma busca traria a bolsa da chave vizinha.
         */
        @Test
        @DisplayName("Com as 32 combinacoes indexadas, cada busca devolve so a sua bolsa")
        void cadaCombinacaoDevolveApenasASuaBolsa() {
            IndiceEstoque indice = new IndiceEstoque();

            for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
                for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
                    indice.adicionar(criarBolsa(codigoDe(grupo, tipo), grupo, tipo));
                }
            }

            assertEquals(32, indice.tamanho());

            for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
                for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
                    List<Bolsa> encontradas = indice.buscar(grupo, tipo);

                    assertEquals(1, encontradas.size(),
                            "a busca de " + grupo + "/" + tipo + " trouxe mais de uma bolsa");
                    assertEquals(codigoDe(grupo, tipo),
                            encontradas.get(0).getCodigoRastreio());
                }
            }
        }

        /**
         * Varias bolsas na mesma chave nao sao colisao de hash: sao o
         * agrupamento que da sentido ao indice.
         */
        @Test
        @DisplayName("Varias bolsas da mesma combinacao ficam juntas: agrupamento, nao colisao")
        void bolsasDaMesmaChaveFicamJuntas() {
            IndiceEstoque indice = new IndiceEstoque();

            indice.adicionar(criarBolsa("B001", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));
            indice.adicionar(criarBolsa("B002", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));
            indice.adicionar(criarBolsa("B003", GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS));

            List<Bolsa> encontradas = indice.buscar(GrupoSanguineo.O_NEG,
                    TipoHemocomponente.CONCENTRADO_HEMACIAS);

            assertEquals(3, encontradas.size(), "as tres ficam na lista da mesma chave");
            assertEquals(3, indice.tamanho());
            assertEquals(List.of("B001", "B002", "B003"),
                    encontradas.stream().map(Bolsa::getCodigoRastreio).toList());
        }

        private String codigoDe(GrupoSanguineo grupo, TipoHemocomponente tipo) {
            return "B" + grupo.ordinal() + tipo.ordinal();
        }
    }

    private Bolsa criarBolsa(String codigo,
                             GrupoSanguineo grupo,
                             TipoHemocomponente tipo) {
        Hemocentro hemocentro = new Hemocentro(
                "H1", "Hemocentro Teste", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100"
        );

        return new Bolsa(
                codigo,
                tipo,
                grupo,
                450,
                LocalDate.of(2026, 8, 1),
                hemocentro
        );
    }
}
