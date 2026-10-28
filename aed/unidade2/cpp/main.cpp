/*
 * Rota Vital - AED Unidade 2
 *
 * Demonstracao da fila FEFO por insercao ordenada: a bolsa que vence primeiro
 * esta sempre no inicio, sem nenhum passo de ordenacao depois.
 *
 * VERSAO EM C++. A versao original em C esta em aed/unidade2/c/ e serve de
 * contraponto para a traducao comentada exigida pela U2 de AED: as duas
 * implementam exatamente a mesma logica, com os mesmos dados e a mesma saida.
 */

#include "fila_fefo.hpp"

#include <iostream>
#include <string>

namespace {

/*
 * DIFERENCA PARA A VERSAO EM C: la esta funcao preenchia campo por campo com
 * copiar_texto, porque atribuir direto a um char[] nao funciona. Com
 * std::string a inicializacao por lista basta.
 */
Bolsa montar(const std::string& codigo, const std::string& grupo,
             const std::string& validade) {
    return Bolsa{codigo, grupo, "CONCENTRADO_HEMACIAS", 450,
                 "2026-08-01", validade, "DISPONIVEL"};
}

void etapa(const std::string& titulo) {
    std::cout << '\n' << std::string(64, '-') << '\n'
              << ' ' << titulo << '\n'
              << std::string(64, '-') << '\n';
}

}  // namespace

int main() {
    std::cout << "\n  ROTA VITAL - AED Unidade 2: FEFO por insercao ordenada\n"
              << "  Versao em C++\n";

    /*
     * DIFERENCA PARA A VERSAO EM C: la a fila era criada com fila_criar, que
     * podia devolver NULL, exigindo um bloco de verificacao antes de seguir.
     * Aqui e um objeto local: o construtor nao falha por falta de memoria e o
     * destrutor roda sozinho no fim do escopo.
     */
    FilaFefo fila;

    etapa("1. Insercao fora de ordem (a fila se mantem ordenada)");
    /* Entram embaralhadas de proposito; cada uma cai na posicao certa. */
    fila.adicionar(montar("B003", "O+", "2026-10-20"));   /* fim    */
    fila.adicionar(montar("B001", "O-", "2026-09-05"));   /* inicio */
    fila.adicionar(montar("B004", "A+", "2026-12-01"));   /* fim    */
    fila.adicionar(montar("B002", "B-", "2026-10-01"));   /* meio   */
    fila.listar();
    std::cout << "  tamanho: " << fila.tamanho() << '\n';

    etapa("2. Empate de validade resolvido pelo codigo de rastreio");
    fila.adicionar(montar("B006", "A-", "2026-10-01"));
    fila.adicionar(montar("B005", "AB+", "2026-10-01"));
    fila.listar();
    std::cout << "  as tres de 2026-10-01 saem na ordem B002, B005, B006\n";

    etapa("3. Proxima a sair (consulta, sem retirar)");
    if (const Bolsa* proxima = fila.consultarProxima()) {
        std::cout << "  " << proxima->codigo_rastreio
                  << ", validade " << proxima->data_validade << '\n';
    }
    std::cout << "  tamanho segue: " << fila.tamanho() << '\n';

    etapa("4. Descarte das vencidas em 2026-10-01");
    /*
     * A B001 venceu em 2026-09-05. A B002, B005 e B006 vencem exatamente na
     * data de referencia: NAO sao descartadas, porque so vencem no dia
     * seguinte.
     *
     * DIFERENCA PARA A VERSAO EM C: la era preciso declarar um vetor com
     * capacidade fixa e passa-lo junto com o tamanho. Aqui o vector volta
     * dimensionado pelo proprio resultado.
     */
    std::vector<Bolsa> descartadas = fila.descartarVencidas("2026-10-01");
    std::cout << "  descartadas: " << descartadas.size() << '\n';
    for (const Bolsa& b : descartadas) {
        std::cout << "    " << b.codigo_rastreio
                  << " (validade " << b.data_validade << ")\n";
    }
    std::cout << "  restantes:\n";
    fila.listar();

    etapa("5. Retirada ate esvaziar");
    /*
     * DIFERENCA PARA A VERSAO EM C: la era preciso declarar a variavel antes,
     * passar o endereco dela e comparar o retorno com RV_OK. Com
     * std::optional o teste e o valor vem juntos na mesma expressao.
     */
    while (auto retirada = fila.retirarProxima()) {
        std::cout << "  sai " << retirada->codigo_rastreio
                  << " (validade " << retirada->data_validade << ")\n";
    }
    std::cout << "  vazia: " << (fila.vazia() ? "sim" : "nao")
              << ", tamanho: " << fila.tamanho() << '\n'
              << "  consultar em fila vazia devolve nullptr: "
              << (fila.consultarProxima() == nullptr ? "sim" : "nao") << '\n';

    etapa("6. Liberacao da memoria");
    /*
     * DIFERENCA PARA A VERSAO EM C: la esta etapa chamava fila_destruir e
     * zerava o ponteiro em seguida. Aqui nao ha nada a fazer: a fila e um
     * objeto local do main, e seu destrutor roda sozinho ao sair do escopo,
     * liberando no por no.
     */
    std::cout << "  o destrutor libera tudo ao sair do escopo (RAII)\n\n";

    return 0;
}
