package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Alocacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlocacaoRepository extends JpaRepository<Alocacao, String> {

    boolean existsByBolsaCodigoRastreio(String codigoRastreio);
}
