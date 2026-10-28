package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.Grafo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de propriedades da alocacao integrada: centenas de cenarios gerados
 * com semente fixa, cada um conferido contra um oraculo de forca bruta que
 * nao compartilha nenhuma linha de codigo com a implementacao.
 *
 * <p>Os testes de exemplo (PI3-59) provam casos escolhidos a mao; este prova
 * o contrato em malhas e estoques que ninguem desenhou. O oraculo recalcula
 * tudo do jeito mais burro possivel - caminho minimo por Floyd-Warshall,
 * escolha da bolsa por varredura completa - e o resultado do servico tem de
 * coincidir. Se as duas implementacoes concordam em todos os cenarios, um
 * erro precisaria existir identico nas duas para passar.</p>
 *
 * <p>As sementes sao fixas, entao a execucao e deterministica: nao ha
 * flakiness, e qualquer falha imprime a semente do cenario para reproducao
 * imediata.</p>
 *
 * <p>Propriedades verificadas em cada cenario:</p>
 * <ul>
 *   <li>o desfecho (sucesso ou motivo) e exatamente o que o oraculo preve;</li>
 *   <li>a bolsa escolhida e a minima por (validade, codigo) entre as
 *       alocaveis alcancaveis;</li>
 *   <li>o tempo devolvido e a distancia minima do Floyd-Warshall;</li>
 *   <li>o caminho comeca na solicitante, termina na unidade da bolsa, so usa
 *       arestas que existem e a soma dos pesos fecha com o tempo.</li>
 * </ul>
 */
class PropriedadesAlocacaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 15);
    private static final long SEMENTE_BASE = 20260915L;
    private static final int CENARIOS = 300;
    private static final double SEM_ROTA = Double.POSITIVE_INFINITY;
    private static final double TOLERANCIA = 1e-6;

    @Test
    void servicoCoincideComOraculoDeForcaBrutaEmCenariosAleatorios() {
        int sucessos = 0;
        int semEstoque = 0;
        int todasVencidas = 0;
        int semCaminho = 0;

        for (int i = 0; i < CENARIOS; i++) {
            long semente = SEMENTE_BASE + i;
            Cenario cenario = gerarCenario(new Random(semente));

            ResultadoAlocacao resultado = new ServicoAlocacaoRota(cenario.malha(), cenario.estoque())
                    .alocar(cenario.solicitante(), GrupoSanguineo.O_NEG,
                            TipoHemocomponente.CONCENTRADO_HEMACIAS, HOJE);

            conferirContraOraculo(cenario, resultado, semente);

            if (resultado.sucesso()) {
                sucessos++;
            } else {
                switch (resultado.motivo()) {
                    case SEM_ESTOQUE -> semEstoque++;
                    case TODAS_VENCIDAS -> todasVencidas++;
                    case SEM_CAMINHO -> semCaminho++;
                }
            }
        }

        // Os quatro desfechos precisam ter aparecido: um gerador que nunca
        // produz um deles nao esta exercitando o contrato inteiro.
        assertTrue(sucessos > 0, "gerador nao produziu nenhum sucesso");
        assertTrue(semEstoque > 0, "gerador nao produziu nenhum SEM_ESTOQUE");
        assertTrue(todasVencidas > 0, "gerador nao produziu nenhum TODAS_VENCIDAS");
        assertTrue(semCaminho > 0, "gerador nao produziu nenhum SEM_CAMINHO");
    }

    // ------------------------------------------------------------------
    // Geracao do cenario
    // ------------------------------------------------------------------

    /**
     * Malha dirigida de 2 a 8 unidades com arestas sorteadas, estoque de ate
     * 14 bolsas espalhadas (metade de outro grupo, validades em torno de
     * hoje, algumas reservadas) e uma solicitante sorteada.
     */
    private static Cenario gerarCenario(Random sorteio) {
        int quantasUnidades = 2 + sorteio.nextInt(7);
        List<String> unidades = new ArrayList<>();
        for (int u = 0; u < quantasUnidades; u++) {
            unidades.add("U" + u);
        }

        Grafo<String> malha = new Grafo<>();
        for (String unidade : unidades) {
            malha.inserirVertice(unidade);
        }
        for (String origem : unidades) {
            for (String destino : unidades) {
                if (!origem.equals(destino) && sorteio.nextDouble() < 0.3) {
                    malha.inserirAresta(origem, destino, 1 + sorteio.nextInt(60));
                }
            }
        }

        Map<String, List<Bolsa>> estoque = new LinkedHashMap<>();
        int quantasBolsas = sorteio.nextInt(15);
        for (int b = 0; b < quantasBolsas; b++) {
            GrupoSanguineo grupo = sorteio.nextBoolean()
                    ? GrupoSanguineo.O_NEG : GrupoSanguineo.A_POS;
            LocalDate validade = HOJE.plusDays(sorteio.nextInt(21) - 10L);
            StatusBolsa status = sorteio.nextDouble() < 0.8
                    ? StatusBolsa.DISPONIVEL : StatusBolsa.RESERVADA;

            Bolsa bolsa = bolsa(String.format("B%03d", b), grupo, validade, status);
            String unidade = unidades.get(sorteio.nextInt(quantasUnidades));
            estoque.computeIfAbsent(unidade, chave -> new ArrayList<>()).add(bolsa);
        }

        String solicitante = unidades.get(sorteio.nextInt(quantasUnidades));
        return new Cenario(malha, estoque, solicitante);
    }

    // ------------------------------------------------------------------
    // Oraculo
    // ------------------------------------------------------------------

    private static void conferirContraOraculo(Cenario cenario, ResultadoAlocacao resultado,
                                              long semente) {
        String contexto = "cenario da semente " + semente;
        Map<String, Double> distancias = distanciasPorForcaBruta(
                cenario.malha(), cenario.solicitante());

        // Reconstroi as tres etapas do funil por varredura completa.
        List<BolsaEmUnidade> daCombinacao = new ArrayList<>();
        List<BolsaEmUnidade> alocaveis = new ArrayList<>();
        List<BolsaEmUnidade> alcancaveis = new ArrayList<>();

        for (Map.Entry<String, List<Bolsa>> entrada : cenario.estoque().entrySet()) {
            for (Bolsa bolsa : entrada.getValue()) {
                if (bolsa.getGrupoSanguineo() != GrupoSanguineo.O_NEG) {
                    continue;
                }
                BolsaEmUnidade par = new BolsaEmUnidade(bolsa, entrada.getKey());
                daCombinacao.add(par);
                boolean valida = !HOJE.isAfter(bolsa.getDataValidade())
                        && bolsa.getStatus() == StatusBolsa.DISPONIVEL;
                if (!valida) {
                    continue;
                }
                alocaveis.add(par);
                if (distancias.get(entrada.getKey()) < SEM_ROTA) {
                    alcancaveis.add(par);
                }
            }
        }

        if (daCombinacao.isEmpty()) {
            assertEquals(MotivoFalhaAlocacao.SEM_ESTOQUE, resultado.motivo(), contexto);
            return;
        }
        if (alocaveis.isEmpty()) {
            assertEquals(MotivoFalhaAlocacao.TODAS_VENCIDAS, resultado.motivo(), contexto);
            return;
        }
        if (alcancaveis.isEmpty()) {
            assertEquals(MotivoFalhaAlocacao.SEM_CAMINHO, resultado.motivo(), contexto);
            return;
        }

        BolsaEmUnidade esperada = alcancaveis.stream()
                .min(Comparator
                        .comparing((BolsaEmUnidade par) -> par.bolsa().getDataValidade())
                        .thenComparing(par -> par.bolsa().getCodigoRastreio()))
                .orElseThrow();

        assertTrue(resultado.sucesso(), contexto);
        assertEquals(esperada.bolsa().getCodigoRastreio(),
                resultado.bolsa().getCodigoRastreio(), contexto);
        assertEquals(esperada.unidade(), resultado.unidadeDaBolsa(), contexto);
        assertEquals(distancias.get(esperada.unidade()), resultado.tempoMinutos(),
                TOLERANCIA, contexto);
        conferirCaminho(cenario, resultado, contexto);
    }

    /**
     * O caminho devolvido precisa se sustentar sozinho: comecar na
     * solicitante, terminar na unidade da bolsa, andar so por arestas que
     * existem e somar exatamente o tempo devolvido.
     */
    private static void conferirCaminho(Cenario cenario, ResultadoAlocacao resultado,
                                        String contexto) {
        List<String> caminho = resultado.rota().caminho();
        assertEquals(cenario.solicitante(), caminho.get(0), contexto);
        assertEquals(resultado.unidadeDaBolsa(), caminho.get(caminho.size() - 1), contexto);

        double soma = 0.0;
        for (int passo = 0; passo < caminho.size() - 1; passo++) {
            Double peso = cenario.malha().pesoAresta(caminho.get(passo), caminho.get(passo + 1));
            assertNotNull(peso, contexto + ": caminho usa aresta inexistente "
                    + caminho.get(passo) + " -> " + caminho.get(passo + 1));
            soma += peso;
        }
        assertEquals(resultado.tempoMinutos(), soma, TOLERANCIA, contexto);
    }

    /**
     * Caminho minimo por Floyd-Warshall, O(V^3): implementacao independente
     * do Dijkstra de producao, de proposito.
     */
    private static Map<String, Double> distanciasPorForcaBruta(Grafo<String> malha,
                                                               String origem) {
        List<String> vertices = new ArrayList<>(malha.listarVertices());
        int n = vertices.size();
        double[][] distancia = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    distancia[i][j] = 0.0;
                } else {
                    Double peso = malha.pesoAresta(vertices.get(i), vertices.get(j));
                    distancia[i][j] = peso == null ? SEM_ROTA : peso;
                }
            }
        }

        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (distancia[i][k] + distancia[k][j] < distancia[i][j]) {
                        distancia[i][j] = distancia[i][k] + distancia[k][j];
                    }
                }
            }
        }

        Map<String, Double> daOrigem = new LinkedHashMap<>();
        int indiceOrigem = vertices.indexOf(origem);
        for (int j = 0; j < n; j++) {
            daOrigem.put(vertices.get(j), distancia[indiceOrigem][j]);
        }
        return daOrigem;
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private static Bolsa bolsa(String codigo, GrupoSanguineo grupo,
                               LocalDate validade, StatusBolsa status) {
        TipoHemocomponente tipo = TipoHemocomponente.CONCENTRADO_HEMACIAS;
        Bolsa criada = new Bolsa(codigo, tipo, grupo, 450,
                validade.minusDays(tipo.getValidadeDias()), hemocentroTeste());
        criada.setStatus(status);
        return criada;
    }

    private static Hemocentro hemocentroTeste() {
        return new Hemocentro(
                "H1", "Hemocentro Teste", "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100");
    }

    private record Cenario(Grafo<String> malha,
                           Map<String, List<Bolsa>> estoque,
                           String solicitante) {
    }

    private record BolsaEmUnidade(Bolsa bolsa, String unidade) {
    }
}
