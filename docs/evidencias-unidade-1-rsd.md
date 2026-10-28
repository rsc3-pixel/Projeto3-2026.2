# Checkpoint Unidade 1 · Redes e Sistemas Distribuídos

Evidências de modelagem de rede, contratos de API e deploy de serviço distribuído.

**Verificado em:** 08/10/2026
**Repositório:** `rsc3-pixel/Projeto3-2026.2` (privado, autorizado pelo professor)

---

## 1. Topologia da rede em produção

**URL pública:** https://rsc3-rotavital.duckdns.org

O sistema opera como um serviço distribuído com os seguintes nós:

| Componente | Tecnologia | Papel na rede |
|---|---|---|
| VM GCP (us-central1) | Debian 12 | Host compartilhado das aplicações |
| Nginx (porta 80/443) | Reverse proxy | Ponto único de entrada; roteia por domínio |
| Spring Boot (porta 8080) | Java 21 + Tomcat embutido | Aplicação Rota Vital |
| H2 (em memória) | Banco de dados | Persistência local (migração para PostgreSQL prevista na U2) |
| GitHub Actions | CI/CD | Orquestração de build → teste → empacotamento → deploy |

A topologia planejada versus a topologia real está documentada em [`docs/W06-topologia-diferencas.md`](W06-topologia-diferencas.md), com justificativa para cada divergência.

---

## 2. Contrato de API — OpenAPI 3.0

O projeto usa [springdoc-openapi](https://springdoc.org/) para gerar automaticamente o contrato da API a partir das anotações do código.

| Endpoint | Descrição |
|---|---|
| [`/v3/api-docs`](https://rsc3-rotavital.duckdns.org/v3/api-docs) | Especificação OpenAPI 3.0 em JSON |
| [`/swagger-ui.html`](https://rsc3-rotavital.duckdns.org/swagger-ui.html) | Interface Swagger UI |
| [`/docs`](https://rsc3-rotavital.duckdns.org/docs) | Interface Scalar API Reference (tema personalizado) |

O contrato é gerado em build-time a partir das anotações `@Tag`, `@Operation` e `@Parameter` nos controllers — qualquer alteração na API reflete automaticamente no contrato publicado, sem documentação manual desatualizada.

### Grupos de recursos expostos

| Tag | Path base | Controllers |
|---|---|---|
| Bolsas | `/api/v1/bolsas` | `BolsaController` |
| Hemocentros | `/api/v1/hemocentros` | `HemocentroController` |
| Hospitais | `/api/v1/hospitais` | `HospitalController` |
| Requisicoes | `/api/v1/requisicoes` | `RequisicaoController` |
| Rotas | `/api/v1/rotas` | `RotaController` |
| Indicadores | `/api/v1/indicadores` | `IndicadorController` |
| Benchmark | `/api/v1/benchmark` | `BenchmarkController` |

---

## 3. Modelagem do grafo da rede logística

A rede de distribuição de hemocomponentes é modelada como um dígrafo ponderado pelo tempo de percurso em minutos.

**Decisões de modelagem:**

| Decisão | Escolha | Justificativa |
|---|---|---|
| Métrica de peso | Tempo (minutos) | Hemocomponentes têm validade curta — minimizar tempo é minimizar risco de descarte |
| Direcionamento | Dígrafo | Percursos de ida e volta não são simétricos (vias, trânsito, restrições) |
| Representação | Lista de adjacência | Rede esparsa: O(V+E) de memória vs. O(V²) da matriz |
| Vértice | `String` (código da unidade) | Agnóstico ao tipo de unidade — serve para hemocentros e hospitais |

A justificativa completa está em [`docs/W02-escopo-grafo-rede-distribuicao.md`](W02-escopo-grafo-rede-distribuicao.md).

**Premissas da malha viária:**

| Parâmetro | Valor | Justificativa |
|---|---|---|
| Velocidade média | 25 km/h | Deslocamento urbano porta a porta na RMR |
| Fator de sinuosidade | 1,4 | Percurso 40% maior que a linha reta em malha urbana densa |
| Vizinhos por unidade | 4 | Mantém o grafo esparso, consistente com a modelagem de lista de adjacência |

Detalhes em [`docs/premissas-malha-rede.md`](premissas-malha-rede.md).

---

## 4. Algoritmo de roteamento — Dijkstra

O algoritmo de caminho mínimo opera sobre o dígrafo ponderado por tempo.

**Implementação:** [`Dijkstra<T>`](../src/main/java/com/rotavital/estruturas/Dijkstra.java)

| Característica | Detalhes |
|---|---|
| Estrutura de prioridade | `PriorityQueue` (heap binário mínimo) |
| Complexidade | O((V + E) log V) |
| Reconstrução de rota | Mapa de predecessores |
| Invariante | Rejeita pesos negativos em `Grafo.inserirAresta` — pré-condição do Dijkstra garantida na inserção |

**Teste de referência:** [`DijkstraTest#redeDeExemplo`](../src/test/java/com/rotavital/estruturas/DijkstraTest.java) valida o caminho mínimo de 23 minutos em uma rede de 5 vértices.

---

## 5. Comunicação entre componentes

O Spring Boot expõe todos os recursos via HTTP/REST sobre TLS. O Nginx atua como terminador TLS e reverse proxy:

```
Cliente HTTP
    └── Nginx (443 TLS) ──> Spring Boot (8080 HTTP) ──> H2 (memória)
```

A configuração do reverse proxy está em `/etc/nginx/sites-available/rotavital`. O deploy é feito via SSH/SCP pelo GitHub Actions, conforme documentado em [`docs/evidencias-unidade-1-so.md`](evidencias-unidade-1-so.md).

---

## 6. Rastreabilidade: histórias de usuário → decisões de rede

| HU | Impacto na rede |
|---|---|
| HU-01 (cadastro de hemocentro) | Endpoint `POST /api/v1/hemocentros`; cria vértice de origem no grafo |
| HU-02 (cadastro de hospital) | Endpoint `POST /api/v1/hospitais`; cria vértice de destino no grafo |
| HU-03 (cadastro de bolsa) | Endpoint `POST /api/v1/bolsas` |
| HU-04 (requisição de hemocomponente) | Endpoint `POST /api/v1/requisicoes`; dispara alocação via Dijkstra |
| HU-05 (rota de entrega) | Endpoint `POST /api/v1/rotas`; resultado direto do caminho mínimo |
| HU-06 (indicadores de estoque) | Endpoints `GET /api/v1/indicadores/*`; leitura sem escrita na rede |

A rede distribuída serve como infraestrutura para todas as HUs operacionais: cada requisição HTTP atravessa Nginx → Spring Boot → H2, e o resultado do Dijkstra é serializado como JSON na resposta.
