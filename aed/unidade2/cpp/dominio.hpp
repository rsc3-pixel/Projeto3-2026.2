#ifndef DOMINIO_HPP
#define DOMINIO_HPP

#include <string>

/*
 * Versao simplificada da Bolsa do Rota Vital, igual a usada na Unidade 1
 * (aed/unidade1/cpp/dominio.hpp), repetida aqui para que a Unidade 2 compile
 * sozinha.
 *
 * A data fica como texto no formato ISO "AAAA-MM-DD". O formato nao e detalhe
 * de exibicao: texto ISO ordena corretamente em comparacao lexical, entao
 * comparar duas datas como string responde a mesma pergunta que compareTo
 * sobre dois LocalDate no Java. E disso que a insercao ordenada por validade
 * depende.
 *
 * DIFERENCA PARA A VERSAO EM C: la os campos de texto eram vetores de char de
 * tamanho fixo, e havia uma funcao copiar_texto para garantir que o
 * terminador fosse sempre gravado, porque strncpy sozinho nao garante. Aqui
 * sao std::string, que cuida do proprio tamanho e do proprio terminador: a
 * funcao de copia segura deixou de ser necessaria.
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

/*
 * Resultado das operacoes que podem falhar, a mesma convencao da Unidade 1.
 *
 * DIFERENCA PARA A VERSAO EM C: la eram as macros RV_OK e RV_ERRO, valendo
 * 1 e 0, e as funcoes devolviam int. Macro nao tem tipo: nada impedia passar
 * RV_OK onde se esperava um tamanho. Aqui e um enum class, entao Resultado
 * nao se converte em int por acidente e o compilador recusa a confusao.
 */
enum class Resultado {
    Erro = 0,
    Ok   = 1
};

#endif /* DOMINIO_HPP */
