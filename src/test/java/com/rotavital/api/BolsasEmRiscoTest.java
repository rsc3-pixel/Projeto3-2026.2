package com.rotavital.api;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.AtualizarStatusBolsaRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.HemocentroRepository;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-04: ver bolsas em risco de vencimento, pelo endpoint
 * {@code GET /api/v1/bolsas/em-risco}.
 *
 * <p>Os quatro criterios de aceite da historia, um bloco para cada: a janela
 * e configuravel, o resultado sai da mais critica para a menos, o estado
 * vazio tem mensagem propria, e as vencidas ficam separadas das que ainda
 * valem. Mais um bloco para o que a historia nao diz mas o estoque exige:
 * bolsa que ja saiu (em transito, entregue) nao e risco de descarte.</p>
 *
 * <p>Todas as consultas filtram pelo hemocentro do teste. O banco de testes e
 * compartilhado com classes que nao sao transacionais, e sem o filtro o
 * cenario "nada em risco" veria bolsas de outros testes.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("HU-04 · Bolsas em risco de vencimento")
class BolsasEmRiscoTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private BolsaRepository bolsas;

    @Autowired
    private HemocentroRepository hemocentros;

    private MockMvc mvc;
    private String hemocentroId;
    private String hospitalId;

    @BeforeEach
    void prepararCenario() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

        hemocentroId = idDe(mvc.perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HemocentroRequest(
                                "Hemocentro do Risco", "(81) 3000-9100", "30303030000303",
                                endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        hospitalId = idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                "Hospital do Risco", "(81) 3000-9200", "9100001",
                                endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Nested
    @DisplayName("Criterio 1: a janela e configuravel")
    class JanelaConfiguravel {

        @Test
        @DisplayName("Com bolsas vencendo em 3, 10 e 40 dias, a janela de 7 mostra so a de 3")
        void janelaDeSeteDias() throws Exception {
            String tresDias = bolsaQueVenceEm(3);
            bolsaQueVenceEm(10);
            bolsaQueVenceEm(40);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dias").value(7))
                    .andExpect(jsonPath("$.totalEmRisco").value(1))
                    .andExpect(jsonPath("$.emRisco[0].id").value(tresDias))
                    .andExpect(jsonPath("$.emRisco[0].diasRestantes").value(3));
        }

        @Test
        @DisplayName("A mesma situacao com janela de 15 mostra duas, e com 30 mostra duas tambem")
        void janelasDeQuinzeETrinta() throws Exception {
            bolsaQueVenceEm(3);
            bolsaQueVenceEm(10);
            bolsaQueVenceEm(40);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "15")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.totalEmRisco").value(2));

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "30")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.totalEmRisco").value(2));
        }

        @Test
        @DisplayName("Sem informar a janela, vale 7 dias")
        void janelaPadrao() throws Exception {
            mvc.perform(get("/api/v1/bolsas/em-risco").param("hemocentroId", hemocentroId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dias").value(7));
        }

        @Test
        @DisplayName("Janela zero ou negativa e recusada com 400")
        void janelaInvalida() throws Exception {
            mvc.perform(get("/api/v1/bolsas/em-risco").param("dias", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Hemocentro inexistente devolve 404")
        void hemocentroInexistente() throws Exception {
            mvc.perform(get("/api/v1/bolsas/em-risco").param("hemocentroId", "nao-existe"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Criterio 2: da mais critica para a menos")
    class OrdenacaoPorUrgencia {

        @Test
        @DisplayName("Bolsas vencendo em 6, 2 e 4 dias saem na ordem 2, 4, 6")
        void ordemPorDiasRestantes() throws Exception {
            // Cadastradas fora de ordem de proposito: a resposta nao pode
            // depender da ordem de insercao.
            String seis = bolsaQueVenceEm(6);
            String dois = bolsaQueVenceEm(2);
            String quatro = bolsaQueVenceEm(4);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.totalEmRisco").value(3))
                    .andExpect(jsonPath("$.emRisco[0].id").value(dois))
                    .andExpect(jsonPath("$.emRisco[1].id").value(quatro))
                    .andExpect(jsonPath("$.emRisco[2].id").value(seis))
                    .andExpect(jsonPath("$.emRisco[0].diasRestantes").value(2))
                    .andExpect(jsonPath("$.emRisco[2].diasRestantes").value(6));
        }

        @Test
        @DisplayName("Bolsa que vence hoje ainda conta como em risco, com zero dias restantes")
        void venceHoje() throws Exception {
            String hoje = bolsaQueVenceEm(0);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.emRisco[0].id").value(hoje))
                    .andExpect(jsonPath("$.emRisco[0].diasRestantes").value(0))
                    .andExpect(jsonPath("$.totalVencidas").value(0));
        }
    }

    @Nested
    @DisplayName("Criterio 3: estado vazio tem mensagem propria")
    class EstadoVazio {

        @Test
        @DisplayName("Sem bolsa vencendo em 7 dias, a resposta traz a mensagem e a lista vazia")
        void mensagemDoEstadoVazio() throws Exception {
            bolsaQueVenceEm(20);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalEmRisco").value(0))
                    .andExpect(jsonPath("$.emRisco").isEmpty())
                    .andExpect(jsonPath("$.mensagem")
                            .value("Nenhuma bolsa em risco nos proximos 7 dias"));
        }

        @Test
        @DisplayName("Com bolsa em risco, nao ha mensagem: a lista fala por si")
        void semMensagemQuandoHaRisco() throws Exception {
            bolsaQueVenceEm(2);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.mensagem").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Criterio 4: vencidas separadas das que ainda valem")
    class VencidasSeparadas {

        @Test
        @DisplayName("Bolsa vencida em estoque vai para a lista de vencidas, nao para a de risco")
        void vencidaNaoEntraNaListaDeRisco() throws Exception {
            String valida = bolsaQueVenceEm(3);
            String vencida = bolsaVencidaHa(2);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.totalEmRisco").value(1))
                    .andExpect(jsonPath("$.emRisco[0].id").value(valida))
                    .andExpect(jsonPath("$.totalVencidas").value(1))
                    .andExpect(jsonPath("$.vencidas[0].id").value(vencida))
                    .andExpect(jsonPath("$.vencidas[0].diasRestantes").value(-2));
        }

        @Test
        @DisplayName("Entre as vencidas, a que venceu ha mais tempo vem primeiro")
        void vencidasDaMaisAntigaParaAMaisRecente() throws Exception {
            String haUm = bolsaVencidaHa(1);
            String haCinco = bolsaVencidaHa(5);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.vencidas[0].id").value(haCinco))
                    .andExpect(jsonPath("$.vencidas[1].id").value(haUm));
        }
    }

    @Nested
    @DisplayName("So o que esta em estoque e risco de descarte")
    class SoEmEstoque {

        @Test
        @DisplayName("Bolsa reservada continua em risco: ainda esta no estoque")
        void reservadaConta() throws Exception {
            String bolsa = bolsaQueVenceEm(3);
            reservarViaRequisicao(bolsa, TipoHemocomponente.CONCENTRADO_HEMACIAS);

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.totalEmRisco").value(1))
                    .andExpect(jsonPath("$.emRisco[0].status").value("RESERVADA"));
        }

        @Test
        @DisplayName("Bolsa descartada ou em transito nao aparece em lista alguma")
        void foraDoEstoqueNaoConta() throws Exception {
            String descartada = bolsaQueVenceEm(3);
            mvc.perform(patch("/api/v1/bolsas/" + descartada)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(
                                    new AtualizarStatusBolsaRequest(StatusBolsa.DESCARTADA))))
                    .andExpect(status().isOk());

            String emTransito = bolsaQueVenceEm(2);
            reservarViaRequisicao(emTransito, TipoHemocomponente.CONCENTRADO_HEMACIAS);
            mvc.perform(patch("/api/v1/bolsas/" + emTransito)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(
                                    new AtualizarStatusBolsaRequest(StatusBolsa.EM_TRANSITO))))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.totalEmRisco").value(0))
                    .andExpect(jsonPath("$.totalVencidas").value(0));
        }
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    /**
     * Bolsa de hemacias (42 dias de validade) coletada no dia certo para
     * vencer daqui a {@code dias}.
     */
    private String bolsaQueVenceEm(int dias) throws Exception {
        TipoHemocomponente tipo = TipoHemocomponente.CONCENTRADO_HEMACIAS;
        LocalDate coleta = LocalDate.now().plusDays(dias).minusDays(tipo.getValidadeDias());
        return idDe(mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, tipo, GrupoSanguineo.O_POS, 450, coleta))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    /**
     * Bolsa vencida gravada direto no repositorio: a API recusa cadastrar
     * bolsa que ja nasce vencida, mas uma bolsa valida vence com o tempo, e e
     * esse estoque envelhecido que o painel precisa mostrar.
     */
    private String bolsaVencidaHa(int dias) {
        TipoHemocomponente tipo = TipoHemocomponente.CONCENTRADO_PLAQUETAS;
        Hemocentro hemocentro = hemocentros.findById(hemocentroId).orElseThrow();
        LocalDate coleta = LocalDate.now().minusDays(dias).minusDays(tipo.getValidadeDias());
        Bolsa vencida = new Bolsa("VENCIDA-" + dias + "-" + hemocentroId.substring(0, 8),
                tipo, GrupoSanguineo.A_POS, 300, coleta, hemocentro);
        return bolsas.save(vencida).getCodigoRastreio();
    }

    /** A bolsa passa a RESERVADA pelo caminho real: alocada a uma requisicao. */
    private void reservarViaRequisicao(String bolsaId, TipoHemocomponente tipo) throws Exception {
        JsonNode requisicao = json.readTree(mvc.perform(post("/api/v1/requisicoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RequisicaoRequest(
                                hospitalId, PrioridadeRequisicao.URGENTE, null, null,
                                List.of(new ItemRequisicaoRequest(tipo, GrupoSanguineo.O_POS, 1))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/v1/requisicoes/" + requisicao.get("id").asString()
                                + "/itens/" + requisicao.get("itens").get(0).get("id").asString()
                                + "/alocacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AlocacaoRequest(bolsaId))))
                .andExpect(status().isCreated());
    }

    private String idDe(String respostaJson) {
        return json.readTree(respostaJson).get("id").asString();
    }

    private EnderecoDto endereco() {
        return new EnderecoDto("Rua do Risco", "30", "Centro", "Recife", "PE",
                "50000-000", -8.05, -34.90);
    }
}
