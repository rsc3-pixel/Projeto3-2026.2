import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { Bolsa, Hemocentro } from '../models';

describe('ApiService', () => {
  let service: ApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(ApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created with providedIn root', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch bolsas using relative /api/v1/bolsas url without absolute localhost', () => {
    const mockBolsas: Bolsa[] = [
      {
        id: 'B1',
        hemocentroId: 'HEMO-1',
        tipoHemocomponente: 'CONCENTRADO_HEMACIAS',
        grupoSanguineo: 'O_POSITIVO',
        volumeMl: 450,
        dataColeta: '2026-10-01',
        dataValidade: '2026-11-05',
        status: 'DISPONIVEL',
      },
    ];

    service.getBolsas().subscribe((bolsas) => {
      expect(bolsas).toEqual(mockBolsas);
      expect(bolsas.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/v1/bolsas');
    expect(req.request.method).toBe('GET');
    expect(req.request.url.startsWith('/api/v1')).toBe(true);
    req.flush(mockBolsas);
  });

  it('should fetch hemocentros using relative /api/v1/hemocentros url', () => {
    const mockHemocentros: Hemocentro[] = [
      { id: 'HEMO-1', nome: 'Hemope' },
    ];

    service.getHemocentros().subscribe((data) => {
      expect(data).toEqual(mockHemocentros);
    });

    const req = httpTesting.expectOne('/api/v1/hemocentros');
    expect(req.request.method).toBe('GET');
    req.flush(mockHemocentros);
  });

  it('should fetch indicadores estoque por tipo using relative url', () => {
    service.getEstoquePorTipo().subscribe();

    const req = httpTesting.expectOne('/api/v1/indicadores/estoque-por-tipo');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
