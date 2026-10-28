/*
 * Rota Vital - AED Unidade 2
 *
 * Duas demonstracoes:
 *
 *   1. Fila FEFO por insercao ordenada (U2/W10): a bolsa que vence primeiro
 *      esta sempre no inicio, sem nenhum passo de ordenacao depois.
 *
 *   2. Roteirizacao por vizinho mais proximo (U2/W12) sobre a malha real das
 *      doze unidades, escolhendo a cada passo o hospital nao visitado mais
 *      proximo da posicao atual.
 *
 *   3. Indice de estoque por hash (U2/W12), com tabela feita a mao e hash
 *      perfeito, e matriz de compatibilidade ABO/Rh.
 *
 * VERSAO EM C++. A versao original em C esta em aed/unidade2/c/ e serve de
 * contraponto para a traducao comentada exigida pela U2 de AED: as duas
 * implementam exatamente a mesma logica, com os mesmos dados e a mesma saida.
 */

#include "fila_fefo.hpp"
#include "indice_estoque.hpp"
#include "matriz_compatibilidade.hpp"
#include "dijkstra.hpp"
#include "grafo.hpp"
#include "vizinho_mais_proximo.hpp"

#include <cmath>
#include <iomanip>
#include <iostream>
#include <string>
#include <vector>

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

/*
 * Monta a malha real das doze unidades da Regiao Metropolitana do Recife.
 *
 * PROCEDENCIA DOS PESOS - LEIA ANTES DE ALTERAR QUALQUER COISA
 *
 * Estes 64 numeros NAO sao calculados aqui: sao copia do que a versao Java
 * produz. Foram extraidos de
 * src/main/java/com/rotavital/rede/CarregadorMalha.java, que os gera a partir
 * das coordenadas reais das unidades em UnidadeRede.java, aplicando as
 * constantes de src/main/java/com/rotavital/rede/PremissasMalha.java:
 *
 *   RAIO_TERRA_KM        = 6371,0   (distancia de Haversine)
 *   FATOR_SINUOSIDADE    = 1,4      (reta para distancia de via)
 *   VELOCIDADE_MEDIA_KMH = 25,0     (distancia para tempo)
 *   VIZINHOS_POR_UNIDADE = 4        (quantas vizinhas cada unidade liga)
 *
 * e arredondando o tempo a uma casa decimal.
 *
 * CONSEQUENCIA: mudar qualquer uma dessas quatro constantes no Java, ou mudar
 * as coordenadas de uma unidade, INVALIDA estes numeros. Eles nao se
 * atualizam sozinhos - esta tabela e uma fotografia, nao um calculo. Portar o
 * calculo geografico nao faz parte desta entrega: o que se quer demonstrar e
 * a estrutura e o roteiro.
 *
 * Para que a desatualizacao nao passe em silencio, a etapa 10 compara a malha
 * construida aqui com os valores de referencia registrados em
 * docs/premissas-malha-rede.md e imprime CONFERE ou DIVERGE em cada linha.
 *
 * Cada unidade se liga as quatro mais proximas, e as arestas sao reciprocas,
 * o que da 64 arestas dirigidas. A malha e esparsa de proposito: e por isso
 * que a roteirizacao mede distancia por caminho minimo e nao por aresta
 * direta.
 */
