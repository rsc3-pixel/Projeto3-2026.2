package com.rotavital.estruturas;

import com.rotavital.dominio.Bolsa;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Fila de prioridade FEFO (First Expired, First Out).
 * A bolsa com a data de validade mais proxima sempre fica no topo.
 */
public class FilaPrioridadeFefo {

    private final PriorityQueue<Bolsa> fila = new PriorityQueue<>(
            Comparator.comparing(Bolsa::getDataValidade)
                    .thenComparing(Bolsa::getCodigoRastreio)
    );

    public void adicionar(Bolsa bolsa) {
        if (bolsa == null) {
            throw new IllegalArgumentException("Bolsa nao pode ser nula");
        }
        fila.offer(bolsa);
    }

    public Bolsa consultarProxima() {
        return fila.peek();
    }

    public Bolsa retirarProxima() {
        return fila.poll();
    }

    public boolean estaVazia() {
        return fila.isEmpty();
    }

    public int tamanho() {
        return fila.size();
    }
}
