#include "Dominio.hpp"
#include "AcumuladorEstoque.hpp"
#include "AcumuladorDemanda.hpp"
#include "ModeloDemanda.hpp"
#include "CalculadorDesabastecimento.hpp"
#include <iostream>
#include <cassert>
#include <cmath>

void testarAcumuladorEstoque() {
    std::cout << "[TESTE] testarAcumuladorEstoque... ";
    AcumuladorEstoque est;

    // Bolsa 1: Valida O+ Hemacias
    Bolsa b1("B1", TipoSanguineo::O_POS, TipoHemocomponente::CONCENTRADO_HEMACIAS, 300, "2026-08-01", "2026-09-12", "HC01", 10, false);
    // Bolsa 2: Vencida O+ Plaquetas
    Bolsa b2("B2", TipoSanguineo::O_POS, TipoHemocomponente::CONCENTRADO_PLAQUETAS, 55, "2026-07-01", "2026-07-06", "HC01", -5, true);
    // Bolsa 3: Valida A+ Hemacias
    Bolsa b3("B3", TipoSanguineo::A_POS, TipoHemocomponente::CONCENTRADO_HEMACIAS, 310, "2026-08-05", "2026-09-16", "HC02", 15, false);

    est += b1;
    est += b2;
    est += b3;

    assert(est.getTotalBolsas() == 3);
    assert(est.getTotalValidas() == 2);
    assert(est.getTotalVencidas() == 1);
    assert(std::abs(est.getTaxaDescarteGlobal() - (100.0 / 3.0)) < 0.01);
    assert(est.getEstoqueValido(TipoSanguineo::O_POS) == 1);
    assert(est.getEstoqueTotal(TipoSanguineo::O_POS) == 2);
    assert(est.getEstoqueValido(TipoSanguineo::A_POS) == 1);
    assert(est.getEstoqueTotal(TipoHemocomponente::CONCENTRADO_PLAQUETAS) == 1);
    assert(est.getEstoqueVencido(TipoHemocomponente::CONCENTRADO_PLAQUETAS) == 1);
    assert(est.getTaxaDescarte(TipoHemocomponente::CONCENTRADO_PLAQUETAS) == 100.0);

    // Teste de medidas de dispersao da validade (HU-06 / PI3-32)
    assert(std::abs(est.getMediaDiasValidade() - 12.5) < 0.001);
    assert(std::abs(est.getMedianaDiasValidade() - 12.5) < 0.001);
    assert(est.getMinDiasValidade() == 10);
    assert(est.getMaxDiasValidade() == 15);
    assert(est.getBolsasCriticasValidade(7) == 0);
    assert(est.getBolsasCriticasValidade(12) == 1); // b1 tem 10 dias

    std::cout << "PASSOU!\n";
}

void testarAcumuladorDemanda() {
    std::cout << "[TESTE] testarAcumuladorDemanda (Analogia da Loja)... ";
    AcumuladorDemanda dem;

    Requisicao req1;
    req1.id = "REQ1";
    req1.prioridade = "ROTINA";
    req1.itens.push_back(ItemRequisicao("IT1", TipoSanguineo::O_POS, TipoHemocomponente::CONCENTRADO_HEMACIAS, 4));
    req1.itens.push_back(ItemRequisicao("IT2", TipoSanguineo::A_POS, TipoHemocomponente::CONCENTRADO_PLAQUETAS, 2));

    Requisicao req2;
    req2.id = "REQ2";
    req2.prioridade = "EMERGENCIA";
    req2.itens.push_back(ItemRequisicao("IT3", TipoSanguineo::O_POS, TipoHemocomponente::CONCENTRADO_HEMACIAS, 3));

    dem += req1;
    dem += req2;

    assert(dem.getTotalRequisicoes() == 2);
    assert(dem.getTotalBolsasDemandadas() == 9);
    assert(dem.getDemanda(TipoSanguineo::O_POS) == 7);
    assert(dem.getDemanda(TipoSanguineo::A_POS) == 2);
    assert(dem.getDemanda(TipoHemocomponente::CONCENTRADO_HEMACIAS) == 7);
    assert(dem.getDemanda(TipoHemocomponente::CONCENTRADO_PLAQUETAS) == 2);
    assert(dem.getRequisicoesPorPrioridade("ROTINA") == 1);
    assert(dem.getRequisicoesPorPrioridade("EMERGENCIA") == 1);

    std::cout << "PASSOU!\n";
}

