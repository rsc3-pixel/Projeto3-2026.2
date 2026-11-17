package com.rotavital.servico;

import com.rotavital.alocacao.SelecaoFefo;
import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.api.dto.BolsasEmRiscoResponse;
import com.rotavital.api.dto.BolsasEmRiscoResponse.BolsaEmRisco;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.AlocacaoRepository;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.servico.evento.BolsaAlterada;
import com.rotavital.servico.evento.BolsaRemovida;
import com.rotavital.servico.excecao.OperacaoInvalidaException;
import com.rotavital.servico.excecao.RecursoNaoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class BolsaServico {

    /**
     * Transicoes permitidas de status. Uma bolsa entregue ou descartada e
     * estado final: nao volta. Sem esse mapa qualquer PATCH poderia
     * "ressuscitar" uma bolsa ja descartada.
     */
    private static final Map<StatusBolsa, Set<StatusBolsa>> TRANSICOES = new EnumMap<>(StatusBolsa.class);

    static {
        TRANSICOES.put(StatusBolsa.DISPONIVEL, Set.of(StatusBolsa.RESERVADA, StatusBolsa.DESCARTADA));
        TRANSICOES.put(StatusBolsa.RESERVADA, Set.of(StatusBolsa.DISPONIVEL, StatusBolsa.EM_TRANSITO, StatusBolsa.DESCARTADA));
        TRANSICOES.put(StatusBolsa.EM_TRANSITO, Set.of(StatusBolsa.ENTREGUE, StatusBolsa.DESCARTADA));
        TRANSICOES.put(StatusBolsa.ENTREGUE, Set.of());
        TRANSICOES.put(StatusBolsa.DESCARTADA, Set.of());
    }

    private final BolsaRepository bolsas;
    private final AlocacaoRepository alocacoes;
    private final HemocentroServico hemocentroServico;
    private final ApplicationEventPublisher eventos;

    public BolsaServico(BolsaRepository bolsas,
                        AlocacaoRepository alocacoes,
                        HemocentroServico hemocentroServico,
                        ApplicationEventPublisher eventos) {
        this.bolsas = bolsas;
        this.alocacoes = alocacoes;
        this.hemocentroServico = hemocentroServico;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<Bolsa> listar(StatusBolsa status, TipoHemocomponente tipo,
                              GrupoSanguineo grupo, String hemocentroId) {
        return bolsas.findAll().stream()
                .filter(b -> status == null || b.getStatus() == status)
                .filter(b -> tipo == null || b.getTipo() == tipo)
                .filter(b -> grupo == null || b.getGrupoSanguineo() == grupo)
                .filter(b -> hemocentroId == null
                        || (b.getHemocentroOrigem() != null
                            && hemocentroId.equals(b.getHemocentroOrigem().getId())))
                // Ordem FEFO: a que vence antes aparece primeiro.
                .sorted((a, b) -> a.getDataValidade().compareTo(b.getDataValidade()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Bolsa> listarPorHemocentro(String hemocentroId, StatusBolsa status,
                                           TipoHemocomponente tipo, GrupoSanguineo grupo) {
        hemocentroServico.buscar(hemocentroId);
        return listar(status, tipo, grupo, hemocentroId);
    }

    @Transactional(readOnly = true)
    public Bolsa buscar(String id) {
        return bolsas.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bolsa", id));
    }

    /**
     * A fila de saida de um hemocentro: as bolsas alocaveis na ordem em que a
     * alocacao as escolheria, e quantas ficaram de fora.
     *
     * <p>A ordenacao e a da {@link SelecaoFefo}, a mesma classe que a
     * alocacao real usa. Ela descarta a bolsa vencida <b>antes</b> de
     * ordenar: o FEFO poe no topo quem vence primeiro, e uma bolsa vencida
     * vence antes de todas as validas, entao filtrar depois a deixaria em
     * primeiro lugar sempre.</p>
     *
     * <p>Mora no servico, e nao no controlador da tela, porque e regra de
     * negocio: a tela so exibe a fila que o sistema seguiria.</p>
     */
    @Transactional(readOnly = true)
    public FilaFefo filaFefo(String hemocentroId, LocalDate dataReferencia) {
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();
        List<Bolsa> todas = listarPorHemocentro(hemocentroId, null, null, null);
        List<Bolsa> fila = SelecaoFefo.ordenar(todas, hoje);
        return new FilaFefo(fila, todas.size() - fila.size());
    }

    /** Resultado de {@link #filaFefo}: a fila ordenada e quantas bolsas nao entraram nela. */
    public record FilaFefo(List<Bolsa> fila, int foraDaFila) {
    }

    /**
     * Painel de estoque critico (HU-04): bolsas em estoque que vencem nos
     * proximos {@code dias}, da mais urgente para a menos, separadas das que
     * ja venceram.
     *
     * <p>"Em estoque" sao as bolsas {@link StatusBolsa#DISPONIVEL} e
     * {@link StatusBolsa#RESERVADA}: sao as que ainda podem virar descarte se
     * nao sairem a tempo. Bolsa em transito ja saiu, entregue ja foi usada e
     * descartada ja e perda contabilizada; nenhuma delas e acao pendente de
     * quem opera o estoque.</p>
     *
     * <p>A ordem e por validade e, em empate, por codigo de rastreio: e a
     * mesma regra de desempate da {@link SelecaoFefo}, para que o painel e a
     * fila de saida nunca discordem sobre quem vem primeiro.</p>
     *
     * @param dias           janela em dias; precisa ser positiva
     * @param hemocentroId   restringe a um hemocentro, ou nulo para a rede
     * @param dataReferencia o "hoje" da contagem; nulo usa a data atual
     * @throws RecursoNaoEncontradoException se o hemocentro informado nao existir
     * @throws IllegalArgumentException      se a janela nao for positiva
     */
    @Transactional(readOnly = true)
    public BolsasEmRiscoResponse emRisco(int dias, String hemocentroId, LocalDate dataReferencia) {
        if (dias < 1) {
            throw new IllegalArgumentException("A janela de dias precisa ser positiva: " + dias);
        }
        LocalDate hoje = dataReferencia != null ? dataReferencia : LocalDate.now();

        List<Bolsa> emEstoque = (hemocentroId != null
                ? listarPorHemocentro(hemocentroId, null, null, null)
                : listar(null, null, null, null))
                .stream()
                .filter(b -> b.getStatus() == StatusBolsa.DISPONIVEL
                        || b.getStatus() == StatusBolsa.RESERVADA)
                .sorted(Comparator.comparing(Bolsa::getDataValidade)
                        .thenComparing(Bolsa::getCodigoRastreio))
                .toList();

        List<BolsaEmRisco> vencidas = new ArrayList<>();
        List<BolsaEmRisco> emRisco = new ArrayList<>();
        for (Bolsa bolsa : emEstoque) {
            BolsaEmRisco item = BolsaEmRisco.de(bolsa, hoje);
            if (bolsa.estaVencida(hoje)) {
                vencidas.add(item);
            } else if (item.diasRestantes() <= dias) {
                emRisco.add(item);
            }
        }

        // Estado vazio com mensagem propria, nao tabela em branco: e o
        // terceiro criterio de aceite da historia.
        String mensagem = emRisco.isEmpty()
                ? "Nenhuma bolsa em risco nos proximos " + dias + " dias"
                : null;

        return new BolsasEmRiscoResponse(dias, hoje, hemocentroId,
                emRisco.size(), vencidas.size(), mensagem,
                List.copyOf(emRisco), List.copyOf(vencidas));
    }

    @Transactional
    public Bolsa criar(BolsaRequest dados) {
        Hemocentro hemocentro = hemocentroServico.buscar(dados.hemocentroId());

        Bolsa bolsa = new Bolsa(
                UUID.randomUUID().toString(),
                dados.tipoHemocomponente(),
                dados.grupoSanguineo(),
                dados.volumeMl(),
                dados.dataColeta(),
                hemocentro);

        if (bolsa.estaVencida(LocalDate.now())) {
            throw new OperacaoInvalidaException(
                    "A bolsa ja nasceria vencida: validade de "
                    + dados.tipoHemocomponente().getValidadeDias()
                    + " dias a partir de " + dados.dataColeta());
        }

        Bolsa salva = bolsas.save(bolsa);
        // O indice de estoque so e atualizado depois do commit.
        eventos.publishEvent(new BolsaAlterada(salva));
        return salva;
    }

    @Transactional
    public Bolsa atualizarStatus(String id, StatusBolsa novoStatus) {
        Bolsa bolsa = buscar(id);
        StatusBolsa atual = bolsa.getStatus();

        if (atual == novoStatus) {
            return bolsa;
        }
        if (!TRANSICOES.get(atual).contains(novoStatus)) {
            throw new OperacaoInvalidaException(
                    "Transicao invalida: " + atual + " -> " + novoStatus);
        }

        bolsa.setStatus(novoStatus);
        Bolsa salva = bolsas.save(bolsa);
        eventos.publishEvent(new BolsaAlterada(salva));
        return salva;
    }

    @Transactional
    public void remover(String id) {
        Bolsa bolsa = buscar(id);

        if (alocacoes.existsByBolsaCodigoRastreio(id)) {
            throw new OperacaoInvalidaException(
                    "Bolsa esta alocada a uma requisicao e nao pode ser removida");
        }
        if (bolsa.getStatus() != StatusBolsa.DISPONIVEL
                && bolsa.getStatus() != StatusBolsa.DESCARTADA) {
            throw new OperacaoInvalidaException(
                    "So e possivel remover bolsa DISPONIVEL ou DESCARTADA. Status atual: "
                    + bolsa.getStatus());
        }
        bolsas.delete(bolsa);
        eventos.publishEvent(new BolsaRemovida(bolsa));
    }
}
