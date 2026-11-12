# Roteiro dos screencasts da Entrega 04

**Tarefas:** PI3-210 (uso do sistema) e PI3-211 (explicação do código)
**Regra do enunciado:** os dois vídeos precisam de áudio ou legenda, e ficam
linkados na seção "Entrega 04" do README.

Os roteiros abaixo foram escritos para quem vai gravar não precisar decidir
nada na hora: o que abrir, o que clicar, o que dizer. Cada bloco traz o tempo
alvo. Gravar com a aplicação local (`./mvnw spring-boot:run`) evita depender
da rede no dia; a URL pública serve de plano B.

## Antes de gravar

```bash
./mvnw spring-boot:run          # H2 em memoria, com a carga inicial
```

Confira que respondem:

- `http://localhost:8080/` (mapa da rede)
- `http://localhost:8080/painel/HC01/risco` (estoque crítico, HU-04)
- `http://localhost:8080/docs` (API Reference, para as chamadas de rota)

Deixe aberto um cliente HTTP (o próprio Scalar em `/docs`, ou `curl`).

---

## Vídeo 1 · Uso do sistema (alvo: 5 min)

Ênfase nas histórias novas: HU-04 (bolsas em risco), HU-05 (cálculo de rota)
e HU-02 (prazo pela prioridade).

| Tempo | Tela | O que fazer | O que dizer |
|---|---|---|---|
| 0:00 | Mapa (`/`) | Mostrar a rede: 4 hemocentros em cima, 6 hospitais embaixo | "O Rota Vital integra estoque, compatibilidade e rota numa rede de hemocentros e hospitais. Dados sintéticos, telemetria simulada." |
| 0:30 | `/painel/HC01` | Rolar pelos indicadores | "Este é o painel do HEMOPE: estoque por tipo, cobertura, descarte, risco de desabastecimento." |
| 1:00 | `/painel/HC01/risco` **(HU-04)** | Clicar em 7, 15 e 30 dias | "Nova nesta entrega: o estoque crítico. O que vence em 7 dias, da mais urgente para a menos. Trocando a janela para 15 e 30, a lista cresce." |
| 1:40 | mesma tela | Apontar a seção de vencidas | "Bolsa vencida fica separada: não é a mais urgente da fila, é descarte. E quando nada está em risco, a tela diz isso, em vez de mostrar uma tabela vazia." |
| 2:10 | `/docs` → `GET /api/v1/bolsas/em-risco` | Executar com `dias=7&hemocentroId=HC01` | "A tela consome o mesmo endpoint. A resposta traz `emRisco`, `vencidas` e `diasRestantes` por bolsa." |
| 2:40 | `/docs` → `GET /api/v1/rotas/calcular` **(HU-05)** | `origemId=HC01&destinoId=HO06` | "Cálculo de rota: o caminho mais rápido do HEMOPE ao Hospital das Clínicas. O peso é tempo em minutos, não distância. Cada parada vem nomeada." |
| 3:10 | mesma chamada | `destinoId` de um hospital inexistente na malha (cadastre antes um hospital com coordenadas de São Paulo) | "Destino sem trecho não é erro: a resposta diz `alcancavel: false` e a mensagem." |
| 3:30 | `/docs` → `POST /api/v1/rotas` | Corpo só com origem, destino e `previsaoSaida` | "Ao confirmar o despacho sem informar a chegada, o sistema calcula: saída mais o tempo do caminho. A rota nasce PLANEJADA com o caminho registrado." |
| 4:00 | `/docs` → `POST /api/v1/requisicoes` **(HU-02)** | Corpo sem `prazoEntrega`, prioridade EMERGENCIA | "Requisição de emergência sem prazo informado: o prazo vem da prioridade, 2 horas. ROTINA daria 72." |
| 4:30 | `/painel/HC01/fila` | Mostrar a fila FEFO | "E a fila de saída continua a mesma regra da alocação: quem vence antes sai antes, e vencida nunca entra." |
| 4:50 | Encerramento | — | "Tudo que apareceu está no ar em rsc3-rotavital.duckdns.org e no README." |

---

## Vídeo 2 · O código das histórias novas (alvo: 6 min)

Abrir o projeto no editor com estes arquivos em abas, nesta ordem.

| Tempo | Arquivo | O que mostrar | O que dizer |
|---|---|---|---|
| 0:00 | `docs/historias-de-usuario.md` | HU-04 e HU-05, os cenários Gherkin | "Cada história tem cenários Dado/Quando/Então. Eles viraram testes, e vou mostrar o código que os faz passar." |
| 0:40 | `servico/BolsaServico.java` → `emRisco` | O filtro por status, a ordenação, a separação em duas listas, a mensagem | "A regra mora no serviço. 'Em estoque' é DISPONIVEL ou RESERVADA; o resto já saiu. A ordem é validade e depois código, o mesmo desempate da seleção FEFO. Vencida vai para outra lista." |
| 1:40 | `api/BolsaController.java` → `emRisco` | O endpoint de 6 linhas | "O controller só lê parâmetros e devolve. `@Min(1)` barra janela zero com 400." |
| 2:00 | `web/EstoqueWebController.java` → `risco` e `fila` | A tela chama o mesmo serviço | "A tela Thymeleaf usa o mesmo método. E a fila FEFO, que antes ordenava no controller, foi para o serviço nesta entrega: nada de lógica em controller." |
| 2:30 | `servico/RoteirizacaoServico.java` | `unidadesDaRede`, `comoUnidadesDeRede`, `caminhoMinimo` | "Aqui a malha é montada a partir dos hemocentros e hospitais do banco, com as coordenadas de cada endereço, e o Dijkstra da AED roda em cima. Uma unidade cadastrada pela API entra na malha na consulta seguinte." |
| 3:20 | `rede/PremissasMalha.java` → `ALCANCE_MAXIMO_KM` e `rede/CarregadorMalha.java` → `carregar(List)` | A premissa nova e o filtro | "Sem um alcance máximo, a regra dos 4 vizinhos mais próximos ligaria qualquer unidade, até uma em São Paulo. Com 50 km, ela fica isolada, e é assim que 'destino inalcançável' existe de verdade." |
| 4:00 | `servico/RotaServico.java` → `criar` | O cálculo da chegada e o `registrarCaminho` | "Ao planejar sem previsão de chegada, soma-se o tempo do caminho à saída, arredondando para cima, com piso de um minuto. Sem caminho, 409. O caminho e o tempo ficam gravados na rota." |
| 4:40 | `dominio/enums/PrioridadeRequisicao.java` e `servico/RequisicaoServico.java` → `criar` | O `prazoHoras` no enum | "HU-02: o prazo mora na prioridade. Antes era uma tabela duplicada na carga inicial; agora é um lugar só." |
| 5:10 | `test/.../CenariosAceitacaoTest.java` → `BolsasEmRisco`, `CalcularRota` | Os `@DisplayName` com Dado/Quando/Então | "Os cenários da história estão aqui, com o mesmo texto. E `FluxoRequisicaoBolsaRotaTest` percorre o caminho inteiro, conferindo o estado de requisição, bolsa e rota a cada passo." |
| 5:50 | Terminal | `./mvnw test` com o resumo final | "Suíte inteira verde, e é ela que barra o deploy no pipeline." |

## Depois de gravar

1. Publicar os dois vídeos (YouTube não listado, ou o mesmo host dos vídeos
   anteriores).
2. Preencher os dois links na tabela "Artefatos" da seção **Entrega 04** do
   README, substituindo "_a gravar_".
3. Conferir que os vídeos têm áudio ou legenda.
