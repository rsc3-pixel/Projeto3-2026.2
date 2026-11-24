package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Hemocentro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acesso aos hemocentros.
 *
 * <p>Nao ha implementacao escrita: o Spring Data gera a classe em tempo de
 * execucao a partir do nome dos metodos. {@code findByEnderecoCidade} vira
 * {@code where endereco.cidade = ?}, porque Endereco e embutido no Local.</p>
 */
public interface HemocentroRepository extends JpaRepository<Hemocentro, String> {

    List<Hemocentro> findByEnderecoCidadeIgnoreCase(String cidade);

    List<Hemocentro> findByEnderecoEstadoIgnoreCase(String estado);

    List<Hemocentro> findByEnderecoCidadeIgnoreCaseAndEnderecoEstadoIgnoreCase(
            String cidade, String estado);

    boolean existsByCnpj(String cnpj);
}
