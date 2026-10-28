// =============================================================================
// Benchmarking de rede do Rota Vital (PI3-31)
//
// Gera a carga HTTP contra a aplicacao implantada e coleta, por requisicao:
// latencia, status e bytes. O resto (vazao, percentis, jitter, taxas de erro)
// e calculado depois, a partir do CSV bruto, por benchmark/analisar_rede.py.
//
// Nao rode este script direto: use benchmark/coletar.sh, que tambem roda a
// sonda TCP (perda e jitter de rede), grava os metadados e organiza a pasta
// de dados brutos.
//
// Linha do tempo de uma rodada (duracoes ajustaveis por variavel de ambiente):
//
//   |-- aquecimento --|---- etapa 1 ----|---- etapa 2 ----|-- repouso --|
//        1 req/s          TAXA1 req/s       TAXA2 req/s     so sondas
//     (descartado)        (medido)          (medido)        ("depois")
//
//   Durante TODA a rodada rodam duas sondas em taxa fixa:
//     - health: GET /actuator/health a 2 req/s, base do jitter de aplicacao
//     - canario: GET na aplicacao vizinha (Flux) a cada 10 s, prova de que a
//       medicao nao a prejudica (criterio de aceite da PI3-31)
//
// Por que constant-arrival-rate (modelo aberto) e nao VUs fixos (modelo
// fechado): com VUs fixos, cada usuario so manda a proxima requisicao quando a
// anterior volta. Se o servidor engasga, o teste automaticamente manda MENOS
// requisicoes, e justamente os momentos ruins ficam sub-representados nos
// percentis (o problema chamado "coordinated omission"). Com taxa de chegada
// fixa, a carga continua chegando no ritmo combinado, como chegaria de
// hospitais reais, e o p99 mostra o que de fato aconteceu.
//
// LGPD: o script so le dados sinteticos da carga inicial e, no cenario de
// telemetria, envia leituras de temperatura SIMULADAS. Nenhum dado real.
// =============================================================================

import http from 'k6/http';
import { check, fail } from 'k6';

// --- Parametros ---------------------------------------------------------------

const BASE_URL = (__ENV.BASE_URL || 'https://rsc3-rotavital.duckdns.org').replace(/\/$/, '');
const CANARIO_URL = __ENV.CANARIO_URL || 'https://rsc3-flux.duckdns.org/';

const TAXA1 = Number(__ENV.TAXA1 || 5);   // req/s na etapa 1
const TAXA2 = Number(__ENV.TAXA2 || 10);  // req/s na etapa 2

const S_AQUEC = Number(__ENV.DUR_AQUECIMENTO_S || 120);
const S_ETAPA = Number(__ENV.DUR_ETAPA_S || 300);
const S_REPOUSO = Number(__ENV.DUR_REPOUSO_S || 60);
const S_TOTAL = S_AQUEC + 2 * S_ETAPA + S_REPOUSO;

// Telemetria simulada: desligada ate o simulador da PI3-112 existir.
const TELEMETRIA = __ENV.TELEMETRIA === '1';
const TAXA_TELEMETRIA = Number(__ENV.TAXA_TELEMETRIA || 1); // leituras/s

// Guardas de seguranca. Estourou, o k6 interrompe a rodada inteira.
const P95_LIMITE_MS = Number(__ENV.P95_LIMITE_MS || 3000);
const ERRO_LIMITE = Number(__ENV.ERRO_LIMITE || 0.05);
const CANARIO_P95_LIMITE_MS = Number(__ENV.CANARIO_P95_LIMITE_MS || 3000);

// Teto de VUs. Se a aplicacao ficar lenta, o k6 abre mais VUs para manter a
// taxa; o teto impede que isso vire uma avalanche sobre a VM compartilhada.
// Quando o teto e atingido, o k6 registra "dropped_iterations": e o sinal de
// saturacao que o relatorio deve citar.
const MAX_VUS = Number(__ENV.MAX_VUS || 40);

