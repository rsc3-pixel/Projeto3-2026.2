package com.rotavital.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IndicadorControllerTest {

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
    @DisplayName("GET /api/v1/indicadores/estoque-por-tipo retorna status 200")
    void deveConsultarEstoquePorTipo() throws Exception {
        mvc().perform(get("/api/v1/indicadores/estoque-por-tipo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/estoque-por-componente retorna status 200")
    void deveConsultarEstoquePorComponente() throws Exception {
        mvc().perform(get("/api/v1/indicadores/estoque-por-componente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/descarte retorna status 200 com o descarte evitavel pela FEFO")
    void deveConsultarTaxaDescarte() throws Exception {
        mvc().perform(get("/api/v1/indicadores/descarte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].taxaDescarte").isNumber())
                .andExpect(jsonPath("$[0].descarteEvitavelFefo").isNumber())
                .andExpect(jsonPath("$[0].percentualEvitavelFefo").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/descarte/por-periodo aceita SEMANA e MES")
    void deveConsultarTaxaDescartePorPeriodo() throws Exception {
        mvc().perform(get("/api/v1/indicadores/descarte/por-periodo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc().perform(get("/api/v1/indicadores/descarte/por-periodo").param("granularidade", "MES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/desabastecimento retorna os oito tipos com probabilidade e risco")
    void deveConsultarProbabilidadeDesabastecimento() throws Exception {
        mvc().perform(get("/api/v1/indicadores/desabastecimento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distribuicao").isString())
                .andExpect(jsonPath("$.diasObservados").isNumber())
                .andExpect(jsonPath("$.porTipo.length()").value(8))
                .andExpect(jsonPath("$.porTipo[0].probabilidadePercentual").isNumber())
                .andExpect(jsonPath("$.porTipo[0].probabilidadePoissonPercentual").isNumber())
                .andExpect(jsonPath("$.porTipo[0].nivelRisco").isString());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/validade retorna status 200 com metricas")
    void deveConsultarDispersaoValidade() throws Exception {
        mvc().perform(get("/api/v1/indicadores/validade"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.n").isNumber())
                .andExpect(jsonPath("$.mediaDias").isNumber())
                .andExpect(jsonPath("$.medianaDias").isNumber())
                .andExpect(jsonPath("$.desvioPadraoDias").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/criticas retorna status 200")
    void deveConsultarBolsasCriticas() throws Exception {
        mvc().perform(get("/api/v1/indicadores/criticas").param("limiteDias", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limiteDias").value(7))
                .andExpect(jsonPath("$.quantidade").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/indicadores/cobertura retorna status 200")
    void deveConsultarCoberturaDemanda() throws Exception {
        mvc().perform(get("/api/v1/indicadores/cobertura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
