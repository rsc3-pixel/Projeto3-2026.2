#ifndef MODELO_DEMANDA_HPP
#define MODELO_DEMANDA_HPP

#include "Dominio.hpp"
#include <string>
#include <vector>
#include <map>

/**
 * @brief Estrutura que armazena os parâmetros estocásticos de uma variável aleatória de demanda.
 */
struct ParametrosDemanda {
    std::string identificador;      // Ex: "A_POS" ou "CONCENTRADO_HEMACIAS"
    std::string distribuicao;       // "POISSON", "NORMAL_TCL", "MONTE_CARLO"
    double lambda;                  // Parâmetro de taxa/média esperada de Poisson E[D]
    double media;                   // Média estimada mu
    double desvioPadrao;            // Desvio-padrão sigma
    long valorObservado;            // Total acumulado observado na amostra
    double probabilidadeAmostral;   // Proporção p_i sobre a demanda global
    std::string justificativa;      // Justificativa teórica e empírica da escolha da distribuição
};

/**
 * ==============================================================================
 * MODELAGEM DA DEMANDA COMO VARIÁVEL ALEATÓRIA (ESTATÍSTICA E CIÊNCIA DE DADOS)
 * ==============================================================================
 * 
 * 1. DEFINIÇÃO DA VARIÁVEL ALEATÓRIA:
 *    A demanda D por hemocomponentes em hospitais é uma variável aleatória 
 *    discreta não-negativa (D in {0, 1, 2, ...}) decorrente de processos 
 *    independentes de internação, cirurgias de emergência e rotina clínica.
 * 
 * 2. DISTRIBUIÇÃO ESCOLHIDA: DISTRIBUIÇÃO DE POISSON D ~ Poisson(lambda)
 *    E APROXIMAÇÃO NORMAL D ~ N(mu, sigma^2) PELO TEOREMA CENTRAL DO LIMITE (TCL).
 * 
 * 3. JUSTIFICATIVA ESTATÍSTICA E OPERACIONAL:
 *    a) Natureza dos eventos de saúde: As requisições hospitalares chegam como um 
 *       Processo Pontual de Poisson homogêneo com taxa constante lambda_req.
 *    b) Princípio de Poisson Thinning (Desmembramento): Ao subdividir as requisições 
 *       por tipo sanguíneo (com probabilidade p_i baseada na prevalência populacional) 
 *       ou por hemocomponente, a teoria das probabilidades garante que cada fluxo 
 *       individual de demanda D_i também segue rigorosamente uma Distribuição de Poisson:
 *           D_i ~ Poisson(lambda_i), com lambda_i = E[D_i].
 *    c) Teorema Central do Limite (TCL): Para tipos de grande circulação (A+ e O+ com 
 *       mais de 500 bolsas acumuladas), a soma de múltiplas variáveis aleatórias 
 *       independentes converge para a Normal N(lambda, lambda), permitindo o cálculo 
 *       contínuo com correção de continuidade de Yates de forma extremamente veloz e estável.
 *    d) Validação via Simulação de Monte Carlo: O modelo contrasta as soluções analíticas 
 *       (Poisson e Normal) com simulações estocásticas empíricas (100.000 iterações), 
 *       assegurando reprodutibilidade e fidelidade ao protocolo CRISP-DM (Fase 4 - Modelagem).
 * ==============================================================================
 */
class ModeloDemanda {
private:
    std::string nomeDistribuicao;
    std::string justificativaModelo;

    // Parâmetros ajustados para cada tipo sanguíneo
    std::map<TipoSanguineo, ParametrosDemanda> parametrosPorTipo;

    // Parâmetros ajustados para cada hemocomponente
    std::map<TipoHemocomponente, ParametrosDemanda> parametrosPorComponente;

    // Parâmetros ajustados para pares cruzados (Componente, Tipo)
    std::map<std::pair<TipoHemocomponente, TipoSanguineo>, ParametrosDemanda> parametrosCruzados;

public:
    ModeloDemanda();

    /**
     * @brief Ajusta os parâmetros das distribuições a partir dos dados acumulados da amostra.
     */
    void calibrarComDadosAcumulados(
        const std::map<TipoSanguineo, long>& demandaPorTipo,
        const std::map<TipoHemocomponente, long>& demandaPorComponente,
        const std::map<std::pair<TipoHemocomponente, TipoSanguineo>, long>& demandaCruzada,
        long totalGeralDemandado
    );

    // Métodos analíticos de probabilidade
    static double poissonPMF(double lambda, int k);
    static double poissonCDF(double lambda, int k);
    static double normalCDF(double media, double desvio, double x);

    /**
     * @brief Calcula a probabilidade de que a demanda D seja estritamente maior que o estoque S.
     * P(D > S) = 1 - P(D <= S) = 1 - CDF(S)
     */
    double calcularProbabilidadeExcedentePoisson(double lambda, int estoqueDisponivel) const;
    double calcularProbabilidadeExcedenteNormal(double media, double desvio, int estoqueDisponivel) const;

    /**
     * @brief Simula estocasticamente a demanda via Monte Carlo determinístico (semente reproduzível)
     * e retorna a probabilidade empírica de desabastecimento: P_hat(D > S).
     */
    double simularProbabilidadeDesabastecimentoMonteCarlo(
        double lambda, int estoqueDisponivel, int numSimulacoes = 100000, unsigned int semente = 2026
    ) const;

    // Getters de documentação e parâmetros
    const std::string& getNomeDistribuicao() const { return nomeDistribuicao; }
    const std::string& getJustificativaModelo() const { return justificativaModelo; }

    const ParametrosDemanda& getParametros(TipoSanguineo tipo) const;
    const ParametrosDemanda& getParametros(TipoHemocomponente comp) const;
    const ParametrosDemanda& getParametros(TipoHemocomponente comp, TipoSanguineo tipo) const;

    const std::map<TipoSanguineo, ParametrosDemanda>& getParametrosPorTipo() const { return parametrosPorTipo; }
    const std::map<TipoHemocomponente, ParametrosDemanda>& getParametrosPorComponente() const { return parametrosPorComponente; }
};

#endif // MODELO_DEMANDA_HPP
