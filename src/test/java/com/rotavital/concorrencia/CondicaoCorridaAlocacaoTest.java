package com.rotavital.concorrencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.api.dto.RequisicaoResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.servico.RequisicaoServico;

/**
 * Prova que duas alocacoes simultaneas nao conseguem reservar a mesma bolsa
 * (PI3-109).
 *
 * <p>O caso e real na operacao: dois hemocentros abrem o painel ao mesmo tempo
 * e alocam para pedidos diferentes. Entre a verificacao de que a bolsa esta
 * DISPONIVEL e a escrita de RESERVADA existe uma janela, e sem protecao as duas
 * passam. O sangue fica prometido duas vezes, e alguem descobre isso na hora da
 * entrega.
 *
 * <p>O teste dispara varias threads contra a mesma bolsa e exige que exatamente
 * uma vença. Ele falha de proposito antes do mecanismo de sincronizacao da
 * PI3-108 existir: e assim que se demonstra que a protecao faz diferenca.
 */
@SpringBootTest
@DisplayName("PI3-109 · Duas alocacoes simultaneas disputando a mesma bolsa")
class CondicaoCorridaAlocacaoTest {

    /** Threads concorrendo. Acima de dois a janela fica mais provavel de abrir. */
    private static final int CONCORRENTES = 8;

    @Autowired
    private RequisicaoServico requisicaoServico;

    @Autowired
    private BolsaRepository bolsas;

    private Bolsa alvo;
    private List<String> itens;

    /** De qual requisicao veio cada item, para a thread saber o que alocar. */
    private final java.util.Map<String, String> requisicoesPorItem =
            new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void prepararDisputa() {
        // Uma unica bolsa disponivel: e ela que todos vao tentar reservar.
        alvo = bolsas.findAll().stream()
                .filter(b -> b.getStatus() == StatusBolsa.DISPONIVEL)
                .filter(b -> b.getDataValidade().isAfter(LocalDate.now()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "a carga inicial precisa de ao menos uma bolsa disponivel"));

        // Um pedido por thread, todos compativeis com a bolsa alvo.
        itens = criarPedidosPara(alvo);
    }

    @Test
    @DisplayName("Dado uma bolsa e varios pedidos, Quando alocam ao mesmo tempo, "
            + "Entao apenas um consegue reservar")
    void apenasUmaAlocacaoVence() throws InterruptedException {
        AtomicInteger venceram = new AtomicInteger();
        AtomicInteger recusadas = new AtomicInteger();

        // A largada solta todas de uma vez: sem isso elas entrariam em fila e
        // a janela entre ler e escrever nunca se abriria.
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(CONCORRENTES);
        ExecutorService pool = Executors.newFixedThreadPool(CONCORRENTES);

        for (String itemId : itens) {
            pool.submit(() -> {
                try {
                    largada.await();
                    requisicaoServico.alocar(
                            requisicaoDoItem(itemId), itemId,
                            new AlocacaoRequest(alvo.getCodigoRastreio()));
                    venceram.incrementAndGet();
                } catch (Exception e) {
                    // Qualquer recusa serve: a bolsa ja mudou de dono.
                    recusadas.incrementAndGet();
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown();
        assertTrue(chegada.await(30, TimeUnit.SECONDS),
                "as threads deveriam terminar em 30s");
        pool.shutdownNow();


        assertEquals(1, venceram.get(),
                "exatamente uma alocacao deveria vencer a disputa, mas "
                        + venceram.get() + " conseguiram reservar a mesma bolsa");
        assertEquals(CONCORRENTES - 1, recusadas.get(),
                "as demais deveriam ser recusadas");


        Bolsa depois = bolsas.findById(alvo.getCodigoRastreio()).orElseThrow();
        assertEquals(StatusBolsa.RESERVADA, depois.getStatus(),
                "a bolsa deveria terminar reservada uma unica vez");
    }

    /** Cria um pedido por concorrente, todos pedindo o tipo da bolsa alvo. */
    private List<String> criarPedidosPara(Bolsa bolsa) {
        return java.util.stream.IntStream.range(0, CONCORRENTES)
                .mapToObj(i -> {
                    RequisicaoResponse r = requisicaoServico.criar(new RequisicaoRequest(
                            hospitalQualquer(),
                            PrioridadeRequisicao.ROTINA,
                            LocalDateTime.now().plusDays(1),
                            "disputa concorrente " + i,
                            List.of(new ItemRequisicaoRequest(
                                    bolsa.getTipo(), bolsa.getGrupoSanguineo(), 1))));
                    requisicoesPorItem.put(r.itens().get(0).id(), r.id());
                    return r.itens().get(0).id();
                })
                .toList();
    }

    private String requisicaoDoItem(String itemId) {
        return requisicoesPorItem.get(itemId);
    }

    /** O hospital nao importa para a disputa: qualquer um da carga serve. */
    private String hospitalQualquer() {
        return "HO04";
    }
}
