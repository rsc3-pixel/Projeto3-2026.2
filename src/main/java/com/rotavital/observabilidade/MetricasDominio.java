package com.rotavital.observabilidade;

import com.rotavital.dominio.enums.TipoHemocomponente;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;

/**
 * Contadores de negocio do Rota Vital, lidos pelo Prometheus em
 * {@code /actuator/prometheus} e mostrados no dashboard do Grafana.
 *
 * <p>As metricas tecnicas (latencia HTTP, JVM, pool de conexoes) o Spring Boot
 * ja publica sozinho. Estas respondem perguntas do dominio que nenhuma
 * metrica tecnica responde: quantas leituras de temperatura sairam da faixa,
 * e de qual componente; quantas alocacoes perderam a disputa por uma bolsa.</p>
 *
 * <p><strong>Cardinalidade baixa de proposito.</strong> As tags so recebem
 * valores de conjuntos fechados (enum, resultado, modo). Nunca id de rota,
 * de bolsa ou de hospital: cada valor distinto de tag vira uma serie nova no
 * Prometheus, e uma tag por rota cresceria sem limite.</p>
 *
 * <p><strong>Telemetria simulada.</strong> As leituras contadas aqui vem do
 * simulador (PI3-112), nao de sensores reais.</p>
 */
@Component
public class MetricasDominio {

    static final String LEITURAS = "rotavital.telemetria.leituras";
    static final String EXCURSOES = "rotavital.telemetria.excursoes";
    static final String ALOCACOES = "rotavital.alocacoes";
    static final String REPETICOES = "rotavital.alocacoes.repeticoes";
    static final String DURACAO = "rotavital.alocacoes.duracao";

    /** Por onde a alocacao entrou: bolsa escolhida pelo usuario ou pelo sistema (FEFO). */
    public enum ModoAlocacao {
        MANUAL, AUTOMATICA;

        String tag() {
            return name().toLowerCase();
        }
    }

    /** Como a tentativa de alocacao terminou. */
    public enum ResultadoAlocacao {
        /** Alocou na primeira tentativa. */
        SUCESSO,
        /** Alocou depois de perder ao menos uma disputa de concorrencia (PI3-108). */
        SUCESSO_APOS_REPETICAO,
        /** Perdeu a disputa em todas as tentativas; o cliente recebe 409. */
        CONFLITO_CONCORRENCIA,
        /** Regra de negocio negou: sem estoque, incompativel, item atendido, nao encontrado. */
        RECUSADA;

        String tag() {
            return name().toLowerCase();
        }
    }

    private final MeterRegistry registro;

    public MetricasDominio(MeterRegistry registro) {
        this.registro = registro;
        registrarSeriesZeradas();
    }

    /**
     * Conta uma leitura de temperatura e, se houver, uma excursao por
     * componente fora da faixa.
     *
     * @param componentesEmExcursao componentes a bordo para os quais a leitura
     *                              ficou fora da faixa; vazio se estava tudo certo
     */
    public void leituraTemperatura(Collection<TipoHemocomponente> componentesEmExcursao) {
        String resultado = componentesEmExcursao.isEmpty() ? "dentro_da_faixa" : "excursao";
        leituras(resultado).increment();
        for (TipoHemocomponente componente : componentesEmExcursao) {
            excursoes(componente).increment();
        }
    }

    public void alocacao(ModoAlocacao modo, ResultadoAlocacao resultado) {
        alocacoes(modo, resultado).increment();
    }

    /** Marca o inicio de uma alocacao, para medir quanto ela leva. */
    public Timer.Sample iniciarAlocacao() {
        return Timer.start(registro);
    }

    /**
     * Conta a alocacao e registra quanto tempo ela levou desde
     * {@link #iniciarAlocacao()}, incluindo as repeticoes por concorrencia.
     */
    public void alocacao(ModoAlocacao modo, ResultadoAlocacao resultado, Timer.Sample inicio) {
        alocacao(modo, resultado);
        inicio.stop(duracao(modo, resultado));
    }

    /** Uma tentativa perdeu a disputa por concorrencia e sera repetida. */
    public void repeticaoAlocacao() {
        repeticoes().increment();
    }

    /**
     * Classifica a excecao de uma alocacao que falhou.
     *
     * <p>Conflito de concorrencia e diferente de recusa: o primeiro indica
     * disputa entre usuarios (sinal de carga), o segundo indica regra de negocio
     * (sinal de estoque ou de uso).</p>
     */
    public static ResultadoAlocacao classificar(RuntimeException erro) {
        return erro instanceof OptimisticLockingFailureException
                ? ResultadoAlocacao.CONFLITO_CONCORRENCIA
                : ResultadoAlocacao.RECUSADA;
    }

    /**
     * Cria todas as series com valor zero na partida.
     *
     * <p>Sem isso, um contador so aparece no Prometheus depois do primeiro
     * incremento, e o painel mostra "No data" em vez de 0. Pior: a funcao
     * {@code increase()} perde o primeiro evento, porque nao ha valor anterior
     * para comparar.</p>
     */
    private void registrarSeriesZeradas() {
        leituras("dentro_da_faixa");
        leituras("excursao");
        for (TipoHemocomponente componente : TipoHemocomponente.values()) {
            excursoes(componente);
        }
        for (ModoAlocacao modo : ModoAlocacao.values()) {
            for (ResultadoAlocacao resultado : ResultadoAlocacao.values()) {
                alocacoes(modo, resultado);
                duracao(modo, resultado);
            }
        }
        repeticoes();
    }

    private Counter leituras(String resultado) {
        return Counter.builder(LEITURAS)
                .description("Leituras de temperatura recebidas (telemetria simulada)")
                .tag("resultado", resultado)
                .register(registro);
    }

    private Counter excursoes(TipoHemocomponente componente) {
        return Counter.builder(EXCURSOES)
                .description("Leituras fora da faixa de temperatura, por componente a bordo")
                .tag("componente", componente.name())
                .register(registro);
    }

    private Counter alocacoes(ModoAlocacao modo, ResultadoAlocacao resultado) {
        return Counter.builder(ALOCACOES)
                .description("Tentativas de alocacao de bolsa a item de requisicao, por resultado")
                .tag("modo", modo.tag())
                .tag("resultado", resultado.tag())
                .register(registro);
    }

    /**
     * Tempo de uma alocacao, do pedido a resposta. Com histograma, para o
     * Grafana calcular o p95. Faixa esperada de 1 ms a 5 s: limita a
     * quantidade de baldes do histograma (series no Prometheus).
     */
    private Timer duracao(ModoAlocacao modo, ResultadoAlocacao resultado) {
        return Timer.builder(DURACAO)
                .description("Tempo de uma alocacao de bolsa, incluindo repeticoes por concorrencia")
                .tag("modo", modo.tag())
                .tag("resultado", resultado.tag())
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registro);
    }

    private Counter repeticoes() {
        return Counter.builder(REPETICOES)
                .description("Tentativas de alocacao repetidas apos conflito de concorrencia")
                .register(registro);
    }
}
