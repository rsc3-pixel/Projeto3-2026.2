#ifndef DOMINIO_HPP
#define DOMINIO_HPP

#include <string>
#include <vector>
#include <iostream>

/**
 * @brief Tipos sanguíneos contemplados no sistema Rota Vital.
 * Representa os 8 grupos clássicos do sistema ABO/Rh.
 */
enum class TipoSanguineo {
    O_POS,
    A_POS,
    B_POS,
    O_NEG,
    A_NEG,
    AB_POS,
    B_NEG,
    AB_NEG
};

/**
 * @brief Tipos de hemocomponentes com suas respectivas validades biológicas.
 */
enum class TipoHemocomponente {
    CONCENTRADO_HEMACIAS,    // Validade: 42 dias
    CONCENTRADO_PLAQUETAS,   // Validade: 5 dias (ultra-perecível)
    PLASMA_FRESCO_CONGELADO, // Validade: 365 dias
    CRIOPRECIPITADO          // Validade: 365 dias
};

// Funções de conversão string <-> enum
std::string tipoSanguineoToString(TipoSanguineo tipo);
std::string tipoSanguineoToDisplay(TipoSanguineo tipo);
TipoSanguineo stringToTipoSanguineo(const std::string& str);

std::string hemocomponenteToString(TipoHemocomponente comp);
std::string hemocomponenteToDisplay(TipoHemocomponente comp);
TipoHemocomponente stringToHemocomponente(const std::string& str);
int getValidadePadraoDias(TipoHemocomponente comp);

/**
 * @brief Representa uma bolsa de hemocomponente armazenada no hemocentro/agência.
 */
struct Bolsa {
    std::string codigo;
    TipoSanguineo tipoSanguineo;
    TipoHemocomponente componente;
    int volumeMl;
    std::string dataColeta;
    std::string dataValidade;
    std::string unidadeId;
    int diasAteVencer;
    bool vencida;

    Bolsa() : tipoSanguineo(TipoSanguineo::O_POS),
              componente(TipoHemocomponente::CONCENTRADO_HEMACIAS),
              volumeMl(0), diasAteVencer(0), vencida(false) {}

    Bolsa(std::string cod, TipoSanguineo tipo, TipoHemocomponente comp,
          int vol, std::string coleta, std::string validade,
          std::string unid, int dias, bool venc)
        : codigo(std::move(cod)), tipoSanguineo(tipo), componente(comp),
          volumeMl(vol), dataColeta(std::move(coleta)), dataValidade(std::move(validade)),
          unidadeId(std::move(unid)), diasAteVencer(dias), vencida(venc) {}
};

/**
 * @brief Item específico de uma requisição hospitalar.
 */
struct ItemRequisicao {
    std::string id;
    TipoSanguineo tipoSanguineo;
    TipoHemocomponente componente;
    int quantidade;

    ItemRequisicao() : tipoSanguineo(TipoSanguineo::O_POS),
                       componente(TipoHemocomponente::CONCENTRADO_HEMACIAS),
                       quantidade(0) {}

    ItemRequisicao(std::string id_, TipoSanguineo tipo, TipoHemocomponente comp, int qtd)
        : id(std::move(id_)), tipoSanguineo(tipo), componente(comp), quantidade(qtd) {}
};

/**
 * @brief Requisição formal emitida por um hospital/agência transfusional.
 */
struct Requisicao {
    std::string id;
    std::string unidadeId;
    std::string cidade;
    std::string prioridade;
    std::string data;
    int prazoHoras;
    std::vector<ItemRequisicao> itens;
    int totalBolsas;

    Requisicao() : prazoHoras(0), totalBolsas(0) {}
};

#endif // DOMINIO_HPP
