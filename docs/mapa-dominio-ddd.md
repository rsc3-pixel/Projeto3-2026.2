# Mapa do domínio em linguagem de DDD — Rota Vital

**Projeto:** Rota Vital — rede de distribuição de hemocomponentes
**Fonte:** o código em [`src/main/java/com/rotavital/dominio`](../src/main/java/com/rotavital/dominio) e os serviços que operam sobre ele
**Natureza:** descritivo. Este documento nomeia, com o vocabulário do DDD (Domain-Driven Design), o que já está implementado. Não propõe modelo novo. Onde o código diverge de um livro-texto, o documento registra a divergência em vez de corrigi-la.

---

## Como ler

Cada classe foi classificada pelo que o código mostra, sem inferir intenção além disso:

| Conceito DDD | Evidência procurada no código |
|---|---|
| Entidade | tem identidade própria (`@Id`), `equals`/`hashCode` pelo id, e muda de estado ao longo do tempo |
| Objeto de valor | não tem identidade; é descrito pelos atributos; imutável (`@Embeddable`, `enum`, `record`, classe `final` sem setters) |
| Raiz de agregado | tem repositório próprio; os serviços carregam e salvam por ela; os membros só são alterados através dela (`cascade`, `orphanRemoval`, setters de pacote) |
| Serviço de domínio | operação do negócio sem estado, que não pertence a uma entidade só, e sem dependência de Spring, HTTP ou JPA |
| Serviço de aplicação | `@Service` transacional que orquestra repositórios, DTOs e eventos |
| Evento de domínio | fato ocorrido, nomeado no passado, publicado por quem muda o agregado e consumido por outro componente |

---

## 1. Contextos delimitados

No sentido estrito, o código tem **um único contexto delimitado**: um modelo, um pacote `dominio`, um banco, e as mesmas classes (`Bolsa`, os enums) compartilhadas pela aplicação inteira. Dentro dele há seis áreas com vocabulário, serviços e estruturas próprias, visíveis na organização dos pacotes e em quem chama quem. Elas são nomeadas aqui como contextos porque é assim que o time já fala delas. A tabela diz onde cada uma começa e termina.

| Contexto | Vocabulário | Classes de domínio | Serviços e estruturas | Persistência |
|---|---|---|---|---|
| **Cadastro da rede** | unidade, hemocentro, hospital, CNPJ, CNES, endereço, cidade/UF | `Local`, `Hemocentro`, `Hospital`, `Endereco` | `HemocentroServico`, `HospitalServico` | `HemocentroRepository`, `HospitalRepository` |
| **Estoque** | bolsa, código de rastreio, hemocomponente, grupo sanguíneo, coleta, validade, status, FEFO, descarte | `Bolsa`, `StatusBolsa`, `TipoHemocomponente`, `GrupoSanguineo` | `BolsaServico`, `IndiceEstoqueServico`, `IngestaoConcorrenteServico`; `IndiceEstoque`, `FilaPrioridadeFefo`; eventos `BolsaAlterada`, `BolsaRemovida` | `BolsaRepository` |
| **Requisição e alocação** | requisição, item, quantidade solicitada/alocada, prioridade, prazo, alocação, reserva, doador/receptor, compatibilidade | `Requisicao`, `ItemRequisicao`, `Alocacao`, `StatusRequisicao`, `PrioridadeRequisicao` | `RequisicaoServico`, `AlocacaoComRepeticao`; `MatrizCompatibilidade`, `SelecaoFefo` | `RequisicaoRepository`, `AlocacaoRepository` (só consulta) |
| **Distribuição (rotas e cadeia fria)** | rota, remessa, origem/destino, caminho e tempo estimado (HU-05), embarque, saída e chegada previstas e reais, leitura, excursão, alerta de cadeia fria | `Rota`, `StatusRota`, `LeituraTemperatura`, `Veiculo` | `RotaServico`, `RoteirizacaoServico` (malha das unidades do banco + `Dijkstra`), `TemperaturaServico` | `RotaRepository`, `LeituraTemperaturaRepository` |
| **Malha e roteirização** | malha, unidade da rede, vértice, aresta, tempo em minutos, caminho mínimo, roteiro, alcançável | `UnidadeRede`, `PremissasMalha`, `Grafo`, `ResultadoRota`, `ResultadoRoteiro`, `ResultadoAlocacao`, `MotivoFalhaAlocacao` | `CarregadorMalha`, `Dijkstra`, `VizinhoMaisProximo`, `ServicoAlocacaoRota` | nenhuma (só memória) |
| **Indicadores** | estoque por tipo e por componente, cobertura, taxa de descarte, descarte evitável, dispersão de validade, probabilidade de desabastecimento | nenhuma própria (lê `Bolsa` e `Requisicao`) | `IndicadorServico`, `ModeloDemanda` | leitura via `BolsaRepository` e `RequisicaoRepository` |

