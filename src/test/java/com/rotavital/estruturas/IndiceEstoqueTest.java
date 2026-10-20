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
import java.util.List;

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
