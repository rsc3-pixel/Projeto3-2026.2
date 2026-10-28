package com.rotavital.alocacao;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.Dijkstra;
import com.rotavital.estruturas.Grafo;
import com.rotavital.rede.CarregadorMalha;
import com.rotavital.rede.UnidadeRede;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Medicao de tempo da PI3-60: Dijkstra e alocacao completa sobre a malha das
 * 12 unidades reais com a massa de estoque da PI3-18.
 *
 * <p>A massa espelha a {@code CargaInicial}: mesma semente (42), mesma
 * sequencia de sorteio e mesmas quantidades - 25 bolsas em cada um dos 4
 * hemocentros, 100 no total. Duplicar a regra aqui e proposital: a carga real
 * escreve em repositorios JPA e a medicao nao pode pagar o custo do banco,
 * senao mediria o Hibernate e nao o algoritmo.</p>
 *
 * <p>Metodo de medicao: {@code System.nanoTime()} por chamada, com as
 * primeiras execucoes descartadas como aquecimento (JIT e caches frios
 * distorcem o inicio) e a media tirada apenas das execucoes medidas. Origem e
 * combinacao pedida giram a cada execucao para nao medir sempre o mesmo
 * caso. Um checksum acumulado impede o JIT de eliminar as chamadas como
 * codigo morto.</p>
 *
 * <p>Os numeros saem no log do teste, prontos para registro no card. O teste
 * nao impoe limite de tempo: medir e registrar nao pode virar flakiness de CI
 * por variacao de maquina.</p>
 */
class MedicaoDesempenhoTest {

    private static final long SEMENTE = 42;
    private static final int BOLSAS_POR_HEMOCENTRO = 25;
    private static final List<String> HEMOCENTROS = List.of("HC01", "HC02", "HC03", "HC08");

    private static final int EXECUCOES_DE_AQUECIMENTO = 50;
    private static final int EXECUCOES_MEDIDAS = 200;

    private static final LocalDate HOJE = LocalDate.now();

    @Test
    void medeDijkstraEAlocacaoCompletaSeparadamente() {
        Grafo<String> malha = CarregadorMalha.carregar();
        Map<String, List<Bolsa>> estoque = massaDaCargaInicial();
        assertEquals(4 * BOLSAS_POR_HEMOCENTRO,
                estoque.values().stream().mapToInt(List::size).sum());

        double mediaDijkstraMicros = medirDijkstra(malha);
        double mediaAlocacaoMicros = medirAlocacaoCompleta(malha, estoque);

        System.out.printf(Locale.ROOT,
                "%n[PI3-60] Malha: %d unidades | Massa: %d bolsas (semente %d)%n"
                        + "[PI3-60] Dijkstra (calcularDistancias): media de %.1f us"
                        + " em %d execucoes (%d de aquecimento descartadas)%n"
                        + "[PI3-60] Alocacao completa (alocar): media de %.1f us"
                        + " em %d execucoes (%d de aquecimento descartadas)%n%n",
                malha.quantidadeVertices(), 4 * BOLSAS_POR_HEMOCENTRO, SEMENTE,
                mediaDijkstraMicros, EXECUCOES_MEDIDAS, EXECUCOES_DE_AQUECIMENTO,
                mediaAlocacaoMicros, EXECUCOES_MEDIDAS, EXECUCOES_DE_AQUECIMENTO);

        assertTrue(mediaDijkstraMicros > 0);
        assertTrue(mediaAlocacaoMicros > 0);
    }

    /**
     * Mede so o caminho minimo, girando a origem pelas 12 unidades para
     * cobrir posicoes diferentes da malha.
     */
    private double medirDijkstra(Grafo<String> malha) {
        Dijkstra<String> dijkstra = new Dijkstra<>(malha);
        List<String> origens = UnidadeRede.TODAS.stream().map(UnidadeRede::id).toList();

        long somaNanos = 0;
        long checksum = 0;
        int total = EXECUCOES_DE_AQUECIMENTO + EXECUCOES_MEDIDAS;

        for (int i = 0; i < total; i++) {
            String origem = origens.get(i % origens.size());

            long inicio = System.nanoTime();
            Map<String, Double> distancias = dijkstra.calcularDistancias(origem);
            long duracao = System.nanoTime() - inicio;

            checksum += distancias.size();
            if (i >= EXECUCOES_DE_AQUECIMENTO) {
                somaNanos += duracao;
            }
        }

        // A malha e conectada: toda origem alcanca as 12 unidades.
        assertEquals((long) total * malha.quantidadeVertices(), checksum);
        return somaNanos / (double) EXECUCOES_MEDIDAS / 1_000.0;
    }

