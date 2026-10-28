package com.rotavital.rede;

import java.util.List;

/**
 * Unidade real da rede de distribuicao: hemocentro, banco de sangue ou
 * hospital com agencia transfusional.
 *
 * <p>E o vertice do grafo, como definido na secao 1 do escopo do grafo
 * (PI3-13). No grafo entra apenas o {@code id}; os demais campos servem para
 * exibir a unidade e para calcular distancia a partir das coordenadas.</p>
 *
 * <p>Os dados sao os mesmos de {@code dados/unidades.py}, que continua sendo a
 * fonte de verdade da rede. Esta classe e a transcricao deles para o lado
 * Java, para que o carregamento da malha nao dependa de executar Python.</p>
 *
 * @param id         identificador da unidade, de HC01 a HC12
 * @param nome       razao social ou nome de operacao da unidade
 * @param tipo       HEMOCENTRO ou AGENCIA_TRANSFUSIONAL
 * @param cidade     municipio da Regiao Metropolitana do Recife
 * @param bairro     bairro da unidade
 * @param latitude   latitude em graus decimais
 * @param longitude  longitude em graus decimais
 */
public record UnidadeRede(
        String id,
        String nome,
        String tipo,
        String cidade,
        String bairro,
        double latitude,
        double longitude) {

    /**
     * As 12 unidades reais da rede, na mesma ordem de
     * {@code dados/unidades.py}. Lista imutavel: a malha e dado de entrada e
     * nao deve ser alterada em tempo de execucao.
     */
    public static final List<UnidadeRede> TODAS = List.of(
            new UnidadeRede("HC01", "Hemocentro Recife (HEMOPE)",
                    "HEMOCENTRO", "Recife", "Graças",
                    -8.0528651, -34.8979716),
            new UnidadeRede("HC02", "GSH Banco de Sangue Hemato",
                    "HEMOCENTRO", "Recife", "Boa Vista",
                    -8.0597226, -34.894112),
            new UnidadeRede("HC03", "IHENE - Instituto de Hematologia do Nordeste",
                    "HEMOCENTRO", "Recife", "Boa Vista",
                    -8.0503551, -34.8928547),
            new UnidadeRede("HC04", "Real Hospital Português de Beneficência",
                    "AGENCIA_TRANSFUSIONAL", "Recife", "Paissandu",
                    -8.0640504, -34.8981508),
            new UnidadeRede("HC05", "IMIP - Instituto de Medicina Integral Prof. Fernando Figueira",
                    "AGENCIA_TRANSFUSIONAL", "Recife", "Boa Vista",
                    -8.0669066, -34.8902756),
            new UnidadeRede("HC06", "Hospital das Clínicas UFPE",
                    "AGENCIA_TRANSFUSIONAL", "Recife", "Cidade Universitária",
                    -8.0474352, -34.9463152),
            new UnidadeRede("HC07", "Hospital do Tricentenário",
                    "AGENCIA_TRANSFUSIONAL", "Olinda", "Bairro Novo",
                    -8.0104411, -34.8443412),
            new UnidadeRede("HC08", "Hemolab Laboratório",
                    "HEMOCENTRO", "Olinda", "Rio Doce",
                    -7.9619962, -34.8434754),
            new UnidadeRede("HC09", "Hospital Nossa Senhora de Lourdes",
                    "AGENCIA_TRANSFUSIONAL", "Jaboatão dos Guararapes", "Cavaleiro",
                    -8.0991939, -34.9703409),
            new UnidadeRede("HC10", "Hospital e Policlínica Jaboatão Prazeres",
                    "AGENCIA_TRANSFUSIONAL", "Jaboatão dos Guararapes", "Cajueiro Seco",
                    -8.1661755, -34.923622),
            new UnidadeRede("HC11", "Hospital Guararapes (Memorial)",
                    "AGENCIA_TRANSFUSIONAL", "Jaboatão dos Guararapes", "Prazeres",
                    -8.1657849, -34.932821),
            new UnidadeRede("HC12", "Hospital Memorial Jaboatão",
                    "AGENCIA_TRANSFUSIONAL", "Jaboatão dos Guararapes", "Engenho Velho",
                    -8.1121248, -35.0118295)
    );
}
