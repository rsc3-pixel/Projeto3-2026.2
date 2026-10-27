import { Bolsa } from './bolsa.model';
import { GrupoSanguineo, TipoHemocomponente } from './enums';

export interface EstoquePorTipo {
  grupoSanguineo: GrupoSanguineo;
  quantidade: number;
  percentual: number;
}

export interface EstoquePorComponente {
  tipoHemocomponente: TipoHemocomponente;
  quantidade: number;
  percentual: number;
}

export interface DescarteComponente {
  tipoHemocomponente: TipoHemocomponente;
  totalBolsas: number;
  bolsasVencidas: number;
  taxaDescartePercentual: number;
}

export interface DispersaoComponente {
  n: number;
  mediaDias: number;
  medianaDias: number;
  desvioPadraoDias: number;
}

export interface ValidadeIndicador {
  n: number;
  mediaDias: number;
  medianaDias: number;
  desvioPadraoDias: number;
  minimoDias: number;
  maximoDias: number;
  porComponente: Record<string, DispersaoComponente>;
}

export interface BolsaCritica {
  limiteDias: number;
  quantidade: number;
  percentualDoEstoqueValido: number;
  bolsas: Bolsa[];
}

export interface CoberturaIndicador {
  grupoSanguineo: GrupoSanguineo;
  disponivel: number;
  demandado: number;
  coberturaPercentual?: number;
  deficit: number;
}
