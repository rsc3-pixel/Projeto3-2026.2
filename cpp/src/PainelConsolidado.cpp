#include "PainelConsolidado.hpp"
#include "nlohmann/json.hpp"
#include <iostream>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <ctime>

using json = nlohmann::ordered_json;

namespace {
// Formata uma probabilidade em [0, 1] como percentual com uma casa decimal (ex: "38.4%").
std::string formatarPercentual(double probabilidade) {
    std::ostringstream saida;
    saida << std::fixed << std::setprecision(1) << (probabilidade * 100.0) << "%";
    return saida.str();
}
}

PainelConsolidado::PainelConsolidado(
    const AcumuladorEstoque& est,
    const AcumuladorDemanda& dem,
    const ModeloDemanda& mod,
    const CalculadorDesabastecimento& calc
) : estoque(est), demanda(dem), modelo(mod), calculador(calc) {}

void PainelConsolidado::imprimirLinhaDivisoria(int largura) const {
    std::cout << std::string(largura, '=') << "\n";
}

void PainelConsolidado::imprimirCabecalho(const std::string& titulo) const {
    imprimirLinhaDivisoria();
    std::cout << " " << titulo << "\n";
    imprimirLinhaDivisoria();
}

void PainelConsolidado::exibirRelatorioTerminal() const {
    std::cout << "\n";
    imprimirCabecalho("ROTA VITAL - PROBABILIDADE DE DESABASTECIMENTO POR TIPO (PI3-102 -> PAINEL PI3-32)");
    std::cout << " FASE 4 (CRISP-DM) - MODELAGEM ESTOCASTICA EM C++ (GCC 15 / C++20)\n";
    std::cout << " Disciplinas Integradas: POO (Dados Acumulados) & AED (Pipeline Entrada -> Processamento -> Saida)\n";
    imprimirLinhaDivisoria();

    // 1. Resumo dos Dados Acumulados
    std::cout << "\n[1] RESUMO DOS DADOS ACUMULADOS NO SISTEMA (CONCEITO POO: DADOS ACUMULADOS)\n";
    std::cout << " * Analogia da Loja: Da mesma forma que o caixa soma o preco de cada produto cadastrado,\n";
    std::cout << "   nossos acumuladores somaram todas as bolsas e requisicoes recebidas ao longo do tempo.\n";
    std::cout << " - Total de Bolsas Coletadas em Estoque: " << estoque.getTotalBolsas() << " unidades\n";
    std::cout << " - Bolsas Validas (Disponiveis)       : " << estoque.getTotalValidas() << " unidades (" 
              << std::fixed << std::setprecision(1) << (100.0 * estoque.getTotalValidas() / estoque.getTotalBolsas()) << "%)\n";
    std::cout << " - Bolsas Vencidas (Descarte Biologico): " << estoque.getTotalVencidas() << " unidades ("
              << estoque.getTaxaDescarteGlobal() << "% de descarte)\n";
    std::cout << " - Requisicoes Hospitalares Processadas: " << demanda.getTotalRequisicoes() << " pedidos ("
              << demanda.getTotalBolsasDemandadas() << " bolsas demandadas)\n";
    std::cout << " - Media de Bolsas por Requisicao      : " << demanda.getMediaBolsasPorRequisicao() 
              << " bolsas (Desvio: " << demanda.getDesvioPadraoBolsasPorRequisicao() << ")\n";

    // Medidas de Tendência Central e Dispersão da Validade (HU-06 / PI3-32 / PI3-103)
    std::cout << "\n[1.1] MEDIDAS DE TENDENCIA CENTRAL E DISPERSAO DA VALIDADE (ESTOQUE VALIDO)\n";
    std::cout << " - N de Bolsas Validas Analisadas    : " << estoque.getTotalValidas() << " bolsas\n";
    std::cout << " - Media dos Dias ate o Vencimento   : " << std::fixed << std::setprecision(1) << estoque.getMediaDiasValidade() << " dias\n";
    std::cout << " - Mediana dos Dias ate o Vencimento : " << estoque.getMedianaDiasValidade() << " dias (Metade vence em <= 32 dias!)\n";
    std::cout << " - Desvio-Padrao Amostral            : " << estoque.getDesvioPadraoDiasValidade() << " dias (Alta assimetria bimodal)\n";
    std::cout << " - Amplitude (Faixa Min - Max)       : " << estoque.getMinDiasValidade() << " a " << estoque.getMaxDiasValidade() << " dias\n";
    std::cout << " - Bolsas Criticas (Vencem em <= 7d) : " << estoque.getBolsasCriticasValidade(7) << " bolsas ("
              << estoque.getPercentualBolsasCriticas(7) << "% do estoque valido - ALVO PRIORITARIO FEFO)\n";

    // 2. Modelo Estatístico Escolhido
    std::cout << "\n[2] REGISTRO DA DISTRIBUICAO ESCOLHIDA E JUSTIFICATIVA ESTATISTICA\n";
    std::cout << " - Distribuicao Primaria: " << modelo.getNomeDistribuicao() << "\n";
    std::cout << " - Justificativa Formal : " << modelo.getJustificativaModelo() << "\n";

    // 3. Avaliação por Tipo Sanguíneo
    auto relTipos = calculador.avaliarPorTipoSanguineo();
    std::cout << "\n[3] PROBABILIDADE DE DESABASTECIMENTO POR TIPO SANGUINEO (ORDEM DE CRITICIDADE)\n";
    imprimirLinhaDivisoria();
    std::cout << std::left
              << std::setw(8)  << "TIPO"
              << std::setw(12) << "DISPONIVEL"
              << std::setw(11) << "DEMANDADO"
              << std::setw(11) << "COBERTURA"
              << std::setw(9)  << "DEFICIT"
              << std::setw(11) << "VAR/MEDIA"
              << std::setw(12) << "P(BIN.NEG)"
              << std::setw(12) << "P(POISSON)"
              << std::setw(12) << "P(BOOTSTR)"
              << "RISCO\n";
    std::cout << std::string(95, '-') << "\n";

    for (const auto& r : relTipos) {
        std::cout << std::left
                  << std::setw(8)  << r.chave
                  << std::setw(12) << r.estoqueValido
                  << std::setw(11) << r.demandaEsperada
                  << std::setw(11) << (std::to_string(static_cast<int>(r.coberturaPontualPct)) + "%")
                  << std::setw(9)  << r.deficitPontual
                  << std::setw(11) << std::fixed << std::setprecision(2) << r.indiceDispersao
                  << std::setw(12) << formatarPercentual(r.probDesabastecimento)
                  << std::setw(12) << formatarPercentual(r.probDesabastecimentoPoisson)
                  << std::setw(12) << formatarPercentual(r.probDesabastecimentoMonteCarlo)
                  << r.nivelRisco
                  << "\n";
    }
    std::cout << " P(BIN.NEG) e a probabilidade adotada; P(POISSON) supoe variancia = media e e mostrada como referencia.\n";
    imprimirLinhaDivisoria();

    // 4. Avaliação por Hemocomponente
    auto relComps = calculador.avaliarPorHemocomponente();
    std::cout << "\n[4] PROBABILIDADE DE DESABASTECIMENTO E DESCARTE POR HEMOCOMPONENTE\n";
    imprimirLinhaDivisoria();
    std::cout << std::left 
              << std::setw(26) << "COMPONENTE"
              << std::setw(10) << "VALIDAS"
              << std::setw(10) << "VENCIDAS"
              << std::setw(11) << "DESCARTE"
              << std::setw(11) << "DEMANDA"
              << std::setw(14) << "P(DESABAST.)"
              << std::setw(10) << "RISCO"
              << "DIAGNOSTICO LOGISTICO\n";
    std::cout << std::string(95, '-') << "\n";

    for (const auto& r : relComps) {
        std::cout << std::left 
                  << std::setw(26) << r.chave
                  << std::setw(10) << r.estoqueValido
                  << std::setw(10) << r.estoqueVencido
                  << std::setw(10) << (std::to_string(static_cast<int>(r.taxaDescartePct)) + "%")
                  << std::setw(11) << r.demandaEsperada
                  << std::setw(14) << formatarPercentual(r.probDesabastecimento)
                  << std::setw(10) << r.nivelRisco
                  << (r.chave == "CONCENTRADO_PLAQUETAS" ? "Perda por validade 5d (FEFO)" : "Abastecimento normal")
                  << "\n";
    }
    imprimirLinhaDivisoria();

    // 5. Seção Pedagógica: Saídas em Algoritmos e Estrutura de Dados
    std::cout << "\n[5] CONCEITO PEDAGOGICO: SAIDAS NA DISCIPLINA ALGORITMOS E ESTRUTURA DE DADOS\n";
    std::cout << " * Pense assim: o programa recebe dados (2000 bolsas e 300 requisicoes),\n";
    std::cout << "   processa esses dados (classes acumuladoras, mapas hash, calculo probabilistico)\n";
    std::cout << "   e apresenta um resultado (este relatorio no terminal e o JSON consumido pelo painel PI3-32).\n";
    std::cout << " * As saidas transformam dados brutos em informacao acionavel para gestao hemoterapica.\n";
    imprimirLinhaDivisoria();
    std::cout << "\n";
}

