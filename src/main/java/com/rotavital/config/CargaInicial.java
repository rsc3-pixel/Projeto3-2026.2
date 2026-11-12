package com.rotavital.config;

import com.rotavital.dominio.Bolsa;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.ItemRequisicao;
import com.rotavital.dominio.Requisicao;
import com.rotavital.dominio.enums.GrupoSanguineo;
import com.rotavital.dominio.enums.PrioridadeRequisicao;
import com.rotavital.dominio.enums.TipoHemocomponente;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.HemocentroRepository;
import com.rotavital.repositorio.HospitalRepository;
import com.rotavital.repositorio.RequisicaoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * Popula o banco com dados sinteticos ao subir a aplicacao.
 *
 * <p>O H2 e em memoria: sem isso a API subiria vazia a cada execucao e nao
 * daria para testar nada sem cadastrar tudo na mao.</p>
 *
 * <p>As unidades sao as mesmas de {@code dados/unidades.py}, usadas na analise
 * estatistica, para que o backend e o CRISP-DM falem da mesma rede. Os dados
 * de bolsa e requisicao sao sinteticos, sem qualquer informacao real de doador
 * ou paciente (LGPD).</p>
 *
 * <p>Nao roda no perfil {@code test}: teste que depende de carga previa fica
 * fragil, cada um monta o proprio cenario.</p>
 *
 * <p><b>Roda em producao de proposito.</b> Carga automatica em producao
 * normalmente e erro grave, mas aqui o banco e H2 em memoria: ele sobe vazio a
 * cada restart, e sem a carga a URL publica responderia listas vazias. Quando
 * o PostgreSQL entrar (Entrega 02), o banco passa a ser persistente e esta
 * anotacao vira {@code @Profile("!test & !prod")}.</p>
 */
@Component
@Profile("!test")
public class CargaInicial implements CommandLineRunner {

    /** Semente fixa: a mesma rede a cada execucao, igual ao script Python. */
    private static final long SEMENTE = 42;

    private static final int BOLSAS_POR_HEMOCENTRO = 25;

    private final HemocentroRepository hemocentros;
    private final HospitalRepository hospitais;
    private final BolsaRepository bolsas;
    private final RequisicaoRepository requisicoes;

    public CargaInicial(HemocentroRepository hemocentros,
                        HospitalRepository hospitais,
                        BolsaRepository bolsas,
                        RequisicaoRepository requisicoes) {
        this.hemocentros = hemocentros;
        this.hospitais = hospitais;
        this.bolsas = bolsas;
        this.requisicoes = requisicoes;
    }

    @Override
    public void run(String... args) {
        if (hemocentros.count() > 0) {
            return;
        }

        Random sorteio = new Random(SEMENTE);

        List<Hemocentro> criados = criarHemocentros();
        hemocentros.saveAll(criados);

        List<Hospital> hospitaisCriados = criarHospitais();
        hospitais.saveAll(hospitaisCriados);

        criarEstoque(criados, sorteio);
        criarRequisicoes(hospitaisCriados, sorteio);
    }

    private List<Hemocentro> criarHemocentros() {
        return List.of(
                new Hemocentro("HC01", "Hemocentro Recife (HEMOPE)", "(81) 3182-4600",
                        endereco("Rua Joaquim Nabuco", "171", "Gracas", "Recife", "PE",
                                "52011-000", -8.0528651, -34.8979716),
                        "11222333000181"),
                new Hemocentro("HC02", "GSH Banco de Sangue Hemato", "(81) 3421-3000",
                        endereco("Rua do Hospicio", "371", "Boa Vista", "Recife", "PE",
                                "50050-050", -8.0597226, -34.8941120),
                        "11222333000182"),
                new Hemocentro("HC03", "IHENE - Instituto de Hematologia do Nordeste", "(81) 3421-5544",
                        endereco("Rua Amaro Bezerra", "140", "Boa Vista", "Recife", "PE",
                                "50050-160", -8.0503551, -34.8928547),
                        "11222333000183"),
                new Hemocentro("HC08", "Hemolab Laboratorio", "(81) 3432-2200",
                        endereco("Avenida Carlos de Lima Cavalcanti", "900", "Rio Doce", "Olinda", "PE",
                                "53130-410", -7.9619962, -34.8434754),
                        "11222333000184"));
    }

