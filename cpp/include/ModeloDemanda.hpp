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
 * 2. DISTRIBUIÇÃO ESCOLHIDA: BINOMIAL NEGATIVA D ~ BN(mu, sigma^2),
 *    COM A POISSON COMO REFERÊNCIA E A NORMAL (TCL) COMO APROXIMAÇÃO.
 *
 * 3. JUSTIFICATIVA ESTATÍSTICA E OPERACIONAL:
 *    a) As requisições chegam de forma independente, o que sugere Poisson para a
 *       contagem de PEDIDOS. Mas a demanda é medida em BOLSAS, e cada item de pedido
 *       traz de 1 a 4 bolsas: a soma vira uma Poisson composta, cuja variância é
 *       maior que a média (sobredispersão).
 *    b) Nos dados sintéticos, a variância da demanda diária fica entre 2 e 4 vezes a
 *       média em todos os tipos. A Poisson impõe variância = média e, por isso,
 *       subestima o risco justamente nos tipos com estoque próximo da demanda.
 *    c) A Binomial Negativa é a distribuição discreta padrão para contagens
 *       sobredispersas: é parametrizada pela média e pela variância observadas na
 *       série diária e se reduz à Poisson quando variância = média.
 *    d) Teorema Central do Limite (TCL): a demanda do horizonte é a soma de N dias
 *       independentes, logo D ~ N(N*mu_dia, N*sigma²_dia), usando a variância real.
 *    e) Validação por Monte Carlo (bootstrap): reamostra os dias observados com
 *       reposição, sem supor distribuição alguma, e confere as soluções analíticas.
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
     * @brief CDF da Binomial Negativa parametrizada por média e variância.
     * Com variância <= média não há sobredispersão e o cálculo cai na Poisson.
     */
    static double binomialNegativaCDF(double media, double variancia, int k);
    double calcularProbabilidadeExcedenteBinomialNegativa(double media, double variancia, int estoqueDisponivel) const;

    /**
     * @brief Variância da demanda total no horizonte: N * s² da série diária
     * (soma de N dias independentes). Retorna -1 quando a série tem menos de 2 dias.
     */
    static double varianciaDoHorizonte(const std::vector<long>& serieDiaria);

    /**
     * @brief Monte Carlo com distribuição de Poisson (semente reproduzível): P_hat(D > S).
     * Usado apenas quando não há série diária para reamostrar.
     */
    double simularProbabilidadeDesabastecimentoMonteCarlo(
        double lambda, int estoqueDisponivel, int numSimulacoes = 100000, unsigned int semente = 2026
    ) const;

    /**
     * @brief Monte Carlo por bootstrap: cada simulação sorteia N dias da série
     * observada, com reposição, e soma a demanda. Não supõe distribuição teórica.
     */
    double simularProbabilidadeDesabastecimentoBootstrap(
        const std::vector<long>& serieDiaria, int estoqueDisponivel,
        int numSimulacoes = 100000, unsigned int semente = 2026
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
