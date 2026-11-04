import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Requisicao } from '../../models';

@Component({
  selector: 'app-requisicoes',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './requisicoes.component.html',
  styleUrl: './requisicoes.component.css',
})
export class RequisicoesComponent implements OnInit {
  private readonly api = inject(ApiService);

  requisicoes = signal<Requisicao[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  filtroStatus = signal('');
  filtroPrioridade = signal('');

  filtered = computed(() => {
    let list = this.requisicoes();
    if (this.filtroStatus()) list = list.filter(r => r.status === this.filtroStatus());
    if (this.filtroPrioridade()) list = list.filter(r => r.prioridade === this.filtroPrioridade());
    return list;
  });

  ngOnInit(): void {
    this.api.getRequisicoes().subscribe({
      next: (r) => { this.requisicoes.set(r); this.loading.set(false); },
      error: () => { this.error.set('Falha ao carregar requisições.'); this.loading.set(false); },
    });
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  }

  formatStatusReq(s: string): string {
    const m: Record<string, string> = {
      PENDENTE: 'Pendente', PARCIALMENTE_ATENDIDA: 'Parcialmente Atendida',
      ATENDIDA: 'Atendida', CANCELADA: 'Cancelada',
    };
    return m[s] ?? s;
  }

  formatPrioridade(p: string): string {
    const m: Record<string, string> = { ROTINA: 'Rotina', URGENTE: 'Urgente', EMERGENCIA: 'Emergência' };
    return m[p] ?? p;
  }

  badgeStatus(s: string): string {
    return 'rv-status rv-status-' + s.toLowerCase().replace(/_/g, '-');
  }

  badgePrioridade(p: string): string {
    return 'rv-status rv-status-' + p.toLowerCase();
  }
}
