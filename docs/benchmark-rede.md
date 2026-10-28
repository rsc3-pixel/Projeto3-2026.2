# Benchmarking de rede do Rota Vital

Como medimos latência, vazão, perda, jitter e erros 4xx/5xx da aplicação
implantada, e onde ficam os resultados. Documento da **PI3-31**. A
interpretação dos números é da **PI3-113**, que cita este método.

> Projeto acadêmico. Todos os dados são sintéticos e a telemetria de
> temperatura e GPS é **simulada**. As medições vêm de uma VM compartilhada
> de pequeno porte e não representam um ambiente hospitalar real.

---

## O que foi entregue

| Arquivo | Papel |
|---|---|
| `benchmark/k6/carga-rede.js` | Gera a carga HTTP e registra cada requisição |
| `benchmark/sonda_tcp.py` | Mede RTT, perda e jitter da rede (handshake TCP na 443) |
| `benchmark/coletar.sh` | Roda uma rodada completa e grava os dados brutos |
| `benchmark/analisar_rede.py` | Consolida as rodadas em `resumo-rede.json` / `.csv` |
| `benchmark/dados-brutos/` | Uma pasta por rodada, com tudo o que foi coletado |
| `.github/workflows/benchmark-rede.yml` | Dispara uma rodada pela aba Actions |
| `src/main/resources/static/painel-rede.html` | Painel de rede, em `/painel-rede.html` |

---

## As métricas

Cada métrica tem unidade, definição e ferramenta fixas, para que três
repetições (e duas origens) sejam comparáveis.

| Métrica | Unidade | Definição | Como é coletada |
|---|---|---|---|
| **Latência** | ms | Tempo entre enviar a requisição e receber o último byte, visto pelo cliente. Reportada em **p50, p90, p95, p99 e máximo**; a média aparece só como referência | k6, `http_req_duration`, por requisição |
| **Vazão** | req/s e KB/s | Requisições concluídas na etapa ÷ duração observada da etapa; bytes recebidos ÷ duração | k6, `http_reqs` e `data_received` |
| **Perda (rede)** | % | Handshakes TCP sem resposta em 1 s | `sonda_tcp.py` |
| **Perda (aplicação)** | % | Requisições sem nenhuma resposta HTTP: timeout, recusa ou reset (status 0 no k6) | k6 |
| **Jitter (rede)** | ms | Média de \|RTT(i) − RTT(i−1)\| entre amostras consecutivas (mesma ideia da RFC 3550) | `sonda_tcp.py` |
| **Jitter (aplicação)** | ms | Mesma fórmula sobre a latência do `/actuator/health`, pedido em taxa fixa de 2/s | k6, cenário `health` |
| **Erros 4xx** | % | Respostas 4xx ÷ total. O **409** é contado aqui e também mostrado à parte: na API ele é conflito de negócio ou de concorrência (PI3-108), não falha de rede | k6 |
| **Erros 5xx** | % | Respostas 5xx ÷ total | k6 |

### Por que percentis e não média

Uma API com p50 de 40 ms e um p99 de 2 s tem média de ~60 ms, e a média
esconde o problema. Na nossa VM a cauda tem causas conhecidas:

- o coletor `SerialGC` **pausa a aplicação inteira** a cada coleta;
- o Nginx enfileira requisições quando a JVM está ocupada;
- a VM pequena divide CPU com o Flux.

O p95 e o p99 mostram o que um hospital sentiria nos piores momentos, que é o
que importa numa requisição de hemocomponente.

### Por que uma sonda TCP e não `ping`

- Os runners do GitHub Actions **não deixam sair ICMP**, então o `ping`
  falharia ali e pareceria perda total.
- O handshake TCP (SYN → SYN-ACK) custa exatamente **uma ida e volta**, pelo
  mesmo caminho e porta (443) do tráfego HTTPS real.
- Quem responde o SYN é o kernel da VM, não o Nginx nem a JVM, então a sonda
  isola a **rede**.

A diferença entre a latência HTTP do k6 e o RTT da sonda é o tempo gasto no
Nginx e na aplicação. Essa decomposição é o ponto de partida da PI3-113.

Alternativa descartada: o `iperf3` em UDP mede perda e jitter direto, mas
exige um servidor na VM e uma porta nova no firewall. O
[deploy](deploy.md) foi desenhado para não mexer no firewall de uma máquina
compartilhada.

---

## O perfil de carga

```
|-- aquecimento --|---- etapa 1 ----|---- etapa 2 ----|-- repouso --|
     1 req/s         5 req/s           10 req/s         só sondas
   (descartado)      (medido)          (medido)        ("depois")
      120 s            300 s             300 s            60 s
```

