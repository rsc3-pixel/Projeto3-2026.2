package com.rotavital.servico;

import com.rotavital.api.dto.LeituraTemperaturaRequest;
import com.rotavital.api.dto.LeituraTemperaturaResponse;
import com.rotavital.api.dto.LeituraTemperaturaResponse.AlertaCadeiaFria;
import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.LeituraTemperatura;
import com.rotavital.dominio.Rota;
import com.rotavital.dominio.enums.StatusRota;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.LeituraTemperaturaRepository;
import com.rotavital.repositorio.RotaRepository;
import com.rotavital.servico.excecao.OperacaoInvalidaException;
import com.rotavital.servico.excecao.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monitora a temperatura das remessas em transito (HU-07).
 *
 * <p><strong>A telemetria e simulada nesta fase do projeto.</strong> O
 * servico registra o que um sensor reportaria e avalia a leitura; nao ha
 * integracao com hardware.</p>
 *
 * <p>A regra central: <strong>a faixa aceitavel e a do componente
 * transportado, nao um valor unico</strong>. Hemacias exigem de 1 a 6 graus,
 * plaquetas de 20 a 24, plasma de -25 a -18. Uma remessa pode levar tipos
 * diferentes no mesmo compartimento, e nesse caso a mesma leitura pode estar
 * correta para um componente e ser excursao para outro.</p>
 */
@Service
public class TemperaturaServico {

    private final RotaRepository rotas;
    private final LeituraTemperaturaRepository leituras;

    public TemperaturaServico(RotaRepository rotas, LeituraTemperaturaRepository leituras) {
        this.rotas = rotas;
        this.leituras = leituras;
    }

    /**
     * Registra uma leitura e devolve a avaliacao contra cada componente
     * embarcado.
     *
     * @throws RecursoNaoEncontradoException se a rota nao existir
     * @throws OperacaoInvalidaException     se a rota nao estiver em transito
     */
    @Transactional
    public LeituraTemperaturaResponse registrar(String rotaId, LeituraTemperaturaRequest dados) {
        Rota rota = rotas.findById(rotaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Rota", rotaId));

        // Leitura de sensor so faz sentido durante o transporte. Aceitar em
        // rota PLANEJADA ou CONCLUIDA poluiria o historico com dados que nao
        // correspondem a nenhuma viagem.
        if (rota.getStatus() != StatusRota.EM_TRANSITO) {
            throw new OperacaoInvalidaException(
                    "Leitura de temperatura so e aceita em rota EM_TRANSITO. "
                    + "Status atual: " + rota.getStatus());
        }

        LeituraTemperatura leitura = new LeituraTemperatura(
                UUID.randomUUID().toString(), rota,
                dados.temperaturaCelsius(), LocalDateTime.now());

        leituras.save(leitura);

        return avaliar(leitura);
    }

    /**
     * Historico de leituras da remessa, do mais recente para o mais antigo,
     * cada uma ja avaliada.
     */
    @Transactional(readOnly = true)
    public List<LeituraTemperaturaResponse> historico(String rotaId) {
        if (!rotas.existsById(rotaId)) {
            throw new RecursoNaoEncontradoException("Rota", rotaId);
        }
        return leituras.findByRotaIdOrderByDataHoraDesc(rotaId).stream()
                .map(this::avaliar)
                .toList();
    }

    /**
     * Compara a leitura com a faixa de cada componente embarcado.
     *
     * <p>A conversao para DTO acontece aqui dentro, e nao no controlador,
     * porque percorrer {@code rota.getBolsas()} exige a sessao do JPA aberta.
     * Com {@code open-in-view} desligado no perfil de producao, fazer isso
     * fora da transacao lancaria {@code LazyInitializationException}.</p>
     */
    private LeituraTemperaturaResponse avaliar(LeituraTemperatura leitura) {
        Rota rota = leitura.getRota();
        double temperatura = leitura.getTemperaturaCelsius();

        // Conta as bolsas por componente antes de avaliar, para o alerta
        // informar quantas estao em risco. LinkedHashMap preserva a ordem de
        // embarque, deixando a resposta estavel entre chamadas.
        Map<TipoHemocomponente, Integer> porComponente = new LinkedHashMap<>();
        for (Bolsa bolsa : rota.getBolsas()) {
            porComponente.merge(bolsa.getTipo(), 1, Integer::sum);
        }

        List<AlertaCadeiaFria> alertas = new ArrayList<>();
        for (Map.Entry<TipoHemocomponente, Integer> entrada : porComponente.entrySet()) {
            TipoHemocomponente componente = entrada.getKey();
            if (!componente.temperaturaAdequada(temperatura)) {
                alertas.add(AlertaCadeiaFria.de(componente, entrada.getValue()));
            }
        }

        return new LeituraTemperaturaResponse(
                leitura.getId(),
                rota.getId(),
                temperatura,
                leitura.getDataHora(),
                alertas.isEmpty(),
                alertas);
    }
}
