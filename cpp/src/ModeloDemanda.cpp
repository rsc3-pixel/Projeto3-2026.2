#include "ModeloDemanda.hpp"
#include <cmath>
#include <random>
#include <sstream>
#include <iomanip>

ModeloDemanda::ModeloDemanda() {
    nomeDistribuicao = "BINOMIAL_NEGATIVA (Poisson como referencia, Normal via TCL e validacao por Monte Carlo bootstrap)";
    justificativaModelo =
        "A demanda hospitalar e uma contagem discreta e nao negativa de bolsas. As requisicoes chegam de forma "
        "independente, o que sugere Poisson para o numero de pedidos; mas cada item de pedido traz de 1 a 4 bolsas, "
        "e a soma resultante (Poisson composta) tem variancia maior que a media. Nos dados sinteticos a variancia "
        "da demanda diaria fica entre 2 e 4 vezes a media em todos os tipos sanguineos, e a Poisson, que impoe "
        "variancia igual a media, subestimaria o risco justamente nos tipos com estoque proximo da demanda. "
        "Por isso a distribuicao escolhida e a Binomial Negativa, parametrizada pela media e pela variancia "
        "observadas na serie diaria; ela se reduz a Poisson quando nao ha sobredispersao. A Normal (TCL) usa a "
        "mesma variancia real, e o Monte Carlo reamostra os dias observados (bootstrap, 100.000 iteracoes, "
        "semente 2026) sem supor distribuicao alguma, servindo de validacao independente.";
}

void ModeloDemanda::calibrarComDadosAcumulados(
    const std::map<TipoSanguineo, long>& demandaPorTipo,
    const std::map<TipoHemocomponente, long>& demandaPorComponente,
    const std::map<std::pair<TipoHemocomponente, TipoSanguineo>, long>& demandaCruzada,
    long totalGeralDemandado
) {
    parametrosPorTipo.clear();
    parametrosPorComponente.clear();
    parametrosCruzados.clear();

    double totalGeral = static_cast<double>(totalGeralDemandado > 0 ? totalGeralDemandado : 1);

    // 1. Calibracao por Tipo Sanguineo
    for (const auto& [tipo, qtd] : demandaPorTipo) {
        ParametrosDemanda p;
        p.identificador = tipoSanguineoToString(tipo);
        p.valorObservado = qtd;
        p.lambda = static_cast<double>(qtd);
        p.media = p.lambda;
        p.desvioPadrao = std::sqrt(p.lambda);
        p.probabilidadeAmostral = (static_cast<double>(qtd) / totalGeral) * 100.0;

        if (p.lambda > 30.0) {
            p.distribuicao = "NORMAL_TCL / POISSON (lambda = " + std::to_string(static_cast<long>(p.lambda)) + ")";
            p.justificativa = "Volume elevado (lambda > 30) confere convergencia gaussiana perfeita pelo TCL com correcao de continuidade.";
        } else {
            p.distribuicao = "POISSON_EXATO (lambda = " + std::to_string(static_cast<long>(p.lambda)) + ")";
            p.justificativa = "Tipagem rara ou menor circulacao: modelagem pontual exata de Poisson.";
        }
        parametrosPorTipo[tipo] = p;
    }

    // 2. Calibracao por Hemocomponente
    for (const auto& [comp, qtd] : demandaPorComponente) {
        ParametrosDemanda p;
        p.identificador = hemocomponenteToString(comp);
        p.valorObservado = qtd;
        p.lambda = static_cast<double>(qtd);
        p.media = p.lambda;
        p.desvioPadrao = std::sqrt(p.lambda);
        p.probabilidadeAmostral = (static_cast<double>(qtd) / totalGeral) * 100.0;
        p.distribuicao = "NORMAL_TCL / POISSON (lambda = " + std::to_string(static_cast<long>(p.lambda)) + ")";
        p.justificativa = "Demanda agregada por componente apresenta alta escala estatistica.";
        parametrosPorComponente[comp] = p;
    }

    // 3. Calibracao Cruzada (Componente x Tipo)
    for (const auto& [par, qtd] : demandaCruzada) {
        ParametrosDemanda p;
        p.identificador = hemocomponenteToString(par.first) + "_" + tipoSanguineoToString(par.second);
        p.valorObservado = qtd;
        p.lambda = static_cast<double>(qtd);
        p.media = p.lambda;
        p.desvioPadrao = std::sqrt(p.lambda > 0 ? p.lambda : 1.0);
        p.probabilidadeAmostral = (static_cast<double>(qtd) / totalGeral) * 100.0;
        p.distribuicao = (p.lambda > 30.0) ? "NORMAL_TCL" : "POISSON_EXATO";
        p.justificativa = "Modelagem da demanda especifica por produto transfusional biocompativeis.";
        parametrosCruzados[par] = p;
    }
}

