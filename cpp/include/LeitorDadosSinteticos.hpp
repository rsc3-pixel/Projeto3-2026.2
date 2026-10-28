#ifndef LEITOR_DADOS_SINTETICOS_HPP
#define LEITOR_DADOS_SINTETICOS_HPP

#include "Dominio.hpp"
#include <vector>
#include <string>

/**
 * ==============================================================================
 * ENTRADA DE DADOS (PIPELINE DE ALGORITMOS E ESTRUTURA DE DADOS)
 * ==============================================================================
 * A classe LeitorDadosSinteticos e responsavel por obter os dados de entrada 
 * do problema:
 *   - Modo 1: Leitura do arquivo oficial dados_sinteticos.json gerado pelas premissas;
 *   - Modo 2: Gerador estocastico nativo em C++ reproduzivel (semente 2026/2027) 
 *     que emula as distribuicoes de premissas.py caso o arquivo JSON nao esteja presente.
 * ==============================================================================
 */
class LeitorDadosSinteticos {
public:
    static bool carregarDoJson(
        const std::string& caminhoArquivo,
        std::vector<Bolsa>& bolsas,
        std::vector<Requisicao>& requisicoes
    );

    static void gerarDadosNativos(
        std::vector<Bolsa>& bolsas,
        std::vector<Requisicao>& requisicoes,
        unsigned int sementeBolsas = 2026,
        unsigned int sementeRequisicoes = 2027
    );
};

#endif // LEITOR_DADOS_SINTETICOS_HPP
