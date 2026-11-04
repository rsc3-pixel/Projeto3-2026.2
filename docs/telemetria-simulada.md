# Canal de telemetria simulada

Como as leituras de temperatura e GPS chegam à aplicação. Documento da
**PI3-112**, que deixa o canal pronto para o benchmarking da **PI3-31**.

> **A telemetria é simulada.** Não há sensor, veículo nem hardware real. Um
> programa faz o papel dos sensores e envia dados sintéticos pela rede, pelo
> mesmo caminho que um sensor de verdade usaria. A avaliação de cadeia fria e
> a compatibilidade ABO/Rh são didáticas e não substituem protocolo clínico.

---

## O que foi implantado

| Peça | Onde | Papel |
|---|---|---|
| `telemetria/simulador.py` | Repositório | O "sensor": 3 veículos reportando temperatura + GPS a cada 30 s |
| `.github/workflows/telemetria-simulada.yml` | GitHub Actions | Hospeda o simulador: uma execução por hora, 55 min de envio cada |
| `POST /api/v1/rotas/{id}/temperaturas` | API (já existia, HU-07) | Recebe a leitura; agora aceita `latitude` e `longitude` opcionais |
| `telemetria/rota-vital-sensor.service` | Repositório | Alternativa: o mesmo simulador como serviço da VM, para quem tiver acesso |

---

## O caminho de uma leitura

```
  GitHub Actions (runner na internet)
  simulador.py  ── 3 veículos simulados
        │
        │  HTTPS/443   POST /api/v1/rotas/{id}/temperaturas
        │              { temperaturaCelsius, latitude, longitude }
        ▼
  ┌──────────────── VM compartilhada (us-west1-a) ────────────────┐
  │  Nginx  ── TLS (Certbot) ──►  HTTP/8081  ──►  API Rota Vital  │
  │                                               avalia a faixa  │
  │                                               do componente   │
  └───────────────────────────────────────────────────────────────┘
        │
        ▼  201 { dentroDaFaixa, alertas[] }
```

No [diagrama de topologia](diagrama_arquitetura_redes.drawio) da U1, este
canal corresponde à caixa "Telemetria simulada — HU-07" da arquitetura-alvo
(sensor → leitura periódica → API). O diagrama ainda a mostra como não
implementada; atualizá-lo para o estado atual fica como pendência.

### Onde hospedar o simulador: alternativas

| Opção | A favor | Contra | Decisão |
|---|---|---|---|
| **GitHub Actions agendado** | Fica na internet, como um sensor num caminhão: a leitura atravessa internet → Nginx → API. Não precisa de acesso à VM nem ocupa a memória dela | O GitHub não garante o horário exato do agendamento: buracos de alguns minutos entre execuções | **Escolhida** |
| Serviço systemd na VM | No ar 24 h, sem buracos | Exige acesso à VM (a equipe que implementou não tinha). Divide a VM com o Flux, por isso a unidade limita memória a 64 MB e CPU a 10% | Pronta em `rota-vital-sensor.service` |
| Agendador dentro da API (`@Scheduled`) | Mais simples | Não passa por rede nenhuma. A PI3-112 pede justamente o **canal de comunicação** | Descartada |
| Notebook da equipe | Fácil de demonstrar | Só funciona com o notebook ligado | Para testes e demonstração |

---

## Ciclo de uma viagem simulada

A API só aceita leitura de rota `EM_TRANSITO`, e só bolsa `RESERVADA`
embarca. Por isso cada veículo percorre o fluxo inteiro do domínio:

1. O hemocentro registra uma bolsa sintética (**reposição de estoque**).
2. O hospital cria uma requisição sintética de 1 bolsa.
3. A alocação automática (FEFO + compatibilidade) reserva uma bolsa.
4. Rota do hemocentro **onde a bolsa está** até o hospital, embarque e saída
   (`EM_TRANSITO`).
