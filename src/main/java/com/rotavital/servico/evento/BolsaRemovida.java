package com.rotavital.servico.evento;

import com.rotavital.dominio.Bolsa;

/**
 * Bolsa excluida do sistema.
 *
 * <p>Separado do {@link BolsaAlterada} porque a decisao do ouvinte e
 * diferente: aqui a bolsa sai do indice sem olhar o status.</p>
 */
public record BolsaRemovida(Bolsa bolsa) {
}
