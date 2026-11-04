import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Bolsa,
  BolsaCritica,
  CoberturaIndicador,
  DescarteComponente,
  EstoquePorComponente,
  EstoquePorTipo,
  Hemocentro,
  Hospital,
  Requisicao,
  Rota,
  StatusBolsa,
  TipoHemocomponente,
  GrupoSanguineo,
  ValidadeIndicador,
} from '../models';

/**
 * Servico centralizado para consumo da API REST do Rota Vital.
 *
 * Equivalente Angular ao @Service do Spring Boot. Usa escopo singleton
 * (providedIn: 'root') e caminhos relativos (/api/v1) para que funcione tanto
 * em desenvolvimento (com proxy reverso) quanto em producao (mesmo dominio/origem).
 */
@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1';

  // --- Bolsas ---

  getBolsas(filtros?: {
    status?: StatusBolsa;
    tipoHemocomponente?: TipoHemocomponente;
    grupoSanguineo?: GrupoSanguineo;
    hemocentroId?: string;
  }): Observable<Bolsa[]> {
    let params = new HttpParams();
    if (filtros) {
      if (filtros.status) params = params.set('status', filtros.status);
      if (filtros.tipoHemocomponente) params = params.set('tipoHemocomponente', filtros.tipoHemocomponente);
      if (filtros.grupoSanguineo) params = params.set('grupoSanguineo', filtros.grupoSanguineo);
      if (filtros.hemocentroId) params = params.set('hemocentroId', filtros.hemocentroId);
    }
    return this.http.get<Bolsa[]>(`${this.baseUrl}/bolsas`, { params });
  }

  getBolsaPorId(id: string): Observable<Bolsa> {
    return this.http.get<Bolsa>(`${this.baseUrl}/bolsas/${id}`);
  }

  // --- Hemocentros ---

  getHemocentros(): Observable<Hemocentro[]> {
    return this.http.get<Hemocentro[]>(`${this.baseUrl}/hemocentros`);
  }

  getHemocentroPorId(id: string): Observable<Hemocentro> {
    return this.http.get<Hemocentro>(`${this.baseUrl}/hemocentros/${id}`);
  }

  // --- Hospitais ---

  getHospitais(): Observable<Hospital[]> {
    return this.http.get<Hospital[]>(`${this.baseUrl}/hospitais`);
  }

  getHospitalPorId(id: string): Observable<Hospital> {
    return this.http.get<Hospital>(`${this.baseUrl}/hospitais/${id}`);
  }

  // --- Requisicoes ---

  getRequisicoes(): Observable<Requisicao[]> {
    return this.http.get<Requisicao[]>(`${this.baseUrl}/requisicoes`);
  }

  getRequisicaoPorId(id: string): Observable<Requisicao> {
    return this.http.get<Requisicao>(`${this.baseUrl}/requisicoes/${id}`);
  }

  // --- Rotas ---

  getRotas(): Observable<Rota[]> {
    return this.http.get<Rota[]>(`${this.baseUrl}/rotas`);
  }

  getRotaPorId(id: string): Observable<Rota> {
    return this.http.get<Rota>(`${this.baseUrl}/rotas/${id}`);
  }

  // --- Indicadores (/api/v1/indicadores/*) ---

  getEstoquePorTipo(): Observable<EstoquePorTipo[]> {
    return this.http.get<EstoquePorTipo[]>(`${this.baseUrl}/indicadores/estoque-por-tipo`);
  }

  getEstoquePorComponente(): Observable<EstoquePorComponente[]> {
    return this.http.get<EstoquePorComponente[]>(`${this.baseUrl}/indicadores/estoque-por-componente`);
  }

  getTaxaDescarte(dataReferencia?: string): Observable<DescarteComponente[]> {
    let params = new HttpParams();
    if (dataReferencia) params = params.set('dataReferencia', dataReferencia);
    return this.http.get<DescarteComponente[]>(`${this.baseUrl}/indicadores/descarte`, { params });
  }

  getDispersaoValidade(dataReferencia?: string): Observable<ValidadeIndicador> {
    let params = new HttpParams();
    if (dataReferencia) params = params.set('dataReferencia', dataReferencia);
    return this.http.get<ValidadeIndicador>(`${this.baseUrl}/indicadores/validade`, { params });
  }

  getBolsasCriticas(limiteDias: number = 7, dataReferencia?: string): Observable<BolsaCritica> {
    let params = new HttpParams().set('limiteDias', limiteDias.toString());
    if (dataReferencia) params = params.set('dataReferencia', dataReferencia);
    return this.http.get<BolsaCritica>(`${this.baseUrl}/indicadores/criticas`, { params });
  }

  getCoberturaPorTipo(dataReferencia?: string): Observable<CoberturaIndicador[]> {
    let params = new HttpParams();
    if (dataReferencia) params = params.set('dataReferencia', dataReferencia);
    return this.http.get<CoberturaIndicador[]>(`${this.baseUrl}/indicadores/cobertura`, { params });
  }
}