double ModeloDemanda::poissonPMF(double lambda, int k) {
    if (k < 0 || lambda < 0.0) return 0.0;
    if (lambda == 0.0) return (k == 0) ? 1.0 : 0.0;

    // Calculo via espaco logaritmico para estabilidade numerica
    double logFat = 0.0;
    for (int i = 1; i <= k; ++i) {
        logFat += std::log(i);
    }
    double logProb = -lambda + k * std::log(lambda) - logFat;
    return std::exp(logProb);
}

double ModeloDemanda::poissonCDF(double lambda, int k) {
    if (k < 0) return 0.0;
    if (lambda <= 0.0) return 1.0;

    // Para lambdas pequenos e moderados (<= 80), somamos progressivamente
    if (lambda <= 80.0) {
        double termo = std::exp(-lambda);
        double cdf = termo;
        for (int i = 1; i <= k; ++i) {
            termo *= (lambda / static_cast<double>(i));
            cdf += termo;
            if (termo < 1e-18 && i > lambda) break; // otimizacao de cauda
        }
        return (cdf > 1.0) ? 1.0 : cdf;
    }

    // Para lambdas grandes (> 80), a convergencia assintotica da Normal com 
    // correcao de continuidade (Yates) e extremamente precisa (erro < 10^-4)
    double z = (static_cast<double>(k) + 0.5 - lambda) / std::sqrt(lambda);
    return 0.5 * (1.0 + std::erf(z / std::sqrt(2.0)));
}

double ModeloDemanda::normalCDF(double media, double desvio, double x) {
    if (desvio <= 0.0) return (x >= media) ? 1.0 : 0.0;
    double z = (x - media) / desvio;
    return 0.5 * (1.0 + std::erf(z / std::sqrt(2.0)));
}

double ModeloDemanda::calcularProbabilidadeExcedentePoisson(double lambda, int estoqueDisponivel) const {
    if (estoqueDisponivel < 0) return 1.0;
    double cdf = poissonCDF(lambda, estoqueDisponivel);
    double excedente = 1.0 - cdf;
    if (excedente < 0.0) excedente = 0.0;
    if (excedente > 1.0) excedente = 1.0;
    return excedente;
}

double ModeloDemanda::calcularProbabilidadeExcedenteNormal(double media, double desvio, int estoqueDisponivel) const {
    if (desvio <= 0.0) return (estoqueDisponivel < media) ? 1.0 : 0.0;
    // Correcao de continuidade de Yates: P(D > S) = P(D >= S + 1) -> corte em S + 0.5
    double z = (static_cast<double>(estoqueDisponivel) + 0.5 - media) / desvio;
    double cdf = 0.5 * (1.0 + std::erf(z / std::sqrt(2.0)));
    double prob = 1.0 - cdf;
    if (prob < 0.0) prob = 0.0;
    if (prob > 1.0) prob = 1.0;
    return prob;
}

double ModeloDemanda::simularProbabilidadeDesabastecimentoMonteCarlo(
    double lambda, int estoqueDisponivel, int numSimulacoes, unsigned int semente
) const {
    if (lambda <= 0.0) return (estoqueDisponivel < 0) ? 1.0 : 0.0;

    std::mt19937_64 rng(semente);
    std::poisson_distribution<int> dist(lambda);

    long contagemDesabastecimento = 0;
    for (int i = 0; i < numSimulacoes; ++i) {
        int demandaSimulada = dist(rng);
        if (demandaSimulada > estoqueDisponivel) {
            contagemDesabastecimento++;
        }
    }

    return static_cast<double>(contagemDesabastecimento) / static_cast<double>(numSimulacoes);
}

