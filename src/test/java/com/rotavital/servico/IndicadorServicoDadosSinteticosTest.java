package com.rotavital.servico;

import com.rotavital.api.dto.indicadores.CoberturaResponse;
import com.rotavital.api.dto.indicadores.DesabastecimentoResponse;
import com.rotavital.api.dto.indicadores.DesabastecimentoResponse.DesabastecimentoTipoDto;
import com.rotavital.api.dto.indicadores.DescarteComponenteResponse;
import com.rotavital.api.dto.indicadores.DescartePeriodoResponse;
import com.rotavital.api.dto.indicadores.PeriodoDescarte;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.RequisicaoRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;

/**
 * Prova que o backend reproduz a analise em Python sobre o dataset oficial do projeto.
 *
 * <p>O banco da aplicacao sobe com uma carga pequena ({@code CargaInicial}), diferente das 2.000
 * bolsas de {@code dados/dados_sinteticos.json}. Para comparar numero a numero com
 * {@code dados/analise.py} e {@code dados/validar_indicadores_u2.py}, este teste carrega o dataset
 * oficial no servico por meio dos repositorios simulados e confere com o esperado gravado por esse
 * script ({@code dados/indicadores_u2_esperado.json}). Toda data e fixa: a referencia e 17/09/2026,
 * implicita no dataset.</p>
 */
@ExtendWith(MockitoExtension.class)
class IndicadorServicoDadosSinteticosTest {

    private static final LocalDate REFERENCIA = LocalDate.of(2026, 9, 17);
    private static final double TOLERANCIA = 1e-9;

    private static JsonNode esperado;
    private static JsonNode resultadosU1;
    private static JsonNode painelCpp;
    private static List<Bolsa> bolsas;
    private static List<Requisicao> requisicoes;

    @Mock
    private BolsaRepository bolsaRepository;

    @Mock
    private RequisicaoRepository requisicaoRepository;

    @InjectMocks
    private IndicadorServico indicadorServico;

    @BeforeAll
    static void carregarDataset() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode base = mapper.readTree(Path.of("dados", "dados_sinteticos.json").toFile());
        esperado = mapper.readTree(Path.of("dados", "indicadores_u2_esperado.json").toFile());
        resultadosU1 = mapper.readTree(Path.of("dados", "resultados.json").toFile());
        painelCpp = mapper.readTree(Path.of("dados", "painel_consolidado_pi3_32.json").toFile());

        Hemocentro hemocentro = new Hemocentro("HC01", "Hemocentro", "0", null, "0");
        Hospital hospital = new Hospital("HO01", "Hospital", "0", null, "0");

        bolsas = new ArrayList<>();
        for (JsonNode b : base.get("bolsas")) {
            Bolsa bolsa = new Bolsa(
                    b.get("codigo").asText(),
                    TipoHemocomponente.valueOf(b.get("componente").asText()),
                    GrupoSanguineo.valueOf(b.get("tipo_sanguineo").asText()),
                    b.get("volume_ml").asInt(),
                    LocalDate.parse(b.get("data_coleta").asText()),
                    hemocentro);
            // A entidade deriva a validade da coleta; se nao bater com o dataset, nada abaixo vale.
            assertThat(bolsa.getDataValidade()).isEqualTo(LocalDate.parse(b.get("data_validade").asText()));
            assertThat(bolsa.estaVencida(REFERENCIA)).isEqualTo(b.get("vencida").asBoolean());
            bolsas.add(bolsa);
        }

