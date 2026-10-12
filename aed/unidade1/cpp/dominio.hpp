#ifndef DOMINIO_HPP
#define DOMINIO_HPP

#include <string>

/*
 * Versoes simplificadas das entidades de dominio do Rota Vital.
 *
 * Sao apenas dados: nada de JPA, nada de comportamento. As datas ficam como
 * texto no formato ISO ("AAAA-MM-DD") porque a biblioteca padrao nao tem um
 * tipo de data pratico para este uso, e texto ISO ordena corretamente na
 * comparacao lexical, caso isso seja necessario mais adiante.
 *
 * DIFERENCA PARA A VERSAO EM C: la os campos de texto eram vetores de char de
 * tamanho fixo (char[16], char[64]), e havia uma funcao copiar_texto para
 * garantir que o terminador fosse sempre gravado. Aqui sao std::string, que
 * cuida do proprio tamanho e do proprio terminador. A funcao de copia segura
 * deixou de ser necessaria: o perigo que ela evitava, de strncpy nao
 * terminar a string, nao existe mais.
 */

struct Bolsa {
    std::string codigo_rastreio;
    std::string grupo_sanguineo;   /* "A+", "O-" */
    std::string componente;        /* "CONCENTRADO_HEMACIAS" */
    int         volume_ml = 0;
    std::string data_coleta;       /* "AAAA-MM-DD" */
    std::string data_validade;
    std::string status;            /* "DISPONIVEL" */
};

struct Requisicao {
    std::string id;
    std::string hospital;
    std::string prioridade;        /* "ROTINA", "URGENTE", "EMERGENCIA" */
    std::string grupo_sanguineo;
    std::string componente;
    int         quantidade = 0;
    std::string data_hora;
};

struct Operacao {
    std::string id;
    std::string operacao;          /* "ENTRADA_BOLSA", "SAIDA_BOLSA", ... */
    std::string descricao;
    std::string data_hora;
};

/*
 * Resultado das operacoes que podem falhar.
 *
 * DIFERENCA PARA A VERSAO EM C: la eram as macros RV_OK e RV_ERRO, valendo
 * 1 e 0, e as funcoes devolviam int. Macro nao tem tipo: nada impedia passar
 * RV_OK onde se esperava um tamanho, nem o compilador avisaria. Aqui e um
 * enum class, entao Resultado nao se converte em int por acidente e o
 * compilador recusa a confusao.
 *
 * Em Java o equivalente seria um enum, ou mais provavelmente uma excecao.
 */
enum class Resultado {
    Erro = 0,
    Ok   = 1
};

#endif /* DOMINIO_HPP */
