# Rota Vital — Cenários de Aceitação (Dado / Quando / Então)

Entrega 01 · Projeto Integrador 3P · Equipe E2

Este documento descreve os cenários de aceitação no formato BDD, com
rastreabilidade direta para os testes automatizados que os implementam.

> **Como ler a rastreabilidade**
> Cada cenário aponta para o `@DisplayName` exato do teste Java correspondente.
> Os `@DisplayName` dos testes seguem o mesmo formato _Dado / Quando / Então_,
> de modo que o nome do teste **é** o cenário. Para localizar o teste, basta
> procurar o texto do DisplayName na classe indicada.

---

## HU-01 — Cadastrar bolsa coletada

> **Como** técnico do hemocentro,  
> **quero** registrar uma bolsa recém-coletada informando componente, tipo
> sanguíneo e data de coleta,  
> **para que** ela entre no estoque disponível com a validade correta, sem que eu
> precise calcular prazo na mão.

### Cenário 1 — Validade calculada automaticamente

```gherkin
Dado  uma coleta de plaquetas feita hoje
Quando eu cadastrar a bolsa informando apenas o componente
Então  a validade é calculada em 5 dias e o status nasce DISPONIVEL
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-01 — Cadastrar bolsa coletada`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado uma coleta de plaquetas, Quando eu cadastrar a bolsa, Entao a validade e calculada em 5 dias sem eu informar"`
- Método: `validadeCalculadaPeloComponente()`

---

## HU-02 — Solicitar hemocomponentes

> **Como** responsável de agência transfusional,  
> **quero** abrir uma requisição informando o que preciso e com que urgência,  
> **para que** o hemocentro saiba o que separar e em quanto tempo.

### Cenário 1 — Status acompanha o atendimento

```gherkin
Dado  uma requisição pedindo 2 bolsas A+
  E  duas bolsas compatíveis em estoque
Quando eu alocar as duas bolsas uma a uma
Então  após a primeira alocação a requisição fica PARCIALMENTE_ATENDIDA
  E   após a segunda alocação a requisição passa a ATENDIDA
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-02 — Solicitar hemocomponentes`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado uma requisicao de 2 bolsas, Quando as duas forem alocadas, Entao a requisicao passa a ATENDIDA"`
- Método: `statusAcompanhaOAtendimento()`

### Cenário 2 — Alocação manual pelo endpoint de fluxo

```gherkin
Dado  uma bolsa compatível em estoque
Quando eu alocar essa bolsa para o item da requisição
Então  a bolsa fica RESERVADA e a requisição vai a PARCIALMENTE_ATENDIDA
```

**Teste que implementa:**
- Classe: [`FluxoRequisicaoTest`](../src/test/java/com/rotavital/api/FluxoRequisicaoTest.java)
- DisplayName: `"Alocar bolsa compativel reserva a bolsa e move a requisicao para PARCIALMENTE_ATENDIDA"`

### Cenário 3 — Bolsa incompatível é recusada com 409

```gherkin
Dado  uma bolsa de grupo sanguíneo diferente do pedido
Quando eu tentar alocá-la
Então  o sistema responde 409 e a requisição permanece no estado atual
```

**Testes que implementam:**
- Classe: [`FluxoRequisicaoTest`](../src/test/java/com/rotavital/api/FluxoRequisicaoTest.java)
- DisplayName: `"Bolsa de grupo sanguineo diferente e recusada com 409"`
- DisplayName: `"Hemocomponente diferente do pedido e recusado com 409"`

### Cenário 4 — Cancelar requisição devolve bolsa ao estoque

```gherkin
Dado  uma requisição com uma bolsa alocada
Quando eu cancelar a requisição
Então  a bolsa volta ao status DISPONIVEL
```

**Teste que implementa:**
- Classe: [`FluxoRequisicaoTest`](../src/test/java/com/rotavital/api/FluxoRequisicaoTest.java)
- DisplayName: `"Cancelar a requisicao devolve a bolsa ao estoque"`

---

## HU-03 — Alocar bolsa priorizando validade (FEFO)

