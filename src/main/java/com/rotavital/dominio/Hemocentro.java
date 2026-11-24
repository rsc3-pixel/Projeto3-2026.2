package com.rotavital.dominio;

import com.rotavital.dominio.enums.TipoUnidade;
import jakarta.persistence.Entity;

/**
 * Unidade que coleta e estoca hemocomponentes. O estoque nao e uma colecao
 * aqui: cada Bolsa aponta para o hemocentro de origem e e consultada pelo
 * repositorio. A colecao inversa que existia nao era usada por ninguem e foi
 * removida (PI3-247).
 */
@Entity
public class Hemocentro extends Local {

    private String cnpj;

    protected Hemocentro() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public Hemocentro(String id, String nome, String telefone, Endereco endereco, String cnpj) {
        super(id, nome, telefone, endereco);
        this.cnpj = cnpj;
    }

    public String getCnpj() { return cnpj; }

    @Override
    public TipoUnidade getTipo() { return TipoUnidade.HEMOCENTRO; }

    @Override
    public String getRegistro() { return cnpj; }

    @Override
    protected void definirRegistro(String registro) { this.cnpj = registro; }
}
