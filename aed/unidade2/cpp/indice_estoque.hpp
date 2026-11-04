#ifndef INDICE_ESTOQUE_HPP
#define INDICE_ESTOQUE_HPP

#include "dominio.hpp"
#include "matriz_compatibilidade.hpp"

#include <string>
#include <vector>

/*
 * Indice hash para localizar bolsas pela combinacao de grupo sanguineo e tipo
 * de hemocomponente.
 *
 * Sem ele, achar "as bolsas O- de concentrado de hemacias" exigiria percorrer
 * o estoque inteiro, O(n) por consulta. Com a chave composta, a consulta cai
 * direto no balde da combinacao.
 *
 * O equivalente em Java e com.rotavital.estruturas.IndiceEstoque, e a decisao
 * sobre colisao esta em docs/W10-indice-estoque-hash.md.
 *
 *
 * HASH PERFEITO: ZERO COLISAO POR CONSTRUCAO
 *
 * O espaco de chaves e pequeno e fechado: 8 grupos sanguineos vezes 4 tipos de
 * hemocomponente dao no maximo 32 chaves distintas. A funcao de dispersao e
 *
 *     hash = ordinal(grupo) * TOTAL_TIPOS + ordinal(tipo)
 *
 * que mapeia as 32 combinacoes em 0..31 sem repetir nenhuma. Com a tabela
 * dimensionada em CAPACIDADE baldes, o indice do balde e o proprio hash: 32
 * baldes distintos, nenhum compartilhado.
 *
 * Isso nao e sorte nem uma funcao "boa o bastante": e um hash perfeito, e a
 * ausencia de colisao vale por construcao. A propriedade e verificavel, e a
 * etapa 11 da demonstracao imprime o balde de cada uma das 32 chaves para
 * mostrar que nenhum se repete.
 *
 * A decisao vem da PI3-132. Antes dela, a versao Java usava
 * Objects.hash(grupo, tipo), que combina o hashCode dos enums - o de
 * identidade herdado de Object, que nao e especificado entre execucoes e
 * depende ate de flags da maquina virtual. Medindo as 32 combinacoes numa
 * tabela de 64 baldes, o numero de colisoes variou de 6 a 31 conforme o modo
 * de hash de identidade da HotSpot. Eram colisoes inofensivas, com cadeias
 * curtas, mas o projeto nao controlava quantas seriam. O hash por ordinal
 * trocou "raro por sorte" por "zero por construcao".
 *
 *
 * O QUE NAO E COLISAO
 *
 * Varias bolsas da mesma combinacao ficam na mesma cadeia, que e o conteudo do
 * balde. Isso nao e colisao de hash: e o agrupamento desejado, e a razao de o
 * indice existir. Colisao seria duas CHAVES diferentes disputando o mesmo
 * balde, e aqui isso nao acontece.
 *
 *
 * MANUTENCAO
 *
 * Se um dos enums ganhar valores, a formula continua perfeita, porque usa
 * TOTAL_TIPOS em vez de um numero solto. O que precisa ser revisto e a
 * CAPACIDADE: o produto dos dois tamanhos tem de caber nela. Hoje sao 32 em
 * 32 baldes, entao qualquer valor novo exige aumentar a tabela.
 *
 *
 * DIFERENCA PARA A VERSAO EM JAVA
 *
 * 1. A tabela hash. O Java usa HashMap, que ja traz dispersao, baldes,
 *    encadeamento e redimensionamento prontos - e por isso o javadoc de la
 *    precisa explicar o que o HashMap faz por baixo (espalhamento
 *    h ^ (h >>> 16), indice (n-1) & hash, lista que vira arvore acima de 8
 *    entradas). Aqui nao ha nada por baixo: o vetor de baldes e alocado com
 *    new, a funcao de dispersao esta escrita, e o encadeamento dentro do balde
 *    e uma cadeia de nos. O que la era comportamento da biblioteca, aqui e
 *    codigo a vista.
 *
 * 2. Redimensionamento. O HashMap do Java dobra a tabela quando passa de 75%
 *    de ocupacao, e os indices mudam junto. Aqui a tabela tem tamanho fixo e
 *    nunca cresce, porque o espaco de chaves e fechado: nao existe a 33a
 *    combinacao. Isso torna o indice do balde estavel do inicio ao fim.
 *
 * 3. Encadeamento. O Java mantem o encadeamento por colisao de chave, que
 *    nunca ocorre neste caso. Aqui a cadeia do balde guarda as BOLSAS daquela
 *    combinacao - o agrupamento, nao a colisao. E a mesma estrutura cumprindo
 *    papel diferente.
 *
 * 4. Memoria. O Java confia no coletor de lixo. Aqui o destrutor percorre os
 *    32 baldes liberando no por no, e por fim libera o proprio vetor de
 *    baldes.
 *
 * 5. Retorno da busca. O Java devolve List.copyOf, uma copia imutavel, porque
 *    o indice e compartilhado entre requisicoes e pode mudar enquanto alguem
 *    percorre o resultado. Aqui std::vector ja volta por valor, que e copia.
 *
 *
 * COMPLEXIDADE: adicionar, buscar e remover localizam o balde em O(1) exato,
 * sem cadeia de chaves a percorrer, mais O(k) no numero de bolsas daquela
 * combinacao. Tamanho e O(1), por contador.
 */