Fora desses contextos, como apoio: `estruturas/basicas` (`ListaEstoque`, `FilaRequisicoes`, `PilhaHistorico`, entregas de AED não consumidas pela aplicação), `paralelo` (`CalculadoraDispersaoValidade`, `GeradorMassaBolsasSintetica`, `ResumoValidade`, consumidos só pelo `BenchmarkParaleloServico`) e `config/CargaInicial` (carga sintética).

### 1.1 Mapa de contextos

Relações como estão implementadas:

| Relação | Como existe no código | Padrão de integração |
|---|---|---|
| Requisição → Estoque | `RequisicaoServico.alocar` muda `Bolsa.status` de DISPONIVEL para RESERVADA na mesma transação da requisição. `desfazerAlocacao` e o cancelamento fazem o caminho inverso. Cada mudança publica `BolsaAlterada`. | dois agregados alterados numa só transação; evento para o índice |
| Requisição → Estoque (leitura) | `alocarAutomaticamente` consulta `IndiceEstoqueServico.buscarCompativeis` | modelo de leitura em memória |
| Distribuição → Estoque | `RotaServico.embarcar` exige bolsa RESERVADA do mesmo hemocentro de origem. `atualizarStatus` arrasta as bolsas para EM_TRANSITO, ENTREGUE ou de volta a RESERVADA. | dois agregados alterados numa só transação; evento para o índice |
| Estoque, Requisição e Distribuição → Cadastro | `Bolsa.hemocentroOrigem`, `Requisicao.hospital`, `Rota.origem` e `Rota.destino` são referências JPA à unidade | referência por objeto entre agregados |
| Cadastro ← os três acima | `HemocentroServico.remover` e `HospitalServico.remover` recusam a exclusão se houver bolsas, requisições ou rotas apontando para a unidade | consistência entre agregados verificada por consulta de contagem |
| Malha ↔ Cadastro | a malha tem modelo próprio da unidade (`UnidadeRede`, record com id HC01 a HC12, tipo e coordenadas) e não usa `Local`. A tradução entre os dois é pelo id. Na `CargaInicial` os hospitais recebem ids `HO04`, `HO05`, … enquanto a malha usa `HC04`, `HC05`, … para as mesmas unidades. | dois modelos do mesmo conceito; tradução por id, hoje incompleta |
| Malha → Estoque | `ServicoAlocacaoRota` recebe `Map<String, List<Bolsa>>` chaveado pelo id da unidade e lê `Bolsa` sem JPA | compartilha a entidade `Bolsa` |
| Malha → Requisição | nenhuma. `ServicoAlocacaoRota`, `Dijkstra`, `VizinhoMaisProximo` e `CarregadorMalha` não são chamados por nenhum controlador ou serviço Spring; são exercitados por testes. | implementado, não integrado ao fluxo da API |
| Indicadores → Estoque e Requisição | `IndicadorServico` é `@Transactional(readOnly = true)` e só lê repositórios | só leitura |
| Telemetria externa → Distribuição | `telemetria/simulador.py` faz `POST /api/v1/rotas/{id}/temperaturas` | o simulador se conforma ao contrato da API |

**Núcleo compartilhado.** `GrupoSanguineo`, `TipoHemocomponente` e a entidade `Bolsa` aparecem em quatro dos seis contextos. Uma mudança em qualquer um deles atravessa a aplicação inteira.

---

## 2. Linguagem ubíqua

Os termos como o código os usa. Quando o mesmo conceito tem dois nomes, os dois estão na tabela.