// --- Cenarios -----------------------------------------------------------------

function s(segundos) {
  return `${segundos}s`;
}

const cenarios = {
  aquecimento: {
    executor: 'constant-arrival-rate',
    exec: 'mistura',
    rate: 1, timeUnit: '1s',
    duration: s(S_AQUEC),
    preAllocatedVUs: 2, maxVUs: 10,
    tags: { fase: 'aquecimento', grupo: 'app' },
  },
  etapa1: {
    executor: 'constant-arrival-rate',
    exec: 'mistura',
    rate: TAXA1, timeUnit: '1s',
    startTime: s(S_AQUEC),
    duration: s(S_ETAPA),
    preAllocatedVUs: Math.max(2, TAXA1), maxVUs: MAX_VUS,
    tags: { fase: 'etapa1', grupo: 'app', medido: 'sim' },
  },
  etapa2: {
    executor: 'constant-arrival-rate',
    exec: 'mistura',
    rate: TAXA2, timeUnit: '1s',
    startTime: s(S_AQUEC + S_ETAPA),
    duration: s(S_ETAPA),
    preAllocatedVUs: Math.max(2, TAXA2), maxVUs: MAX_VUS,
    tags: { fase: 'etapa2', grupo: 'app', medido: 'sim' },
  },
  health: {
    executor: 'constant-arrival-rate',
    exec: 'health',
    rate: 2, timeUnit: '1s',
    duration: s(S_TOTAL),
    preAllocatedVUs: 2, maxVUs: 6,
    tags: { grupo: 'sonda' },
  },
  canario: {
    executor: 'constant-arrival-rate',
    exec: 'canario',
    rate: 1, timeUnit: '10s',
    duration: s(S_TOTAL),
    preAllocatedVUs: 1, maxVUs: 3,
    tags: { grupo: 'canario' },
  },
};

if (TELEMETRIA) {
  cenarios.telemetria = {
    executor: 'constant-arrival-rate',
    exec: 'telemetria',
    rate: TAXA_TELEMETRIA, timeUnit: '1s',
    startTime: s(S_AQUEC),
    duration: s(2 * S_ETAPA),
    preAllocatedVUs: 2, maxVUs: 10,
    tags: { fase: 'telemetria', grupo: 'app', medido: 'sim' },
  };
}

export const options = {
  scenarios: cenarios,
  // Thresholds so sobre as fases medidas: o cold start do aquecimento pode
  // levar 30 s e devolver 504, e nao pode abortar a rodada.
  thresholds: {
    'http_req_duration{medido:sim}': [
      { threshold: `p(95)<${P95_LIMITE_MS}`, abortOnFail: true, delayAbortEval: '30s' },
    ],
    'http_req_failed{medido:sim}': [
      { threshold: `rate<${ERRO_LIMITE}`, abortOnFail: true, delayAbortEval: '30s' },
    ],
    'http_req_duration{grupo:canario}': [
      { threshold: `p(95)<${CANARIO_P95_LIMITE_MS}`, abortOnFail: true, delayAbortEval: '60s' },
    ],
  },
  // Os percentis que aparecem no resumo de terminal. O relatorio usa os do
  // CSV bruto, nao estes.
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  // Sem isso o k6 mandaria um User-Agent generico; com ele, o log do Nginx
  // separa o trafego do benchmark do trafego normal.
  userAgent: 'rota-vital-benchmark/1.0 (PI3-31)',
  // Nao segue redirects silenciosamente: um 301 de http->https contaria a
  // latencia de duas requisicoes como se fosse uma.
  maxRedirects: 0,
};

