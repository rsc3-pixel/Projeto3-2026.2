#include "CalculadorDesabastecimento.hpp"
#include <algorithm>
#include <cmath>

CalculadorDesabastecimento::CalculadorDesabastecimento(
    const AcumuladorEstoque& est,
    const AcumuladorDemanda& dem,
    const ModeloDemanda& mod
) : estoque(est), demanda(dem), modelo(mod) {}

std::string CalculadorDesabastecimento::classificarRisco(double probDesabastecimento, double cobertura) {
    if (probDesabastecimento >= 0.80 || cobertura < 70.0) {
        return "CRITICO";
    }
    if (probDesabastecimento >= 0.50 || cobertura < 90.0) {
        return "ALTO";
    }
    if (probDesabastecimento >= 0.20 || cobertura < 100.0) {
        return "MODERADO";
    }
    return "BAIXO";
}

std::string CalculadorDesabastecimento::gerarRecomendacao(
    double probDesabastecimento, double cobertura, double taxaDescarte
) {
    if (taxaDescarte >= 80.0) {
        return "FEFO IMEDIATO: Descarte critico detectado. Priorizar despacho prioritario em menos de 24h.";
    }
    if (probDesabastecimento >= 0.90) {
        return "EMERGENCIA DE SUPRIMENTO: Deficit severo. Convocar doadores compativeis ou alocar O-.";
    }
    if (probDesabastecimento >= 0.50) {
        return "ALERTA PREVENTIVO: Demanda proxima do limite fisico. Monitorar requisicoes programadas.";
    }
    if (cobertura > 110.0) {
        return "ESTOQUE SEGURO: Superavit registrado. Pode servir como retaguarda estrategica regional.";
    }
    return "OPERACAO ESTAVEL: Manter monitoramento de rotina.";
}

void CalculadorDesabastecimento::calcularProbabilidades(
    RelatorioDesabastecimento& rel, const std::vector<long>& serieDiaria, int numSimulacoes
) const {
    double media = static_cast<double>(rel.demandaEsperada);
    int estoqueValido = static_cast<int>(rel.estoqueValido);

    // Sem serie diaria (requisicoes sem data) nao ha como medir a variancia:
    // assume-se variancia = media, o que equivale a hipotese de Poisson.
    double variancia = ModeloDemanda::varianciaDoHorizonte(serieDiaria);
    if (variancia < 0.0) variancia = media;

    rel.varianciaDemanda = variancia;
    rel.indiceDispersao = (media > 0.0) ? variancia / media : 1.0;

    rel.probDesabastecimentoPoisson = modelo.calcularProbabilidadeExcedentePoisson(media, estoqueValido);
    rel.probDesabastecimento = modelo.calcularProbabilidadeExcedenteBinomialNegativa(media, variancia, estoqueValido);
    rel.probDesabastecimentoNormal = modelo.calcularProbabilidadeExcedenteNormal(
        media, std::sqrt(variancia > 0.0 ? variancia : 1.0), estoqueValido);
    rel.probDesabastecimentoMonteCarlo = (serieDiaria.size() >= 2)
        ? modelo.simularProbabilidadeDesabastecimentoBootstrap(serieDiaria, estoqueValido, numSimulacoes)
        : modelo.simularProbabilidadeDesabastecimentoMonteCarlo(media, estoqueValido, numSimulacoes);
}

namespace {
void ordenarPorRisco(std::vector<RelatorioDesabastecimento>& relatorios) {
    // Ordena decrescente pela probabilidade adotada (maior risco primeiro)
    std::sort(relatorios.begin(), relatorios.end(), [](const RelatorioDesabastecimento& a, const RelatorioDesabastecimento& b) {
        return a.probDesabastecimento > b.probDesabastecimento;
    });
}
}

std::vector<RelatorioDesabastecimento> CalculadorDesabastecimento::avaliarPorTipoSanguineo() const {
    std::vector<RelatorioDesabastecimento> relatorios;
    std::vector<TipoSanguineo> tipos = {
        TipoSanguineo::AB_NEG, TipoSanguineo::AB_POS, TipoSanguineo::A_POS, TipoSanguineo::B_POS,
        TipoSanguineo::A_NEG, TipoSanguineo::O_POS, TipoSanguineo::B_NEG, TipoSanguineo::O_NEG
    };

    for (auto tipo : tipos) {
        RelatorioDesabastecimento rel;
        rel.chave = tipoSanguineoToString(tipo);
        rel.nomeExibicao = tipoSanguineoToDisplay(tipo);
        rel.estoqueTotal = estoque.getEstoqueTotal(tipo);
        rel.estoqueValido = estoque.getEstoqueValido(tipo);
        rel.estoqueVencido = estoque.getEstoqueVencido(tipo);
        rel.taxaDescartePct = (rel.estoqueTotal > 0)
            ? (100.0 * static_cast<double>(rel.estoqueVencido) / static_cast<double>(rel.estoqueTotal))
            : 0.0;

        rel.demandaEsperada = demanda.getDemanda(tipo);
        rel.coberturaPontualPct = (rel.demandaEsperada > 0)
            ? (100.0 * static_cast<double>(rel.estoqueValido) / static_cast<double>(rel.demandaEsperada))
            : 0.0;
        rel.deficitPontual = std::max(0L, rel.demandaEsperada - rel.estoqueValido);

        calcularProbabilidades(rel, demanda.getSerieDiaria(tipo), 100000);

        rel.nivelRisco = classificarRisco(rel.probDesabastecimento, rel.coberturaPontualPct);
        rel.recomendacaoAcao = gerarRecomendacao(rel.probDesabastecimento, rel.coberturaPontualPct, rel.taxaDescartePct);

        relatorios.push_back(rel);
    }

    ordenarPorRisco(relatorios);
    return relatorios;
}

