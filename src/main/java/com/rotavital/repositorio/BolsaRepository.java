package com.rotavital.repositorio;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.StatusBolsa;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BolsaRepository extends JpaRepository<Bolsa, String> {

    List<Bolsa> findByHemocentroOrigemId(String hemocentroId);

    @Query("SELECT b FROM Bolsa b LEFT JOIN FETCH b.hemocentroOrigem")
    List<Bolsa> findAllComHemocentro();

    // Consultas das metricas de estoque (observabilidade/MetricasEstoque).
    // Agregam no banco: devolvem no maximo uma linha por combinacao de tipo e
    // status, em vez de carregar todas as bolsas para contar em memoria.

    /** Linhas {@code [TipoHemocomponente, StatusBolsa, Long]}. */
    @Query("SELECT b.tipo, b.status, COUNT(b) FROM Bolsa b GROUP BY b.tipo, b.status")
    List<Object[]> contarPorTipoEStatus();

    /** Linhas {@code [TipoHemocomponente, Long]}; validade entre as datas, inclusive. */
    @Query("SELECT b.tipo, COUNT(b) FROM Bolsa b "
            + "WHERE b.status = :status AND b.dataValidade BETWEEN :inicio AND :fim "
            + "GROUP BY b.tipo")
    List<Object[]> contarPorTipoComValidadeEntre(@Param("status") StatusBolsa status,
                                                  @Param("inicio") LocalDate inicio,
                                                  @Param("fim") LocalDate fim);

    /** Linhas {@code [TipoHemocomponente, Long]}; validade anterior a data. */
    @Query("SELECT b.tipo, COUNT(b) FROM Bolsa b "
            + "WHERE b.status = :status AND b.dataValidade < :data "
            + "GROUP BY b.tipo")
    List<Object[]> contarPorTipoComValidadeAntesDe(@Param("status") StatusBolsa status,
                                                    @Param("data") LocalDate data);
}
