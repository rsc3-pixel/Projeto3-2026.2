#include "LeitorDadosSinteticos.hpp"
#include "nlohmann/json.hpp"
#include <fstream>
#include <iostream>
#include <random>

using json = nlohmann::json;

bool LeitorDadosSinteticos::carregarDoJson(
    const std::string& caminhoArquivo,
    std::vector<Bolsa>& bolsas,
    std::vector<Requisicao>& requisicoes
) {
    bolsas.clear();
    requisicoes.clear();

    std::ifstream arq(caminhoArquivo);
    if (!arq.is_open()) {
        return false;
    }

    try {
        json j;
        arq >> j;

        if (j.contains("bolsas") && j["bolsas"].is_array()) {
            for (const auto& item : j["bolsas"]) {
                Bolsa b;
                b.codigo = item.value("codigo", "");
                b.tipoSanguineo = stringToTipoSanguineo(item.value("tipo_sanguineo", "O_POS"));
                b.componente = stringToHemocomponente(item.value("componente", "CONCENTRADO_HEMACIAS"));
                b.volumeMl = item.value("volume_ml", 0);
                b.dataColeta = item.value("data_coleta", "");
                b.dataValidade = item.value("data_validade", "");
                b.unidadeId = item.value("unidade_id", "");
                b.diasAteVencer = item.value("dias_ate_vencer", 0);
                b.vencida = item.value("vencida", false);
                bolsas.push_back(b);
            }
        }

        if (j.contains("requisicoes") && j["requisicoes"].is_array()) {
            for (const auto& rItem : j["requisicoes"]) {
                Requisicao r;
                r.id = rItem.value("id", "");
                r.unidadeId = rItem.value("unidade_id", "");
                r.cidade = rItem.value("cidade", "");
                r.prioridade = rItem.value("prioridade", "ROTINA");
                r.prazoHoras = rItem.value("prazo_horas", 72);
                r.totalBolsas = rItem.value("total_bolsas", 0);

                if (rItem.contains("itens") && rItem["itens"].is_array()) {
                    for (const auto& itObj : rItem["itens"]) {
                        ItemRequisicao it;
                        it.id = itObj.value("id", "");
                        it.tipoSanguineo = stringToTipoSanguineo(itObj.value("tipo_sanguineo", "O_POS"));
                        it.componente = stringToHemocomponente(itObj.value("componente", "CONCENTRADO_HEMACIAS"));
                        it.quantidade = itObj.value("quantidade", 1);
                        r.itens.push_back(it);
                    }
                }
                requisicoes.push_back(r);
            }
        }
        return true;
    } catch (const std::exception& e) {
        std::cerr << "Erro no parse do JSON " << caminhoArquivo << ": " << e.what() << "\n";
        return false;
    }
}

void LeitorDadosSinteticos::gerarDadosNativos(
    std::vector<Bolsa>& bolsas,
    std::vector<Requisicao>& requisicoes,
    unsigned int sementeBolsas,
    unsigned int sementeRequisicoes
) {
    bolsas.clear();
    requisicoes.clear();

    // Pesos da populacao brasileira (premissas.py)
    std::vector<TipoSanguineo> tipos = {
        TipoSanguineo::O_POS, TipoSanguineo::A_POS, TipoSanguineo::B_POS, TipoSanguineo::O_NEG,
        TipoSanguineo::A_NEG, TipoSanguineo::AB_POS, TipoSanguineo::B_NEG, TipoSanguineo::AB_NEG
    };
    std::vector<double> pesosTipos = { 0.360, 0.340, 0.080, 0.090, 0.080, 0.025, 0.020, 0.005 };

    std::vector<TipoHemocomponente> comps = {
        TipoHemocomponente::CONCENTRADO_HEMACIAS,
        TipoHemocomponente::CONCENTRADO_PLAQUETAS,
        TipoHemocomponente::PLASMA_FRESCO_CONGELADO,
        TipoHemocomponente::CRIOPRECIPITADO
    };
    std::vector<double> pesosComps = { 0.55, 0.20, 0.20, 0.05 };

    std::mt19937 rngBolsas(sementeBolsas);
    std::discrete_distribution<int> distTipos(pesosTipos.begin(), pesosTipos.end());
    std::discrete_distribution<int> distComps(pesosComps.begin(), pesosComps.end());
    std::uniform_int_distribution<int> distJanela(0, 60);

    // Gerar 2000 bolsas
    for (int i = 1; i <= 2000; ++i) {
        TipoHemocomponente comp = comps[distComps(rngBolsas)];
        TipoSanguineo tipo = tipos[distTipos(rngBolsas)];
        int validade = getValidadePadraoDias(comp);
        int diasAtras = distJanela(rngBolsas);
        int diasRestantes = validade - diasAtras;
        bool vencida = (diasRestantes < 0);

        Bolsa b;
        b.codigo = "BOL" + std::to_string(100000 + i).substr(1);
        b.componente = comp;
        b.tipoSanguineo = tipo;
        b.volumeMl = 300;
        b.diasAteVencer = diasRestantes;
        b.vencida = vencida;
        b.unidadeId = "HC01";
        bolsas.push_back(b);
    }

    // Gerar 300 requisicoes
    std::mt19937 rngReq(sementeRequisicoes);
    std::vector<std::string> prioridades = { "ROTINA", "URGENTE", "EMERGENCIA" };
    std::vector<double> pesosPrioridades = { 0.65, 0.28, 0.07 };
    std::discrete_distribution<int> distPrio(pesosPrioridades.begin(), pesosPrioridades.end());
    std::uniform_int_distribution<int> distQtdItens(1, 3);
    std::uniform_int_distribution<int> distQtdBolsas(1, 4);

    for (int i = 1; i <= 300; ++i) {
        Requisicao r;
        r.id = "REQ" + std::to_string(100000 + i).substr(1);
        r.prioridade = prioridades[distPrio(rngReq)];
        r.unidadeId = "AT01";
        r.cidade = "Recife";
        r.prazoHoras = (r.prioridade == "EMERGENCIA" ? 2 : (r.prioridade == "URGENTE" ? 12 : 72));

        int numItens = (r.prioridade == "EMERGENCIA") ? 1 : distQtdItens(rngReq);
        r.totalBolsas = 0;
        for (int j = 1; j <= numItens; ++j) {
            ItemRequisicao item;
            item.id = "ITEM" + std::to_string(i) + "-" + std::to_string(j);
            item.tipoSanguineo = tipos[distTipos(rngReq)];
            item.componente = comps[distComps(rngReq)];
            item.quantidade = distQtdBolsas(rngReq);
            r.itens.push_back(item);
            r.totalBolsas += item.quantidade;
        }
        requisicoes.push_back(r);
    }
}
