package com.rotavital.aplicacao;

import com.rotavital.dominio.Endereco;
import com.rotavital.dominio.Local;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import com.rotavital.dominio.repositorio.UnidadeRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso comuns a qualquer unidade da rede (PI3-247).
 *
 * <p>HemocentroServico e HospitalServico eram cerca de 90% o mesmo codigo,
 * com o nome trocado. O que e igual mora aqui, escrito uma vez: listar com
 * filtro de endereco, buscar com 404, garantir registro oficial unico e o
 * esqueleto da remocao. Cada subclasse declara so o que e dela: como saber se
 * o registro esta em uso e o que impede a remocao.</p>
 *
 * <p>E um Template Method: os metodos publicos fixam a ordem dos passos, e os
 * abstratos sao os pontos que variam.</p>
 */
public abstract class ServicoDeUnidade<T extends Local> {

    private final UnidadeRepository<T> unidades;
    private final String recurso;
    private final String nomeDoRegistro;

    /**
     * @param recurso        nome da unidade nas mensagens, ex.: "Hemocentro"
     * @param nomeDoRegistro nome do registro oficial, ex.: "CNPJ"
     */
    protected ServicoDeUnidade(UnidadeRepository<T> unidades, String recurso, String nomeDoRegistro) {
        this.unidades = unidades;
        this.recurso = recurso;
        this.nomeDoRegistro = nomeDoRegistro;
    }

    @Transactional(readOnly = true)
    public List<T> listar(String cidade, String uf) {
        if (cidade != null && uf != null) {
            return unidades.findByEnderecoCidadeIgnoreCaseAndEnderecoEstadoIgnoreCase(cidade, uf);
        }
        if (cidade != null) {
            return unidades.findByEnderecoCidadeIgnoreCase(cidade);
        }
        if (uf != null) {
            return unidades.findByEnderecoEstadoIgnoreCase(uf);
        }
        return unidades.findAll();
    }

    @Transactional(readOnly = true)
    public T buscar(String id) {
        return unidades.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(recurso, id));
    }

    /**
     * Remove a unidade, mas so se ela nao deixar orfaos: rastreabilidade e
     * requisito do dominio. O que conta como vinculo depende do tipo.
     */
    @Transactional
    public void remover(String id) {
        T unidade = buscar(id);
        exigirSemVinculos(id);
        unidades.delete(unidade);
    }

    /** Grava uma unidade nova, recusando registro oficial ja usado. */
    protected T cadastrar(T nova) {
        exigirRegistroLivre(nova.getRegistro());
        return unidades.save(nova);
    }

    /** Atualiza o cadastro; o registro so e conferido se mudou. */
    protected T atualizarCadastro(String id, String nome, String telefone,
                                  Endereco endereco, String registro) {
        T unidade = buscar(id);
        if (!unidade.getRegistro().equals(registro)) {
            exigirRegistroLivre(registro);
        }
        unidade.atualizarCadastro(nome, telefone, endereco, registro);
        return unidades.save(unidade);
    }

    private void exigirRegistroLivre(String registro) {
        if (registroEmUso(registro)) {
            throw new OperacaoInvalidaException("Ja existe " + recurso.toLowerCase()
                    + " com o " + nomeDoRegistro + " " + registro);
        }
    }

    protected abstract boolean registroEmUso(String registro);

    /** Lanca {@link OperacaoInvalidaException} se a unidade tiver vinculos. */
    protected abstract void exigirSemVinculos(String id);
}
