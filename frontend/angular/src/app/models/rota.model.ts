import { Bolsa } from './bolsa.model';
import { StatusRota } from './enums';

export interface Rota {
  id: string;
  hemocentroOrigemId: string;
  hospitalDestinoId: string;
  previsaoSaida: string;
  previsaoChegada: string;
  saidaReal?: string;
  chegadaReal?: string;
  status: StatusRota;
  bolsas?: Bolsa[];
}
