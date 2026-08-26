package com.rotavital.estruturas;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Indice hash para localizar bolsas pela combinacao de grupo sanguineo
 * e tipo de hemocomponente.
 */
public class IndiceEstoque {

    private final Map<ChaveEstoque, List<Bolsa>> indice = new HashMap<>();

    public void adicionar(Bolsa bolsa) {
        if (bolsa == null) {
            throw new IllegalArgumentException("Bolsa nao pode ser nula");
        }

        ChaveEstoque chave = new ChaveEstoque(
                bolsa.getGrupoSanguineo(),
                bolsa.getTipo()
        );

        indice.computeIfAbsent(chave, ignorada -> new ArrayList<>()).add(bolsa);
    }

    public List<Bolsa> buscar(GrupoSanguineo grupoSanguineo,
                              TipoHemocomponente tipo) {
        ChaveEstoque chave = new ChaveEstoque(grupoSanguineo, tipo);
        List<Bolsa> bolsas = indice.get(chave);

        if (bolsas == null) {
            return Collections.emptyList();
        }

        // Retorna uma visao somente-leitura sem copiar a lista inteira.
        return Collections.unmodifiableList(bolsas);
    }

    public boolean remover(Bolsa bolsa) {
        if (bolsa == null) {
            return false;
        }

        ChaveEstoque chave = new ChaveEstoque(
                bolsa.getGrupoSanguineo(),
                bolsa.getTipo()
        );

        List<Bolsa> bolsas = indice.get(chave);
        if (bolsas == null) {
            return false;
        }

        boolean removida = bolsas.remove(bolsa);
        if (bolsas.isEmpty()) {
            indice.remove(chave);
        }
        return removida;
    }

    private static final class ChaveEstoque {
        private final GrupoSanguineo grupoSanguineo;
        private final TipoHemocomponente tipo;

        private ChaveEstoque(GrupoSanguineo grupoSanguineo,
                             TipoHemocomponente tipo) {
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
