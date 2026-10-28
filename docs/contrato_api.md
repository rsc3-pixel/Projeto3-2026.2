# Contrato de API REST — Hemobank

---

## 1. Modelos de dados

### Unidade (base de Hemocentro / Hospital)

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | Identificador único |
| `nome` | string | sim | Nome fantasia da unidade |
| `cidade` | string | sim | Cidade sintética |
| `uf` | string(2) | sim | UF |
| `telefone` | string | não | Telefone de contato sintético |
| `email` | string | não | E-mail de contato sintético |
| `criadoEm` / `atualizadoEm` | date-time | gerado pelo backend | — |

`HemocentroResumo` / `HospitalResumo`: `id`, `nome`, `cidade`, `uf`.
`HemocentroDetalhe` / `HospitalDetalhe`: todos os campos acima.

### Bolsa

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | — |
| `hemocentroId` | UUID | sim | Hemocentro onde a bolsa está armazenada |
| `tipoHemocomponente` | enum | sim | `CONCENTRADO_HEMACIAS`, `PLASMA_FRESCO_CONGELADO`, `CONCENTRADO_PLAQUETAS`, `CRIOPRECIPITADO` |
| `grupoSanguineo` | string | sim | Notação ABO/Rh didática: `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-` |
| `volumeMl` | integer | sim | Volume da bolsa em mililitros |
| `dataColeta` | date | sim | Data da coleta |
| `dataValidade` | date | sim | Data de validade (usada para priorização FEFO) |
| `status` | enum | gerado pelo backend (`DISPONIVEL` na criação) | `DISPONIVEL`, `RESERVADA`, `ALOCADA`, `EM_TRANSITO`, `ENTREGUE`, `DESCARTADA` |
| `criadoEm` / `atualizadoEm` | date-time | gerado pelo backend | — |

`BolsaResumo`: `id`, `tipoHemocomponente`, `grupoSanguineo`, `status`, `dataValidade`, `hemocentroId`.
`BolsaDetalhe`: todos os campos.

### Requisição e Item de Requisição

**Requisição**

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | — |
| `hospitalId` | UUID | sim | Hospital requisitante |
| `prioridade` | enum | sim | `ROTINA`, `URGENTE`, `EMERGENCIA` |
| `observacao` | string | não | Texto livre, opcional |
| `status` | enum | gerado pelo backend (`PENDENTE` na criação) | `PENDENTE`, `PARCIALMENTE_ATENDIDA`, `ATENDIDA`, `CANCELADA` |
| `itens` | array de `ItemRequisicao` | sim (mín. 1 item) | Ver abaixo |
| `criadoEm` / `atualizadoEm` | date-time | gerado pelo backend | — |

**ItemRequisicao**

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | — |
| `tipoHemocomponente` | enum | sim | Mesmo enum de `Bolsa.tipoHemocomponente` |
| `grupoSanguineo` | string | sim | Mesma notação de `Bolsa.grupoSanguineo` |
| `quantidadeSolicitada` | integer | sim (> 0) | Quantidade de bolsas necessárias |
| `quantidadeAlocada` | integer | calculado | Soma das alocações vinculadas a este item |
| `status` | enum | calculado | `PENDENTE`, `PARCIALMENTE_ALOCADO`, `ALOCADO` |

`RequisicaoResumo`: `id`, `hospitalId`, `status`, `prioridade`, `criadoEm`.
`RequisicaoDetalhe`: todos os campos de Requisição, com `itens` expandido (`ItemRequisicaoDetalhe`).

**AlocacaoDetalhe**: `id`, `itemRequisicaoId`, `bolsaId`, `alocadoEm`.

### Rota

