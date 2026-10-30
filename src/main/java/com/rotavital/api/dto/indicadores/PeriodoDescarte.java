package com.rotavital.api.dto.indicadores;

/** Granularidade do agrupamento da taxa de descarte por periodo de coleta. */
public enum PeriodoDescarte {
    /** Semana ISO 8601: comeca na segunda-feira, rotulo {@code 2026-W33}. */
    SEMANA,
    /** Mes civil, rotulo {@code 2026-08}. */
    MES
}