> **Como** técnico do hemocentro,  
> **quero** que o sistema me indique qual bolsa separar para cada item,  
> **para que** eu use primeiro a que vence antes e reduza o descarte.

### Cenário 1 — A bolsa que vence primeiro aparece no topo

```gherkin
Dado  três bolsas O- de hemácias cadastradas fora de ordem de validade
Quando eu consultar o estoque ordenado por dataValidade asc
Então  a primeira da lista é a que vence antes (FEFO)
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-03 — Alocar bolsa priorizando validade (FEFO)`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado bolsas de validades diferentes, Quando eu consultar o estoque ordenado, Entao a que vence antes aparece primeiro"`
- Método: `bolsaQueVenceAntesSaiPrimeiro()`

### Cenário 2 — Bolsa já vencida não entra no estoque

```gherkin
Dado  uma coleta de plaquetas feita há 10 dias (validade 5 dias)
Quando eu tentar cadastrar essa bolsa
Então  o sistema recusa com 409 antes de ela entrar no estoque
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-03 — Alocar bolsa priorizando validade (FEFO)`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado uma coleta antiga demais, Quando eu tentar cadastrar a bolsa, Entao o sistema recusa antes de ela entrar no estoque"`
- Método: `bolsaVencidaNemChegaAoEstoque()`

### Cenário 3 — Bolsa no último dia de validade ainda pode ser alocada

```gherkin
Dado  uma bolsa de plaquetas coletada exatamente 5 dias atrás
Quando eu tentar alocá-la hoje
Então  a alocação é aceita (vencer = passar da data, não chegar nela)
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-03 — Alocar bolsa priorizando validade (FEFO)`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado uma bolsa que vence hoje, Quando eu aloca-la, Entao a alocacao e aceita: ela so vence amanha"`
- Método: `bolsaNoUltimoDiaAindaVale()`

### Cenário 4 — FEFO e compatibilidade na alocação automática

```gherkin
Dado  dois grupos compatíveis em estoque, A+ vencendo antes e B+ depois
Quando o sistema fizer a alocação automática para um receptor AB+
Então  escolhe a bolsa A+ por FEFO, pois ambas são compatíveis
```

**Teste que implementa:**
- Classe: [`AlocacaoAutomaticaTest` › `FEFO e compatibilidade na escolha automatica`](../src/test/java/com/rotavital/api/AlocacaoAutomaticaTest.java)
- DisplayName: `"Entre compativeis, sai a de validade mais proxima, ainda que de outro grupo"`

### Cenário 5 — Sem estoque compatível: 409 e requisição permanece aberta

```gherkin
Dado  estoque vazio para o componente e grupo pedidos
Quando o sistema tentar fazer a alocação automática
Então  responde 409 e a requisição continua PENDENTE
```

**Teste que implementa:**
- Classe: [`AlocacaoAutomaticaTest` › `FEFO e compatibilidade na escolha automatica`](../src/test/java/com/rotavital/api/AlocacaoAutomaticaTest.java)
- DisplayName: `"Sem estoque devolve 409 e a requisicao continua aberta"`

### Cenário 6 — Atendimento parcial: dois itens escolhem bolsas diferentes

```gherkin
Dado  um item que ainda não foi alocado
Quando o sistema fizer duas chamadas de alocação automática consecutivas
Então  cada chamada escolhe uma bolsa diferente
```

**Teste que implementa:**
- Classe: [`AlocacaoAutomaticaTest` › `FEFO e compatibilidade na escolha automatica`](../src/test/java/com/rotavital/api/AlocacaoAutomaticaTest.java)
- DisplayName: `"Duas chamadas no mesmo item escolhem bolsas diferentes"`

---

## HU-05 — Calcular rota de entrega

> **Como** técnico do hemocentro,  
> **quero** que o sistema calcule o caminho mais rápido até o hospital de destino,  
> **para que** a remessa chegue dentro da janela de tempo e da cadeia fria.

### Cenário 1 — Rota segue o ciclo de vida planejada → em trânsito

