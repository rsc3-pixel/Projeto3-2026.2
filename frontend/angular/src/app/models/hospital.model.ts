import { Endereco } from './endereco.model';

export interface Hospital {
  id: string;
  nome: string;
  telefone?: string;
  cnes?: string;
  endereco?: Endereco;
}
