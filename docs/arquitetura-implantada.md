# Rota Vital — Arquitetura implantada

**Projeto Integrador 3 · Equipe E2 · Referência para SO U2**

Este documento descreve o que **está de fato em execução** hoje. Não é um
diagrama de aspiração: cada componente listado aqui tem evidência versionada no
repositório. A distinção entre o que é atual e o que é proposto é mantida em
todo o texto; para os cenários de evolução (moderado e alto), consulte
[`so-u2-cenarios-dimensionamento.md`](so-u2-cenarios-dimensionamento.md).

---

## 1. Visão geral

```
         internet
             │  HTTPS 443
             ▼
     ┌───────────────────┐
     │       Nginx        │  proxy reverso + TLS (Certbot / Let's Encrypt)
     │  rsc3-rotavital    │  domínio DuckDNS · porta 80 → redireciona 443
     │   .duckdns.org     │
     └────────┬───────────┘
              │  HTTP  127.0.0.1:8081  (loopback — inalcançável de fora)
              ▼
     ┌───────────────────┐
     │   Spring Boot 3   │  Java 21 · perfil prod · porta 8081
     │   (JVM única)     │  heap −Xmx256m · metaspace max 128m · SerialGC
     │                   │
     │  H2 em memória    │  banco embutido na JVM · dados perdidos ao reiniciar
     └───────────────────┘
              │
     systemd: rota-vital.service   (gerencia reinicialização automática)
     usuário: rotavital             (sem shell, sem home, sem root)
```

A VM que hospeda o Rota Vital **também hospeda outra aplicação** (Flux, Node.js
na porta 3000). Nenhuma das duas tem a máquina só para si.

---

## 2. Componentes e evidências

| Componente | Configuração efetiva | Fonte versionada |
|---|---|---|
| **VM** | Compartilhada (`flux`, us-west1-a); 2 vCPU compartilhados; ~978 MB RAM total; ~577 MB disponíveis com Flux em execução | [`deploy.md`](deploy.md), [`provisionar.sh`](../infra/provisionar.sh) |
| **Nginx** | Proxy reverso nas portas 80/443; TLS via Certbot (Let's Encrypt); serve os estáticos do frontend Angular em `/app/` diretamente de `/var/www/rota-vital-angular/`; encaminha `/api/` e `/` para `127.0.0.1:8081`; bloqueia `/h2-console`; logs separados em `/var/log/nginx/rota-vital-*.log` | [`nginx-rota-vital.conf`](../infra/nginx-rota-vital.conf) |
| **TLS** | Certificado emitido por Certbot com `--nginx`; renovação automática pelo Certbot; bloco `listen 443 ssl` gerado pelo Certbot (não versionado); porta 80 redireciona para HTTPS | [`nginx-rota-vital.conf`](../infra/nginx-rota-vital.conf), [`deploy.md § 1.5`](deploy.md) |
| **Spring Boot** | Java 21; perfil `prod`; porta `8081`; bind em `127.0.0.1` (não em `0.0.0.0`); Actuator público restrito a `/actuator/health` | [`application-prod.properties`](../src/main/resources/application-prod.properties) |
| **JVM** | Heap máximo 256 MiB (`-Xmx256m`); metaspace limitado a 128 MiB; SerialGC (uma thread — adequado para 2 vCPU compartilhados); RSS medido: 331 MB estável após 100 requisições | [`provisionar.sh`](../infra/provisionar.sh) |
| **H2 em memória** | Banco embutido na JVM; sem arquivo persistente; console desligado em produção (`spring.h2.console.enabled=false`); sem driver PostgreSQL no `pom.xml` | [`application-prod.properties`](../src/main/resources/application-prod.properties), [`pom.xml`](../pom.xml) |
| **systemd** | Serviço `rota-vital.service`; `Restart=always`; `RestartSec=10`; `ProtectSystem=strict`; `PrivateTmp=true`; `NoNewPrivileges=true`; logs via `journalctl -u rota-vital` | [`provisionar.sh`](../infra/provisionar.sh) |
| **CI/CD** | GitHub Actions: build → testes → empacotamento → deploy por SSH/SCP; deploy somente na `main`; confirma health interno (`127.0.0.1:8081`) e externo (URL pública) | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) |
| **Carga inicial** | `CargaInicial` popula 25 bolsas por hemocentro (100 total em 4 hemocentros) e requisições sintéticas para 6 hospitais; semente 42; executa em produção a cada reinício (`@Profile("!test")`) | [`CargaInicial.java`](../src/main/java/com/rotavital/config/CargaInicial.java) |

### 2.1 Fluxo de uma requisição

