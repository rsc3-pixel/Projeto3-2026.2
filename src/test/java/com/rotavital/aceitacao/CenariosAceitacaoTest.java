package com.rotavital.aceitacao;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AtualizarStatusBolsaRequest;
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
import com.rotavital.dominio.enums.StatusBolsa;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de aceitacao, um por historia de usuario.
 *
 * <p>Cada metodo traduz um cenario escrito em Gherkin no documento
 * {@code docs/historias-de-usuario.md}. A estrutura Dado / Quando / Entao
 * aparece no nome do teste e nos comentarios do corpo, para que o cenario
 * continue legivel por quem escreveu a historia e nao le Java.</p>
 *
 * <p>Diferente dos testes de unidade, que exercitam uma regra isolada, e dos
 * de integracao, que exercitam um endpoint, estes percorrem o caminho que o
 * usuario percorreria: montam o cenario pela API, executam a acao e conferem
 * o resultado observavel.</p>
 *
 * <p>Entregavel do E2 do desafio de Infraestrutura de Software: ao menos um
 * teste de aceitacao por historia.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Testes de aceitacao das historias de usuario")
class CenariosAceitacaoTest {

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
                                "Hemocentro da Aceitacao", "(81) 3000-7000",
                                "40404040000104", endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        hospitalId = idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                "Hospital da Aceitacao", "(81) 3000-8000",
                                "1010101", endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-01 — Cadastrar bolsa coletada")
    class CadastrarBolsa {

        @Test
        @DisplayName("Dado uma coleta de plaquetas, Quando eu cadastrar a bolsa, "
                   + "Entao a validade e calculada em 5 dias sem eu informar")
        void validadeCalculadaPeloComponente() throws Exception {
            // Dado uma coleta de plaquetas feita hoje
            LocalDate coleta = LocalDate.now();

            // Quando eu cadastrar a bolsa informando apenas o componente
            String resposta = mvc.perform(post("/api/v1/bolsas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new BolsaRequest(
                                    hemocentroId, TipoHemocomponente.CONCENTRADO_PLAQUETAS,
                                    GrupoSanguineo.O_NEG, 300, coleta))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            // Entao a validade vem calculada, e nao informada por mim
            JsonNode bolsa = json.readTree(resposta);
            String esperada = coleta.plusDays(
                    TipoHemocomponente.CONCENTRADO_PLAQUETAS.getValidadeDias()).toString();

            org.junit.jupiter.api.Assertions.assertEquals(
                    esperada, bolsa.get("dataValidade").asString(),
                    "plaquetas valem 5 dias a partir da coleta");

            // E a bolsa nasce disponivel para o estoque
            org.junit.jupiter.api.Assertions.assertEquals(
                    "DISPONIVEL", bolsa.get("status").asString());
        }
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-02 — Solicitar hemocomponentes")
    class SolicitarHemocomponentes {

        @Test
        @DisplayName("Dado uma requisicao de 2 bolsas, Quando as duas forem alocadas, "
                   + "Entao a requisicao passa a ATENDIDA")
        void statusAcompanhaOAtendimento() throws Exception {
            // Dado uma requisicao pedindo duas bolsas A+
            JsonNode requisicao = criarRequisicao(
                    TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS, 2);
            String requisicaoId = requisicao.get("id").asString();
            String itemId = requisicao.get("itens").get(0).get("id").asString();

            // E duas bolsas compativeis em estoque
            String primeira = criarBolsa(
                    TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);
            String segunda = criarBolsa(
                    TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);

            // Quando eu alocar a primeira
            alocar(requisicaoId, itemId, primeira);

            // Entao a requisicao fica parcialmente atendida
            mvc.perform(get("/api/v1/requisicoes/" + requisicaoId))
                    .andExpect(jsonPath("$.status").value("PARCIALMENTE_ATENDIDA"));

            // Quando eu alocar a segunda
            alocar(requisicaoId, itemId, segunda);

            // Entao a requisicao passa a atendida
            mvc.perform(get("/api/v1/requisicoes/" + requisicaoId))
                    .andExpect(jsonPath("$.status").value("ATENDIDA"))
                    .andExpect(jsonPath("$.itens[0].status").value("ALOCADO"));
        }
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-03 — Alocar bolsa priorizando validade (FEFO)")
    class AlocarPorValidade {