void montarMalha(Grafo& malha) {
    malha.inserirAresta("HC01", "HC02", 2.9);
    malha.inserirAresta("HC01", "HC03", 2.1);
    malha.inserirAresta("HC01", "HC04", 4.2);
    malha.inserirAresta("HC01", "HC05", 6.0);
    malha.inserirAresta("HC01", "HC06", 18.0);
    malha.inserirAresta("HC01", "HC07", 25.4);
    malha.inserirAresta("HC01", "HC08", 39.5);
    malha.inserirAresta("HC02", "HC01", 2.9);
    malha.inserirAresta("HC02", "HC03", 3.5);
    malha.inserirAresta("HC02", "HC04", 2.2);
    malha.inserirAresta("HC02", "HC05", 3.0);
    malha.inserirAresta("HC02", "HC06", 19.8);
    malha.inserirAresta("HC02", "HC07", 26.0);
    malha.inserirAresta("HC02", "HC08", 41.0);
    malha.inserirAresta("HC03", "HC01", 2.1);
    malha.inserirAresta("HC03", "HC02", 3.5);
    malha.inserirAresta("HC03", "HC04", 5.5);
    malha.inserirAresta("HC03", "HC05", 6.3);
    malha.inserirAresta("HC03", "HC06", 19.8);
    malha.inserirAresta("HC03", "HC07", 23.3);
    malha.inserirAresta("HC03", "HC08", 37.7);
    malha.inserirAresta("HC04", "HC01", 4.2);
    malha.inserirAresta("HC04", "HC02", 2.2);
    malha.inserirAresta("HC04", "HC03", 5.5);
    malha.inserirAresta("HC04", "HC05", 3.1);
    malha.inserirAresta("HC04", "HC06", 18.9);
    malha.inserirAresta("HC04", "HC09", 29.8);
    malha.inserirAresta("HC04", "HC11", 40.1);
    malha.inserirAresta("HC05", "HC01", 6.0);
    malha.inserirAresta("HC05", "HC02", 3.0);
    malha.inserirAresta("HC05", "HC03", 6.3);
    malha.inserirAresta("HC05", "HC04", 3.1);
    malha.inserirAresta("HC05", "HC10", 39.1);
    malha.inserirAresta("HC06", "HC01", 18.0);
    malha.inserirAresta("HC06", "HC02", 19.8);
    malha.inserirAresta("HC06", "HC03", 19.8);
    malha.inserirAresta("HC06", "HC04", 18.9);
    malha.inserirAresta("HC06", "HC09", 21.3);
    malha.inserirAresta("HC06", "HC12", 34.2);
    malha.inserirAresta("HC07", "HC01", 25.4);
    malha.inserirAresta("HC07", "HC02", 26.0);
    malha.inserirAresta("HC07", "HC03", 23.3);
    malha.inserirAresta("HC07", "HC08", 18.1);
    malha.inserirAresta("HC08", "HC01", 39.5);
    malha.inserirAresta("HC08", "HC02", 41.0);
    malha.inserirAresta("HC08", "HC03", 37.7);
    malha.inserirAresta("HC08", "HC07", 18.1);
    malha.inserirAresta("HC09", "HC04", 29.8);
    malha.inserirAresta("HC09", "HC06", 21.3);
    malha.inserirAresta("HC09", "HC10", 30.4);
    malha.inserirAresta("HC09", "HC11", 28.5);
    malha.inserirAresta("HC09", "HC12", 16.1);
    malha.inserirAresta("HC10", "HC05", 39.1);
    malha.inserirAresta("HC10", "HC09", 30.4);
    malha.inserirAresta("HC10", "HC11", 3.4);
    malha.inserirAresta("HC10", "HC12", 38.4);
    malha.inserirAresta("HC11", "HC04", 40.1);
    malha.inserirAresta("HC11", "HC09", 28.5);
    malha.inserirAresta("HC11", "HC10", 3.4);
    malha.inserirAresta("HC11", "HC12", 35.4);
    malha.inserirAresta("HC12", "HC06", 34.2);
    malha.inserirAresta("HC12", "HC09", 16.1);
    malha.inserirAresta("HC12", "HC10", 38.4);
    malha.inserirAresta("HC12", "HC11", 35.4);
}