| Campo | Tipo | Obrigatório na criação | Descrição |
|---|---|---|---|
| `id` | UUID | gerado pelo backend | — |
| `hemocentroOrigemId` | UUID | sim | — |
| `hospitalDestinoId` | UUID | sim | — |
| `previsaoSaida` | date-time | sim | — |
| `previsaoChegada` | date-time | sim | — |
| `saidaReal` | date-time | não (preenchido em trânsito) | — |
| `chegadaReal` | date-time | não (preenchido na conclusão) | — |
| `status` | enum | gerado pelo backend (`PLANEJADA` na criação) | `PLANEJADA`, `EM_TRANSITO`, `CONCLUIDA`, `CANCELADA` |
| `bolsas` | array de `BolsaResumo` | não (embarque via sub-recurso) | — |
| `criadoEm` / `atualizadoEm` | date-time | gerado pelo backend | — |

`RotaResumo`: `id`, `hemocentroOrigemId`, `hospitalDestinoId`, `status`, `previsaoSaida`, `previsaoChegada`.
`RotaDetalhe`: todos os campos.

### Indicadores (somente leitura)

**EstoqueIndicador** (item de `EstoqueIndicador[]`): `hemocentroId`, `hemocentroNome`, `tipoHemocomponente`,
`grupoSanguineo`, `quantidadeDisponivel`, `quantidadeProximaValidade72h`.

**RequisicaoIndicador**: `periodoInicio`, `periodoFim`, `totalRequisicoes`, `requisicoesAtendidas`,
`taxaAtendimentoPercentual`, `tempoMedioAtendimentoHoras`.

**RotaIndicador**: `periodoInicio`, `periodoFim`, `totalRotas`, `tempoMedioEntregaHoras`, `rotasAtrasadas`,
`percentualAtrasadas`.

---

## 2. Endpoints por recurso

### 2.1 Hemocentros — `/api/v1/hemocentros`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/hemocentros` | query: `cidade`, `uf`, `page`, `size`, `sort` | — | `Page<HemocentroResumo>` | 200 |
| GET | `/api/v1/hemocentros/{id}` | path: `id` | — | `HemocentroDetalhe` | 200, 404 |
| POST | `/api/v1/hemocentros` | — | `HemocentroDetalhe` (sem `id`/`criadoEm`/`atualizadoEm`) | `HemocentroDetalhe` | 201, 400, 401, 403 |
| PUT | `/api/v1/hemocentros/{id}` | path: `id` | `HemocentroDetalhe` (sem `id`/`criadoEm`/`atualizadoEm`) | `HemocentroDetalhe` | 200, 400, 404 |
| DELETE | `/api/v1/hemocentros/{id}` | path: `id` | — | — | 204, 404, 409 |
| GET | `/api/v1/hemocentros/{id}/bolsas` | path: `id`; query: `status`, `tipoHemocomponente`, `grupoSanguineo`, `page`, `size` | — | `Page<BolsaResumo>` | 200, 404 |
| GET | `/api/v1/hemocentros/{id}/rotas` | path: `id`; query: `status`, `page`, `size` | — | `Page<RotaResumo>` | 200, 404 |

### 2.2 Hospitais — `/api/v1/hospitais`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/hospitais` | query: `cidade`, `uf`, `page`, `size`, `sort` | — | `Page<HospitalResumo>` | 200 |
| GET | `/api/v1/hospitais/{id}` | path: `id` | — | `HospitalDetalhe` | 200, 404 |
| POST | `/api/v1/hospitais` | — | `HospitalDetalhe` (sem `id`/`criadoEm`/`atualizadoEm`) | `HospitalDetalhe` | 201, 400, 401, 403 |
| PUT | `/api/v1/hospitais/{id}` | path: `id` | `HospitalDetalhe` (sem `id`/`criadoEm`/`atualizadoEm`) | `HospitalDetalhe` | 200, 400, 404 |
| DELETE | `/api/v1/hospitais/{id}` | path: `id` | — | — | 204, 404, 409 |
| GET | `/api/v1/hospitais/{id}/requisicoes` | path: `id`; query: `status`, `page`, `size` | — | `Page<RequisicaoResumo>` | 200, 404 |

