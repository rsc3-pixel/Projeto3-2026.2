package com.rotavital.servico;

import com.rotavital.api.dto.BolsaRequest;
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
