#ifndef ACUMULADOR_DEMANDA_HPP
#define ACUMULADOR_DEMANDA_HPP

#include "Dominio.hpp"
#include <map>
#include <vector>

/**
 * ==============================================================================
 * CONCEITO PEDAGÓGICO: DADOS ACUMULADOS (PROGRAMAÇÃO ORIENTADA A OBJETOS - POO)
 * ==============================================================================
 * Definição:
 * Dados acumulativos são valores que vão sendo somados ou atualizados ao longo 
 * da execução do programa, conforme novas informações são recebidas.
 * 
 * Exemplo prático da disciplina:
 * Imagine um sistema de uma loja que registra o preço de vários produtos. 
 * A cada produto cadastrado, o valor dele é adicionado ao total da compra.
 * 
 * Aplicação na Engenharia do Rota Vital:
 * A classe AcumuladorDemanda atua exatamente como esse somador inteligente. 
 * Ao processar as requisições hospitalares (pedidos de emergência, urgência ou rotina), 
 * cada item contendo bolsas solicitadas é inspecionado e seu valor (quantidade 
 * de bolsas) é somado cumulativamente:
 *   1. Ao total geral de bolsas demandadas no sistema;
 *   2. Ao total demandado daquele tipo sanguíneo específico (O+, A+, etc.);
 *   3. Ao total demandado daquele hemocomponente específico (Hemácias, Plaquetas, etc.);
 *   4. À matriz estocástica combinada (Hemocomponente x Tipo Sanguíneo).
 * 
 * Em POO, essa responsabilidade fica encapsulada na classe, permitindo que o 
 * programa acumule dados dinamicamente sem poluir a lógica de negócio externa.
 * ==============================================================================
 */
class AcumuladorDemanda {
private:
    long totalRequisicoes;
    long totalBolsasDemandadas;

    // Contadores acumulativos por tipo sanguíneo
    std::map<TipoSanguineo, long> demandaPorTipo;

    // Contadores acumulativos por hemocomponente
    std::map<TipoHemocomponente, long> demandaPorComponente;

    // Matriz cruzada acumulativa: (Componente, Tipo) -> Total Demandado
    std::map<std::pair<TipoHemocomponente, TipoSanguineo>, long> demandaCruzada;

    // Contagem de requisições por nível de prioridade
    std::map<std::string, long> requisicoesPorPrioridade;

    // Histórico de volumes por requisição para cálculo de variância amostral
    std::vector<int> volumesPorRequisicao;

public:
    AcumuladorDemanda();

    /**
     * @brief Acumula um item individual de requisição hospitalar.
     * Analogia: Equivalente a adicionar um produto individual ao carrinho da loja.
     */
    void acumularItem(const ItemRequisicao& item);

    /**
     * @brief Acumula uma requisição completa emitida por um hospital.
     * Analogia: Equivalente a processar uma comanda ou lote de produtos na compra.
     */
    void acumularRequisicao(const Requisicao& req);

    // Sobrecargas de operadores POO (padrão Accumulator)
    AcumuladorDemanda& operator+=(const ItemRequisicao& item);
    AcumuladorDemanda& operator+=(const Requisicao& req);

    // Getters de dados acumulados
    long getTotalRequisicoes() const { return totalRequisicoes; }
    long getTotalBolsasDemandadas() const { return totalBolsasDemandadas; }

    long getDemanda(TipoSanguineo tipo) const;
    long getDemanda(TipoHemocomponente comp) const;
    long getDemanda(TipoHemocomponente comp, TipoSanguineo tipo) const;
    long getRequisicoesPorPrioridade(const std::string& prioridade) const;

    const std::map<TipoSanguineo, long>& getDemandaPorTipo() const { return demandaPorTipo; }
    const std::map<TipoHemocomponente, long>& getDemandaPorComponente() const { return demandaPorComponente; }
    const std::map<std::pair<TipoHemocomponente, TipoSanguineo>, long>& getDemandaCruzada() const { return demandaCruzada; }
    const std::vector<int>& getVolumesPorRequisicao() const { return volumesPorRequisicao; }

    // Medidas estatísticas sobre os dados acumulados
    double getMediaBolsasPorRequisicao() const;
    double getDesvioPadraoBolsasPorRequisicao() const;

    void reiniciar();
};

#endif // ACUMULADOR_DEMANDA_HPP
