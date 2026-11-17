package com.rotavital.aceitacao;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AtualizarStatusRotaRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EmbarqueBolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.LeituraTemperaturaRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.api.dto.RotaRequest;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusRota;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O caminho completo requisicao -> bolsa -> rota, conferindo o estado de cada
 * entidade a cada passo (PI3-215).
 *
 * <p>Os testes de integracao existentes cobrem cada trecho isolado: o
 * {@code FluxoRequisicaoTest} para a alocacao, o {@code RotaControllerTest}
 * para o ciclo da rota, o {@code TemperaturaRemessaTest} para a cadeia fria.
 * Este percorre tudo de uma vez, na ordem em que acontece na operacao, e a
 * cada passo le de volta as tres entidades pela API. O que ele protege e a
 * <b>coerencia entre elas</b>: a requisicao, a bolsa e a rota contam a mesma
 * historia em todo instante.</p>
 *
 * <p>A tabela de estados esperada, que o teste segue linha a linha:</p>
 * <pre>
 * passo                         requisicao              item           bolsa        rota
 * criar requisicao (2 bolsas)   PENDENTE                PENDENTE       -            -
 * alocar a primeira             PARCIALMENTE_ATENDIDA   PARC_ALOCADO   RESERVADA    -
 * alocar a segunda              ATENDIDA                ALOCADO        RESERVADA    -
 * planejar rota (HU-05)         ATENDIDA                ALOCADO        RESERVADA    PLANEJADA
 * embarcar as duas              ATENDIDA                ALOCADO        RESERVADA    PLANEJADA
 * sair                          ATENDIDA                ALOCADO        EM_TRANSITO  EM_TRANSITO
 * leitura de temperatura        (sem mudanca)
 * concluir                      ATENDIDA                ALOCADO        ENTREGUE     CONCLUIDA
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Caminho completo: requisicao -> bolsa -> rota")
class FluxoRequisicaoBolsaRotaTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    private MockMvc mvc;
    private String hemocentroId;
    private String hospitalId;

    @BeforeEach
    void prepararRede() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

        hemocentroId = idDe(mvc.perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HemocentroRequest(
                                "Hemocentro do Caminho Completo", "(81) 3000-7100",
                                "50505050000505", endereco(-8.0528651, -34.8979716)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        hospitalId = idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                "Hospital do Caminho Completo", "(81) 3000-7200",
                                "9300001", endereco(-8.0474352, -34.9463152)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Dado um pedido de 2 bolsas, Quando ele e alocado, despachado e entregue, "
               + "Entao requisicao, item, bolsa e rota mudam de estado juntos, passo a passo")
    void doPedidoAEntrega() throws Exception {
        // ---- Passo 1: o hospital pede duas bolsas O+ de hemacias ----------
        JsonNode requisicao = criarRequisicao(2);
        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        conferirRequisicao(requisicaoId, "PENDENTE", "PENDENTE", 0);

        // ---- Passo 2: o hemocentro tem duas bolsas compativeis ------------
        String primeira = criarBolsa();
        String segunda = criarBolsa();
        conferirBolsa(primeira, "DISPONIVEL");
        conferirBolsa(segunda, "DISPONIVEL");

        // ---- Passo 3: aloca a primeira: a bolsa reserva, o pedido anda ----
        alocar(requisicaoId, itemId, primeira);
        conferirBolsa(primeira, "RESERVADA");
        conferirBolsa(segunda, "DISPONIVEL");
        conferirRequisicao(requisicaoId, "PARCIALMENTE_ATENDIDA", "PARCIALMENTE_ALOCADO", 1);

        // ---- Passo 4: aloca a segunda: o pedido fecha --------------------
        alocar(requisicaoId, itemId, segunda);
        conferirBolsa(segunda, "RESERVADA");
        conferirRequisicao(requisicaoId, "ATENDIDA", "ALOCADO", 2);

        // Pedido atendido nao aceita uma terceira bolsa.
        String terceira = criarBolsa();
        mvc.perform(post("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(terceira))))
                .andExpect(status().isConflict());
        conferirBolsa(terceira, "DISPONIVEL");

        // ---- Passo 5: planeja a rota, com a chegada calculada (HU-05) -----
        String rotaId = idDe(mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RotaRequest(
                                hemocentroId, hospitalId, LocalDateTime.now().plusHours(1), null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANEJADA"))
                .andExpect(jsonPath("$.previsaoChegada").exists())
                .andExpect(jsonPath("$.tempoEstimadoMinutos").exists())
                .andExpect(jsonPath("$.bolsas").isEmpty())
                .andReturn().getResponse().getContentAsString());

        // Rota planejada e vazia nao sai: nao ha o que transportar.
        mudarStatusRota(rotaId, StatusRota.EM_TRANSITO).andExpect(status().isConflict());
        conferirRota(rotaId, "PLANEJADA", 0);

        // ---- Passo 6: embarca as duas; a reserva continua ate a saida -----
        embarcar(rotaId, primeira);
        embarcar(rotaId, segunda);
        conferirRota(rotaId, "PLANEJADA", 2);
        conferirBolsa(primeira, "RESERVADA");
        conferirBolsa(segunda, "RESERVADA");

        // A terceira, que esta DISPONIVEL e nao reservada, nao embarca.
        mvc.perform(post("/api/v1/rotas/" + rotaId + "/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EmbarqueBolsaRequest(terceira))))
                .andExpect(status().isConflict());

        // ---- Passo 7: a rota sai; as bolsas vao junto ---------------------
        mudarStatusRota(rotaId, StatusRota.EM_TRANSITO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saidaReal").exists());
        conferirRota(rotaId, "EM_TRANSITO", 2);
        conferirBolsa(primeira, "EM_TRANSITO");
        conferirBolsa(segunda, "EM_TRANSITO");
        conferirRequisicao(requisicaoId, "ATENDIDA", "ALOCADO", 2);

        // Com as bolsas na rua, o pedido nao pode mais ser cancelado nem a
        // alocacao desfeita: as tres entidades travam juntas.
        mvc.perform(patch("/api/v1/requisicoes/" + requisicaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isConflict());

        String alocacaoId = json.readTree(mvc.perform(
                        get("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes"))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asString();
        mvc.perform(delete("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId
                        + "/alocacoes/" + alocacaoId))
                .andExpect(status().isConflict());

        // ---- Passo 8: a cadeia fria e monitorada no caminho (HU-07) --------
        mvc.perform(post("/api/v1/rotas/" + rotaId + "/temperaturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LeituraTemperaturaRequest(4.0, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dentroDaFaixa").value(true));
        // A leitura nao muda estado algum.
        conferirRota(rotaId, "EM_TRANSITO", 2);
        conferirBolsa(primeira, "EM_TRANSITO");

        // ---- Passo 9: entrega: a rota conclui e as bolsas sao entregues ----
        mudarStatusRota(rotaId, StatusRota.CONCLUIDA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chegadaReal").exists());
        conferirRota(rotaId, "CONCLUIDA", 2);
        conferirBolsa(primeira, "ENTREGUE");
        conferirBolsa(segunda, "ENTREGUE");
        conferirRequisicao(requisicaoId, "ATENDIDA", "ALOCADO", 2);

        // Entregue e estado final para a bolsa e para a rota.
        mudarStatusRota(rotaId, StatusRota.PLANEJADA).andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/bolsas/" + primeira)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISPONIVEL\"}"))
                .andExpect(status().isConflict());
        conferirBolsa(primeira, "ENTREGUE");

        // A terceira bolsa, que nunca entrou no fluxo, segue intacta no estoque.
        conferirBolsa(terceira, "DISPONIVEL");
    }

    @Test
    @DisplayName("Dado uma rota planejada com bolsas embarcadas, Quando ela e cancelada, "
               + "Entao as bolsas voltam a RESERVADA e o pedido continua ATENDIDA")
    void cancelarARotaDevolveAsBolsasAoPedido() throws Exception {
        JsonNode requisicao = criarRequisicao(1);
        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();
        String bolsa = criarBolsa();
        alocar(requisicaoId, itemId, bolsa);

        String rotaId = idDe(mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RotaRequest(
                                hemocentroId, hospitalId, LocalDateTime.now().plusHours(1), null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        embarcar(rotaId, bolsa);

        mudarStatusRota(rotaId, StatusRota.CANCELADA).andExpect(status().isOk());

        // A bolsa continua presa ao pedido, so nao viaja mais nesta rota.
        conferirRota(rotaId, "CANCELADA", 1);
        conferirBolsa(bolsa, "RESERVADA");
        conferirRequisicao(requisicaoId, "ATENDIDA", "ALOCADO", 1);

        // E o pedido, que ainda so tem reserva, pode ser cancelado: a bolsa
        // volta ao estoque.
        mvc.perform(patch("/api/v1/requisicoes/" + requisicaoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADA\"}"))
                .andExpect(status().isOk());
        conferirBolsa(bolsa, "DISPONIVEL");
        conferirRequisicao(requisicaoId, "CANCELADA", "PENDENTE", 0);
    }

    // -----------------------------------------------------------------------
    // Leituras de estado
    // -----------------------------------------------------------------------

    private void conferirRequisicao(String id, String status, String statusItem,
                                    int alocadas) throws Exception {
        mvc.perform(get("/api/v1/requisicoes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.itens[0].status").value(statusItem))
                .andExpect(jsonPath("$.itens[0].quantidadeAlocada").value(alocadas));
    }

    private void conferirBolsa(String id, String status) throws Exception {
        mvc.perform(get("/api/v1/bolsas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }

    private void conferirRota(String id, String status, int bolsasABordo) throws Exception {
        mvc.perform(get("/api/v1/rotas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.bolsas.length()").value(bolsasABordo));
    }

    // -----------------------------------------------------------------------
    // Acoes
    // -----------------------------------------------------------------------

    private JsonNode criarRequisicao(int quantidade) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/requisicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RequisicaoRequest(
                                hospitalId, PrioridadeRequisicao.URGENTE, null, null,
                                List.of(new ItemRequisicaoRequest(
                                        TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                        GrupoSanguineo.O_POS, quantidade))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String criarBolsa() throws Exception {
        return idDe(mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                GrupoSanguineo.O_POS, 450, LocalDate.now().minusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private void alocar(String requisicaoId, String itemId, String bolsaId) throws Exception {
        mvc.perform(post("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isCreated());
    }

    private void embarcar(String rotaId, String bolsaId) throws Exception {
        mvc.perform(post("/api/v1/rotas/" + rotaId + "/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EmbarqueBolsaRequest(bolsaId))))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions mudarStatusRota(
            String rotaId, StatusRota novo) throws Exception {
        return mvc.perform(patch("/api/v1/rotas/" + rotaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AtualizarStatusRotaRequest(novo, null, null))));
    }

    private String idDe(String respostaJson) {
        return json.readTree(respostaJson).get("id").asString();
    }

    private EnderecoDto endereco(double latitude, double longitude) {
        return new EnderecoDto("Rua do Caminho", "1", "Centro", "Recife", "PE",
                "50000-000", latitude, longitude);
    }
}