        @Test
        @DisplayName("Dado bolsas de validades diferentes, Quando eu consultar o estoque "
                   + "ordenado, Entao a que vence antes aparece primeiro")
        void bolsaQueVenceAntesSaiPrimeiro() throws Exception {
            // Dado tres bolsas O- de hemacias, cadastradas fora de ordem
            criarBolsaComColeta(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, LocalDate.now().minusDays(5));   // vence depois
            criarBolsaComColeta(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, LocalDate.now().minusDays(40));  // vence antes
            criarBolsaComColeta(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, LocalDate.now().minusDays(20));  // no meio

            // Quando eu consultar o estoque ordenado por validade
            String resposta = mvc.perform(get("/api/v1/bolsas")
                            .param("sort", "dataValidade,asc"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // Entao a primeira da lista e a que vence antes
            JsonNode bolsas = json.readTree(resposta);
            String primeira = bolsas.get(0).get("dataValidade").asString();
            String segunda = bolsas.get(1).get("dataValidade").asString();

            org.junit.jupiter.api.Assertions.assertTrue(
                    primeira.compareTo(segunda) <= 0,
                    "a bolsa que vence antes precisa vir primeiro: FEFO");
        }

        @Test
        @DisplayName("Dado uma coleta antiga demais, Quando eu tentar cadastrar a bolsa, "
                   + "Entao o sistema recusa antes de ela entrar no estoque")
        void bolsaVencidaNemChegaAoEstoque() throws Exception {
            // Dado uma coleta de plaquetas feita ha 10 dias, sendo que plaquetas
            // valem 5: a bolsa ja nasceria vencida.
            //
            // Descoberto ao escrever este cenario: a barreira contra bolsa
            // vencida nao esta so na alocacao, esta na entrada do estoque. E
            // mais forte assim, porque a bolsa invalida nunca chega a existir.
            LocalDate coletaAntiga = LocalDate.now().minusDays(10);

            // Quando eu tentar cadastrar essa bolsa
            // Entao o sistema recusa com conflito
            mvc.perform(post("/api/v1/bolsas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new BolsaRequest(
                                    hemocentroId, TipoHemocomponente.CONCENTRADO_PLAQUETAS,
                                    GrupoSanguineo.O_NEG, 300, coletaAntiga))))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Dado uma bolsa que vence hoje, Quando eu aloca-la, "
                   + "Entao a alocacao e aceita: ela so vence amanha")
        void bolsaNoUltimoDiaAindaVale() throws Exception {
            // Dado uma bolsa de plaquetas coletada ha exatamente 5 dias, o
            // limite da validade do componente
            String noLimite = criarBolsaComColeta(
                    TipoHemocomponente.CONCENTRADO_PLAQUETAS,
                    GrupoSanguineo.O_NEG, LocalDate.now().minusDays(
                            TipoHemocomponente.CONCENTRADO_PLAQUETAS.getValidadeDias()));

            // E uma requisicao que pede esse componente
            JsonNode requisicao = criarRequisicao(
                    TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_NEG, 1);

            // Quando eu alocar a bolsa que vence hoje
            // Entao a alocacao e aceita: vencer e passar da data, nao chegar nela
            alocarEsperando(requisicao, noLimite, true);
        }
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-05 — Calcular rota de entrega")
    class CalcularRota {

        @Test
        @DisplayName("Dado uma rota planejada com bolsa embarcada, Quando ela sair, "
                   + "Entao o status passa a EM_TRANSITO")
        void rotaSegueOCicloDeVida() throws Exception {
            // Dado uma rota planejada entre o hemocentro e o hospital
            String rota = criarRota();

            // E uma bolsa reservada embarcada nela
            embarcar(rota, TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);

            // Quando a rota sair para entrega
            porEmTransito(rota);

            // Entao o status reflete que ela esta em transito
            mvc.perform(get("/api/v1/rotas/" + rota))
                    .andExpect(jsonPath("$.status").value("EM_TRANSITO"));
        }
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-06 — Acompanhar indicadores da rede")
    class AcompanharIndicadores {

        @Test
        @DisplayName("Dado bolsas em estoque, Quando eu consultar os indicadores, "
                   + "Entao vejo o estoque por tipo sanguineo")
        void indicadoresRefletemOEstoqueReal() throws Exception {
            // Dado duas bolsas O- em estoque
            criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
            criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);

            // Quando eu consultar o indicador de estoque por tipo
            // Entao o indicador responde com os dados reais, nao valores fixos
            mvc.perform(get("/api/v1/indicadores/estoque-por-tipo"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].grupoSanguineo").exists());
        }
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-07 — Monitorar temperatura durante o transporte")
    class MonitorarTemperatura {

        @Test
        @DisplayName("Dado uma remessa com hemacias e plaquetas, Quando o sensor marcar 5C, "
                   + "Entao o alerta sai apenas para as plaquetas")
        void faixaDependeDoComponenteEmbarcado() throws Exception {
            // Dado uma rota transportando hemacias e plaquetas no mesmo compartimento
            String rota = criarRota();
            embarcar(rota, TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
            embarcar(rota, TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.A_POS);
            porEmTransito(rota);

            // Quando o sensor registrar 5 graus
            // 5C esta dentro da faixa das hemacias (1 a 6) e fora da faixa das
            // plaquetas (20 a 24). Uma faixa unica por remessa erraria aqui.
            mvc.perform(post("/api/v1/rotas/" + rota + "/temperaturas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(
                                    new LeituraTemperaturaRequest(5.0))))
                    .andExpect(status().isCreated())
                    // Entao a leitura e marcada como fora da faixa
                    .andExpect(jsonPath("$.dentroDaFaixa").value(false))
                    // E o alerta sai apenas para as plaquetas
                    .andExpect(jsonPath("$.alertas.length()").value(1))
                    .andExpect(jsonPath("$.alertas[0].componente")
                            .value("CONCENTRADO_PLAQUETAS"));
        }
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-08 — Alocar respeitando compatibilidade ABO/Rh")
    class AlocarPorCompatibilidade {