| Termo | Significado no código | Onde |
|---|---|---|
| Unidade | hemocentro ou hospital da rede | `Local` (JPA); `UnidadeRede` (malha) |
| Hemocentro | unidade que coleta e estoca; identificada por CNPJ | `Hemocentro` |
| Hospital / agência transfusional | unidade que requisita; identificada por CNES. A malha a chama de `AGENCIA_TRANSFUSIONAL` | `Hospital`; `UnidadeRede.tipo` |
| Bolsa | uma unidade de hemocomponente, identificada pelo código de rastreio | `Bolsa` |
| Hemocomponente | hemácias, plasma, plaquetas ou crioprecipitado, cada um com faixa de temperatura e validade em dias | `TipoHemocomponente` |
| Grupo sanguíneo | ABO/Rh, oito valores | `GrupoSanguineo` |
| Validade | `dataColeta + validadeDias` do componente. A bolsa vence no dia seguinte à validade: `estaVencida` usa `isAfter` | `Bolsa` |
| Disponível, reservada, em trânsito, entregue, descartada | estados da bolsa | `StatusBolsa` |
| Requisição | pedido de um hospital, com prioridade, prazo e itens | `Requisicao` |
| Item | combinação de hemocomponente, grupo e quantidade dentro de uma requisição | `ItemRequisicao` |
| Alocação / reserva | vínculo de uma bolsa a um item; reserva a bolsa | `Alocacao` |
| Atendido | item com `quantidadeAlocada >= quantidadeSolicitada` | `ItemRequisicao.estaAtendido` |
| Doador / receptor | na compatibilidade, a bolsa é a doadora e o item é o receptor. A relação não é simétrica | `MatrizCompatibilidade` |
| FEFO | First Expired, First Out: a bolsa de validade mais próxima sai primeiro; desempate por código de rastreio | `SelecaoFefo`, `FilaPrioridadeFefo` |
| Rota / remessa | viagem de um hemocentro a um hospital com bolsas embarcadas | `Rota` |
| Embarque | colocar bolsa RESERVADA numa rota PLANEJADA | `RotaServico.embarcar` |
| Leitura | temperatura, e posição opcional, reportada durante rota EM_TRANSITO | `LeituraTemperatura` |
| Excursão / alerta de cadeia fria | leitura fora da faixa de algum componente embarcado; avaliada por componente, não por valor único | `TemperaturaServico.avaliar` |
| Malha | grafo das 12 unidades reais: vértice é o id da unidade, aresta é o trecho, peso é o tempo em minutos | `Grafo`, `CarregadorMalha` |
| Alcançável | existe caminho na malha a partir da unidade solicitante | `ResultadoRota.alcancavel` |
| Descarte por vencimento, descarte evitável, desabastecimento | indicadores calculados sobre o estoque e a demanda | `IndicadorServico` |

---

## 3. Entidades

| Entidade | Identidade | Igualdade | O que muda ao longo do tempo | Observações |
|---|---|---|---|---|
| `Local` (abstrata) | `id` (String) | `equals`/`hashCode` por `id`, válido para qualquer `Local` | nome, telefone, endereço | `SINGLE_TABLE`; base de `Hemocentro` e `Hospital` |
| `Hemocentro` | herdada | herdada | `cnpj` | coleção inversa `estoque` (`mappedBy`), sem cascade |
| `Hospital` | herdada | herdada | `cnes` | coleção inversa `requisicoes` (`mappedBy`), sem cascade |
| `Bolsa` | `codigoRastreio` | por `codigoRastreio` | só `status` (único setter) | `dataValidade` é derivada no construtor; nasce DISPONIVEL |
| `Requisicao` | `id` | por `id` | `status`, `prazoEntrega`, `observacao`, `itens` | nasce PENDENTE; `recalcularStatus()` deriva o status dos itens |
| `ItemRequisicao` | `id` | por `id` | `quantidadeAlocada`, `alocacoes` | `@Version` para bloqueio otimista; `setRequisicao` é de pacote |
| `Alocacao` | `id` | por `id` | nada depois de criada (sem setters) | `@OneToOne` com `Bolsa`: uma bolsa participa de no máximo uma alocação |
| `Rota` | `id` | por `id` | `status`, `saidaReal`, `chegadaReal`, `dataHoraChegadaPrevista`, `bolsas` | nasce PLANEJADA; `@ManyToMany` com `Bolsa` pela tabela `rota_bolsa` |
| `LeituraTemperatura` | `id` | por `id` | nada depois de criada | latitude e longitude opcionais (GPS simulado) |
| `Veiculo` | `id` | **não sobrescreve** `equals`/`hashCode`: igualdade por referência | placa, modelo, capacidade, disponível | sem repositório, serviço, controlador ou teste; `Rota` não o referencia |

---

## 4. Objetos de valor

### 4.1 No pacote `dominio`

**`Endereco`.** `@Embeddable`, sem identidade, sem setters, gravado nas colunas da tabela de `Local`. O javadoc da classe se declara objeto de valor. As consultas por cidade e UF passam por ele (`findByEnderecoCidadeIgnoreCase`). **Não implementa `equals`/`hashCode`**: dois endereços iguais campo a campo não são iguais para o Java. A igualdade por valor é intenção registrada, não contrato implementado.

**Enums**, todos imutáveis e com igualdade por valor:

