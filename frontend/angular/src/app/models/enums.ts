export type TipoHemocomponente =
  | 'CONCENTRADO_HEMACIAS'
  | 'PLASMA_FRESCO_CONGELADO'
  | 'CONCENTRADO_PLAQUETAS'
  | 'CRIOPRECIPITADO';

export type GrupoSanguineo =
  | 'A_POSITIVO'
  | 'A_NEGATIVO'
  | 'B_POSITIVO'
  | 'B_NEGATIVO'
  | 'AB_POSITIVO'
  | 'AB_NEGATIVO'
  | 'O_POSITIVO'
  | 'O_NEGATIVO';

export type StatusBolsa =
  | 'DISPONIVEL'
  | 'RESERVADA'
  | 'ALOCADA'
  | 'EM_TRANSITO'
  | 'ENTREGUE'
  | 'DESCARTADA';

export type PrioridadeRequisicao = 'ROTINA' | 'URGENTE' | 'EMERGENCIA';

export type StatusRequisicao =
  | 'PENDENTE'
  | 'PARCIALMENTE_ATENDIDA'
  | 'ATENDIDA'
  | 'CANCELADA';

export type StatusItemRequisicao = 'PENDENTE' | 'PARCIALMENTE_ALOCADO' | 'ALOCADO';

export type StatusRota = 'PLANEJADA' | 'EM_TRANSITO' | 'CONCLUIDA' | 'CANCELADA';
