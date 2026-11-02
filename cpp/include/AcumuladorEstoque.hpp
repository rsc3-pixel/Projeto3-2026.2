#ifndef ACUMULADOR_ESTOQUE_HPP
#define ACUMULADOR_ESTOQUE_HPP

#include "Dominio.hpp"
#include <map>
#include <vector>

/**
 * ==============================================================================
 * CONCEITO PEDAGÓGICO: DADOS ACUMULADOS (PROGRAMAÇÃO ORIENTADA A OBJETOS - POO)
 * ==============================================================================
 * Dados acumulativos são valores que vão sendo somados ou atualizados ao longo 
 * da execução do programa, conforme novas informações são recebidas.
 * 
 * Analogia do sistema de loja:
 * Imagine um sistema de uma loja que registra o preço de vários produtos. 
 * A cada produto cadastrado ou passado pelo leitor do caixa, o valor dele é 
 * adicionado progressivamente ao total da compra.
 * 
 * No contexto do Rota Vital:
 * A classe AcumuladorEstoque representa esse "caixa centralizador do banco de 
 * sangue". À medida que cada bolsa física é recebida da coleta (ou lida do 
 * sistema), seus atributos são inspecionados (validade, tipo sanguíneo, 
 * hemocomponente) e os contadores internos são incrementados continuamente.
 * O encapsulamento em POO protege o estado acumulado contra modificações espúrias.
 * ==============================================================================
 */
class AcumuladorEstoque {
private:
    long totalBolsas;
    long totalValidas;
    long totalVencidas;

    // Contadores acumulados por tipo sanguíneo
    std::map<TipoSanguineo, long> estoqueGeralPorTipo;
    std::map<TipoSanguineo, long> estoqueValidoPorTipo;
    std::map<TipoSanguineo, long> estoqueVencidoPorTipo;

    // Contadores acumulados por hemocomponente
    std::map<TipoHemocomponente, long> estoqueGeralPorComponente;
    std::map<TipoHemocomponente, long> estoqueValidoPorComponente;
    std::map<TipoHemocomponente, long> estoqueVencidoPorComponente;

    // Matriz cruzada acumulada: (Componente, Tipo) -> Estoque Válido
    std::map<std::pair<TipoHemocomponente, TipoSanguineo>, long> estoqueValidoCruzado;

    // Lista de dias restantes de bolsas válidas para cálculo de dispersão
    std::vector<int> diasAteVencerValidas;

public:
    AcumuladorEstoque();

    /**
     * @brief Registra uma nova bolsa no estoque, somando aos totais acumulados.
     * Analogia: Equivalente a passar um produto no caixa e somar ao total da compra.
     */
    void acumular(const Bolsa& bolsa);

    // Sobrecarga de operador para uso idiomático em POO (acumulador)
    AcumuladorEstoque& operator+=(const Bolsa& bolsa);

    // Getters de dados acumulados
    long getTotalBolsas() const { return totalBolsas; }
    long getTotalValidas() const { return totalValidas; }
    long getTotalVencidas() const { return totalVencidas; }
    double getTaxaDescarteGlobal() const;

    long getEstoqueValido(TipoSanguineo tipo) const;
    long getEstoqueTotal(TipoSanguineo tipo) const;
    long getEstoqueVencido(TipoSanguineo tipo) const;

    long getEstoqueValido(TipoHemocomponente comp) const;
    long getEstoqueTotal(TipoHemocomponente comp) const;
    long getEstoqueVencido(TipoHemocomponente comp) const;
    double getTaxaDescarte(TipoHemocomponente comp) const;

    long getEstoqueValido(TipoHemocomponente comp, TipoSanguineo tipo) const;

    const std::map<TipoSanguineo, long>& getEstoqueValidoPorTipo() const { return estoqueValidoPorTipo; }
    const std::map<TipoHemocomponente, long>& getEstoqueValidoPorComponente() const { return estoqueValidoPorComponente; }
    const std::vector<int>& getDiasAteVencerValidas() const { return diasAteVencerValidas; }

    // Medidas de tendencia central e dispersao da validade (HU-06 / PI3-32)
    double getMediaDiasValidade() const;
    double getMedianaDiasValidade() const;
    double getDesvioPadraoDiasValidade() const;
    int getMinDiasValidade() const;
    int getMaxDiasValidade() const;
    long getBolsasCriticasValidade(int limiteDias = 7) const;
    double getPercentualBolsasCriticas(int limiteDias = 7) const;

    void reiniciar();
};

#endif // ACUMULADOR_ESTOQUE_HPP
