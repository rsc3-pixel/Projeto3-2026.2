package com.rotavital.estruturas;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class FilaPrioridadeFefoTest {

    @Test
    void filaVaziaNaoTemProximaBolsa() {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();

        assertTrue(fila.estaVazia());
        assertNull(fila.consultarProxima());
        assertNull(fila.retirarProxima());
    }

    @Test
    void elementoUnicoEhRetornado() {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
        Bolsa bolsa = criarBolsa("B001", LocalDate.of(2026, 8, 1));

        fila.adicionar(bolsa);

        assertSame(bolsa, fila.retirarProxima());
        assertTrue(fila.estaVazia());
    }

    @Test
    void devolveSempreABolsaQueVencePrimeiro() {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
        Bolsa venceDepois = criarBolsa("B002", LocalDate.of(2026, 8, 10));
        Bolsa vencePrimeiro = criarBolsa("B001", LocalDate.of(2026, 8, 1));

        fila.adicionar(venceDepois);
        fila.adicionar(vencePrimeiro);

        assertSame(vencePrimeiro, fila.retirarProxima());
        assertSame(venceDepois, fila.retirarProxima());
    }

    @Test
    void empateDeValidadeEhResolvidoDeFormaDeterministicaPeloCodigo() {
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
        LocalDate mesmaColeta = LocalDate.of(2026, 8, 1);
        Bolsa b002 = criarBolsa("B002", mesmaColeta);
        Bolsa b001 = criarBolsa("B001", mesmaColeta);

        fila.adicionar(b002);
        fila.adicionar(b001);

        assertEquals("B001", fila.retirarProxima().getCodigoRastreio());
        assertEquals("B002", fila.retirarProxima().getCodigoRastreio());
    }

    private Bolsa criarBolsa(String codigo, LocalDate dataColeta) {
        Hemocentro hemocentro = new Hemocentro(
                "H1", "Hemocentro Teste", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100"
        );

        return new Bolsa(
                codigo,
                TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_POS,
                dataColeta,
                hemocentro
        );
    }
}