std::vector<RelatorioDesabastecimento> CalculadorDesabastecimento::avaliarPorHemocomponente() const {
    std::vector<RelatorioDesabastecimento> relatorios;
    std::vector<TipoHemocomponente> componentes = {
        TipoHemocomponente::CONCENTRADO_HEMACIAS,
        TipoHemocomponente::CONCENTRADO_PLAQUETAS,
        TipoHemocomponente::PLASMA_FRESCO_CONGELADO,
        TipoHemocomponente::CRIOPRECIPITADO
    };

    for (auto comp : componentes) {
        RelatorioDesabastecimento rel;
        rel.chave = hemocomponenteToString(comp);
        rel.nomeExibicao = hemocomponenteToDisplay(comp);
        rel.estoqueTotal = estoque.getEstoqueTotal(comp);
        rel.estoqueValido = estoque.getEstoqueValido(comp);
        rel.estoqueVencido = estoque.getEstoqueVencido(comp);
        rel.taxaDescartePct = estoque.getTaxaDescarte(comp);

        rel.demandaEsperada = demanda.getDemanda(comp);
        rel.coberturaPontualPct = (rel.demandaEsperada > 0)
            ? (100.0 * static_cast<double>(rel.estoqueValido) / static_cast<double>(rel.demandaEsperada))
            : 0.0;
        rel.deficitPontual = std::max(0L, rel.demandaEsperada - rel.estoqueValido);

        calcularProbabilidades(rel, demanda.getSerieDiaria(comp), 100000);

        rel.nivelRisco = classificarRisco(rel.probDesabastecimento, rel.coberturaPontualPct);
        rel.recomendacaoAcao = gerarRecomendacao(rel.probDesabastecimento, rel.coberturaPontualPct, rel.taxaDescartePct);

        relatorios.push_back(rel);
    }

    ordenarPorRisco(relatorios);
    return relatorios;
}

std::vector<RelatorioDesabastecimento> CalculadorDesabastecimento::avaliarMatrizCruzada() const {
    std::vector<RelatorioDesabastecimento> relatorios;
    std::vector<TipoHemocomponente> componentes = {
        TipoHemocomponente::CONCENTRADO_HEMACIAS,
        TipoHemocomponente::CONCENTRADO_PLAQUETAS,
        TipoHemocomponente::PLASMA_FRESCO_CONGELADO,
        TipoHemocomponente::CRIOPRECIPITADO
    };
    std::vector<TipoSanguineo> tipos = {
        TipoSanguineo::O_POS, TipoSanguineo::A_POS, TipoSanguineo::B_POS, TipoSanguineo::O_NEG,
        TipoSanguineo::A_NEG, TipoSanguineo::AB_POS, TipoSanguineo::B_NEG, TipoSanguineo::AB_NEG
    };

    for (auto comp : componentes) {
        for (auto tipo : tipos) {
            RelatorioDesabastecimento rel;
            rel.chave = hemocomponenteToString(comp) + " x " + tipoSanguineoToString(tipo);
            rel.nomeExibicao = hemocomponenteToDisplay(comp) + " (" + tipoSanguineoToDisplay(tipo) + ")";
            rel.estoqueValido = estoque.getEstoqueValido(comp, tipo);
            rel.estoqueTotal = rel.estoqueValido; // Na matriz cruzada, o foco e o estoque util disponivel
            rel.estoqueVencido = 0;
            rel.taxaDescartePct = 0.0;

            rel.demandaEsperada = demanda.getDemanda(comp, tipo);
            rel.coberturaPontualPct = (rel.demandaEsperada > 0)
                ? (100.0 * static_cast<double>(rel.estoqueValido) / static_cast<double>(rel.demandaEsperada))
                : 100.0;
            rel.deficitPontual = std::max(0L, rel.demandaEsperada - rel.estoqueValido);

            calcularProbabilidades(rel, demanda.getSerieDiaria(comp, tipo), 20000);

            rel.nivelRisco = classificarRisco(rel.probDesabastecimento, rel.coberturaPontualPct);
            rel.recomendacaoAcao = gerarRecomendacao(rel.probDesabastecimento, rel.coberturaPontualPct, 0.0);

            relatorios.push_back(rel);
        }
    }

    ordenarPorRisco(relatorios);
    return relatorios;
}
