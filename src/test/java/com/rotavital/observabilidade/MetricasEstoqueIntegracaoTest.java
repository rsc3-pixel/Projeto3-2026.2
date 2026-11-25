package com.rotavital.observabilidade;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.dominio.repositorio.BolsaRepository;
import com.rotavital.dominio.repositorio.HemocentroRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gauges de estoque contra o banco de verdade (H2 do perfil de teste): as
 * consultas JPQL agrupadas rodam e os numeros acompanham o que foi gravado.
 *
 * <p>Compara antes e depois, em vez de valores absolutos, porque o contexto do
 * Spring e compartilhado entre classes de teste. Dados sinteticos (LGPD).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MetricasEstoqueIntegracaoTest {

    @Autowired
    private MeterRegistry registro;

    @Autowired
    private MetricasEstoque metricasEstoque;

    @Autowired
    private BolsaRepository bolsas;

    @Autowired
    private HemocentroRepository hemocentros;

    private Hemocentro hemocentro;

    @BeforeEach
    void preparar() {
        hemocentro = hemocentros.save(new Hemocentro("hem-metricas", "Hemocentro Sintetico Metricas",
                "(81) 3000-0000",
                new Endereco("Rua Sintetica", "1", "Centro", "Recife", "PE", "50000-000",
                        -8.05, -34.90),
                "00000000000191"));
    }

    private double gauge(String nome, String... tags) {
        metricasEstoque.expirarCache();
        return registro.get(nome).tags(tags).gauge().value();
    }

    private Bolsa bolsa(String codigo, TipoHemocomponente tipo, LocalDate coleta) {
        return bolsas.save(new Bolsa(codigo, tipo, GrupoSanguineo.O_NEG, 450, coleta, hemocentro));
    }

    @Test
    @DisplayName("Toda metrica sai com a tag application=rota-vital, a de dominio e a do Spring")
    void tagApplicationEmTodasAsMetricas() {
        assertThat(registro.find(MetricasEstoque.BOLSAS).tag("application", "rota-vital").gauges())
                .isNotEmpty();
        assertThat(registro.find("rotavital.telemetria.leituras").tag("application", "rota-vital").counters())
                .isNotEmpty();
        assertThat(registro.find("jvm.memory.used").tag("application", "rota-vital").gauges())
                .isNotEmpty();
    }

    @Test
    @DisplayName("Bolsa disponivel nova entra na serie do seu tipo e status")
    void bolsaDisponivelEntraNaContagem() {
        String[] tags = {"tipo", "CONCENTRADO_HEMACIAS", "status", "DISPONIVEL"};
        double antes = gauge(MetricasEstoque.BOLSAS, tags);

        bolsa("MET-001", TipoHemocomponente.CONCENTRADO_HEMACIAS, LocalDate.now());
        bolsa("MET-002", TipoHemocomponente.CONCENTRADO_HEMACIAS, LocalDate.now());

        assertThat(gauge(MetricasEstoque.BOLSAS, tags)).isEqualTo(antes + 2);
    }

    @Test
    @DisplayName("Mudar o status move a bolsa de uma serie para outra")
    void statusMoveEntreSeries() {
        String[] disponivel = {"tipo", "CRIOPRECIPITADO", "status", "DISPONIVEL"};
        String[] descartada = {"tipo", "CRIOPRECIPITADO", "status", "DESCARTADA"};
        Bolsa crio = bolsa("MET-010", TipoHemocomponente.CRIOPRECIPITADO, LocalDate.now());
        double disponivelAntes = gauge(MetricasEstoque.BOLSAS, disponivel);
        double descartadaAntes = gauge(MetricasEstoque.BOLSAS, descartada);

        crio.descartar();
        bolsas.save(crio);

        assertThat(gauge(MetricasEstoque.BOLSAS, disponivel)).isEqualTo(disponivelAntes - 1);
        assertThat(gauge(MetricasEstoque.BOLSAS, descartada)).isEqualTo(descartadaAntes + 1);
    }

    @Test
    @DisplayName("Plaquetas que vencem em 2 dias contam como 'vencendo'; hemacias vencidas, como vencidas")
    void vencendoEVencidas() {
        double vencendoAntes = gauge(MetricasEstoque.VENCENDO, "tipo", "CONCENTRADO_PLAQUETAS");
        double vencidasAntes = gauge(MetricasEstoque.VENCIDAS_DISPONIVEIS, "tipo", "CONCENTRADO_HEMACIAS");

        // Plaquetas duram 5 dias: coletada ha 3 dias, vence em 2.
        bolsa("MET-020", TipoHemocomponente.CONCENTRADO_PLAQUETAS, LocalDate.now().minusDays(3));
        // Hemacias duram 42 dias: coletada ha 50, venceu ha 8 e segue DISPONIVEL.
        bolsa("MET-021", TipoHemocomponente.CONCENTRADO_HEMACIAS, LocalDate.now().minusDays(50));

        assertThat(gauge(MetricasEstoque.VENCENDO, "tipo", "CONCENTRADO_PLAQUETAS"))
                .isEqualTo(vencendoAntes + 1);
        assertThat(gauge(MetricasEstoque.VENCIDAS_DISPONIVEIS, "tipo", "CONCENTRADO_HEMACIAS"))
                .isEqualTo(vencidasAntes + 1);
    }
}
