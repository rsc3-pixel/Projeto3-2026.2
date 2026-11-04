import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Hospital } from '../../models';

@Component({
  selector: 'app-hospitais',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="page-content">
      <h2>Hospitais</h2>
      @if (loading()) {
        <p class="loading-msg">Carregando hospitais...</p>
      } @else if (error()) {
        <p class="error-msg">{{ error() }}</p>
      } @else {
        <div class="rv-frame">
        <div class="table-wrap">
          <table class="rv-table">
            <thead>
              <tr>
                <th>Nome</th>
                <th>CNES</th>
                <th>Telefone</th>
                <th>Cidade/UF</th>
              </tr>
            </thead>
            <tbody>
              @for (h of hospitais(); track h.id) {
                <tr>
                  <td>{{ h.nome }}</td>
                  <td>{{ h.cnes ?? '—' }}</td>
                  <td>{{ h.telefone ?? '—' }}</td>
                  <td>{{ h.endereco ? h.endereco.cidade + '/' + h.endereco.uf : '—' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="empty-msg">Nenhum hospital cadastrado.</td></tr>
              }
            </tbody>
          </table>
        </div>
        </div>
      }
    </section>
  `,
})
export class HospitaisComponent implements OnInit {
  private readonly api = inject(ApiService);

  hospitais = signal<Hospital[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.getHospitais().subscribe({
      next: (h) => { this.hospitais.set(h); this.loading.set(false); },
      error: () => { this.error.set('Falha ao carregar hospitais.'); this.loading.set(false); },
    });
  }
}