```gherkin
Dado  uma rota planejada entre hemocentro e hospital
  E  uma bolsa reservada embarcada nela
Quando a rota sair para entrega
Então  o status da rota passa a EM_TRANSITO
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-05 — Calcular rota de entrega`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado uma rota planejada com bolsa embarcada, Quando ela sair, Entao o status passa a EM_TRANSITO"`
- Método: `rotaSegueOCicloDeVida()`

---

## HU-06 — Acompanhar indicadores da rede

> **Como** coordenador da rede,  
> **quero** ver estoque, descarte e cobertura de demanda por tipo sanguíneo,  
> **para que** eu identifique onde falta sangue antes que vire desabastecimento.

### Cenário 1 — Indicadores refletem o estoque real

```gherkin
Dado  duas bolsas O- cadastradas em estoque
Quando eu consultar os indicadores de estoque por tipo
Então  o endpoint responde 200 com array contendo o campo grupoSanguineo
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-06 — Acompanhar indicadores da rede`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado bolsas em estoque, Quando eu consultar os indicadores, Entao vejo o estoque por tipo sanguineo"`
- Método: `indicadoresRefletemOEstoqueReal()`

### Cenário 2 — Indicadores calculados com dados sintéticos

```gherkin
Dado  a base de dados sintéticos carregada
Quando eu consultar o endpoint de estoque por tipo
Então  o resultado traz ao menos um grupo sanguíneo com contagem e média calculadas
```

**Teste que implementa:**
- Classe: [`IndicadorServicoDadosSinteticosTest`](../src/test/java/com/rotavital/servico/IndicadorServicoDadosSinteticosTest.java)

---

## HU-07 — Monitorar temperatura durante o transporte

> **Como** técnico do hemocentro,  
> **quero** ser avisado quando a temperatura de uma remessa sair da faixa exigida,  
> **para que** eu possa agir antes que as bolsas se tornem inviáveis.

### Cenário 1 — Faixa depende do componente, não da remessa

```gherkin
Dado  uma rota transportando hemácias e plaquetas no mesmo compartimento
Quando o sensor registrar 5 °C
Então  o alerta é gerado apenas para as plaquetas
  E   nenhum alerta é gerado para as hemácias
  (5 °C está dentro de 1–6 °C para hemácias e fora de 20–24 °C para plaquetas)
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-07 — Monitorar temperatura durante o transporte`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado uma remessa com hemacias e plaquetas, Quando o sensor marcar 5C, Entao o alerta sai apenas para as plaquetas"`
- Método: `faixaDependeDoComponenteEmbarcado()`

### Cenário 2 — Faixa aceita por componente

| Componente              | Faixa aceita   |
|-------------------------|----------------|
| Hemácias                | 1 a 6 °C       |
| Plaquetas               | 20 a 24 °C     |
| Plasma Fresco Congelado | −25 a −18 °C   |

**Testes que implementam:**
- Classe: [`TemperaturaRemessaTest`](../src/test/java/com/rotavital/api/TemperaturaRemessaTest.java)

---

## HU-08 — Alocar respeitando compatibilidade ABO/Rh

> **Como** técnico do hemocentro,  
> **quero** que o sistema só me ofereça bolsas compatíveis com o grupo do paciente,  
> **para que** eu não dependa de consultar a tabela de compatibilidade de cabeça.

### Cenário 1 — Doador universal é aceito para qualquer receptor

```gherkin
Dado  uma bolsa O- em estoque
  E  uma requisição para um paciente A+
Quando eu alocar a bolsa O- para o item A+
Então  a alocação é aceita (O- doa para todos os grupos)
  E   a bolsa fica RESERVADA
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-08 — Alocar respeitando compatibilidade ABO/Rh`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado um paciente A+ e uma bolsa O-, Quando eu alocar, Entao a doacao do doador universal e aceita"`
- Método: `doadorUniversalEhAceito()`

### Cenário 2 — Bolsa incompatível é recusada mesmo sendo a única

