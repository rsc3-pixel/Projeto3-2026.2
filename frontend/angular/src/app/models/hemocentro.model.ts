import { Endereco } from './endereco.model';

export interface Hemocentro {
  id: string;
  nome: string;
  telefone?: string;
  cnpj?: string;
  endereco?: Endereco;
}
