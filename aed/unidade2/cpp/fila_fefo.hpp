#ifndef FILA_FEFO_HPP
#define FILA_FEFO_HPP

#include "dominio.hpp"

#include <optional>
#include <vector>

/*
 * Fila de prioridade FEFO (First Expired, First Out) por INSERCAO ORDENADA.
 *
 * A lista e mantida ordenada o tempo todo: cada bolsa nova e colocada direto
 * na posicao certa, percorrendo a partir do inicio ate achar o ponto de
 * encaixe. Nao existe passo de ordenacao depois - a ordem e invariante da
 * estrutura, e por isso a bolsa que vence primeiro esta sempre no inicio.
 *
 * E a tecnica pedida na Unidade 2 de AED, em contraste com o heap binario
 * usado na Unidade 1. O equivalente em Java e a classe
 * com.rotavital.estruturas.FilaPrioridadeFefo, e a correspondencia entre as
 * duas esta documentada em docs/W10-fefo-insercao-ordenada.md.
 *
 * Criterio de ordem: data de validade crescente e, no empate, codigo de
 * rastreio crescente. O desempate pelo codigo e o que torna o resultado
 * deterministico: duas bolsas de mesma validade sempre resolvem para a mesma
 * escolha, em qualquer ordem de montagem do estoque.
 *
 * Complexidade: adicionar O(n), consultar O(1), retirar O(1),
 * descartarVencidas O(k) para k vencidas, tamanho O(1).
 *
 * DIFERENCA PARA A VERSAO EM C: la a fila era uma struct manipulada por
 * funcoes soltas (fila_criar, fila_adicionar, fila_destruir), e quem usava
 * precisava lembrar de chamar fila_destruir no fim. Aqui e uma classe: o
 * construtor faz o papel de fila_criar e o destrutor faz o de fila_destruir,
 * chamado sozinho quando o objeto sai de escopo. Esse e o RAII.
 *
 * Os nos continuam alocados a mao com new e liberados com delete, como no C
 * original. Trocar por std::list ou std::multiset resolveria em poucas
 * linhas, mas apagaria justamente o que este exercicio quer mostrar: o
 * encadeamento, o religamento de ponteiros na insercao e o custo O(n) de
 * manter a ordem a cada entrada.
 */

class FilaFefo {
public:
    FilaFefo() = default;
    ~FilaFefo();

    /*
     * Copiar a fila exigiria duplicar toda a cadeia de nos. Sem isso, duas
     * filas apontariam para os mesmos nos e cada destrutor tentaria liberar
     * os mesmos enderecos. Como o exercicio nao precisa de copia, ela e
     * proibida: o compilador recusa em vez de gerar codigo quebrado em
     * silencio.
     */
    FilaFefo(const FilaFefo&) = delete;
    FilaFefo& operator=(const FilaFefo&) = delete;

    /* Insere mantendo a ordem. */
    Resultado adicionar(const Bolsa& bolsa);

    /*
     * A proxima bolsa a sair, sem retirar. Devolve nullptr se a fila estiver
     * vazia: estoque vazio nao e erro, e situacao normal da operacao.
     */
    const Bolsa* consultarProxima() const;

    /*
     * Retira a primeira bolsa e a devolve, ou std::nullopt se a fila estiver
     * vazia.
     *
     * DIFERENCA PARA A VERSAO EM C: la a assinatura era
     * fila_retirar_proxima(fila, &saida), devolvendo RV_OK ou RV_ERRO e
     * gravando o resultado num parametro de saida. Com std::optional o valor
     * e o proprio retorno, e o caso vazio fica explicito no tipo. Em Java o
     * equivalente seria Optional.
     */
    std::optional<Bolsa> retirarProxima();

    /*
     * Remove do inicio todas as bolsas vencidas na data de referencia e as
     * devolve.
     *
     * Como a lista e ordenada por validade, as vencidas estao todas no
     * inicio: o laco para na primeira valida, sem percorrer o resto.
     *
     * A bolsa cuja validade e exatamente a data de referencia NAO e
     * descartada: ela so vence no dia seguinte.
     *
     * DIFERENCA PARA A VERSAO EM C: la quem chamava precisava passar um vetor
     * ja alocado e a sua capacidade, e o retorno podia ser maior que ela,
     * indicando que nem tudo coube. Aqui o vector cresce conforme necessario
     * e nao ha limite a controlar.
     */
    std::vector<Bolsa> descartarVencidas(const std::string& dataReferencia);

    void listar() const;
    int  tamanho() const { return tamanho_; }
    bool vazia() const   { return primeiro_ == nullptr; }

private:
    struct No {
        Bolsa bolsa;              /* copia da bolsa, armazenada no proprio no */
        No*   proximo = nullptr;  /* nullptr quando este e o ultimo no */
    };

    No* primeiro_ = nullptr;      /* nullptr quando a fila esta vazia */
    int tamanho_  = 0;
};

#endif /* FILA_FEFO_HPP */
