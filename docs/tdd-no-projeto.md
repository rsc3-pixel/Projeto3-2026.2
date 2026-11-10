# Como o TDD foi aplicado no Rota Vital

**Tarefa:** PI3-187
**Escopo:** o que os testes do repositório protegem, onde um teste encontrou um defeito antes da correção existir, e o que o histórico do git consegue e não consegue provar sobre a ordem do trabalho.
**Ponto de partida:** a seção "Como o TDD foi aplicado" do [story map](story-map.md). Este documento detalha aquela seção, aponta os testes reais e corrige uma afirmação dela que o histórico não sustenta (seção 5).

---

## 1. Resumo

- **O caso mais forte é a PI3-109.** O teste de contagem de item sob concorrência encontrou uma condição de corrida real, com escrita perdida, antes de existir qualquer mecanismo de sincronização. O defeito foi medido, a correção foi guiada pelo teste, e o vermelho pode ser reproduzido hoje (seção 2).
- **Em outros três casos o teste mudou uma decisão de projeto:** a regra de cancelamento, a remessa vazia e a função de hash do índice de estoque (seção 3).
- **A suíte funciona como especificação executável.** Cenários Gherkin das histórias viram testes, regras clínicas e de domínio têm teste nomeado, e dois testes usam oráculos independentes da implementação (seção 4).
- **O histórico do git não prova que o teste veio antes do código.** Nos exemplos citados pelo story map, o commit de teste vem depois do commit de funcionalidade, às vezes no mesmo segundo. A evidência de "vermelho antes do verde" está nas mensagens de commit, nos comentários dos testes e na reprodução descrita aqui, não na ordem dos commits (seção 5).
- **Na esteira, um teste vermelho barra o deploy.** Isso foi demonstrado de propósito no PR #72 (seção 6).

---

## 2. O caso mais forte: a corrida na contagem do item (PI3-109 e PI3-108)

### 2.1 O que aconteceu, na ordem

A mensagem do commit `79fa503` (24/10/2026), que fecha PI3-30, PI3-108 e PI3-109, registra a sequência:

1. **O caso óbvio passou sem proteção nenhuma.** O primeiro teste foi o de duas alocações disputando a **mesma** bolsa ([CondicaoCorridaAlocacaoTest](../src/test/java/com/rotavital/concorrencia/CondicaoCorridaAlocacaoTest.java)). Ele passou antes de qualquer sincronização, porque o `@OneToOne` de `Alocacao` gera uma restrição UNIQUE em `bolsa_id`, e o banco recusa a segunda reserva. A proteção existia por acidente do modelo de dados, não por decisão de concorrência.
2. **O teste de contagem achou a corrida real.** O segundo teste ([ContagemItemConcorrenteTest](../src/test/java/com/rotavital/concorrencia/ContagemItemConcorrenteTest.java)) aloca bolsas **diferentes** ao **mesmo** item, ao mesmo tempo. Cada thread lia `quantidadeAlocada`, somava um e gravava. Duas liam o mesmo valor, e a segunda escrita apagava a primeira. Resultado medido: duas alocações aceitas, uma registrada. Na operação, o sistema acharia o item menos atendido do que estava e alocaria mais sangue do que o hospital pediu.
3. **A primeira correção acertou a contagem e criou outro problema.** `@Version` em `ItemRequisicao` faz a segunda transação falhar em vez de sobrescrever. A contagem ficou exata, mas só uma alocação passava por rodada.
4. **A tentativa de repetir dentro do serviço não gravava nada.** Chamar um método `@Transactional` de dentro da própria classe não passa pelo proxy do Spring, e a transação não acontece. Nessa fase o teste também revelou uma fraqueza própria: com zero aceitas e zero registradas, a igualdade valia e o teste passava sem provar nada. Por isso ele ganhou um piso, `alocadas > 0`, com o comentário que explica o motivo.
5. **A repetição foi para uma classe separada.** [AlocacaoComRepeticao](../src/main/java/com/rotavital/servico/AlocacaoComRepeticao.java) refaz a alocação sobre o estado novo, até três vezes, e é o caminho que a API usa.

Progressão medida, como registrada no commit:

