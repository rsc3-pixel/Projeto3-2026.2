#include "AcumuladorDemanda.hpp"
#include <chrono>
#include <cmath>

namespace {
// Converte "AAAA-MM-DD" em dia ordinal (dias desde 1970-01-01); -1 se invalida.
long diaOrdinal(const std::string& data) {
    if (data.size() < 10) return -1;
    try {
        std::chrono::year_month_day ymd{
            std::chrono::year{std::stoi(data.substr(0, 4))},
            std::chrono::month{static_cast<unsigned>(std::stoi(data.substr(5, 2)))},
            std::chrono::day{static_cast<unsigned>(std::stoi(data.substr(8, 2)))}
        };
        if (!ymd.ok()) return -1;
        return std::chrono::sys_days{ymd}.time_since_epoch().count();
    } catch (const std::exception&) {
        return -1;
    }
}
}

AcumuladorDemanda::AcumuladorDemanda() {
    reiniciar();
}

void AcumuladorDemanda::reiniciar() {
    totalRequisicoes = 0;
    totalBolsasDemandadas = 0;
    demandaPorTipo.clear();
    demandaPorComponente.clear();
    demandaCruzada.clear();
    requisicoesPorPrioridade.clear();
    volumesPorRequisicao.clear();
    demandaDiaria.clear();

    std::vector<TipoSanguineo> tipos = {
        TipoSanguineo::O_POS, TipoSanguineo::A_POS, TipoSanguineo::B_POS, TipoSanguineo::O_NEG,
        TipoSanguineo::A_NEG, TipoSanguineo::AB_POS, TipoSanguineo::B_NEG, TipoSanguineo::AB_NEG
    };
    for (auto t : tipos) {
        demandaPorTipo[t] = 0;
    }

    std::vector<TipoHemocomponente> comps = {
        TipoHemocomponente::CONCENTRADO_HEMACIAS,
        TipoHemocomponente::CONCENTRADO_PLAQUETAS,
        TipoHemocomponente::PLASMA_FRESCO_CONGELADO,
        TipoHemocomponente::CRIOPRECIPITADO
    };
    for (auto c : comps) {
        demandaPorComponente[c] = 0;
        for (auto t : tipos) {
            demandaCruzada[{c, t}] = 0;
        }
    }

    requisicoesPorPrioridade["ROTINA"] = 0;
    requisicoesPorPrioridade["URGENTE"] = 0;
    requisicoesPorPrioridade["EMERGENCIA"] = 0;
}

void AcumuladorDemanda::acumularItem(const ItemRequisicao& item) {
    totalBolsasDemandadas += item.quantidade;
    demandaPorTipo[item.tipoSanguineo] += item.quantidade;
    demandaPorComponente[item.componente] += item.quantidade;
    demandaCruzada[{item.componente, item.tipoSanguineo}] += item.quantidade;
}

void AcumuladorDemanda::acumularRequisicao(const Requisicao& req) {
    totalRequisicoes++;
    requisicoesPorPrioridade[req.prioridade]++;

    long dia = diaOrdinal(req.data);
    int somaReq = 0;
    for (const auto& item : req.itens) {
        acumularItem(item);
        somaReq += item.quantidade;
        if (dia >= 0) {
            demandaDiaria[dia][{item.componente, item.tipoSanguineo}] += item.quantidade;
        }
    }
    volumesPorRequisicao.push_back(somaReq);
}

template <typename Filtro>
std::vector<long> AcumuladorDemanda::montarSerieDiaria(Filtro filtro) const {
    if (demandaDiaria.empty()) return {};
    long primeiro = demandaDiaria.begin()->first;
    long ultimo = demandaDiaria.rbegin()->first;
    std::vector<long> serie(static_cast<size_t>(ultimo - primeiro + 1), 0);
    for (const auto& [dia, porChave] : demandaDiaria) {
        for (const auto& [chave, qtd] : porChave) {
            if (filtro(chave.first, chave.second)) {
                serie[static_cast<size_t>(dia - primeiro)] += qtd;
            }
        }
    }
    return serie;
}

std::vector<long> AcumuladorDemanda::getSerieDiaria(TipoSanguineo tipo) const {
    return montarSerieDiaria([tipo](TipoHemocomponente, TipoSanguineo t) { return t == tipo; });
}

std::vector<long> AcumuladorDemanda::getSerieDiaria(TipoHemocomponente comp) const {
    return montarSerieDiaria([comp](TipoHemocomponente c, TipoSanguineo) { return c == comp; });
}

std::vector<long> AcumuladorDemanda::getSerieDiaria(TipoHemocomponente comp, TipoSanguineo tipo) const {
    return montarSerieDiaria([comp, tipo](TipoHemocomponente c, TipoSanguineo t) { return c == comp && t == tipo; });
}

AcumuladorDemanda& AcumuladorDemanda::operator+=(const ItemRequisicao& item) {
    acumularItem(item);
    return *this;
}

AcumuladorDemanda& AcumuladorDemanda::operator+=(const Requisicao& req) {
    acumularRequisicao(req);
    return *this;
}

long AcumuladorDemanda::getDemanda(TipoSanguineo tipo) const {
    auto it = demandaPorTipo.find(tipo);
    return (it != demandaPorTipo.end()) ? it->second : 0;
}

long AcumuladorDemanda::getDemanda(TipoHemocomponente comp) const {
    auto it = demandaPorComponente.find(comp);
    return (it != demandaPorComponente.end()) ? it->second : 0;
}

long AcumuladorDemanda::getDemanda(TipoHemocomponente comp, TipoSanguineo tipo) const {
    auto it = demandaCruzada.find({comp, tipo});
    return (it != demandaCruzada.end()) ? it->second : 0;
}

long AcumuladorDemanda::getRequisicoesPorPrioridade(const std::string& prioridade) const {
    auto it = requisicoesPorPrioridade.find(prioridade);
    return (it != requisicoesPorPrioridade.end()) ? it->second : 0;
}

double AcumuladorDemanda::getMediaBolsasPorRequisicao() const {
    if (totalRequisicoes == 0) return 0.0;
    return static_cast<double>(totalBolsasDemandadas) / static_cast<double>(totalRequisicoes);
}

double AcumuladorDemanda::getDesvioPadraoBolsasPorRequisicao() const {
    if (volumesPorRequisicao.size() < 2) return 0.0;
    double media = getMediaBolsasPorRequisicao();
    double somaQuadrados = 0.0;
    for (int v : volumesPorRequisicao) {
        double diff = v - media;
        somaQuadrados += diff * diff;
    }
    return std::sqrt(somaQuadrados / (volumesPorRequisicao.size() - 1));
}
