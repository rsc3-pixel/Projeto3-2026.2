package com.rotavital.api;

import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roteiro de entregas por vizinho mais proximo pela API (PI3-227).
 *
 * <p>Ate aqui o {@code VizinhoMaisProximo} so era exercitado pelos testes da
 * estrutura: nenhum controller nem servico o chamava. Este teste cobre o
 * caminho pela aplicacao, com a malha montada a partir das unidades do
 * banco, como o calculo de rota da HU-05.</p>
 *
 * <p>Os tres hospitais ficam na mesma direcao, a distancias crescentes do
 * hemocentro. Assim a escolha gulosa e previsivel: o mais perto primeiro, e
 * de cada um o seguinte mais perto.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("PI3-227 · Roteiro de entregas por vizinho mais proximo")
class RoteiroVizinhoMaisProximoTest {

    /** HEMOPE, Gracas: o ponto de partida. */
    private static final EnderecoDto HEMOPE = endereco(-8.0528651, -34.8979716);

    /** Tres pontos a leste do HEMOPE, a cerca de 1, 3 e 5 km em linha reta. */
    private static final EnderecoDto A_1KM = endereco(-8.0528651, -34.8889);
    private static final EnderecoDto A_3KM = endereco(-8.0528651, -34.8707);
    private static final EnderecoDto A_5KM = endereco(-8.0528651, -34.8525);

    /** Sao Paulo: a mais de 2.000 km de qualquer unidade, entao sem trecho na malha. */
    private static final EnderecoDto FORA_DA_MALHA = endereco(-23.5614, -46.6559);

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    private MockMvc mvc;
    private String hemocentroId;
    private String perto;
    private String meio;
    private String longe;

    @BeforeEach
    void prepararRede() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        hemocentroId = criarHemocentro("Hemocentro do Roteiro", "51515151000151", HEMOPE);
        perto = criarHospital("Hospital a 1 km", "9300001", A_1KM);
        meio = criarHospital("Hospital a 3 km", "9300002", A_3KM);
        longe = criarHospital("Hospital a 5 km", "9300003", A_5KM);
    }

    @Test
    @DisplayName("Visita os destinos do mais proximo ao mais distante, em qualquer ordem de entrada")
    void ordemDoVizinhoMaisProximo() throws Exception {
        mvc.perform(get("/api/v1/rotas/roteiro")
                        .param("origemId", hemocentroId)
                        .param("destinos", longe, perto, meio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origemId").value(hemocentroId))
                .andExpect(jsonPath("$.alcancouTodos").value(true))
                .andExpect(jsonPath("$.ordemDeVisita.length()").value(3))
                .andExpect(jsonPath("$.ordemDeVisita[0].id").value(perto))
                .andExpect(jsonPath("$.ordemDeVisita[1].id").value(meio))
                .andExpect(jsonPath("$.ordemDeVisita[2].id").value(longe))
                .andExpect(jsonPath("$.ordemDeVisita[0].nome").value("Hospital a 1 km"))
                .andExpect(jsonPath("$.tempoTotalMinutos").isNumber())
                .andExpect(jsonPath("$.inalcancaveis").isEmpty())
                .andExpect(jsonPath("$.mensagem").doesNotExist());
    }

    @Test
    @DisplayName("O caminho completo comeca na origem e termina no ultimo destino visitado")
    void caminhoCompleto() throws Exception {
        mvc.perform(get("/api/v1/rotas/roteiro")
                        .param("origemId", hemocentroId)
                        .param("destinos", perto, longe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caminho[0].id").value(hemocentroId))
                .andExpect(jsonPath("$.caminho[-1].id").value(longe));
    }

    @Test
    @DisplayName("Destino sem trecho na malha vai para inalcancaveis, e os outros sao visitados")
    void destinoInalcancavel() throws Exception {
        String isolado = criarHospital("Hospital fora da malha", "9300004", FORA_DA_MALHA);

        mvc.perform(get("/api/v1/rotas/roteiro")
                        .param("origemId", hemocentroId)
                        .param("destinos", perto, isolado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alcancouTodos").value(false))
                .andExpect(jsonPath("$.ordemDeVisita.length()").value(1))
                .andExpect(jsonPath("$.ordemDeVisita[0].id").value(perto))
                .andExpect(jsonPath("$.inalcancaveis[0].id").value(isolado))
                .andExpect(jsonPath("$.mensagem").isString());
    }

    @Test
    @DisplayName("A origem repetida entre os destinos e ignorada: ja se esta nela")
    void origemEntreOsDestinos() throws Exception {
        mvc.perform(get("/api/v1/rotas/roteiro")
                        .param("origemId", hemocentroId)
                        .param("destinos", hemocentroId, perto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordemDeVisita.length()").value(1))
                .andExpect(jsonPath("$.ordemDeVisita[0].id").value(perto));
    }

    @Test
    @DisplayName("Origem ou destino que nao existem devolvem 404")
    void unidadeInexistente() throws Exception {
        mvc.perform(get("/api/v1/rotas/roteiro")
                        .param("origemId", "NAO-EXISTE")
                        .param("destinos", perto))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/rotas/roteiro")
                        .param("origemId", hemocentroId)
                        .param("destinos", perto, "NAO-EXISTE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sem destinos a requisicao e invalida")
    void semDestinos() throws Exception {
        mvc.perform(get("/api/v1/rotas/roteiro")
                        .param("origemId", hemocentroId))
                .andExpect(status().isBadRequest());
    }

    private static EnderecoDto endereco(double latitude, double longitude) {
        return new EnderecoDto("Rua de Teste", "1", "Centro", "Recife", "PE", "50000-000",
                latitude, longitude);
    }

    private String criarHemocentro(String nome, String cnpj, EnderecoDto endereco) throws Exception {
        return idDe(mvc.perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HemocentroRequest(
                                nome, "(81) 3000-0000", cnpj, endereco))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String criarHospital(String nome, String cnes, EnderecoDto endereco) throws Exception {
        return idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                nome, "(81) 3000-0001", cnes, endereco))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String idDe(String resposta) {
        return json.readTree(resposta).get("id").asString();
    }
}
