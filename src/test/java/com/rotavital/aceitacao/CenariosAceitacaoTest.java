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
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.Dijkstra;
import com.rotavital.estruturas.Grafo;
import com.rotavital.estruturas.ResultadoRota;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    /**
     * Acesso direto ao banco, usado so para envelhecer uma bolsa (HU-04): a
     * API recusa cadastrar bolsa que ja nasce vencida, mas uma bolsa valida
     * vence com o tempo, e esse estoque e o que o painel precisa mostrar.
     */
    @Autowired
    private BolsaRepository bolsas;

    @Autowired
    private HemocentroRepository hemocentros;

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

        @Test
        @DisplayName("Dado uma coleta de hemacias, Quando eu cadastrar a bolsa, "
                   + "Entao a validade e de 42 dias: cada componente tem o seu prazo")
        void validadeVariaPorComponente() throws Exception {
            // Dado uma coleta de hemacias feita hoje
            LocalDate coleta = LocalDate.now();

            // Quando eu cadastrar a bolsa
            String resposta = mvc.perform(post("/api/v1/bolsas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new BolsaRequest(
                                    hemocentroId, TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                    GrupoSanguineo.A_POS, 450, coleta))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            // Entao a validade e 42 dias depois da coleta, nao os 5 das plaquetas
            assertEquals(coleta.plusDays(42).toString(),
                    json.readTree(resposta).get("dataValidade").asString());
        }

        @Test
        @DisplayName("Dado o cadastro sem o tipo sanguineo, Quando eu tentar salvar, "
                   + "Entao recebo 400 apontando o campo, e a bolsa nao e criada")
        void camposObrigatorios() throws Exception {
            // Dado um cadastro sem o grupo sanguineo
            BolsaRequest semGrupo = new BolsaRequest(
                    hemocentroId, TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    null, 450, LocalDate.now());

            // Quando eu tentar salvar
            // Entao a resposta e legivel: diz qual campo faltou, sem erro tecnico
            mvc.perform(post("/api/v1/bolsas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(semGrupo)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Dados invalidos"))
                    .andExpect(jsonPath("$.campos.grupoSanguineo").exists());

            // E a bolsa nao foi criada: o estoque do hemocentro segue vazio
            mvc.perform(get("/api/v1/hemocentros/" + hemocentroId + "/bolsas"))
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-02 — Solicitar hemocomponentes")
    class SolicitarHemocomponentes {

        @Test
        @DisplayName("Dado um pedido URGENTE com 2 itens, Quando eu criar a requisicao, "
                   + "Entao ela nasce PENDENTE com 2 itens somando 3 bolsas")
        void requisicaoComMultiplosItens() throws Exception {
            // Dado um pedido de 2 hemacias O- e 1 plasma O-
            RequisicaoRequest pedido = new RequisicaoRequest(
                    hospitalId, PrioridadeRequisicao.URGENTE, null, null,
                    List.of(
                            new ItemRequisicaoRequest(
                                    TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 2),
                            new ItemRequisicaoRequest(
                                    TipoHemocomponente.PLASMA_FRESCO_CONGELADO, GrupoSanguineo.O_NEG, 1)));

            // Quando eu criar a requisicao
            String resposta = mvc.perform(post("/api/v1/requisicoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(pedido)))
                    .andExpect(status().isCreated())
                    // Entao ela nasce PENDENTE
                    .andExpect(jsonPath("$.status").value("PENDENTE"))
                    .andExpect(jsonPath("$.itens.length()").value(2))
                    .andReturn().getResponse().getContentAsString();

            // E os 2 itens somam 3 bolsas
            int total = 0;
            for (JsonNode item : json.readTree(resposta).get("itens")) {
                total += item.get("quantidadeSolicitada").asInt();
            }
            assertEquals(3, total);
        }

        @Test
        @DisplayName("Dado uma requisicao sem prazo informado, Quando a prioridade e EMERGENCIA, "
                   + "Entao o prazo e de 2 horas; Quando e ROTINA, Entao e de 72")
        void prazoConformeAPrioridade() throws Exception {
            // Quando eu marcar a prioridade como EMERGENCIA
            // Entao o prazo de atendimento e de 2 horas a partir da criacao
            assertEquals(Duration.ofHours(2), prazoDe(PrioridadeRequisicao.EMERGENCIA));

            // Quando eu marcar a prioridade como ROTINA
            // Entao o prazo de atendimento e de 72 horas
            assertEquals(Duration.ofHours(72), prazoDe(PrioridadeRequisicao.ROTINA));

            // E URGENTE fica no meio, com 12
            assertEquals(Duration.ofHours(12), prazoDe(PrioridadeRequisicao.URGENTE));
        }

        @Test
        @DisplayName("Dado uma requisicao com prazo informado pelo hospital, Quando eu criar, "
                   + "Entao vale o prazo informado, nao o da prioridade")
        void prazoInformadoPrevalece() throws Exception {
            LocalDateTime prazoDoHospital = LocalDateTime.of(2026, 12, 1, 8, 30);

            String resposta = mvc.perform(post("/api/v1/requisicoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RequisicaoRequest(
                                    hospitalId, PrioridadeRequisicao.EMERGENCIA, prazoDoHospital, null,
                                    List.of(new ItemRequisicaoRequest(
                                            TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                            GrupoSanguineo.O_NEG, 1))))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            assertEquals(prazoDoHospital, LocalDateTime.parse(
                    json.readTree(resposta).get("prazoEntrega").asString()));
        }

        @Test
        @DisplayName("Dado uma requisicao sem item algum, Quando eu tentar salvar, "
                   + "Entao recebo 400 apontando o campo itens, e a requisicao nao e criada")
        void requisicaoPrecisaTerItens() throws Exception {
            mvc.perform(post("/api/v1/requisicoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RequisicaoRequest(
                                    hospitalId, PrioridadeRequisicao.ROTINA, null, null, List.of()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.itens")
                            .value("a requisicao precisa de pelo menos um item"));

            mvc.perform(get("/api/v1/hospitais/" + hospitalId + "/requisicoes"))
                    .andExpect(jsonPath("$").isEmpty());
        }

        /** Cria uma requisicao sem prazo e devolve o intervalo entre a criacao e o prazo calculado. */
        private Duration prazoDe(PrioridadeRequisicao prioridade) throws Exception {
            String resposta = mvc.perform(post("/api/v1/requisicoes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RequisicaoRequest(
                                    hospitalId, prioridade, null, null,
                                    List.of(new ItemRequisicaoRequest(
                                            TipoHemocomponente.CONCENTRADO_HEMACIAS,
                                            GrupoSanguineo.O_NEG, 1))))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            JsonNode requisicao = json.readTree(resposta);
            LocalDateTime criacao = LocalDateTime.parse(requisicao.get("dataHoraCriacao").asString());
            LocalDateTime prazo = LocalDateTime.parse(requisicao.get("prazoEntrega").asString());
            return Duration.between(criacao, prazo);
        }

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
    @DisplayName("HU-04 — Ver bolsas em risco de vencimento")
    class BolsasEmRisco {

        @Test
        @DisplayName("Dado bolsas vencendo em 3, 10 e 40 dias, Quando eu abrir o painel com filtro de 7, "
                   + "Entao vejo apenas a que vence em 3 dias")
        void painelMostraApenasOQueEstaEmRisco() throws Exception {
            // Dado o estoque com bolsas vencendo em 3, 10 e 40 dias
            String tresDias = criarBolsaQueVenceEm(3);
            criarBolsaQueVenceEm(10);
            criarBolsaQueVenceEm(40);

            // Quando eu abrir o painel de estoque critico com filtro de 7 dias
            // Entao vejo apenas a bolsa que vence em 3 dias
            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalEmRisco").value(1))
                    .andExpect(jsonPath("$.emRisco[0].id").value(tresDias));
        }

        @Test
        @DisplayName("Dado bolsas vencendo em 6, 2 e 4 dias, Quando eu abrir o painel, "
                   + "Entao elas aparecem na ordem 2, 4 e 6")
        void ordenacaoPorUrgencia() throws Exception {
            // Dado bolsas cadastradas fora de ordem
            criarBolsaQueVenceEm(6);
            criarBolsaQueVenceEm(2);
            criarBolsaQueVenceEm(4);

            // Quando eu abrir o painel de estoque critico
            // Entao a ordem e da mais critica para a menos
            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.emRisco[0].diasRestantes").value(2))
                    .andExpect(jsonPath("$.emRisco[1].diasRestantes").value(4))
                    .andExpect(jsonPath("$.emRisco[2].diasRestantes").value(6));
        }

        @Test
        @DisplayName("Dado que nenhuma bolsa vence nos proximos 7 dias, Quando eu abrir o painel, "
                   + "Entao vejo a mensagem 'Nenhuma bolsa em risco nos proximos 7 dias'")
        void estoqueSaudavel() throws Exception {
            // Dado um estoque em que tudo vence depois de 7 dias
            criarBolsaQueVenceEm(30);

            // Quando eu abrir o painel
            // Entao o estado vazio tem mensagem propria, nao tabela em branco
            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.emRisco").isEmpty())
                    .andExpect(jsonPath("$.mensagem").value("Nenhuma bolsa em risco nos proximos 7 dias"));
        }

        @Test
        @DisplayName("Dado uma bolsa vencida ainda em estoque, Quando eu abrir o painel, "
                   + "Entao ela aparece separada das que ainda podem ser usadas")
        void vencidasSeparadas() throws Exception {
            // Dado uma bolsa valida que vence em 3 dias e uma que venceu ha 2
            String valida = criarBolsaQueVenceEm(3);
            String vencida = envelhecerBolsaAteVencerHa(2);

            // Quando eu abrir o painel
            // Entao cada uma esta na sua lista
            mvc.perform(get("/api/v1/bolsas/em-risco")
                            .param("dias", "7")
                            .param("hemocentroId", hemocentroId))
                    .andExpect(jsonPath("$.emRisco.length()").value(1))
                    .andExpect(jsonPath("$.emRisco[0].id").value(valida))
                    .andExpect(jsonPath("$.vencidas.length()").value(1))
                    .andExpect(jsonPath("$.vencidas[0].id").value(vencida));
        }
    }

    // =======================================================================
    @Nested
    @DisplayName("HU-05 — Calcular rota de entrega")
    class CalcularRota {

        @Test
        @DisplayName("Dado a rede de trechos da historia, Quando eu calcular a rota do Hemocentro ao Destino, "
                   + "Entao o caminho e Hemocentro > A > B > Destino em 23 minutos")
        void caminhoMaisRapidoNaoOMaisCurto() throws Exception {
            // Dado que a rede tem os trechos da tabela da historia, em minutos
            Grafo<String> rede = new Grafo<>();
            rede.inserirAresta("Hemocentro", "Hospital A", 10);
            rede.inserirAresta("Hemocentro", "Hospital B", 20);
            rede.inserirAresta("Hospital A", "Hospital B", 5);
            rede.inserirAresta("Hospital B", "Destino", 8);

            // Quando eu calcular a rota do Hemocentro ate o Destino
            ResultadoRota<String> rota = new Dijkstra<>(rede).calcularRota("Hemocentro", "Destino");

            // Entao a rota passa por A e B, e nao pela ligacao direta ao B
            assertEquals(List.of("Hemocentro", "Hospital A", "Hospital B", "Destino"), rota.caminho());
            // E o tempo estimado e de 23 minutos: o peso e tempo, nao distancia
            assertEquals(23.0, rota.custoTotal());
        }

        @Test
        @DisplayName("Dado um hospital sem trecho ate o hemocentro, Quando eu calcular a rota, "
                   + "Entao vejo a mensagem 'Nao ha rota disponivel para este destino'")
        void destinoInalcancavel() throws Exception {
            // Dado um hospital a mais de 2.000 km de qualquer unidade da malha
            String hospitalX = idDe(mvc.perform(post("/api/v1/hospitais")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new HospitalRequest(
                                    "Hospital X", "(11) 3000-0000", "1010102",
                                    new EnderecoDto("Avenida Paulista", "1000", "Bela Vista",
                                            "Sao Paulo", "SP", "01310-100", -23.5614, -46.6559)))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());

            // Quando eu calcular a rota do Hemocentro ate o Hospital X
            // Entao a resposta e tratada, nao um erro: 200 com a mensagem
            mvc.perform(get("/api/v1/rotas/calcular")
                            .param("origemId", hemocentroId)
                            .param("destinoId", hospitalX))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alcancavel").value(false))
                    .andExpect(jsonPath("$.mensagem").value("Nao ha rota disponivel para este destino"));
        }

        @Test
        @DisplayName("Dado que calculei uma rota, Quando eu confirmar o despacho, "
                   + "Entao a rota nasce PLANEJADA com previsao de saida e chegada, "
                   + "e as bolsas embarcadas mudam para EM_TRANSITO ao sair")
        void rotaRegistraOrigemDestinoEPrevisao() throws Exception {
            // Dado que calculei a rota do hemocentro ao hospital
            String calculo = mvc.perform(get("/api/v1/rotas/calcular")
                            .param("origemId", hemocentroId)
                            .param("destinoId", hospitalId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alcancavel").value(true))
                    .andReturn().getResponse().getContentAsString();
            double tempo = json.readTree(calculo).get("tempoMinutos").asDouble();

            // Quando eu confirmar o despacho, sem informar a chegada
            LocalDateTime saida = LocalDateTime.now().plusHours(1);
            String resposta = mvc.perform(post("/api/v1/rotas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new RotaRequest(
                                    hemocentroId, hospitalId, saida, null))))
                    .andExpect(status().isCreated())
                    // Entao a rota e criada PLANEJADA
                    .andExpect(jsonPath("$.status").value("PLANEJADA"))
                    .andReturn().getResponse().getContentAsString();

            // E contem a previsao de saida e de chegada, esta calculada pelo caminho
            JsonNode rota = json.readTree(resposta);
            LocalDateTime chegada = LocalDateTime.parse(rota.get("previsaoChegada").asString());
            assertTrue(chegada.isAfter(saida), "a chegada vem depois da saida");
            assertEquals(tempo, rota.get("tempoEstimadoMinutos").asDouble());
            assertFalse(rota.get("caminho").isEmpty(), "o caminho fica registrado na rota");

            // E as bolsas embarcadas mudam para EM_TRANSITO quando a rota sai
            String rotaId = rota.get("id").asString();
            embarcar(rotaId, TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG);
            porEmTransito(rotaId);
            mvc.perform(get("/api/v1/rotas/" + rotaId))
                    .andExpect(jsonPath("$.status").value("EM_TRANSITO"))
                    .andExpect(jsonPath("$.bolsas[0].status").value("EM_TRANSITO"));
        }

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
                                    new LeituraTemperaturaRequest(5.0, null, null))))
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

    /** Bolsa de hemacias (42 dias) coletada no dia exato para vencer daqui a {@code dias}. */
    private String criarBolsaQueVenceEm(int dias) throws Exception {
        TipoHemocomponente tipo = TipoHemocomponente.CONCENTRADO_HEMACIAS;
        return criarBolsaComColeta(tipo, GrupoSanguineo.O_POS,
                LocalDate.now().plusDays(dias).minusDays(tipo.getValidadeDias()));
    }

    /**
     * Grava direto no banco uma bolsa que venceu ha {@code dias}: a API nao
     * aceita cadastrar bolsa ja vencida, mas o estoque envelhece.
     */
    private String envelhecerBolsaAteVencerHa(int dias) {
        TipoHemocomponente tipo = TipoHemocomponente.CONCENTRADO_PLAQUETAS;
        Hemocentro hemocentro = hemocentros.findById(hemocentroId).orElseThrow();
        Bolsa vencida = new Bolsa("ACEIT-VENCIDA-" + dias, tipo, GrupoSanguineo.O_POS, 300,
                LocalDate.now().minusDays(dias).minusDays(tipo.getValidadeDias()), hemocentro);
        return bolsas.save(vencida).getCodigoRastreio();
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
