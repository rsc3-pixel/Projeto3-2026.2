package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HospitalRepository extends JpaRepository<Hospital, String> {

    List<Hospital> findByEnderecoCidadeIgnoreCase(String cidade);

    List<Hospital> findByEnderecoEstadoIgnoreCase(String estado);

    List<Hospital> findByEnderecoCidadeIgnoreCaseAndEnderecoEstadoIgnoreCase(
            String cidade, String estado);

    boolean existsByCnes(String cnes);
}
