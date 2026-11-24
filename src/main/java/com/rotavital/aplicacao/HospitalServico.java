package com.rotavital.aplicacao;

import com.rotavital.api.dto.HospitalRequest;
import com.rotavital.api.dto.MapeadorEndereco;
import com.rotavital.dominio.Hospital;
import com.rotavital.dominio.repositorio.HospitalRepository;
import com.rotavital.dominio.repositorio.RequisicaoRepository;
import com.rotavital.dominio.repositorio.RotaRepository;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class HospitalServico {

    private final HospitalRepository hospitais;
    private final RequisicaoRepository requisicoes;
    private final RotaRepository rotas;

    public HospitalServico(HospitalRepository hospitais,
                           RequisicaoRepository requisicoes,
                           RotaRepository rotas) {
        this.hospitais = hospitais;
        this.requisicoes = requisicoes;
        this.rotas = rotas;
    }

    @Transactional(readOnly = true)
    public List<Hospital> listar(String cidade, String uf) {
        if (cidade != null && uf != null) {
            return hospitais.findByEnderecoCidadeIgnoreCaseAndEnderecoEstadoIgnoreCase(cidade, uf);
        }
        if (cidade != null) {
            return hospitais.findByEnderecoCidadeIgnoreCase(cidade);
        }
        if (uf != null) {
            return hospitais.findByEnderecoEstadoIgnoreCase(uf);
        }
        return hospitais.findAll();
    }

    @Transactional(readOnly = true)
    public Hospital buscar(String id) {
        return hospitais.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Hospital", id));
    }

    @Transactional
    public Hospital criar(HospitalRequest dados) {
        if (hospitais.existsByCnes(dados.cnes())) {
            throw new OperacaoInvalidaException("Ja existe hospital com o CNES " + dados.cnes());
        }
        Hospital hospital = new Hospital(
                UUID.randomUUID().toString(),
                dados.nome(),
                dados.telefone(),
                MapeadorEndereco.paraDominio(dados.endereco()),
                dados.cnes());
        return hospitais.save(hospital);
    }

    @Transactional
    public Hospital atualizar(String id, HospitalRequest dados) {
        Hospital hospital = buscar(id);

        boolean cnesMudou = !hospital.getCnes().equals(dados.cnes());
        if (cnesMudou && hospitais.existsByCnes(dados.cnes())) {
            throw new OperacaoInvalidaException("Ja existe hospital com o CNES " + dados.cnes());
        }

        hospital.setNome(dados.nome());
        hospital.setTelefone(dados.telefone());
        hospital.setCnes(dados.cnes());
        hospital.setEndereco(MapeadorEndereco.paraDominio(dados.endereco()));
        return hospitais.save(hospital);
    }

    @Transactional
    public void remover(String id) {
        Hospital hospital = buscar(id);

        if (requisicoes.countByHospitalId(id) > 0) {
            throw new OperacaoInvalidaException(
                    "Hospital possui requisicoes cadastradas e nao pode ser removido");
        }
        if (rotas.countByDestinoId(id) > 0) {
            throw new OperacaoInvalidaException(
                    "Hospital possui rotas vinculadas e nao pode ser removido");
        }
        hospitais.delete(hospital);
    }
}
