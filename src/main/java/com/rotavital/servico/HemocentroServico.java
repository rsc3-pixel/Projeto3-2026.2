package com.rotavital.servico;

import com.rotavital.api.dto.HemocentroRequest;
import com.rotavital.api.dto.MapeadorEndereco;
import com.rotavital.dominio.Hemocentro;
import com.rotavital.repositorio.BolsaRepository;
import com.rotavital.repositorio.HemocentroRepository;
import com.rotavital.repositorio.RotaRepository;
import com.rotavital.dominio.excecao.OperacaoInvalidaException;
import com.rotavital.dominio.excecao.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Regras de negocio dos hemocentros.
 *
 * <p>O controlador nao fala com o repositorio direto: passa por aqui. E o
 * servico que decide o que pode e o que nao pode, e o controlador so cuida
 * de HTTP.</p>
 */
@Service
public class HemocentroServico {

    private final HemocentroRepository hemocentros;
    private final BolsaRepository bolsas;
    private final RotaRepository rotas;

    /**
     * Injecao por construtor: o Spring passa as dependencias ao criar o bean.
     * Sem {@code new} espalhado pelo codigo, e os campos podem ser final.
     */
    public HemocentroServico(HemocentroRepository hemocentros,
                             BolsaRepository bolsas,
                             RotaRepository rotas) {
        this.hemocentros = hemocentros;
        this.bolsas = bolsas;
        this.rotas = rotas;
    }

    @Transactional(readOnly = true)
    public List<Hemocentro> listar(String cidade, String uf) {
        if (cidade != null && uf != null) {
            return hemocentros.findByEnderecoCidadeIgnoreCaseAndEnderecoEstadoIgnoreCase(cidade, uf);
        }
        if (cidade != null) {
            return hemocentros.findByEnderecoCidadeIgnoreCase(cidade);
        }
        if (uf != null) {
            return hemocentros.findByEnderecoEstadoIgnoreCase(uf);
        }
        return hemocentros.findAll();
    }

    @Transactional(readOnly = true)
    public Hemocentro buscar(String id) {
        return hemocentros.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Hemocentro", id));
    }

    @Transactional
    public Hemocentro criar(HemocentroRequest dados) {
        if (hemocentros.existsByCnpj(dados.cnpj())) {
            throw new OperacaoInvalidaException("Ja existe hemocentro com o CNPJ " + dados.cnpj());
        }
        Hemocentro hemocentro = new Hemocentro(
                UUID.randomUUID().toString(),
                dados.nome(),
                dados.telefone(),
                MapeadorEndereco.paraDominio(dados.endereco()),
                dados.cnpj());
        return hemocentros.save(hemocentro);
    }

    @Transactional
    public Hemocentro atualizar(String id, HemocentroRequest dados) {
        Hemocentro hemocentro = buscar(id);

        boolean cnpjMudou = !hemocentro.getCnpj().equals(dados.cnpj());
        if (cnpjMudou && hemocentros.existsByCnpj(dados.cnpj())) {
            throw new OperacaoInvalidaException("Ja existe hemocentro com o CNPJ " + dados.cnpj());
        }

        hemocentro.setNome(dados.nome());
        hemocentro.setTelefone(dados.telefone());
        hemocentro.setCnpj(dados.cnpj());
        hemocentro.setEndereco(MapeadorEndereco.paraDominio(dados.endereco()));
        return hemocentros.save(hemocentro);
    }

    /**
     * Remove o hemocentro, mas so se ele nao deixar orfaos. Apagar um
     * hemocentro com estoque perderia o rastro das bolsas, e rastreabilidade
     * e requisito do dominio.
     */
    @Transactional
    public void remover(String id) {
        Hemocentro hemocentro = buscar(id);

        if (!bolsas.findByHemocentroOrigemId(id).isEmpty()) {
            throw new OperacaoInvalidaException(
                    "Hemocentro possui bolsas cadastradas e nao pode ser removido");
        }
        if (rotas.countByOrigemId(id) > 0) {
            throw new OperacaoInvalidaException(
                    "Hemocentro possui rotas vinculadas e nao pode ser removido");
        }
        hemocentros.delete(hemocentro);
    }
}