void testarModeloDemandaEstatistico() {
    std::cout << "[TESTE] testarModeloDemandaEstatistico (Poisson e Normal)... ";
    ModeloDemanda mod;

    // Teste 1: PMF de Poisson
    // Para lambda = 2.0 e k = 2: P(X=2) = (e^-2 * 2^2) / 2! = (0.135335 * 4) / 2 = 0.27067
    double pmf = ModeloDemanda::poissonPMF(2.0, 2);
    assert(std::abs(pmf - 0.27067) < 0.001);

    // Teste 2: CDF de Poisson
    // P(X <= 1) para lambda = 2: P(0) + P(1) = e^-2 + 2*e^-2 = 3 * 0.135335 = 0.4060
    double cdf = ModeloDemanda::poissonCDF(2.0, 1);
    assert(std::abs(cdf - 0.4060) < 0.005);

    // Teste 3: Probabilidade de Excedente (Desabastecimento)
    // Se lambda = 10 e estoque S = 5:
    double probDesab = mod.calcularProbabilidadeExcedentePoisson(10.0, 5);
    assert(probDesab > 0.90); // Alta chance de faltar sangue

    // Se lambda = 5 e estoque S = 15:
    double probSeguro = mod.calcularProbabilidadeExcedentePoisson(5.0, 15);
    assert(probSeguro < 0.001); // Chance insignificante de faltar sangue

    // Teste 4: Monte Carlo
    double probMC = mod.simularProbabilidadeDesabastecimentoMonteCarlo(10.0, 5, 20000, 2026);
    assert(std::abs(probMC - probDesab) < 0.02);

    std::cout << "PASSOU!\n";
}

void testarCalculadorDesabastecimento() {
    std::cout << "[TESTE] testarCalculadorDesabastecimento... ";
    AcumuladorEstoque est;
    AcumuladorDemanda dem;
    ModeloDemanda mod;

    // Criando estoque: 10 bolsas O+, 2 bolsas AB-
    for (int i = 0; i < 10; ++i) {
        est += Bolsa("B" + std::to_string(i), TipoSanguineo::O_POS, TipoHemocomponente::CONCENTRADO_HEMACIAS, 300, "", "", "HC01", 10, false);
    }
    for (int i = 0; i < 2; ++i) {
        est += Bolsa("BAB" + std::to_string(i), TipoSanguineo::AB_NEG, TipoHemocomponente::CONCENTRADO_HEMACIAS, 300, "", "", "HC01", 10, false);
    }

    // Criando demanda: 8 bolsas O+, 10 bolsas AB-
    Requisicao r1;
    r1.itens.push_back(ItemRequisicao("IT1", TipoSanguineo::O_POS, TipoHemocomponente::CONCENTRADO_HEMACIAS, 8));
    r1.itens.push_back(ItemRequisicao("IT2", TipoSanguineo::AB_NEG, TipoHemocomponente::CONCENTRADO_HEMACIAS, 10));
    dem += r1;

    mod.calibrarComDadosAcumulados(dem.getDemandaPorTipo(), dem.getDemandaPorComponente(), {}, dem.getTotalBolsasDemandadas());

    CalculadorDesabastecimento calc(est, dem, mod);
    auto rel = calc.avaliarPorTipoSanguineo();

    // AB- deve ser o primeiro da lista devido ao risco critico (demanda 10, estoque 2)
    assert(rel[0].chave == "AB_NEG");
    assert(rel[0].nivelRisco == "CRITICO");
    assert(rel[0].deficitPontual == 8);
    assert(rel[0].probDesabastecimentoPoisson > 0.95);
    // Sem datas nas requisicoes nao ha serie diaria: o modelo assume variancia = media (Poisson)
    assert(rel[0].indiceDispersao == 1.0);
    assert(std::abs(rel[0].probDesabastecimento - rel[0].probDesabastecimentoPoisson) < 1e-12);

    std::cout << "PASSOU!\n";
}

