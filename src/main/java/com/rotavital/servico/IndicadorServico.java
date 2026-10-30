package com.rotavital.servico;

import com.rotavital.api.dto.BolsaResponse;
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
import com.rotavital.api.dto.indicadores.ValidadeResponse.DispersaoComponenteDto;
import com.rotavital.compatibilidade.MatrizCompatibilidade;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estatistica.ModeloDemanda;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.RequisicaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Servico responsavel pelo calculo dinamico de indicadores operacionais e estatisticos.
 *
 * <p>Porta a logica de analise estatistica de {@code dados/analise.py} sobre as entidades do banco,
 * garantindo fidelidade nas metricas de tendencia central, dispersao, descarte e cobertura.</p>
 *
 * <p>Os indicadores da Unidade 2 (descarte por periodo, descarte evitavel pela FEFO e probabilidade
 * de desabastecimento) seguem a mesma regra: o que o painel mostra e o que a API devolve, calculado
 * uma vez so, aqui. A equivalencia com a analise em Python e com o modulo C++ e verificada por
 * {@code IndicadorServicoDadosSinteticosTest} sobre {@code dados/dados_sinteticos.json}.</p>
 */
@Service
@Transactional(readOnly = true)
public class IndicadorServico {

    /** Registro da distribuicao adotada, devolvido junto com o indicador para o painel exibir. */
    static final String DISTRIBUICAO_DEMANDA =
            "Binomial Negativa parametrizada pela media e pela variancia da serie diaria de requisicoes; "
            + "Poisson calculada so como referencia, porque supoe variancia igual a media";

    private final BolsaRepository bolsaRepository;
    private final RequisicaoRepository requisicaoRepository;

    public IndicadorServico(BolsaRepository bolsaRepository, RequisicaoRepository requisicaoRepository) {
        this.bolsaRepository = bolsaRepository;
        this.requisicaoRepository = requisicaoRepository;
    }

    /**
     * Conta bolsas por grupo sanguineo com percentuais sobre o estoque total.
     */
    public List<EstoquePorTipoResponse> estoquePorTipoSanguineo() {
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        long total = bolsas.size();

        Map<GrupoSanguineo, Long> contagem = new EnumMap<>(GrupoSanguineo.class);
        for (GrupoSanguineo grupo : GrupoSanguineo.values()) {
            contagem.put(grupo, 0L);
        }

        for (Bolsa b : bolsas) {
            if (b.getGrupoSanguineo() != null) {
                contagem.put(b.getGrupoSanguineo(), contagem.get(b.getGrupoSanguineo()) + 1);
            }
        }

        return contagem.entrySet().stream()
                .map(e -> {
                    double pct = total > 0 ? (100.0 * e.getValue() / total) : 0.0;
                    return new EstoquePorTipoResponse(e.getKey(), e.getValue(), arredondar1Casa(pct));
                })
                .sorted((a, b) -> Long.compare(b.quantidade(), a.quantidade()))
                .toList();
    }

    /**
     * Conta bolsas por componente com percentuais sobre o estoque total.
     */
    public List<EstoquePorComponenteResponse> estoquePorComponente() {
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        long total = bolsas.size();

        Map<TipoHemocomponente, Long> contagem = new EnumMap<>(TipoHemocomponente.class);
        for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
            contagem.put(tipo, 0L);
        }

        for (Bolsa b : bolsas) {
            if (b.getTipo() != null) {
                contagem.put(b.getTipo(), contagem.get(b.getTipo()) + 1);
            }
        }

