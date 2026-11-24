package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Hemocentro;

/**
 * Acesso aos hemocentros. As buscas por endereco vem de
 * {@link UnidadeRepository}; aqui fica so o que e do hemocentro.
 *
 * <p>Nao ha implementacao escrita: o Spring Data gera a classe em tempo de
 * execucao a partir do nome dos metodos.</p>
 */
public interface HemocentroRepository extends UnidadeRepository<Hemocentro> {

    boolean existsByCnpj(String cnpj);
}
