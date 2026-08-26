package com.rotavital.estruturas;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
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
                LocalDate.of(2026, 8, 1),
                hemocentro
        );
    }
}