        return contagem.entrySet().stream()
                .map(e -> {
                    double pct = total > 0 ? (100.0 * e.getValue() / total) : 0.0;
                    return new EstoquePorComponenteResponse(e.getKey(), e.getValue(), arredondar1Casa(pct));
                })
                .sorted((a, b) -> Long.compare(b.quantidade(), a.quantidade()))
                .toList();
    }

    /**
     * Calcula total, quantidade vencida e taxa de descarte (%) por componente, e quanto desse
     * descarte a politica FEFO poderia ter evitado.
     */
    public List<DescarteComponenteResponse> taxaDescartePorComponente(LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        List<Requisicao> requisicoes = requisicaoRepository.findAllComHospitalEItens();

        Set<String> evitaveis = vencidasComDemandaCompativel(bolsas, requisicoes, hoje);
        return resumirDescartePorComponente(bolsas, hoje, evitaveis);
    }

    /**
     * Taxa de descarte das bolsas coletadas em cada periodo (semana ISO ou mes), do periodo mais
     * antigo ao mais recente. So entram periodos com pelo menos uma bolsa coletada.
     */
    public List<DescartePeriodoResponse> taxaDescartePorPeriodo(LocalDate dataReferencia,
                                                                PeriodoDescarte granularidade) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        PeriodoDescarte periodo = granularidade != null ? granularidade : PeriodoDescarte.SEMANA;
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        List<Requisicao> requisicoes = requisicaoRepository.findAllComHospitalEItens();

        Set<String> evitaveis = vencidasComDemandaCompativel(bolsas, requisicoes, hoje);

        // TreeMap pela data de inicio: os periodos saem em ordem cronologica sem ordenar depois.
        Map<LocalDate, List<Bolsa>> porInicio = new TreeMap<>();
        for (Bolsa b : bolsas) {
            if (b.getDataColeta() == null) {
                continue;
            }
            porInicio.computeIfAbsent(inicioDoPeriodo(b.getDataColeta(), periodo), k -> new ArrayList<>()).add(b);
        }

        List<DescartePeriodoResponse> lista = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Bolsa>> e : porInicio.entrySet()) {
            LocalDate inicio = e.getKey();
            List<Bolsa> doPeriodo = e.getValue();
            long total = doPeriodo.size();
            long vencidas = doPeriodo.stream().filter(b -> b.estaVencida(hoje)).count();
            double taxa = total > 0 ? (100.0 * vencidas / total) : 0.0;
            lista.add(new DescartePeriodoResponse(
                    rotuloDoPeriodo(inicio, periodo),
                    inicio,
                    fimDoPeriodo(inicio, periodo),
                    total,
                    vencidas,
                    arredondar1Casa(taxa),
                    resumirDescartePorComponente(doPeriodo, hoje, evitaveis)));
        }
        return lista;
    }

    /**
     * Probabilidade de a demanda superar o estoque valido, por tipo sanguineo, do maior para o
     * menor risco. A demanda e a variavel aleatoria; o estoque e a fotografia de hoje.
     */
    public DesabastecimentoResponse probabilidadeDesabastecimento(LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        List<Requisicao> requisicoes = requisicaoRepository.findAllComHospitalEItens();

        Map<GrupoSanguineo, Long> disponivel = new EnumMap<>(GrupoSanguineo.class);
        Map<GrupoSanguineo, Long> demandado = new EnumMap<>(GrupoSanguineo.class);
        for (GrupoSanguineo g : GrupoSanguineo.values()) {
            disponivel.put(g, 0L);
            demandado.put(g, 0L);
        }
        for (Bolsa b : bolsas) {
            if (!b.estaVencida(hoje) && b.getGrupoSanguineo() != null) {
                disponivel.put(b.getGrupoSanguineo(), disponivel.get(b.getGrupoSanguineo()) + 1);
            }
        }
        for (Requisicao r : requisicoes) {
            for (ItemRequisicao item : r.getItens()) {
                if (item.getGrupoSanguineo() != null) {
                    demandado.put(item.getGrupoSanguineo(),
                            demandado.get(item.getGrupoSanguineo()) + item.getQuantidadeSolicitada());
                }
            }
        }

        SeriesDiarias series = montarSeriesDiarias(requisicoes);

        List<DesabastecimentoTipoDto> porTipo = new ArrayList<>();
        List<Double> probabilidades = new ArrayList<>();
        for (GrupoSanguineo g : GrupoSanguineo.values()) {
            long estoque = disponivel.get(g);
            long demanda = demandado.get(g);
            double media = demanda;

            // Sem serie diaria (requisicoes sem data) nao ha como medir a variancia:
            // assume-se variancia = media, que e a hipotese da Poisson.
            double variancia = ModeloDemanda.varianciaDoHorizonte(series.porTipo().get(g));
            if (variancia < 0.0) {
                variancia = media;
            }
            double indiceDispersao = media > 0.0 ? variancia / media : 1.0;

            double probabilidade = ModeloDemanda.probabilidadeExcedenteBinomialNegativa(media, variancia, estoque);
            double probabilidadePoisson = ModeloDemanda.probabilidadeExcedentePoisson(media, estoque);
            Double cobertura = demanda > 0 ? (100.0 * estoque / demanda) : null;
            long deficit = Math.max(0, demanda - estoque);

            porTipo.add(new DesabastecimentoTipoDto(
                    g,
                    estoque,
                    demanda,
                    arredondar1Casa(variancia),
                    arredondar2Casas(indiceDispersao),
                    cobertura != null ? arredondar1Casa(cobertura) : null,
                    deficit,
                    arredondar1Casa(probabilidade * 100.0),
                    arredondar1Casa(probabilidadePoisson * 100.0),
                    classificarRisco(probabilidade, cobertura)));
            probabilidades.add(probabilidade);
        }

        // Maior risco primeiro; empate resolvido pela ordem do enum, para a resposta ser estavel.
        List<Integer> ordem = new ArrayList<>();
        for (int i = 0; i < porTipo.size(); i++) {
            ordem.add(i);
        }
        ordem.sort((a, b) -> {
            int cmp = Double.compare(probabilidades.get(b), probabilidades.get(a));
            return cmp != 0 ? cmp : Integer.compare(a, b);
        });
        List<DesabastecimentoTipoDto> ordenado = new ArrayList<>();
        for (int i : ordem) {
            ordenado.add(porTipo.get(i));
        }

        int diasObservados = series.inicio() == null
                ? 0
                : (int) ChronoUnit.DAYS.between(series.inicio(), series.fim()) + 1;

        return new DesabastecimentoResponse(hoje, series.inicio(), series.fim(), diasObservados,
                DISTRIBUICAO_DEMANDA, ordenado);
    }

    /**
     * Medidas de tendencia central e dispersao (n, media, mediana, desvio-padrao amostral, min, max)
     * dos dias ate o vencimento considerando apenas as bolsas validas.
     */
    public ValidadeResponse dispersaoValidade(LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();

        List<Long> diasGerais = bolsas.stream()
                .filter(b -> !b.estaVencida(hoje))
                .map(b -> ChronoUnit.DAYS.between(hoje, b.getDataValidade()))
                .toList();

        long n = diasGerais.size();
        double media = calcularMedia(diasGerais);
        double mediana = calcularMediana(diasGerais);
        double desvio = calcularDesvioPadraoAmostral(diasGerais, media);
        long min = diasGerais.isEmpty() ? 0 : Collections.min(diasGerais);
        long max = diasGerais.isEmpty() ? 0 : Collections.max(diasGerais);

        Map<TipoHemocomponente, DispersaoComponenteDto> porComponente = new LinkedHashMap<>();
        for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
            List<Long> diasComp = bolsas.stream()
                    .filter(b -> b.getTipo() == tipo && !b.estaVencida(hoje))
                    .map(b -> ChronoUnit.DAYS.between(hoje, b.getDataValidade()))
                    .toList();

            if (diasComp.size() >= 2) {
                double mediaComp = calcularMedia(diasComp);
                double medianaComp = calcularMediana(diasComp);
                double desvioComp = calcularDesvioPadraoAmostral(diasComp, mediaComp);
                porComponente.put(tipo, new DispersaoComponenteDto(
                        diasComp.size(),
                        arredondar1Casa(mediaComp),
                        arredondar1Casa(medianaComp),
                        arredondar1Casa(desvioComp)
                ));
            }
        }

        return new ValidadeResponse(
                n,
                arredondar1Casa(media),
                arredondar1Casa(mediana),
                arredondar1Casa(desvio),
                min,
                max,
                porComponente
        );
    }

    /**
     * Bolsas validas que vencem dentro do limite de dias especificado (alvo prioritario do FEFO).
     */
    public BolsaCriticaResponse bolsasProximasDoVencimento(int limiteDias, LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> todas = bolsaRepository.findAllComHemocentro();

        List<Bolsa> validas = todas.stream()
                .filter(b -> !b.estaVencida(hoje))
                .toList();

        List<Bolsa> criticas = validas.stream()
                .filter(b -> {
                    long dias = ChronoUnit.DAYS.between(hoje, b.getDataValidade());
                    return dias <= limiteDias;
                })
                .toList();

        double percentual = validas.isEmpty() ? 0.0 : (100.0 * criticas.size() / validas.size());

        List<BolsaResponse> dtos = criticas.stream()
                .map(BolsaResponse::de)
                .toList();

        return new BolsaCriticaResponse(limiteDias, criticas.size(), arredondar1Casa(percentual), dtos);
    }

    /**
     * Razao entre o estoque disponivel (bolsas validas) e o total demandado por tipo sanguineo.
     */
    public List<CoberturaResponse> coberturaPorTipo(LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> bolsas = bolsaRepository.findAllComHemocentro();
        List<Requisicao> requisicoes = requisicaoRepository.findAllComHospitalEItens();

        Map<GrupoSanguineo, Long> disponivel = new EnumMap<>(GrupoSanguineo.class);
        Map<GrupoSanguineo, Long> demandado = new EnumMap<>(GrupoSanguineo.class);
        for (GrupoSanguineo g : GrupoSanguineo.values()) {
            disponivel.put(g, 0L);
            demandado.put(g, 0L);
        }

        for (Bolsa b : bolsas) {
            if (!b.estaVencida(hoje) && b.getGrupoSanguineo() != null) {
                disponivel.put(b.getGrupoSanguineo(), disponivel.get(b.getGrupoSanguineo()) + 1);
            }
        }

        for (Requisicao r : requisicoes) {
            for (ItemRequisicao item : r.getItens()) {
                if (item.getGrupoSanguineo() != null) {
                    demandado.put(item.getGrupoSanguineo(),
                            demandado.get(item.getGrupoSanguineo()) + item.getQuantidadeSolicitada());
                }
            }
        }

        List<CoberturaResponse> lista = new ArrayList<>();
        for (GrupoSanguineo g : GrupoSanguineo.values()) {
            long disp = disponivel.get(g);
            long dem = demandado.get(g);
            Double cobPct = dem > 0 ? arredondar1Casa(100.0 * disp / dem) : null;
            long deficit = Math.max(0, dem - disp);
            lista.add(new CoberturaResponse(g, disp, dem, cobPct, deficit));
        }

        lista.sort((a, b) -> {
            if (a.coberturaPercentual() == null && b.coberturaPercentual() == null) return 0;
            if (a.coberturaPercentual() == null) return 1;
            if (b.coberturaPercentual() == null) return -1;
            return Double.compare(a.coberturaPercentual(), b.coberturaPercentual());
        });

        return lista;
    }

    // Descarte: agrupamento e relacao com a FEFO

    private List<DescarteComponenteResponse> resumirDescartePorComponente(List<Bolsa> bolsas, LocalDate hoje,
                                                                          Set<String> evitaveis) {
        Map<TipoHemocomponente, List<Bolsa>> porComponente = new EnumMap<>(TipoHemocomponente.class);
        for (TipoHemocomponente tipo : TipoHemocomponente.values()) {
            porComponente.put(tipo, new ArrayList<>());
        }
        for (Bolsa b : bolsas) {
            if (b.getTipo() != null) {
                porComponente.get(b.getTipo()).add(b);
            }
        }

        return porComponente.entrySet().stream()
                .map(e -> {
                    List<Bolsa> doComponente = e.getValue();
                    long total = doComponente.size();
                    List<Bolsa> vencidas = doComponente.stream().filter(b -> b.estaVencida(hoje)).toList();
                    long evitavel = vencidas.stream().filter(b -> evitaveis.contains(b.getCodigoRastreio())).count();
                    double taxa = total > 0 ? (100.0 * vencidas.size() / total) : 0.0;
                    double pctEvitavel = vencidas.isEmpty() ? 0.0 : (100.0 * evitavel / vencidas.size());
                    return new DescarteComponenteResponse(e.getKey(), total, vencidas.size(),
                            arredondar1Casa(taxa), evitavel, arredondar1Casa(pctEvitavel));
                })
                .sorted((a, b) -> Double.compare(b.taxaDescarte(), a.taxaDescarte()))
                .toList();
    }

    /**
     * Codigos das bolsas vencidas que tiveram demanda compativel enquanto ainda valiam.
     *
     * <p>Uma bolsa entra aqui quando existiu um item de requisicao do mesmo hemocomponente, de grupo
     * que ela podia atender ({@link MatrizCompatibilidade}), datado entre a coleta e a validade.
     * A FEFO despacha primeiro a bolsa que vence antes; se havia pedido compativel no periodo, ela
     * teria saido antes de vencer. E a parcela do descarte atribuivel a alocacao, nao a validade.</p>
     */
    private Set<String> vencidasComDemandaCompativel(List<Bolsa> bolsas, List<Requisicao> requisicoes,
                                                     LocalDate hoje) {
        record ItemDatado(LocalDate data, TipoHemocomponente tipo, GrupoSanguineo receptor) { }

        List<ItemDatado> itens = new ArrayList<>();
        for (Requisicao r : requisicoes) {
            if (r.getDataHoraCriacao() == null) {
                continue;
            }
            LocalDate data = r.getDataHoraCriacao().toLocalDate();
            for (ItemRequisicao item : r.getItens()) {
                itens.add(new ItemDatado(data, item.getTipo(), item.getGrupoSanguineo()));
            }
        }

        Set<String> evitaveis = new HashSet<>();
        for (Bolsa b : bolsas) {
            if (!b.estaVencida(hoje) || b.getDataColeta() == null) {
                continue;
            }
            for (ItemDatado item : itens) {
                boolean dentroDaValidade = !item.data().isBefore(b.getDataColeta())
                        && !item.data().isAfter(b.getDataValidade());
                if (item.tipo() == b.getTipo() && dentroDaValidade
                        && MatrizCompatibilidade.compativel(b.getGrupoSanguineo(), item.receptor(), b.getTipo())) {
                    evitaveis.add(b.getCodigoRastreio());
                    break;
                }
            }
        }
        return evitaveis;
    }

    private static LocalDate inicioDoPeriodo(LocalDate data, PeriodoDescarte periodo) {
        return switch (periodo) {
            case SEMANA -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> data.withDayOfMonth(1);
        };
    }

    private static LocalDate fimDoPeriodo(LocalDate inicio, PeriodoDescarte periodo) {
        return switch (periodo) {
            case SEMANA -> inicio.plusDays(6);
            case MES -> inicio.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    private static String rotuloDoPeriodo(LocalDate inicio, PeriodoDescarte periodo) {
        return switch (periodo) {
            case SEMANA -> String.format("%d-W%02d",
                    inicio.get(IsoFields.WEEK_BASED_YEAR), inicio.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MES -> YearMonth.from(inicio).toString();
        };
    }

    // Desabastecimento: serie diaria e classificacao de risco

    /** Demanda por dia e por tipo, do primeiro ao ultimo dia com requisicao; dias sem pedido valem zero. */
    private record SeriesDiarias(LocalDate inicio, LocalDate fim, Map<GrupoSanguineo, List<Long>> porTipo) { }

    private SeriesDiarias montarSeriesDiarias(List<Requisicao> requisicoes) {
        LocalDate inicio = null;
        LocalDate fim = null;
        for (Requisicao r : requisicoes) {
            if (r.getDataHoraCriacao() == null) {
                continue;
            }
            LocalDate dia = r.getDataHoraCriacao().toLocalDate();
            if (inicio == null || dia.isBefore(inicio)) inicio = dia;
            if (fim == null || dia.isAfter(fim)) fim = dia;
        }

        Map<GrupoSanguineo, List<Long>> porTipo = new EnumMap<>(GrupoSanguineo.class);
        int dias = inicio == null ? 0 : (int) ChronoUnit.DAYS.between(inicio, fim) + 1;
        for (GrupoSanguineo g : GrupoSanguineo.values()) {
            porTipo.put(g, new ArrayList<>(Collections.nCopies(dias, 0L)));
        }
        if (inicio == null) {
            return new SeriesDiarias(null, null, porTipo);
        }

        for (Requisicao r : requisicoes) {
            if (r.getDataHoraCriacao() == null) {
                continue;
            }
            int indice = (int) ChronoUnit.DAYS.between(inicio, r.getDataHoraCriacao().toLocalDate());
            for (ItemRequisicao item : r.getItens()) {
                if (item.getGrupoSanguineo() != null) {
                    List<Long> serie = porTipo.get(item.getGrupoSanguineo());
                    serie.set(indice, serie.get(indice) + item.getQuantidadeSolicitada());
                }
            }
        }
        return new SeriesDiarias(inicio, fim, porTipo);
    }

    /**
     * Mesma regua do modulo C++ (PI3-102): a probabilidade adotada e a cobertura pontual decidem o
     * nivel. Sem demanda a cobertura e indefinida e so a probabilidade conta.
     */
    static String classificarRisco(double probabilidade, Double coberturaPercentual) {
        if (probabilidade >= 0.80 || abaixoDe(coberturaPercentual, 70.0)) {
            return "CRITICO";
        }
        if (probabilidade >= 0.50 || abaixoDe(coberturaPercentual, 90.0)) {
            return "ALTO";
        }
        if (probabilidade >= 0.20 || abaixoDe(coberturaPercentual, 100.0)) {
            return "MODERADO";
        }
        return "BAIXO";
    }

    private static boolean abaixoDe(Double cobertura, double limite) {
        return cobertura != null && cobertura < limite;
    }

    // Metodos auxiliares para estatistica descritiva

    private double calcularMedia(List<Long> valores) {
        if (valores.isEmpty()) return 0.0;
        double soma = 0;
        for (Long v : valores) soma += v;
        return soma / valores.size();
    }

    private double calcularMediana(List<Long> valores) {
        if (valores.isEmpty()) return 0.0;
        List<Long> ordenados = new ArrayList<>(valores);
        Collections.sort(ordenados);
        int n = ordenados.size();
        if (n % 2 == 1) {
            return ordenados.get(n / 2);
        } else {
            return (ordenados.get((n / 2) - 1) + ordenados.get(n / 2)) / 2.0;
        }
    }

    /**
     * Calcula o desvio-padrao amostral (divisao por N - 1), identico ao {@code statistics.stdev} do Python.
     */
    private double calcularDesvioPadraoAmostral(List<Long> valores, double media) {
        if (valores.size() < 2) return 0.0;
        double somaQuadrados = 0;
        for (Long v : valores) {
            double diff = v - media;
            somaQuadrados += diff * diff;
        }
        return Math.sqrt(somaQuadrados / (valores.size() - 1));
    }

    private double arredondar1Casa(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }

    private double arredondar2Casas(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