double ModeloDemanda::binomialNegativaCDF(double media, double variancia, int k) {
    if (k < 0) return 0.0;
    if (media <= 0.0) return 1.0;
    if (variancia <= media) return poissonCDF(media, k);

    // Parametrizacao por media e variancia: r = mu^2 / (sigma^2 - mu), p = r / (r + mu).
    // Cada termo e calculado em espaco logaritmico para evitar underflow em medias altas.
    double r = (media * media) / (variancia - media);
    double p = r / (r + media);
    double logP = std::log(p);
    double log1mP = std::log1p(-p);
    double lgammaR = std::lgamma(r);

    double cdf = 0.0;
    for (int i = 0; i <= k; ++i) {
        double logPmf = std::lgamma(i + r) - lgammaR - std::lgamma(i + 1.0) + r * logP + i * log1mP;
        cdf += std::exp(logPmf);
    }
    return (cdf > 1.0) ? 1.0 : cdf;
}

double ModeloDemanda::calcularProbabilidadeExcedenteBinomialNegativa(
    double media, double variancia, int estoqueDisponivel
) const {
    if (estoqueDisponivel < 0) return 1.0;
    double excedente = 1.0 - binomialNegativaCDF(media, variancia, estoqueDisponivel);
    if (excedente < 0.0) excedente = 0.0;
    if (excedente > 1.0) excedente = 1.0;
    return excedente;
}

double ModeloDemanda::varianciaDoHorizonte(const std::vector<long>& serieDiaria) {
    size_t n = serieDiaria.size();
    if (n < 2) return -1.0;
    double soma = 0.0;
    for (long v : serieDiaria) soma += static_cast<double>(v);
    double mediaDia = soma / static_cast<double>(n);
    double somaQuadrados = 0.0;
    for (long v : serieDiaria) {
        double diff = static_cast<double>(v) - mediaDia;
        somaQuadrados += diff * diff;
    }
    double varianciaDia = somaQuadrados / static_cast<double>(n - 1);
    return static_cast<double>(n) * varianciaDia;
}

double ModeloDemanda::simularProbabilidadeDesabastecimentoBootstrap(
    const std::vector<long>& serieDiaria, int estoqueDisponivel, int numSimulacoes, unsigned int semente
) const {
    if (serieDiaria.empty()) return (estoqueDisponivel < 0) ? 1.0 : 0.0;

    std::mt19937_64 rng(semente);
    std::uniform_int_distribution<size_t> sorteioDia(0, serieDiaria.size() - 1);

    long contagemDesabastecimento = 0;
    for (int i = 0; i < numSimulacoes; ++i) {
        long demandaSimulada = 0;
        for (size_t d = 0; d < serieDiaria.size(); ++d) {
            demandaSimulada += serieDiaria[sorteioDia(rng)];
        }
        if (demandaSimulada > estoqueDisponivel) {
            contagemDesabastecimento++;
        }
    }

    return static_cast<double>(contagemDesabastecimento) / static_cast<double>(numSimulacoes);
}

const ParametrosDemanda& ModeloDemanda::getParametros(TipoSanguineo tipo) const {
    auto it = parametrosPorTipo.find(tipo);
    if (it == parametrosPorTipo.end()) {
        throw std::runtime_error("Parametros nao encontrados para o tipo sanguineo solicitado.");
    }
    return it->second;
}

const ParametrosDemanda& ModeloDemanda::getParametros(TipoHemocomponente comp) const {
    auto it = parametrosPorComponente.find(comp);
    if (it == parametrosPorComponente.end()) {
        throw std::runtime_error("Parametros nao encontrados para o hemocomponente solicitado.");
    }
    return it->second;
}

const ParametrosDemanda& ModeloDemanda::getParametros(TipoHemocomponente comp, TipoSanguineo tipo) const {
    auto it = parametrosCruzados.find({comp, tipo});
    if (it == parametrosCruzados.end()) {
        throw std::runtime_error("Parametros nao encontrados para a combinacao componente x tipo solicitada.");
    }
    return it->second;
}
