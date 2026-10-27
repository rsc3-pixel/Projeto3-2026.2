export interface Endereco {
  logradouro: string;
  numero: string;
  bairro?: string;
  cidade: string;
  uf: string;
  cep?: string;
  latitude?: number;
  longitude?: number;
}
