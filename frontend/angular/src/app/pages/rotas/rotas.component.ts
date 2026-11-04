import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Rota } from '../../models';

@Component({
  selector: 'app-rotas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './rotas.component.html',
  styleUrl: './rotas.component.css',
})
export class RotasComponent implements OnInit {
  private readonly api = inject(ApiService);

  rotas = signal<Rota[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  filtroStatus = signal('');

  filtered = computed(() => {
    const list = this.rotas();
    if (this.filtroStatus()) return list.filter(r => r.status === this.filtroStatus());
    return list;
  });

  ngOnInit(): void {
    this.api.getRotas().subscribe({
      next: (r) => { this.rotas.set(r); this.loading.set(false); },
      error: () => { this.error.set('Falha ao carregar rotas.'); this.loading.set(false); },
    });
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  formatStatusRota(s: string): string {
    const m: Record<string, string> = {
      PLANEJADA: 'Planejada', EM_TRANSITO: 'Em Trânsito', CONCLUIDA: 'Concluída', CANCELADA: 'Cancelada',
    };
    return m[s] ?? s;
  }

  badgeRota(s: string): string {
    return 'rv-status rv-status-' + s.toLowerCase().replace(/_/g, '-');
  }
}