class IndiceEstoque {
public:
    /* 32 baldes para 32 chaves possiveis: um por combinacao. */
    static constexpr int CAPACIDADE = TOTAL_GRUPOS * TOTAL_TIPOS;

    IndiceEstoque();
    ~IndiceEstoque();

    /*
     * Copiar o indice exigiria duplicar as 32 cadeias. Sem isso, dois indices
     * apontariam para os mesmos nos e cada destrutor tentaria liberar os
     * mesmos enderecos. Como o exercicio nao precisa de copia, ela e proibida.
     */
    IndiceEstoque(const IndiceEstoque&) = delete;
    IndiceEstoque& operator=(const IndiceEstoque&) = delete;

    /*
     * A funcao de dispersao. Publica e estatica para que a demonstracao possa
     * imprimir o balde de cada chave usando exatamente a formula que o indice
     * usa, em vez de reimplementa-la.
     *
     * DIFERENCA PARA A VERSAO EM JAVA: la a formula fica em hashDaChave,
     * package-private, pelo mesmo motivo - o teste precisa ver a formula real.
     */
    static int hashDaChave(GrupoSanguineo grupo, TipoHemocomponente tipo);

    /*
     * Indexa a bolsa pela combinacao de grupo e tipo.
     *
     * Nao duplica: se uma bolsa de mesmo codigo de rastreio ja estiver
     * indexada, a entrada antiga e substituida. Sem isso, uma bolsa reindexada
     * depois de mudar de status apareceria duas vezes e poderia ser alocada em
     * duplicidade.
     */
    Resultado adicionar(const Bolsa& bolsa, GrupoSanguineo grupo, TipoHemocomponente tipo);

    /*
     * Bolsas indexadas na combinacao pedida, em copia.
     *
     * Combinacao sem bolsa devolve vetor vazio, nunca erro: ausencia de dado
     * nao e erro, a mesma decisao da MatrizCompatibilidade.
     */
    std::vector<Bolsa> buscar(GrupoSanguineo grupo, TipoHemocomponente tipo) const;

    /* Remove pelo codigo de rastreio. Devolve se encontrou. */
    bool remover(const std::string& codigoRastreio,
                 GrupoSanguineo grupo, TipoHemocomponente tipo);

    /* Quantidade total de bolsas indexadas, somando todos os baldes. */
    int tamanho() const { return tamanho_; }

    /* Esvazia o indice, liberando as cadeias e mantendo o vetor de baldes. */
    void limpar();

    /* Quantas bolsas ha no balde, para a demonstracao inspecionar a tabela. */
    int ocupacaoDoBalde(int balde) const;

private:
    /* No da cadeia de um balde: a bolsa e a ligacao para a proxima. */
    struct No {
        Bolsa bolsa;
        No*   proxima = nullptr;   /* nullptr quando e a ultima do balde */
    };

    /*
     * Vetor de baldes alocado a mao. Cada posicao guarda o inicio da cadeia
     * daquela combinacao, ou nullptr se nao houver bolsa dela.
     */
    No** baldes_   = nullptr;
    int  tamanho_  = 0;
};

#endif /* INDICE_ESTOQUE_HPP */