| Etapa | Alocações aceitas | Registradas no item |
|---|---|---|
| Sem proteção | 2 | 1 |
| Com `@Version` | 1 | 1 |
| Com `@Version` e repetição | 2 | 2 |

### 2.2 Reprodução do vermelho, feita em 10/11/2026

Para não depender só do relato, o vermelho foi reproduzido sobre a `main` atual. O `@Version` de `ItemRequisicao` foi comentado temporariamente, o teste rodou três vezes, e o arquivo foi restaurado em seguida.

| Execução | Resultado |
|---|---|
| Código atual, com `@Version` | passou |
| Sem `@Version`, rodada 1 | falhou: esperado 2, registrado 1 |
| Sem `@Version`, rodada 2 | falhou: esperado 2, registrado 1 |
| Sem `@Version`, rodada 3 | falhou: esperado 2, registrado 1 |

A falha foi a mesma nas três rodadas:

```
ContagemItemConcorrenteTest.contagemNaoSePerde:125
a quantidade registrada deveria contar todas as alocacoes aceitas;
diferenca indica escrita perdida entre threads ==> expected: <2> but was: <1>
```

O número coincide com o "duas aceitas, uma registrada" do commit original. Com `@Version` e sem a repetição, a contagem fica certa, mas só uma alocação passa: é a etapa intermediária da tabela.

Para repetir em qualquer máquina:

```bash
# 1. comentar a linha @Version em src/main/java/com/rotavital/dominio/ItemRequisicao.java
./mvnw test -Dtest=ContagemItemConcorrenteTest      # vermelho: expected <2> but was <1>
# 2. restaurar o arquivo
git checkout -- src/main/java/com/rotavital/dominio/ItemRequisicao.java
./mvnw test -Dtest=ContagemItemConcorrenteTest      # verde
```

### 2.3 Por que este é o exemplo mais forte

- **O defeito era real e silencioso.** Nenhuma exceção, nenhum log: só um número errado no banco, que levaria a excesso de alocação.
- **O teste veio antes do mecanismo.** `@Version` e `AlocacaoComRepeticao` não existiam quando a corrida foi medida, e as duas decisões foram tomadas para fazer este teste passar.
- **O teste também corrigiu a si mesmo.** O piso `alocadas > 0` nasceu de um verde falso, que é exatamente o tipo de erro que o ciclo vermelho, verde e refatoração existe para pegar.
- **O resultado é reproduzível.** A seção 2.2 mostra o vermelho voltando sempre que a proteção sai.

### 2.4 Divergência registrada no teste da mesma bolsa

O javadoc de `CondicaoCorridaAlocacaoTest` diz que o teste "falha de propósito antes do mecanismo de sincronização da PI3-108 existir". A mensagem do commit diz o contrário: ele passou sem proteção, por causa do UNIQUE. A reprodução confirma o commit:

| Execução | Resultado |
|---|---|
| `CondicaoCorridaAlocacaoTest` sem `@Version` | passou |

O teste da mesma bolsa não depende do mecanismo da PI3-108, e isso confirma o commit: quem segura essa disputa é a restrição UNIQUE.

O teste continua útil. Ele trava a garantia de que uma bolsa nunca é reservada duas vezes, e falharia se alguém trocasse o `@OneToOne` por `@ManyToOne`. O que está errado é só a frase do javadoc sobre a ordem.

---

## 3. Outros casos em que o teste mudou a implementação

Os três casos abaixo já estão citados no story map. Aqui estão os testes que os sustentam.

| Caso | O que o teste revelou | Teste |
|---|---|---|
| **Cancelamento de requisição** | A regra escrita proibia cancelar requisição ATENDIDA. O teste mostrou que a trava certa é o status das bolsas: RESERVADA se desfaz, despachada não. | `FluxoRequisicaoTest.cancelamentoLiberaBolsa` e `naoCancelaComBolsaDespachada` |
| **Remessa vazia** | O teste de temperatura sobre rota sem bolsas falhou e revelou que `RotaServico` recusa EM_TRANSITO sem carga. A regra ficou, e o teste foi reescrito para documentá-la. O comentário "Descoberto ao escrever este teste" está no corpo do método. | `TemperaturaRemessaTest.rotaVaziaNaoEntraEmTransito`; `RotaControllerTest.rotaVaziaNaoSai` |
| **Colisão no índice de estoque** | A documentação dizia que a colisão era "rara por construção". A medição mostrou que `Objects.hash` sobre enum usa o hash de identidade, que muda entre execuções. A chave passou a usar `ordinal()` no commit `07f7e5f` (PI3-132). | `IndiceEstoqueTest.hashPerfeitoSobreAsTrintaEDuasCombinacoes`, `hashEhEstavelEDeterministico`, `contratoEqualsHashCode` |

