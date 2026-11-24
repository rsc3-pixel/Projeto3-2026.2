package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Hospital;

/**
 * Acesso aos hospitais. As buscas por endereco vem de
 * {@link UnidadeRepository}; aqui fica so o que e do hospital.
 */
public interface HospitalRepository extends UnidadeRepository<Hospital> {

    boolean existsByCnes(String cnes);
}
