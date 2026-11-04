import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Hemocentro } from '../../models';

@Component({
  selector: 'app-hemocentros',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="page-content">
      <h2>Hemocentros</h2>
      @if (loading()) {
        <p class="loading-msg">Carregando hemocentros...</p>
      } @else if (error()) {
        <p class="error-msg">{{ error() }}</p>
      } @else {
        <div class="rv-frame">
        <div class="table-wrap">
          <table class="rv-table">
            <thead>
              <tr>
                <th>Nome</th>
                <th>CNPJ</th>
                <th>Telefone</th>
                <th>Cidade/UF</th>
              </tr>
            </thead>
            <tbody>
              @for (h of hemocentros(); track h.id) {
                <tr>
                  <td>{{ h.nome }}</td>
                  <td>{{ h.cnpj ?? '—' }}</td>
                  <td>{{ h.telefone ?? '—' }}</td>
                  <td>{{ h.endereco ? h.endereco.cidade + '/' + h.endereco.uf : '—' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="empty-msg">Nenhum hemocentro cadastrado.</td></tr>
              }
            </tbody>
          </table>
        </div>
        </div>
      }
    </section>
  `,
})
export class HemocentrosComponent implements OnInit {
  private readonly api = inject(ApiService);

  hemocentros = signal<Hemocentro[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.getHemocentros().subscribe({
      next: (h) => { this.hemocentros.set(h); this.loading.set(false); },
      error: () => { this.error.set('Falha ao carregar hemocentros.'); this.loading.set(false); },
    });
  }
}