Um quarto caso aparece no próprio código. O javadoc de [CompatibilidadeAlocacaoTest](../src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java) (HU-08) registra que a alocação comparava grupos com `!=`, o que recusava doação válida: um paciente A+ só recebia A+. O teste fixa a regra de compatibilidade no lugar da igualdade.

---

## 4. Os testes do repositório e o que cada um protege

A suíte tem 39 classes de teste. Na execução completa de 10/11/2026 sobre a `main`, foram 451 testes, sem falha e sem teste pulado. O README ainda cita 421, número de antes das entregas mais recentes. A diferença entre os 350 métodos anotados e os 451 executados vem dos testes parametrizados, que rodam um caso por conjunto de dados.

### 4.1 Regras de domínio que afetam o paciente

| Teste | O que protege |
|---|---|
| [MatrizCompatibilidadeTest](../src/test/java/com/rotavital/compatibilidade/MatrizCompatibilidadeTest.java) | a tabela ABO/Rh por hemocomponente, a assimetria doador e receptor, e a tabela invertida do plasma |
| [CasosBordaCompatibilidadeTest](../src/test/java/com/rotavital/alocacao/CasosBordaCompatibilidadeTest.java) (PI3-130) | que a resposta certa da tabela chega inteira até a bolsa escolhida, inclusive com argumento nulo e estoque todo incompatível |
| [CompatibilidadeAlocacaoTest](../src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java) (HU-08) | que a API aceita bolsa compatível, não só idêntica, e recusa a incompatível com 409 |
| [SelecaoFefoTest](../src/test/java/com/rotavital/alocacao/SelecaoFefoTest.java) | FEFO: filtro antes da ordenação, bolsa vencida ou indisponível nunca escolhida, borda da validade no próprio dia, desempate por código |
| [FilaPrioridadeFefoTest](../src/test/java/com/rotavital/estruturas/FilaPrioridadeFefoTest.java) | a ordem da fila por inserção ordenada, que a seleção FEFO e a alocação com rota reusam |
| [TemperaturaRemessaTest](../src/test/java/com/rotavital/api/TemperaturaRemessaTest.java) (HU-07) | a faixa de cadeia fria por componente embarcado: a mesma leitura pode estar certa para hemácias e ser excursão para plaquetas |

### 4.2 Fluxo de negócio e estado

| Teste | O que protege |
|---|---|
| [FluxoRequisicaoTest](../src/test/java/com/rotavital/api/FluxoRequisicaoTest.java) | o caminho completo: alocar reserva a bolsa e muda o status da requisição; mesma bolsa não é alocada duas vezes; cancelamento libera a bolsa e é barrado depois do despacho |
| [AlocacaoAutomaticaTest](../src/test/java/com/rotavital/api/AlocacaoAutomaticaTest.java) (PI3-131) | que o índice por hash é consultado no caminho real de alocação e é atualizado só depois do commit |
| [RotaControllerTest](../src/test/java/com/rotavital/api/RotaControllerTest.java) | o ciclo de vida da rota e o efeito dele no status das bolsas embarcadas |
| [HemocentroControllerTest](../src/test/java/com/rotavital/api/HemocentroControllerTest.java) | o cadastro de ponta a ponta, a validação de entrada e a unicidade do CNPJ |
| [CenariosAceitacaoTest](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java) | um cenário Gherkin por história de [historias-de-usuario.md](historias-de-usuario.md), com Dado, Quando e Então no nome e no corpo |

### 4.3 Concorrência

