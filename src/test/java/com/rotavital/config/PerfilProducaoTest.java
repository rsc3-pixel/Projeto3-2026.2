package com.rotavital.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava as decisoes de seguranca do perfil de producao.
 *
 * <p>Le o arquivo direto do classpath em vez de subir a aplicacao com
 * {@code @ActiveProfiles("prod")}. O motivo: subir de verdade em modo prod
 * dispararia a carga de dados e o {@code ddl-auto=create}, deixando o teste
 * lento e acoplado ao banco. O que precisa ser verificado aqui e o conteudo do
 * arquivo, e ele basta.</p>
 *
 * <p>Se alguem religar o console do H2 para depurar e esquecer de desligar,
 * este teste quebra o build antes do deploy. E a diferenca entre uma regra
 * escrita num documento e uma regra que o pipeline cobra.</p>
 */
class PerfilProducaoTest {

    private Properties producao() throws IOException {
        Properties propriedades = new Properties();
        try (InputStream entrada = new ClassPathResource("application-prod.properties").getInputStream()) {
            propriedades.load(entrada);
        }
        return propriedades;
    }

    @Test
    @DisplayName("Console do H2 esta desligado em producao")
    void consoleDoH2Desligado() throws IOException {
        assertThat(producao().getProperty("spring.h2.console.enabled"))
                .as("o console do H2 e um terminal SQL sem senha; nao pode ficar exposto")
                .isEqualTo("false");
    }

    @Test
    @DisplayName("Erro em producao nao devolve stack trace nem mensagem interna")
    void erroNaoVazaDetalhe() throws IOException {
        Properties prod = producao();

        assertThat(prod.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(prod.getProperty("server.error.include-message")).isEqualTo("never");
    }

    @Test
    @DisplayName("Actuator expoe apenas o health")
    void actuatorRestrito() throws IOException {
        Properties prod = producao();

        assertThat(prod.getProperty("management.endpoints.web.exposure.include"))
                .as("/actuator/env lista variaveis de ambiente; /actuator/beans mapeia a aplicacao")
                .isEqualTo("health");

        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    @DisplayName("SQL nao vai para o log em producao")
    void semSqlNoLog() throws IOException {
        assertThat(producao().getProperty("spring.jpa.show-sql")).isEqualTo("false");
    }

    @Test
    @DisplayName("A aplicacao escuta apenas em localhost, atras do proxy")
    void escutaSomenteLocalmente() throws IOException {
        assertThat(producao().getProperty("server.address"))
                .as("a VM e compartilhada; so o Nginx deve alcancar a aplicacao")
                .isEqualTo("127.0.0.1");
    }

    /**
     * A porta aparece em tres lugares que precisam concordar: este arquivo, o
     * proxy do Nginx e o servico systemd. Divergencia entre eles produz um
     * deploy que publica sem erro e uma URL que nao responde.
     */
    @Test
    @DisplayName("A porta de producao bate com a configurada no proxy do Nginx")
    void portaConsistenteComOProxy() throws IOException {
        String porta = producao().getProperty("server.port");

        assertThat(porta)
                .as("porta de producao, diferente da 8080 usada em desenvolvimento")
                .isEqualTo("8081");

        String proxy = java.nio.file.Files.readString(
                java.nio.file.Path.of("infra/nginx-rota-vital.conf"));

        assertThat(proxy)
                .as("o proxy_pass do Nginx precisa apontar para a mesma porta")
                .contains("127.0.0.1:" + porta);
    }

    @Test
    @DisplayName("Nenhuma senha esta escrita no arquivo de producao")
    void semSegredoVersionado() throws IOException {
        Properties prod = producao();

        for (String chave : prod.stringPropertyNames()) {
            boolean pareceSegredo = chave.contains("password")
                    || chave.contains("secret")
                    || chave.contains("token");

            if (pareceSegredo) {
                assertThat(prod.getProperty(chave))
                        .as("segredo deve vir de variavel de ambiente, nunca do arquivo: " + chave)
                        .matches("^\\$\\{.*\\}$|^$");
            }
        }
    }

    /**
     * O timeout do proxy precisa caber o tempo de subida da aplicacao.
     *
     * <p>A JVM leva de 21 a 23 segundos para subir na VM, medido em outubro de
     * 2026. Com o timeout em 30s a margem sumia sob carga, e o Nginx devolvia
     * 504 antes de a aplicacao responder (issue #58). Nao era a aplicacao
     * falhando, era o proxy cortando.
     *
     * <p>Este teste trava a regressao: baixar o valor de novo quebra aqui, e
     * nao em producao numa segunda-feira.
     */
    @Test
    @DisplayName("O timeout do proxy comporta o tempo de subida da aplicacao")
    void timeoutDoProxyComportaOColdStart() throws java.io.IOException {
        String proxy = java.nio.file.Files.readString(
                java.nio.file.Path.of("infra/nginx-rota-vital.conf"));

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("proxy_read_timeout\\s+(\\d+)s")
                .matcher(proxy);

        assertThat(m.find())
                .as("o proxy precisa declarar proxy_read_timeout")
                .isTrue();

        int segundos = Integer.parseInt(m.group(1));
        assertThat(segundos)
                .as("a JVM leva ate 23s para subir; abaixo de 60s a margem some "
                        + "quando a VM esta sob carga e o Nginx devolve 504")
                .isGreaterThanOrEqualTo(60);
    }
}
