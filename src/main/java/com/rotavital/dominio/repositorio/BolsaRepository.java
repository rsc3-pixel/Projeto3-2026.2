package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Bolsa;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BolsaRepository extends JpaRepository<Bolsa, String> {

    List<Bolsa> findByHemocentroOrigemId(String hemocentroId);

    @Query("SELECT b FROM Bolsa b LEFT JOIN FETCH b.hemocentroOrigem")
    List<Bolsa> findAllComHemocentro();
}
