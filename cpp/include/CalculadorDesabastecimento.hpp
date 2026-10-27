#ifndef CALCULADOR_DESABASTECIMENTO_HPP
#define CALCULADOR_DESABASTECIMENTO_HPP

#include "Dominio.hpp"
#include "AcumuladorEstoque.hpp"
#include "AcumuladorDemanda.hpp"
#include "ModeloDemanda.hpp"
#include <vector>
#include <string>

/**
 * @brief Resultado detalhado da avaliação de risco de desabastecimento para uma categoria.
 */
struct RelatorioDesabastecimento {
    std::string chave;                       // Ex: "A_POS" ou "CONCENTRADO_HEMACIAS"
    std::string nomeExibicao;                // Ex: "A+" ou "Concentrado de Hemacias"
    long estoqueTotal;                       // Total físico armazenado
    long estoqueValido;                      // Apenas bolsas válidas (disponíveis)
    long estoqueVencido;                     // Bolsas descartadas
    double taxaDescartePct;                  // % vencidas / total
    long demandaEsperada;                    // E[D] = lambda observado
    double coberturaPontualPct;              // (EstoqueValido / Demanda) * 100
    long deficitPontual;                     // max(0, Demanda - EstoqueValido)
    double probDesabastecimentoPoisson;      // P(D > S) calculada por Poisson
    double probDesabastecimentoNormal;       // P(D > S) calculada pela Normal (TCL)
    double probDesabastecimentoMonteCarlo;   // P_hat(D > S) via Monte Carlo (100k sim)
    std::string nivelRisco;                  // "CRITICO", "ALTO", "MODERADO", "BAIXO"
    std::string recomendacaoAcao;            // Decisão recomendada para a operação
};

/**
 * ==============================================================================
 * CLASSE CALCULADOR_DESABASTECIMENTO (ANÁLISE COMPARATIVA DEMANDA X ESTOQUE)
 * ==============================================================================
 * Esta classe é responsável por confrontar as variáveis estocásticas da demanda 
 * contra a disponibilidade física do estoque de hemocomponentes.
 * 
 * Regra fundamental de negócio:
 * Bolsas vencidas NÃO podem atender requisições hospitalares. Logo, a variável de 
 * estoque físico S considera estritamente as bolsas VÁLIDAS.
 * 
 * Risco de Desabastecimento:
 * Desabastecimento ocorre quando a demanda estocástica supera o estoque válido:
 *     Evento = { D > S_valido }
 *     P(Desabastecimento) = 1 - F_D(S_valido)
 * ==============================================================================
 */
class CalculadorDesabastecimento {
private:
    const AcumuladorEstoque& estoque;
    const AcumuladorDemanda& demanda;
    const ModeloDemanda& modelo;

public:
    CalculadorDesabastecimento(
        const AcumuladorEstoque& est,
        const AcumuladorDemanda& dem,
        const ModeloDemanda& mod
    );

    /**
     * @brief Avalia o risco de desabastecimento para todos os 8 tipos sanguíneos.
     */
    std::vector<RelatorioDesabastecimento> avaliarPorTipoSanguineo() const;

    /**
     * @brief Avalia o risco de desabastecimento para todos os 4 hemocomponentes.
     */
    std::vector<RelatorioDesabastecimento> avaliarPorHemocomponente() const;

    /**
     * @brief Avalia a matriz cruzada (Hemocomponente x Tipo Sanguíneo).
     */
    std::vector<RelatorioDesabastecimento> avaliarMatrizCruzada() const;

private:
    static std::string classificarRisco(double probDesabastecimento, double cobertura);
    static std::string gerarRecomendacao(
        const std::string& identificador, double probDesabastecimento, double cobertura, double taxaDescarte
    );
};

#endif // CALCULADOR_DESABASTECIMENTO_HPP
