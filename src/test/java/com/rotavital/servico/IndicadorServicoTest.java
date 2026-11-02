package com.rotavital.servico;

import com.rotavital.api.dto.indicadores.BolsaCriticaResponse;
import com.rotavital.api.dto.indicadores.CoberturaResponse;
import com.rotavital.api.dto.indicadores.DesabastecimentoResponse;
import com.rotavital.api.dto.indicadores.DesabastecimentoResponse.DesabastecimentoTipoDto;
import com.rotavital.api.dto.indicadores.DescarteComponenteResponse;
import com.rotavital.api.dto.indicadores.DescartePeriodoResponse;
import com.rotavital.api.dto.indicadores.EstoquePorComponenteResponse;
import com.rotavital.api.dto.indicadores.EstoquePorTipoResponse;
import com.rotavital.api.dto.indicadores.PeriodoDescarte;
import com.rotavital.api.dto.indicadores.ValidadeResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicadorServicoTest {

    @Mock
    private BolsaRepository bolsaRepository;

    @Mock
    private RequisicaoRepository requisicaoRepository;

    @InjectMocks
    private IndicadorServico indicadorServico;

    private final LocalDate hoje = LocalDate.of(2026, 9, 17);

    private Hemocentro hemocentro() {
        return new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
    }

    /** Requisicao de um item, datada (ou sem data, quando {@code data} e nulo). */
    private Requisicao requisicaoEm(LocalDate data, TipoHemocomponente tipo, GrupoSanguineo receptor, int quantidade) {
        LocalDateTime criacao = data == null ? null : data.atTime(10, 0);
        Requisicao r = new Requisicao("REQ-" + data + "-" + tipo + "-" + receptor,
                new Hospital("hos-1", "Hospital 1", "123", null, "2400001"),
                PrioridadeRequisicao.ROTINA, criacao, criacao == null ? null : criacao.plusHours(72));
        r.adicionarItem(new ItemRequisicao(r.getId() + "-I1", tipo, receptor, quantidade));
        return r;
    }

    private static DescarteComponenteResponse componente(List<DescarteComponenteResponse> lista, TipoHemocomponente tipo) {
        return lista.stream().filter(c -> c.tipoHemocomponente() == tipo).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Deve agrupar o descarte por semana ISO e por mes de coleta, em ordem cronologica")
    void deveCalcularDescartePorPeriodo() {
        Hemocentro h = hemocentro();
        // Hemacias valem 42 dias: coletada em 30/07 venceu em 10/09; coletada em 10/08 vale ate 21/09.
        Bolsa vencida = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, LocalDate.of(2026, 7, 30), h);
        Bolsa valida = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, LocalDate.of(2026, 8, 10), h);
        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(valida, vencida));
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(List.of());

        List<DescartePeriodoResponse> semanas = indicadorServico.taxaDescartePorPeriodo(hoje, PeriodoDescarte.SEMANA);

        assertThat(semanas).extracting(DescartePeriodoResponse::periodo).containsExactly("2026-W31", "2026-W33");
        DescartePeriodoResponse semana31 = semanas.get(0);
        assertThat(semana31.inicio()).isEqualTo(LocalDate.of(2026, 7, 27)); // segunda-feira
        assertThat(semana31.fim()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(semana31.total()).isEqualTo(1);
        assertThat(semana31.vencidas()).isEqualTo(1);
        assertThat(semana31.taxaDescarte()).isEqualTo(100.0);
        assertThat(semanas.get(1).taxaDescarte()).isZero();
        assertThat(componente(semana31.porComponente(), TipoHemocomponente.CONCENTRADO_HEMACIAS).vencidas()).isEqualTo(1);

        List<DescartePeriodoResponse> meses = indicadorServico.taxaDescartePorPeriodo(hoje, PeriodoDescarte.MES);

        assertThat(meses).extracting(DescartePeriodoResponse::periodo).containsExactly("2026-07", "2026-08");
        assertThat(meses.get(0).inicio()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(meses.get(0).fim()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(meses.get(0).taxaDescarte()).isEqualTo(100.0);
        assertThat(meses.get(1).taxaDescarte()).isZero();
    }

    @Test
    @DisplayName("Descarte evitavel pela FEFO: so a vencida que teve pedido compativel dentro da validade")
    void deveContarDescarteEvitavelPelaFefo() {
        Hemocentro h = hemocentro();
        LocalDate coleta = LocalDate.of(2026, 7, 1); // hemacias vencem em 12/08, plaquetas em 06/07
        Bolsa oNeg = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 450, coleta, h);
        Bolsa bPos = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.B_POS, 450, coleta, h);
        Bolsa plaquetaCedo = new Bolsa("B3", TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_NEG, 250, coleta, h);
        // coletada em 15/07, vence exatamente em 20/07: o pedido desse dia ainda a alcanca
        Bolsa plaquetaNoLimite = new Bolsa("B4", TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_NEG, 250, LocalDate.of(2026, 7, 15), h);
        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(oNeg, bPos, plaquetaCedo, plaquetaNoLimite));
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(List.of(
                requisicaoEm(LocalDate.of(2026, 7, 20), TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS, 2),
                requisicaoEm(LocalDate.of(2026, 7, 20), TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.A_POS, 1)));

        List<DescarteComponenteResponse> resultado = indicadorServico.taxaDescartePorComponente(hoje);

        // O- doa para A+ e estava na validade: evitavel. B+ nao doa para A+: a perda nao e da alocacao.
        DescarteComponenteResponse hemacias = componente(resultado, TipoHemocomponente.CONCENTRADO_HEMACIAS);
        assertThat(hemacias.vencidas()).isEqualTo(2);
        assertThat(hemacias.descarteEvitavelFefo()).isEqualTo(1);
        assertThat(hemacias.percentualEvitavelFefo()).isEqualTo(50.0);

        // A primeira plaqueta ja tinha vencido quando o pedido chegou; a segunda vencia no proprio dia.
        DescarteComponenteResponse plaquetas = componente(resultado, TipoHemocomponente.CONCENTRADO_PLAQUETAS);
        assertThat(plaquetas.vencidas()).isEqualTo(2);
        assertThat(plaquetas.descarteEvitavelFefo()).isEqualTo(1);
        assertThat(plaquetas.percentualEvitavelFefo()).isEqualTo(50.0);

        DescarteComponenteResponse plasma = componente(resultado, TipoHemocomponente.PLASMA_FRESCO_CONGELADO);
        assertThat(plasma.vencidas()).isZero();
        assertThat(plasma.percentualEvitavelFefo()).isZero();
    }

    @Test
    @DisplayName("Probabilidade de desabastecimento mede a variancia na serie diaria e supera a Poisson com sobredispersao")
    void deveCalcularProbabilidadeDesabastecimentoComSerieDiaria() {
        Hemocentro h = hemocentro();
        List<Bolsa> estoque = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            estoque.add(new Bolsa("B" + i, TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 450, hoje, h));
        }
        when(bolsaRepository.findAllComHemocentro()).thenReturn(estoque);
        // 11 bolsas pedidas em 5 dias, concentradas em picos: a variancia supera a media
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(List.of(
                requisicaoEm(LocalDate.of(2026, 8, 1), TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 4),
                requisicaoEm(LocalDate.of(2026, 8, 3), TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 6),
                requisicaoEm(LocalDate.of(2026, 8, 5), TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_NEG, 1)));

        DesabastecimentoResponse resultado = indicadorServico.probabilidadeDesabastecimento(hoje);

        assertThat(resultado.dataReferencia()).isEqualTo(hoje);
        assertThat(resultado.inicioDemanda()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(resultado.fimDemanda()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(resultado.diasObservados()).isEqualTo(5);
        assertThat(resultado.porTipo()).hasSize(8);

        // O- e o unico com demanda, entao e o primeiro da lista (maior risco)
        DesabastecimentoTipoDto oNeg = resultado.porTipo().get(0);
        assertThat(oNeg.grupoSanguineo()).isEqualTo(GrupoSanguineo.O_NEG);
        assertThat(oNeg.estoqueValido()).isEqualTo(12);
        assertThat(oNeg.demandaEsperada()).isEqualTo(11);
        // serie {4, 0, 6, 0, 1}: s^2 = 7,2 e N = 5 -> variancia 36; indice 36 / 11
        assertThat(oNeg.varianciaDemanda()).isEqualTo(36.0);
        assertThat(oNeg.indiceDispersao()).isEqualTo(3.27);
        assertThat(oNeg.coberturaPercentual()).isEqualTo(109.1);
        assertThat(oNeg.deficit()).isZero();
        // P(BN) = 34,7% contra 31,1% da Poisson, conferido em dados/validar_indicadores_u2.py
        assertThat(oNeg.probabilidadePercentual()).isEqualTo(34.7);
        assertThat(oNeg.probabilidadePoissonPercentual()).isEqualTo(31.1);
        assertThat(oNeg.nivelRisco()).isEqualTo("MODERADO");

        // quem nao teve pedido nao corre risco
        DesabastecimentoTipoDto semDemanda = resultado.porTipo().get(7);
        assertThat(semDemanda.demandaEsperada()).isZero();
        assertThat(semDemanda.coberturaPercentual()).isNull();
        assertThat(semDemanda.probabilidadePercentual()).isZero();
        assertThat(semDemanda.nivelRisco()).isEqualTo("BAIXO");
    }

    @Test
    @DisplayName("Sem data nas requisicoes nao ha serie diaria: o calculo assume variancia = media (Poisson)")
    void deveCairNaPoissonSemDatas() {
        Hemocentro h = hemocentro();
        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(
                new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.AB_NEG, 450, hoje, h),
                new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.AB_NEG, 450, hoje, h)));
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(List.of(
                requisicaoEm(null, TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.AB_NEG, 10)));

        DesabastecimentoResponse resultado = indicadorServico.probabilidadeDesabastecimento(hoje);

        assertThat(resultado.diasObservados()).isZero();
        assertThat(resultado.inicioDemanda()).isNull();
        DesabastecimentoTipoDto abNeg = resultado.porTipo().get(0);
        assertThat(abNeg.grupoSanguineo()).isEqualTo(GrupoSanguineo.AB_NEG);
        assertThat(abNeg.indiceDispersao()).isEqualTo(1.0);
        assertThat(abNeg.probabilidadePercentual()).isEqualTo(abNeg.probabilidadePoissonPercentual());
        assertThat(abNeg.deficit()).isEqualTo(8);
        assertThat(abNeg.nivelRisco()).isEqualTo("CRITICO");
    }

    @Test
    @DisplayName("Sem bolsas nem requisicoes, os indicadores da U2 respondem vazios ou sem risco, nunca com erro")
    void deveResponderSemDadosNaU2() {
        when(bolsaRepository.findAllComHemocentro()).thenReturn(Collections.emptyList());
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(Collections.emptyList());

        assertThat(indicadorServico.taxaDescartePorPeriodo(hoje, PeriodoDescarte.SEMANA)).isEmpty();
        assertThat(indicadorServico.taxaDescartePorComponente(hoje))
                .hasSize(4)
                .allSatisfy(c -> assertThat(c.descarteEvitavelFefo()).isZero());

        DesabastecimentoResponse resultado = indicadorServico.probabilidadeDesabastecimento(hoje);
        assertThat(resultado.diasObservados()).isZero();
        assertThat(resultado.porTipo())
                .hasSize(8)
                .allSatisfy(t -> {
                    assertThat(t.probabilidadePercentual()).isZero();
                    assertThat(t.nivelRisco()).isEqualTo("BAIXO");
                });
    }

    @Test
    @DisplayName("Deve calcular estoque por tipo sanguíneo e percentuais corretamente")
    void deveCalcularEstoquePorTipo() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        List<Bolsa> bolsas = List.of(
                new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h),
                new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h),
                new Bolsa("B3", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.A_POS, 450, hoje, h),
                new Bolsa("B4", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.B_NEG, 450, hoje, h)
        );

        when(bolsaRepository.findAllComHemocentro()).thenReturn(bolsas);

        List<EstoquePorTipoResponse> resultado = indicadorServico.estoquePorTipoSanguineo();

        assertThat(resultado).isNotEmpty();
        EstoquePorTipoResponse oPos = resultado.stream()
                .filter(r -> r.grupoSanguineo() == GrupoSanguineo.O_POS)
                .findFirst().orElseThrow();

        assertThat(oPos.quantidade()).isEqualTo(2);
        assertThat(oPos.percentual()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Deve calcular taxa de descarte por componente considerando bolsas vencidas")
    void deveCalcularTaxaDescartePorComponente() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        // Plaquetas valem 5 dias. Coletadas ha 10 dias ja estao vencidas. Coletadas hoje sao validas.
        Bolsa bVencida = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_POS, 250, hoje.minusDays(10), h);
        Bolsa bValida = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_POS, 250, hoje, h);

        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(bVencida, bValida));

        List<DescarteComponenteResponse> resultado = indicadorServico.taxaDescartePorComponente(hoje);

        DescarteComponenteResponse plaquetas = resultado.stream()
                .filter(r -> r.tipoHemocomponente() == TipoHemocomponente.CONCENTRADO_PLAQUETAS)
                .findFirst().orElseThrow();

        assertThat(plaquetas.total()).isEqualTo(2);
        assertThat(plaquetas.vencidas()).isEqualTo(1);
        assertThat(plaquetas.taxaDescarte()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Deve calcular medidas de dispersao e tendencia central (media, mediana e desvio padrao amostral)")
    void deveCalcularDispersaoValidade() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        // Bolsas com diferentes validades
        Bolsa b1 = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h); // vence em 42 dias
        Bolsa b2 = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje.minusDays(10), h); // vence em 32 dias
        Bolsa b3 = new Bolsa("B3", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje.minusDays(20), h); // vence em 22 dias

        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(b1, b2, b3));

        ValidadeResponse resultado = indicadorServico.dispersaoValidade(hoje);

        assertThat(resultado.n()).isEqualTo(3);
        // dias restantes: 42, 32, 22 -> media = 32.0, mediana = 32.0, desvio amostral = 10.0
        assertThat(resultado.mediaDias()).isEqualTo(32.0);
        assertThat(resultado.medianaDias()).isEqualTo(32.0);
        assertThat(resultado.desvioPadraoDias()).isEqualTo(10.0);
        assertThat(resultado.minimoDias()).isEqualTo(22);
        assertThat(resultado.maximoDias()).isEqualTo(42);
    }

    @Test
    @DisplayName("Deve filtrar bolsas criticas dentro do limite de dias configurado")
    void deveFiltrarBolsasCriticas() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        // Plaquetas valem 5 dias -> coletada hoje vence em 5 dias (critica <= 7 dias)
        Bolsa critica = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_PLAQUETAS, GrupoSanguineo.O_POS, 250, hoje, h);
        // Hemacias valem 42 dias -> coletada hoje vence em 42 dias (nao critica)
        Bolsa naoCritica = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h);

        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(critica, naoCritica));

        BolsaCriticaResponse resultado = indicadorServico.bolsasProximasDoVencimento(7, hoje);

        assertThat(resultado.quantidade()).isEqualTo(1);
        assertThat(resultado.percentualDoEstoqueValido()).isEqualTo(50.0);
        assertThat(resultado.bolsas()).hasSize(1);
        assertThat(resultado.bolsas().get(0).id()).isEqualTo("B1");
    }

    @Test
    @DisplayName("Deve calcular cobertura de demanda e deficit acumulado por tipo sanguíneo")
    void deveCalcularCoberturaDemanda() {
        Hemocentro h = new Hemocentro("hem-1", "Hemocentro 1", "123", null, "11.111.111/0001-11");
        Hospital hosp = new Hospital("hosp-1", "Hospital 1", "999", null, "22.222.222/0001-22");

        Bolsa b1 = new Bolsa("B1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h);
        Bolsa b2 = new Bolsa("B2", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 450, hoje, h);

        Requisicao req = new Requisicao("req-1", hosp, PrioridadeRequisicao.ROTINA, LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        req.adicionarItem(new ItemRequisicao("item-1", TipoHemocomponente.CONCENTRADO_HEMACIAS, GrupoSanguineo.O_POS, 5)); // demandou 5, tem 2

        when(bolsaRepository.findAllComHemocentro()).thenReturn(List.of(b1, b2));
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(List.of(req));

        List<CoberturaResponse> resultado = indicadorServico.coberturaPorTipo(hoje);

        CoberturaResponse oPos = resultado.stream()
                .filter(r -> r.grupoSanguineo() == GrupoSanguineo.O_POS)
                .findFirst().orElseThrow();

        assertThat(oPos.disponivel()).isEqualTo(2);
        assertThat(oPos.demandado()).isEqualTo(5);
        assertThat(oPos.coberturaPercentual()).isEqualTo(40.0);
        assertThat(oPos.deficit()).isEqualTo(3);
    }

    @Test
    @DisplayName("Deve lidar com casos de borda: listas vazias sem disparar excecao")
    void deveLidarComListasVazias() {
        when(bolsaRepository.findAllComHemocentro()).thenReturn(Collections.emptyList());
        when(requisicaoRepository.findAllComHospitalEItens()).thenReturn(Collections.emptyList());

        ValidadeResponse val = indicadorServico.dispersaoValidade(hoje);
        assertThat(val.n()).isEqualTo(0);
        assertThat(val.mediaDias()).isEqualTo(0.0);
        assertThat(val.desvioPadraoDias()).isEqualTo(0.0);

        List<CoberturaResponse> cob = indicadorServico.coberturaPorTipo(hoje);
        assertThat(cob).allMatch(c -> c.disponivel() == 0 && c.demandado() == 0 && c.coberturaPercentual() == null);
    }
}