| Enum | Valores | Conhecimento que carrega |
|---|---|---|
| `GrupoSanguineo` | A+, A−, B+, B−, AB+, AB−, O+, O− | `descricao` para exibição; `@JsonValue` devolve o nome da constante |
| `TipoHemocomponente` | hemácias, plasma fresco congelado, plaquetas, crioprecipitado | **o VO com mais regra do domínio**: `tempMinCelsius`, `tempMaxCelsius`, `validadeDias` e `temperaturaAdequada(double)`. Define a validade de cada bolsa e a faixa da cadeia fria |
| `PrioridadeRequisicao` | ROTINA, URGENTE, EMERGENCIA | só filtro de listagem (`RequisicaoServico.listar`); nenhuma regra de alocação ou de ordem consulta a prioridade |
| `StatusBolsa` | DISPONIVEL, RESERVADA, EM_TRANSITO, ENTREGUE, DESCARTADA | estados do ciclo de vida da bolsa (seção 7) |
| `StatusRequisicao` | PENDENTE, PARCIALMENTE_ATENDIDA, ATENDIDA, CANCELADA | estados da requisição, derivados das alocações |
| `StatusRota` | PLANEJADA, EM_TRANSITO, CONCLUIDA, CANCELADA | estados da rota |

### 4.2 Fora do pacote `dominio`, com forma de objeto de valor

| Classe | Pacote | Forma | O que representa |
|---|---|---|---|
| `ResultadoAlocacao` | `alocacao` | `final`, construtor privado, fábricas `alocada` e `falha`, `equals` por valor | sucesso (bolsa, unidade, rota, tempo) ou falha com motivo |
| `MotivoFalhaAlocacao` | `alocacao` | enum | SEM_ESTOQUE, TODAS_VENCIDAS, SEM_CAMINHO, avaliados nessa ordem |
| `ResultadoRota<T>` | `estruturas` | `final`, imutável, fábrica `semCaminho()` | caminho mínimo e custo em minutos; ausência de caminho é estado, não erro |
| `ResultadoRoteiro<T>` | `estruturas` | `final`, imutável | ordem de visita, caminho completo e pontos inalcançáveis |
| `UnidadeRede` | `rede` | record | unidade real da malha: id HC01 a HC12, tipo, cidade, bairro, coordenadas |
| `PremissasMalha` | `rede` | constantes | premissas de tempo e de vizinhança usadas para gerar as arestas |
| `ResumoValidade` | `paralelo` | record | estatísticas de validade de uma massa de bolsas |
| `IngestaoConcorrenteServico.ResultadoIngestao` | `servico` | record | aceitas, recusadas e duração de um lote |
| chave de `IndiceEstoque` | `estruturas` | par (`GrupoSanguineo`, `TipoHemocomponente`) com `equals` e `hashCode` | combinação de busca no índice |
| `LeituraTemperaturaResponse.AlertaCadeiaFria` | `api.dto` | record | componente fora da faixa e quantas bolsas afetadas. **Calculado, não persistido**; vive no DTO |

---

## 5. Agregados e raízes

Resumo. A raiz está em negrito.

| Agregado | Membros | Repositório | Quem altera |
|---|---|---|---|
| **`Requisicao`** | `ItemRequisicao`, `Alocacao` | `RequisicaoRepository` | `RequisicaoServico`; `AlocacaoComRepeticao` por cima dele |
| **`Bolsa`** | nenhum | `BolsaRepository` | `BolsaServico`; o status também por `RequisicaoServico` e `RotaServico` |
| **`Rota`** | nenhum; referencia `Bolsa`, `Hemocentro`, `Hospital` | `RotaRepository` | `RotaServico` |
| **`LeituraTemperatura`** | nenhum; referencia `Rota` | `LeituraTemperaturaRepository` | `TemperaturaServico` |
| **`Hemocentro`** | `Endereco` (VO) | `HemocentroRepository` | `HemocentroServico` |
| **`Hospital`** | `Endereco` (VO) | `HospitalRepository` | `HospitalServico` |
| `Veiculo` | nenhum | nenhum | ninguém |

### 5.1 `Requisicao` ⊃ `ItemRequisicao` ⊃ `Alocacao`

É o único agregado com mais de uma entidade, e a fronteira é a mais nítida do código.

Evidência da fronteira:

- `Requisicao.itens` tem `cascade = ALL` e `orphanRemoval = true`; `ItemRequisicao.alocacoes` também.
- `ItemRequisicao.setRequisicao` é de pacote: só `Requisicao.adicionarItem` amarra o lado dono.
- Não existe `ItemRequisicaoRepository`. `AlocacaoRepository` existe, mas tem um único método de consulta (`existsByBolsaCodigoRastreio`), usado por `BolsaServico.remover`. Ninguém salva `Alocacao` diretamente.
- `RequisicaoServico` segue sempre o mesmo caminho: `buscar(requisicaoId)`, `itemDe(requisicao, itemId)`, altera, `requisicoes.save(requisicao)`.

