package com.rotavital.paralelo;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GeradorMassaBolsasSintetica {

    private GeradorMassaBolsasSintetica() {
    }

    public static List<Bolsa> gerar(int quantidade, long semente, LocalDate hoje) {
        Random sorteio = new Random(semente);
        GrupoSanguineo[] grupos = GrupoSanguineo.values();
        TipoHemocomponente[] tipos = TipoHemocomponente.values();

        Hemocentro unidadeSintetica = new Hemocentro(
                "HC-BENCH", "Hemocentro sintetico (benchmark de desempenho)", "00000000000",
                new Endereco("Rua Sintetica", "0", "Bairro Sintetico", "Cidade Sintetica",
                        "PE", "00000-000", 0.0, 0.0),
                "00000000000000");

        List<Bolsa> bolsas = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            TipoHemocomponente tipo = tipos[sorteio.nextInt(tipos.length)];
            GrupoSanguineo grupo = grupos[sorteio.nextInt(grupos.length)];

            int diasAtras = sorteio.nextInt(Math.max(1, tipo.getValidadeDias() * 2));
            int volume = 200 + sorteio.nextInt(201);

            bolsas.add(new Bolsa(
                    "BENCH" + i,
                    tipo,
                    grupo,
                    volume,
                    hoje.minusDays(diasAtras),
                    unidadeSintetica));
        }
        return bolsas;
    }
}
