import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Hospital } from '../../models';

interface NovoHospitalForm {
  nome: string;
  cnes: string;
  telefone: string;
  logradouro: string;
  numero: string;
  bairro: string;
  cidade: string;
  uf: string;
  cep: string;
}

@Component({
  selector: 'app-hospitais',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './hospitais.component.html',
})
export class HospitaisComponent implements OnInit {
  private readonly api = inject(ApiService);

  hospitais = signal<Hospital[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  mostrarFormulario = signal(false);
  enviando = signal(false);
  sucessoMsg = signal<string | null>(null);
  erroForm = signal<string | null>(null);

  form: NovoHospitalForm = {
    nome: '', cnes: '', telefone: '',
    logradouro: '', numero: '', bairro: '', cidade: '', uf: '', cep: '',
  };

  ngOnInit(): void {
    this.api.getHospitais().subscribe({
      next: (h) => { this.hospitais.set(h); this.loading.set(false); },
      error: () => { this.error.set('Falha ao carregar hospitais.'); this.loading.set(false); },
    });
  }

  toggleFormulario(): void {
    this.mostrarFormulario.update(v => !v);
    this.sucessoMsg.set(null);
    this.erroForm.set(null);
  }

  enviarHospital(): void {
    if (!this.form.nome || !this.form.cnes || !this.form.logradouro || !this.form.cidade || !this.form.uf) {
      this.erroForm.set('Nome, CNES, logradouro, cidade e UF são obrigatórios.');
      return;
    }
    this.enviando.set(true);
    this.erroForm.set(null);

    const body = {
      nome: this.form.nome,
      cnes: this.form.cnes,
      telefone: this.form.telefone || undefined,
      endereco: {
        logradouro: this.form.logradouro,
        numero: this.form.numero,
        bairro: this.form.bairro || undefined,
        cidade: this.form.cidade,
        uf: this.form.uf,
        cep: this.form.cep || undefined,
      },
    };

    this.api.postHospital(body).subscribe({
      next: (novo) => {
        this.hospitais.update(list => [...list, novo]);
        this.sucessoMsg.set(`Hospital "${novo.nome}" cadastrado com sucesso!`);
        this.enviando.set(false);
        this.mostrarFormulario.set(false);
        this.form = { nome: '', cnes: '', telefone: '', logradouro: '', numero: '', bairro: '', cidade: '', uf: '', cep: '' };
      },
      error: () => {
        this.erroForm.set('Erro ao cadastrar hospital. Verifique os dados e tente novamente.');
        this.enviando.set(false);
      },
    });
  }
}
