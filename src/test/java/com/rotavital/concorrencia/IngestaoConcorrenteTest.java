package com.rotavital.concorrencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.rotavital.api.dto.BolsaRequest;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.servico.IngestaoConcorrenteServico;
import com.rotavital.servico.IngestaoConcorrenteServico.ResultadoIngestao;

/**
 * Entrada concorrente de bolsas no estoque (PI3-30).
 */
@SpringBootTest
@DisplayName("PI3-30 · Ingestao concorrente de bolsas")
class IngestaoConcorrenteTest {

    @Autowired
    private IngestaoConcorrenteServico ingestao;

    @Autowired
    private BolsaRepository bolsas;

    @Nested
    @DisplayName("Um lote valido")
    class LoteValido {

        @Test
        @DisplayName("Dado um lote de bolsas validas, Quando entram em paralelo, "
                + "Entao todas sao gravadas")
        void todasEntram() {
            long antes = bolsas.count();
            List<BolsaRequest> lote = loteDe(12);

            ResultadoIngestao r = ingestao.ingerir(lote);

            assertEquals(12, r.aceitas().size(),
                    "todas as bolsas validas deveriam entrar; recusadas: "
                            + r.recusadas());
            assertTrue(r.recusadas().isEmpty());
            assertEquals(antes + 12, bolsas.count(),
                    "o banco deveria ter as doze bolsas novas");
        }

        @Test
        @DisplayName("Dado um lote, Quando entra em paralelo, Entao nenhum codigo "
                + "de rastreio se repete")
        void codigosNaoColidem() {
            // Gerar codigo em paralelo e onde uma corrida apareceria: duas
            // threads tirando o mesmo numero de sequencia.
            ResultadoIngestao r = ingestao.ingerir(loteDe(20));

            long distintos = r.aceitas().stream().distinct().count();
            assertEquals(r.aceitas().size(), distintos,
                    "codigo de rastreio repetido indica corrida na geracao");
        }
    }

    @Nested
    @DisplayName("Um lote com bolsa invalida")
    class LoteComProblema {

        @Test
        @DisplayName("Dado um lote com uma bolsa invalida, Quando entra em paralelo, "
                + "Entao as validas entram e so a invalida e recusada")
        void umaRecusaNaoDerrubaOLote() {
            long antes = bolsas.count();

            List<BolsaRequest> lote = new java.util.ArrayList<>(loteDe(5));

            // Hemocentro inexistente: o BolsaServico recusa ao buscar a origem.
            //
            // A primeira tentativa foi com data de coleta futura, e ela passou:
            // essa regra vive na anotacao @PastOrPresent do DTO, validada pelo
            // @Valid no controller, e nao no servico. Chamando o servico direto
            // ela nao roda.
            lote.add(new BolsaRequest(
                    "HC-INEXISTENTE", TipoHemocomponente.CONCENTRADO_HEMACIAS,
                    GrupoSanguineo.O_NEG, 450, LocalDate.now()));

            ResultadoIngestao r = ingestao.ingerir(lote);

            assertEquals(5, r.aceitas().size(),
                    "as cinco validas deveriam entrar mesmo com uma recusada");
            assertEquals(1, r.recusadas().size());
            assertFalse(r.recusadas().get(0).isBlank(),
                    "a recusa deveria dizer o motivo");
            assertEquals(antes + 5, bolsas.count(),
                    "so as validas deveriam estar no banco");
        }
    }

    /** Bolsas validas, coletadas hoje, todas no mesmo hemocentro. */
    private List<BolsaRequest> loteDe(int quantas) {
        return IntStream.range(0, quantas)
                .mapToObj(i -> new BolsaRequest(
                        "HC01",
                        TipoHemocomponente.CONCENTRADO_HEMACIAS,
                        GrupoSanguineo.values()[i % GrupoSanguineo.values().length],
                        450,
                        LocalDate.now()))
                .toList();
    }
}
