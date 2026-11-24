package com.rotavital.dominio;

import com.rotavital.dominio.enums.TipoUnidade;
import jakarta.persistence.Entity;

/**
 * Unidade que solicita hemocomponentes. As requisicoes apontam para o
 * hospital e sao consultadas pelo repositorio; a colecao inversa que existia
 * nao era usada e foi removida (PI3-247).
 */
@Entity
public class Hospital extends Local {

    private String cnes;

    protected Hospital() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    public Hospital(String id, String nome, String telefone, Endereco endereco, String cnes) {
        super(id, nome, telefone, endereco);
        this.cnes = cnes;
    }

    public String getCnes() { return cnes; }

    @Override
    public TipoUnidade getTipo() { return TipoUnidade.HOSPITAL; }

    @Override
    public String getRegistro() { return cnes; }

    @Override
    protected void definirRegistro(String registro) { this.cnes = registro; }
}