Invariantes e onde cada um mora:

| Invariante | Onde |
|---|---|
| `quantidadeAlocada` é a quantidade de alocações do item | `ItemRequisicao.adicionarAlocacao` e `removerAlocacao` (entidade) |
| status da requisição deriva dos itens: ATENDIDA se todos atendidos e há itens; PARCIALMENTE_ATENDIDA se algum item tem alocação; senão PENDENTE; CANCELADA não muda | `Requisicao.recalcularStatus` (entidade) |
| duas alocações simultâneas ao mesmo item não perdem a contagem | `@Version` em `ItemRequisicao`; a segunda transação falha com `OptimisticLockingFailureException` e `AlocacaoComRepeticao` repete até três vezes |
| requisição cancelada não aceita alteração | `RequisicaoServico.exigirRequisicaoAberta` |
| só o cancelamento é manual | `RequisicaoServico.atualizarStatus` |
| cancelamento barrado se alguma bolsa alocada já saiu (status diferente de RESERVADA) | `RequisicaoServico.atualizarStatus` |
| item com bolsa alocada não é removido | `RequisicaoServico.removerItem` |
| alocação exige bolsa DISPONIVEL, na validade, do mesmo componente e de grupo compatível | `RequisicaoServico.alocar` |
| alocação só se desfaz enquanto a bolsa está RESERVADA | `RequisicaoServico.desfazerAlocacao` |

Referência a outro agregado: `Alocacao.bolsa` aponta para `Bolsa`, que é raiz de outro agregado. A alocação muda `Bolsa.status` dentro da transação da requisição e publica `BolsaAlterada`.

### 5.2 `Bolsa`

Agregado de uma entidade só. Invariantes no construtor: `dataValidade = dataColeta + validadeDias`, status inicial DISPONIVEL. Comportamento próprio: `estaVencida(data)`.

O status é mudado por três serviços. A tabela de transições em `BolsaServico.TRANSICOES` é aplicada só no caminho do PATCH (`atualizarStatus`); `RequisicaoServico` e `RotaServico` chamam `setStatus` diretamente. Outras regras: a bolsa não nasce vencida (`BolsaServico.criar`); só é removida se nenhuma `Alocacao` a referencia e se está DISPONIVEL ou DESCARTADA (`BolsaServico.remover`). Toda mudança publica `BolsaAlterada` ou `BolsaRemovida`.

`Bolsa` **não** faz parte do agregado `Hemocentro`. `Hemocentro.estoque` é o lado inverso (`mappedBy`); o lado dono é `Bolsa.hemocentroOrigem`. Nenhum serviço chama `Hemocentro.adicionarBolsa`, `removerBolsa` ou `getEstoque`; o estoque de um hemocentro é obtido por `BolsaRepository.findByHemocentroOrigemId`.

### 5.3 `Rota`

Raiz de uma entidade só. `Rota.bolsas` é `@ManyToMany` sem cascade: referência, não posse. As bolsas continuam raízes do próprio agregado.

Regras em `RotaServico`: chegada prevista depois da saída prevista (`criar`); transições pela tabela `TRANSICOES`; sair EM_TRANSITO exige ao menos uma bolsa; embarque e desembarque só em rota PLANEJADA; embarque exige bolsa RESERVADA, ainda não embarcada, e do mesmo hemocentro de origem da rota; a mudança de status da rota propaga para as bolsas (EM_TRANSITO, ENTREGUE, ou de volta a RESERVADA no cancelamento). `Veiculo` não é referenciado.

### 5.4 `LeituraTemperatura`

Conceitualmente pertence à rota, como o javadoc diz: o sensor mede o compartimento, não cada bolsa. Na persistência, porém, é independente: `Rota` não tem coleção de leituras, e `TemperaturaServico` grava pelo `LeituraTemperaturaRepository`. É um agregado de uma entidade, só de inserção, que referencia `Rota`.

Regra: leitura só é aceita em rota EM_TRANSITO (`TemperaturaServico.registrar`). O alerta não é gravado: `avaliar` conta as bolsas por componente embarcado e compara a temperatura com `TipoHemocomponente.temperaturaAdequada` a cada consulta.

### 5.5 `Hemocentro` e `Hospital`