    /**
     * Mede o fluxo inteiro da PI3-58 - candidatas, Dijkstra, filtro de
     * alcance, FEFO e reconstrucao da rota - girando solicitante e
     * combinacao pedida. Combinacao sem estoque tambem entra na medicao:
     * falha tratada faz parte do custo real do servico.
     */
    private double medirAlocacaoCompleta(Grafo<String> malha, Map<String, List<Bolsa>> estoque) {
        ServicoAlocacaoRota servico = new ServicoAlocacaoRota(malha, estoque);
        List<String> solicitantes = UnidadeRede.TODAS.stream().map(UnidadeRede::id).toList();
        GrupoSanguineo[] grupos = GrupoSanguineo.values();
        TipoHemocomponente[] tipos = TipoHemocomponente.values();

        long somaNanos = 0;
        int sucessos = 0;
        int total = EXECUCOES_DE_AQUECIMENTO + EXECUCOES_MEDIDAS;

        for (int i = 0; i < total; i++) {
            String solicitante = solicitantes.get(i % solicitantes.size());
            GrupoSanguineo grupo = grupos[i % grupos.length];
            TipoHemocomponente tipo = tipos[i % tipos.length];

            long inicio = System.nanoTime();
            ResultadoAlocacao resultado = servico.alocar(solicitante, grupo, tipo, HOJE);
            long duracao = System.nanoTime() - inicio;

            if (resultado.sucesso()) {
                sucessos++;
            }
            if (i >= EXECUCOES_DE_AQUECIMENTO) {
                somaNanos += duracao;
            }
        }

        // Com 100 bolsas sorteadas entre 32 combinacoes, parte das chamadas
        // tem de alocar: zero sucesso indicaria massa ou servico quebrados.
        assertTrue(sucessos > 0, "nenhuma alocacao teve sucesso com a massa carregada");
        return somaNanos / (double) EXECUCOES_MEDIDAS / 1_000.0;
    }

    /**
     * As mesmas 100 bolsas da {@code CargaInicial}: semente 42 e sorteios na
     * mesma ordem (tipo, grupo, dias desde a coleta, volume), 25 por
     * hemocentro. Nenhuma nasce vencida, porque a coleta e sorteada dentro da
     * metade da validade do componente.
     */
    private Map<String, List<Bolsa>> massaDaCargaInicial() {
        Random sorteio = new Random(SEMENTE);
        GrupoSanguineo[] grupos = GrupoSanguineo.values();
        TipoHemocomponente[] tipos = TipoHemocomponente.values();

        Map<String, List<Bolsa>> estoque = new LinkedHashMap<>();
        int codigo = 1;
        for (String idHemocentro : HEMOCENTROS) {
            Hemocentro hemocentro = hemocentro(idHemocentro);
            List<Bolsa> bolsas = new ArrayList<>(BOLSAS_POR_HEMOCENTRO);

            for (int i = 0; i < BOLSAS_POR_HEMOCENTRO; i++) {
                TipoHemocomponente tipo = tipos[sorteio.nextInt(tipos.length)];
                GrupoSanguineo grupo = grupos[sorteio.nextInt(grupos.length)];
                int diasAtras = sorteio.nextInt(Math.max(1, tipo.getValidadeDias() / 2));
                int volume = 200 + sorteio.nextInt(201);

                bolsas.add(new Bolsa(String.format("BOL%05d", codigo++),
                        tipo, grupo, volume, HOJE.minusDays(diasAtras), hemocentro));
            }
            estoque.put(idHemocentro, bolsas);
        }
        return estoque;
    }

    private static Hemocentro hemocentro(String id) {
        return new Hemocentro(
                id, "Hemocentro " + id, "81999999999",
                new Endereco("Rua A", "1", "Centro", "Recife", "PE", "50000-000", -8.05, -34.90),
                "00000000000100");
    }
}