```gherkin
Dado  uma bolsa A+ como única em estoque
  E  uma requisição para um paciente O-
Quando eu tentar alocar a bolsa A+ para o item O-
Então  a alocação é recusada com 409
  (falta de estoque não autoriza transfusão incompatível)
```

**Teste que implementa:**
- Classe: [`CenariosAceitacaoTest` › `HU-08 — Alocar respeitando compatibilidade ABO/Rh`](../src/test/java/com/rotavital/aceitacao/CenariosAceitacaoTest.java)
- DisplayName: `"Dado um paciente O- e uma bolsa A+, Quando eu alocar, Entao a alocacao e recusada mesmo sendo a unica bolsa"`
- Método: `bolsaIncompativelEhRecusada()`

### Cenário 3 — Receptor AB+ aceita bolsa de qualquer grupo

```gherkin
Dado  uma bolsa de qualquer grupo em estoque
  E  uma requisição para um paciente AB+
Quando eu alocar
Então  a alocação é aceita (AB+ é o receptor universal)
```

**Teste que implementa:**
- Classe: [`CompatibilidadeAlocacaoTest`](../src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java)
- DisplayName: `"Paciente AB+ aceita bolsa de qualquer grupo, por ser receptor universal"`

### Cenário 4 — Rh positivo não doa para Rh negativo do mesmo grupo ABO

```gherkin
Dado  uma bolsa Rh+ e uma requisição para Rh- do mesmo grupo ABO
Quando eu tentar alocar
Então  a alocação é recusada (Rh+ não doa para Rh-)
```

**Teste que implementa:**
- Classe: [`CompatibilidadeAlocacaoTest`](../src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java)
- DisplayName: `"Rh positivo nao doa para Rh negativo do mesmo grupo ABO"`

### Cenário 5 — A relação de compatibilidade não é simétrica

```gherkin
Dado  uma bolsa O- e uma requisição A+ → aceita
  E  uma bolsa A+ e uma requisição O- → recusada
Então  trocar os lados muda a resposta (a relação não é simétrica)
```

**Testes que implementam:**
- Classe: [`CompatibilidadeAlocacaoTest`](../src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java)
- DisplayName: `"A relacao nao e simetrica: O- doa para A+, mas A+ nao doa para O-"`
- Classe: [`CasosBordaCompatibilidadeTest` › `Caso 3: a relacao nao e simetrica`](../src/test/java/com/rotavital/alocacao/CasosBordaCompatibilidadeTest.java)
- DisplayName: `"O- doa para A+, mas A+ nao doa para O-: trocar os lados muda a resposta"`

### Cenário 6 — Plasma segue regra invertida

```gherkin
Dado  uma bolsa de plasma O- e uma requisição de plasma para paciente A+
Quando eu tentar alocar
Então  a alocação é recusada (plasma: grupo doador ≥ receptor, regra inversa)
```

**Teste que implementa:**
- Classe: [`CompatibilidadeAlocacaoTest`](../src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java)
- DisplayName: `"Plasma segue regra invertida: O- nao serve para paciente A+"`

### Cenário 7 — Plasma AB serve para qualquer receptor

```gherkin
Dado  uma bolsa de plasma AB e uma requisição de plasma para qualquer grupo
Quando eu alocar
Então  a alocação é aceita (plasma AB é o doador universal de plasma)
```

**Teste que implementa:**
- Classe: [`CompatibilidadeAlocacaoTest`](../src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java)
- DisplayName: `"Plasma AB serve para qualquer receptor, o oposto das hemacias"`

### Cenário 8 — Estoque incompatível gera atendimento parcial, não erro

```gherkin
Dado  estoque com uma bolsa compatível e uma incompatível para o mesmo item
Quando o sistema fizer a alocação automática
Então  a bolsa compatível é alocada
  E   a requisição fica PARCIALMENTE_ATENDIDA (não erro)
```

**Teste que implementa:**
- Classe: [`CompatibilidadeAlocacaoTest` › `PI3-130: estoque incompativel gera atendimento parcial, nao erro`](../src/test/java/com/rotavital/api/CompatibilidadeAlocacaoTest.java)
- DisplayName: `"Com uma compativel e uma incompativel, a requisicao fica PARCIALMENTE_ATENDIDA"`

