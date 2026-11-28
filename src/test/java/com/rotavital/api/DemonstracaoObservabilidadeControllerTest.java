package com.rotavital.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O endpoint de erro proposital so pode existir no perfil {@code docker}, o da
 * demonstracao de observabilidade. Em producao ele seria um 500 de graca.
 */
@SpringBootTest
@ActiveProfiles("test")
class DemonstracaoObservabilidadeControllerTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    @DisplayName("Fora do perfil docker o controller de erro proposital nao e carregado")
    void naoCarregaForaDoPerfilDocker() {
        assertThat(contexto.getBeansOfType(DemonstracaoObservabilidadeController.class)).isEmpty();
    }

    @Test
    @DisplayName("O controller esta restrito ao perfil docker, e so a ele")
    void restritoAoPerfilDocker() {
        Profile perfil = DemonstracaoObservabilidadeController.class.getAnnotation(Profile.class);

        assertThat(perfil).isNotNull();
        assertThat(perfil.value()).containsExactly("docker");
    }

    @Test
    @DisplayName("Quando chamado, falha com erro de servidor e nao devolve dado")
    void falhaDeProposito() {
        assertThatThrownBy(() -> new DemonstracaoObservabilidadeController().erro())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulado");
    }
}
