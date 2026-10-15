package com.rotavital.api;

import com.rotavital.api.dto.AtualizarStatusBolsaRequest;
import com.rotavital.api.dto.AtualizarStatusRotaRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EmbarqueBolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.LeituraTemperaturaRequest;
import com.rotavital.api.dto.RotaRequest;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
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

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-07: monitorar temperatura durante o transporte.
 *
 * <p>Os cenarios saem de {@code docs/historias-de-usuario.md}, secao HU-07.
 * O cenario 4 e o que uma implementacao apressada erra: uma remessa pode
 * levar componentes de faixas diferentes, e a mesma leitura pode estar
 * correta para um e ser excursao para outro.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TemperaturaRemessaTest {

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
                                "Hemocentro da Telemetria", "(81) 3000-5000",
                                "30303030000103", endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        hospitalId = idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                "Hospital da Telemetria", "(81) 3000-6000",
                                "9999999", endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Cenario 1: 4 graus com hemacias a bordo nao gera alerta")
    void leituraDentroDaFaixa() throws Exception {
        String rota = rotaEmTransitoCom(TipoHemocomponente.CONCENTRADO_HEMACIAS);

        registrar(rota, 4.0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dentroDaFaixa").value(true))
                .andExpect(jsonPath("$.alertas").isEmpty())
                .andExpect(jsonPath("$.temperaturaCelsius").value(4.0));
    }

    @Test
    @DisplayName("Cenario 2: 9 graus com hemacias a bordo gera alerta de cadeia fria")
    void excursaoDeTemperatura() throws Exception {
        String rota = rotaEmTransitoCom(TipoHemocomponente.CONCENTRADO_HEMACIAS);

        registrar(rota, 9.0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dentroDaFaixa").value(false))
                .andExpect(jsonPath("$.alertas.length()").value(1))
                .andExpect(jsonPath("$.alertas[0].componente").value("CONCENTRADO_HEMACIAS"))
                .andExpect(jsonPath("$.alertas[0].faixaMinima").value(1.0))
                .andExpect(jsonPath("$.alertas[0].faixaMaxima").value(6.0))
                .andExpect(jsonPath("$.alertas[0].bolsasAfetadas").value(1));
    }

    @Test
    @DisplayName("Cenario 3: 22 graus e normal para plaquetas e alerta para hemacias")
    void aFaixaDependeDoComponente() throws Exception {
        String comPlaquetas = rotaEmTransitoCom(TipoHemocomponente.CONCENTRADO_PLAQUETAS);
        registrar(comPlaquetas, 22.0)
                .andExpect(jsonPath("$.dentroDaFaixa").value(true));

        String comHemacias = rotaEmTransitoCom(TipoHemocomponente.CONCENTRADO_HEMACIAS);
        registrar(comHemacias, 22.0)
                .andExpect(jsonPath("$.dentroDaFaixa").value(false))
                .andExpect(jsonPath("$.alertas[0].componente").value("CONCENTRADO_HEMACIAS"));
    }

    @Test
    @DisplayName("Cenario 4: remessa com hemacias e plaquetas juntas avalia cada componente")
    void remessaMistaAvaliaCadaComponente() throws Exception {
        String rota = criarRota();
        embarcar(rota, TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
        embarcar(rota, TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.A_POS);
        porEmTransito(rota);

        // 5 graus: correto para hemacias (1 a 6), excursao para plaquetas
        // (20 a 24). Uma faixa unica por remessa erraria aqui.
        registrar(rota, 5.0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dentroDaFaixa").value(false))
                .andExpect(jsonPath("$.alertas.length()").value(1))
                .andExpect(jsonPath("$.alertas[0].componente").value("CONCENTRADO_PLAQUETAS"));
    }

    @Test
    @DisplayName("Leitura que viola os dois componentes gera dois alertas")
    void doisAlertasQuandoNenhumComponenteEstaSeguro() throws Exception {
        String rota = criarRota();
        embarcar(rota, TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
        embarcar(rota, TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.A_POS);
        porEmTransito(rota);

        // 40 graus: fora da faixa de ambos.
        registrar(rota, 40.0)
                .andExpect(jsonPath("$.dentroDaFaixa").value(false))
                .andExpect(jsonPath("$.alertas.length()").value(2));
    }

    @Test
    @DisplayName("Plasma congelado: -20 graus e normal, 0 grau e excursao")
    void faixaNegativaDoPlasma() throws Exception {
        String rota = rotaEmTransitoCom(TipoHemocomponente.PLASMA_FRESCO_CONGELADO);

        registrar(rota, -20.0).andExpect(jsonPath("$.dentroDaFaixa").value(true));
        registrar(rota, 0.0).andExpect(jsonPath("$.dentroDaFaixa").value(false));
    }

    @Test
    @DisplayName("Os limites da faixa sao inclusivos")
    void limitesInclusivos() throws Exception {
        String rota = rotaEmTransitoCom(TipoHemocomponente.CONCENTRADO_HEMACIAS);

        registrar(rota, 1.0).andExpect(jsonPath("$.dentroDaFaixa").value(true));
        registrar(rota, 6.0).andExpect(jsonPath("$.dentroDaFaixa").value(true));
        registrar(rota, 0.9).andExpect(jsonPath("$.dentroDaFaixa").value(false));
        registrar(rota, 6.1).andExpect(jsonPath("$.dentroDaFaixa").value(false));
    }

    @Test
    @DisplayName("O alerta informa quantas bolsas daquele componente estao a bordo")
    void contagemDeBolsasAfetadas() throws Exception {
        String rota = criarRota();
        embarcar(rota, TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_NEG);
        embarcar(rota, TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.A_POS);
        embarcar(rota, TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.B_POS);
        porEmTransito(rota);

        registrar(rota, 5.0)
                .andExpect(jsonPath("$.alertas[0].bolsasAfetadas").value(3));
    }

    @Test
    @DisplayName("Historico devolve as leituras da mais recente para a mais antiga")
    void historicoRegistrado() throws Exception {
        String rota = rotaEmTransitoCom(TipoHemocomponente.CONCENTRADO_HEMACIAS);

        registrar(rota, 3.0);
        registrar(rota, 9.0);

        mvc.perform(get("/api/v1/rotas/" + rota + "/temperaturas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].temperaturaCelsius").value(9.0))
                .andExpect(jsonPath("$[1].temperaturaCelsius").value(3.0));
    }

    @Test
    @DisplayName("Leitura em rota que nao esta em transito e recusada com 409")
    void rotaPlanejadaNaoAceitaLeitura() throws Exception {
        String rota = criarRota();
        embarcar(rota, TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
        // sem por em transito

        registrar(rota, 4.0).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Rota inexistente devolve 404")
    void rotaInexistente() throws Exception {
        registrar("nao-existe", 4.0).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Temperatura fora do plausivel para um sensor e recusada com 400")
    void leituraImplausivelERecusada() throws Exception {
        String rota = rotaEmTransitoCom(TipoHemocomponente.CONCENTRADO_HEMACIAS);

        // Nao e excursao de temperatura: e defeito de sensor.
        registrar(rota, -200.0).andExpect(status().isBadRequest());
        registrar(rota, 500.0).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Rota sem bolsas nao entra em transito, entao nao ha remessa vazia para medir")
    void rotaVaziaNaoEntraEmTransito() throws Exception {
        String rota = criarRota();

        // Descoberto ao escrever este teste: RotaServico recusa a transicao
        // para EM_TRANSITO quando nao ha bolsa embarcada. A regra faz sentido,
        // e tem um efeito util aqui: nao existe leitura de temperatura sobre
        // remessa vazia, porque remessa vazia nao viaja.
        mvc.perform(patch("/api/v1/rotas/" + rota)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AtualizarStatusRotaRequest(
                                StatusRota.EM_TRANSITO, LocalDateTime.now(), null))))
                .andExpect(status().isConflict());
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions registrar(
            String rotaId, double temperatura) throws Exception {
        return mvc.perform(post("/api/v1/rotas/" + rotaId + "/temperaturas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LeituraTemperaturaRequest(temperatura))));
    }

    private String rotaEmTransitoCom(TipoHemocomponente tipo) throws Exception {
        String rota = criarRota();
        embarcar(rota, tipo, GrupoSanguineo.O_NEG);
        porEmTransito(rota);
        return rota;
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
        String bolsa = idDe(mvc.perform(post("/api/v1/bolsas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new BolsaRequest(
                                hemocentroId, tipo, grupo, 450, LocalDate.now().minusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        // So bolsa RESERVADA embarca: a regra existe para impedir que uma
        // bolsa disponivel no estoque saia em viagem sem destino definido.
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
        return new EnderecoDto("Rua da Telemetria", "30", "Derby", "Recife", "PE",
                "50000-200", -8.07, -34.89);
    }
}
