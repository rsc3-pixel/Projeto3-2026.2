package com.rotavital.servico;

import com.rotavital.compatibilidade.MatrizCompatibilidade;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.estruturas.IndiceEstoque;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.servico.evento.BolsaAlterada;
import com.rotavital.servico.evento.BolsaRemovida;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Mantem o {@link IndiceEstoque} vivo e sincronizado com o banco, e e o ponto
 * por onde a alocacao consulta o estoque por hash.
 *
 * <p><b>O indice guarda apenas bolsas {@link StatusBolsa#DISPONIVEL}.</b>
 * Bolsa reservada, em transito, entregue ou descartada esta comprometida com
 * outra requisicao ou fora de circulacao: mante-la indexada obrigaria todo
 * consumidor a filtrar de novo, e o ganho do indice - responder "o que esta
 * disponivel desta combinacao" em O(1) - se perderia.</p>
 *
 * <p><b>Atualizacao apenas depois do commit.</b> Os ouvintes usam
 * {@link TransactionalEventListener} na fase
 * {@link TransactionPhase#AFTER_COMMIT}. Se o indice fosse atualizado no meio
 * da transacao, um rollback deixaria memoria e banco discordando: a bolsa
 * voltaria a ficar {@code DISPONIVEL} na tabela, mas continuaria fora do
 * indice, e nenhuma alocacao automatica a encontraria de novo ate o proximo
 * reinicio. Escolhemos o evento transacional em vez de
 * {@code TransactionSynchronization} manual porque o acoplamento fica menor:
 * os servicos apenas publicam o fato, sem conhecer o indice.</p>
 *
 * <p><b>Thread-safety.</b> O {@link java.util.HashMap} interno do indice nao
 * e seguro para acesso concorrente, e este componente e um singleton servindo
 * requisicoes em paralelo. Um {@link ReentrantReadWriteLock} protege o
 * acesso: varias leituras simultaneas, escrita exclusiva. A escolha pelo
 * {@code ReadWriteLock} em vez de {@code synchronized} e pelo perfil de uso -
 * a alocacao le com muito mais frequencia do que o estoque muda.</p>
 */
@Component
public class IndiceEstoqueServico {

    private final BolsaRepository bolsas;
    private final IndiceEstoque indice = new IndiceEstoque();
    private final ReentrantReadWriteLock trava = new ReentrantReadWriteLock();

    public IndiceEstoqueServico(BolsaRepository bolsas) {
        this.bolsas = bolsas;
    }

    /**
     * Reconstroi o indice a partir do banco, com todas as bolsas disponiveis.
     *
     * <p>Roda no {@link ApplicationReadyEvent}, que dispara depois dos
     * {@code CommandLineRunner} - entao a carga inicial de dados ja terminou
     * quando o indice e montado. Fica publico porque os testes precisam
     * recarregar depois de montar o cenario.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void carregar() {
        trava.writeLock().lock();
        try {
            indice.limpar();
            for (Bolsa bolsa : bolsas.findAll()) {
                if (bolsa.getStatus() == StatusBolsa.DISPONIVEL) {
                    indice.adicionar(bolsa);
                }
            }
        } finally {
            trava.writeLock().unlock();
        }
    }

    /**
     * Coloca a bolsa no indice se ela estiver disponivel, e a tira caso
     * contrario.
     *
     * <p>Remove sempre antes de decidir: a bolsa pode ter mudado de status
     * desde a ultima indexacao, e remover pela instancia nova funciona porque
     * {@code Bolsa.equals} compara pelo codigo de rastreio.</p>
     */
    public void sincronizar(Bolsa bolsa) {
        if (bolsa == null) {
            return;
        }
        trava.writeLock().lock();
        try {
            indice.remover(bolsa);
            if (bolsa.getStatus() == StatusBolsa.DISPONIVEL) {
                indice.adicionar(bolsa);
            }
        } finally {
            trava.writeLock().unlock();
        }
    }

    /**
     * Tira a bolsa do indice, sem olhar o status. Usado quando ela e
     * excluida.
     */
    public void retirar(Bolsa bolsa) {
        if (bolsa == null) {
            return;
        }
        trava.writeLock().lock();
        try {
            indice.remover(bolsa);
        } finally {
            trava.writeLock().unlock();
        }
    }

    /**
     * Bolsas disponiveis que podem ser transfundidas no receptor, para aquele
     * hemocomponente.
     *
     * <p>Consulta a {@link MatrizCompatibilidade} para saber quais grupos
     * procurar e faz uma busca no indice por grupo compativel. Para hemacias
     * de receptor O-, por exemplo, e uma unica busca, nao oito.</p>
     *
     * @return lista com as candidatas; vazia se nao houver nenhuma ou se
     *         algum argumento for nulo. Nunca {@code null}
     */
    public List<Bolsa> buscarCompativeis(GrupoSanguineo receptor, TipoHemocomponente tipo) {
        Set<GrupoSanguineo> doadores = MatrizCompatibilidade.doadoresPara(receptor, tipo);

        List<Bolsa> candidatas = new ArrayList<>();
        trava.readLock().lock();
        try {
            for (GrupoSanguineo doador : doadores) {
                candidatas.addAll(indice.buscar(doador, tipo));
            }
        } finally {
            trava.readLock().unlock();
        }
        return candidatas;
    }

    /**
     * Bolsas disponiveis de uma combinacao exata, sem compatibilidade.
     * Existe para os testes poderem inspecionar o estado do indice.
     */
    public List<Bolsa> buscarExato(GrupoSanguineo grupo, TipoHemocomponente tipo) {
        trava.readLock().lock();
        try {
            return indice.buscar(grupo, tipo);
        } finally {
            trava.readLock().unlock();
        }
    }

    /** Quantidade de bolsas indexadas. */
    public int tamanho() {
        trava.readLock().lock();
        try {
            return indice.tamanho();
        } finally {
            trava.readLock().unlock();
        }
    }

    // ------------------------------------------------------------------
    // Ouvintes: so rodam depois do commit.
    // ------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAlterarBolsa(BolsaAlterada evento) {
        sincronizar(evento.bolsa());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRemoverBolsa(BolsaRemovida evento) {
        retirar(evento.bolsa());
    }
}