Durante toda a rodada também rodam:

- `health`: `GET /actuator/health` a 2 req/s, base do jitter de aplicação;
- `canario`: `GET` no Flux a cada 10 s, a prova de que não o prejudicamos.

A sonda TCP roda 60 s em repouso **antes** da carga e de novo **durante** as
duas etapas.

### Endpoints exercitados

| Peso | Endpoint |
|---|---|
| 30% | `GET /api/v1/hemocentros` |
| 30% | `GET /api/v1/bolsas?status=DISPONIVEL` |
| 20% | `GET /api/v1/indicadores/estoque-por-tipo` |
| 20% | `GET /api/v1/indicadores/validade` |

Só leitura, de propósito: escrever no H2 em memória mudaria o estado entre
repetições, e elas deixariam de ser comparáveis.

**Ficam de fora:**

- `/api/v1/benchmark/dispersao-validade`: usa CPU com até 8 threads e
  sozinho satura a VM.
- `POST /api/v1/bolsas/lote` (PI3-30): cada lote usa 4 das 10 conexões do
  pool. Três lotes simultâneos esgotam o pool, e a espera de 30 s do Hikari
  coincide com o `proxy_read_timeout` do Nginx, virando 504. Seria "erro de
  rede" sem ser.
- Alocação automática: o 409 de conflito é comportamento correto (PI3-108) e
  inflaria a taxa de 4xx.

### Taxa de chegada fixa (e o problema da *coordinated omission*)

O k6 usa o executor `constant-arrival-rate`: as requisições chegam no ritmo
combinado, haja o que houver no servidor. Com usuários fixos (modelo
fechado), cada usuário só manda a próxima requisição quando a anterior volta.
Se o servidor engasga, o teste manda **menos** requisições justamente nos
momentos ruins, e o p99 sai artificialmente bom. Esse é o viés conhecido como
*coordinated omission*.

Se a aplicação ficar lenta a ponto de faltar usuário virtual para manter a
taxa, o k6 registra `dropped_iterations`. O painel e o resumo mostram esse
número: é o sinal de saturação.

### Telemetria simulada

O cenário `telemetria` (`TELEMETRIA=1`) envia leituras de temperatura
**simuladas** para `POST /api/v1/rotas/{id}/temperaturas`. Ele fica
**desligado** até o simulador da PI3-112 existir; o script já está pronto.

A API só aceita leitura de rota `EM_TRANSITO`, e só bolsa `RESERVADA`
embarca. Por isso o `setup()` monta, a cada rodada, uma requisição
sintética, a alocação automática, a rota, o embarque e a saída. Usar uma
rota nova por rodada também evita que o histórico de leituras, que cresce
sem paginação, deixe a repetição seguinte mais lenta. No fim, o
`teardown()` conclui a rota.

---

## Como a medição protege a VM compartilhada

Critério de aceite: *a medição não prejudica as outras aplicações
hospedadas na mesma VM*.

| Proteção | Como |
|---|---|
| A carga nunca sai da VM | Rodar lá disputaria CPU e memória com o Flux, e mediria o loopback em vez da rede |
| Carga modesta | No máximo 10 req/s, só leitura, sem endpoints pesados |
| Teto de VUs | `MAX_VUS=40`: se a aplicação engasgar, o k6 não abre usuários sem limite |
| Interrupção automática | Thresholds com `abortOnFail`: p95 medido > 3 s, erros > 5% ou **p95 do Flux > 3 s** encerram a rodada (código 99) |
| Evidência | O canário mede o Flux antes, durante e depois; o painel compara os três momentos |
| Recursos da VM | Com os segredos de deploy configurados, a rodada pelo Actions grava `vmstat` da VM |

Uma rodada interrompida não é jogada fora: os dados até o momento valem, e
`meta.json` registra `"abortada_por_threshold": true`.

---

## Antes de medir

1. **Congelar deploys.** Nenhum push na `main` durante a rodada: o deploy
   reinicia a aplicação e zera o H2.
2. **Cold start.** A primeira requisição após ociosidade leva 20 a 30 s e
   pode devolver 504. O `coletar.sh` insiste no `/actuator/health` até
   receber `UP`. Depois, o aquecimento de 2 min é descartado da análise.
3. **Espaçar as repetições** em 20 a 30 min. VMs pequenas do GCP trabalham
   com créditos de CPU, e rodadas coladas pioram artificialmente.

---

## Como rodar

### Pelo GitHub Actions (origem principal)

Aba **Actions** > **Benchmark de rede** > **Run workflow**, informando a
repetição (1, 2, 3). O resultado vem como artefato da execução. Para
guardá-lo:

