package com.rotavital.dominio;

import com.rotavital.dominio.enums.TipoUnidade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hemocentro e Hospital como especializacoes de uma unidade da rede
 * (PI3-247): o que e comum mora no Local, e cada um so declara o que e seu.
 */
@DisplayName("Unidade da rede")
class UnidadeTest {

    private static final Endereco RECIFE = new Endereco("Rua A", "1", "Gracas", "Recife", "PE",
            "50000-000", -8.05, -34.89);
    private static final Endereco OLINDA = new Endereco("Rua B", "2", "Rio Doce", "Olinda", "PE",
            "53000-000", -7.96, -34.84);

    @Test
    @DisplayName("Dado um hemocentro, entao o tipo e HEMOCENTRO e o registro e o CNPJ")
    void hemocentro() {
        Local hemope = new Hemocentro("HC01", "Hemope", "(81) 0000-0000", RECIFE, "11.111.111/0001-11");

        assertThat(hemope.getTipo()).isEqualTo(TipoUnidade.HEMOCENTRO);
        assertThat(hemope.getRegistro()).isEqualTo("11.111.111/0001-11");
    }

    @Test
    @DisplayName("Dado um hospital, entao o tipo e HOSPITAL e o registro e o CNES")
    void hospital() {
        Local imip = new Hospital("HC05", "IMIP", "(81) 0000-0001", RECIFE, "0000005");

        assertThat(imip.getTipo()).isEqualTo(TipoUnidade.HOSPITAL);
        assertThat(imip.getRegistro()).isEqualTo("0000005");
    }

    @Test
    @DisplayName("Dado um hemocentro, quando atualizar o cadastro, entao muda nome, telefone, endereco e CNPJ de uma vez")
    void atualizarCadastroDoHemocentro() {
        Hemocentro hemope = new Hemocentro("HC01", "Hemope", "(81) 0000-0000", RECIFE, "11.111.111/0001-11");

        hemope.atualizarCadastro("Hemope Olinda", "(81) 9999-9999", OLINDA, "22.222.222/0001-22");

        assertThat(hemope.getNome()).isEqualTo("Hemope Olinda");
        assertThat(hemope.getTelefone()).isEqualTo("(81) 9999-9999");
        assertThat(hemope.getEndereco()).isSameAs(OLINDA);
        assertThat(hemope.getCnpj()).isEqualTo("22.222.222/0001-22");
    }

    @Test
    @DisplayName("Dado um hospital, quando atualizar o cadastro, entao o CNES acompanha")
    void atualizarCadastroDoHospital() {
        Hospital imip = new Hospital("HC05", "IMIP", "(81) 0000-0001", RECIFE, "0000005");

        imip.atualizarCadastro("IMIP", "(81) 0000-0001", RECIFE, "0000099");

        assertThat(imip.getCnes()).isEqualTo("0000099");
        assertThat(imip.getRegistro()).isEqualTo("0000099");
    }

    @Test
    @DisplayName("Dado duas unidades com o mesmo id, entao sao a mesma unidade")
    void identidadePeloId() {
        Local a = new Hospital("HC05", "IMIP", "1", RECIFE, "0000005");
        Local b = new Hospital("HC05", "Outro nome", "2", OLINDA, "0000006");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