### 2.3 Bolsas — `/api/v1/bolsas`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/bolsas` | query: `status`, `tipoHemocomponente`, `grupoSanguineo`, `hemocentroId`, `page`, `size`, `sort` (padrão `dataValidade,asc`) | — | `Page<BolsaResumo>` | 200 |
| GET | `/api/v1/bolsas/{id}` | path: `id` | — | `BolsaDetalhe` | 200, 404 |
| POST | `/api/v1/bolsas` | — | `BolsaDetalhe` (sem `id`/`status`/`criadoEm`/`atualizadoEm`) | `BolsaDetalhe` | 201, 400, 401, 403 |
| PATCH | `/api/v1/bolsas/{id}` | path: `id` | `{ "status": "..." }` | `BolsaDetalhe` | 200, 400, 404, 409 |
| DELETE | `/api/v1/bolsas/{id}` | path: `id` | — | — | 204, 404, 409 |

### 2.4 Requisições — `/api/v1/requisicoes`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/requisicoes` | query: `status`, `hospitalId`, `prioridade`, `page`, `size`, `sort` | — | `Page<RequisicaoResumo>` | 200 |
| GET | `/api/v1/requisicoes/{id}` | path: `id` | — | `RequisicaoDetalhe` | 200, 404 |
| POST | `/api/v1/requisicoes` | — | `{ hospitalId, prioridade, observacao?, itens: [{ tipoHemocomponente, grupoSanguineo, quantidadeSolicitada }] }` | `RequisicaoDetalhe` | 201, 400, 401, 403 |
| PATCH | `/api/v1/requisicoes/{id}` | path: `id` | `{ "status": "CANCELADA" }` | `RequisicaoDetalhe` | 200, 400, 404, 409 |
| GET | `/api/v1/requisicoes/{id}/itens` | path: `id` | — | `List<ItemRequisicaoDetalhe>` | 200, 404 |
| POST | `/api/v1/requisicoes/{id}/itens` | path: `id` | `{ tipoHemocomponente, grupoSanguineo, quantidadeSolicitada }` | `ItemRequisicaoDetalhe` | 201, 400, 404, 409 |
| DELETE | `/api/v1/requisicoes/{id}/itens/{itemId}` | path: `id`, `itemId` | — | — | 204, 404, 409 |
| GET | `/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes` | path: `id`, `itemId` | — | `List<AlocacaoDetalhe>` | 200, 404 |
| POST | `/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes` | path: `id`, `itemId` | `{ "bolsaId": "..." }` | `AlocacaoDetalhe` | 201, 400, 404, 409 |
| POST | `/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes/automatica` | path: `id`, `itemId` | — | `AlocacaoDetalhe` | 201, 404, 409 |
| DELETE | `/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes/{alocacaoId}` | path: `id`, `itemId`, `alocacaoId` | — | — | 204, 404, 409 |

### 2.5 Rotas — `/api/v1/rotas`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/rotas` | query: `status`, `hemocentroId`, `hospitalId`, `page`, `size`, `sort` | — | `Page<RotaResumo>` | 200 |
| GET | `/api/v1/rotas/{id}` | path: `id` | — | `RotaDetalhe` | 200, 404 |
| POST | `/api/v1/rotas` | — | `{ hemocentroOrigemId, hospitalDestinoId, previsaoSaida, previsaoChegada }` | `RotaDetalhe` | 201, 400, 401, 403 |
| PATCH | `/api/v1/rotas/{id}` | path: `id` | `{ "status": "EM_TRANSITO" }` (com `saidaReal`/`chegadaReal` quando aplicável) | `RotaDetalhe` | 200, 400, 404, 409 |
| GET | `/api/v1/rotas/{id}/bolsas` | path: `id` | — | `List<BolsaResumo>` | 200, 404 |
| POST | `/api/v1/rotas/{id}/bolsas` | path: `id` | `{ "bolsaId": "..." }` | `BolsaResumo` | 201, 400, 404, 409 |
| DELETE | `/api/v1/rotas/{id}/bolsas/{bolsaId}` | path: `id`, `bolsaId` | — | — | 204, 404, 409 |
| POST | `/api/v1/rotas/{id}/temperaturas` | path: `id` | `{ "temperaturaCelsius": 4.0, "latitude"?: -8.05, "longitude"?: -34.90 }` | `LeituraTemperatura` | 201, 400, 404, 409 |
| GET | `/api/v1/rotas/{id}/temperaturas` | path: `id` | — | `List<LeituraTemperatura>` | 200, 404 |

