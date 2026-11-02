#include "Dominio.hpp"
#include "AcumuladorEstoque.hpp"
#include "AcumuladorDemanda.hpp"
#include "ModeloDemanda.hpp"
#include "CalculadorDesabastecimento.hpp"
#include "PainelConsolidado.hpp"
#include "LeitorDadosSinteticos.hpp"
#include <iostream>
#include <vector>
#include <filesystem>

namespace fs = std::filesystem;

int main() {
    std::cout << "==============================================================================\n";
    std::cout << " INICIANDO SISTEMA ROTA VITAL: MODELAGEM ESTOCASTICA & DESABASTECIMENTO C++\n";
    std::cout << "==============================================================================\n";

    // 1. PIPELINE AED: ENTRADA DE DADOS (INPUT)
    std::vector<Bolsa> bolsas;
    std::vector<Requisicao> requisicoes;

    // Tentativas de caminhos para o arquivo JSON de dados sintéticos
    std::vector<std::string> caminhosTentativa = {
        "dados/dados_sinteticos.json",
        "../dados/dados_sinteticos.json",
        "../../dados/dados_sinteticos.json",
        "dados_sinteticos.json"
    };

    bool carregou = false;
    std::string caminhoEncontrado = "";
    for (const auto& caminho : caminhosTentativa) {
        if (fs::exists(caminho)) {
            std::cout << "[AED - ENTRADA] Carregando dados sinteticos de: " << caminho << " ...\n";
            if (LeitorDadosSinteticos::carregarDoJson(caminho, bolsas, requisicoes)) {
                carregou = true;
                caminhoEncontrado = caminho;
                break;
            }
        }
    }

    if (!carregou) {
        std::cout << "[AED - ENTRADA] Arquivo JSON nao localizado nos caminhos padrao.\n";
        std::cout << "[AED - ENTRADA] Ativando gerador estocastico nativo em C++ (Sementes 2026/2027)...\n";
        LeitorDadosSinteticos::gerarDadosNativos(bolsas, requisicoes, 2026, 2027);
    }

    std::cout << "[AED - ENTRADA] Sucesso! " << bolsas.size() << " bolsas e " 
              << requisicoes.size() << " requisicoes carregadas na memoria.\n\n";

    // 2. PIPELINE POO & AED: PROCESSAMENTO COM DADOS ACUMULADOS
    std::cout << "[POO - ACUMULACAO] Processando bolsas no AcumuladorEstoque...\n";
    AcumuladorEstoque acumuladorEstoque;
    for (const auto& b : bolsas) {
        acumuladorEstoque += b; // Sobrecarga de operador acumulativo (POO)
    }

    std::cout << "[POO - ACUMULACAO] Processando requisicoes no AcumuladorDemanda (Analogia do Caixa de Loja)...\n";
    AcumuladorDemanda acumuladorDemanda;
    for (const auto& r : requisicoes) {
        acumuladorDemanda += r; // Sobrecarga de operador acumulativo (POO)
    }

    // 3. MODELAGEM ESTATÍSTICA: VARIÁVEL ALEATÓRIA E CALIBRAÇÃO DO MODELO
    std::cout << "[ESTATISTICA] Calibrando modelo de demanda (Binomial Negativa, Poisson e TCL) com dados acumulados...\n";
    ModeloDemanda modeloDemanda;
    modeloDemanda.calibrarComDadosAcumulados(
        acumuladorDemanda.getDemandaPorTipo(),
        acumuladorDemanda.getDemandaPorComponente(),
        acumuladorDemanda.getDemandaCruzada(),
        acumuladorDemanda.getTotalBolsasDemandadas()
    );

    // 4. AVALIAÇÃO DE DESABASTECIMENTO (DEMANDA X ESTOQUE)
    std::cout << "[OPERACAO] Cruzando demanda estocastica contra estoque fisico valido...\n";
    CalculadorDesabastecimento calculador(acumuladorEstoque, acumuladorDemanda, modeloDemanda);

    // 5. PIPELINE AED: SAÍDAS DE DADOS (OUTPUT)
    PainelConsolidado painel(acumuladorEstoque, acumuladorDemanda, modeloDemanda, calculador);

    // 5.1. Saída Visual no Terminal (Console Output)
    painel.exibirRelatorioTerminal();

    // 5.2. Saída Estruturada em JSON para o Painel Consolidado (PI3-32).
    // Grava ao lado do arquivo de entrada, para que rodar a partir da raiz ou de cpp/ dê o mesmo resultado.
    fs::path arquivoSaida = carregou
        ? fs::path(caminhoEncontrado).parent_path() / "painel_consolidado_pi3_32.json"
        : fs::path("painel_consolidado_pi3_32.json");

    std::cout << "[AED - SAIDA] Exportando resultado para o painel PI3-32 em: " << arquivoSaida.string() << " ...\n";
    if (!painel.exportarJsonPainel(arquivoSaida.string())) {
        std::cerr << "[AED - SAIDA] Falha na geracao do arquivo JSON.\n";
        return 1;
    }
    std::cout << "[AED - SAIDA] Exportacao concluida com sucesso! Arquivo pronto para consumo pelo painel.\n";

    std::cout << "\n==============================================================================\n";
    std::cout << " EXECUCAO CONCLUIDA COM SUCESSO (PI3-102)\n";
    std::cout << "==============================================================================\n";
    return 0;
}
