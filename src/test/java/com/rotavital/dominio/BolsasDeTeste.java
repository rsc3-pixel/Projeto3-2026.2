package com.rotavital.dominio;

import com.rotavital.dominio.enums.StatusBolsa;

/**
 * Leva uma bolsa de teste ate o status pedido pelo caminho que o dominio
 * permite. Sem setStatus publico (PI3-247), os testes tambem nao pulam etapas.
 */
public final class BolsasDeTeste {

    private BolsasDeTeste() {
    }

    public static Bolsa levarAte(Bolsa bolsa, StatusBolsa destino) {
        switch (destino) {
            case DISPONIVEL -> { }
            case RESERVADA -> bolsa.reservar(bolsa.getDataColeta());
            case EM_TRANSITO -> {
                bolsa.reservar(bolsa.getDataColeta());
                bolsa.despachar();
            }
            case ENTREGUE -> {
                bolsa.reservar(bolsa.getDataColeta());
                bolsa.despachar();
                bolsa.entregar();
            }
            case DESCARTADA -> bolsa.descartar();
        }
        return bolsa;
    }
}
