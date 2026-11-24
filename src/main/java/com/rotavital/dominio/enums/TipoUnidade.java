package com.rotavital.dominio.enums;

/**
 * Tipo de unidade da rede, no vocabulario unico do sistema Java (PI3-247).
 *
 * <p>Antes coexistiam "HOSPITAL" (API e banco) e "AGENCIA_TRANSFUSIONAL"
 * (malha), como strings soltas. Os scripts Python de dados, que sao outro
 * contexto, continuam com AGENCIA_TRANSFUSIONAL; a traducao fica registrada no
 * mapa do dominio.</p>
 */
public enum TipoUnidade {
    HEMOCENTRO,
    HOSPITAL
}
