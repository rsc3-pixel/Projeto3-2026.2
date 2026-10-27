#include "AcumuladorDemanda.hpp"
#include <cmath>

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

    int somaReq = 0;
    for (const auto& item : req.itens) {
        acumularItem(item);
        somaReq += item.quantidade;
    }
    volumesPorRequisicao.push_back(somaReq);
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
