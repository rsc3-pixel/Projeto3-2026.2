package com.rotavital.servico;

import com.rotavital.api.dto.RotaCalculadaResponse;
import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.Local;
import com.rotavital.estruturas.Dijkstra;
import com.rotavital.estruturas.Grafo;
import com.rotavital.estruturas.ResultadoRota;
import com.rotavital.rede.CarregadorMalha;
import com.rotavital.rede.UnidadeRede;
import com.rotavital.repositorio.HemocentroRepository;
import com.rotavital.repositorio.HospitalRepository;
import com.rotavital.servico.excecao.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcula o caminho mais rapido entre duas unidades da rede (HU-05).
 *
 * <p>Liga as pecas que ja existiam: o {@link Grafo} e o {@link Dijkstra} da
 * AED, e as premissas de malha de {@link CarregadorMalha}. A diferenca para
 * a demonstracao da PI3-61 e a fonte dos vertices: aqui a malha e montada a
 * partir dos hemocentros e hospitais <b>que estao no banco</b>, com as
 * coordenadas do endereco de cada um, e nao da lista fixa das 12 unidades.
 * Uma unidade cadastrada pela API entra na malha na consulta seguinte.</p>
 *
 * <p>O peso de cada trecho e tempo estimado em minutos, nao distancia: e a
 * decisao da secao 3 do escopo do grafo, e o que a historia pede ("o caminho
 * mais rapido, nao o mais curto").</p>
 *
 * <p>Destino sem caminho e resposta tratada, nunca excecao: a malha e
 * metropolitana e uma unidade fora do alcance simplesmente nao tem trecho.
 * Quem chama decide o que fazer com isso; o {@link RotaServico}, por
 * exemplo, recusa planejar o despacho.</p>
 *
 * <p>A malha e remontada a cada chamada. Com algumas dezenas de unidades o
 * custo e desprezivel, e assim nao ha cache para ficar defasado quando uma
 * unidade e cadastrada ou removida.</p>
 */
@Service
public class RoteirizacaoServico {

    private final HemocentroRepository hemocentros;
    private final HospitalRepository hospitais;

    public RoteirizacaoServico(HemocentroRepository hemocentros, HospitalRepository hospitais) {
        this.hemocentros = hemocentros;
        this.hospitais = hospitais;
    }

    /**
     * O caminho mais rapido entre duas unidades, com as paradas nomeadas.
     *
     * @throws RecursoNaoEncontradoException se a origem ou o destino nao
     *         existirem como hemocentro nem como hospital
     */
    @Transactional(readOnly = true)
    public RotaCalculadaResponse calcular(String origemId, String destinoId) {
        Map<String, Local> unidades = unidadesDaRede();
        Local origem = unidadeOuErro(unidades, origemId);
        Local destino = unidadeOuErro(unidades, destinoId);

        ResultadoRota<String> rota = caminhoMinimo(unidades, origem.getId(), destino.getId());
        return RotaCalculadaResponse.de(origem, destino, rota, unidades);
    }

    /**
     * O caminho minimo bruto, para quem precisa do tempo e das paradas sem o
     * DTO: e o que o {@link RotaServico} usa ao planejar o despacho.
     *
     * @return {@link ResultadoRota#semCaminho()} se nao houver trecho ligando
     *         as duas, ou se alguma delas nao estiver no banco
     */
    @Transactional(readOnly = true)
    public ResultadoRota<String> caminhoMinimo(String origemId, String destinoId) {
        return caminhoMinimo(unidadesDaRede(), origemId, destinoId);
    }

    private ResultadoRota<String> caminhoMinimo(Map<String, Local> unidades,
                                                String origemId, String destinoId) {
        Grafo<String> malha = CarregadorMalha.carregar(comoUnidadesDeRede(unidades));
        return new Dijkstra<>(malha).calcularRota(origemId, destinoId);
    }

    /**
     * Hemocentros e hospitais do banco, por id, na ordem em que vieram. A
     * ordem importa para o desempate da malha ser reproduzivel.
     */
    private Map<String, Local> unidadesDaRede() {
        Map<String, Local> unidades = new LinkedHashMap<>();
        for (Hemocentro h : hemocentros.findAll()) {
            unidades.put(h.getId(), h);
        }
        for (Hospital h : hospitais.findAll()) {
            unidades.put(h.getId(), h);
        }
        return unidades;
    }

    /**
     * Converte as entidades para o registro que o carregador da malha
     * entende. Unidade sem endereco nao tem coordenada e fica fora da malha:
     * sem posicao nao ha como estimar trecho ate ela.
     */
    private static List<UnidadeRede> comoUnidadesDeRede(Map<String, Local> unidades) {
        List<UnidadeRede> lista = new ArrayList<>();
        for (Local local : unidades.values()) {
            Endereco endereco = local.getEndereco();
            if (endereco == null) {
                continue;
            }
            lista.add(new UnidadeRede(
                    local.getId(),
                    local.getNome(),
                    tipoDe(local),
                    endereco.getCidade(),
                    endereco.getBairro(),
                    endereco.getLatitude(),
                    endereco.getLongitude()));
        }
        return lista;
    }

    /** Vocabulario da API: HEMOCENTRO ou HOSPITAL, como os proprios recursos. */
    public static String tipoDe(Local local) {
        return local instanceof Hemocentro ? "HEMOCENTRO" : "HOSPITAL";
    }

    private static Local unidadeOuErro(Map<String, Local> unidades, String id) {
        Local local = id == null ? null : unidades.get(id);
        if (local == null) {
            throw new RecursoNaoEncontradoException("Unidade da rede", String.valueOf(id));
        }
        return local;
    }
}
