/*
 * Rota Vital - AED Unidade 1
 *
 * Demonstracao das tres estruturas basicas construidas a mao, com alocacao
 * manual de memoria: lista encadeada de estoque, fila FIFO de requisicoes e
 * pilha LIFO de historico.
 *
 * Os dados sao sinteticos e seguem o dominio da aplicacao Java do projeto
 * (Bolsa, Requisicao e as operacoes de estoque).
 *
 * VERSAO EM C++. A versao original em C esta em aed/unidade1/c/ e serve de
 * contraponto para a traducao comentada exigida pela U2 de AED: as duas
 * implementam exatamente a mesma logica, com os mesmos dados e a mesma
 * saida.
 */

#include "dominio.hpp"
#include "fila_requisicoes.hpp"
#include "lista_estoque.hpp"
#include "pilha_historico.hpp"

#include <iostream>
#include <string>

namespace {

void etapa(int numero, const std::string& titulo) {
    std::cout << '\n'
              << std::string(80, '=') << '\n'
              << " ETAPA " << numero << " - " << titulo << '\n'
              << std::string(80, '=') << '\n';
}

void subtitulo(const std::string& texto) {
    std::cout << "\n-- " << texto << '\n';
}

/*
 * DIFERENCA PARA A VERSAO EM C: la existiam montar_bolsa, montar_requisicao e
 * montar_operacao, que preenchiam campo por campo com copiar_texto porque
 * atribuir direto a um char[] nao funciona. Com std::string a inicializacao
 * por lista basta, e as tres funcoes deixaram de ser necessarias.
 */

void mostrar_busca(ListaEstoque& estoque, const std::string& codigo) {
    const Bolsa* encontrada = estoque.buscar(codigo);
    if (encontrada == nullptr) {
        std::cout << "  buscar(\"" << codigo << "\") -> NAO ENCONTRADA\n";
    } else {
        std::cout << "  buscar(\"" << codigo << "\") -> "
                  << encontrada->codigo_rastreio << ' '
                  << encontrada->grupo_sanguineo << ", "
                  << encontrada->volume_ml << " ml, validade "
                  << encontrada->data_validade << '\n';
    }
}

void remover_e_mostrar(ListaEstoque& estoque, const std::string& codigo,
                       const std::string& qual_caso) {
    std::cout << "\n  removendo " << codigo << " (" << qual_caso << ")\n";
    if (estoque.remover(codigo) == Resultado::Ok) {
        std::cout << "  removida. tamanho agora: " << estoque.tamanho() << '\n';
    } else {
        std::cout << "  falhou: codigo nao encontrado\n";
    }
    estoque.listar();
}

}  // namespace

