# Planejamento e execução da refatoração: DDD, modelo rico e Clean Architecture

**Ticket:** [PI3-247](https://csprj-adsr-3p-e2.atlassian.net/browse/PI3-247) · **Disciplina:** SO - Infraestrutura de Software · **Unidade 02**
**Data:** 24/11/2026 · **Responsável:** Renato Santos Chong
**Origem:** comentários do professor para o planejamento de 24/11 e a entrega de 01/12

Este documento responde aos cinco pontos levantados pelo professor. Cada seção diz o que foi encontrado, o que mudou no código e como conferir. Ele é também a especificação da refatoração (SDD): foi escrito a partir do diagnóstico, antes de cada mudança, e os testes de domínio foram escritos a partir dele.

---

## 1. DDD e modelo rico

### O que estava errado

As entidades eram anêmicas: guardavam dados e expunham `setStatus`, e as regras de negócio moravam nos serviços. Como a regra não tinha dono, cada serviço que precisava dela escrevia a sua versão, e as cópias divergiram.

| Problema | Evidência no código antes da refatoração |
|---|---|
| Status da bolsa trocado sem validação | `setStatus` chamado em 9 pontos de 3 serviços; só o `BolsaServico` consultava a tabela de transições |
| Transição fora da tabela | o cancelamento da rota levava a bolsa de EM_TRANSITO para RESERVADA, transição que a tabela proibia |
| Regra "a bolsa serve para este pedido?" em 3 lugares | `SelecaoFefo`, `RequisicaoServico.alocar` e `ServicoAlocacaoRota` |
| Uma das cópias já divergia | o `ServicoAlocacaoRota` comparava grupos com `!=` e recusava O- para um receptor A+, que a API aceitava |
| Requisição com status atribuível de fora | `Requisicao.setStatus` público e `recalcularStatus` chamado pelo serviço |

### O que mudou

| Entidade | Antes | Depois |
|---|---|---|
| `Bolsa` | `setStatus(StatusBolsa)` | `reservar`, `liberar`, `despachar`, `entregar`, `retornarAoHemocentro`, `descartar`; cada operação valida a transição |
| `Bolsa` | elegibilidade espalhada | `podeAtender`, `motivoDeRecusa`, `exigirQuePodeAtender`: a única definição, usada pelos três caminhos de alocação |
| `StatusBolsa`, `StatusRota` | tabelas `TRANSICOES` nos serviços | `podeIrPara(destino)` no próprio enum |
| `Rota` | setters de status, saída e chegada; regras no `RotaServico` | `embarcar`, `desembarcar`, `sair`, `concluir`, `cancelar`; a rota arrasta o status das bolsas |
| `Requisicao` | `setStatus` público; o serviço mexia em itens e alocações | raiz do agregado: `alocar`, `desfazerAlocacao`, `cancelar`, `adicionarItem`, `removerItem`; `recalcularStatus` privado |
| `ItemRequisicao` | `adicionarAlocacao` e `removerAlocacao` públicos | visibilidade de pacote: só a raiz altera |
| Exceções de negócio | `servico.excecao` | `dominio.excecao`, porque agora quem lança é a entidade |

Efeito nos serviços: `RequisicaoServico` caiu de 385 para 260 linhas, e `RotaServico` e `BolsaServico` perderam as tabelas de transição. O serviço passa a fazer só o papel de aplicação: carregar, chamar a operação do domínio, salvar e publicar o evento.

O mapa completo do domínio, com entidades, agregados e ciclos de vida, está em [`mapa-dominio-ddd.md`](mapa-dominio-ddd.md), atualizado para o estado novo.

---

## 2. Camadas e Clean Architecture

```
   api, web  ──►  aplicacao  ──►  dominio  ◄──  infraestrutura
 (controllers,   (casos de uso:    (entidades ricas,   (carga inicial,
  DTOs, erros)    transação,         regras, contratos   OpenAPI; Spring
                  eventos)           de repositório)     Data gera os repos)
```

| Camada | Pacote | Antes |
|---|---|---|
| Domínio | `dominio`, `dominio.enums`, `dominio.excecao`, `dominio.repositorio` | `dominio`, `repositorio`, `servico.excecao` |
| Aplicação | `aplicacao`, `aplicacao.evento` | `servico` |
| Infraestrutura | `infraestrutura` | `config` |
| Apresentação | `api`, `web` | sem mudança |

Os algoritmos das disciplinas de AED e Estatística (`estruturas`, `alocacao`, `compatibilidade`, `rede`, `estatistica`, `paralelo`) ficaram como estão. São bibliotecas de apoio ao domínio, sem dependência de Spring.

### Decisões e o que ainda falta

| Decisão | Justificativa |
|---|---|
| As entidades mantêm `@Entity` | separar entidade de domínio e entidade de banco dobraria as classes sem mudar regra nenhuma. É um meio-termo consciente, comum em projetos Spring |
| O contrato do repositório mora em `dominio.repositorio` | guardar e buscar bolsas é um conceito do domínio; a implementação é gerada pelo Spring Data em tempo de execução. Assim a aplicação depende só do domínio |

Pendências conhecidas, registradas para não serem esquecidas:

1. **A aplicação ainda recebe e devolve DTOs de `api.dto`.** É uma seta de dentro para fora. O próximo passo é a aplicação ter os próprios comandos e resultados, e o controller converter.
2. **Os contratos de repositório ainda estendem `JpaRepository`.** Para o domínio ficar livre do Spring Data, cada contrato passaria a ser uma interface pura, implementada na infraestrutura.
3. **`HemocentroServico` e `HospitalServico` repetem cerca de 90% do código.**
4. **`Veiculo` é entidade sem uso nenhum.** O grupo decidiu mantê-lo (README, seção de pendências); fica registrado que ele puxa a cobertura do domínio para baixo.

---

## 3. O que mudou efetivamente e o que fica para depois

### Entregue em 24/11/2026

| # | Entrega | Commit |
|---|---|---|
| 1 | Exceções de negócio movidas para o domínio | `refactor(dominio): move as excecoes de negocio para o dominio` |
| 2 | `Bolsa` rica e elegibilidade única (corrige a divergência do `ServicoAlocacaoRota`) | `refactor(dominio): Bolsa rica e elegibilidade em um lugar so` |
| 3 | `Rota` como agregado do embarque e da viagem | `refactor(dominio): Rota como agregado do embarque e da viagem` |
| 4 | `Requisicao` como raiz do agregado | `refactor(dominio): Requisicao como raiz do agregado` |
| 5 | JaCoCo com trava de cobertura no CI | `build: cobertura com JaCoCo e trava no CI` |
| 6 | Pacotes organizados em camadas | `refactor: organiza os pacotes nas camadas da Clean Architecture` |

O contrato da API não mudou: os testes de controller e os cenários de aceitação passam sem ajuste.

### Próximos passos, em ordem

1. Comandos e resultados próprios da camada de aplicação (pendência 1 da seção 2).
2. `TemperaturaServico`: levar a regra "leitura só em rota EM_TRANSITO" para a `Rota`.
3. Unificar o que é comum a `HemocentroServico` e `HospitalServico`.
4. Contratos de repositório puros (pendência 2).
5. Subir a meta de cobertura do domínio para 90% de linhas e 80% de ramos.

---

## 4. TDD e cobertura

### Como foi feito

Os testes de domínio foram escritos antes da implementação. O primeiro passo de cada entidade foi um teste que nem compilava, porque os métodos ainda não existiam (vermelho). Depois veio o código mínimo para passar (verde). Não existia nenhum teste unitário de domínio antes; agora são três classes com 39 testes, mais um no `ServicoAlocacaoRotaTest` que prova a correção da divergência:

| Classe de teste | O que prova |
|---|---|
| `BolsaTest` | caminho feliz do ciclo de vida, transições proibidas, estados finais, bolsa vencida, elegibilidade com hemácias e plasma, último dia de validade |
| `RotaTest` | embarque válido e inválido, saída sem carga, cancelamento devolvendo a carga, estado final |
| `RequisicaoTest` | alocação parcial e completa, bolsa incompatível, desfazer, cancelar, idempotência do cancelamento, itens |

Os testes antigos que montavam o cenário com `setStatus` passaram a usar `BolsasDeTeste.levarAte`, que percorre o caminho legal. Assim, nem o teste pula etapas do domínio.

### Cobertura medida com JaCoCo

Suite completa: 544 testes, 0 falhas (`./mvnw clean test`). As linhas de `dominio` somam o pacote e os subpacotes (`enums`, `excecao`, `repositorio`), a mesma medida da linha de base.

| Escopo | Antes (24/11, início) | Depois (24/11, fim) | Trava no CI |
|---|---|---|---|
| Total, linhas | 86,5% | 87,6% | 85% |
| Total, ramos | 77,2% | 78,4% | 75% |
| `dominio`, linhas | 77,1% | 84,7% | 80% |
| `dominio`, ramos | **46,2%** | **75,2%** | 70% |

O salto nos ramos do domínio é a medida mais direta do modelo rico: antes não havia decisão nas entidades para testar.

A trava funciona como catraca: fica logo abaixo do valor atingido, então a cobertura pode subir e nunca cair. O JaCoCo roda na fase `test`, e o mesmo `./mvnw test` do CI reprova o build e bloqueia o deploy se a cobertura cair. O relatório sai como artefato `relatorio-cobertura` em cada execução do pipeline.

### BDD

Os testes de domínio usam o formato Dado / Quando / Então no `@DisplayName`, por exemplo "Dado uma bolsa vencida, quando reservar, então recusa". O projeto já tinha cenários de aceitação ponta a ponta em `CenariosAceitacaoTest` e `FluxoRequisicaoBolsaRotaTest`, que exercitam a API real e serviram de rede de segurança na refatoração. Não foi adotado Cucumber: o custo de configuração não se pagava no prazo.

---

## 5. SDD

A ordem de trabalho foi esta:

1. diagnóstico, com cada problema apontado em arquivo e linha;
2. especificação, que é este documento: o que muda em cada entidade e por quê;
3. testes escritos a partir da especificação;
4. código;
5. atualização do mapa de domínio, para a documentação não voltar a divergir do código.

A especificação está ligada ao ticket PI3-247 no Jira. Cada commit cita o ticket e explica o motivo da mudança, não só o que mudou.

---

## Como conferir

```bash
./mvnw clean test                       # suite inteira + trava de cobertura
open target/site/jacoco/index.html      # relatório de cobertura
git log --oneline --grep=PI3-247        # os commits da refatoração
grep -rn "setStatus" src/main/java      # não deve haver nenhum no domínio
```