// --- Mistura de endpoints -----------------------------------------------------
//
// So leitura. Escrita no H2 em memoria faria o estado mudar entre repeticoes
// (e o heap crescer), e as rodadas deixariam de ser comparaveis.
//
// Fora de proposito:
//   - /api/v1/benchmark/dispersao-validade: CPU-intensivo com ate 8 threads,
//     sozinho satura a VM compartilhada.
//   - /api/v1/bolsas/lote (PI3-30): usa 4 conexoes do pool de 10 por lote;
//     tres lotes simultaneos ja esgotam o pool e a espera vira 504.
//   - alocacao automatica: conflitos de concorrencia devolvem 409 por desenho
//     (PI3-108) e inflariam a taxa de 4xx sem ser falha.

const MISTURA = [
  { peso: 30, nome: 'GET /api/v1/hemocentros', caminho: '/api/v1/hemocentros' },
  { peso: 30, nome: 'GET /api/v1/bolsas?status=DISPONIVEL', caminho: '/api/v1/bolsas?status=DISPONIVEL' },
  { peso: 20, nome: 'GET /api/v1/indicadores/estoque-por-tipo', caminho: '/api/v1/indicadores/estoque-por-tipo' },
  { peso: 20, nome: 'GET /api/v1/indicadores/validade', caminho: '/api/v1/indicadores/validade' },
];
const PESO_TOTAL = MISTURA.reduce((soma, e) => soma + e.peso, 0);

function sortearEndpoint() {
  let r = Math.random() * PESO_TOTAL;
  for (const e of MISTURA) {
    r -= e.peso;
    if (r < 0) return e;
  }
  return MISTURA[MISTURA.length - 1];
}

const PARAMS_BASE = { timeout: '35s' }; // acima dos 30 s do proxy_read_timeout do Nginx

export function mistura() {
  const e = sortearEndpoint();
  const res = http.get(BASE_URL + e.caminho, { ...PARAMS_BASE, tags: { name: e.nome } });
  check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });
}

export function health() {
  const res = http.get(BASE_URL + '/actuator/health', {
    ...PARAMS_BASE,
    tags: { name: 'GET /actuator/health' },
  });
  check(res, { 'health UP': (r) => r.status === 200 });
}

export function canario() {
  // So o status e a latencia interessam; o corpo e descartado.
  const res = http.get(CANARIO_URL, {
    ...PARAMS_BASE,
    tags: { name: 'GET canario (Flux)' },
    responseType: 'none',
  });
  check(res, { 'canario respondeu': (r) => r.status > 0 && r.status < 500 });
}

// --- Telemetria simulada (cenario opcional, TELEMETRIA=1) ---------------------
//
// Leitura de temperatura so e aceita em rota EM_TRANSITO, e so bolsa
// RESERVADA embarca. O setup monta esse estado do zero a cada rodada
// (o H2 zera a cada deploy, e uma rota nova por rodada evita que o historico
// acumulado de uma repeticao deixe a seguinte mais lenta):
//
//   requisicao sintetica -> alocacao automatica (reserva uma bolsa)
//   -> rota do hemocentro da bolsa ate o hospital -> embarque -> EM_TRANSITO

function json(res, contexto) {
  if (res.status < 200 || res.status >= 300) {
    fail(`setup da telemetria falhou em "${contexto}": HTTP ${res.status} ${res.body}`);
  }
  return res.json();
}

function dataHora(deslocamentoMin) {
  // LocalDateTime sem fuso, como a API espera
  return new Date(Date.now() + deslocamentoMin * 60000).toISOString().slice(0, 19);
}

