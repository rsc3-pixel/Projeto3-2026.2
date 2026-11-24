package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Rota;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RotaRepository extends JpaRepository<Rota, String> {

    long countByOrigemId(String hemocentroId);

    long countByDestinoId(String hospitalId);
}
