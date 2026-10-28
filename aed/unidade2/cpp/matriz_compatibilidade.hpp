#ifndef MATRIZ_COMPATIBILIDADE_HPP
#define MATRIZ_COMPATIBILIDADE_HPP

#include <string>
#include <vector>

/*
 * Compatibilidade transfusional ABO/Rh: quem pode doar para quem, por tipo de
 * hemocomponente.
 *
 * O equivalente em Java e com.rotavital.compatibilidade.MatrizCompatibilidade.
 *
 * A COMPATIBILIDADE AQUI E DIDATICA E NAO SUBSTITUI PROTOCOLO CLINICO. Na
 * pratica transfusional entram prova cruzada, pesquisa de anticorpos
 * irregulares e outros fatores que este projeto nao modela.
 *
 *
 * A RELACAO NAO E SIMETRICA
 *
 * compativel(O_NEG, A_POS, hemacias) e verdadeiro, porque O- doa para A+;
 * compativel(A_POS, O_NEG, hemacias) e falso, porque A+ nao doa para O-.
 * Trocar a ordem dos argumentos muda a resposta, e por isso os parametros se
 * chamam doador e receptor, e nao a e b. Inverter os dois e justamente a
 * transfusao que mata.
 *
 *
 * DUAS TABELAS, E A DO PLASMA E INVERTIDA
 *
 * Hemacias carregam antigenos: o receptor nao pode receber antigeno que nao
 * possui, senao seus anticorpos atacam as hemacias transfundidas. Dai O- ser
 * doador universal de hemacias e AB+ receptor universal.
 *
 * Plasma carrega anticorpos, nao antigenos, e a regra se inverte. Quem doa
 * hemacia para todos, o O-, tem plasma com anticorpos anti-A e anti-B, entao
 * seu plasma so serve para receptores O. Ja AB, que nao tem esses anticorpos,
 * e o doador universal de plasma. Ignorar essa inversao e o erro mais comum
 * ao modelar compatibilidade, porque a intuicao construida com hemacias leva
 * para o lado errado.
 *
 * Plaquetas seguem a regra das hemacias; crioprecipitado segue a do plasma.
 *
 *
 * DIFERENCA PARA A VERSAO EM JAVA
 *
 * 1. Representacao das tabelas. O Java usa EnumMap de receptor para EnumSet de
 *    doadores - um vetor indexado pelo ordinal e um conjunto de bits, ambos da
 *    biblioteca padrao. Aqui sao vetores de bits escritos a mao: cada linha e
 *    um inteiro de 8 bits em que o bit i ligado significa "o grupo de ordinal
 *    i pode doar". A consulta vira um deslocamento e um E logico, O(1), e a
 *    tabela inteira cabe em 16 bytes.
 *
 * 2. Tipos. O Java tem enum de verdade, com ordinal e values(). Aqui sao enum
 *    class com valor explicito, e a contagem vem de uma constante - nao ha
 *    values() para perguntar quantos sao.
 *
 * 3. Retorno do conjunto. O Java devolve EnumSet com copia defensiva, para que
 *    alterar o retorno nao mude a tabela estatica. Aqui doadoresPara devolve
 *    std::vector por valor, que ja e copia: nao ha referencia para o interior
 *    da tabela escapando.
 *
 * 4. Memoria. Nao ha alocacao dinamica nesta classe: as duas tabelas sao
 *    constantes de tamanho fixo. Diferente do indice e do grafo, aqui nao ha
 *    destrutor a escrever.
 */

/*
 * Grupos sanguineos, na MESMA ordem declarada do enum Java. A ordem importa:
 * o ordinal e usado como posicao de bit nas tabelas e como parte do hash do
 * indice de estoque. Mudar a ordem aqui sem mudar la quebraria a
 * correspondencia entre as duas versoes.
 */
enum class GrupoSanguineo {
    A_POS = 0, A_NEG = 1, B_POS = 2, B_NEG = 3,
    AB_POS = 4, AB_NEG = 5, O_POS = 6, O_NEG = 7
};

/* Hemocomponentes, tambem na ordem declarada do enum Java. */
enum class TipoHemocomponente {
    CONCENTRADO_HEMACIAS = 0,
    PLASMA_FRESCO_CONGELADO = 1,
    CONCENTRADO_PLAQUETAS = 2,
    CRIOPRECIPITADO = 3
};

constexpr int TOTAL_GRUPOS = 8;
constexpr int TOTAL_TIPOS  = 4;

/* Nome da constante, para impressao. Equivale ao name() do enum Java. */
const char* nomeGrupo(GrupoSanguineo grupo);
const char* nomeTipo(TipoHemocomponente tipo);

/* Rotulo curto do grupo, como "A+" e "O-". Equivale a descricao do enum Java. */
const char* rotuloGrupo(GrupoSanguineo grupo);

class MatrizCompatibilidade {
public:
    /*
     * Classe de consulta apenas: as tabelas sao constantes e nao ha estado a
     * guardar. Por isso tudo e estatico e a construcao e proibida, como o
     * construtor privado faz no Java.
     */
    MatrizCompatibilidade() = delete;

    /*
     * Diz se uma bolsa do grupo doador pode ser transfundida em um paciente do
     * grupo receptor, considerando o hemocomponente.
     *
     * A ordem dos dois primeiros argumentos importa: a relacao nao e
     * simetrica.
     *
     * Argumento fora da faixa valida devolve falso, e nao excecao: ausencia de
     * dado nao e autorizacao para transfundir. E a mesma decisao do Java, que
     * devolve false para qualquer argumento nulo.
     */
    static bool compativel(GrupoSanguineo doador,
                           GrupoSanguineo receptor,
                           TipoHemocomponente tipo);

    /*
     * Grupos que podem doar para o receptor, naquele hemocomponente.
     *
     * Serve a selecao de bolsas: em vez de testar bolsa por bolsa, o chamador
     * obtem de uma vez quais grupos procurar no estoque.
     *
     * Devolve vetor vazio se algum argumento estiver fora da faixa - nunca
     * excecao.
     */
    static std::vector<GrupoSanguineo> doadoresPara(GrupoSanguineo receptor,
                                                    TipoHemocomponente tipo);

    /*
     * A linha crua da tabela, como vetor de bits: o bit i ligado significa que
     * o grupo de ordinal i pode doar. Existe para a demonstracao poder
     * comparar a tabela inteira de uma vez com a referencia extraida do Java.
     */
    static unsigned char doadoresEmBits(GrupoSanguineo receptor,
                                        TipoHemocomponente tipo);
};

#endif /* MATRIZ_COMPATIBILIDADE_HPP */
