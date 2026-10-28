package com.rotavital.servico.evento;

import com.rotavital.dominio.Bolsa;

/**
 * Bolsa criada ou com status alterado.
 *
 * <p>Publicado pelos servicos que mexem no estoque e consumido pelo
 * {@code IndiceEstoqueServico} <b>depois do commit</b>. O evento carrega a
 * bolsa inteira, e nao so o id, para que o ouvinte decida pelo status atual
 * se ela entra ou sai do indice sem precisar voltar ao banco.</p>
 */
public record BolsaAlterada(Bolsa bolsa) {
}
