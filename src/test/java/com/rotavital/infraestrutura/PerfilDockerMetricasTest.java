package com.rotavital.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O perfil {@code docker} expoe {@code health}, {@code info}, {@code metrics} e
 * {@code prometheus}, numa porta de gestao separada da API, que o compose nao
 * publica na internet.
 *
 * <p>Abrir o Prometheus nao pode virar pretexto para expor tudo: o
 * {@code /actuator/env} e o {@code /actuator/configprops} mostram as variaveis
 * de ambiente, onde esta a senha do PostgreSQL. A regra equivalente para
 * producao (so health) fica em {@link PerfilProducaoTest}.</p>
 */
class PerfilDockerMetricasTest {

    private static Properties carregar(String arquivo) throws IOException {
        Properties propriedades = new Properties();
        try (InputStream entrada = new ClassPathResource(arquivo).getInputStream()) {
            propriedades.load(entrada);
        }
        return propriedades;
    }

    private List<String> endpointsExpostos(String arquivo) throws IOException {
        String valor = carregar(arquivo).getProperty("management.endpoints.web.exposure.include", "");
        return Arrays.stream(valor.split(",")).map(String::trim).toList();
    }

    @Test
    @DisplayName("Perfil docker expoe health, info, metrics e prometheus, e nada alem disso")
    void dockerExpoeSoOsEndpointsDeObservabilidade() throws IOException {
        assertThat(endpointsExpostos("application-docker.properties"))
                .containsExactlyInAnyOrder("health", "info", "metrics", "prometheus");
    }

    @Test
    @DisplayName("No perfil docker o Actuator fica numa porta propria, diferente da API")
    void dockerSeparaAPortaDoActuator() throws IOException {
        Properties docker = carregar("application-docker.properties");

        assertThat(docker.getProperty("management.server.port")).isEqualTo("8081");
        assertThat(docker.getProperty("management.server.port"))
                .isNotEqualTo(docker.getProperty("server.port"));
    }

    @Test
    @DisplayName("Nenhum perfil expoe todos os endpoints com '*'")
    void nenhumPerfilUsaCuringa() throws IOException {
        for (String arquivo : List.of("application.properties",
                "application-docker.properties", "application-prod.properties")) {
            assertThat(endpointsExpostos(arquivo)).as(arquivo).doesNotContain("*");
        }
    }
}
