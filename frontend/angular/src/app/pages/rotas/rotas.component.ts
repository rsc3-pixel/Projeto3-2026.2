import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../services/api.service';
import { Hemocentro, Hospital, Rota } from '../../models';

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
  hemocentros = signal<Hemocentro[]>([]);
  hospitais = signal<Hospital[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  filtroStatus = signal('');

  filtered = computed(() => {
    const list = this.rotas();
    if (this.filtroStatus()) return list.filter(r => r.status === this.filtroStatus());
    return list;
  });

  private hemoMap = computed(() => {
    const m = new Map<string, string>();
    this.hemocentros().forEach(h => m.set(h.id, h.nome));
    return m;
  });

  private hospitalMap = computed(() => {
    const m = new Map<string, string>();
    this.hospitais().forEach(h => m.set(h.id, h.nome));
    return m;
  });

  ngOnInit(): void {
    forkJoin({
      rotas: this.api.getRotas(),
      hemocentros: this.api.getHemocentros(),
      hospitais: this.api.getHospitais(),
    }).subscribe({
      next: (d) => {
        this.rotas.set(d.rotas);
        this.hemocentros.set(d.hemocentros);
        this.hospitais.set(d.hospitais);
        this.loading.set(false);
      },
      error: () => { this.error.set('Falha ao carregar rotas.'); this.loading.set(false); },
    });
  }

  nomeOrigem(id: string): string {
    return this.hemoMap().get(id) ?? id.slice(0, 8) + '…';
  }

  nomeDestino(id: string): string {
    return this.hospitalMap().get(id) ?? id.slice(0, 8) + '…';
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

  duracao(saida: string, chegada: string): string {
    const diff = new Date(chegada).getTime() - new Date(saida).getTime();
    if (diff <= 0) return '—';
    const h = Math.floor(diff / 3600000);
    const m = Math.floor((diff % 3600000) / 60000);
    return h > 0 ? `${h}h ${m}min` : `${m}min`;
  }

  isAtrasada(rota: Rota): boolean {
    if (rota.status === 'CONCLUIDA' || rota.status === 'CANCELADA') return false;
    return new Date(rota.previsaoChegada).getTime() < Date.now();
  }
}
