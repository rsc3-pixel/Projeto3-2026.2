package com.rotavital.api;

import com.rotavital.api.dto.AtualizarStatusBolsaRequest;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.EnderecoDto;
import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.servico.IndiceEstoqueServico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PI3-131: o indice de estoque por hash e consultado no caminho real de
 * alocacao.
 *
 * <p>Antes desta subtarefa o {@code IndiceEstoque} existia e era testado, mas
 * nenhum servico o usava: o {@code grep -rn IndiceEstoque src/main/java}
 * respondia apenas a propria classe e a {@code SelecaoFefo}. A alocacao real
 * recebia o id da bolsa escolhido pelo usuario e apenas validava.</p>
 *
 * <p>A integracao foi feita criando a alocacao automatica, em
 * {@code POST /alocacoes/automatica}, que consulta o indice, filtra por
 * compatibilidade ABO/Rh e escolhe por FEFO. Os testes abaixo provam que o
 * indice e realmente consultado, que ele acompanha as mudancas de status da
 * bolsa e que uma transacao revertida nao o corrompe.</p>
 *
 * <p>Os testes nao sao {@code @Transactional}: o indice so e atualizado
 * depois do commit, entao uma transacao de teste que nunca commita deixaria
 * os ouvintes sem disparar. A limpeza entre testes e feita recriando o
 * cenario com ids proprios e recarregando o indice.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AlocacaoAutomaticaTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    @MockitoSpyBean
    private IndiceEstoqueServico indiceEstoque;

    @Autowired
    private com.rotavital.repositorio.BolsaRepository bolsas;

    @Autowired
    private com.rotavital.repositorio.RequisicaoRepository requisicoes;

    @Autowired
    private com.rotavital.repositorio.RotaRepository rotas;

    private MockMvc mvc;
    private String hemocentroId;
    private String hospitalId;

    @BeforeEach
    void prepararCenario() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();

        // Estes testes nao sao @Transactional de proposito: o indice so e
        // atualizado depois do commit, e uma transacao de teste que nunca
        // commita deixaria os ouvintes sem disparar. Como nada e revertido
        // automaticamente, a limpeza e explicita - sem ela, bolsas de testes
        // anteriores continuariam disponiveis e entrariam no indice na
        // recarga, quebrando os cenarios de "sem estoque".
        rotas.deleteAll();
        requisicoes.deleteAll();
        bolsas.deleteAll();

        hemocentroId = idDe(mvc.perform(post("/api/v1/hemocentros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HemocentroRequest(
                                "Hemocentro do Indice " + SEQUENCIA.get(), "(81) 3000-5000",
                                cnpj(), endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        hospitalId = idDe(mvc.perform(post("/api/v1/hospitais")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new HospitalRequest(
                                "Hospital do Indice " + SEQUENCIA.get(), "(81) 3000-6000",
                                cnes(), endereco()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        // Recarrega a partir do banco ja limpo: o indice parte vazio.
        indiceEstoque.carregar();
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("O indice e consultado no caminho real de alocacao")
    class ConsultaNoCaminhoReal {

        @Test
        @DisplayName("A alocacao automatica chama buscarCompativeis do indice")
        void alocacaoAutomaticaConsultaOIndice() throws Exception {
            String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 10);
            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.A_POS, 1);

            alocarAutomatico(requisicao, 201)
                    .andExpect(jsonPath("$.bolsaId").value(bolsa));

            // A prova direta: o caminho real passou pelo indice.
            verify(indiceEstoque, atLeastOnce()).buscarCompativeis(
                    eq(GrupoSanguineo.A_POS), eq(TipoHemocomponente.CONCENTRADO_HEMACIAS));
        }

        /**
         * Prova indireta e mais forte: a bolsa existe no banco e esta
         * DISPONIVEL, mas foi retirada do indice. Se a alocacao varresse o
         * banco, ela seria escolhida; como consulta o indice, nao e.
         */
        @Test
        @DisplayName("Bolsa fora do indice nao e escolhida, mesmo existindo e disponivel no banco")
        void bolsaForaDoIndiceNaoEhEscolhida() throws Exception {
            String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 10);

            // Confirma que ela esta no banco e disponivel.
            mvc.perform(get("/api/v1/bolsas/" + bolsa))
                    .andExpect(jsonPath("$.status").value("DISPONIVEL"));

            // Tira do indice sem tocar no banco.
            indiceEstoque.retirar(bolsaDoBanco(bolsa));

            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.A_POS, 1);

            alocarAutomatico(requisicao, 409);

            mvc.perform(get("/api/v1/bolsas/" + bolsa))
                    .andExpect(jsonPath("$.status").value("DISPONIVEL"));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("FEFO e compatibilidade na escolha automatica")
    class EscolhaAutomatica {

        @Test
        @DisplayName("Entre compativeis, sai a de validade mais proxima, ainda que de outro grupo")
        void escolheAMaisProximaDoVencimento() throws Exception {
            // O- vence antes; A+ vence depois. As duas servem a um paciente A+.
            String venceAntes = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 5);
            String venceDepois = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.A_POS, 30);

            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.A_POS, 1);

            alocarAutomatico(requisicao, 201)
                    .andExpect(jsonPath("$.bolsaId").value(venceAntes));

            mvc.perform(get("/api/v1/bolsas/" + venceDepois))
                    .andExpect(jsonPath("$.status").value("DISPONIVEL"));
        }

        @Test
        @DisplayName("Bolsa incompativel nunca e escolhida: A+ nao serve a paciente O-")
        void incompativelNuncaEhEscolhida() throws Exception {
            criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS, 5);

            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 1);

            alocarAutomatico(requisicao, 409);
        }

        @Test
        @DisplayName("Sem estoque devolve 409 e a requisicao continua aberta")
        void semEstoqueDevolve409() throws Exception {
            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CRIOPRECIPITADO,
                    GrupoSanguineo.AB_NEG, 1);
            String requisicaoId = requisicao.get("id").asString();

            alocarAutomatico(requisicao, 409);

            mvc.perform(get("/api/v1/requisicoes/" + requisicaoId))
                    .andExpect(jsonPath("$.status").value("PENDENTE"))
                    .andExpect(jsonPath("$.itens[0].quantidadeAlocada").value(0));
        }

        @Test
        @DisplayName("Duas chamadas no mesmo item escolhem bolsas diferentes")
        void duasChamadasEscolhemBolsasDiferentes() throws Exception {
            criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 5);
            criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 20);

            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.A_POS, 2);
            String requisicaoId = requisicao.get("id").asString();

            String primeira = json.readTree(alocarAutomatico(requisicao, 201)
                    .andReturn().getResponse().getContentAsString()).get("bolsaId").asString();
            String segunda = json.readTree(alocarAutomatico(requisicao, 201)
                    .andReturn().getResponse().getContentAsString()).get("bolsaId").asString();

            assertNotEquals(primeira, segunda,
                    "a primeira saiu do indice ao ficar RESERVADA");

            mvc.perform(get("/api/v1/requisicoes/" + requisicaoId))
                    .andExpect(jsonPath("$.status").value("ATENDIDA"))
                    .andExpect(jsonPath("$.itens[0].quantidadeAlocada").value(2));
        }
    }

    // ------------------------------------------------------------------
    @Nested
    @DisplayName("O indice acompanha a mudanca de status da bolsa")
    class IndiceAcompanhaStatus {

        @Test
        @DisplayName("Criar bolsa indexa; alocar retira; desfazer alocacao devolve")
        void criarAlocarEDesfazer() throws Exception {
            String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 10);

            assertTrue(estaNoIndice(bolsa, GrupoSanguineo.O_NEG),
                    "bolsa recem-criada entra no indice");

            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.A_POS, 1);
            String requisicaoId = requisicao.get("id").asString();
            String itemId = requisicao.get("itens").get(0).get("id").asString();

            String alocacaoId = json.readTree(alocarAutomatico(requisicao, 201)
                    .andReturn().getResponse().getContentAsString()).get("id").asString();

            assertTrue(!estaNoIndice(bolsa, GrupoSanguineo.O_NEG),
                    "RESERVADA sai do indice");

            mvc.perform(delete("/api/v1/requisicoes/" + requisicaoId
                            + "/itens/" + itemId + "/alocacoes/" + alocacaoId))
                    .andExpect(status().isNoContent());

            assertTrue(estaNoIndice(bolsa, GrupoSanguineo.O_NEG),
                    "ao voltar para DISPONIVEL, volta ao indice");
        }

        @Test
        @DisplayName("Cancelar a requisicao devolve a bolsa ao indice")
        void cancelarRequisicaoDevolveAoIndice() throws Exception {
            String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 10);
            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.A_POS, 1);
            String requisicaoId = requisicao.get("id").asString();

            alocarAutomatico(requisicao, 201);
            assertTrue(!estaNoIndice(bolsa, GrupoSanguineo.O_NEG));

            mvc.perform(patch("/api/v1/requisicoes/" + requisicaoId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"CANCELADA\"}"))
                    .andExpect(status().isOk());

            assertTrue(estaNoIndice(bolsa, GrupoSanguineo.O_NEG),
                    "cancelamento libera a bolsa e ela volta ao indice");
        }

        @Test
        @DisplayName("PATCH de status para DESCARTADA tira a bolsa do indice")
        void descartarTiraDoIndice() throws Exception {
            String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 10);
            assertTrue(estaNoIndice(bolsa, GrupoSanguineo.O_NEG));

            mvc.perform(patch("/api/v1/bolsas/" + bolsa)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(
                                    new AtualizarStatusBolsaRequest(StatusBolsa.DESCARTADA))))
                    .andExpect(status().isOk());

            assertTrue(!estaNoIndice(bolsa, GrupoSanguineo.O_NEG));
        }

        @Test
        @DisplayName("DELETE da bolsa tira do indice")
        void removerTiraDoIndice() throws Exception {
            String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.B_NEG, 10);
            assertTrue(estaNoIndice(bolsa, GrupoSanguineo.B_NEG));

            mvc.perform(delete("/api/v1/bolsas/" + bolsa))
                    .andExpect(status().isNoContent());

            assertTrue(!estaNoIndice(bolsa, GrupoSanguineo.B_NEG));
        }

        /**
         * Uma alocacao recusada por validacao reverte a transacao. O indice e
         * atualizado apenas depois do commit, entao a bolsa nao pode ter
         * saido dele.
         */
        @Test
        @DisplayName("Transacao revertida nao altera o indice")
        void rollbackNaoAlteraOIndice() throws Exception {
            String bolsa = criarBolsa(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.A_POS, 10);
            assertTrue(estaNoIndice(bolsa, GrupoSanguineo.A_POS));

            // Paciente O- nao pode receber A+: a alocacao manual falha com 409.
            JsonNode requisicao = criarRequisicao(TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 1);
            String requisicaoId = requisicao.get("id").asString();
            String itemId = requisicao.get("itens").get(0).get("id").asString();

            mvc.perform(post("/api/v1/requisicoes/" + requisicaoId
                            + "/itens/" + itemId + "/alocacoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"bolsaId\":\"" + bolsa + "\"}"))
                    .andExpect(status().isConflict());

            assertTrue(estaNoIndice(bolsa, GrupoSanguineo.A_POS),
                    "a transacao reverteu, entao o indice nao pode ter mudado");
            mvc.perform(get("/api/v1/bolsas/" + bolsa))
                    .andExpect(jsonPath("$.status").value("DISPONIVEL"));
        }
    }

    // -----------------------------------------------------------------------
    // Apoio
    // -----------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions alocarAutomatico(
            JsonNode requisicao, int statusEsperado) throws Exception {
        String requisicaoId = requisicao.get("id").asString();
        String itemId = requisicao.get("itens").get(0).get("id").asString();

        var resultado = mvc.perform(post("/api/v1/requisicoes/" + requisicaoId
                + "/itens/" + itemId + "/alocacoes/automatica"));

        return statusEsperado == 201
                ? resultado.andExpect(status().isCreated())
                : resultado.andExpect(status().isConflict());
    }

    /** O indice guarda a bolsa sob o grupo dela, nao sob o do receptor. */
    private boolean estaNoIndice(String codigoBolsa, GrupoSanguineo grupoDaBolsa) {
        return indiceEstoque.buscarExato(grupoDaBolsa, TipoHemocomponente.CONCENTRADO_HEMACIAS)
                .stream()
                .anyMatch(b -> b.getCodigoRastreio().equals(codigoBolsa));
    }

    private com.rotavital.dominio.Bolsa bolsaDoBanco(String codigo) {
        return indiceEstoque.buscarExato(GrupoSanguineo.O_NEG,
                        TipoHemocomponente.CONCENTRADO_HEMACIAS).stream()
                .filter(b -> b.getCodigoRastreio().equals(codigo))
                .findFirst()
                .orElseThrow();
    }

    private String criarBolsa(TipoHemocomponente tipo, GrupoSanguineo grupo,
                              int diasAteVencer) throws Exception {
        // A validade e dataColeta + validade do tipo; a coleta e calculada de
        // tras para frente para fixar quantos dias faltam para vencer.
        LocalDate coleta = LocalDate.now().plusDays(diasAteVencer)
                .minusDays(tipo.getValidadeDias());

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

    private String idDe(String respostaJson) {
        return json.readTree(respostaJson).get("id").asString();
    }

    /**
     * Documento unico por chamada. Um recorte de System.nanoTime nao serve:
     * os primeiros digitos mal mudam dentro de uma execucao e os cadastros
     * colidiriam com 409.
     */
    private static final java.util.concurrent.atomic.AtomicLong SEQUENCIA =
            new java.util.concurrent.atomic.AtomicLong(System.nanoTime() % 1_000_000L);

    private String cnpj() {
        return String.format("%014d", SEQUENCIA.incrementAndGet());
    }

    private String cnes() {
        return String.format("%07d", SEQUENCIA.incrementAndGet());
    }

    private EnderecoDto endereco() {
        return new EnderecoDto("Rua do Indice", "10", "Boa Vista", "Recife", "PE",
                "50000-200", -8.06, -34.88);
    }
}
