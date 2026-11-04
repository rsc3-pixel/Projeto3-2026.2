import {
  Component, inject, signal, OnInit, OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import {
  Chart,
  BarController, BarElement,
  DoughnutController, ArcElement,
  CategoryScale, LinearScale,
  Tooltip, Legend,
} from 'chart.js';
import { ApiService } from '../../services/api.service';
import {
  BolsaCritica, CoberturaIndicador, DescarteComponente,
  EstoquePorComponente, EstoquePorTipo,
} from '../../models';

Chart.register(
  BarController, BarElement,
  DoughnutController, ArcElement,
  CategoryScale, LinearScale,
  Tooltip, Legend,
);

@Component({
  selector: 'app-painel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './painel.component.html',
  styleUrl: './painel.component.css',
})
export class PainelComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);

  loading   = signal(true);
  error     = signal<string | null>(null);
  estoquePorTipo        = signal<EstoquePorTipo[]>([]);
  estoquePorComponente  = signal<EstoquePorComponente[]>([]);
  cobertura             = signal<CoberturaIndicador[]>([]);
  descarte              = signal<DescarteComponente[]>([]);
  bolsasCriticas        = signal<BolsaCritica | null>(null);
  totalHemocentros      = signal(0);
  totalHospitais        = signal(0);

  private charts: Chart[] = [];
  private themeObserver?: MutationObserver;

  get totalBolsas(): number {
    return this.estoquePorTipo().reduce((s, e) => s + e.quantidade, 0);
  }

  ngOnInit(): void {
    const app = document.querySelector('.rv-app');
    if (app) {
      this.themeObserver = new MutationObserver(() => {
        if (!this.loading()) setTimeout(() => this.renderCharts(), 50);
      });
      this.themeObserver.observe(app, { attributes: true, attributeFilter: ['data-theme'] });
    }

    forkJoin({
      tipo:       this.api.getEstoquePorTipo(),
      componente: this.api.getEstoquePorComponente(),
      cobertura:  this.api.getCoberturaPorTipo(),
      descarte:   this.api.getTaxaDescarte(),
      criticas:   this.api.getBolsasCriticas(7),
      hemocentros: this.api.getHemocentros(),
      hospitais:  this.api.getHospitais(),
    }).subscribe({
      next: (d) => {
        this.estoquePorTipo.set(d.tipo);
        this.estoquePorComponente.set(d.componente);
        this.cobertura.set(d.cobertura);
        this.descarte.set(d.descarte);
        this.bolsasCriticas.set(d.criticas);
        this.totalHemocentros.set(d.hemocentros.length);
        this.totalHospitais.set(d.hospitais.length);
        this.loading.set(false);
        setTimeout(() => this.renderCharts(), 50);
      },
      error: () => {
        this.error.set('Falha ao carregar indicadores. Verifique se a API está disponível.');
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.destroyCharts();
    this.themeObserver?.disconnect();
  }

  private destroyCharts(): void {
    this.charts.forEach(c => { try { c.destroy(); } catch {} });
    this.charts = [];
  }

  private cssVar(name: string): string {
    const root = document.querySelector('.rv-app') as HTMLElement ?? document.documentElement;
    return getComputedStyle(root).getPropertyValue(name).trim();
  }

  private renderCharts(): void {
    this.destroyCharts();

    const ink    = this.cssVar('--ink2')   || '#666';
    const border = this.cssVar('--border') || 'rgba(0,0,0,0.08)';
    const baseFont = { family: "-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif", size: 12 };

    /* ── 1. Distribuição por tipo — barra horizontal ─────── */
    const canTipo = document.getElementById('chart-tipo') as HTMLCanvasElement | null;
    if (canTipo && this.estoquePorTipo().length) {
      const sorted = [...this.estoquePorTipo()].sort((a, b) => b.quantidade - a.quantidade);
      this.charts.push(new Chart(canTipo, {
        type: 'bar',
        data: {
          labels: sorted.map(e => this.formatGrupo(e.grupoSanguineo)),
          datasets: [{
            data: sorted.map(e => e.quantidade),
            backgroundColor: 'rgba(220,38,38,0.80)',
            hoverBackgroundColor: 'rgba(220,38,38,1)',
            borderRadius: 4,
            borderSkipped: false,
          }],
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: { label: ctx => ` ${ctx.parsed.x} bolsas` },
            },
          },
          scales: {
            x: {
              beginAtZero: true,
              grid: { color: border },
              ticks: { color: ink, font: baseFont },
              border: { display: false },
            },
            y: {
              grid: { display: false },
              ticks: { color: ink, font: { ...baseFont, size: 11 } },
              border: { display: false },
            },
          },
        },
      }));
    }

    /* ── 2. Por componente — doughnut ───────────────────── */
    const canComp = document.getElementById('chart-componente') as HTMLCanvasElement | null;
    if (canComp && this.estoquePorComponente().length) {
      const palette = [
        'rgba(220,38,38,0.85)',
        'rgba(21,128,61,0.85)',
        'rgba(180,83,9,0.85)',
        'rgba(100,116,139,0.85)',
      ];
      this.charts.push(new Chart(canComp, {
        type: 'doughnut',
        data: {
          labels: this.estoquePorComponente().map(e => this.formatComponente(e.tipoHemocomponente)),
          datasets: [{
            data: this.estoquePorComponente().map(e => e.quantidade),
            backgroundColor: palette,
            hoverOffset: 6,
            borderWidth: 0,
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '68%',
          plugins: {
            legend: {
              position: 'bottom',
              labels: { color: ink, font: baseFont, padding: 14, boxWidth: 10, boxHeight: 10 },
            },
            tooltip: {
              callbacks: {
                label: ctx => {
                  const pct = this.estoquePorComponente()[ctx.dataIndex]?.percentual ?? 0;
                  return ` ${ctx.parsed} bolsas (${pct.toFixed(1)}%)`;
                },
              },
            },
          },
        },
      }));
    }

    /* ── 3. Cobertura vs demanda — barras por tipo ──────── */
    const canCob = document.getElementById('chart-cobertura') as HTMLCanvasElement | null;
    if (canCob && this.cobertura().length) {
      const sorted = [...this.cobertura()].sort(
        (a, b) => (a.coberturaPercentual ?? 0) - (b.coberturaPercentual ?? 0),
      );
      const pcts = sorted.map(c => c.coberturaPercentual ?? 0);
      const bgColors = pcts.map(p =>
        p >= 100 ? 'rgba(21,128,61,0.80)'
        : p >= 50  ? 'rgba(180,83,9,0.80)'
        :            'rgba(220,38,38,0.80)',
      );
      this.charts.push(new Chart(canCob, {
        type: 'bar',
        data: {
          labels: sorted.map(c => this.formatGrupo(c.grupoSanguineo)),
          datasets: [{
            label: '% Cobertura',
            data: pcts.map(p => Math.min(p, 300)),
            backgroundColor: bgColors,
            hoverBackgroundColor: bgColors.map(c => c.replace('0.80', '1')),
            borderRadius: 4,
            borderSkipped: false,
          }],
        },
        options: {
          indexAxis: 'y',
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: {
              callbacks: {
                label: ctx => {
                  const orig = sorted[ctx.dataIndex];
                  const pct = orig.coberturaPercentual != null ? orig.coberturaPercentual.toFixed(1) + '%' : '—';
                  return ` Cobertura: ${pct}  |  Dispon.: ${orig.disponivel}  Demanda: ${orig.demandado}`;
                },
              },
            },
          },
          scales: {
            x: {
              beginAtZero: true,
              max: 300,
              grid: { color: border },
              ticks: {
                color: ink, font: baseFont,
                callback: v => v + '%',
              },
              border: { display: false },
            },
            y: {
              grid: { display: false },
              ticks: { color: ink, font: { ...baseFont, size: 11 } },
              border: { display: false },
            },
          },
        },
      }));
    }
  }

  formatGrupo(g: string): string {
    return g.replace('_POSITIVO', '+').replace('_NEGATIVO', '-').replace(/_/g, ' ');
  }

  formatComponente(c: string): string {
    const m: Record<string, string> = {
      CONCENTRADO_HEMACIAS: 'Hemácias',
      PLASMA_FRESCO_CONGELADO: 'Plasma',
      CONCENTRADO_PLAQUETAS: 'Plaquetas',
      CRIOPRECIPITADO: 'Crioprecipitado',
    };
    return m[c] ?? c;
  }
}