/* Nome legivel de cada unidade, so para a impressao. */
const char* nomeDa(const std::string& id) {
    if (id == "HC01") return "Hemocentro Recife (HEMOPE)";
    if (id == "HC02") return "GSH Banco de Sangue Hemato";
    if (id == "HC03") return "IHENE - Instituto de Hematologia do Nordeste";
    if (id == "HC04") return "Real Hospital Portugues de Beneficencia";
    if (id == "HC05") return "IMIP";
    if (id == "HC06") return "Hospital das Clinicas UFPE";
    if (id == "HC07") return "Hospital do Tricentenario";
    if (id == "HC08") return "Hemolab Laboratorio";
    if (id == "HC09") return "Hospital Nossa Senhora de Lourdes";
    if (id == "HC10") return "Hospital e Policlinica Jaboatao Prazeres";
    if (id == "HC11") return "Hospital Guararapes (Memorial)";
    if (id == "HC12") return "Hospital Memorial Jaboatao";
    return "(desconhecida)";
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
    std::cout << "  o destrutor libera tudo ao sair do escopo (RAII)\n";

    /* ------------------------------------------------------------------ */
    /* Segunda demonstracao: roteirizacao por vizinho mais proximo (W12).  */
    /* ------------------------------------------------------------------ */

    std::cout << "\n\n  ROTA VITAL - AED Unidade 2: roteirizacao por vizinho mais proximo\n"
              << "  Versao em C++\n";

    /*
     * DIFERENCA PARA A VERSAO EM JAVA: la a malha e construida pelo
     * CarregadorMalha, que le as coordenadas das unidades e calcula os tempos.
     * Aqui as arestas entram prontas, com os mesmos pesos, porque o calculo
     * geografico nao faz parte deste port.
     */
    Grafo malha;
    montarMalha(malha);

    etapa("7. A malha real das doze unidades");
    std::cout << "  vertices: " << malha.quantidadeVertices() << '\n';
    int arestas = 0;
    for (const std::string& v : malha.listarVertices()) {
        arestas += static_cast<int>(malha.vizinhosComPeso(v).size());
    }
    std::cout << "  arestas dirigidas: " << arestas << '\n'
              << "  dirigido: " << (malha.direcionado() ? "sim" : "nao") << '\n'
              << "  aresta HC01 -> HC04: ";
    if (auto peso = malha.pesoAresta("HC01", "HC04")) {
        std::cout << std::fixed << std::setprecision(1) << *peso << " min\n";
    } else {
        std::cout << "(inexistente)\n";
    }

    etapa("8. Roteiro do HEMOPE para os oito hospitais");
    /*
     * Os pontos de entrega sao as unidades do tipo AGENCIA_TRANSFUSIONAL. A
     * ordem desta lista define o desempate quando dois hospitais estao a mesma
     * distancia.
     */
    const std::vector<std::string> hospitais = {
        "HC04", "HC05", "HC06", "HC07", "HC09", "HC10", "HC11", "HC12"
    };

    std::cout << "  origem: HC01 - " << nomeDa("HC01") << '\n'
              << "  pontos pedidos: ";
    for (const std::string& h : hospitais) std::cout << h << ' ';
    std::cout << '\n';

    VizinhoMaisProximo roteirizador(malha);
    const ResultadoRoteiro roteiro = roteirizador.calcularRoteiro("HC01", hospitais);

    std::cout << "\n  passo a passo, com o custo acumulado:\n";
    double acumulado = 0.0;
    std::string de = "HC01";
    for (std::size_t i = 0; i < roteiro.trechos.size(); ++i) {
        const TrechoRoteiro& t = roteiro.trechos[i];
        acumulado += t.custoMinutos;
        std::cout << "    " << (i + 1) << ". " << de << " -> " << t.destino
                  << std::fixed << std::setprecision(1)
                  << "  trecho " << t.custoMinutos << " min"
                  << "  acumulado " << acumulado << " min"
                  << "   (" << nomeDa(t.destino) << ")\n";
        de = t.destino;
    }

    std::cout << "\n  ordem de visita: ";
    for (const std::string& p : roteiro.ordemDeVisita) std::cout << p << ' ';
    std::cout << "\n  custo total: " << std::fixed << std::setprecision(1)
              << roteiro.custoTotalMinutos << " min\n"
              << "  visitados: " << roteiro.quantidadeVisitada()
              << " de " << hospitais.size() << '\n'
              << "  alcancou todos: " << (roteiro.alcancouTodos() ? "sim" : "nao") << '\n';

    etapa("9. Ponto inalcancavel nao interrompe o roteiro");
    /*
     * Uma unidade inserida sem nenhuma aresta forma um componente isolado:
     * existe no grafo, mas ninguem chega nela.
     *
     * DIFERENCA PARA A VERSAO EM JAVA: identica em comportamento. Nos dois, o
     * ponto vai para a lista de nao atendidos e o roteiro parcial e devolvido,
     * sem excecao.
     */
    malha.inserirVertice("HC99");
    const ResultadoRoteiro comIsolado =
            roteirizador.calcularRoteiro("HC01", {"HC04", "HC99", "HC05"});

    std::cout << "  pedidos: HC04 HC99 HC05\n  visitados: ";
    for (const std::string& p : comIsolado.ordemDeVisita) std::cout << p << ' ';
    std::cout << "\n  nao atendidos: ";
    for (const std::string& p : comIsolado.pontosInalcancaveis) std::cout << p << ' ';
    std::cout << "\n  alcancou todos: " << (comIsolado.alcancouTodos() ? "sim" : "nao")
              << "\n  custo do roteiro parcial: " << std::fixed << std::setprecision(1)
              << comIsolado.custoTotalMinutos << " min\n";

    etapa("10. Conferencia com docs/premissas-malha-rede.md");
    /*
     * Os pesos da tabela acima sao uma fotografia do que o Java calcula. Se
     * alguem mudar uma premissa no Java e esquecer de regerar a tabela, esta
     * etapa passa a imprimir DIVERGE - em vez de a demo ficar errada em
     * silencio.
     *
     * Os valores de referencia estao em docs/premissas-malha-rede.md, secao
     * "Resultado" e "Um detalhe que mostra o Dijkstra funcionando".
     */
    Dijkstra dijkstra(malha);
    int divergencias = 0;

    auto conferir = [&divergencias](const std::string& descricao,
                                    double esperado, double obtido) {
        /* Tolerancia de meio decimo: os pesos tem uma casa decimal. */
        const bool confere = std::fabs(esperado - obtido) < 0.05;
        if (!confere) ++divergencias;
        std::cout << "    " << (confere ? "CONFERE" : "DIVERGE") << "  "
                  << std::left << std::setw(46) << descricao << std::right
                  << " esperado " << std::fixed << std::setprecision(1) << esperado
                  << ", obtido " << obtido << '\n';
    };

    auto conferirCaminho = [&divergencias](const std::string& descricao,
                                           const std::string& esperado,
                                           const std::vector<std::string>& obtido) {
        std::string juntado;
        for (std::size_t i = 0; i < obtido.size(); ++i) {
            if (i > 0) juntado += " -> ";
            juntado += obtido[i];
        }
        const bool confere = (juntado == esperado);
        if (!confere) ++divergencias;
        std::cout << "    " << (confere ? "CONFERE" : "DIVERGE") << "  "
                  << std::left << std::setw(46) << descricao << std::right
                  << " esperado " << esperado << ", obtido " << juntado << '\n';
    };

    /* A etapa 9 inseriu HC99 isolado; ele nao conta como aresta. */
    int arestasConferencia = 0;
    for (const std::string& v : malha.listarVertices()) {
        arestasConferencia += static_cast<int>(malha.vizinhosComPeso(v).size());
    }
    conferir("total de arestas dirigidas", 64.0, arestasConferencia);

    const ResultadoRota ate05 = dijkstra.calcularRota("HC01", "HC05");
    conferir("HC01 -> HC05, caminho minimo", 5.9, ate05.custoTotal);
    conferirCaminho("HC01 -> HC05, passando por HC02", "HC01 -> HC02 -> HC05",
                    ate05.caminho);
    if (auto direta = malha.pesoAresta("HC01", "HC05")) {
        conferir("HC01 -> HC05, aresta direta (maior que o caminho)", 6.0, *direta);
    } else {
        ++divergencias;
        std::cout << "    DIVERGE  aresta direta HC01 -> HC05 nao existe\n";
    }

    const ResultadoRota ate12 = dijkstra.calcularRota("HC01", "HC12");
    conferir("HC01 -> HC12, caminho minimo", 50.1, ate12.custoTotal);
    conferirCaminho("HC01 -> HC12, caminho", "HC01 -> HC04 -> HC09 -> HC12",
                    ate12.caminho);

    const ResultadoRota ate11 = dijkstra.calcularRota("HC01", "HC11");
    conferir("HC01 -> HC11, caminho minimo", 44.3, ate11.custoTotal);

    std::cout << "\n  " << (divergencias == 0
            ? "tudo confere com o documento de premissas"
            : "ATENCAO: a malha divergiu do documento em "
              + std::to_string(divergencias) + " ponto(s)")
              << '\n';

    etapa("11. Indice de estoque por hash: um balde por combinacao");
    /*
     * As 32 combinacoes possiveis - 8 grupos x 4 tipos - sao indexadas, uma
     * bolsa em cada, e o balde de todas e impresso. Se dois baldes se
     * repetissem, haveria colisao; a conferencia no fim acusa.
     */
    IndiceEstoque indice;

    std::cout << "  capacidade da tabela: " << IndiceEstoque::CAPACIDADE
              << " baldes, para " << (TOTAL_GRUPOS * TOTAL_TIPOS)
              << " chaves possiveis\n\n";

    bool baldeUsado[IndiceEstoque::CAPACIDADE] = {false};
    int baldesRepetidos = 0;

    std::cout << "    chave                                        balde\n";
    for (int g = 0; g < TOTAL_GRUPOS; ++g) {
        for (int t = 0; t < TOTAL_TIPOS; ++t) {
            const GrupoSanguineo grupo = static_cast<GrupoSanguineo>(g);
            const TipoHemocomponente tipo = static_cast<TipoHemocomponente>(t);

            const std::string codigo = std::string("BS") + std::to_string(g) + std::to_string(t);
            indice.adicionar(Bolsa{codigo, rotuloGrupo(grupo), nomeTipo(tipo), 450,
                                   "2026-08-01", "2026-10-01", "DISPONIVEL"},
                             grupo, tipo);

            const int balde = IndiceEstoque::hashDaChave(grupo, tipo);
            if (baldeUsado[balde]) ++baldesRepetidos;
            baldeUsado[balde] = true;

            std::cout << "    " << std::left << std::setw(8) << rotuloGrupo(grupo)
                      << std::setw(28) << nomeTipo(tipo) << std::right
                      << std::setw(10) << balde << '\n';
        }
    }

    std::cout << "\n  bolsas indexadas: " << indice.tamanho() << '\n';

    /* Busca: cada combinacao devolve so a sua bolsa. */
    const std::vector<Bolsa> soONeg = indice.buscar(GrupoSanguineo::O_NEG,
                                                    TipoHemocomponente::CONCENTRADO_HEMACIAS);
    std::cout << "  busca O- / CONCENTRADO_HEMACIAS devolve " << soONeg.size()
              << " bolsa(s): " << (soONeg.empty() ? "-" : soONeg[0].codigo_rastreio) << '\n';

    /* Agrupamento: varias bolsas na mesma chave nao sao colisao. */
    indice.adicionar(Bolsa{"BS-EXTRA", "O-", "CONCENTRADO_HEMACIAS", 450,
                           "2026-08-01", "2026-11-01", "DISPONIVEL"},
                     GrupoSanguineo::O_NEG, TipoHemocomponente::CONCENTRADO_HEMACIAS);
    const int baldeONeg = IndiceEstoque::hashDaChave(GrupoSanguineo::O_NEG,
                                                     TipoHemocomponente::CONCENTRADO_HEMACIAS);
    std::cout << "  apos somar outra bolsa O-, o balde " << baldeONeg << " tem "
              << indice.ocupacaoDoBalde(baldeONeg)
              << " bolsa(s) - agrupamento, nao colisao\n";

    /* Remocao. */
    const bool removeu = indice.remover("BS-EXTRA", GrupoSanguineo::O_NEG,
                                        TipoHemocomponente::CONCENTRADO_HEMACIAS);
    std::cout << "  remover BS-EXTRA: " << (removeu ? "ok" : "falhou")
              << ", total agora " << indice.tamanho() << '\n';

    std::cout << "\n    " << (baldesRepetidos == 0 ? "CONFERE" : "DIVERGE")
              << "  as 32 chaves ocuparam 32 baldes distintos"
              << " (repetidos: " << baldesRepetidos << ")\n";

    etapa("12. Matriz de compatibilidade ABO/Rh");
    /*
     * Percorre os 8 grupos nas duas tabelas, imprime quem pode doar para quem
     * e compara com a referencia extraida do MatrizCompatibilidade.java.
     *
     * A referencia e um vetor de bits por linha: o bit i ligado significa que
     * o grupo de ordinal i pode doar. Foi gerada executando a versao Java, e
     * nao reescrita a mao - se as duas divergirem, uma das implementacoes
     * mudou.
     */
    struct LinhaReferencia {
        TipoHemocomponente tipo;
        GrupoSanguineo     receptor;
        unsigned char      doadores;
    };

    static const LinhaReferencia REFERENCIA_JAVA[] = {
    {TipoHemocomponente::CONCENTRADO_HEMACIAS, GrupoSanguineo::A_POS, 0xC3},
    {TipoHemocomponente::CONCENTRADO_HEMACIAS, GrupoSanguineo::A_NEG, 0x82},
    {TipoHemocomponente::CONCENTRADO_HEMACIAS, GrupoSanguineo::B_POS, 0xCC},
    {TipoHemocomponente::CONCENTRADO_HEMACIAS, GrupoSanguineo::B_NEG, 0x88},
    {TipoHemocomponente::CONCENTRADO_HEMACIAS, GrupoSanguineo::AB_POS, 0xFF},
    {TipoHemocomponente::CONCENTRADO_HEMACIAS, GrupoSanguineo::AB_NEG, 0xAA},
    {TipoHemocomponente::CONCENTRADO_HEMACIAS, GrupoSanguineo::O_POS, 0xC0},
    {TipoHemocomponente::CONCENTRADO_HEMACIAS, GrupoSanguineo::O_NEG, 0x80},
    {TipoHemocomponente::PLASMA_FRESCO_CONGELADO, GrupoSanguineo::A_POS, 0x33},
    {TipoHemocomponente::PLASMA_FRESCO_CONGELADO, GrupoSanguineo::A_NEG, 0x33},
    {TipoHemocomponente::PLASMA_FRESCO_CONGELADO, GrupoSanguineo::B_POS, 0x3C},
    {TipoHemocomponente::PLASMA_FRESCO_CONGELADO, GrupoSanguineo::B_NEG, 0x3C},
    {TipoHemocomponente::PLASMA_FRESCO_CONGELADO, GrupoSanguineo::AB_POS, 0x30},
    {TipoHemocomponente::PLASMA_FRESCO_CONGELADO, GrupoSanguineo::AB_NEG, 0x30},
    {TipoHemocomponente::PLASMA_FRESCO_CONGELADO, GrupoSanguineo::O_POS, 0xFF},
    {TipoHemocomponente::PLASMA_FRESCO_CONGELADO, GrupoSanguineo::O_NEG, 0xFF},
    };

    int divergenciasMatriz = 0;

    for (const LinhaReferencia& linha : REFERENCIA_JAVA) {
        const unsigned char obtido =
                MatrizCompatibilidade::doadoresEmBits(linha.receptor, linha.tipo);
        const bool confere = (obtido == linha.doadores);
        if (!confere) ++divergenciasMatriz;

        /* Lista legivel de quem pode doar. */
        std::string lista;
        for (const GrupoSanguineo d :
                 MatrizCompatibilidade::doadoresPara(linha.receptor, linha.tipo)) {
            if (!lista.empty()) lista += " ";
            lista += rotuloGrupo(d);
        }

        std::cout << "    " << (confere ? "CONFERE" : "DIVERGE") << "  "
                  << std::left << std::setw(24) << nomeTipo(linha.tipo)
                  << " recebe " << std::setw(4) << rotuloGrupo(linha.receptor)
                  << " de: " << std::setw(26) << lista << std::right;
        if (!confere) {
            std::cout << "  esperado 0x" << std::hex << static_cast<int>(linha.doadores)
                      << ", obtido 0x" << static_cast<int>(obtido) << std::dec;
        }
        std::cout << '\n';
    }

    /* A nao simetria, que e o erro classico ao modelar compatibilidade. */
    std::cout << "\n  a relacao nao e simetrica:\n";
    const bool oNegDoaParaAPos = MatrizCompatibilidade::compativel(
            GrupoSanguineo::O_NEG, GrupoSanguineo::A_POS,
            TipoHemocomponente::CONCENTRADO_HEMACIAS);
    const bool aPosDoaParaONeg = MatrizCompatibilidade::compativel(
            GrupoSanguineo::A_POS, GrupoSanguineo::O_NEG,
            TipoHemocomponente::CONCENTRADO_HEMACIAS);
    std::cout << "    O- doa hemacias para A+: " << (oNegDoaParaAPos ? "sim" : "nao") << '\n'
              << "    A+ doa hemacias para O-: " << (aPosDoaParaONeg ? "sim" : "nao") << '\n';
    const bool naoSimetrica = oNegDoaParaAPos && !aPosDoaParaONeg;
    if (!naoSimetrica) ++divergenciasMatriz;
    std::cout << "    " << (naoSimetrica ? "CONFERE" : "DIVERGE")
              << "  inverter os argumentos muda a resposta\n";

    /* A inversao do plasma, que a intuicao das hemacias contradiz. */
    const bool oNegDoaPlasmaParaAPos = MatrizCompatibilidade::compativel(
            GrupoSanguineo::O_NEG, GrupoSanguineo::A_POS,
            TipoHemocomponente::PLASMA_FRESCO_CONGELADO);
    std::cout << "\n  o plasma inverte a regra:\n"
              << "    O- doa plasma para A+: " << (oNegDoaPlasmaParaAPos ? "sim" : "nao")
              << " (em hemacias doaria)\n";
    if (oNegDoaPlasmaParaAPos) ++divergenciasMatriz;
    std::cout << "    " << (!oNegDoaPlasmaParaAPos ? "CONFERE" : "DIVERGE")
              << "  plasma de O- nao serve para receptor A+\n";

    /* Argumento fora da faixa nao lanca, devolve vazio. */
    const auto invalidos = MatrizCompatibilidade::doadoresPara(
            static_cast<GrupoSanguineo>(99), TipoHemocomponente::CONCENTRADO_HEMACIAS);
    if (!invalidos.empty()) ++divergenciasMatriz;
    std::cout << "\n    " << (invalidos.empty() ? "CONFERE" : "DIVERGE")
              << "  grupo invalido devolve conjunto vazio, sem excecao\n";

    std::cout << "\n  " << (divergenciasMatriz == 0 && baldesRepetidos == 0
            ? "tudo confere com a versao Java"
            : "ATENCAO: houve divergencia com a versao Java")
              << '\n';

    etapa("13. Liberacao da memoria do grafo e do indice");
    /*
     * DIFERENCA PARA A VERSAO EM JAVA: la nao ha nada a fazer, o coletor de
     * lixo recolhe os mapas. Aqui os destrutores percorrem as cadeias
     * liberando no por no, e rodam sozinhos ao sair do escopo.
     */
    std::cout << "  o destrutor do Grafo libera vertices e arestas (RAII)\n"
              << "  o destrutor do IndiceEstoque libera os 32 baldes e o vetor\n\n";

    return 0;
}
