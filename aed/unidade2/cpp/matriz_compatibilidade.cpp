#include "matriz_compatibilidade.hpp"

namespace {

/* Bit do grupo na linha da tabela: posicao i para o grupo de ordinal i. */
constexpr unsigned char bitDe(GrupoSanguineo grupo) {
    return static_cast<unsigned char>(1u << static_cast<int>(grupo));
}

constexpr unsigned char A_POS  = bitDe(GrupoSanguineo::A_POS);
constexpr unsigned char A_NEG  = bitDe(GrupoSanguineo::A_NEG);
constexpr unsigned char B_POS  = bitDe(GrupoSanguineo::B_POS);
constexpr unsigned char B_NEG  = bitDe(GrupoSanguineo::B_NEG);
constexpr unsigned char AB_POS = bitDe(GrupoSanguineo::AB_POS);
constexpr unsigned char AB_NEG = bitDe(GrupoSanguineo::AB_NEG);
constexpr unsigned char O_POS  = bitDe(GrupoSanguineo::O_POS);
constexpr unsigned char O_NEG  = bitDe(GrupoSanguineo::O_NEG);
constexpr unsigned char TODOS  = 0xFF;

/*
 * Hemacias: para cada receptor, de quem ele pode receber.
 *
 * A regra e a mais restritiva: o receptor nao pode receber antigeno que nao
 * possui. Duas leituras saltam da tabela - O- aparece em todas as linhas, e e
 * o doador universal; AB+ aceita os oito, e e o receptor universal.
 *
 * Indexada pelo ordinal do receptor, na ordem declarada do enum.
 */
constexpr unsigned char HEMACIAS[TOTAL_GRUPOS] = {
    /* A_POS  */ O_NEG | O_POS | A_NEG | A_POS,
    /* A_NEG  */ O_NEG | A_NEG,
    /* B_POS  */ O_NEG | O_POS | B_NEG | B_POS,
    /* B_NEG  */ O_NEG | B_NEG,
    /* AB_POS */ TODOS,
    /* AB_NEG */ O_NEG | A_NEG | B_NEG | AB_NEG,
    /* O_POS  */ O_NEG | O_POS,
    /* O_NEG  */ O_NEG
};

/*
 * Plasma: regra INVERTIDA em relacao as hemacias.
 *
 * O plasma carrega anticorpos, nao antigenos. Quem doa hemacia para todos, o
 * O-, tem plasma com anticorpos anti-A e anti-B, entao seu plasma so serve
 * para receptores O. Ja AB e o doador universal de plasma.
 *
 * O fator Rh nao restringe a transfusao de plasma, entao cada linha aceita as
 * duas variantes do grupo ABO correspondente.
 */
constexpr unsigned char PLASMA[TOTAL_GRUPOS] = {
    /* A_POS  */ A_NEG | A_POS | AB_NEG | AB_POS,
    /* A_NEG  */ A_NEG | A_POS | AB_NEG | AB_POS,
    /* B_POS  */ B_NEG | B_POS | AB_NEG | AB_POS,
    /* B_NEG  */ B_NEG | B_POS | AB_NEG | AB_POS,
    /* AB_POS */ AB_NEG | AB_POS,
    /* AB_NEG */ AB_NEG | AB_POS,
    /* O_POS  */ TODOS,
    /* O_NEG  */ TODOS
};

bool grupoValido(GrupoSanguineo grupo) {
    const int i = static_cast<int>(grupo);
    return i >= 0 && i < TOTAL_GRUPOS;
}

bool tipoValido(TipoHemocomponente tipo) {
    const int i = static_cast<int>(tipo);
    return i >= 0 && i < TOTAL_TIPOS;
}

/*
 * Escolhe a tabela conforme o hemocomponente.
 *
 * Plasma e crioprecipitado sao derivados do plasma e seguem a regra
 * invertida. Hemacias e plaquetas seguem a regra das hemacias.
 */
const unsigned char* tabelaDe(TipoHemocomponente tipo) {
    switch (tipo) {
        case TipoHemocomponente::PLASMA_FRESCO_CONGELADO:
        case TipoHemocomponente::CRIOPRECIPITADO:
            return PLASMA;
        case TipoHemocomponente::CONCENTRADO_HEMACIAS:
        case TipoHemocomponente::CONCENTRADO_PLAQUETAS:
            return HEMACIAS;
    }
    return nullptr;
}

}  // namespace