        @Test
        @DisplayName("Dado um paciente A+ e uma bolsa O-, Quando eu alocar, "
                   + "Entao a doacao do doador universal e aceita")
        void doadorUniversalEhAceito() throws Exception {
            // Dado uma bolsa O-, o doador universal de hemacias
            String bolsa = criarBolsa(
                    TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);

            // E uma requisicao para um paciente A+
            JsonNode requisicao = criarRequisicao(
                    TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS, 1);

            // Quando eu alocar a bolsa O- para o item A+
            // Entao a alocacao e aceita, porque O- doa para todos os grupos
            alocarEsperando(requisicao, bolsa, true);

            // E a bolsa fica reservada para esta requisicao
            mvc.perform(get("/api/v1/bolsas/" + bolsa))
                    .andExpect(jsonPath("$.status").value("RESERVADA"));
        }

        @Test
        @DisplayName("Dado um paciente O- e uma bolsa A+, Quando eu alocar, "
                   + "Entao a alocacao e recusada mesmo sendo a unica bolsa")
        void bolsaIncompativelEhRecusada() throws Exception {
            // Dado uma bolsa A+ como unica em estoque
            String bolsa = criarBolsa(
                    TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS);

            // E uma requisicao para um paciente O-, que so recebe de O-
            JsonNode requisicao = criarRequisicao(
                    TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 1);

            // Quando eu tentar alocar
            // Entao a alocacao e recusada: falta de estoque compativel nao
            // autoriza transfundir sangue incompativel
            alocarEsperando(requisicao, bolsa, false);
        }
    }

    // =======================================================================
    // Apoio
    // =======================================================================

    private String criarBolsa(TipoHemocomponente tipo, GrupoSanguineo grupo) throws Exception {
        return criarBolsaComColeta(tipo, grupo, LocalDate.now().minusDays(1));
    }

    private String criarBolsaComColeta(TipoHemocomponente tipo, GrupoSanguineo grupo,
                                       LocalDate coleta) throws Exception {
        String resposta = mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, tipo, grupo, 450, coleta))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idDe(resposta);
    }

    private JsonNode criarRequisicao(TipoHemocomponente tipo, GrupoSanguineo grupo,
                                     int quantidade) throws Exception {
        String resposta = mvc.perform(post("/api/v1/requisicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RequisicaoRequest(
                                hospitalId, PrioridadeRequisicao.URGENTE, null, null,
                                List.of(new ItemRequisicaoRequest(tipo, grupo, quantidade))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta);
    }

    private void alocar(String requisicaoId, String itemId, String bolsaId) throws Exception {
        mvc.perform(post("/api/v1/requisicoes/" + requisicaoId
                                + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isCreated());
    }

    private void alocarEsperando(JsonNode requisicao, String bolsaId, boolean aceita)
            throws Exception {
        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        var resultado = mvc.perform(post("/api/v1/requisicoes/" + requisicaoId
                                + "/itens/" + itemId + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))));

        if (aceita) {
            resultado.andExpect(status().isCreated());
        } else {
            resultado.andExpect(status().isConflict());
        }
    }

    private String criarRota() throws Exception {
        return idDe(mvc.perform(post("/api/v1/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RotaRequest(
                                hemocentroId, hospitalId,
                                LocalDateTime.now().plusHours(1),
                                LocalDateTime.now().plusHours(3)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private void embarcar(String rotaId, TipoHemocomponente tipo, GrupoSanguineo grupo)
            throws Exception {
        String bolsa = criarBolsa(tipo, grupo);

        // So bolsa RESERVADA embarca: a regra impede que uma bolsa disponivel
        // no estoque saia em viagem sem destino definido.
        mvc.perform(patch("/api/v1/bolsas/" + bolsa)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new AtualizarStatusBolsaRequest(StatusBolsa.RESERVADA))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/rotas/" + rotaId + "/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EmbarqueBolsaRequest(bolsa))))
                .andExpect(status().isCreated());
    }

    private void porEmTransito(String rotaId) throws Exception {
        mvc.perform(patch("/api/v1/rotas/" + rotaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AtualizarStatusRotaRequest(
                                StatusRota.EM_TRANSITO, LocalDateTime.now(), null))))
                .andExpect(status().isOk());
    }

    private String idDe(String respostaJson) throws Exception {
        return json.readTree(respostaJson).get("id").asString();
    }

    private EnderecoDto endereco() {
        return new EnderecoDto("Rua da Aceitacao", "40", "Espinheiro", "Recife", "PE",
                "50000-300", -8.04, -34.90);
    }
}
