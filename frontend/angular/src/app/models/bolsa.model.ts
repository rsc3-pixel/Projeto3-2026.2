import { GrupoSanguineo, StatusBolsa, TipoHemocomponente } from './enums';

export interface Bolsa {
  id: string;
  hemocentroId: string;
  tipoHemocomponente: TipoHemocomponente;
  grupoSanguineo: GrupoSanguineo;
  volumeMl: number;
  dataColeta: string;
  dataValidade: string;
  status: StatusBolsa;
}
