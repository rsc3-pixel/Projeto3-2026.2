package com.rotavital.observabilidade;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao comum a todas as metricas da aplicacao.
 *
 * <p>Toda metrica sai com a tag {@code application=rota-vital} (o valor de
 * {@code spring.application.name}). E essa tag que separa as aplicacoes no
 * Grafana quando mais de uma e coletada pelo mesmo Prometheus.</p>
 *
 * <p>Feito com um {@link MeterFilter}, que e API do Micrometer, e nao com
 * propriedade do Spring Boot: o Spring Boot aplica sozinho todo bean
 * {@code MeterFilter} aos registros de metricas, antes de qualquer metrica ser
 * criada, e esse mecanismo nao mudou entre as versoes 3 e 4.</p>
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoMetricas {

    @Bean
    MeterFilter tagAplicacao(@Value("${spring.application.name}") String aplicacao) {
        return MeterFilter.commonTags(Tags.of("application", aplicacao));
    }
}
