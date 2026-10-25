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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.rotavital.api.dto.AlocacaoRequest;
import com.rotavital.api.dto.ItemRequisicaoRequest;
import com.rotavital.api.dto.RequisicaoRequest;
import com.rotavital.api.dto.RequisicaoResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.servico.AlocacaoComRepeticao;
import com.rotavital.servico.RequisicaoServico;

/**
 * Prova que a contagem de um item nao se perde sob alocacoes simultaneas
 * (PI3-109).
 *
 * <p>O caso anterior, duas alocacoes brigando pela <b>mesma</b> bolsa, ja era
 * barrado pela restricao UNIQUE que o {@code @OneToOne} de Alocacao cria. Este
 * e diferente e nao tem nada protegendo: varias bolsas <b>diferentes</b> sendo
 * alocadas ao mesmo item, cada thread lendo quantidadeAlocada, somando um e
 * gravando de volta. Duas leituras do mesmo valor produzem uma escrita perdida,
 * e o item fica marcado como menos atendido do que realmente esta.
 *
 * <p>O efeito pratico: o sistema acha que ainda faltam bolsas, e aloca mais do
 * que o hospital pediu.
 */
@SpringBootTest
@DisplayName("PI3-109 · Contagem de um item sob alocacoes simultaneas")
class ContagemItemConcorrenteTest {

    private static final int CONCORRENTES = 6;

    @Autowired
    private RequisicaoServico requisicaoServico;

    @Autowired
    private BolsaRepository bolsas;

    /** O caminho que a API usa: repete quando perde a disputa. */
    @Autowired
    private AlocacaoComRepeticao alocacaoComRepeticao;

    @Test
    @DisplayName("Dado um item pedindo varias bolsas, Quando sao alocadas ao mesmo "
            + "tempo, Entao a quantidade atendida soma todas")
    void contagemNaoSePerde() throws InterruptedException {
        List<Bolsa> disponiveis = bolsas.findAll().stream()
                .filter(b -> b.getStatus() == StatusBolsa.DISPONIVEL)
                .filter(b -> b.getDataValidade().isAfter(LocalDate.now()))
                .limit(CONCORRENTES)
                .toList();

        assertTrue(disponiveis.size() == CONCORRENTES,
                "a carga inicial precisa de " + CONCORRENTES + " bolsas disponiveis");

        Bolsa referencia = disponiveis.get(0);

        // Um unico item pedindo todas elas: as threads disputam o mesmo contador.
        RequisicaoResponse pedido = requisicaoServico.criar(new RequisicaoRequest(
                "HO04",
                PrioridadeRequisicao.ROTINA,
                LocalDateTime.now().plusDays(1),
                "contagem sob concorrencia",
                List.of(new ItemRequisicaoRequest(
                        referencia.getTipo(), referencia.getGrupoSanguineo(),
                        CONCORRENTES))));

        String itemId = pedido.itens().get(0).id();

        AtomicInteger alocadas = new AtomicInteger();
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(CONCORRENTES);
        ExecutorService pool = Executors.newFixedThreadPool(CONCORRENTES);

        for (Bolsa b : disponiveis) {
            pool.submit(() -> {
                try {
                    largada.await();
                    alocacaoComRepeticao.alocar(pedido.id(), itemId,
                            new AlocacaoRequest(b.getCodigoRastreio()));
                    alocadas.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    chegada.countDown();
                }
            });
        }

        largada.countDown();
        assertTrue(chegada.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        int registrado = requisicaoServico.listarItens(pedido.id()).stream()
                .filter(i -> i.id().equals(itemId))
                .findFirst()
                .orElseThrow()
                .quantidadeAlocada();


        // Sem este piso o teste passaria com tudo zerado, que e o que acontece
        // quando a alocacao quebra por completo: zero aceitas e zero
        // registradas sao iguais, e nao provam nada.
        assertTrue(alocadas.get() > 0,
                "ao menos uma alocacao precisa passar, senao o teste nao exercita "
                        + "a concorrencia; aceitas=" + alocadas.get());

        assertEquals(alocadas.get(), registrado,
                "a quantidade registrada deveria contar todas as alocacoes aceitas; "
                        + "diferenca indica escrita perdida entre threads");
    }
}