Cada um é raiz do próprio agregado, com `Endereco` como objeto de valor embutido. Unicidade de CNPJ e CNES verificada por `existsByCnpj` e `existsByCnes` nos serviços. A exclusão é guardada por consultas de contagem sobre os outros agregados: hemocentro com bolsas ou rotas de origem não sai; hospital com requisições ou rotas de destino não sai. As coleções inversas (`estoque`, `requisicoes`) e seus métodos `adicionar...` não são usados fora do pacote `dominio`.

### 5.6 `Veiculo`

Entidade declarada (o `ddl-auto` cria a tabela), sem repositório, serviço, controlador, teste ou referência de outra entidade. Está modelado, não integrado.

---

## 6. Serviços

### 6.1 Serviços de domínio (sem Spring, sem JPA)

| Serviço | Operação | Regra que encapsula | Contexto |
|---|---|---|---|
| `MatrizCompatibilidade` | `compativel(doador, receptor, tipo)`, `doadoresPara(receptor, tipo)` | ABO/Rh por componente. Hemácias e plaquetas usam a tabela de hemácias; plasma e crioprecipitado usam a tabela invertida. Argumento nulo devolve falso ou conjunto vazio | Requisição e alocação |
| `SelecaoFefo` | `alocavel`, `ordenar`, `escolher` (sobrecargas com e sem receptor, e sobre `IndiceEstoque`) | filtro antes da ordenação: vencida, indisponível e incompatível nunca entram na fila. Ordem por validade, desempate por código de rastreio, via `FilaPrioridadeFefo` | Requisição e alocação |
| `ServicoAlocacaoRota` | `alocar(unidade, grupo, tipo, data)` → `ResultadoAlocacao` | candidatas por unidade, Dijkstra a partir da solicitante, descarte das inalcançáveis, FEFO entre as que sobram. Falha com motivo na ordem SEM_ESTOQUE, TODAS_VENCIDAS, SEM_CAMINHO. **Casa grupo idêntico**, sem `MatrizCompatibilidade` (registrado no próprio javadoc). Não muda status de bolsa | Malha e roteirização |
| `Dijkstra<T>` | `calcularDistancias`, `calcularRota` → `ResultadoRota` | caminho mínimo em minutos; pesos não negativos garantidos pelo `Grafo` na inserção | Malha e roteirização |
| `VizinhoMaisProximo<T>` | roteiro → `ResultadoRoteiro` | heurística gulosa sobre o caminho mínimo; desempate determinístico pela ordem de entrada | Malha e roteirização |
| `CarregadorMalha` | monta `Grafo<String>` e `indicePorId()` | fábrica da malha a partir de `UnidadeRede` e `PremissasMalha` | Malha e roteirização |
| `ModeloDemanda` | probabilidade excedente por binomial negativa e por Poisson | demanda como variável aleatória; usado por `IndicadorServico.probabilidadeDesabastecimento` | Indicadores |

Comportamentos que moram nas entidades e nos VOs, e por isso não são serviços: `Bolsa.estaVencida`, `TipoHemocomponente.temperaturaAdequada`, `Requisicao.recalcularStatus` e `estaCompleta`, `ItemRequisicao.estaAtendido` e `quantidadePendente`.

### 6.2 Serviços de aplicação (`@Service`, transacionais)

| Serviço | Responsabilidade |
|---|---|
| `HemocentroServico`, `HospitalServico` | CRUD das unidades, unicidade de CNPJ e CNES, bloqueio de exclusão |
| `BolsaServico` | CRUD da bolsa, tabela de transições de status, publicação de eventos |
| `IngestaoConcorrenteServico` | cadastro de lote em paralelo, cada bolsa na própria transação |
| `RequisicaoServico` | ciclo completo da requisição: itens, alocação manual e automática, desfazer, cancelar |
| `AlocacaoComRepeticao` | repete `RequisicaoServico.alocar` em conflito de bloqueio otimista |
| `RotaServico` | ciclo da rota, embarque, propagação de status para as bolsas |
| `TemperaturaServico` | registro e avaliação das leituras de cadeia fria |
| `IndiceEstoqueServico` | mantém o modelo de leitura do estoque (seção 6.4) |
| `IndicadorServico` | indicadores de estoque, cobertura, descarte, validade e desabastecimento, só leitura |
| `BenchmarkParaleloServico` | mede o cálculo paralelo de dispersão de validade; apoio, não regra de negócio |

Regras de domínio que hoje moram nesses serviços, e não nas entidades:

| Regra | Onde mora |
|---|---|
| transições válidas de `StatusBolsa` | `BolsaServico.TRANSICOES` |
| bolsa não nasce vencida | `BolsaServico.criar` |
| bolsa alocada, ou fora de DISPONIVEL e DESCARTADA, não é removida | `BolsaServico.remover` |
| transições válidas de `StatusRota` | `RotaServico.TRANSICOES` |
| chegada prevista depois da saída prevista | `RotaServico.criar` |
| rota só sai com carga; embarque só em PLANEJADA, só bolsa RESERVADA do hemocentro de origem | `RotaServico.atualizarStatus`, `RotaServico.embarcar` |
| status da bolsa acompanha o da rota | `RotaServico.atualizarStatus` |
| leitura só em rota EM_TRANSITO; alerta por componente embarcado | `TemperaturaServico.registrar`, `avaliar` |
| alocação exige bolsa DISPONIVEL, na validade, mesmo componente, grupo compatível | `RequisicaoServico.alocar` |
| só o cancelamento é manual; demais status derivam das alocações | `RequisicaoServico.atualizarStatus` com `Requisicao.recalcularStatus` |
| cancelamento barrado se alguma bolsa já saiu | `RequisicaoServico.atualizarStatus` |
| requisição cancelada não aceita alteração | `RequisicaoServico.exigirRequisicaoAberta` |
| item com bolsa alocada não é removido | `RequisicaoServico.removerItem` |
| alocação só se desfaz com a bolsa RESERVADA | `RequisicaoServico.desfazerAlocacao` |
| CNPJ e CNES únicos; unidade referenciada não é removida | `HemocentroServico`, `HospitalServico` |

### 6.3 Eventos de domínio

`BolsaAlterada(Bolsa)` e `BolsaRemovida(Bolsa)`, records no pacote `servico.evento`. Publicados por `BolsaServico`, `RequisicaoServico` e `RotaServico` pelo `ApplicationEventPublisher` sempre que o status de uma bolsa muda ou ela é excluída. Consumidos por `IndiceEstoqueServico` com `@TransactionalEventListener(phase = AFTER_COMMIT)`: se a transação reverte, o índice não muda. Carregam a entidade inteira para que o ouvinte decida pelo status atual sem voltar ao banco. Há um único consumidor.

### 6.4 Modelo de leitura

`IndiceEstoque` (hash por par grupo e componente) mantido por `IndiceEstoqueServico`: guarda só bolsas DISPONIVEL, é reconstruído no `ApplicationReadyEvent`, sincronizado pelos eventos após o commit e protegido por `ReentrantReadWriteLock`. `buscarCompativeis` consulta `MatrizCompatibilidade.doadoresPara` para saber quais grupos procurar. É o caminho da alocação automática.

### 6.5 Repositórios e fábricas

Sete repositórios Spring Data, um por raiz: `HemocentroRepository`, `HospitalRepository`, `BolsaRepository`, `RequisicaoRepository`, `RotaRepository`, `LeituraTemperaturaRepository`, e `AlocacaoRepository`, que só serve consulta. Não há repositório para `ItemRequisicao`, `Alocacao` como escrita, nem `Veiculo`.

Não há fábricas de entidade: os serviços geram o id com `UUID.randomUUID()` e chamam o construtor; a `CargaInicial` usa ids fixos. As fábricas que existem são de objetos de valor (`ResultadoAlocacao.alocada` e `falha`, `ResultadoRota.semCaminho`, `AlertaCadeiaFria.de`) e da malha (`CarregadorMalha`).

---

## 7. Ciclos de vida

### `StatusBolsa`

Tabela de `BolsaServico.TRANSICOES` e quem executa cada transição:

| De | Para | Quem |
|---|---|---|
| DISPONIVEL | RESERVADA | `RequisicaoServico.alocar`; PATCH |
| DISPONIVEL | DESCARTADA | PATCH |
| RESERVADA | DISPONIVEL | `RequisicaoServico.desfazerAlocacao`, cancelamento da requisição; PATCH |
| RESERVADA | EM_TRANSITO | `RotaServico.atualizarStatus` para EM_TRANSITO; PATCH |
| RESERVADA | DESCARTADA | PATCH |
| EM_TRANSITO | ENTREGUE | `RotaServico.atualizarStatus` para CONCLUIDA; PATCH |
| EM_TRANSITO | DESCARTADA | PATCH |
| EM_TRANSITO | RESERVADA | `RotaServico.atualizarStatus` para CANCELADA. **Não está na tabela de `BolsaServico`**; o serviço de rota a executa por `setStatus` |
| ENTREGUE, DESCARTADA | nenhum | estados finais |

### `StatusRequisicao`