### 2.6 Indicadores — `/api/v1/indicadores`

| Método | Path | Parâmetros | Entrada | Saída | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/indicadores/estoque` | query: `hemocentroId?`, `tipoHemocomponente?`, `grupoSanguineo?` | — | `EstoqueIndicador[]` | 200, 400 |
| GET | `/api/v1/indicadores/requisicoes` | query: `periodoInicio`, `periodoFim`, `hospitalId?` | — | `RequisicaoIndicador` | 200, 400 |
| GET | `/api/v1/indicadores/rotas` | query: `periodoInicio`, `periodoFim`, `hemocentroId?` | — | `RotaIndicador` | 200, 400 |

---

## 3. Exemplos completos de request/response

### 3.1 `POST /api/v1/bolsas` — registrar bolsa coletada

**Request**

```http
POST /api/v1/bolsas HTTP/1.1
Authorization: Bearer <token-hemocentro>
Content-Type: application/json

{
  "hemocentroId": "5b1e2a10-2c3d-4e5f-8a9b-0c1d2e3f4a5b",
  "tipoHemocomponente": "CONCENTRADO_HEMACIAS",
  "grupoSanguineo": "O-",
  "volumeMl": 350,
  "dataColeta": "2026-08-20",
  "dataValidade": "2026-09-24"
}
```

**Response — `201 Created`**

```http
HTTP/1.1 201 Created
Location: /api/v1/bolsas/9c1e6a2e-2f3a-4b7f-9d10-5e6f7a8b9c0d
Content-Type: application/json

{
  "id": "9c1e6a2e-2f3a-4b7f-9d10-5e6f7a8b9c0d",
  "hemocentroId": "5b1e2a10-2c3d-4e5f-8a9b-0c1d2e3f4a5b",
  "tipoHemocomponente": "CONCENTRADO_HEMACIAS",
  "grupoSanguineo": "O-",
  "volumeMl": 350,
  "dataColeta": "2026-08-20",
  "dataValidade": "2026-09-24",
  "status": "DISPONIVEL",
  "criadoEm": "2026-08-26T14:02:11Z",
  "atualizadoEm": "2026-08-26T14:02:11Z"
}
```

*(Dado sintético — bolsa, datas e IDs fictícios para fins didáticos.)*

---

### 3.2 `POST /api/v1/requisicoes` — hospital cria requisição com itens

**Request**

```http
POST /api/v1/requisicoes HTTP/1.1
Authorization: Bearer <token-hospital>
Content-Type: application/json

{
  "hospitalId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "prioridade": "URGENTE",
  "observacao": "Paciente em cirurgia eletiva — dado fictício de exemplo",
  "itens": [
    { "tipoHemocomponente": "CONCENTRADO_HEMACIAS", "grupoSanguineo": "O-", "quantidadeSolicitada": 2 },
    { "tipoHemocomponente": "PLASMA_FRESCO_CONGELADO", "grupoSanguineo": "O-", "quantidadeSolicitada": 1 }
  ]
}
```

**Response — `201 Created`**

```http
HTTP/1.1 201 Created
Location: /api/v1/requisicoes/7f8e9d0c-1b2a-3948-5766-7a8b9c0d1e2f
Content-Type: application/json

