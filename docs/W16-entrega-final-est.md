# W16 — Entrega final integrada de EST

**Data da revisão:** 18/11/2026. **Apresentação final acordada:** 04/12/2026 (substitui a janela antiga de 09 a 13/11 da história).

Esta entrega reúne as PI3-232 a PI3-236. **O fechamento integral permanece condicionado à conferência do Jira e ao ensaio real com o grupo.** Não há declaração de ensaio realizado nem de todas as histórias encerradas.

## 1. PI3-232 — indicadores e análises no painel

Foram consultados o painel Thymeleaf `/painel/HC01`, o healthcheck e nove endpoints de indicadores em produção. **Os 11 GETs retornaram HTTP 200** entre 19:33:59 e 19:34:11 de 18/11/2026 (Fortaleza). A conferência é de respostas HTTP e conteúdo textual; não é um teste visual de navegador.

- [Captura HTTP completa, com URLs, horários e respostas JSON](evidencias/w16-est/captura-http.json).
- [Texto extraído do painel publicado](evidencias/w16-est/painel-texto.txt).
- [Script de captura somente leitura](../dados/conferir_painel_est_w16.py).

| Indicador | Endpoint sob `/api/v1/indicadores` | Unidade e período | Dispersão/interpretação |
|---|---|---|---|
| Estoque | `/estoque-por-tipo`, `/estoque-por-componente` | Bolsas cadastradas na fotografia; a tela indica data | Contagens/categorias, não exigem desvio padrão por célula |
| Cobertura | `/cobertura` | Bolsas válidas / demanda registrada, período informado no painel | Razão, não probabilidade nem atendimento efetivo |
| Críticas | `/criticas` | Bolsas válidas com até 7 dias restantes, data de referência na resposta | Janela de validade; não mistura vencidas |
| Validade | `/validade` | Dias restantes nas bolsas válidas | Média, mediana, desvio padrão amostral, mínimo/máximo e separação por componente |
| Desabastecimento | `/desabastecimento` | Probabilidade em %, estoque na referência versus demanda no horizonte observado | API fornece variância; painel mostra variância/média e comparação com Poisson |
| Descarte | `/descarte` | Bolsas vencidas / total por componente, referência na legenda | Proporção observada; oportunidade FEFO é limite superior |
| Períodos de coleta | `/descarte/por-periodo` e `?granularidade=MES` | Grupos semanais ou mensais de coleta | Tela exibe as 12 semanas mais recentes; API contém série completa e meses |

Não se atribui desvio padrão a cada percentual ou contagem: a medida de dispersão pertinente está nas distribuições de validade e demanda. Unidades e períodos de endpoints que devolvem apenas listas são contextualizados pela legenda do painel e pelo contrato dos parâmetros, não por metadados inexistentes no JSON.

### Conferência dos valores publicados

Na captura, painel e API apresentaram **121 bolsas**, **41 críticas em até 7 dias**, **1 tipo com déficit** e **1 tipo com probabilidade de falta ≥ 50%**. A validade apresentou média **130,1 dias**, mediana **42,0**, desvio **141,3**, mínimo **3** e máximo **365**. Para AB−, o estoque era **9**, a demanda **13**, o déficit **4**, a dispersão **4,69** e a probabilidade BN **62,0%**. O horizonte observado era **16 a 18/11/2026 (3 dias)**. O descarte era zero nos quatro componentes.

Os GETs são sequenciais, não uma transação única; alterações durante a captura poderiam produzir divergências. Estes números são uma evidência datada, não valores a decorar para dezembro. A tela identifica o hemocentro selecionado, mas o controller chama os indicadores **globais da rede**, sem passar o ID ao serviço. Não apresentar essas contagens como estoque exclusivo do HEMOPE.

O painel em operação tem uma base diferente das 2.000 bolsas do relatório estatístico. Não se espera igualdade entre os dois conjuntos. A igualdade entre implementações é verificada com a mesma base sintética nos testes e no script da PI3-233.

### Ajuste encontrado nesta revisão

A legenda publicada dizia que as bolsas com pedido compatível “sob FEFO teriam saído antes”. A branch corrige essa afirmação para **oportunidade de alocação, limite superior, sem comprovação de perda evitada**, pois o cálculo não verifica suficiência da quantidade demandada nem transporte. A captura histórica conserva o texto anterior; não comprova a publicação da correção. Após merge/deploy, repetir a inspeção antes da apresentação.

A tentativa de print pelo navegador foi bloqueada pela revisão automática de aprovação (“blocked by policy”). As evidências disponíveis são JSON/texto obtidos por HTTP e os gráficos reproduzíveis da W14; não se declara inspeção visual completa.

## 2. PI3-233 — consolidação U1/U2

O [relatório W14](W14-relatorio-achados-est.md) permanece como documento central, com índice integrado das evidências U1/U2. São preservadas as datas e bases distintas. O comando `python dados/evidencias_est_u2.py --check` passou na base de `origin/main` usada para criar esta branch e deve ser repetido após o merge, sem antecipar o resultado de uma futura main.

