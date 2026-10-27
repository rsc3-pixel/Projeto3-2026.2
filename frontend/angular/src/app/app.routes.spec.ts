import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Location } from '@angular/common';
import { routes } from './app.routes';
import { PainelComponent } from './pages/painel/painel.component';
import { BolsasComponent } from './pages/bolsas/bolsas.component';
import { HemocentrosComponent } from './pages/hemocentros/hemocentros.component';
import { HospitaisComponent } from './pages/hospitais/hospitais.component';
import { RequisicoesComponent } from './pages/requisicoes/requisicoes.component';
import { RotasComponent } from './pages/rotas/rotas.component';

describe('App Routes', () => {
  let router: Router;
  let location: Location;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideRouter(routes)],
    }).compileComponents();

    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    router.initialNavigation();
  });

  it('nao deve conter o prefixo /app em nenhum caminho configurado', () => {
    for (const route of routes) {
      if (route.path) {
        expect(route.path.startsWith('/app')).toBe(false);
        expect(route.path.startsWith('app')).toBe(false);
      }
    }
  });

  it('deve ter as 6 rotas principais configuradas', () => {
    const paths = routes.map((r) => r.path);
    expect(paths).toContain('painel');
    expect(paths).toContain('bolsas');
    expect(paths).toContain('hemocentros');
    expect(paths).toContain('hospitais');
    expect(paths).toContain('requisicoes');
    expect(paths).toContain('rotas');
  });

  it('deve redirecionar a raiz ("") para /painel', async () => {
    await router.navigateByUrl('');
    expect(location.path()).toBe('/painel');
  });

  it('deve navegar para a rota de estoque/bolsas', async () => {
    await router.navigateByUrl('/bolsas');
    expect(location.path()).toBe('/bolsas');
  });

  it('deve redirecionar /estoque para /bolsas', async () => {
    await router.navigateByUrl('/estoque');
    expect(location.path()).toBe('/bolsas');
  });

  it('deve navegar para a rota de hemocentros', async () => {
    await router.navigateByUrl('/hemocentros');
    expect(location.path()).toBe('/hemocentros');
  });

  it('deve navegar para a rota de hospitais', async () => {
    await router.navigateByUrl('/hospitais');
    expect(location.path()).toBe('/hospitais');
  });

  it('deve navegar para a rota de requisicoes', async () => {
    await router.navigateByUrl('/requisicoes');
    expect(location.path()).toBe('/requisicoes');
  });

  it('deve navegar para a rota de rotas de distribuicao', async () => {
    await router.navigateByUrl('/rotas');
    expect(location.path()).toBe('/rotas');
  });

  it('deve redirecionar URL invalida para /painel (rota coringa **)', async () => {
    await router.navigateByUrl('/rota-inexistente-ou-invalida');
    expect(location.path()).toBe('/painel');
  });
});