export function setup() {
  if (!TELEMETRIA) return {};

  const cab = { headers: { 'Content-Type': 'application/json' }, tags: { name: 'setup' } };

  const hospitais = json(http.get(`${BASE_URL}/api/v1/hospitais`, cab), 'listar hospitais');
  const disponiveis = json(http.get(`${BASE_URL}/api/v1/bolsas?status=DISPONIVEL`, cab), 'listar bolsas');
  if (hospitais.length === 0 || disponiveis.length === 0) {
    fail('setup da telemetria: sem hospital ou sem bolsa DISPONIVEL na carga inicial');
  }
  const modelo = disponiveis[0];

  const requisicao = json(http.post(`${BASE_URL}/api/v1/requisicoes`, JSON.stringify({
    hospitalId: hospitais[0].id,
    prioridade: 'ROTINA',
    observacao: 'SINTETICO - criada pelo benchmark de rede (PI3-31)',
    itens: [{
      tipoHemocomponente: modelo.tipoHemocomponente,
      grupoSanguineo: modelo.grupoSanguineo,
      quantidadeSolicitada: 1,
    }],
  }), cab), 'criar requisicao');

  const item = requisicao.itens[0];
  const alocacao = json(http.post(
    `${BASE_URL}/api/v1/requisicoes/${requisicao.id}/itens/${item.id}/alocacoes/automatica`, null, cab),
    'alocar bolsa');
  const bolsa = json(http.get(`${BASE_URL}/api/v1/bolsas/${alocacao.bolsaId}`, cab), 'buscar bolsa');

  const rota = json(http.post(`${BASE_URL}/api/v1/rotas`, JSON.stringify({
    hemocentroOrigemId: bolsa.hemocentroId,
    hospitalDestinoId: hospitais[0].id,
    previsaoSaida: dataHora(0),
    previsaoChegada: dataHora(60),
  }), cab), 'criar rota');

  json(http.post(`${BASE_URL}/api/v1/rotas/${rota.id}/bolsas`,
    JSON.stringify({ bolsaId: bolsa.id }), cab), 'embarcar bolsa');
  json(http.patch(`${BASE_URL}/api/v1/rotas/${rota.id}`,
    JSON.stringify({ status: 'EM_TRANSITO' }), cab), 'colocar em transito');

  return { rotaId: rota.id, componente: bolsa.tipoHemocomponente };
}

// Faixa central por componente, para a leitura simulada oscilar dentro do
// aceitavel com excursoes ocasionais (mesmas faixas do TipoHemocomponente).
const FAIXA = {
  CONCENTRADO_HEMACIAS: [1, 6],
  CONCENTRADO_PLAQUETAS: [20, 24],
  PLASMA_FRESCO_CONGELADO: [-25, -18],
  CRIOPRECIPITADO: [-25, -18],
};

export function telemetria(dados) {
  const [min, max] = FAIXA[dados.componente] || [2, 6];
  const excursao = Math.random() < 0.05 ? (max - min) : 0; // ~5% fora da faixa
  const temperatura = min + Math.random() * (max - min) + excursao;

  const res = http.post(`${BASE_URL}/api/v1/rotas/${dados.rotaId}/temperaturas`,
    JSON.stringify({ temperaturaCelsius: Math.round(temperatura * 10) / 10 }),
    {
      ...PARAMS_BASE,
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/v1/rotas/{id}/temperaturas' },
    });
  check(res, { 'leitura aceita (201)': (r) => r.status === 201 });
}

export function teardown(dados) {
  if (!TELEMETRIA || !dados.rotaId) return;
  // Encerra a rota sintetica para nao deixar uma remessa "em transito" para sempre.
  http.patch(`${BASE_URL}/api/v1/rotas/${dados.rotaId}`,
    JSON.stringify({ status: 'CONCLUIDA' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'teardown' } });
}

// --- Resumo -------------------------------------------------------------------
//
// O coletar.sh passa SAIDA com a pasta da rodada; o resumo do k6 vai junto dos
// dados brutos como referencia rapida (o relatorio usa o CSV, nao este arquivo).

export function handleSummary(resumo) {
  const saida = {};
  if (__ENV.SAIDA) saida[`${__ENV.SAIDA}/resumo-k6.json`] = JSON.stringify(resumo, null, 2);
  saida.stdout = `\nRodada encerrada. Thresholds: ${
    Object.entries(resumo.metrics)
      .filter(([, m]) => m.thresholds)
      .map(([nome, m]) => `${nome} ${Object.values(m.thresholds).every((t) => t.ok) ? 'OK' : 'ESTOUROU'}`)
      .join(' | ')
  }\n`;
  return saida;
}
