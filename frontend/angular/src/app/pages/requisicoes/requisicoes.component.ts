import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Hospital, Requisicao } from '../../models';
import { GrupoSanguineo, PrioridadeRequisicao, TipoHemocomponente } from '../../models/enums';

interface NovaRequisicaoForm {
  hospitalId: string;
  prioridade: PrioridadeRequisicao;
  prazoEntrega: string;
  observacao: string;
  tipoHemocomponente: TipoHemocomponente;
  grupoSanguineo: GrupoSanguineo;
  quantidadeSolicitada: number;
}

@Component({
  selector: 'app-requisicoes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './requisicoes.component.html',
  styleUrl: './requisicoes.component.css',
})
export class RequisicoesComponent implements OnInit {
  private readonly api = inject(ApiService);

  requisicoes = signal<Requisicao[]>([]);
  hospitais = signal<Hospital[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  mostrarFormulario = signal(false);
  enviando = signal(false);
  sucessoMsg = signal<string | null>(null);
  erroForm = signal<string | null>(null);

  filtroStatus = signal('');
  filtroPrioridade = signal('');

  form: NovaRequisicaoForm = {
    hospitalId: '',
    prioridade: 'ROTINA',
    prazoEntrega: '',
    observacao: '',
    tipoHemocomponente: 'CONCENTRADO_HEMACIAS',
    grupoSanguineo: 'O_POSITIVO',
    quantidadeSolicitada: 1,
  };

  private hospitalMap = computed(() => {
    const m = new Map<string, string>();
    this.hospitais().forEach(h => m.set(h.id, h.nome));
    return m;
  });

  nomeHospital(id: string): string {
    return this.hospitalMap().get(id) ?? id.slice(0, 8) + '…';
  }

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
    this.api.getHospitais().subscribe({
      next: (h) => this.hospitais.set(h),
    });
  }

  toggleFormulario(): void {
    this.mostrarFormulario.update(v => !v);
    this.sucessoMsg.set(null);
    this.erroForm.set(null);
  }

  enviarRequisicao(): void {
    if (!this.form.hospitalId || !this.form.prioridade) {
      this.erroForm.set('Hospital e prioridade são obrigatórios.');
      return;
    }
    this.enviando.set(true);
    this.erroForm.set(null);

    const body = {
      hospitalId: this.form.hospitalId,
      prioridade: this.form.prioridade,
      prazoEntrega: this.form.prazoEntrega ? this.form.prazoEntrega + ':00' : undefined,
      observacao: this.form.observacao || undefined,
      itens: [{
        tipoHemocomponente: this.form.tipoHemocomponente,
        grupoSanguineo: this.form.grupoSanguineo,
        quantidadeSolicitada: this.form.quantidadeSolicitada,
      }],
    };

    this.api.postRequisicao(body).subscribe({
      next: (nova) => {
        this.requisicoes.update(list => [nova, ...list]);
        this.sucessoMsg.set('Requisição criada com sucesso!');
        this.enviando.set(false);
        this.mostrarFormulario.set(false);
        this.form = {
          hospitalId: '',
          prioridade: 'ROTINA',
          prazoEntrega: '',
          observacao: '',
          tipoHemocomponente: 'CONCENTRADO_HEMACIAS',
          grupoSanguineo: 'O_POSITIVO',
          quantidadeSolicitada: 1,
        };
      },
      error: () => {
        this.erroForm.set('Erro ao criar requisição. Verifique os dados e tente novamente.');
        this.enviando.set(false);
      },
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