PENDENTE, PARCIALMENTE_ATENDIDA e ATENDIDA são derivados por `Requisicao.recalcularStatus` a cada alocação criada ou desfeita e a cada item adicionado ou removido. CANCELADA é o único status aceito manualmente e é final.

### `StatusRota`

PLANEJADA vai para EM_TRANSITO ou CANCELADA; EM_TRANSITO vai para CONCLUIDA ou CANCELADA; CONCLUIDA e CANCELADA são finais. Tabela em `RotaServico.TRANSICOES`.

---

## 8. Fatos registrados durante o mapeamento

São observações, não propostas. Ficam aqui para que quem ler o mapa saiba onde o código e a documentação anterior não coincidem.

1. **O diagrama em `docs/diagramas/dominio.mmd` está defasado em relação ao código.** Ele mostra `StatusBolsa.VENCIDA`, `StatusRequisicao.EM_ATENDIMENTO`, `StatusRota.EM_ANDAMENTO` e `ItemRequisicao.incrementarAlocada()`, que não existem; e não mostra `LeituraTemperatura`, `Veiculo`, o `@Version` do item nem `saidaReal` e `chegadaReal` da rota. Este documento segue o código.
2. **`Veiculo` está modelado e não integrado** (seção 5.6).
3. **As coleções inversas `Hemocentro.estoque` e `Hospital.requisicoes` não são usadas** fora do pacote `dominio`; os serviços consultam os repositórios.
4. **`Endereco` não implementa `equals`/`hashCode`** apesar de se declarar objeto de valor.
5. **Os ids dos hospitais divergem entre a carga inicial (`HO04`…) e a malha (`HC04`…)** para as mesmas unidades reais. Os hemocentros coincidem (`HC01`, `HC02`, `HC03`, `HC08`).
6. **Há dois caminhos de alocação com regras diferentes.** O caminho da API (`RequisicaoServico`, com `IndiceEstoqueServico` e `SelecaoFefo`) aplica compatibilidade ABO/Rh; o caminho em memória (`ServicoAlocacaoRota`) casa grupo idêntico e considera a malha. Os dois reusam `SelecaoFefo.alocavel` e `FilaPrioridadeFefo`. O segundo não é chamado pela API.
7. **`AlertaCadeiaFria` vive em `api.dto`**, não no domínio: é calculado a cada consulta e não é persistido.
8. **A transição EM_TRANSITO → RESERVADA, feita no cancelamento da rota, não consta na tabela de `BolsaServico`** (seção 7).

---

## 9. Onde cada afirmação é verificada

| Afirmação | Testes |
|---|---|
| FEFO, filtro antes da ordenação, desempate, borda da validade | `alocacao/SelecaoFefoTest`, `CriteriosAceiteAlocacaoTest`, `PropriedadesAlocacaoTest`, `estruturas/FilaPrioridadeFefoTest` |
| compatibilidade ABO/Rh, assimetria, tabela invertida do plasma | `compatibilidade/MatrizCompatibilidadeTest`, `alocacao/CasosBordaCompatibilidadeTest`, `api/CompatibilidadeAlocacaoTest` |
| alocação com rota e motivos de falha | `alocacao/ServicoAlocacaoRotaTest`, `MotivoFalhaAlocacaoTest`, `DemoPontaAPontaTest` |
| agregado da requisição: alocar, desfazer, cancelar, status derivado | `api/FluxoRequisicaoTest`, `api/AlocacaoAutomaticaTest`, `aceitacao/CenariosAceitacaoTest` |
| bloqueio otimista do item (`@Version`) e repetição | `concorrencia/ContagemItemConcorrenteTest` |
| a mesma bolsa não é reservada por duas alocações simultâneas | `concorrencia/CondicaoCorridaAlocacaoTest` |
| rota: transições, embarque, propagação de status, leituras | `api/RotaControllerTest`, `api/TemperaturaRemessaTest` |
| unidades: unicidade e bloqueio de exclusão | `api/HemocentroControllerTest` |
| índice de estoque e eventos após o commit | `estruturas/IndiceEstoqueTest`, `api/AlocacaoAutomaticaTest` |
| malha: grafo, Dijkstra, roteiro | `estruturas/GrafoTest`, `DijkstraTest`, `DijkstraCasosSemSolucaoTest`, `VizinhoMaisProximoTest`, `rede/CarregadorMalhaTest` |
| indicadores e modelo de demanda | `servico/IndicadorServicoTest`, `IndicadorServicoDadosSinteticosTest`, `estatistica/ModeloDemandaTest` |
| ingestão concorrente | `concorrencia/IngestaoConcorrenteTest` |
