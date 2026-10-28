package com.rotavital.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class BenchmarkControllerTest {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mockMvc;

    private MockMvc mvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        }
        return mockMvc;
    }

    @Test
    @DisplayName("GET /api/v1/benchmark/dispersao-validade com threads=1 roda a versao sequencial")
    void deveMedirVersaoSequencial() throws Exception {
        mvc().perform(get("/api/v1/benchmark/dispersao-validade")
                        .param("tamanhoAmostra", "2000")
                        .param("threads", "1")
                        .param("semente", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("SEQUENCIAL"))
                .andExpect(jsonPath("$.tamanhoAmostra").value(2000))
                .andExpect(jsonPath("$.tempoMs").isNumber())
                .andExpect(jsonPath("$.resultado.n").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/benchmark/dispersao-validade com threads=4 roda a versao paralela com o mesmo resultado")
    void deveMedirVersaoParalelaComOMesmoResultado() throws Exception {
        String amostra = "2001";

        var respostaSequencial = mvc().perform(get("/api/v1/benchmark/dispersao-validade")
                        .param("tamanhoAmostra", amostra)
                        .param("threads", "1")
                        .param("semente", "99"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc().perform(get("/api/v1/benchmark/dispersao-validade")
                        .param("tamanhoAmostra", amostra)
                        .param("threads", "4")
                        .param("semente", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("PARALELO"))
                .andExpect(jsonPath("$.threads").value(4));

        org.junit.jupiter.api.Assertions.assertTrue(respostaSequencial.contains("\"resultado\""));
    }

    @Test
    @DisplayName("GET /api/v1/benchmark/dispersao-validade com tamanhoAmostra invalido retorna 409")
    void deveRejeitarTamanhoInvalido() throws Exception {
        mvc().perform(get("/api/v1/benchmark/dispersao-validade")
                        .param("tamanhoAmostra", "0")
                        .param("threads", "1"))
                .andExpect(status().isConflict());
    }
}
