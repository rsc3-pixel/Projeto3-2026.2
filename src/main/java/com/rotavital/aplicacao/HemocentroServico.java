package com.rotavital.aplicacao;

import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.MapeadorEndereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.repositorio.BolsaRepository;
import com.rotavital.dominio.repositorio.HemocentroRepository;
import com.rotavital.dominio.repositorio.RotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Casos de uso do hemocentro. O comum a toda unidade vem de
 * {@link ServicoDeUnidade}; aqui fica o CNPJ e o que impede a remocao.
 */
@Service
public class HemocentroServico extends ServicoDeUnidade<Hemocentro> {

    private final HemocentroRepository hemocentros;
    private final BolsaRepository bolsas;
    private final RotaRepository rotas;

    public HemocentroServico(HemocentroRepository hemocentros,
                             BolsaRepository bolsas,
                             RotaRepository rotas) {
        super(hemocentros, "Hemocentro", "CNPJ");
        this.hemocentros = hemocentros;
        this.bolsas = bolsas;
        this.rotas = rotas;
    }

    @Transactional
    public Hemocentro criar(HemocentroRequest dados) {
        return cadastrar(new Hemocentro(
                UUID.randomUUID().toString(),
                dados.nome(),
                dados.telefone(),
                MapeadorEndereco.paraDominio(dados.endereco()),
                dados.cnpj()));
    }

    @Transactional
    public Hemocentro atualizar(String id, HemocentroRequest dados) {
        return atualizarCadastro(id, dados.nome(), dados.telefone(),
                MapeadorEndereco.paraDominio(dados.endereco()), dados.cnpj());
    }

    @Override
    protected boolean registroEmUso(String cnpj) {
        return hemocentros.existsByCnpj(cnpj);
    }

    /** Hemocentro com estoque ou com rotas de origem perderia o rastro das bolsas. */
    @Override
    protected void exigirSemVinculos(String id) {
        if (!bolsas.findByHemocentroOrigemId(id).isEmpty()) {
            throw new OperacaoInvalidaException(
                    "Hemocentro possui bolsas cadastradas e nao pode ser removido");
        }
        if (rotas.countByOrigemId(id) > 0) {
            throw new OperacaoInvalidaException(
                    "Hemocentro possui rotas vinculadas e nao pode ser removido");
        }
    }
}
