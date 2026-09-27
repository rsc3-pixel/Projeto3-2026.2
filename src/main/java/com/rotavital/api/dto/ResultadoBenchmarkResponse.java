package com.rotavital.api.dto;

import com.rotavital.paralelo.ResumoValidade;

public record ResultadoBenchmarkResponse(
        int tamanhoAmostra,
        String modo,
        int threads,
        double tempoMs,
        ResumoValidade resultado) {
}
