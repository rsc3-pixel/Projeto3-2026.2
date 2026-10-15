package com.rotavital.api;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-08: a alocacao aceita bolsa compativel, nao apenas bolsa de grupo
 * identico.
 *
 * <p>Antes desta historia a verificacao era {@code bolsa.getGrupoSanguineo()
 * != item.getGrupoSanguineo()}, o que recusava doacao valida: um paciente A+
 * so recebia bolsa A+, embora possa receber de O-, O+ e A- tambem. O estoque
 * utilizavel ficava parado.</p>
 *
 * <p>Os cenarios abaixo saem de {@code docs/historias-de-usuario.md}, secao
 * HU-08.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompatibilidadeAlocacaoTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    private MockMvc mvc;
    private String hemocentroId;
    private String hospitalId;

    @BeforeEach
    void prepararCenario() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

        hemocentroId = idDe(mvc.perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HemocentroRequest(
                                "Hemocentro da Compatibilidade", "(81) 3000-3000",
                                "20202020000102", endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        hospitalId = idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                "Hospital da Compatibilidade", "(81) 3000-4000",
                                "8888888", endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Cenario 1: paciente A+ recebe bolsa O-, o doador universal")
    void doadorUniversalEAceito() throws Exception {
        String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.A_POS, 1);

        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        // Antes da HU-08 isto devolvia 409: a bolsa O- era recusada por um
        // item que pedia A+, embora O- doe para todos.
        mvc.perform(post("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsa))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bolsaId").value(bolsa));

        mvc.perform(get("/api/v1/bolsas/" + bolsa))
                .andExpect(jsonPath("$.status").value("RESERVADA"));
    }

    @Test
    @DisplayName("Cenario 2: paciente O- nao recebe bolsa A+, mesmo sendo a unica disponivel")
    void bolsaIncompativelEhRecusada() throws Exception {
        String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_NEG, 1);

        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        mvc.perform(post("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsa))))
                .andExpect(status().isConflict());

        // A bolsa recusada permanece disponivel para outro item.
        mvc.perform(get("/api/v1/bolsas/" + bolsa))
                .andExpect(jsonPath("$.status").value("DISPONIVEL"));
    }

    @Test
    @DisplayName("A relacao nao e simetrica: O- doa para A+, mas A+ nao doa para O-")
    void relacaoNaoSimetrica() throws Exception {
        // Sentido que funciona
        String bolsaONeg = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
        JsonNode pedeAPos = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.A_POS, 1);
        alocarEsperando(pedeAPos, bolsaONeg, 201);

        // Sentido inverso, com os mesmos grupos trocados de lado
        String bolsaAPos = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);
        JsonNode pedeONeg = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_NEG, 1);
        alocarEsperando(pedeONeg, bolsaAPos, 409);
    }

    @Test
    @DisplayName("Paciente AB+ aceita bolsa de qualquer grupo, por ser receptor universal")
    void receptorUniversalAceitaTodos() throws Exception {
        for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
            String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, grupo);
            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.AB_POS, 1);
            alocarEsperando(requisicao, bolsa, 201);
        }
    }

    @Test
    @DisplayName("Rh positivo nao doa para Rh negativo do mesmo grupo ABO")
    void positivoNaoDoaParaNegativo() throws Exception {
        String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.A_NEG, 1);
        alocarEsperando(requisicao, bolsa, 409);
    }

    @Test
    @DisplayName("Plasma segue regra invertida: O- nao serve para paciente A+")
    void plasmaSegueRegraInvertida() throws Exception {
        // Em hemacias esta mesma combinacao e aceita, como prova o cenario 1.
        String bolsa = criarBolsa(TipoHemocomponente.PLASMA_FRESCO_CONGELADO, GrupoSanguineo.O_NEG);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.PLASMA_FRESCO_CONGELADO,
                GrupoSanguineo.A_POS, 1);
        alocarEsperando(requisicao, bolsa, 409);
    }

    @Test
    @DisplayName("Plasma AB serve para qualquer receptor, o oposto das hemacias")
    void plasmaDeAbServeParaTodos() throws Exception {
        String bolsa = criarBolsa(TipoHemocomponente.PLASMA_FRESCO_CONGELADO, GrupoSanguineo.AB_POS);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.PLASMA_FRESCO_CONGELADO,
                GrupoSanguineo.O_NEG, 1);
        alocarEsperando(requisicao, bolsa, 201);
    }

    @Test
    @DisplayName("A mensagem de erro lista os grupos aceitos")
    void mensagemDeErroOrientaOUsuario() throws Exception {
        String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.O_NEG, 1);

        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        String corpo = mvc.perform(
                        post("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(new AlocacaoRequest(bolsa))))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        // Saber que foi recusado nao basta: quem opera precisa saber o que
        // procurar no estoque. A mensagem usa a descricao do enum ("O-"), nao
        // o nome da constante, porque GrupoSanguineo.toString() devolve o
        // texto legivel.
        org.junit.jupiter.api.Assertions.assertTrue(
                corpo.contains("Grupos aceitos") && corpo.contains("O-"),
                "a mensagem deveria listar os grupos aceitos, mas veio: " + corpo);
    }

    @Test
    @DisplayName("Hemocomponente diferente continua sendo recusado, mesmo com grupo compativel")
    void tipoIncompativelSegueRecusado() throws Exception {
        String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_NEG);
        JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                GrupoSanguineo.A_POS, 1);
        alocarEsperando(requisicao, bolsa, 409);
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private void alocarEsperando(JsonNode requisicao, String bolsaId, int statusEsperado)
            throws Exception {
        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        var resultado = mvc.perform(
                post("/api/v1/requisicoes/" + requisicaoId + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))));

        if (statusEsperado == 201) {
            resultado.andExpect(status().isCreated());
        } else {
            resultado.andExpect(status().isConflict());
        }
    }

    private String criarBolsa(TipoHemocomponente tipo, GrupoSanguineo grupo) throws Exception {
        String resposta = mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, tipo, grupo, 450, LocalDate.now().minusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idDe(resposta);
    }

    private JsonNode criarRequisicao(TipoHemocomponente tipo, GrupoSanguineo grupo, int quantidade)
            throws Exception {
        String resposta = mvc.perform(post("/api/v1/requisicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RequisicaoRequest(
                                hospitalId, PrioridadeRequisicao.URGENTE, null, null,
                                List.of(new ItemRequisicaoRequest(tipo, grupo, quantidade))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta);
    }

    private String idDe(String respostaJson) throws Exception {
        return json.readTree(respostaJson).get("id").asString();
    }

    private EnderecoDto endereco() {
        return new EnderecoDto("Rua da Compatibilidade", "20", "Boa Vista", "Recife", "PE",
                "50000-100", -8.06, -34.88);
    }
}