```
browser
  │  HTTPS → 443
  ▼
Nginx (rsc3-rotavital.duckdns.org)
  │  termina TLS
  │  adiciona X-Real-IP, X-Forwarded-For, X-Forwarded-Proto
  │  HTTP → 127.0.0.1:8081
  ▼
Spring Boot (perfil prod)
  │  processa no pool de threads
  │  lê/escreve no H2 em memória
  ▼
resposta HTTP → Nginx → HTTPS → browser
```

Nenhuma requisição chega ao Spring Boot sem passar pelo Nginx. A porta 8081
escuta apenas em `127.0.0.1`; o SO recusa conexões externas antes que o Nginx
precise decidir qualquer coisa.

---

## 3. Decisões de segurança ativas

| Decisão | Mecanismo | Por quê importa |
|---|---|---|
| Console H2 inacessível | `spring.h2.console.enabled=false` + bloco Nginx `return 404` + `PerfilProducaoTest` quebra o build se religar + workflow confere após deploy | H2 sobe com usuário `sa` e senha vazia; console exposto na internet equivale a acesso SQL irrestrito sem autenticação |
| Aplicação inalcançável diretamente | `server.address=127.0.0.1` | Mesmo que a porta 8081 fosse aberta no firewall por engano, não haveria o que alcançar |
| Sem root | Usuário `rotavital` sem shell e sem home; `NoNewPrivileges=true` | Exploração de falha fica confinada ao que o usuário pode fazer; protege também o Flux na mesma VM |
| Sudo restrito | `NOPASSWD` apenas para `/opt/rota-vital/publicar.sh` | Vazamento de chave SSH permite deploy de jar, não root da máquina |
| Sem segredos no repositório | IP, usuário e chave privada em GitHub Secrets; `PerfilProducaoTest.semSegredoVersionado` recusa propriedades de senha ou token sem variável de ambiente | Repositório privado não é garantia; rotação de secret não exige commit |

---

## 4. Limitações do cenário atual

Estas são limitações **estruturais** da topologia hoje implantada. Algumas
podem ser aceitas para o escopo de demonstração; outras são gatilhos diretos
para evoluir ao cenário moderado ou alto de
[`so-u2-cenarios-dimensionamento.md`](so-u2-cenarios-dimensionamento.md).

### 4.1 Perda de dados ao reiniciar

O H2 opera em memória: **todos os dados são perdidos** quando a JVM para.
Reinícios acontecem a cada deploy, em OutOfMemoryError ou por reinicialização
da VM. A `CargaInicial` repopula as 100 bolsas sintéticas, mas requisições,
alocações, rotas, leituras de temperatura e histórico de indicadores não são
restaurados.

> **Gatilho para evoluir:** qualquer requisito de preservar histórico entre
> reinícios, independentemente do RPS, justifica migrar para PostgreSQL
> (cenário moderado). Não é uma questão de carga; é funcional.

### 4.2 VM compartilhada sem reserva de recursos

A VM hospeda também o Flux. Não há cgroups, reservas de CPU nem `MemoryLimit`
no systemd isolando os dois processos. Um pico de consumo de memória do
Rota Vital pode pressionar a memória disponível para o Flux — e vice-versa.

Referência de memória: total ~978 MB, disponível com Flux em execução ~577 MB,
RSS medido do Rota Vital 331 MB, folga estimada ~246 MB. Essa folga não é
garantida: o consumo do Flux varia, e o RSS da JVM cresce com a massa de dados.

> **Sinal de alerta:** `MemAvailable` abaixo de 200 MiB por 5 minutos, OOM
> ou degradação do Flux. Resposta: reduzir heap ou migrar para VM dedicada
> (cenário moderado). **Não aumentar o heap sem antes confirmar a folga atual.**

### 4.3 Sem reserva garantida de CPU

Os 2 vCPU são compartilhados com o Flux e com outros processos do SO. A
estimativa de 0,051 núcleo no regime sustentado do cenário baixo
(1,07 RPS) é confortável, mas não há isolamento que impeça o Flux de consumir
os dois núcleos em um pico.

### 4.4 Ponto único de falha em cada camada

Há uma instância de cada componente: um Nginx, uma JVM, um H2. Reiniciar
qualquer um deles interrompe o serviço. O deploy já causa breves segundos de
indisponibilidade a cada publicação.

> Isso é aceitável para uma demonstração acadêmica. **Não é aceitável** se
> houver requisito de continuidade. Os cenários moderado e alto também mantêm
> uma instância de cada camada; alta disponibilidade exigiria dimensionamento
> adicional específico.

### 4.5 Capacidade de disco não confirmada

O repositório não registra o espaço em disco atual da VM. O documento de
provisionamento usa 20 GiB como referência de planejamento para SO/pacotes/logs,
mas esse número **não foi medido** nesta VM e precisa ser confirmado antes de
qualquer plano de retenção de dados.

### 4.6 Sem paginação nem limite nas listagens