| Teste | O que protege |
|---|---|
| [ContagemItemConcorrenteTest](../src/test/java/com/rotavital/concorrencia/ContagemItemConcorrenteTest.java) (PI3-109) | que alocações simultâneas ao mesmo item não perdem a contagem (seção 2) |
| [CondicaoCorridaAlocacaoTest](../src/test/java/com/rotavital/concorrencia/CondicaoCorridaAlocacaoTest.java) (PI3-109) | que oito threads disputando uma bolsa resultam em exatamente uma reserva |
| [IngestaoConcorrenteTest](../src/test/java/com/rotavital/concorrencia/IngestaoConcorrenteTest.java) (PI3-30) | que o lote paralelo não duplica código, e que uma bolsa inválida não derruba as outras |

### 4.4 Malha, caminho mínimo e alocação com rota

| Teste | O que protege |
|---|---|
| [GrafoTest](../src/test/java/com/rotavital/estruturas/GrafoTest.java), [DijkstraTest](../src/test/java/com/rotavital/estruturas/DijkstraTest.java) | a lista de adjacência, a recusa de peso negativo e o caminho mínimo em minutos |
| [DijkstraCasosSemSolucaoTest](../src/test/java/com/rotavital/estruturas/DijkstraCasosSemSolucaoTest.java) (PI3-19) | que rota inexistente é resposta tratada, não exceção |
| [ServicoAlocacaoRotaTest](../src/test/java/com/rotavital/alocacao/ServicoAlocacaoRotaTest.java) (PI3-58) | que bolsa em unidade inalcançável nunca é escolhida |
| [MotivoFalhaAlocacaoTest](../src/test/java/com/rotavital/alocacao/MotivoFalhaAlocacaoTest.java) (PI3-57) | os três motivos de falha, distinguíveis e sem exceção |
| [CriteriosAceiteAlocacaoTest](../src/test/java/com/rotavital/alocacao/CriteriosAceiteAlocacaoTest.java) (PI3-59) | os seis critérios de aceite do card, um teste por critério |
| [PropriedadesAlocacaoTest](../src/test/java/com/rotavital/alocacao/PropriedadesAlocacaoTest.java) | centenas de cenários gerados, conferidos contra um oráculo de força bruta (seção 4.6) |
| [VizinhoMaisProximoTest](../src/test/java/com/rotavital/estruturas/VizinhoMaisProximoTest.java) (PI3-150) | a roteirização gulosa, o desempate determinístico e os pontos inalcançáveis |
| [CarregadorMalhaTest](../src/test/java/com/rotavital/rede/CarregadorMalhaTest.java) | a malha das 12 unidades reais gerada a partir das premissas |
| [DemoPontaAPontaTest](../src/test/java/com/rotavital/alocacao/DemoPontaAPontaTest.java) (PI3-61) | a demonstração com sucesso e os três motivos de falha, cada cena com asserção |

### 4.5 Indicadores, estruturas, configuração e desempenho

| Teste | O que protege |
|---|---|
| [IndicadorServicoTest](../src/test/java/com/rotavital/servico/IndicadorServicoTest.java), [IndicadorControllerTest](../src/test/java/com/rotavital/api/IndicadorControllerTest.java) | os cálculos de estoque, cobertura, descarte e validade, e os endpoints que os expõem |
| [IndicadorServicoDadosSinteticosTest](../src/test/java/com/rotavital/servico/IndicadorServicoDadosSinteticosTest.java) | que o Java reproduz, campo a campo, a análise em Python sobre as 2.000 bolsas oficiais (seção 4.6) |
| [ModeloDemandaTest](../src/test/java/com/rotavital/estatistica/ModeloDemandaTest.java) | as distribuições Binomial Negativa e Poisson do risco de desabastecimento |
| [IndiceEstoqueTest](../src/test/java/com/rotavital/estruturas/IndiceEstoqueTest.java) | busca por chave composta, contrato de `equals` e `hashCode`, e hash sem colisão e estável entre execuções |
| `ListaEstoqueTest`, `FilaRequisicoesTest`, `PilhaHistoricoTest` | as estruturas feitas à mão da AED, espelhadas da versão em C |
| [PerfilProducaoTest](../src/test/java/com/rotavital/config/PerfilProducaoTest.java) | as decisões de segurança de produção: console do H2 desligado, sem stack trace, actuator só com health, sem senha no arquivo, porta e timeout alinhados ao Nginx |
| `CalculadoraDispersaoValidadeTest`, `BenchmarkParaleloServicoTest`, `BenchmarkControllerTest` | o cálculo paralelo de dispersão e o endpoint de benchmark |
| `MedicaoDesempenhoTest`, `MedicaoParalelismoTest` | medições de tempo; servem de evidência, não de regra de negócio |
| `RotaVitalApplicationTests` | que o contexto do Spring sobe |