Requisicao requisicaoNoDia(const std::string& data, TipoSanguineo tipo, int quantidade) {
    Requisicao r;
    r.data = data;
    r.prioridade = "ROTINA";
    r.itens.push_back(ItemRequisicao("IT", tipo, TipoHemocomponente::CONCENTRADO_HEMACIAS, quantidade));
    return r;
}

void testarSerieDiaria() {
    std::cout << "[TESTE] testarSerieDiaria (dias sem pedido entram com zero)... ";
    AcumuladorDemanda dem;
    dem += requisicaoNoDia("2026-07-30", TipoSanguineo::O_NEG, 3);
    dem += requisicaoNoDia("2026-08-02", TipoSanguineo::O_NEG, 2);
    dem += requisicaoNoDia("2026-08-02", TipoSanguineo::A_POS, 5);

    // 30/07 a 02/08 atravessa a virada de mes: 4 dias
    auto serieONeg = dem.getSerieDiaria(TipoSanguineo::O_NEG);
    assert((serieONeg == std::vector<long>{3, 0, 0, 2}));
    auto serieAPos = dem.getSerieDiaria(TipoSanguineo::A_POS);
    assert((serieAPos == std::vector<long>{0, 0, 0, 5}));
    auto serieHemacias = dem.getSerieDiaria(TipoHemocomponente::CONCENTRADO_HEMACIAS);
    assert((serieHemacias == std::vector<long>{3, 0, 0, 7}));

    // Requisicao sem data continua acumulando o total, mas nao entra na serie
    AcumuladorDemanda semData;
    semData += requisicaoNoDia("", TipoSanguineo::O_NEG, 4);
    assert(semData.getDemanda(TipoSanguineo::O_NEG) == 4);
    assert(semData.getSerieDiaria(TipoSanguineo::O_NEG).empty());

    std::cout << "PASSOU!\n";
}

void testarBinomialNegativa() {
    std::cout << "[TESTE] testarBinomialNegativa (sobredispersao)... ";
    ModeloDemanda mod;

    // Sem sobredispersao (variancia = media) a Binomial Negativa e a propria Poisson
    for (int k : {0, 3, 10, 25}) {
        assert(std::abs(ModeloDemanda::binomialNegativaCDF(10.0, 10.0, k) - ModeloDemanda::poissonCDF(10.0, k)) < 1e-12);
    }

    // Valor de referencia: media 4, variancia 8 -> r = 4, p = 0.5; P(X = 0) = 0.5^4 = 0.0625
    assert(std::abs(ModeloDemanda::binomialNegativaCDF(4.0, 8.0, 0) - 0.0625) < 1e-9);
    // P(X <= 2) = 0.0625 + 0.125 + 0.15625 = 0.34375
    assert(std::abs(ModeloDemanda::binomialNegativaCDF(4.0, 8.0, 2) - 0.34375) < 1e-9);

    // Com estoque acima da media, mais variancia significa mais risco de faltar
    double riscoPoisson = mod.calcularProbabilidadeExcedentePoisson(97.0, 119);
    double riscoBinNeg = mod.calcularProbabilidadeExcedenteBinomialNegativa(97.0, 3.3 * 97.0, 119);
    assert(riscoBinNeg > 3.0 * riscoPoisson);

    // Media alta nao causa underflow
    double cdfAlta = ModeloDemanda::binomialNegativaCDF(818.0, 2500.0, 2000);
    assert(cdfAlta > 0.999 && cdfAlta <= 1.0);

    std::cout << "PASSOU!\n";
}

