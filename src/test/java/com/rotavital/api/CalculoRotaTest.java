package com.rotavital.api;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AtualizarStatusRotaRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EmbarqueBolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.api.dto.RotaRequest;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.dominio.enums.TipoHemocomponente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-05: calcular rota de entrega pela API.
 *
 * <p>A malha aqui e montada a partir das unidades que o teste cadastra, com
 * as coordenadas reais do HEMOPE e do Hospital das Clinicas. O banco de
 * testes e compartilhado e pode ter outras unidades de outros testes; isso
 * nao atrapalha, porque a asserção e sobre o caminho entre as duas do teste,
 * e o Dijkstra so devolve um caminho mais rapido, nunca mais lento, quando
 * ha unidades a mais na malha.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("HU-05 · Calcular rota de entrega")
class CalculoRotaTest {

    /** HEMOPE, Gracas. */
    private static final EnderecoDto HEMOPE = new EnderecoDto(
            "Rua Joaquim Nabuco", "171", "Gracas", "Recife", "PE", "52011-000",
            -8.0528651, -34.8979716);

    /** Hospital das Clinicas da UFPE, Cidade Universitaria: 5,4 km em linha reta do HEMOPE. */
    private static final EnderecoDto HC_UFPE = new EnderecoDto(
            "Avenida Professor Moraes Rego", "1235", "Cidade Universitaria", "Recife", "PE",
            "50670-901", -8.0474352, -34.9463152);

