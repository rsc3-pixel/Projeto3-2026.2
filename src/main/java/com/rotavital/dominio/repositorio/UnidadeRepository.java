package com.rotavital.dominio.repositorio;

import com.rotavital.dominio.Local;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * Consultas comuns a qualquer unidade da rede (PI3-247). Antes as mesmas tres
 * buscas por endereco estavam repetidas no repositorio de hemocentros e no de
 * hospitais.
 *
 * <p>{@code @NoRepositoryBean} avisa o Spring Data que esta interface e so uma
 * base: ele gera implementacao para os repositorios concretos que a estendem,
 * nao para ela.</p>
 */
@NoRepositoryBean
public interface UnidadeRepository<T extends Local> extends JpaRepository<T, String> {

    List<T> findByEnderecoCidadeIgnoreCase(String cidade);

    List<T> findByEnderecoEstadoIgnoreCase(String estado);

    List<T> findByEnderecoCidadeIgnoreCaseAndEnderecoEstadoIgnoreCase(String cidade, String estado);
}
