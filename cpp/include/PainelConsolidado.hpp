#ifndef PAINEL_CONSOLIDADO_HPP
#define PAINEL_CONSOLIDADO_HPP

#include "CalculadorDesabastecimento.hpp"
#include <string>
#include <vector>

/**
 * ==============================================================================
 * CONCEITO PEDAGÓGICO: SAÍDAS NA DISCIPLINA ALGORITMOS E ESTRUTURA DE DADOS (AED)
 * ==============================================================================
 * Definição:
 * As saídas são as informações que o programa apresenta ao usuário depois de 
 * executar uma instrução ou processar dados.
 * Pense assim: o programa recebe dados (ENTRADA), processa esses dados através 
 * de algoritmos e estruturas de dados (PROCESSAMENTO) e apresenta um resultado (SAÍDA).
 * 
 * No Rota Vital:
 * O PainelConsolidado é a camada de SAÍDA do pipeline de AED:
 *   1. ENTRADA: 2.000 bolsas e 300 requisições hospitalares (dados brutos).
 *   2. PROCESSAMENTO: Acumuladores de POO, estruturas de dados de busca rápida 
 *      (std::map, std::vector) e algoritmos probabilísticos de Poisson/TCL/Monte Carlo.
 *   3. SAÍDA (Apresentação ao Usuário):
 *      a) Terminal Console: Relatório tabular visual de alto impacto com bordas, 
 *         percentuais alinhados, alertas e síntese para tomada de decisão.
 *      b) Arquivo JSON Estruturado (PI3-32): Arquivo consolidado pronto para ser 
 *         consumido pela API REST e exibido no dashboard de indicadores hospitalares.
 * ==============================================================================
 */
class PainelConsolidado {
private:
    const AcumuladorEstoque& estoque;
    const AcumuladorDemanda& demanda;
    const ModeloDemanda& modelo;
    const CalculadorDesabastecimento& calculador;

public:
    PainelConsolidado(
        const AcumuladorEstoque& est,
        const AcumuladorDemanda& dem,
        const ModeloDemanda& mod,
        const CalculadorDesabastecimento& calc
    );

    /**
     * @brief Apresenta a saída visual no terminal (Console Output).
     */
    void exibirRelatorioTerminal() const;

    /**
     * @brief Exporta a saída estruturada em formato JSON para o painel consolidado (PI3-32).
     */
    bool exportarJsonPainel(const std::string& caminhoArquivo) const;

private:
    void imprimirCabecalho(const std::string& titulo) const;
    void imprimirLinhaDivisoria(int largura = 95) const;
};

#endif // PAINEL_CONSOLIDADO_HPP
