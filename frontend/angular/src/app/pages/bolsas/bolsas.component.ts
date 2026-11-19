import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Bolsa, Hemocentro } from '../../models';
import { GrupoSanguineo, TipoHemocomponente } from '../../models/enums';

interface NovaBolsaForm {
  hemocentroId: string;
  tipoHemocomponente: TipoHemocomponente;
  grupoSanguineo: GrupoSanguineo;
  volumeMl: number;
  dataColeta: string;
}

@Component({
  selector: 'app-bolsas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bolsas.component.html',
  styleUrl: './bolsas.component.css',
})
export class BolsasComponent implements OnInit {
  private readonly api = inject(ApiService);

  bolsas = signal<Bolsa[]>([]);
  hemocentros = signal<Hemocentro[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  mostrarFormulario = signal(false);
  enviando = signal(false);
  sucessoMsg = signal<string | null>(null);
  erroForm = signal<string | null>(null);

  filtroStatus = signal('');
  filtroTipo = signal('');
  filtroGrupo = signal('');

  form: NovaBolsaForm = {
    hemocentroId: '',
    tipoHemocomponente: 'CONCENTRADO_HEMACIAS',
    grupoSanguineo: 'O_POSITIVO',
    volumeMl: 300,
    dataColeta: new Date().toISOString().split('T')[0],
  };

  filtered = computed(() => {
    let list = this.bolsas();
    if (this.filtroStatus()) list = list.filter(b => b.status === this.filtroStatus());
    if (this.filtroTipo()) list = list.filter(b => b.tipoHemocomponente === this.filtroTipo());
    if (this.filtroGrupo()) list = list.filter(b => b.grupoSanguineo === this.filtroGrupo());
    return list;
  });

  private hemoMap = computed(() => {
    const m = new Map<string, string>();
    this.hemocentros().forEach(h => m.set(h.id, h.nome));
    return m;
  });

  ngOnInit(): void {
    this.api.getBolsas().subscribe({
      next: (b) => { this.bolsas.set(b); this.loading.set(false); },
      error: () => { this.error.set('Falha ao carregar bolsas.'); this.loading.set(false); },
    });
    this.api.getHemocentros().subscribe({
      next: (h) => this.hemocentros.set(h),
    });
  }

  nomeHemocentro(id: string): string {
    return this.hemoMap().get(id) ?? id.slice(0, 8) + '…';
  }

  toggleFormulario(): void {
    this.mostrarFormulario.update(v => !v);
    this.sucessoMsg.set(null);
    this.erroForm.set(null);
  }

  enviarBolsa(): void {
    if (!this.form.hemocentroId) {
      this.erroForm.set('Hemocentro é obrigatório.');
      return;
    }
    this.enviando.set(true);
    this.erroForm.set(null);

    this.api.postBolsa({
      hemocentroId: this.form.hemocentroId,
      tipoHemocomponente: this.form.tipoHemocomponente,
      grupoSanguineo: this.form.grupoSanguineo,
      volumeMl: this.form.volumeMl,
      dataColeta: this.form.dataColeta,
    }).subscribe({
      next: (nova) => {
        this.bolsas.update(list => [nova, ...list]);
        this.sucessoMsg.set('Bolsa cadastrada com sucesso!');
        this.enviando.set(false);
        this.mostrarFormulario.set(false);
        this.form = {
          hemocentroId: '',
          tipoHemocomponente: 'CONCENTRADO_HEMACIAS',
          grupoSanguineo: 'O_POSITIVO',
          volumeMl: 300,
          dataColeta: new Date().toISOString().split('T')[0],
        };
      },
      error: () => {
        this.erroForm.set('Erro ao cadastrar bolsa. Verifique os dados.');
        this.enviando.set(false);
      },
    });
  }

  diasParaVencer(dataValidade: string): number {
    return Math.ceil((new Date(dataValidade).getTime() - Date.now()) / 86400000);
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