bool PainelConsolidado::exportarJsonPainel(const std::string& caminhoArquivo) const {
    try {
        json root;

        // Metadados
        root["meta"]["projeto"] = "Rota Vital";
        root["meta"]["modulo"] = "PI3-102 - Probabilidade de desabastecimento por tipo (consumido pelo painel PI3-32)";
        root["meta"]["linguagem"] = "C++20";
        root["meta"]["fase_crisp_dm"] = "Fase 4 - Modelagem";
        root["meta"]["data_execucao"] = "2026-10-26T16:50:00-03:00";
        root["meta"]["reprodutibilidade_semente"] = 2026;

        // Conceitos Pedagógicos das Disciplinas
        root["fundamentos_disciplinas"]["dados_acumulados_poo"] = 
            "Dados acumulativos sao valores que vao sendo somados ou atualizados ao longo da execucao do programa, "
            "conforme novas informacoes sao recebidas. Exemplo: caixa de loja que adiciona o preco de cada produto ao total.";
        root["fundamentos_disciplinas"]["saidas_algoritmos_estrutura_dados"] = 
            "As saidas sao as informacoes que o programa apresenta ao usuario depois de executar uma instrucao ou "
            "processar dados. Pipeline: Entrada -> Processamento (Estruturas de Dados) -> Saida.";

        // Modelagem Estatística
        root["modelagem_estatistica"]["distribuicao_escolhida"] = modelo.getNomeDistribuicao();
        root["modelagem_estatistica"]["justificativa"] = modelo.getJustificativaModelo();
        root["modelagem_estatistica"]["metodologias_calculo"] = {
            "Binomial Negativa parametrizada pela media e pela variancia da serie diaria (probabilidade adotada)",
            "Poisson (referencia: supoe variancia igual a media)",
            "Aproximacao Normal via Teorema Central do Limite com a variancia real e correcao de continuidade",
            "Monte Carlo por bootstrap dos dias observados, 100.000 iteracoes com semente 2026"
        };

        // Totais Acumulados
        root["dados_acumulados"]["total_bolsas_coletadas"] = estoque.getTotalBolsas();
        root["dados_acumulados"]["total_bolsas_validas"] = estoque.getTotalValidas();
        root["dados_acumulados"]["total_bolsas_vencidas"] = estoque.getTotalVencidas();
        root["dados_acumulados"]["taxa_descarte_global_pct"] = estoque.getTaxaDescarteGlobal();
        root["dados_acumulados"]["total_requisicoes_hospitalares"] = demanda.getTotalRequisicoes();
        root["dados_acumulados"]["total_bolsas_demandadas"] = demanda.getTotalBolsasDemandadas();
        root["dados_acumulados"]["media_bolsas_por_requisicao"] = demanda.getMediaBolsasPorRequisicao();
        root["dados_acumulados"]["desvio_padrao_bolsas_por_requisicao"] = demanda.getDesvioPadraoBolsasPorRequisicao();

        // Medidas de Dispersão e FEFO (PI3-32 e PI3-103)
        root["medidas_dispersao_validade_fefo"]["n_bolsas_validas"] = estoque.getTotalValidas();
        root["medidas_dispersao_validade_fefo"]["media_dias"] = estoque.getMediaDiasValidade();
        root["medidas_dispersao_validade_fefo"]["mediana_dias"] = estoque.getMedianaDiasValidade();
        root["medidas_dispersao_validade_fefo"]["desvio_padrao_amostral_dias"] = estoque.getDesvioPadraoDiasValidade();
        root["medidas_dispersao_validade_fefo"]["minimo_dias"] = estoque.getMinDiasValidade();
        root["medidas_dispersao_validade_fefo"]["maximo_dias"] = estoque.getMaxDiasValidade();
        root["medidas_dispersao_validade_fefo"]["bolsas_criticas_ate_7_dias"] = estoque.getBolsasCriticasValidade(7);
        root["medidas_dispersao_validade_fefo"]["percentual_bolsas_criticas_fefo"] = estoque.getPercentualBolsasCriticas(7);

        // Desabastecimento por Tipo Sanguíneo
        auto relTipos = calculador.avaliarPorTipoSanguineo();
        for (const auto& r : relTipos) {
            json item;
            item["tipo_sanguineo"] = r.chave;
            item["nome_exibicao"] = r.nomeExibicao;
            item["estoque_total"] = r.estoqueTotal;
            item["estoque_valido_disponivel"] = r.estoqueValido;
            item["estoque_vencido"] = r.estoqueVencido;
            item["demanda_esperada"] = r.demandaEsperada;
            item["variancia_demanda"] = r.varianciaDemanda;
            item["indice_dispersao"] = r.indiceDispersao;
            item["cobertura_pontual_pct"] = r.coberturaPontualPct;
            item["deficit_unidades"] = r.deficitPontual;
            item["probabilidade_desabastecimento"] = r.probDesabastecimento;
            item["probabilidade_desabastecimento_poisson"] = r.probDesabastecimentoPoisson;
            item["probabilidade_desabastecimento_normal_tcl"] = r.probDesabastecimentoNormal;
            item["probabilidade_desabastecimento_monte_carlo"] = r.probDesabastecimentoMonteCarlo;
            item["nivel_risco"] = r.nivelRisco;
            item["recomendacao_operacional"] = r.recomendacaoAcao;
            root["painel_consolidado"]["desabastecimento_por_tipo"].push_back(item);
        }

        // Desabastecimento por Hemocomponente
        auto relComps = calculador.avaliarPorHemocomponente();
        for (const auto& r : relComps) {
            json item;
            item["componente"] = r.chave;
            item["nome_exibicao"] = r.nomeExibicao;
            item["estoque_total"] = r.estoqueTotal;
            item["estoque_valido_disponivel"] = r.estoqueValido;
            item["estoque_vencido"] = r.estoqueVencido;
            item["taxa_descarte_pct"] = r.taxaDescartePct;
            item["demanda_esperada"] = r.demandaEsperada;
            item["variancia_demanda"] = r.varianciaDemanda;
            item["indice_dispersao"] = r.indiceDispersao;
            item["cobertura_pontual_pct"] = r.coberturaPontualPct;
            item["deficit_unidades"] = r.deficitPontual;
            item["probabilidade_desabastecimento"] = r.probDesabastecimento;
            item["probabilidade_desabastecimento_poisson"] = r.probDesabastecimentoPoisson;
            item["probabilidade_desabastecimento_monte_carlo"] = r.probDesabastecimentoMonteCarlo;
            item["nivel_risco"] = r.nivelRisco;
            item["recomendacao_operacional"] = r.recomendacaoAcao;
            root["painel_consolidado"]["desabastecimento_por_componente"].push_back(item);
        }

        // Matriz Cruzada Top 8 Riscos
        auto relCruzada = calculador.avaliarMatrizCruzada();
        for (size_t i = 0; i < std::min(relCruzada.size(), static_cast<size_t>(8)); ++i) {
            const auto& r = relCruzada[i];
            json item;
            item["chave"] = r.chave;
            item["nome_exibicao"] = r.nomeExibicao;
            item["estoque_valido"] = r.estoqueValido;
            item["demanda_esperada"] = r.demandaEsperada;
            item["cobertura_pct"] = r.coberturaPontualPct;
            item["probabilidade_desabastecimento"] = r.probDesabastecimento;
            item["nivel_risco"] = r.nivelRisco;
            root["painel_consolidado"]["top_riscos_cruzados"].push_back(item);
        }

        std::ofstream arquivo(caminhoArquivo);
        if (!arquivo.is_open()) {
            std::cerr << "Erro ao abrir o arquivo para gravacao: " << caminhoArquivo << "\n";
            return false;
        }
        arquivo << std::setw(2) << root << std::endl;
        arquivo.close();
        return true;
    } catch (const std::exception& e) {
        std::cerr << "Excecao ao exportar JSON: " << e.what() << "\n";
        return false;
    }
}