{
  "id": "7f8e9d0c-1b2a-3948-5766-7a8b9c0d1e2f",
  "hospitalId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "prioridade": "URGENTE",
  "observacao": "Paciente em cirurgia eletiva — dado fictício de exemplo",
  "status": "PENDENTE",
  "itens": [
    {
      "id": "aa11bb22-cc33-dd44-ee55-ff6677889900",
      "tipoHemocomponente": "CONCENTRADO_HEMACIAS",
      "grupoSanguineo": "O-",
      "quantidadeSolicitada": 2,
      "quantidadeAlocada": 0,
      "status": "PENDENTE"
    },
    {
      "id": "bb22cc33-dd44-ee55-ff66-778899001122",
      "tipoHemocomponente": "PLASMA_FRESCO_CONGELADO",
      "grupoSanguineo": "O-",
      "quantidadeSolicitada": 1,
      "quantidadeAlocada": 0,
      "status": "PENDENTE"
    }
  ],
  "criadoEm": "2026-08-26T14:10:03Z",
  "atualizadoEm": "2026-08-26T14:10:03Z"
}
```

---

### 3.3 `POST /api/v1/requisicoes/{id}/itens/{itemId}/alocacoes` — hemocentro aloca bolsa a um item

**Request**

```http
POST /api/v1/requisicoes/7f8e9d0c-1b2a-3948-5766-7a8b9c0d1e2f/itens/aa11bb22-cc33-dd44-ee55-ff6677889900/alocacoes HTTP/1.1
Authorization: Bearer <token-hemocentro>
Content-Type: application/json

{
  "bolsaId": "9c1e6a2e-2f3a-4b7f-9d10-5e6f7a8b9c0d"
}
```

**Response — `201 Created`**

```http
HTTP/1.1 201 Created
Location: /api/v1/requisicoes/7f8e9d0c.../itens/aa11bb22.../alocacoes/cc33dd44-ee55-ff66-7788-990011223344
Content-Type: application/json

{
  "id": "cc33dd44-ee55-ff66-7788-990011223344",
  "itemRequisicaoId": "aa11bb22-cc33-dd44-ee55-ff6677889900",
  "bolsaId": "9c1e6a2e-2f3a-4b7f-9d10-5e6f7a8b9c0d",
  "alocadoEm": "2026-08-26T14:15:47Z"
}
```

Efeito colateral que vale documentar para os testes: a bolsa referenciada passa de `status: "DISPONIVEL"` para
`"ALOCADA"`, e o item de requisição passa a refletir `quantidadeAlocada: 1`, `status: "PARCIALMENTE_ALOCADO"`.

### 3.4 `POST /api/v1/requisicoes/{id}/itens/{itemId}/alocacoes/automatica` — o sistema escolhe a bolsa

Diferente do endpoint acima, aqui o cliente **não informa a bolsa**. O sistema consulta o índice de
estoque por hash, que resolve em O(1) quais bolsas disponíveis existem de cada grupo compatível,
filtra pela compatibilidade ABO/Rh da `MatrizCompatibilidade` e escolhe por FEFO a de validade mais
próxima, com desempate determinístico por código de rastreio.

```http
POST /api/v1/requisicoes/7f8e9d0c-1b2a-3948-5766-7a8b9c0d1e2f/itens/aa11bb22-cc33-dd44-ee55-ff6677889900/alocacoes/automatica HTTP/1.1
Host: api.rotavital.local
```

Sem corpo de requisição.

```http
HTTP/1.1 201 Created
Location: /api/v1/requisicoes/7f8e9d0c.../itens/aa11bb22.../alocacoes/dd44ee55-ff66-7788-9900-112233445566
Content-Type: application/json

