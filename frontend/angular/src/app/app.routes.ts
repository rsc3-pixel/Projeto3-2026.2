import { Routes } from '@angular/router';
import { PainelComponent } from './pages/painel/painel.component';
import { BolsasComponent } from './pages/bolsas/bolsas.component';
import { HemocentrosComponent } from './pages/hemocentros/hemocentros.component';
import { HospitaisComponent } from './pages/hospitais/hospitais.component';
import { RequisicoesComponent } from './pages/requisicoes/requisicoes.component';
import { RotasComponent } from './pages/rotas/rotas.component';

export const routes: Routes = [
  { path: '', redirectTo: 'painel', pathMatch: 'full' },
  { path: 'painel', component: PainelComponent },
  { path: 'bolsas', component: BolsasComponent },
  { path: 'estoque', redirectTo: 'bolsas', pathMatch: 'full' },
  { path: 'hemocentros', component: HemocentrosComponent },
  { path: 'hospitais', component: HospitaisComponent },
  { path: 'requisicoes', component: RequisicoesComponent },
  { path: 'rotas', component: RotasComponent },
  { path: '**', redirectTo: 'painel' },
];