---

## Rastreabilidade consolidada

| Cenário | Classe de teste | Método / DisplayName (trecho) |
|---|---|---|
| HU-01 C1: validade calculada | `CenariosAceitacaoTest` | `validadeCalculadaPeloComponente` |
| HU-02 C1: status acompanha atendimento | `CenariosAceitacaoTest` | `statusAcompanhaOAtendimento` |
| HU-02 C2: alocação manual | `FluxoRequisicaoTest` | _"Alocar bolsa compativel reserva..."_ |
| HU-02 C3: grupo errado → 409 | `FluxoRequisicaoTest` | _"Bolsa de grupo sanguineo diferente..."_ |
| HU-02 C4: cancelar devolve bolsa | `FluxoRequisicaoTest` | _"Cancelar a requisicao devolve..."_ |
| HU-03 C1: FEFO no estoque | `CenariosAceitacaoTest` | `bolsaQueVenceAntesSaiPrimeiro` |
| HU-03 C2: bolsa vencida rejeitada | `CenariosAceitacaoTest` | `bolsaVencidaNemChegaAoEstoque` |
| HU-03 C3: último dia ainda vale | `CenariosAceitacaoTest` | `bolsaNoUltimoDiaAindaVale` |
| HU-03 C4: FEFO + compatibilidade automática | `AlocacaoAutomaticaTest` | _"Entre compativeis, sai a de validade..."_ |
| HU-03 C5: sem estoque → 409 | `AlocacaoAutomaticaTest` | _"Sem estoque devolve 409..."_ |
| HU-03 C6: duas alocações, bolsas distintas | `AlocacaoAutomaticaTest` | _"Duas chamadas no mesmo item..."_ |
| HU-05 C1: ciclo PLANEJADA → EM_TRANSITO | `CenariosAceitacaoTest` | `rotaSegueOCicloDeVida` |
| HU-06 C1: indicadores pelo estoque real | `CenariosAceitacaoTest` | `indicadoresRefletemOEstoqueReal` |
| HU-06 C2: indicadores com dados sintéticos | `IndicadorServicoDadosSinteticosTest` | _(todos os métodos)_ |
| HU-07 C1: faixa por componente na remessa | `CenariosAceitacaoTest` | `faixaDependeDoComponenteEmbarcado` |
| HU-07 C2: faixas de temperatura | `TemperaturaRemessaTest` | _(todos os métodos)_ |
| HU-08 C1: doador universal aceito | `CenariosAceitacaoTest` | `doadorUniversalEhAceito` |
| HU-08 C2: incompatível recusado | `CenariosAceitacaoTest` | `bolsaIncompativelEhRecusada` |
| HU-08 C3: AB+ receptor universal | `CompatibilidadeAlocacaoTest` | _"Paciente AB+ aceita bolsa..."_ |
| HU-08 C4: Rh+ não doa para Rh- | `CompatibilidadeAlocacaoTest` | _"Rh positivo nao doa..."_ |
| HU-08 C5: relação não simétrica | `CompatibilidadeAlocacaoTest` + `CasosBordaCompatibilidadeTest` | _"A relacao nao e simetrica..."_ |
| HU-08 C6: plasma regra invertida | `CompatibilidadeAlocacaoTest` | _"Plasma segue regra invertida..."_ |
| HU-08 C7: plasma AB doador universal | `CompatibilidadeAlocacaoTest` | _"Plasma AB serve para qualquer..."_ |
| HU-08 C8: parcial não é erro | `CompatibilidadeAlocacaoTest` | _"Com uma compativel e uma incompativel..."_ |

---

> **Nota:** HU-04 (Ver bolsas em risco de vencimento) ainda não possui teste de
> aceitação de ponta a ponta com cenário Dado/Quando/Então registrado em
> `CenariosAceitacaoTest`. Os critérios estão cobertos parcialmente pelos testes
> de serviço e endpoint de bolsas com filtro de validade.