    /** Um hospital em Sao Paulo: a mais de 2.000 km de qualquer unidade da malha. */
    private static final EnderecoDto FORA_DA_MALHA = new EnderecoDto(
            "Avenida Paulista", "1000", "Bela Vista", "Sao Paulo", "SP", "01310-100",
            -23.5614, -46.6559);

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
        hemocentroId = criarHemocentro("Hemocentro da Rota Calculada", "40404040000404", HEMOPE);
        hospitalId = criarHospital("Hospital da Rota Calculada", "9200001", HC_UFPE);
    }

    @Nested
    @DisplayName("GET /api/v1/rotas/calcular")
    class Calcular {

        @Test
        @DisplayName("Devolve o caminho da origem ao destino com o tempo em minutos")
        void caminhoEntreDuasUnidades() throws Exception {
            String resposta = mvc.perform(get("/api/v1/rotas/calcular")
                            .param("origemId", hemocentroId)
                            .param("destinoId", hospitalId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alcancavel").value(true))
                    .andExpect(jsonPath("$.origemId").value(hemocentroId))
                    .andExpect(jsonPath("$.destinoId").value(hospitalId))
                    .andExpect(jsonPath("$.mensagem").doesNotExist())
                    .andReturn().getResponse().getContentAsString();

            JsonNode rota = json.readTree(resposta);
            double tempo = rota.get("tempoMinutos").asDouble();
            JsonNode caminho = rota.get("caminho");

            // 5,4 km em linha reta, com sinuosidade 1,4 e 25 km/h, dao cerca
            // de 18 minutos pela aresta direta. Como cada unidade so se liga
            // as quatro mais proximas, unidades de outros testes podem obrigar
            // o caminho a passar por uma parada intermediaria e somar um ou
            // dois minutos; a faixa abaixo acomoda isso sem aceitar absurdo.
            assertTrue(tempo > 0 && tempo < 25, "tempo fora do esperado: " + tempo);
            assertTrue(caminho.size() >= 2);
            assertEquals(hemocentroId, caminho.get(0).get("id").asString());
            assertEquals(hospitalId, caminho.get(caminho.size() - 1).get("id").asString());
            assertEquals("HEMOCENTRO", caminho.get(0).get("tipo").asString());
            assertEquals("HOSPITAL", caminho.get(caminho.size() - 1).get("tipo").asString());
            assertEquals("Hemocentro da Rota Calculada", caminho.get(0).get("nome").asString());
        }

        @Test
        @DisplayName("Destino sem trecho na malha devolve 200 com a mensagem, nao erro")
        void destinoInalcancavel() throws Exception {
            String isolado = criarHospital("Hospital Fora da Malha", "9200002", FORA_DA_MALHA);

            mvc.perform(get("/api/v1/rotas/calcular")
                            .param("origemId", hemocentroId)
                            .param("destinoId", isolado))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alcancavel").value(false))
                    .andExpect(jsonPath("$.caminho").isEmpty())
                    .andExpect(jsonPath("$.tempoMinutos").doesNotExist())
                    .andExpect(jsonPath("$.mensagem")
                            .value("Nao ha rota disponivel para este destino"));
        }

        @Test
        @DisplayName("O caminho tambem pode partir de um hospital: a malha nao tem sentido unico")
        void deHospitalParaHemocentro() throws Exception {
            mvc.perform(get("/api/v1/rotas/calcular")
                            .param("origemId", hospitalId)
                            .param("destinoId", hemocentroId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alcancavel").value(true))
                    .andExpect(jsonPath("$.caminho[0].id").value(hospitalId));
        }

        @Test
        @DisplayName("Origem ou destino que nao existem devolvem 404")
        void unidadeInexistente() throws Exception {
            mvc.perform(get("/api/v1/rotas/calcular")
                            .param("origemId", "nao-existe")
                            .param("destinoId", hospitalId))
                    .andExpect(status().isNotFound());

            mvc.perform(get("/api/v1/rotas/calcular")
                            .param("origemId", hemocentroId)
                            .param("destinoId", "nao-existe"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/rotas com a previsao de chegada calculada")
    class PlanejarComPrevisaoCalculada {

        @Test
        @DisplayName("Sem previsaoChegada, a chegada e a saida mais o tempo do caminho, arredondado ao minuto")
        void chegadaCalculada() throws Exception {
            LocalDateTime saida = LocalDateTime.of(2026, 11, 13, 8, 0);

            String resposta = mvc.perform(post("/api/v1/rotas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RotaRequest(
                                    hemocentroId, hospitalId, saida, null))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PLANEJADA"))
                    .andExpect(jsonPath("$.tempoEstimadoMinutos").exists())
                    .andReturn().getResponse().getContentAsString();

            JsonNode rota = json.readTree(resposta);
            double tempo = rota.get("tempoEstimadoMinutos").asDouble();
            LocalDateTime saidaGravada = LocalDateTime.parse(rota.get("previsaoSaida").asString());
            LocalDateTime chegada = LocalDateTime.parse(rota.get("previsaoChegada").asString());

            long esperado = Math.max(1, (long) Math.ceil(tempo));
            assertEquals(saida, saidaGravada);
            assertEquals(esperado, ChronoUnit.MINUTES.between(saida, chegada));
            assertEquals(hemocentroId, rota.get("caminho").get(0).asString());
            assertEquals(hospitalId, rota.get("caminho").get(rota.get("caminho").size() - 1).asString());
        }

        @Test
        @DisplayName("Unidades no mesmo endereco tem caminho de custo zero, e a chegada fica um minuto depois da saida")
        void pisoDeUmMinuto() throws Exception {
            String mesmoLugar = criarHospital("Hospital no Mesmo Endereco", "9200003", HEMOPE);
            LocalDateTime saida = LocalDateTime.of(2026, 11, 13, 9, 0);

            String resposta = mvc.perform(post("/api/v1/rotas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RotaRequest(
                                    hemocentroId, mesmoLugar, saida, null))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tempoEstimadoMinutos").value(0.0))
                    .andReturn().getResponse().getContentAsString();

            LocalDateTime chegada = LocalDateTime.parse(
                    json.readTree(resposta).get("previsaoChegada").asString());
            assertEquals(saida.plusMinutes(1), chegada);
        }

        @Test
        @DisplayName("Sem previsaoChegada e sem caminho, o despacho nao pode ser planejado: 409")
        void semCaminhoNaoPlaneja() throws Exception {
            String isolado = criarHospital("Hospital Fora da Malha", "9200004", FORA_DA_MALHA);

            mvc.perform(post("/api/v1/rotas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RotaRequest(
                                    hemocentroId, isolado, LocalDateTime.now().plusHours(1), null))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.startsWith(
                                    "Nao ha rota disponivel para este destino")));
        }

        @Test
        @DisplayName("Com previsaoChegada informada, vale a informada, e o caminho fica registrado como referencia")
        void chegadaInformadaPrevalece() throws Exception {
            LocalDateTime saida = LocalDateTime.of(2026, 11, 13, 10, 0);
            LocalDateTime chegada = saida.plusHours(3);

            String resposta = mvc.perform(post("/api/v1/rotas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RotaRequest(
                                    hemocentroId, hospitalId, saida, chegada))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tempoEstimadoMinutos").exists())
                    .andExpect(jsonPath("$.caminho[0]").value(hemocentroId))
                    .andReturn().getResponse().getContentAsString();

            assertEquals(chegada, LocalDateTime.parse(
                    json.readTree(resposta).get("previsaoChegada").asString()));
        }

        @Test
        @DisplayName("Com previsaoChegada informada, destino sem caminho ainda pode ser planejado a mao")
        void planejamentoManualSemCaminho() throws Exception {
            String isolado = criarHospital("Hospital Fora da Malha", "9200005", FORA_DA_MALHA);
            LocalDateTime saida = LocalDateTime.of(2026, 11, 13, 11, 0);

            mvc.perform(post("/api/v1/rotas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RotaRequest(
                                    hemocentroId, isolado, saida, saida.plusDays(1)))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tempoEstimadoMinutos").doesNotExist())
                    .andExpect(jsonPath("$.caminho").isEmpty());
        }
    }

    @Nested
    @DisplayName("Cenario 3 da historia: do calculo ao despacho")
    class DoCalculoAoDespacho {

        @Test
        @DisplayName("Rota calculada e confirmada nasce PLANEJADA, com previsoes, e as bolsas embarcadas saem EM_TRANSITO")
        void confirmarODespacho() throws Exception {
            // Dado que calculei a rota
            mvc.perform(get("/api/v1/rotas/calcular")
                            .param("origemId", hemocentroId)
                            .param("destinoId", hospitalId))
                    .andExpect(jsonPath("$.alcancavel").value(true));

            // Quando eu confirmar o despacho
            LocalDateTime saida = LocalDateTime.now().plusHours(1);
            String rotaId = idDe(mvc.perform(post("/api/v1/rotas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RotaRequest(
                                    hemocentroId, hospitalId, saida, null))))
                    .andExpect(status().isCreated())
                    // Entao a rota nasce PLANEJADA com as duas previsoes
                    .andExpect(jsonPath("$.status").value("PLANEJADA"))
                    .andExpect(jsonPath("$.previsaoSaida").exists())
                    .andExpect(jsonPath("$.previsaoChegada").exists())
                    .andReturn().getResponse().getContentAsString());

            // E, embarcada uma bolsa reservada e dada a saida...
            String bolsa = bolsaReservada();
            mvc.perform(post("/api/v1/rotas/" + rotaId + "/bolsas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new EmbarqueBolsaRequest(bolsa))))
                    .andExpect(status().isCreated());
            mvc.perform(patch("/api/v1/rotas/" + rotaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new AtualizarStatusRotaRequest(
                                    StatusRota.EM_TRANSITO, null, null))))
                    .andExpect(status().isOk());

            // ...a bolsa embarcada muda para EM_TRANSITO
            mvc.perform(get("/api/v1/bolsas/" + bolsa))
                    .andExpect(jsonPath("$.status").value("EM_TRANSITO"));
        }
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

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

    /** Bolsa RESERVADA pelo caminho real: cadastrada e alocada a uma requisicao. */
    private String bolsaReservada() throws Exception {
        String bolsa = idDe(mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                GrupoSanguineo.O_NEG, 450, LocalDate.now().minusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        JsonNode requisicao = json.readTree(mvc.perform(post("/api/v1/requisicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RequisicaoRequest(
                                hospitalId, PrioridadeRequisicao.EMERGENCIA, null, null,
                                List.of(new ItemRequisicaoRequest(
                                        TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                        GrupoSanguineo.O_NEG, 1))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/requisicoes/" + requisicao.get("id").asString()
                                + "/itens/" + requisicao.get("itens").get(0).get("id").asString()
                                + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsa))))
                .andExpect(status().isCreated());
        return bolsa;
    }

    private String idDe(String respostaJson) {
        return json.readTree(respostaJson).get("id").asString();
    }
}