| Entrega | Material de referência |
|---|---|
| U1: CRISP-DM, média/desvio e cobertura | [Sumário EST U1](evidencias-unidade-1-est.md), [resultados verificados](img/est-u1/resultados-verificados.json) |
| U2: demanda e probabilidade | [PI3-102](modelagem_demanda_desabastecimento_cpp.md), [JSON C++](../dados/painel_consolidado_pi3_32.json) |
| U2: descarte e oportunidade FEFO | [PI3-103](evidencias-pi3-103.md), [resultados Python/Java](../dados/indicadores_u2_esperado.json) |
| U2: integração | [PI3-32](evidencias-pi3-32.md), captura da seção 1 |
| Interpretação e decisão | [W14](W14-relatorio-achados-est.md), três gráficos e [script de conferência](../dados/evidencias_est_u2.py) |

## 3. PI3-234 — apresentação

O [roteiro específico de EST](roteiro-apresentacao-est-w16.md) está pronto para ensaiar: fala de 4 minutos, divisão por blocos, navegação, plano sem Internet e perguntas esperadas. A duração é uma proposta ajustável ao tempo atribuído pelo grupo. Complementa o [roteiro integrado de cinco minutos](roteiro-demonstracao.md), sem trocar o cronograma das outras disciplinas.

## 4. PI3-235 — registro de pendências e justificativas

Este quadro é uma **auditoria do repositório**, não uma consulta autenticada ao Jira. O status administrativo atual de cada issue não foi fornecido. Ausência de confirmação não significa que uma história esteja aberta ou fechada.

| Item | Evidência técnica | Pendência/justificativa | Responsável pela confirmação e ação |
|---|---|---|---|
| PI3-102 | Modelo e resultados disponíveis na main | Status Jira a confirmar | Grupo: informar status e eventuais critérios não atendidos |
| PI3-103 | Descarte por componente/período e testes disponíveis | Status Jira a confirmar; ganho causal de FEFO não foi demonstrado e não será declarado | Grupo: conferir aceite; manter ressalva no relato |
| PI3-32 | Integração Thymeleaf/API e captura HTTP | Status Jira a confirmar; não presumir paridade de indicadores no Angular | Grupo: apresentar Thymeleaf para EST conforme roteiro |
| W14 / PI3-165 a PI3-168 | Relatório integrado pelo PR #90 | Status Jira a confirmar | Grupo: confrontar critérios com as evidências W14 |
| PI3-232 | HTTP e conteúdo conferidos; legenda corrigida na branch | Publicação da legenda depende de merge/deploy; inspeção visual final pendente | Responsável pela entrega: verificar painel após publicação |
| PI3-233 | Relatório consolidado e `--check` aprovado | Repetir `--check` na main após merge | Responsável pela entrega |
| PI3-234 | Roteiro elaborado | Distribuição nominal das falas e ajuste de tempo pelo grupo | Apresentadores |
| PI3-235 | Este inventário e justificativas registrados | **Inventário Jira ainda não confirmado**; não certifica ausência de outras pendências | Grupo: enviar lista/exportação atual de EST U2 e registrar cada item adicional |
| PI3-236 | Modelo de registro disponível | **Ensaio não confirmado/registrado**; requer participação real de todos | Todos os integrantes: ensaiar, cronometrar e preencher registro |

Prazo de revisão dessas pendências: antes da apresentação de **04/12/2026**, com folga para corrigir problemas. Para encerrar a PI3-235, registrar data da consulta, códigos/status das histórias de EST U2 e justificativa, responsável e próximo passo para cada item aberto. Não basta copiar o estado deste quadro para o Jira.

## 5. PI3-236 — registro de ensaio (pendente)

**Não realizado ou não informado nesta entrega.** O roteiro preparado e o tempo estimado não substituem ensaio com o grupo.

| Campo | Registro a preencher após o ensaio |
|---|---|
| Data e horário | Pendente |
| Integrantes presentes | Pendente — conferir participação de todos |
| Divisão das falas | Pendente |
| Tempo real de cada bloco e total | Pendente — usar cronômetro |
| Ambiente, URL e commit apresentados | Pendente |
| Problemas encontrados | Pendente |
| Ajustes, responsáveis e prazo | Pendente |
| Segundo ensaio, se necessário | Pendente |
| Evidência | Pendente — ata confirmada pelo grupo ou link de gravação, se houver |

Verificar no ensaio: aviso de dados sintéticos; distinção entre fotografia de produção e base histórica; significado do horizonte; média versus dispersão; cobertura versus risco; limite superior de FEFO; viés de idade nos meses; respeito ao tempo; funcionamento do plano sem Internet. A história principal só deve ser encerrada quando o grupo confirmar esses critérios e o registro do Jira estiver completo.

## 6. Reprodução

Validação nesta branch: `--check` aprovado e **32 testes Java aprovados, zero falhas, erros ou ignorados**, conforme [resumo dos relatórios Surefire](evidencias/w16-est/validacao-testes.json). A execução Maven registrou `BUILD SUCCESS`. Esses testes conferem modelo, serviço e endpoints; não validam visualmente a alteração da legenda nem representam ensaio com o grupo.

```powershell
python dados/evidencias_est_u2.py --check
.\mvnw.cmd -B test "-Dtest=ModeloDemandaTest,IndicadorServicoDadosSinteticosTest,IndicadorServicoTest,IndicadorControllerTest"
# Nova captura em outra pasta para preservar o histórico:
python dados/conferir_painel_est_w16.py --saida docs/evidencias/w16-est-nova-conferencia
```

Não usar `-O` no Python: o script de evidências utiliza assertivas de conferência. Não gerar dados em produção para forçar os números da apresentação. O endpoint e o painel devem ser verificados novamente no dia da entrega.
