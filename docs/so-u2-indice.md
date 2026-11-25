# SO U2: índice das entregas de Infraestrutura de Software

**Tarefa:** PI3-238

Ponto de entrada para tudo o que a disciplina de SO entregou na Unidade 2. Cada linha diz o que o material prova e onde ele está.

---

## 0. Parte 2: planejamento da remodelagem

| O que prova | Onde |
|---|---|
| Documento da Parte 2 (DDD, três camadas, microsserviços, ADRs, cenários com Lei de Little e orçamento) | [arquitetura/planejamento-u2-parte2.pdf](arquitetura/planejamento-u2-parte2.pdf) e a pasta [arquitetura/](arquitetura/) |

## 1. Arquitetura implantada

| O que prova | Onde |
|---|---|
| A arquitetura em produção: VM compartilhada, Nginx com TLS, Spring Boot e H2 em memória, e as limitações desse cenário | [arquitetura-implantada.md](arquitetura-implantada.md) |
| Como reproduzir o ambiente do zero: provisionamento, subdomínio, Nginx, HTTPS e os segredos do pipeline | [deploy.md](deploy.md) |
| O modelo de domínio em DDD, com a evidência de cada afirmação no código | [mapa-dominio-ddd.md](mapa-dominio-ddd.md) e [diagramas/dominio.mmd](diagramas/dominio.mmd) |

## 2. Cenários e dimensionamento

| Cenário | Decisão | Diagrama |
|---|---|---|
| Baixo | conservar o monólito na VM compartilhada | [cenario-baixo.png](img/so-u2/cenario-baixo.png) |
| Moderado | VM dedicada e banco persistente local | [cenario-moderado.png](img/so-u2/cenario-moderado.png) |
| Alto | separar aplicação e dados, mantendo uma JVM | [cenario-alto.png](img/so-u2/cenario-alto.png) |

Premissas, memória de cálculo de CPU, rede e armazenamento e o inventário de componentes: [so-u2-cenarios-dimensionamento.md](so-u2-cenarios-dimensionamento.md). As fontes dos diagramas ficam em `diagramas/so-u2-*.mmd`.

## 3. Orçamento

| Cenário | Custo mensal estimado (USD) | Limite com reserva de 15% (USD) |
|---|---:|---:|
| Baixo | 12,65 | 14,55 |
| Moderado | 63,07 | 72,53 |
| Alto | 234,15 | 269,27 |

Preços, prints das calculadoras e a justificativa de cada item: [so-u2-orcamento-mensal.md](so-u2-orcamento-mensal.md). A reserva de 15% é decisão de planejamento, não tarifa do Google Cloud.

## 4. Concorrência e sincronização

Não há documento dedicado: o mecanismo está no código, comprovado pelos testes, e a história de como os testes o encontraram está em [tdd-no-projeto.md](tdd-no-projeto.md), seção 2.

| Ticket | O que foi feito | Código | Teste |
|---|---|---|---|
| PI3-30 | Entrada de lotes de bolsas em paralelo, 4 por vez, cada uma na própria transação: uma recusada não desfaz as outras | `servico/IngestaoConcorrenteServico.java` | `IngestaoConcorrenteTest` |
| PI3-108 | Bloqueio otimista (`@Version`) no item da requisição, e repetição da alocação em até 3 tentativas quando outra operação chega primeiro | `dominio/ItemRequisicao.java`, `servico/AlocacaoComRepeticao.java` | `ContagemItemConcorrenteTest` |
| PI3-109 | Teste que demonstra a ausência de condição de corrida, com threads largando juntas por `CountDownLatch` | | `ContagemItemConcorrenteTest`, `CondicaoCorridaAlocacaoTest` |

**O que os testes mostraram.** O caso óbvio, duas alocações disputando a mesma bolsa, passou mesmo sem proteção: a restrição `UNIQUE` que o `@OneToOne` de `Alocacao` cria já barrava. A corrida real apareceu em outro lugar, várias bolsas diferentes sendo alocadas ao mesmo item: duas threads liam o mesmo contador e uma escrita se perdia. Sem o `@Version`, o teste falha com "esperado 2, registrado 1".

**Um erro do caminho que vale contar.** A primeira tentativa de repetir a alocação ficou dentro do próprio serviço, e nenhuma alocação era gravada: um método `@Transactional` chamado de dentro da mesma classe não passa pelo proxy do Spring. Por isso a repetição mora numa classe separada.

Os testes estão em `src/test/java/com/rotavital/concorrencia/`.

## 5. Deploy contínuo

| O que prova | Onde |
|---|---|
| A demonstração reproduzível, do commit à produção, com o registro de uma execução real (2 min 42 s) | [demonstracao-deploy-continuo.md](demonstracao-deploy-continuo.md) |
| O portão de qualidade barrando um commit ruim (esteira vermelha do PR #72) e a esteira verde | [evidencias/e3-pipeline.md](evidencias/e3-pipeline.md) |
| O pipeline em si: build, testes, imagem, empacotamento, deploy da API e deploy do front-end | `.github/workflows/ci.yml` |

## 6. TDD e BDD

| O que prova | Onde |
|---|---|
| Como o TDD foi aplicado, o que cada teste protege e a corrida reproduzida | [tdd-no-projeto.md](tdd-no-projeto.md) |
| Cenários Dado/Quando/Então ligados aos testes que os implementam | [cenarios-aceitacao.md](cenarios-aceitacao.md) |

## 7. Unidade 1

Aplicação no ar, pipeline com os jobs encadeados e deploy automático, como entregues no checkpoint da U1: [evidencias-unidade-1-so.md](evidencias-unidade-1-so.md).
