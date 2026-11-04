import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Bolsa } from '../../models';

@Component({
  selector: 'app-bolsas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './bolsas.component.html',
  styleUrl: './bolsas.component.css',
})
export class BolsasComponent implements OnInit {
  private readonly api = inject(ApiService);

  bolsas = signal<Bolsa[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  filtroStatus = signal('');
  filtroTipo = signal('');
  filtroGrupo = signal('');

  filtered = computed(() => {
    let list = this.bolsas();
    if (this.filtroStatus()) list = list.filter(b => b.status === this.filtroStatus());
    if (this.filtroTipo()) list = list.filter(b => b.tipoHemocomponente === this.filtroTipo());
    if (this.filtroGrupo()) list = list.filter(b => b.grupoSanguineo === this.filtroGrupo());
    return list;
  });

  ngOnInit(): void {
    this.api.getBolsas().subscribe({
      next: (b) => { this.bolsas.set(b); this.loading.set(false); },
      error: () => { this.error.set('Falha ao carregar bolsas.'); this.loading.set(false); },
    });
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('pt-BR');
  }

  formatComponente(c: string): string {
    const m: Record<string, string> = {
      CONCENTRADO_HEMACIAS: 'Conc. Hemácias',
      PLASMA_FRESCO_CONGELADO: 'Plasma',
      CONCENTRADO_PLAQUETAS: 'Plaquetas',
      CRIOPRECIPITADO: 'Crioprecipitado',
    };
    return m[c] ?? c;
  }

  formatGrupo(g: string): string {
    return g.replace('_POSITIVO', ' +').replace('_NEGATIVO', ' -').replace(/_/g, ' ');
  }

  formatStatus(s: string): string {
    const m: Record<string, string> = {
      DISPONIVEL: 'Disponível', RESERVADA: 'Reservada', ALOCADA: 'Alocada',
      EM_TRANSITO: 'Em Trânsito', ENTREGUE: 'Entregue', DESCARTADA: 'Descartada',
    };
    return m[s] ?? s;
  }

  statusClass(status: string): string {
    return 'rv-status rv-status-' + status.toLowerCase().replace(/_/g, '-');
  }
}