Endpoints como `GET /api/v1/bolsas`, e os indicadores que carregam coleções
inteiras (`IndicadorServico`), não limitam o volume retornado. Com a massa
sintética atual (100 bolsas), isso não é problema. Com a massa do cenário
moderado (2.000 bolsas) ou alto (20.000), CPU e memória crescem com o volume
e podem ultrapassar os limites da JVM atual.

### 4.7 Telemetria de temperatura simulada e síncrona

`POST /api/v1/rotas/{id}/temperaturas` processa a leitura e avalia os
componentes de forma síncrona, na thread do request. Não há fila nem
desacoplamento. Com 20 rotas em trânsito lendo a cada 15 s (cenário
moderado, 1,33 RPS de telemetria), isso é viável. Com 100 rotas a cada 5 s
(cenário alto, 20 RPS de telemetria), a pressão sobre o pool de threads
e a memória do H2 precisa ser avaliada.

---

## 5. Coerência com os cenários dimensionados

O documento [`so-u2-cenarios-dimensionamento.md`](so-u2-cenarios-dimensionamento.md)
define três cenários de evolução a partir desta base. A tabela abaixo mostra
onde cada componente do cenário atual se enquadra e o que muda em cada evolução.

| Componente | **Atual (implantado)** | Cenário Baixo (conservar) | Cenário Moderado | Cenário Alto |
|---|---|---|---|---|
| VM | Compartilhada · ~978 MB · 2 vCPU divididos | Mesma VM compartilhada | VM dedicada 2 vCPU / 4 GiB | VM app 4 vCPU / 8 GiB + VM dados 2 vCPU / 4 GiB |
| Nginx + TLS | ✅ Existente | Mantido | Mantido | Mantido |
| Spring Boot (JVM única) | ✅ Existente | Mantido | Mantido | Mantido |
| Banco | H2 em memória — **sem persistência** | H2 em memória | PostgreSQL local (`127.0.0.1:5432`) | PostgreSQL em VM separada (IP privado) |
| Backup externo | ❌ Nenhum (H2 sem arquivo) | Sem histórico garantido | Cópia diária criptografada (reserva 15 GiB) | Cópia diária criptografada (reserva 180 GiB) |
| systemd + usuário próprio | ✅ Existente | Mantido | Mantido | Mantido |
| CI/CD (GitHub Actions) | ✅ Existente | Mantido | Mesmo fluxo, alvo atualizado | Mesmo fluxo para VM de aplicação |
| RPS sustentado estimado | Demonstração (~1 RPS) | ≤ 1,07 RPS | ≤ 6,33 RPS | ≤ 40 RPS |

> **O cenário baixo é o atual.** A coluna "Cenário Baixo" no documento do
> Fernando descreve o que está implantado hoje com a topologia preservada —
> não é uma proposta de mudança, é a referência de partida. Moderado e alto
> são evoluções que ainda **não foram implementadas**.

### 5.1 Pré-requisitos para evoluir ao cenário moderado

Os itens abaixo são mudanças de código e infraestrutura que precisam acontecer
antes de migrar para PostgreSQL — independentemente de qual VM será usada:

- Adicionar driver PostgreSQL ao `pom.xml`
- Configurar credenciais fora do repositório (variáveis de ambiente ou secrets)
- Criar migrations com Flyway ou Liquibase; substituir `ddl-auto=create` por validação de schema
- Ajustar `CargaInicial` para não repopular dados em um banco persistente a cada restart
- Testar consultas e DDL contra PostgreSQL (H2 e PostgreSQL têm dialetos diferentes)
- Ensaiar restauração de backup em ambiente separado, medindo RPO/RTO

Nenhum desses passos foi executado. O H2 atual não é uma limitação temporária
esquecida: é uma escolha explícita para manter a demonstração simples enquanto
os pré-requisitos não são cumpridos.

---

## 6. Referências cruzadas

| Documento | Relação com este |
|---|---|
| [`deploy.md`](deploy.md) | Procedimento completo de provisionamento, pipeline CI/CD e troubleshooting |
| [`so-u2-cenarios-dimensionamento.md`](so-u2-cenarios-dimensionamento.md) | Três cenários de evolução (baixo = atual, moderado, alto), dimensionamento de recursos e critérios de migração |
| [`so-u2-orcamento-mensal.md`](so-u2-orcamento-mensal.md) | Cotação dos recursos no Google Cloud (Oregon) para cada cenário |
| [`W06-topologia-diferencas.md`](W06-topologia-diferencas.md) | Comparação entre o que foi planejado (Redis, RabbitMQ, réplicas) e o que foi implantado |
| [`infra/nginx-rota-vital.conf`](../infra/nginx-rota-vital.conf) | Configuração completa do Nginx com comentários de cada decisão |
| [`infra/provisionar.sh`](../infra/provisionar.sh) | Script de provisionamento idempotente com memória de cada escolha |