        requisicoes = new ArrayList<>();
        for (JsonNode r : base.get("requisicoes")) {
            LocalDateTime criacao = LocalDate.parse(r.get("data").asText()).atStartOfDay();
            Requisicao requisicao = new Requisicao(
                    r.get("id").asText(),
                    hospital,
                    PrioridadeRequisicao.valueOf(r.get("prioridade").asText()),
                    criacao,
                    criacao.plusHours(r.get("prazo_horas").asInt()));
            for (JsonNode item : r.get("itens")) {
                requisicao.adicionarItem(new ItemRequisicao(
                        item.get("id").asText(),
                        TipoHemocomponente.valueOf(item.get("componente").asText()),
                        GrupoSanguineo.valueOf(item.get("tipo_sanguineo").asText()),
                        item.get("quantidade").asInt()));
            }
            requisicoes.add(requisicao);
        }
    }

    @BeforeEach
    void prepararRepositorios() {
        lenient().when(bolsaRepository.findAllComHemocentro()).thenReturn(bolsas);
        lenient().when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(requisicoes);
    }

    @Test
    @DisplayName("O dataset oficial entra inteiro: 2.000 bolsas e 300 requisicoes")
    void datasetCarregadoInteiro() {
        assertThat(bolsas).hasSize(esperado.get("bolsas").asInt()).hasSize(2000);
        assertThat(requisicoes).hasSize(esperado.get("requisicoes").asInt()).hasSize(300);
        assertThat(esperado.get("data_referencia").asText()).isEqualTo(REFERENCIA.toString());
    }

    @Test
    @DisplayName("Taxa de descarte e descarte evitavel pela FEFO batem com o Python, componente a componente")
    void descartePorComponenteBateComPython() {
        List<DescarteComponenteResponse> java = indicadorServico.taxaDescartePorComponente(REFERENCIA);

        for (JsonNode e : esperado.get("descarte_por_componente")) {
            DescarteComponenteResponse c = componente(java, e.get("componente").asText());
            assertThat(c.total()).as("total %s", c.tipoHemocomponente()).isEqualTo(e.get("total").asLong());
            assertThat(c.vencidas()).as("vencidas %s", c.tipoHemocomponente()).isEqualTo(e.get("vencidas").asLong());
            assertThat(c.taxaDescarte()).as("taxa %s", c.tipoHemocomponente())
                    .isCloseTo(e.get("taxa").asDouble(), within(TOLERANCIA));
            assertThat(c.descarteEvitavelFefo()).as("evitavel %s", c.tipoHemocomponente())
                    .isEqualTo(e.get("evitavel_fefo").asLong());
            assertThat(c.percentualEvitavelFefo()).as("% evitavel %s", c.tipoHemocomponente())
                    .isCloseTo(e.get("pct_evitavel").asDouble(), within(TOLERANCIA));
        }
        // Os numeros da U1 continuam os mesmos da analise original (HU-06, cenario 3)
        DescarteComponenteResponse plaquetas = componente(java, "CONCENTRADO_PLAQUETAS");
        assertThat(plaquetas.total()).isEqualTo(407);
        assertThat(plaquetas.vencidas()).isEqualTo(367);
        assertThat(plaquetas.taxaDescarte()).isEqualTo(90.2);
        assertThat(java.get(0).tipoHemocomponente()).isEqualTo(TipoHemocomponente.CONCENTRADO_PLAQUETAS);
    }

    @Test
    @DisplayName("Descarte por semana ISO e por mes de coleta bate com o Python, periodo a periodo")
    void descartePorPeriodoBateComPython() {
        for (PeriodoDescarte granularidade : PeriodoDescarte.values()) {
            List<DescartePeriodoResponse> java = indicadorServico.taxaDescartePorPeriodo(REFERENCIA, granularidade);
            JsonNode esperados = esperado.get("descarte_por_periodo").get(granularidade.name());

            assertThat(java).as("quantidade de periodos %s", granularidade).hasSize(esperados.size());
            int posicao = 0;
            for (JsonNode e : esperados) {
                DescartePeriodoResponse p = java.get(posicao++);
                String rotulo = e.get("periodo").asText();
                assertThat(p.periodo()).isEqualTo(rotulo);
                assertThat(p.inicio()).as("inicio %s", rotulo).isEqualTo(LocalDate.parse(e.get("inicio").asText()));
                assertThat(p.fim()).as("fim %s", rotulo).isEqualTo(LocalDate.parse(e.get("fim").asText()));
                assertThat(p.total()).as("total %s", rotulo).isEqualTo(e.get("total").asLong());
                assertThat(p.vencidas()).as("vencidas %s", rotulo).isEqualTo(e.get("vencidas").asLong());
                assertThat(p.taxaDescarte()).as("taxa %s", rotulo).isCloseTo(e.get("taxa").asDouble(), within(TOLERANCIA));

                for (JsonNode ec : e.get("por_componente")) {
                    DescarteComponenteResponse c = componente(p.porComponente(), ec.get("componente").asText());
                    assertThat(c.total()).as("%s total %s", rotulo, c.tipoHemocomponente()).isEqualTo(ec.get("total").asLong());
                    assertThat(c.vencidas()).as("%s vencidas %s", rotulo, c.tipoHemocomponente()).isEqualTo(ec.get("vencidas").asLong());
                    assertThat(c.descarteEvitavelFefo()).as("%s evitavel %s", rotulo, c.tipoHemocomponente())
                            .isEqualTo(ec.get("evitavel_fefo").asLong());
                }
            }
        }
    }

    @Test
    @DisplayName("Probabilidade de desabastecimento bate com o Python em todos os campos e na ordem de risco")
    void desabastecimentoBateComPython() {
        DesabastecimentoResponse java = indicadorServico.probabilidadeDesabastecimento(REFERENCIA);
        JsonNode e = esperado.get("desabastecimento");

        assertThat(java.inicioDemanda()).isEqualTo(LocalDate.parse(e.get("inicio").asText()));
        assertThat(java.fimDemanda()).isEqualTo(LocalDate.parse(e.get("fim").asText()));
        assertThat(java.diasObservados()).isEqualTo(e.get("dias_observados").asInt()).isEqualTo(61);

        List<String> ordemJava = java.porTipo().stream().map(t -> t.grupoSanguineo().name()).toList();
        List<String> ordemPython = new ArrayList<>();
        for (JsonNode et : e.get("por_tipo")) {
            ordemPython.add(et.get("tipo").asText());
            DesabastecimentoTipoDto t = tipo(java, et.get("tipo").asText());
            String g = t.grupoSanguineo().name();
            assertThat(t.estoqueValido()).as("estoque %s", g).isEqualTo(et.get("estoque_valido").asLong());
            assertThat(t.demandaEsperada()).as("demanda %s", g).isEqualTo(et.get("demanda").asLong());
            assertThat(t.varianciaDemanda()).as("variancia %s", g).isCloseTo(et.get("variancia").asDouble(), within(TOLERANCIA));
            assertThat(t.indiceDispersao()).as("indice %s", g).isCloseTo(et.get("indice_dispersao").asDouble(), within(TOLERANCIA));
            assertThat(t.coberturaPercentual()).as("cobertura %s", g).isCloseTo(et.get("cobertura").asDouble(), within(TOLERANCIA));
            assertThat(t.deficit()).as("deficit %s", g).isEqualTo(et.get("deficit").asLong());
            assertThat(t.probabilidadePercentual()).as("P(BN) %s", g).isCloseTo(et.get("probabilidade").asDouble(), within(TOLERANCIA));
            assertThat(t.probabilidadePoissonPercentual()).as("P(Poisson) %s", g)
                    .isCloseTo(et.get("probabilidade_poisson").asDouble(), within(TOLERANCIA));
            assertThat(t.nivelRisco()).as("risco %s", g).isEqualTo(et.get("risco").asText());
        }
        assertThat(ordemJava).isEqualTo(ordemPython);

        // Em todos os tipos a variancia supera a media: a Poisson nao descreve esta demanda
        assertThat(java.porTipo()).allSatisfy(t -> assertThat(t.indiceDispersao()).isGreaterThan(2.0));
    }

    @Test
    @DisplayName("Binomial Negativa em Java bate com o modulo C++ do PI3-102 a uma casa decimal")
    void binomialNegativaBateComModuloCpp() {
        DesabastecimentoResponse java = indicadorServico.probabilidadeDesabastecimento(REFERENCIA);

        for (JsonNode c : painelCpp.get("painel_consolidado").get("desabastecimento_por_tipo")) {
            DesabastecimentoTipoDto t = tipo(java, c.get("tipo_sanguineo").asText());
            double percentualCpp = c.get("probabilidade_desabastecimento").asDouble() * 100.0;
            assertThat(t.probabilidadePercentual()).as("P(BN) %s", t.grupoSanguineo())
                    .isCloseTo(percentualCpp, within(0.11));
            assertThat(t.estoqueValido()).isEqualTo(c.get("estoque_valido_disponivel").asLong());
            assertThat(t.demandaEsperada()).isEqualTo(c.get("demanda_esperada").asLong());
            assertThat(t.nivelRisco()).isEqualTo(c.get("nivel_risco").asText());
        }
    }

    @Test
    @DisplayName("Cobertura por tipo continua igual a analise original da U1 (dados/resultados.json)")
    void coberturaBateComAnaliseOriginalDaU1() {
        List<CoberturaResponse> java = indicadorServico.coberturaPorTipo(REFERENCIA);
        JsonNode cobertura = resultadosU1.get("cobertura");

        for (GrupoSanguineo g : GrupoSanguineo.values()) {
            JsonNode e = cobertura.get(g.name());
            CoberturaResponse c = java.stream().filter(x -> x.grupoSanguineo() == g).findFirst().orElseThrow();
            assertThat(c.disponivel()).as("disponivel %s", g).isEqualTo(e.get("disponivel").asLong());
            assertThat(c.demandado()).as("demandado %s", g).isEqualTo(e.get("demandado").asLong());
            assertThat(c.coberturaPercentual()).as("cobertura %s", g).isCloseTo(e.get("cobertura").asDouble(), within(TOLERANCIA));
            assertThat(c.deficit()).as("deficit %s", g).isEqualTo(e.get("deficit").asLong());
        }
    }

    private static DescarteComponenteResponse componente(List<DescarteComponenteResponse> lista, String nome) {
        TipoHemocomponente tipo = TipoHemocomponente.valueOf(nome);
        return lista.stream().filter(c -> c.tipoHemocomponente() == tipo).findFirst().orElseThrow();
    }

    private static DesabastecimentoTipoDto tipo(DesabastecimentoResponse resposta, String nome) {
        GrupoSanguineo grupo = GrupoSanguineo.valueOf(nome);
        return resposta.porTipo().stream().filter(t -> t.grupoSanguineo() == grupo).findFirst().orElseThrow();
    }
}
