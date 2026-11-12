package com.rotavital.web;

import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * As telas Thymeleaf do estoque renderizam, e mostram o que o servico
 * calculou (PI3-140, HU-04).
 *
 * <p>Nao e teste de regra: a regra do estoque critico e da fila FEFO tem
 * teste no servico e na API. O que este protege e o template: uma expressao
 * errada em Thymeleaf so explode em tempo de execucao, na frente de quem
 * abre a tela, e sem isto nenhum teste da suite chegaria a renderiza-la.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Telas do estoque (Thymeleaf)")
class EstoqueWebControllerTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    private MockMvc mvc;
    private String hemocentroId;

    @BeforeEach
    void prepararCenario() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

        hemocentroId = json.readTree(mvc.perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HemocentroRequest(
                                "Hemocentro das Telas", "(81) 3000-9900", "60606060000606",
                                new EnderecoDto("Rua das Telas", "1", "Centro", "Recife", "PE",
                                        "50000-000", -8.05, -34.90)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    @Test
    @DisplayName("Estoque critico vazio renderiza com a mensagem do estado vazio e os tres filtros")
    void riscoVazio() throws Exception {
        mvc.perform(get("/painel/" + hemocentroId + "/risco"))
                .andExpect(status().isOk())
                .andExpect(view().name("painel/risco"))
                .andExpect(content().string(containsString("ESTOQUE CRITICO")))
                .andExpect(content().string(containsString("Nenhuma bolsa em risco nos proximos 7 dias")))
                .andExpect(content().string(containsString("dias=15")))
                .andExpect(content().string(containsString("dias=30")))
                .andExpect(content().string(containsString("nenhuma bolsa vencida em estoque")));
    }

    @Test
    @DisplayName("Com bolsa vencendo em 3 dias, a tabela aparece e a mensagem do vazio some")
    void riscoComBolsa() throws Exception {
        TipoHemocomponente tipo = TipoHemocomponente.CONCENTRADO_HEMACIAS;
        mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, tipo, GrupoSanguineo.O_NEG, 450,
                                LocalDate.now().plusDays(3).minusDays(tipo.getValidadeDias())))))
                .andExpect(status().isCreated());

        mvc.perform(get("/painel/" + hemocentroId + "/risco").param("dias", "7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3 dias")))
                .andExpect(content().string(containsString("1 BOLSA(S)")))
                .andExpect(content().string(not(containsString("Nenhuma bolsa em risco"))));
    }

    @Test
    @DisplayName("Janela fora das oferecidas cai na primeira, em vez de abrir um filtro inexistente")
    void janelaDesconhecidaCaiNaPrimeira() throws Exception {
        mvc.perform(get("/painel/" + hemocentroId + "/risco").param("dias", "99"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("VENCEM EM ATE 7 DIAS")));
    }

    @Test
    @DisplayName("Fila de saida e estoque continuam renderizando depois da fila ir para o servico")
    void filaEEstoque() throws Exception {
        mvc.perform(get("/painel/" + hemocentroId + "/fila"))
                .andExpect(status().isOk())
                .andExpect(view().name("painel/fila"))
                .andExpect(content().string(containsString("FILA DE SAIDA")));

        mvc.perform(get("/painel/" + hemocentroId + "/estoque"))
                .andExpect(status().isOk())
                .andExpect(view().name("painel/estoque"));
    }

    @Test
    @DisplayName("Hemocentro inexistente devolve 404")
    void hemocentroInexistente() throws Exception {
        mvc.perform(get("/painel/nao-existe/risco"))
                .andExpect(status().isNotFound());
    }
}