    private List<Hospital> criarHospitais() {
        return List.of(
                new Hospital("HO04", "Real Hospital Portugues de Beneficencia", "(81) 3416-1122",
                        endereco("Avenida Portugal", "163", "Paissandu", "Recife", "PE",
                                "52010-100", -8.0640504, -34.8981508),
                        "2400001"),
                new Hospital("HO05", "IMIP - Instituto de Medicina Integral", "(81) 2122-4100",
                        endereco("Rua dos Coelhos", "300", "Boa Vista", "Recife", "PE",
                                "50070-550", -8.0669066, -34.8902756),
                        "2400002"),
                new Hospital("HO06", "Hospital das Clinicas UFPE", "(81) 2126-3600",
                        endereco("Avenida Professor Moraes Rego", "1235", "Cidade Universitaria",
                                "Recife", "PE", "50670-901", -8.0474352, -34.9463152),
                        "2400003"),
                new Hospital("HO07", "Hospital do Tricentenario", "(81) 3493-4700",
                        endereco("Rua Doutor Jose Rufino", "550", "Bairro Novo", "Olinda", "PE",
                                "53030-030", -8.0104411, -34.8443412),
                        "2400004"),
                new Hospital("HO09", "Hospital Nossa Senhora de Lourdes", "(81) 3372-1100",
                        endereco("Avenida Barreto de Menezes", "800", "Cavaleiro",
                                "Jaboatao dos Guararapes", "PE", "54250-000", -8.0991939, -34.9703409),
                        "2400005"),
                new Hospital("HO11", "Hospital Guararapes (Memorial)", "(81) 3471-9000",
                        endereco("Avenida Bernardo Vieira de Melo", "1400", "Prazeres",
                                "Jaboatao dos Guararapes", "PE", "54325-000", -8.1657849, -34.9328210),
                        "2400006"));
    }

    /**
     * Distribui bolsas pelos hemocentros. A data de coleta e sorteada dentro da
     * validade do proprio componente, entao nenhuma bolsa nasce vencida: o
     * estoque inicial e utilizavel.
     */
    private void criarEstoque(List<Hemocentro> unidades, Random sorteio) {
        GrupoSanguineo[] grupos = GrupoSanguineo.values();
        TipoHemocomponente[] tipos = TipoHemocomponente.values();

        int codigo = 1;
        for (Hemocentro hemocentro : unidades) {
            for (int i = 0; i < BOLSAS_POR_HEMOCENTRO; i++) {
                TipoHemocomponente tipo = tipos[sorteio.nextInt(tipos.length)];
                GrupoSanguineo grupo = grupos[sorteio.nextInt(grupos.length)];

                int diasAtras = sorteio.nextInt(Math.max(1, tipo.getValidadeDias() / 2));
                int volume = 200 + sorteio.nextInt(201);

                bolsas.save(new Bolsa(
                        String.format("BOL%05d", codigo++),
                        tipo,
                        grupo,
                        volume,
                        LocalDate.now().minusDays(diasAtras),
                        hemocentro));
            }
        }
    }

    private void criarRequisicoes(List<Hospital> unidades, Random sorteio) {
        GrupoSanguineo[] grupos = GrupoSanguineo.values();
        TipoHemocomponente[] tipos = TipoHemocomponente.values();
        PrioridadeRequisicao[] prioridades = PrioridadeRequisicao.values();

        int codigo = 1;
        for (Hospital hospital : unidades) {
            int quantasRequisicoes = 1 + sorteio.nextInt(2);

            for (int r = 0; r < quantasRequisicoes; r++) {
                PrioridadeRequisicao prioridade = prioridades[sorteio.nextInt(prioridades.length)];

                Requisicao requisicao = new Requisicao(
                        String.format("REQ%05d", codigo++),
                        hospital,
                        prioridade,
                        LocalDateTime.now().minusHours(sorteio.nextInt(48)),
                        // O prazo vem da propria prioridade, a mesma regra que
                        // o RequisicaoServico aplica quando o hospital nao
                        // informa a data (HU-02).
                        LocalDateTime.now().plusHours(prioridade.getPrazoHoras()));

                int quantosItens = 1 + sorteio.nextInt(3);
                for (int i = 0; i < quantosItens; i++) {
                    requisicao.adicionarItem(new ItemRequisicao(
                            requisicao.getId() + "-I" + (i + 1),
                            tipos[sorteio.nextInt(tipos.length)],
                            grupos[sorteio.nextInt(grupos.length)],
                            1 + sorteio.nextInt(4)));
                }

                requisicoes.save(requisicao);
            }
        }
    }

    private Endereco endereco(String logradouro, String numero, String bairro, String cidade,
                              String uf, String cep, double latitude, double longitude) {
        return new Endereco(logradouro, numero, bairro, cidade, uf, cep, latitude, longitude);
    }
}