{
  "id": "dd44ee55-ff66-7788-9900-112233445566",
  "itemRequisicaoId": "aa11bb22-cc33-dd44-ee55-ff6677889900",
  "bolsaId": "BS-2026-000314",
  "alocadoEm": "2026-10-19T14:15:47Z"
}
```

Quando não há bolsa disponível e compatível, a resposta é `409 Conflict` com a mensagem
`Sem estoque disponivel para <grupo> <tipo>`, e a requisição permanece aberta para ser atendida
quando chegar estoque.

As demais regras são as mesmas do endpoint manual: a bolsa escolhida passa a `RESERVADA`, o item
recalcula `quantidadeAlocada` e a requisição recalcula o seu `status`.

**Compatibilidade ABO/Rh.** A bolsa não precisa ser do mesmo grupo sanguíneo do
item: precisa ser **compatível**. Um item que pede `A_POS` aceita bolsa `O_NEG`,
`O_POS`, `A_NEG` ou `A_POS`.

A regra varia por hemocomponente, porque plasma e crioprecipitado carregam
anticorpos em vez de antígenos e seguem tabela invertida:

| | Doador universal | Receptor universal |
|---|---|---|
| Hemácias e plaquetas | `O_NEG` | `AB_POS` |
| Plasma e crioprecipitado | `AB_POS` | `O_NEG` |

A relação não é simétrica: `O_NEG` doa hemácias para `A_POS`, mas `A_POS` não
doa para `O_NEG`.

Bolsa incompatível devolve `409`, com a mensagem listando os grupos aceitos:

```json
{
  "status": 409,
  "title": "Operacao nao permitida",
  "detail": "Grupo sanguineo incompativel: item pede O- e a bolsa e A+. Grupos aceitos para Concentrado de Hemacias: [O-]"
}
```

A compatibilidade implementada é didática e não substitui protocolo clínico.

*(Todos os dados dos exemplos acima são sintéticos, para fins didáticos.)*

---

### 3.4 `POST /api/v1/rotas/{id}/temperaturas` — sensor reporta leitura da remessa

**A telemetria é simulada nesta fase do projeto.** O endpoint recebe o que um
sensor reportaria; não há integração com hardware. Quem envia as leituras hoje
é o simulador da PI3-112 (`telemetria/simulador.py`, ver
[`telemetria-simulada.md`](telemetria-simulada.md)).

`latitude` e `longitude` são **opcionais** (GPS simulado, PI3-112). Quando
enviadas, vão as duas. Leituras só com temperatura continuam válidas, como
antes da PI3-112.

**Request**

```http
POST /api/v1/rotas/5a6b7c8d-9e0f-1a2b-3c4d-5e6f7a8b9c0d/temperaturas HTTP/1.1
Content-Type: application/json

{
  "temperaturaCelsius": 5.0,
  "latitude": -8.061230,
  "longitude": -34.893410
}
```

**Response — `201 Created`**

```json
{
  "id": "e1f2a3b4-c5d6-7890-1234-56789abcdef0",
  "rotaId": "5a6b7c8d-9e0f-1a2b-3c4d-5e6f7a8b9c0d",
  "temperaturaCelsius": 5.0,
  "latitude": -8.06123,
  "longitude": -34.89341,
  "dataHora": "2026-10-14T16:42:11",
  "dentroDaFaixa": false,
  "alertas": [
    {
      "componente": "CONCENTRADO_PLAQUETAS",
      "faixaMinima": 20.0,
      "faixaMaxima": 24.0,
      "bolsasAfetadas": 2
    }
  ]
}
```

**A faixa aceitável é a do componente transportado, não um valor único:**

| Componente | Faixa |
|---|---|
| Concentrado de hemácias | 1 a 6 °C |
| Concentrado de plaquetas | 20 a 24 °C |
| Plasma fresco congelado | −25 a −18 °C |
| Crioprecipitado | −25 a −18 °C |

Uma remessa pode levar componentes de faixas diferentes no mesmo
compartimento. O exemplo acima mostra isso: 5 °C está correto para hemácias e
é excursão para plaquetas, então o alerta sai apenas para as plaquetas. Por
isso `alertas` é uma lista, e `dentroDaFaixa` só é `true` quando nenhum
componente foi violado.

Os limites são inclusivos: 1,0 °C e 6,0 °C são aceitos para hemácias.

**Códigos de erro**

| Código | Quando |
|---|---|
| `400` | temperatura fora do intervalo plausível para um sensor (−80 a 60 °C), o que indica defeito do equipamento e não excursão |
| `400` | só uma das coordenadas enviada, ou latitude fora de −90 a 90 / longitude fora de −180 a 180 |
| `404` | rota inexistente |
| `409` | rota não está `EM_TRANSITO` |

*(Todos os dados dos exemplos acima são sintéticos, para fins didáticos.)*
