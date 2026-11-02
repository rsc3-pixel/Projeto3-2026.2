#include "Dominio.hpp"
#include <stdexcept>

std::string tipoSanguineoToString(TipoSanguineo tipo) {
    switch (tipo) {
        case TipoSanguineo::O_POS:  return "O_POS";
        case TipoSanguineo::A_POS:  return "A_POS";
        case TipoSanguineo::B_POS:  return "B_POS";
        case TipoSanguineo::O_NEG:  return "O_NEG";
        case TipoSanguineo::A_NEG:  return "A_NEG";
        case TipoSanguineo::AB_POS: return "AB_POS";
        case TipoSanguineo::B_NEG:  return "B_NEG";
        case TipoSanguineo::AB_NEG: return "AB_NEG";
    }
    return "DESCONHECIDO";
}

std::string tipoSanguineoToDisplay(TipoSanguineo tipo) {
    switch (tipo) {
        case TipoSanguineo::O_POS:  return "O+";
        case TipoSanguineo::A_POS:  return "A+";
        case TipoSanguineo::B_POS:  return "B+";
        case TipoSanguineo::O_NEG:  return "O-";
        case TipoSanguineo::A_NEG:  return "A-";
        case TipoSanguineo::AB_POS: return "AB+";
        case TipoSanguineo::B_NEG:  return "B-";
        case TipoSanguineo::AB_NEG: return "AB-";
    }
    return "?";
}

TipoSanguineo stringToTipoSanguineo(const std::string& str) {
    if (str == "O_POS" || str == "O+") return TipoSanguineo::O_POS;
    if (str == "A_POS" || str == "A+") return TipoSanguineo::A_POS;
    if (str == "B_POS" || str == "B+") return TipoSanguineo::B_POS;
    if (str == "O_NEG" || str == "O-") return TipoSanguineo::O_NEG;
    if (str == "A_NEG" || str == "A-") return TipoSanguineo::A_NEG;
    if (str == "AB_POS" || str == "AB+") return TipoSanguineo::AB_POS;
    if (str == "B_NEG" || str == "B-") return TipoSanguineo::B_NEG;
    if (str == "AB_NEG" || str == "AB-") return TipoSanguineo::AB_NEG;
    throw std::invalid_argument("Tipo sanguineo invalido: " + str);
}

std::string hemocomponenteToString(TipoHemocomponente comp) {
    switch (comp) {
        case TipoHemocomponente::CONCENTRADO_HEMACIAS:    return "CONCENTRADO_HEMACIAS";
        case TipoHemocomponente::CONCENTRADO_PLAQUETAS:   return "CONCENTRADO_PLAQUETAS";
        case TipoHemocomponente::PLASMA_FRESCO_CONGELADO: return "PLASMA_FRESCO_CONGELADO";
        case TipoHemocomponente::CRIOPRECIPITADO:         return "CRIOPRECIPITADO";
    }
    return "DESCONHECIDO";
}

std::string hemocomponenteToDisplay(TipoHemocomponente comp) {
    switch (comp) {
        case TipoHemocomponente::CONCENTRADO_HEMACIAS:    return "Concentrado de Hemacias";
        case TipoHemocomponente::CONCENTRADO_PLAQUETAS:   return "Concentrado de Plaquetas";
        case TipoHemocomponente::PLASMA_FRESCO_CONGELADO: return "Plasma Fresco Congelado";
        case TipoHemocomponente::CRIOPRECIPITADO:         return "Crioprecipitado";
    }
    return "?";
}

TipoHemocomponente stringToHemocomponente(const std::string& str) {
    if (str == "CONCENTRADO_HEMACIAS") return TipoHemocomponente::CONCENTRADO_HEMACIAS;
    if (str == "CONCENTRADO_PLAQUETAS") return TipoHemocomponente::CONCENTRADO_PLAQUETAS;
    if (str == "PLASMA_FRESCO_CONGELADO") return TipoHemocomponente::PLASMA_FRESCO_CONGELADO;
    if (str == "CRIOPRECIPITADO") return TipoHemocomponente::CRIOPRECIPITADO;
    throw std::invalid_argument("Hemocomponente invalido: " + str);
}

int getValidadePadraoDias(TipoHemocomponente comp) {
    switch (comp) {
        case TipoHemocomponente::CONCENTRADO_HEMACIAS:    return 42;
        case TipoHemocomponente::CONCENTRADO_PLAQUETAS:   return 5;
        case TipoHemocomponente::PLASMA_FRESCO_CONGELADO: return 365;
        case TipoHemocomponente::CRIOPRECIPITADO:         return 365;
    }
    return 0;
}
