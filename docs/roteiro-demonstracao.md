# Roteiro da demonstração do produto

**Tarefas:** PI3-219 (demonstração rápida de POO) e PI3-224 (demonstração da entrega final integrada)

Dois roteiros com o mesmo caminho de cliques: um de **1 minuto**, para o slide 8 da apresentação de POO ([apresentacao-final-poo.md](apresentacao-final-poo.md)), e um de **5 minutos**, para a apresentação final do projeto, que passa pelas cinco disciplinas. Os dois servem tanto para **gravar** quanto para **apresentar ao vivo**.

Todos os endereços foram testados em produção em 17/11/2026.

---

## 1. Antes de apresentar (no mesmo dia)

O banco é H2 em memória: cada deploy reinicia a aplicação e recarrega a carga inicial. Os números que aparecem na tela mudam com a data e depois de qualquer deploy, então **confira na hora**, não decore.

| Conferir | Como | Esperado |
|---|---|---|
| Aplicação no ar | abrir `https://rsc3-rotavital.duckdns.org/actuator/health` | `{"status":"UP"}` |
| Último deploy verde | aba Actions do GitHub, último run da `main` | os 7 jobs verdes |
| Angular no ar | abrir `/app/` | o painel carrega com gráficos |
| Nenhum deploy em andamento | aba Actions | nenhum run rodando: um deploy no meio da apresentação reinicia a aplicação por ~25 s |

**Aquecer antes de começar.** Abra cada tela do roteiro uma vez, alguns minutos antes. A primeira requisição depois de um deploy espera a JVM terminar de subir.

**Plano B.** Grave a demonstração de 1 minuto com antecedência, mesmo que a ideia seja apresentar ao vivo. Se a rede da sala falhar, roda o vídeo.

---

## 2. Demonstração de POO: 1 minuto (slide 8)

Mostra as histórias da Entrega 04 e a ligação entre os dois front-ends.

| Tempo | Tela | Ação | Fala |
|---|---|---|---|
| 0:00 | `/painel/HC01/risco` (HU-04) | Clicar em 7, depois em 30 dias | "O estoque crítico do HEMOPE: o que vence em 7 dias, da bolsa mais urgente para a menos. Mudo a janela para 30 dias e a lista cresce." |
| 0:20 | mesma tela | Apontar a contagem | "Hoje são `N` bolsas vencendo em 7 dias. Bolsa já vencida não aparece misturada: ela é descarte, não urgência." |
| 0:30 | `/docs` → `GET /api/v1/rotas/calcular` (HU-05) | `origemId=HC01`, `destinoId=HC06` | "Antes de despachar, o sistema calcula o caminho mais rápido na malha. Do HEMOPE ao Hospital das Clínicas: cerca de 18 minutos." |
| 0:50 | qualquer tela do Thymeleaf | Clicar em **ANGULAR →** | "A mesma API alimenta dois front-ends: este painel em Thymeleaf e um painel de gestão em Angular, a um clique." |
| 1:00 | fim | | |

Em 17/11, os números de referência eram 5 bolsas em risco em 7 dias e 18,0 minutos até o Hospital das Clínicas.

---

## 3. Demonstração da entrega final: 5 minutos

Uma passagem por disciplina, na ordem do caminho de uma bolsa: entra no estoque, é escolhida, viaja, é monitorada.

| Tempo | Disciplina | Tela | Ação | Fala |
|---|---|---|---|---|
| 0:00 | POO | `/` | Mostrar o mapa | "A rede: quatro hemocentros em cima, seis hospitais embaixo. Cada linha é um trecho da malha." |
| 0:30 | EST | `/painel/HC01` | Rolar pelos indicadores | "O painel do HEMOPE: estoque, cobertura, descarte e risco de desabastecimento por tipo sanguíneo, cada número com unidade e período." |
| 1:10 | AED | `/painel/HC01/fila` | Mostrar a fila | "A fila de saída é FEFO: quem vence antes sai antes. Por baixo, uma lista encadeada com inserção ordenada." |
| 1:40 | AED | `/docs` → `GET /api/v1/rotas/roteiro` | `origemId=HC01`, `destinos=HC04,HC05,HC06` | "Para várias entregas numa saída, o vizinho mais próximo monta a ordem de visita. É uma heurística: bom roteiro, não necessariamente o ótimo." **Só depois do PR #107 entrar na main.** |
| 2:20 | POO | `/painel/HC01/risco` | Clicar em 7 e 30 dias | (fala do roteiro de 1 minuto) |
| 2:50 | POO | **ANGULAR →** | Mostrar `/app/bolsas` e os filtros | "O mesmo dado no painel de gestão em Angular." |
| 3:20 | RSD | (só fala) | Citar a telemetria | "Os veículos mandam temperatura e GPS por HTTPS, como um sensor real. É simulado: não há caminhão nem sensor." |
| 3:40 | SO | GitHub, aba Actions | Mostrar o último run da `main` | "Cada merge passa por build, testes e deploy automático: do commit à produção em menos de três minutos." Para fazer o ciclo inteiro ao vivo, seguir [demonstracao-deploy-continuo.md](demonstracao-deploy-continuo.md). |
| 4:30 | todas | (só fala) | Encerrar | "Dados sintéticos, telemetria simulada e regra de compatibilidade didática. Tudo está no ar e no repositório." |
| 5:00 | fim | | | |

**A frase de encerramento é obrigatória.** Declarar que os dados são sintéticos e a telemetria é simulada é critério de aceite da apresentação.

---

## 4. O que não mostrar

- **O console do H2** (`/h2-console`): está bloqueado em produção de propósito, e responde 404.
- **Dados criados à mão antes de um deploy**: somem quando a aplicação reinicia.
- **O link do screencast de código da Entrega 02**: está hospedado num domínio de terceiros, fora do controle da equipe. Se for preciso mostrá-lo, conferir antes que ele ainda abre.
