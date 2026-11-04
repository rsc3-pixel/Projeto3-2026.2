import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';

const PAGE_TITLES: Record<string, string> = {
  painel: 'Painel', bolsas: 'Estoque de Bolsas',
  hemocentros: 'Hemocentros', hospitais: 'Hospitais',
  requisicoes: 'Requisições', rotas: 'Rotas',
};

@Component({
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);

  theme = signal<'light' | 'dark'>('light');
  collapsed = signal(false);

  pageTitle = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      map((e: NavigationEnd) => PAGE_TITLES[e.urlAfterRedirects.split('/')[1]] ?? 'Painel')
    ),
    { initialValue: 'Painel' }
  );

  toggleTheme() { this.theme.update(t => t === 'light' ? 'dark' : 'light'); }
  toggleCollapse() { this.collapsed.update(c => !c); }
}
