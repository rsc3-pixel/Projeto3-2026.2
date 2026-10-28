package com.rotavital.repositorio;

import com.rotavital.dominio.LeituraTemperatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeituraTemperaturaRepository extends JpaRepository<LeituraTemperatura, String> {

    /**
     * Historico da remessa, do mais recente para o mais antigo.
     *
     * <p>A ordem decrescente e deliberada: quem consulta quer saber como esta
     * agora, e so depois como chegou ate aqui.</p>
     */
    List<LeituraTemperatura> findByRotaIdOrderByDataHoraDesc(String rotaId);
}
