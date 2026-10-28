package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.Dijkstra;
import com.rotavital.estruturas.FilaPrioridadeFefo;
import com.rotavital.estruturas.Grafo;
import com.rotavital.estruturas.ResultadoRota;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servico de integracao da PI3-58: transforma uma requisicao em bolsa mais
 * rota, ligando as tres pecas ja construidas - o indice de candidatas, o
 * caminho minimo ({@link Dijkstra}) e a selecao FEFO ({@link SelecaoFefo} /
 * {@link FilaPrioridadeFefo}).
 *
 * <p>O fluxo de uma chamada a {@link #alocar}:</p>
 * <ol>
 *   <li>levanta as candidatas da combinacao pedida, unidade a unidade;</li>
 *   <li>roda o Dijkstra a partir da unidade solicitante;</li>
 *   <li>descarta as candidatas em unidades inalcancaveis - bolsa sem caminho
 *       nunca e escolhida, por melhor que seja sua validade;</li>
 *   <li>escolhe por FEFO entre as que sobraram;</li>
 *   <li>devolve a bolsa, o caminho ate ela e o tempo total.</li>
 * </ol>
 *
 * <p>A ordem dos passos importa: o alcance filtra antes do FEFO exatamente
 * como a validade filtra antes do FEFO na {@link SelecaoFefo} - inalcancavel
 * nao e uma opcao pior, e uma opcao invalida.</p>
 *
 * <p>Quando nao ha bolsa para entregar, a resposta carrega um
 * {@link MotivoFalhaAlocacao} em vez de excecao (PI3-57). Argumento nulo, por
 * outro lado, e erro de programa e lanca {@link IllegalArgumentException}.</p>
 *
 * <p>O servico nao muda o status da bolsa escolhida: e uma consulta, e
 * chamadas repetidas com o mesmo estado devolvem o mesmo resultado. Efetivar
 * a alocacao (reservar a bolsa, criar a {@code Alocacao}) e papel da camada de
 * servico JPA, fora deste escopo.</p>
 *
 * <p><b>Thread-safety:</b> depois de construido o servico nao tem estado
 * mutavel proprio e pode ser compartilhado entre threads. A unica ressalva e
 * o status das bolsas, que sao referencias vivas: quem alterar status em
 * paralelo a uma chamada ve a leitura da chamada em curso, como em qualquer
 * consulta sobre dado compartilhado. A malha recebida nao deve ser alterada
 * apos a construcao.</p>
 *
 * <p>A busca e por grupo sanguineo identico, sem tabela de compatibilidade
 * ABO/Rh - mesma decisao ja registrada na {@link SelecaoFefo}.</p>
 *
 * <p><b>Complexidade</b> por chamada, com B bolsas no estoque, V unidades e E
 * arestas: O(B) para levantar e filtrar candidatas, O((V + E) log V) para o
 * Dijkstra (a reconstrucao da rota roda uma segunda travessia, sem alterar a
 * ordem de grandeza) e O(B log B) no pior caso para a fila FEFO. Total
 * O(B log B + (V + E) log V).</p>
 */
public final class ServicoAlocacaoRota {

    private final Dijkstra<String> dijkstra;
    private final Map<String, List<Bolsa>> estoquePorUnidade;

    /**
     * @param malha             grafo da rede, com o id da unidade como vertice
     * @param estoquePorUnidade bolsas de cada unidade, chaveadas pelo mesmo id
     *                          usado na malha. A estrutura e copiada: mudancas
     *                          posteriores no mapa ou nas listas do chamador
     *                          nao afetam o servico. As bolsas em si sao
     *                          referencias vivas, entao mudanca de status vale
     *                          na chamada seguinte. Entradas nulas na lista
     *                          sao descartadas na copia - buraco na lista e
     *                          situacao de dado, nao erro de programa
     */
    public ServicoAlocacaoRota(Grafo<String> malha, Map<String, List<Bolsa>> estoquePorUnidade) {
        if (malha == null) {
            throw new IllegalArgumentException("Malha nao pode ser nula");
        }
        if (estoquePorUnidade == null) {
            throw new IllegalArgumentException("Estoque por unidade nao pode ser nulo");
        }
        this.dijkstra = new Dijkstra<>(malha);
        this.estoquePorUnidade = copiarEstoque(estoquePorUnidade);
    }

    /**
     * Uma chamada, uma resposta: a bolsa a entregar com o caminho ate ela, ou
     * o motivo de nao haver bolsa (PI3-58).
     *
     * <p>Bolsa na propria unidade solicitante e o melhor caso possivel de
     * rota: caminho de um unico vertice e tempo zero.</p>
     *
     * @param unidadeSolicitante id da unidade que pede a bolsa, o mesmo usado
     *                           como vertice da malha
     * @param grupo              grupo sanguineo pedido, casado de forma
     *                           identica
     * @param tipo               tipo de hemocomponente pedido
     * @param dataReferencia     data usada para julgar a validade das bolsas
     * @return sucesso com bolsa, unidade, rota e tempo; ou falha com
     *         {@link MotivoFalhaAlocacao#SEM_ESTOQUE},
     *         {@link MotivoFalhaAlocacao#TODAS_VENCIDAS} ou
     *         {@link MotivoFalhaAlocacao#SEM_CAMINHO}
     * @throws IllegalArgumentException se qualquer argumento for nulo
     */
    public ResultadoAlocacao alocar(String unidadeSolicitante,
                                    GrupoSanguineo grupo,
                                    TipoHemocomponente tipo,
                                    LocalDate dataReferencia) {
        exigirNaoNulo(unidadeSolicitante, "Unidade solicitante");
        exigirNaoNulo(grupo, "Grupo sanguineo");
        exigirNaoNulo(tipo, "Tipo de hemocomponente");
        exigirNaoNulo(dataReferencia, "Data de referencia");

        // Passo 1: candidatas da combinacao pedida, por unidade. Os dois
        // filtros sao separados de proposito: existir bolsa da combinacao e
        // existir bolsa alocavel distinguem SEM_ESTOQUE de TODAS_VENCIDAS.
        boolean existeDaCombinacao = false;
        List<Candidata> alocaveis = new ArrayList<>();

        for (Map.Entry<String, List<Bolsa>> unidade : estoquePorUnidade.entrySet()) {
            for (Bolsa bolsa : unidade.getValue()) {
                if (bolsa.getGrupoSanguineo() != grupo || bolsa.getTipo() != tipo) {
                    continue;
                }
                existeDaCombinacao = true;
                if (SelecaoFefo.alocavel(bolsa, dataReferencia)) {
                    alocaveis.add(new Candidata(unidade.getKey(), bolsa));
                }
            }
        }

        if (!existeDaCombinacao) {
            return ResultadoAlocacao.falha(MotivoFalhaAlocacao.SEM_ESTOQUE);
        }
        if (alocaveis.isEmpty()) {
            return ResultadoAlocacao.falha(MotivoFalhaAlocacao.TODAS_VENCIDAS);
        }

        // Passo 2: Dijkstra a partir da solicitante. Ausencia no mapa e o
        // proprio sinal de inalcancavel; solicitante fora da malha devolve
        // mapa vazio e cai em SEM_CAMINHO, sem caso especial.
        Map<String, Double> distancias = dijkstra.calcularDistancias(unidadeSolicitante);

        // Passo 3 e 4: filtra alcancaveis e escolhe por FEFO. A fila reusa a
        // regra unica de ordenacao (validade, depois codigo de rastreio), a
        // mesma da SelecaoFefo - escrever outro comparador aqui abriria
        // espaco para as duas ordens divergirem.
        FilaPrioridadeFefo fila = new FilaPrioridadeFefo();
        Map<String, String> unidadePorCodigo = new HashMap<>();

        for (Candidata candidata : alocaveis) {
            if (!distancias.containsKey(candidata.unidade())) {
                continue;
            }
            // Codigo de rastreio repetido em mais de uma unidade e erro de
            // dado; a primeira ocorrencia vence, e como o estoque preserva a
            // ordem de insercao a escolha e deterministica.
            if (unidadePorCodigo.putIfAbsent(
                    candidata.bolsa().getCodigoRastreio(), candidata.unidade()) == null) {
                fila.adicionar(candidata.bolsa());
            }
        }

        if (fila.estaVazia()) {
            return ResultadoAlocacao.falha(MotivoFalhaAlocacao.SEM_CAMINHO);
        }

        // Passo 5: bolsa, rota e tempo. A rota existe por construcao - a
        // unidade da bolsa acabou de sair do mapa de distancias.
        Bolsa escolhida = fila.consultarProxima();
        String unidadeDaBolsa = unidadePorCodigo.get(escolhida.getCodigoRastreio());
        ResultadoRota<String> rota = dijkstra.calcularRota(unidadeSolicitante, unidadeDaBolsa);

        return ResultadoAlocacao.alocada(escolhida, unidadeDaBolsa, rota);
    }

    /**
     * Copia defensiva do estoque, preservando a ordem de insercao das
     * unidades - e a ordem que torna deterministico o tratamento de codigo de
     * rastreio duplicado.
     */
    private static Map<String, List<Bolsa>> copiarEstoque(Map<String, List<Bolsa>> original) {
        Map<String, List<Bolsa>> copia = new LinkedHashMap<>();
        for (Map.Entry<String, List<Bolsa>> entrada : original.entrySet()) {
            if (entrada.getKey() == null || entrada.getValue() == null) {
                throw new IllegalArgumentException(
                        "Estoque nao pode ter unidade nula nem lista nula de bolsas");
            }
            List<Bolsa> semBuracos = new ArrayList<>();
            for (Bolsa bolsa : entrada.getValue()) {
                if (bolsa != null) {
                    semBuracos.add(bolsa);
                }
            }
            copia.put(entrada.getKey(), List.copyOf(semBuracos));
        }
        return copia;
    }

    private static void exigirNaoNulo(Object valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException(nome + " nao pode ser nulo(a)");
        }
    }

    /**
     * Par bolsa e unidade onde ela esta, enquanto a selecao esta em curso.
     */
    private record Candidata(String unidade, Bolsa bolsa) {
    }
}