5. Leituras a cada 30 s, durante uma viagem de 20 a 40 min.
6. Chegada (`CONCLUIDA`), pausa de 30 s a 2 min, nova viagem.

**Por que repor a bolsa no passo 1.** Sem reposição, 3 veículos esvaziariam o
estoque da carga inicial em menos de um dia. A FEFO escolhe sempre a bolsa de
validade mais próxima, então as bolsas da carga inicial saem primeiro e as
repostas, mais novas, ficam no lugar delas. O nível de estoque fica estável.

Tudo o que o simulador cria leva a marca `SINTETICO - simulador de
telemetria (PI3-112)` na observação da requisição.

---

## Os modelos

### Temperatura

O compartimento refrigerado é modelado como um processo que volta para o
centro da faixa do componente (o refrigerador corrigindo), com ruído pequeno:

```
T(i+1) = T(i) + k · (alvo − T(i)) + ruído        k = 0,25
```

- **Normal:** o alvo é o centro da faixa (3,5 °C para hemácias, 22 °C para
  plaquetas, −21,5 °C para plasma e crioprecipitado).
- **Porta aberta:** com 2% de chance por leitura, o alvo vai para fora da
  faixa por 3 a 6 leituras e depois volta. É esse evento que gera os
  **alertas de cadeia fria** na API.

Com k = 0,25, depois que a porta fecha a temperatura recupera ~95% do
caminho até o alvo em ~10 leituras (0,75¹⁰ ≈ 0,06).

### GPS

Interpolação em **linha reta** entre as coordenadas do hemocentro e do
hospital, proporcional ao tempo de viagem decorrido, com ruído de ~15 m (a
imprecisão típica de um GPS). É uma simplificação: o simulador não conhece a
malha viária. Para a rede, o que importa é o volume e o formato das
mensagens, não o traçado.

---

## Mudança no contrato da API

`POST /api/v1/rotas/{id}/temperaturas` ganhou dois campos **opcionais**:

```json
{ "temperaturaCelsius": 4.2, "latitude": -8.061230, "longitude": -34.893410 }
```

| Regra | Resposta |
|---|---|
| Só temperatura, como antes da PI3-112 | `201`; `latitude` e `longitude` voltam `null` |
| Temperatura + as duas coordenadas | `201`; a posição também volta no histórico (`GET .../temperaturas`) |
| Só uma das coordenadas | `400`: meia coordenada não localiza nada |
| Latitude fora de −90..90 ou longitude fora de −180..180 | `400` |

Nada que já usava o endpoint quebra: o campo é opcional nos dois sentidos.
Testes em `TemperaturaRemessaTest` (seção "GPS simulado").

---

## Volume e impacto na VM compartilhada

| Item | Valor |
|---|---|
| Leituras | 3 veículos × 2 por minuto = **6 req/min** (~0,1 req/s) |
| Preparação de viagem | ~8 requisições a cada 20–40 min por veículo |
| Dados acumulados no H2 | ~8.600 leituras/dia, poucos MB; o H2 zera a cada deploy |
| Processo na VM | nenhum (o simulador roda no runner do GitHub) |

É uma carga ~50 vezes menor que a etapa mais leve do benchmarking (5 req/s).

---

## Robustez

O H2 é em memória: **cada deploy reinicia a API e apaga tudo**. O simulador
trata isso como evento normal:

| Situação | O que o simulador faz |
|---|---|
| Rota sumiu (404) ou não está mais em trânsito (409) | Abandona a viagem e começa outra |
| API reiniciando (502/504), falha de rede, timeout | Conta uma leitura perdida e segue no próximo intervalo |
| Não conseguiu preparar a viagem (ex.: 409 sem estoque compatível) | Tenta de novo com espera crescente: 15 s, 30 s, 60 s… até 5 min |
| Fim da execução ou Ctrl+C / SIGTERM | Conclui as rotas abertas antes de sair, para nenhuma remessa ficar "em trânsito" para sempre |