const char* nomeGrupo(GrupoSanguineo grupo) {
    switch (grupo) {
        case GrupoSanguineo::A_POS:  return "A_POS";
        case GrupoSanguineo::A_NEG:  return "A_NEG";
        case GrupoSanguineo::B_POS:  return "B_POS";
        case GrupoSanguineo::B_NEG:  return "B_NEG";
        case GrupoSanguineo::AB_POS: return "AB_POS";
        case GrupoSanguineo::AB_NEG: return "AB_NEG";
        case GrupoSanguineo::O_POS:  return "O_POS";
        case GrupoSanguineo::O_NEG:  return "O_NEG";
    }
    return "(invalido)";
}

const char* rotuloGrupo(GrupoSanguineo grupo) {
    switch (grupo) {
        case GrupoSanguineo::A_POS:  return "A+";
        case GrupoSanguineo::A_NEG:  return "A-";
        case GrupoSanguineo::B_POS:  return "B+";
        case GrupoSanguineo::B_NEG:  return "B-";
        case GrupoSanguineo::AB_POS: return "AB+";
        case GrupoSanguineo::AB_NEG: return "AB-";
        case GrupoSanguineo::O_POS:  return "O+";
        case GrupoSanguineo::O_NEG:  return "O-";
    }
    return "??";
}

const char* nomeTipo(TipoHemocomponente tipo) {
    switch (tipo) {
        case TipoHemocomponente::CONCENTRADO_HEMACIAS:    return "CONCENTRADO_HEMACIAS";
        case TipoHemocomponente::PLASMA_FRESCO_CONGELADO: return "PLASMA_FRESCO_CONGELADO";
        case TipoHemocomponente::CONCENTRADO_PLAQUETAS:   return "CONCENTRADO_PLAQUETAS";
        case TipoHemocomponente::CRIOPRECIPITADO:         return "CRIOPRECIPITADO";
    }
    return "(invalido)";
}

unsigned char MatrizCompatibilidade::doadoresEmBits(GrupoSanguineo receptor,
                                                    TipoHemocomponente tipo) {
    if (!grupoValido(receptor) || !tipoValido(tipo)) {
        return 0;   /* ausencia de dado nao autoriza transfusao */
    }
    const unsigned char* tabela = tabelaDe(tipo);
    return tabela == nullptr ? 0 : tabela[static_cast<int>(receptor)];
}

bool MatrizCompatibilidade::compativel(GrupoSanguineo doador,
                                       GrupoSanguineo receptor,
                                       TipoHemocomponente tipo) {
    if (!grupoValido(doador)) {
        return false;
    }
    /* Um deslocamento e um E logico: a consulta e O(1). */
    return (doadoresEmBits(receptor, tipo) & bitDe(doador)) != 0;
}

std::vector<GrupoSanguineo> MatrizCompatibilidade::doadoresPara(GrupoSanguineo receptor,
                                                                TipoHemocomponente tipo) {
    std::vector<GrupoSanguineo> doadores;

    const unsigned char linha = doadoresEmBits(receptor, tipo);
    for (int i = 0; i < TOTAL_GRUPOS; ++i) {
        const GrupoSanguineo candidato = static_cast<GrupoSanguineo>(i);
        if ((linha & bitDe(candidato)) != 0) {
            doadores.push_back(candidato);
        }
    }
    return doadores;
}
