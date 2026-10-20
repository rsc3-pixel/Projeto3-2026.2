package com.rotavital.servico;

import com.rotavital.api.dto.AtualizarStatusRotaRequest;
import com.rotavital.api.dto.BolsaResponse;
import com.rotavital.api.dto.EmbarqueBolsaRequest;
import com.rotavital.api.dto.RotaRequest;
import com.rotavital.api.dto.RotaResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.Rota;
import com.rotavital.dominio.enums.StatusBolsa;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.RotaRepository;
import com.rotavital.servico.evento.BolsaAlterada;
import com.rotavital.servico.excecao.OperacaoInvalidaException;
import com.rotavital.servico.excecao.RecursoNaoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RotaServico {

    /**
     * Ciclo de vida da rota. Concluida e cancelada sao estados finais.
     */
    private static final Map<StatusRota, Set<StatusRota>> TRANSICOES = new EnumMap<>(StatusRota.class);

    static {
        TRANSICOES.put(StatusRota.PLANEJADA, Set.of(StatusRota.EM_TRANSITO, StatusRota.CANCELADA));
        TRANSICOES.put(StatusRota.EM_TRANSITO, Set.of(StatusRota.CONCLUIDA, StatusRota.CANCELADA));
        TRANSICOES.put(StatusRota.CONCLUIDA, Set.of());
        TRANSICOES.put(StatusRota.CANCELADA, Set.of());
    }

    private final RotaRepository rotas;
    private final BolsaRepository bolsas;
    private final HemocentroServico hemocentroServico;
    private final HospitalServico hospitalServico;
    private final ApplicationEventPublisher eventos;

    public RotaServico(RotaRepository rotas,
                       BolsaRepository bolsas,
                       HemocentroServico hemocentroServico,
                       HospitalServico hospitalServico,
                       ApplicationEventPublisher eventos) {
        this.rotas = rotas;
        this.bolsas = bolsas;
        this.hemocentroServico = hemocentroServico;
        this.hospitalServico = hospitalServico;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<RotaResponse> listar(StatusRota status, String hemocentroId, String hospitalId) {
        return rotas.findAll().stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> hemocentroId == null
                        || (r.getOrigem() != null && hemocentroId.equals(r.getOrigem().getId())))
                .filter(r -> hospitalId == null
                        || (r.getDestino() != null && hospitalId.equals(r.getDestino().getId())))
                .map(RotaResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RotaResponse> listarPorHemocentro(String hemocentroId, StatusRota status) {
        hemocentroServico.buscar(hemocentroId);
        return listar(status, hemocentroId, null);
    }

    @Transactional(readOnly = true)
    public RotaResponse detalhar(String id) {
        return RotaResponse.de(buscar(id));
    }

    /**
     * Devolve a entidade. Uso interno deste servico, que ja roda dentro de
     * transacao. O controlador usa {@link #detalhar(String)}.
     */
    @Transactional(readOnly = true)
    public Rota buscar(String id) {
        return rotas.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Rota", id));
    }

    @Transactional
    public RotaResponse criar(RotaRequest dados) {
        Hemocentro origem = hemocentroServico.buscar(dados.hemocentroOrigemId());
        Hospital destino = hospitalServico.buscar(dados.hospitalDestinoId());

        if (!dados.previsaoChegada().isAfter(dados.previsaoSaida())) {
            throw new OperacaoInvalidaException(
                    "previsaoChegada precisa ser posterior a previsaoSaida");
        }

        Rota rota = new Rota(
                UUID.randomUUID().toString(),
                origem,
                destino,
                dados.previsaoSaida(),
                dados.previsaoChegada());

        return RotaResponse.de(rotas.save(rota));
    }

    /**
     * Muda o status da rota e arrasta o status das bolsas embarcadas junto:
     * sair em transito coloca a carga em transito, concluir marca entregue.
     * E o que mantem estoque e logistica coerentes sem exigir duas chamadas.
     */
    @Transactional
    public RotaResponse atualizarStatus(String id, AtualizarStatusRotaRequest dados) {
        Rota rota = buscar(id);
        StatusRota atual = rota.getStatus();
        StatusRota novo = dados.status();

        if (atual == novo) {
            return RotaResponse.de(rota);
        }
        if (!TRANSICOES.get(atual).contains(novo)) {
            throw new OperacaoInvalidaException("Transicao invalida: " + atual + " -> " + novo);
        }

        switch (novo) {
            case EM_TRANSITO -> {
                if (rota.getBolsas().isEmpty()) {
                    throw new OperacaoInvalidaException("Rota sem bolsas embarcadas nao pode sair");
                }
                rota.setSaidaReal(dados.saidaReal() != null ? dados.saidaReal() : LocalDateTime.now());
                rota.getBolsas().forEach(b -> {
                    b.setStatus(StatusBolsa.EM_TRANSITO);
                    eventos.publishEvent(new BolsaAlterada(b));
                });
            }
            case CONCLUIDA -> {
                rota.setChegadaReal(dados.chegadaReal() != null ? dados.chegadaReal() : LocalDateTime.now());
                rota.getBolsas().forEach(b -> {
                    b.setStatus(StatusBolsa.ENTREGUE);
                    eventos.publishEvent(new BolsaAlterada(b));
                });
            }
            case CANCELADA -> {
                // Carga volta ao estoque: rota cancelada nao consome bolsa.
                rota.getBolsas().forEach(b -> {
                    b.setStatus(StatusBolsa.RESERVADA);
                    eventos.publishEvent(new BolsaAlterada(b));
                });
            }
            default -> throw new OperacaoInvalidaException("Status nao suportado: " + novo);
        }

        rota.setStatus(novo);
        return RotaResponse.de(rotas.save(rota));
    }

    @Transactional(readOnly = true)
    public List<BolsaResponse> listarBolsas(String rotaId) {
        return buscar(rotaId).getBolsas().stream()
                .map(BolsaResponse::de)
                .toList();
    }

    @Transactional
    public BolsaResponse embarcar(String rotaId, EmbarqueBolsaRequest dados) {
        Rota rota = buscar(rotaId);

        if (rota.getStatus() != StatusRota.PLANEJADA) {
            throw new OperacaoInvalidaException(
                    "So e possivel embarcar em rota PLANEJADA. Status atual: " + rota.getStatus());
        }

        Bolsa bolsa = bolsas.findById(dados.bolsaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bolsa", dados.bolsaId()));

        if (rota.getBolsas().contains(bolsa)) {
            throw new OperacaoInvalidaException("Bolsa ja embarcada nesta rota");
        }
        if (bolsa.getStatus() != StatusBolsa.RESERVADA) {
            throw new OperacaoInvalidaException(
                    "So bolsa RESERVADA pode ser embarcada. Status atual: " + bolsa.getStatus());
        }
        if (bolsa.getHemocentroOrigem() == null
                || !bolsa.getHemocentroOrigem().getId().equals(rota.getOrigem().getId())) {
            throw new OperacaoInvalidaException(
                    "A bolsa esta em outro hemocentro e nao pode sair nesta rota");
        }

        rota.adicionarBolsa(bolsa);
        rotas.save(rota);
        return BolsaResponse.de(bolsa);
    }

    @Transactional
    public void desembarcar(String rotaId, String bolsaId) {
        Rota rota = buscar(rotaId);

        if (rota.getStatus() != StatusRota.PLANEJADA) {
            throw new OperacaoInvalidaException(
                    "So e possivel desembarcar de rota PLANEJADA. Status atual: " + rota.getStatus());
        }

        Bolsa bolsa = rota.getBolsas().stream()
                .filter(b -> b.getCodigoRastreio().equals(bolsaId))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bolsa na rota", bolsaId));

        rota.removerBolsa(bolsa);
        rotas.save(rota);
    }
}