Verificado contra uma API de teste com as mesmas regras de status, apagando
todos os dados no meio de uma execução: cada veículo perdeu uma leitura,
começou viagem nova e, no fim, todas as rotas estavam concluídas e o estoque
no nível inicial.

---

## Como usar

### Ver que a telemetria está chegando

- **No GitHub:** aba **Actions** > **Telemetria simulada**: o log mostra cada
  viagem e cada alerta, e o artefato `leituras.jsonl` traz uma linha por
  leitura, com status HTTP e latência.
- **Na API:**
  ```bash
  curl https://rsc3-rotavital.duckdns.org/api/v1/rotas?status=EM_TRANSITO
  curl https://rsc3-rotavital.duckdns.org/api/v1/rotas/<id>/temperaturas
  ```

### Disparar ou pausar

- **Disparar agora:** Actions > Telemetria simulada > **Run workflow**. Dá
  para mudar duração, número de veículos e intervalo.
- **Pausar:** Actions > Telemetria simulada > "…" > **Disable workflow**.

### Rodar do notebook

```bash
python telemetria/simulador.py --duracao 600 --saida leituras.jsonl
```

Use `--base-url http://localhost:8080` contra a aplicação local, e
`--semente 42` para repetir a mesma sequência de leituras. Para ver todas
as opções: `python telemetria/simulador.py --help`.

Com o workflow ativo, rodar também do notebook dobra os veículos. Para um
teste rápido isso não é problema.

### Testes do simulador

```bash
python -m unittest telemetria/test_simulador.py -v
```

Os testes verificam:

- a temperatura fica dentro da faixa sem eventos e oscila em torno do centro;
- o evento de porta aberta gera excursão e a temperatura volta depois;
- o valor nunca sai do intervalo plausível do sensor;
- o GPS acerta os extremos e o meio do trajeto, com ruído da ordem de metros.

O workflow roda esses testes antes de cada execução.

---

## Relação com o benchmarking (PI3-31)

- Com o canal no ar, o cenário `TELEMETRIA=1` do `benchmark/coletar.sh` pode
  ser ligado. Ele cria uma rota própria e mede o `POST .../temperaturas` sob
  carga.
- O tráfego de fundo do simulador (~0,1 req/s) é pequeno perto da carga do
  benchmark, mas é tráfego real. Para uma rodada sem nenhum ruído de fundo,
  desative o workflow durante a medição e registre isso no relatório.

---

## Limitações

- **Buracos de agendamento:** o GitHub pode atrasar execuções agendadas; há
  minutos sem leitura entre uma hora e outra.
- **GPS em linha reta**, sem malha viária.
- **Sem autenticação do sensor:** a API ainda não tem autenticação (nenhum
  endpoint tem). Na arquitetura-alvo, cada sensor teria credencial própria.
- **Entidade ainda chamada `LeituraTemperatura`:** com o GPS, ela virou uma
  leitura de telemetria. O nome foi mantido para não quebrar o contrato e o
  histórico; renomear é uma refatoração à parte.

---

## Critérios de aceite da PI3-112

| Critério | Onde está atendido |
|---|---|
| Serviços no ar e acessíveis | API, Nginx e HTTPS em produção desde a PI3-21 ([deploy](deploy.md)); canal de telemetria no ar pelo workflow |
| Coerentes com o diagrama de topologia | O canal segue o fluxo previsto no diagrama da U1 (sensor simulado → leitura periódica → API), pela borda `HTTPS/443` do Nginx. Atualizar o diagrama para marcá-lo como implementado está pendente |
| Telemetria simulada chegando à aplicação | Leituras com temperatura e GPS a cada 30 s; log e artefato de cada execução no Actions |
| Pronta para o benchmarking (PI3-31) | Cenário `TELEMETRIA=1` do benchmark liberado; seção acima |
| Telemetria declarada como simulada | Este documento, o simulador, o workflow, o diagrama e a marca nos dados |