### 4.6 Duas técnicas que vão além do exemplo escolhido à mão

- **Oráculo de força bruta.** `PropriedadesAlocacaoTest` gera cenários com semente fixa e recalcula cada um do jeito mais simples possível: Floyd-Warshall para o caminho e varredura completa para a bolsa. O oráculo não compartilha código com a implementação. Para um erro passar, ele teria de existir idêntico nas duas.
- **Oráculo externo.** `IndicadorServicoDadosSinteticosTest` compara o serviço Java com o esperado gravado por `dados/validar_indicadores_u2.py`, sobre o mesmo dataset. O Python é a especificação, e o Java tem de chegar ao mesmo número.

---

## 5. O que o histórico do git prova e o que não prova

O story map diz que "o histórico do Git mostra o padrão `test:` antes de `feat:` nas histórias mais recentes". A verificação commit a commit não sustenta essa frase:

| História | O que o histórico mostra |
|---|---|
| PI3-131 | `refactor`, `feat` e `test` foram criados no mesmo segundo (19/10, 21:17:56). Na cadeia de commits, o `test` (`d4162b2`) é filho do `feat` (`e1100cc`), que cria `alocarAutomaticamente`. |
| PI3-150 | o teste (16/10, 11:51) completa a cobertura de `VizinhoMaisProximo`, criado às 11:23 pela PI3-149. |
| PI3-130 | os testes de borda (15/10, 17:57) cobrem a integração da compatibilidade feita às 11:59 do mesmo dia (PI3-129). |
| PI3-109 | teste e correção entraram juntos no commit `79fa503`. |

O que isso significa:

- **A ordem dos commits não é a ordem do trabalho.** Commits criados no mesmo segundo foram separados na hora de commitar. O histórico mostra como o trabalho foi empacotado, não em que ordem o teste e o código foram escritos.
- **A evidência de teste antes do código está em outro lugar.** Ela está na mensagem do commit `79fa503`, nos comentários dentro dos testes, como em `rotaVaziaNaoEntraEmTransito`, e na reprodução da seção 2.2, que qualquer pessoa pode refazer.
- **A frase do story map deve ser corrigida.** A forma que o histórico sustenta é "os testes de cada história entram no mesmo PR que a funcionalidade, e os casos da seção 3 registram o teste mudando a implementação".
- **A Unidade 1 continua como o story map já registra.** Ali o código veio primeiro e os testes depois.

---

## 6. O teste como portão da entrega

A esteira encadeia os jobs com `needs`: build, teste, empacotamento e deploy. No [PR #72](https://github.com/rsc3-pixel/Projeto3-2026.2/pull/72), uma asserção de `MatrizCompatibilidadeTest` foi invertida de propósito. O job de teste falhou, e empacotamento e deploy nem chegaram a iniciar. A evidência está em [evidencias/e3-pipeline.md](evidencias/e3-pipeline.md).

Isso não é TDD em si, mas é o que dá consequência a ele: um teste vermelho impede que a regra quebrada chegue à aplicação no ar.

---

## 7. Como verificar

```bash
./mvnw test                                        # suíte completa
./mvnw test -Dtest=ContagemItemConcorrenteTest     # o caso da seção 2
./mvnw test -Dtest=CondicaoCorridaAlocacaoTest     # a disputa pela mesma bolsa
./mvnw test -Dtest=CenariosAceitacaoTest           # os cenários das histórias
./mvnw test -Dtest=PropriedadesAlocacaoTest        # o oráculo de força bruta
```

Para ver a ordem real dos commits de uma história:

```bash
git log --reverse --format='%h %ad %s' --date=format:'%m-%d %H:%M:%S' --grep='PI3-131'
```
