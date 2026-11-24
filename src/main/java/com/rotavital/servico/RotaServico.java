package com.rotavital.servico;

import com.rotavital.api.dto.AtualizarStatusRotaRequest;
import com.rotavital.api.dto.BolsaResponse;
import com.rotavital.api.dto.EmbarqueBolsaRequest;
import com.rotavital.api.dto.RotaCalculadaResponse;
import com.rotavital.api.dto.RotaRequest;
import com.rotavital.api.dto.RotaResponse;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.Rota;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.estruturas.ResultadoRota;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.RotaRepository;
import com.rotavital.servico.evento.BolsaAlterada;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RotaServico {

    private final RotaRepository rotas;
    private final BolsaRepository bolsas;
    private final HemocentroServico hemocentroServico;
    private final HospitalServico hospitalServico;
    private final RoteirizacaoServico roteirizacao;
    private final ApplicationEventPublisher eventos;

    public RotaServico(RotaRepository rotas,
                       BolsaRepository bolsas,
                       HemocentroServico hemocentroServico,
                       HospitalServico hospitalServico,
                       RoteirizacaoServico roteirizacao,
                       ApplicationEventPublisher eventos) {
        this.rotas = rotas;
        this.bolsas = bolsas;
        this.hemocentroServico = hemocentroServico;
        this.hospitalServico = hospitalServico;
        this.roteirizacao = roteirizacao;
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

    /**
     * Planeja uma rota entre um hemocentro e um hospital (HU-05).
     *
     * <p>O caminho mais rapido na malha e calculado sempre, e fica registrado
     * na rota com o tempo estimado. A previsao de chegada segue uma de duas
     * regras:</p>
     * <ul>
     *   <li>informada pelo usuario: vale a informada, desde que posterior a
     *       saida;</li>
     *   <li>omitida: e a saida somada ao tempo do caminho. Se nao ha caminho,
     *       o despacho nao pode ser planejado e a chamada e recusada com a
     *       mensagem da roteirizacao.</li>
     * </ul>
     *
     * <p>O tempo e arredondado para cima ao minuto inteiro, com piso de um
     * minuto: duas unidades no mesmo endereco tem caminho de custo zero, e
     * uma rota que chega no instante em que sai nao faz sentido no
     * calendario.</p>
     */
    @Transactional
    public RotaResponse criar(RotaRequest dados) {
        Hemocentro origem = hemocentroServico.buscar(dados.hemocentroOrigemId());
        Hospital destino = hospitalServico.buscar(dados.hospitalDestinoId());

        ResultadoRota<String> caminho = roteirizacao.caminhoMinimo(origem.getId(), destino.getId());

        LocalDateTime chegada = dados.previsaoChegada();
        if (chegada == null) {
            if (!caminho.alcancavel()) {
                throw new OperacaoInvalidaException(RotaCalculadaResponse.SEM_ROTA
                        + ": " + destino.getNome() + " nao tem trecho na malha a partir de "
                        + origem.getNome());
            }
            long minutos = Math.max(1, (long) Math.ceil(caminho.custoTotal()));
            chegada = dados.previsaoSaida().plusMinutes(minutos);
        }

        if (!chegada.isAfter(dados.previsaoSaida())) {
            throw new OperacaoInvalidaException(
                    "previsaoChegada precisa ser posterior a previsaoSaida");
        }

        Rota rota = new Rota(
                UUID.randomUUID().toString(),
                origem,
                destino,
                dados.previsaoSaida(),
                chegada);
        if (caminho.alcancavel()) {
            rota.registrarCaminho(caminho.caminho(), caminho.custoTotal());
        }

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
        switch (novo) {
            case EM_TRANSITO -> rota.sair(dados.saidaReal() != null ? dados.saidaReal() : LocalDateTime.now());
            case CONCLUIDA -> rota.concluir(dados.chegadaReal() != null ? dados.chegadaReal() : LocalDateTime.now());
            case CANCELADA -> rota.cancelar();
            default -> throw new OperacaoInvalidaException("Transicao invalida: " + atual + " -> " + novo);
        }
        // A rota ja mudou o status das bolsas; aqui so avisamos o indice de
        // estoque, que e infraestrutura e nao pertence ao dominio.
        rota.getBolsas().forEach(b -> eventos.publishEvent(new BolsaAlterada(b)));

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
        Bolsa bolsa = bolsas.findById(dados.bolsaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bolsa", dados.bolsaId()));

        rota.embarcar(bolsa);
        rotas.save(rota);
        return BolsaResponse.de(bolsa);
    }

    @Transactional
    public void desembarcar(String rotaId, String bolsaId) {
        Rota rota = buscar(rotaId);
        rota.desembarcar(bolsaId);
        rotas.save(rota);
    }
}