1. baixe o artefato;
2. copie a pasta da rodada para `benchmark/dados-brutos/`;
3. faça commit.

O workflow não faz commit sozinho, para que uma rodada contaminada (deploy
no meio, VM fora do ar) não entre sem revisão.

### Do notebook (segunda origem)

Precisa de **k6** e **Python 3**:

| Sistema | Instalar o k6 |
|---|---|
| Windows | `winget install k6 --source winget` |
| macOS | `brew install k6` |
| Linux / WSL | <https://grafana.com/docs/k6/latest/set-up/install-k6/> |

Rode no Git Bash, no WSL ou num terminal Linux/macOS:

```bash
ORIGEM=notebook-recife REPETICAO=1 bash benchmark/coletar.sh
```

`ORIGEM` identifica de onde sai a carga (sem espaço). Para um teste rápido
dos scripts sem gastar 13 min, encurte as fases:

```bash
ORIGEM=teste REPETICAO=0 DUR_AQUECIMENTO_S=10 DUR_ETAPA_S=20 DUR_REPOUSO_S=10 \
  bash benchmark/coletar.sh
```

Apague a pasta de teste depois: ela não é uma medição.

### Analisar e publicar no painel

```bash
python3 benchmark/analisar_rede.py --publicar
```

O comando gera `benchmark/resumo-rede.json` e `.csv` e copia o JSON para
`src/main/resources/static/rede/`. O painel `/painel-rede.html` passa a
mostrá-lo a partir do próximo deploy.

---

## Estrutura dos dados brutos

```
benchmark/dados-brutos/2026-10-27_1400_github-actions_rep1/
  meta.json            data e hora (UTC e local), origem, parâmetros, versão do k6
  k6.csv.gz            uma linha por métrica por requisição (latência, status, bytes...)
  resumo-k6.json       resumo do próprio k6, só referência
  sonda-repouso.csv    RTT de cada handshake, sem carga
  sonda-carga.csv      RTT de cada handshake, durante as etapas
  vmstat.txt           CPU e memória da VM a cada 5 s (quando houver SSH)
```

O CSV do k6 vai compactado: tem várias linhas por requisição. Para abrir,
use `zcat` ou o próprio `analisar_rede.py`.

---

## Plano de coleta

| Origem | Repetições | Para quê |
|---|---|---|
| `github-actions` | 3 | Origem estável e reprodutível (máquina de data center nos EUA) |
| notebook da equipe | 3 | Caminho real de quem usaria o sistema a partir do Brasil, em rede residencial |

O contraste entre as duas origens mostra quanto da latência é distância e
rede, e quanto é a aplicação, já que a aplicação é a mesma nos dois casos.

---

## Limitações conhecidas

- **H2 em memória**: a aplicação mede um banco que não vai para produção
  (PostgreSQL entra na Entrega 02). Consultas no PostgreSQL terão custo
  diferente.
- **A sonda mede o RTT até o kernel**, não até a aplicação. É o que se quer
  para isolar a rede, mas não captura filas dentro do Nginx.
- **Jitter por diferença consecutiva** depende da ordem das amostras. Na
  sonda a ordem é exata. No `health` é a ordem de chegada das respostas; com
  2 req/s a diferença é desprezível.
- **Uma VM só**: não há balanceador, então a vazão medida é a de uma
  instância com 256 MB de heap, compartilhada.

---

## Opcional: latência do lado do servidor no Nginx

Incluir `$request_time` e `$upstream_response_time` no log do Rota Vital
permitiria separar rede, proxy e aplicação **por requisição**. Isso exige
declarar um `log_format` no bloco `http` do Nginx, que é configuração
compartilhada com o Flux. Ainda não foi aplicado: é decisão da equipe.

Se aprovado, o procedimento segue o mesmo cuidado do [deploy](deploy.md):

1. alterar a configuração;
2. validar com `sudo nginx -t`;
3. aplicar com `sudo systemctl reload nginx`, nunca `restart`.

---

## Critérios de aceite da PI3-31

| Critério | Onde está atendido |
|---|---|
| As cinco métricas, com unidade e método documentado | Tabela "As métricas" acima |
| Dados brutos no repositório, não só o resumo | `benchmark/dados-brutos/` |
| Latência em percentis, não só média | p50/p90/p95/p99/máx no resumo e no painel |
| Medição não prejudica as outras aplicações da VM | Seção "Como a medição protege a VM" + canário no painel |
| Três repetições com data, hora e origem | `meta.json` de cada rodada; tabela de reprodutibilidade no painel |
| Painel de rede | `/painel-rede.html` |