void testarBootstrap() {
    std::cout << "[TESTE] testarBootstrap (Monte Carlo reamostrando os dias)... ";
    ModeloDemanda mod;

    // Variancia do horizonte = N * s²: serie {0, 2, 4} tem s² = 4, N = 3 -> 12
    assert(std::abs(ModeloDemanda::varianciaDoHorizonte({0, 2, 4}) - 12.0) < 1e-9);
    assert(ModeloDemanda::varianciaDoHorizonte({5}) < 0.0);

    // Serie constante: a demanda do horizonte e sempre 3 * 2 = 6
    std::vector<long> constante = {2, 2, 2};
    assert(mod.simularProbabilidadeDesabastecimentoBootstrap(constante, 5, 1000) == 1.0);
    assert(mod.simularProbabilidadeDesabastecimentoBootstrap(constante, 6, 1000) == 0.0);

    // Mesma semente, mesmo resultado
    std::vector<long> serie = {0, 1, 0, 4, 2, 0, 3, 1};
    double p1 = mod.simularProbabilidadeDesabastecimentoBootstrap(serie, 12, 20000, 2026);
    double p2 = mod.simularProbabilidadeDesabastecimentoBootstrap(serie, 12, 20000, 2026);
    assert(p1 == p2);

    // O bootstrap concorda com a Binomial Negativa ajustada a mesma serie
    double variancia = ModeloDemanda::varianciaDoHorizonte(serie);
    double pBinNeg = mod.calcularProbabilidadeExcedenteBinomialNegativa(11.0, variancia, 12);
    assert(std::abs(p1 - pBinNeg) < 0.05);

    std::cout << "PASSOU!\n";
}

void testarCalculadorComSerieDiaria() {
    std::cout << "[TESTE] testarCalculadorComSerieDiaria... ";
    AcumuladorEstoque est;
    AcumuladorDemanda dem;
    ModeloDemanda mod;

    for (int i = 0; i < 12; ++i) {
        est += Bolsa("B" + std::to_string(i), TipoSanguineo::O_NEG, TipoHemocomponente::CONCENTRADO_HEMACIAS, 300, "", "", "HC01", 10, false);
    }
    // 10 bolsas em 5 dias, concentradas em picos: variancia maior que a media
    dem += requisicaoNoDia("2026-08-01", TipoSanguineo::O_NEG, 4);
    dem += requisicaoNoDia("2026-08-03", TipoSanguineo::O_NEG, 6);
    dem += requisicaoNoDia("2026-08-05", TipoSanguineo::O_NEG, 0);

    CalculadorDesabastecimento calc(est, dem, mod);
    auto rel = calc.avaliarPorTipoSanguineo();
    const RelatorioDesabastecimento* oNeg = nullptr;
    for (const auto& r : rel) {
        if (r.chave == "O_NEG") oNeg = &r;
    }
    assert(oNeg != nullptr);
    assert(oNeg->demandaEsperada == 10);
    assert(oNeg->indiceDispersao > 1.0);
    assert(oNeg->probDesabastecimento > oNeg->probDesabastecimentoPoisson);

    std::cout << "PASSOU!\n";
}

int main() {
    std::cout << "==============================================================================\n";
    std::cout << " EXECUTANDO SUITE DE TESTES AUTOMATIZADOS (C++ / ROTA VITAL)\n";
    std::cout << "==============================================================================\n";

    testarAcumuladorEstoque();
    testarAcumuladorDemanda();
    testarModeloDemandaEstatistico();
    testarCalculadorDesabastecimento();
    testarSerieDiaria();
    testarBinomialNegativa();
    testarBootstrap();
    testarCalculadorComSerieDiaria();

    std::cout << "==============================================================================\n";
    std::cout << " TODOS OS TESTES PASSARAM COM 100% DE SUCESSO!\n";
    std::cout << "==============================================================================\n";
    return 0;
}