int main() {
    std::cout << "\n  ROTA VITAL - AED Unidade 1\n"
              << "  Lista encadeada, fila FIFO e pilha LIFO com alocacao manual\n"
              << "  Versao em C++\n";

    /* ------------------------------------------------------------------ */
    etapa(1, "Criacao das tres estruturas");

    /*
     * DIFERENCA PARA A VERSAO EM C: la as tres eram criadas com lista_criar,
     * fila_criar e pilha_criar, cada uma podendo devolver NULL, o que exigia
     * um bloco de verificacao e liberacao antes de seguir. Aqui sao objetos
     * locais: o construtor nao falha por falta de memoria e o destrutor roda
     * sozinho no fim do escopo, mesmo se uma excecao interromper o caminho.
     */
    ListaEstoque    estoque;
    FilaRequisicoes fila;
    PilhaHistorico  historico;

    std::cout << "  estoque   criado - vazio: " << (estoque.vazia() ? "sim" : "nao")
              << ", tamanho: " << estoque.tamanho() << '\n'
              << "  fila      criada - vazia: " << (fila.vazia() ? "sim" : "nao")
              << ", tamanho: " << fila.tamanho() << '\n'
              << "  historico criado - vazio: " << (historico.vazia() ? "sim" : "nao")
              << ", tamanho: " << historico.tamanho() << '\n';

    /* ------------------------------------------------------------------ */
    etapa(2, "Estoque: insercao, listagem e busca");

    estoque.inserir({"BS0001", "O-",  "CONCENTRADO_HEMACIAS",    450,
                     "2026-09-20", "2026-11-01", "DISPONIVEL"});
    estoque.inserir({"BS0002", "A+",  "CONCENTRADO_PLAQUETAS",   300,
                     "2026-10-03", "2026-10-08", "DISPONIVEL"});
    estoque.inserir({"BS0003", "O+",  "CONCENTRADO_HEMACIAS",    450,
                     "2026-09-28", "2026-11-09", "DISPONIVEL"});
    estoque.inserir({"BS0004", "AB-", "PLASMA_FRESCO_CONGELADO", 600,
                     "2026-08-15", "2027-08-15", "DISPONIVEL"});
    estoque.inserir({"BS0005", "B+",  "CRIOPRECIPITADO",         250,
                     "2026-09-01", "2027-09-01", "RESERVADA"});

    subtitulo("estoque apos 5 insercoes no fim da lista");
    estoque.listar();
    std::cout << "  tamanho: " << estoque.tamanho() << '\n';

    subtitulo("buscas");
    mostrar_busca(estoque, "BS0003");   /* existe */
    mostrar_busca(estoque, "BS9999");   /* nao existe */

    /* ------------------------------------------------------------------ */
    etapa(3, "Estoque: remocao nos tres casos de ponteiro");

    /* Os tres casos que a remocao em lista encadeada precisa tratar. */
    remover_e_mostrar(estoque, "BS0001", "PRIMEIRO no - move o ponteiro primeiro");
    remover_e_mostrar(estoque, "BS0003", "no do MEIO - religa o anterior ao proximo");
    remover_e_mostrar(estoque, "BS0005", "ULTIMO no - recua o ponteiro ultimo");

    subtitulo("tentativa de remover codigo inexistente");
    remover_e_mostrar(estoque, "BS9999", "nao esta na lista");

    /* ------------------------------------------------------------------ */
    etapa(4, "Fila de requisicoes (FIFO)");

    fila.enfileirar({"RQ001", "Real Hospital Portugues", "EMERGENCIA",
                     "O-", "CONCENTRADO_HEMACIAS", 4, "2026-10-07 08:15"});
    fila.enfileirar({"RQ002", "IMIP", "URGENTE",
                     "A+", "CONCENTRADO_PLAQUETAS", 2, "2026-10-07 08:40"});
    fila.enfileirar({"RQ003", "Hospital das Clinicas UFPE", "ROTINA",
                     "O+", "CONCENTRADO_HEMACIAS", 6, "2026-10-07 09:05"});
    fila.enfileirar({"RQ004", "Hospital do Tricentenario", "URGENTE",
                     "AB-", "PLASMA_FRESCO_CONGELADO", 1, "2026-10-07 09:30"});

    subtitulo("fila apos 4 chegadas");
    fila.listar();
    std::cout << "  tamanho: " << fila.tamanho() << '\n';

    subtitulo("quem esta na frente (consulta, sem retirar)");
    if (const Requisicao* proxima = fila.frente()) {
        std::cout << "  " << proxima->id << " - " << proxima->hospital
                  << " (" << proxima->prioridade << ")\n";
    }
    std::cout << "  tamanho segue: " << fila.tamanho() << '\n';

    subtitulo("atendendo 2 requisicoes (desenfileirar)");
    for (int i = 0; i < 2; i++) {
        /*
         * DIFERENCA PARA A VERSAO EM C: la era preciso declarar a variavel
         * antes, passar o endereco dela e conferir o retorno RV_OK para saber
         * se foi preenchida. Com std::optional o teste e o valor vem juntos.
         */
        if (auto atendida = fila.desenfileirar()) {
            std::cout << "  atendida: " << atendida->id << " - "
                      << atendida->hospital << " (" << atendida->prioridade
                      << "), " << atendida->quantidade << " bolsa(s) de "
                      << atendida->componente << '\n';
        }
    }

    subtitulo("fila restante");
    fila.listar();
    std::cout << "  tamanho: " << fila.tamanho() << '\n';

    /* ------------------------------------------------------------------ */
    etapa(5, "Pilha de historico (LIFO)");

    historico.empilhar({"OP001", "ENTRADA_BOLSA",
                        "BS0001 recebida do HEMOPE", "2026-10-07 07:50"});
    historico.empilhar({"OP002", "ENTRADA_BOLSA",
                        "BS0002 recebida do GSH Hemato", "2026-10-07 07:55"});
    historico.empilhar({"OP003", "RESERVA_BOLSA",
                        "BS0005 reservada para RQ002", "2026-10-07 08:42"});
    historico.empilhar({"OP004", "SAIDA_BOLSA",
                        "BS0001 despachada para RQ001", "2026-10-07 08:20"});
    historico.empilhar({"OP005", "DESCARTE_BOLSA",
                        "BS0003 descartada por vencimento", "2026-10-07 09:10"});

    subtitulo("historico apos 5 operacoes (topo = mais recente)");
    historico.listar();
    std::cout << "  tamanho: " << historico.tamanho() << '\n';

    subtitulo("operacao no topo (consulta, sem retirar)");
    if (const Operacao* ultima = historico.topo()) {
        std::cout << "  " << ultima->id << " - " << ultima->operacao
                  << ": " << ultima->descricao << '\n';
    }
    std::cout << "  tamanho segue: " << historico.tamanho() << '\n';

    subtitulo("desfazendo as 2 ultimas operacoes (desempilhar)");
    for (int i = 0; i < 2; i++) {
        if (auto desfeita = historico.desempilhar()) {
            std::cout << "  desfeita: " << desfeita->id << " - "
                      << desfeita->operacao << " (" << desfeita->data_hora
                      << ")\n";
        }
    }

    subtitulo("historico restante");
    historico.listar();
    std::cout << "  tamanho: " << historico.tamanho() << '\n';

    /* ------------------------------------------------------------------ */
    etapa(6, "Liberacao da memoria");

    std::cout << "  antes de sair do escopo: estoque=" << estoque.tamanho()
              << ", fila=" << fila.tamanho()
              << ", historico=" << historico.tamanho() << " nos\n";

    /*
     * DIFERENCA PARA A VERSAO EM C: la esta etapa chamava lista_destruir,
     * fila_destruir e pilha_destruir, e em seguida zerava os tres ponteiros
     * para evitar uso acidental de memoria liberada. Aqui nao ha nada a
     * fazer: os tres objetos sao locais do main, e seus destrutores rodam
     * sozinhos ao sair do escopo, liberando no por no.
     *
     * Esse e o ponto em que C++ se aproxima do Java: a liberacao deixa de ser
     * responsabilidade de quem usa. A diferenca e que aqui ela acontece num
     * momento previsivel, o fim do escopo, enquanto em Java depende do
     * coletor de lixo.
     */
    std::cout << "  os destrutores liberam tudo ao sair do escopo (RAII)\n"
              << "\n  todas as alocacoes serao liberadas: nenhum vazamento\n\n";

    return 0;
}
