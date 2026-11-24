package com.rotavital.aplicacao;

import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.MapeadorEndereco;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.repositorio.HospitalRepository;
import com.rotavital.dominio.repositorio.RequisicaoRepository;
import com.rotavital.dominio.repositorio.RotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Casos de uso do hospital. O comum a toda unidade vem de
 * {@link ServicoDeUnidade}; aqui fica o CNES e o que impede a remocao.
 */
@Service
public class HospitalServico extends ServicoDeUnidade<Hospital> {

    private final HospitalRepository hospitais;
    private final RequisicaoRepository requisicoes;
    private final RotaRepository rotas;

    public HospitalServico(HospitalRepository hospitais,
                           RequisicaoRepository requisicoes,
                           RotaRepository rotas) {
        super(hospitais, "Hospital", "CNES");
        this.hospitais = hospitais;
        this.requisicoes = requisicoes;
        this.rotas = rotas;
    }

    @Transactional
    public Hospital criar(HospitalRequest dados) {
        return cadastrar(new Hospital(
                UUID.randomUUID().toString(),
                dados.nome(),
                dados.telefone(),
                MapeadorEndereco.paraDominio(dados.endereco()),
                dados.cnes()));
    }

    @Transactional
    public Hospital atualizar(String id, HospitalRequest dados) {
        return atualizarCadastro(id, dados.nome(), dados.telefone(),
                MapeadorEndereco.paraDominio(dados.endereco()), dados.cnes());
    }

    @Override
    protected boolean registroEmUso(String cnes) {
        return hospitais.existsByCnes(cnes);
    }

    /** Hospital com requisicoes ou rotas de destino perderia o historico de atendimento. */
    @Override
    protected void exigirSemVinculos(String id) {
        if (requisicoes.countByHospitalId(id) > 0) {
            throw new OperacaoInvalidaException(
                    "Hospital possui requisicoes cadastradas e nao pode ser removido");
        }
        if (rotas.countByDestinoId(id) > 0) {
            throw new OperacaoInvalidaException(
                    "Hospital possui rotas vinculadas e nao pode ser removido");
        }
    }
}
