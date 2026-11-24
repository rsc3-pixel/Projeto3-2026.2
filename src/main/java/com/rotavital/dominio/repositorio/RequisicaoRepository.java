package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Requisicao;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RequisicaoRepository extends JpaRepository<Requisicao, String> {

    long countByHospitalId(String hospitalId);

    @Query("SELECT DISTINCT r FROM Requisicao r LEFT JOIN FETCH r.hospital LEFT JOIN FETCH r.itens")
    List<Requisicao> findAllComHospitalEItens();
}
