#include "AcumuladorEstoque.hpp"
#include <algorithm>
#include <cmath>
#include <numeric>

AcumuladorEstoque::AcumuladorEstoque() {
    reiniciar();
}

void AcumuladorEstoque::reiniciar() {
    totalBolsas = 0;
    totalValidas = 0;
    totalVencidas = 0;
    estoqueGeralPorTipo.clear();
    estoqueValidoPorTipo.clear();
    estoqueVencidoPorTipo.clear();
    estoqueGeralPorComponente.clear();
    estoqueValidoPorComponente.clear();
    estoqueVencidoPorComponente.clear();
    estoqueValidoCruzado.clear();
    diasAteVencerValidas.clear();

    // Inicializa todos os tipos com 0
    std::vector<TipoSanguineo> tipos = {
        TipoSanguineo::O_POS, TipoSanguineo::A_POS, TipoSanguineo::B_POS, TipoSanguineo::O_NEG,
        TipoSanguineo::A_NEG, TipoSanguineo::AB_POS, TipoSanguineo::B_NEG, TipoSanguineo::AB_NEG
    };
    for (auto t : tipos) {
        estoqueGeralPorTipo[t] = 0;
        estoqueValidoPorTipo[t] = 0;
        estoqueVencidoPorTipo[t] = 0;
    }

    std::vector<TipoHemocomponente> comps = {
        TipoHemocomponente::CONCENTRADO_HEMACIAS,
        TipoHemocomponente::CONCENTRADO_PLAQUETAS,
        TipoHemocomponente::PLASMA_FRESCO_CONGELADO,
        TipoHemocomponente::CRIOPRECIPITADO
    };
    for (auto c : comps) {
        estoqueGeralPorComponente[c] = 0;
        estoqueValidoPorComponente[c] = 0;
        estoqueVencidoPorComponente[c] = 0;
        for (auto t : tipos) {
            estoqueValidoCruzado[{c, t}] = 0;
        }
    }
}

void AcumuladorEstoque::acumular(const Bolsa& bolsa) {
    totalBolsas++;
    estoqueGeralPorTipo[bolsa.tipoSanguineo]++;
    estoqueGeralPorComponente[bolsa.componente]++;

    if (bolsa.vencida) {
        totalVencidas++;
        estoqueVencidoPorTipo[bolsa.tipoSanguineo]++;
        estoqueVencidoPorComponente[bolsa.componente]++;
    } else {
        totalValidas++;
        estoqueValidoPorTipo[bolsa.tipoSanguineo]++;
        estoqueValidoPorComponente[bolsa.componente]++;
        estoqueValidoCruzado[{bolsa.componente, bolsa.tipoSanguineo}]++;
        diasAteVencerValidas.push_back(bolsa.diasAteVencer);
    }
}

AcumuladorEstoque& AcumuladorEstoque::operator+=(const Bolsa& bolsa) {
    acumular(bolsa);
    return *this;
}

double AcumuladorEstoque::getTaxaDescarteGlobal() const {
    if (totalBolsas == 0) return 0.0;
    return (100.0 * static_cast<double>(totalVencidas)) / static_cast<double>(totalBolsas);
}

long AcumuladorEstoque::getEstoqueValido(TipoSanguineo tipo) const {
    auto it = estoqueValidoPorTipo.find(tipo);
    return (it != estoqueValidoPorTipo.end()) ? it->second : 0;
}

long AcumuladorEstoque::getEstoqueTotal(TipoSanguineo tipo) const {
    auto it = estoqueGeralPorTipo.find(tipo);
    return (it != estoqueGeralPorTipo.end()) ? it->second : 0;
}

long AcumuladorEstoque::getEstoqueVencido(TipoSanguineo tipo) const {
    auto it = estoqueVencidoPorTipo.find(tipo);
    return (it != estoqueVencidoPorTipo.end()) ? it->second : 0;
}

long AcumuladorEstoque::getEstoqueValido(TipoHemocomponente comp) const {
    auto it = estoqueValidoPorComponente.find(comp);
    return (it != estoqueValidoPorComponente.end()) ? it->second : 0;
}

long AcumuladorEstoque::getEstoqueTotal(TipoHemocomponente comp) const {
    auto it = estoqueGeralPorComponente.find(comp);
    return (it != estoqueGeralPorComponente.end()) ? it->second : 0;
}

long AcumuladorEstoque::getEstoqueVencido(TipoHemocomponente comp) const {
    auto it = estoqueVencidoPorComponente.find(comp);
    return (it != estoqueVencidoPorComponente.end()) ? it->second : 0;
}

double AcumuladorEstoque::getTaxaDescarte(TipoHemocomponente comp) const {
    long tot = getEstoqueTotal(comp);
    if (tot == 0) return 0.0;
    long venc = getEstoqueVencido(comp);
    return (100.0 * static_cast<double>(venc)) / static_cast<double>(tot);
}

long AcumuladorEstoque::getEstoqueValido(TipoHemocomponente comp, TipoSanguineo tipo) const {
    auto it = estoqueValidoCruzado.find({comp, tipo});
    return (it != estoqueValidoCruzado.end()) ? it->second : 0;
}

double AcumuladorEstoque::getMediaDiasValidade() const {
    if (diasAteVencerValidas.empty()) return 0.0;
    double soma = std::accumulate(diasAteVencerValidas.begin(), diasAteVencerValidas.end(), 0.0);
    return soma / static_cast<double>(diasAteVencerValidas.size());
}

double AcumuladorEstoque::getMedianaDiasValidade() const {
    if (diasAteVencerValidas.empty()) return 0.0;
    std::vector<int> copia = diasAteVencerValidas;
    std::sort(copia.begin(), copia.end());
    size_t n = copia.size();
    if (n % 2 == 1) {
        return static_cast<double>(copia[n / 2]);
    } else {
        return (static_cast<double>(copia[(n / 2) - 1]) + static_cast<double>(copia[n / 2])) / 2.0;
    }
}

double AcumuladorEstoque::getDesvioPadraoDiasValidade() const {
    if (diasAteVencerValidas.size() < 2) return 0.0;
    double media = getMediaDiasValidade();
    double somaQuadrados = 0.0;
    for (int d : diasAteVencerValidas) {
        double diff = static_cast<double>(d) - media;
        somaQuadrados += diff * diff;
    }
    return std::sqrt(somaQuadrados / static_cast<double>(diasAteVencerValidas.size() - 1));
}

int AcumuladorEstoque::getMinDiasValidade() const {
    if (diasAteVencerValidas.empty()) return 0;
    return *std::min_element(diasAteVencerValidas.begin(), diasAteVencerValidas.end());
}

int AcumuladorEstoque::getMaxDiasValidade() const {
    if (diasAteVencerValidas.empty()) return 0;
    return *std::max_element(diasAteVencerValidas.begin(), diasAteVencerValidas.end());
}

long AcumuladorEstoque::getBolsasCriticasValidade(int limiteDias) const {
    long count = 0;
    for (int d : diasAteVencerValidas) {
        if (d >= 0 && d <= limiteDias) {
            count++;
        }
    }
    return count;
}

double AcumuladorEstoque::getPercentualBolsasCriticas(int limiteDias) const {
    if (totalValidas == 0) return 0.0;
    long crit = getBolsasCriticasValidade(limiteDias);
    return (100.0 * static_cast<double>(crit)) / static_cast<double>(totalValidas);
}
