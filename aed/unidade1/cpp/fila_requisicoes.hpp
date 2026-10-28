#ifndef FILA_REQUISICOES_HPP
#define FILA_REQUISICOES_HPP

#include "dominio.hpp"

#include <optional>

/*
 * Fila FIFO (First In, First Out) de requisicoes de hemocomponentes.
 *
 * FIFO e a ordem de atendimento por chegada: a requisicao que entrou primeiro
 * e atendida primeiro. Nesta unidade a prioridade da requisicao e apenas um
 * campo informativo, impresso junto com os dados; usa-la para reordenar o
 * atendimento exigiria fila de prioridade, que e Unidade 2.
 *
 * Dois ponteiros, 'frente' e 'tras', para que as duas pontas custem O(1):
 * entra pelo tras, sai pela frente. Com apenas um deles, uma das operacoes
 * teria de percorrer a fila inteira.
 */

class FilaRequisicoes {
public:
    FilaRequisicoes() = default;
    ~FilaRequisicoes();

    FilaRequisicoes(const FilaRequisicoes&) = delete;
    FilaRequisicoes& operator=(const FilaRequisicoes&) = delete;

    /* Insere no fim (tras). */
    Resultado enfileirar(const Requisicao& requisicao);

    /*
     * Remove da frente e devolve a requisicao retirada, ou std::nullopt se a
     * fila estiver vazia.
     *
     * DIFERENCA PARA A VERSAO EM C: la a assinatura era
     * fila_desenfileirar(fila, &saida), devolvendo RV_OK ou RV_ERRO e
     * gravando o resultado num parametro de saida. Quem chamava precisava
     * declarar a variavel antes e conferir o retorno para saber se ela foi
     * preenchida. Com std::optional o valor e o proprio retorno, e o caso
     * vazio fica explicito no tipo. Em Java o equivalente seria Optional.
     *
     * A copia continua sendo necessaria pelo mesmo motivo do C: o no e
     * liberado em seguida, entao devolver ponteiro para ele entregaria
     * endereco invalido.
     */
    std::optional<Requisicao> desenfileirar();

    /*
     * Consulta quem sera atendido sem retirar da fila. Devolve nullptr se
     * vazia. O ponteiro aponta para dentro do no e deixa de valer quando ele
     * sai.
     */
    Requisicao* frente();

    void listar() const;
    int  tamanho() const { return tamanho_; }
    bool vazia() const   { return frente_ == nullptr; }

private:
    struct No {
        Requisicao requisicao;      /* copia da requisicao dentro do no */
        No*        proximo = nullptr;
    };

    No* frente_ = nullptr;          /* de onde sai: nullptr se vazia */
    No* tras_   = nullptr;          /* por onde entra: nullptr se vazia */
    int tamanho_ = 0;
};

#endif /* FILA_REQUISICOES_HPP */
