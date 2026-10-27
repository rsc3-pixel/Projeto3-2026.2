import {
  GrupoSanguineo,
  PrioridadeRequisicao,
  StatusItemRequisicao,
  StatusRequisicao,
  TipoHemocomponente,
} from './enums';

export interface ItemRequisicao {
  id: string;
  tipoHemocomponente: TipoHemocomponente;
  grupoSanguineo: GrupoSanguineo;
  quantidadeSolicitada: number;
  quantidadeAlocada: number;
  status: StatusItemRequisicao | string;
}

export interface Requisicao {
  id: string;
  hospitalId: string;
  prioridade: PrioridadeRequisicao;
  status: StatusRequisicao;
  dataHoraCriacao: string;
  prazoEntrega?: string;
  observacao?: string;
  itens: ItemRequisicao[];
}
