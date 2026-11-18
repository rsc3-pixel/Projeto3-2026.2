package com.rotavital.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de erro proposital, para a demonstracao de observabilidade.
 *
 * <p>Devolve 500 e registra um log de ERROR dentro da requisicao. Serve para
 * mostrar, no Grafana, o painel de taxa de 5xx subindo e a navegacao do log
 * (Loki) ate o trace (Tempo) pelo {@code trace_id}. O gerador de carga do
 * compose chama este endpoint de tempos em tempos.</p>
 *
 * <p><strong>So existe no perfil {@code docker}</strong>: nao sobe em
 * producao, no perfil padrao nem nos testes. Fica fora da documentacao da API
 * ({@link Hidden}).</p>
 */
@Hidden
@Profile("docker")
@RestController
@RequestMapping("/api/v1/demonstracao")
public class DemonstracaoObservabilidadeController {

    private static final Logger log = LoggerFactory.getLogger(DemonstracaoObservabilidadeController.class);

    @GetMapping("/erro")
    public void erro() {
        log.error("Erro simulado para a demonstracao de observabilidade (log -> trace no Grafana)");
        throw new IllegalStateException("Erro simulado (demonstracao de observabilidade)");
    }
}
