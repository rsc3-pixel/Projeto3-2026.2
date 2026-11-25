package com.rotavital.dominio;
import com.rotavital.dominio.enums.TipoUnidade;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Embedded;

import java.util.Objects;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class Local {

    @Id
    private String id;
    private String nome;
    private String telefone;
    
    @Embedded
    private Endereco endereco;

    protected Local() {
        // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
    }

    protected Local(String id, String nome, String telefone, Endereco endereco) {
        this.id = id;
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
    }

    public String getId()         { return id; }
    public String getNome()       { return nome; }
    public String getTelefone()   { return telefone; }
    public Endereco getEndereco() { return endereco; }

    /** Hemocentro ou hospital: cada especializacao responde por si. */
    public abstract TipoUnidade getTipo();

    /** Registro oficial da unidade: CNPJ do hemocentro, CNES do hospital. */
    public abstract String getRegistro();

    protected abstract void definirRegistro(String registro);

    /**
     * Atualiza o cadastro inteiro de uma vez. Substitui os quatro setters que
     * cada servico chamava em sequencia: o cadastro muda como uma unidade.
     */
    public void atualizarCadastro(String nome, String telefone, Endereco endereco, String registro) {
        this.nome = nome;
        this.telefone = telefone;
        this.endereco = endereco;
        definirRegistro(registro);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Local)) return false;
        Local local = (Local) o;
        return Objects.equals(id, local.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "] " + nome;
    }
}
