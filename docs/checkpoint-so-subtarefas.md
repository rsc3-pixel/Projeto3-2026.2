# Checkpoint Unidade 1 · SO — quebra em subtarefas

Decomposição do checkpoint em subtarefas verificáveis, para cadastro no Jira.

**Tarefa pai:** Checkpoint de entrega da Unidade 1 de Sistemas Operacionais
**Verificado em:** 29/09/2026
**Evidências consolidadas:** [docs/evidencias-unidade-1-so.md](evidencias-unidade-1-so.md)

---

## Como esta quebra foi feita

O corte é por **entregável verificável**, não por etapa cronológica. Cada
subtarefa tem um critério de aceite que alguém de fora consegue conferir
sozinho, rodando um comando ou abrindo uma URL. Subtarefa sem forma de
verificar vira "achismo de conclusão", e é justamente isso que um checkpoint
existe para evitar.

### O que o professor exige em cada ticket

Da avaliação da sprint anterior, aplicável a todo ticket do projeto:

| Campo | Observação |
|---|---|
| Responsável | sem ele o professor não consegue avaliar o aluno |
| Disciplina | vários tickets estavam sem, inclusive na sprint ativa |
| Unidade | campo **Versões corrigidas** |
| Descrição | **o resultado da atividade, no passado**, não o que será feito |
| Evidência | anexo, link ou comentário; afirmar que foi feito não basta |

A cobrança sobre a descrição apareceu nas duas avaliações seguidas: *"A
Descrição não representa o resultado da execução da atividade e sim uma
informação do que será realizado"*. As descrições das subtarefas concluídas
abaixo já estão escritas no passado, descrevendo resultado.

Texto pronto para cadastro, campo a campo:
`C:\tmp\rotavital-jira\subtarefas-para-cadastrar.md`

As seis primeiras (01 a 06) estão **concluídas** e a verificação abaixo foi
executada em 29/09/2026. As duas últimas (07 e 08) dependem de ação manual.

| # | Subtarefa | Estado |
|---|---|---|
| 01 | Pipeline de integração contínua | Concluída |
| 02 | Provisionamento da VM | Concluída |
| 03 | Deploy automático por SSH | Concluída |
| 04 | Publicação com domínio e HTTPS | Concluída |
| 05 | Perfil de produção e endurecimento | Concluída |
| 06 | Documentação das evidências | Concluída |
| 07 | Captura dos prints de evidência | **Pendente** |
| 08 | Registro no ticket | **Pendente** (depende da 07) |


---

## Subtarefa 01 · Pipeline de integração contínua

**Descrição**
Configurar GitHub Actions para compilar e testar a cada push na `main` e a
cada pull request, impedindo que código quebrado entre na branch principal.

**Entregável**
[.github/workflows/ci.yml](../.github/workflows/ci.yml)

**Critério de aceite**
- [x] Jobs `build`, `teste` e `empacotar` encadeados por `needs`
- [x] Dispara em `push` na `main` e em `pull_request`
- [x] Os 151 testes rodam no job `teste`
- [x] `concurrency` com `cancel-in-progress` evita deploys concorrentes
- [x] O jar só é gerado se os testes passaram

**Como verificar**
```bash
./mvnw test          # 151 testes, 0 falhas
```
Ou abrir a aba Actions e conferir os jobs da execução mais recente.

---

## Subtarefa 02 · Provisionamento da VM

**Descrição**
Preparar a VM para hospedar a aplicação como serviço de sistema, convivendo
com as outras aplicações já instaladas nela.

**Entregável**
[infra/provisionar.sh](../infra/provisionar.sh) · unidade systemd `rota-vital`

**Critério de aceite**
- [x] Script idempotente: rodar duas vezes não quebra nada
- [x] Serviço systemd `enabled`, sobe sozinho se a VM reiniciar
- [x] `Restart=always`, o processo volta se morrer
- [x] Usuário próprio `rotavital`, sem shell
- [x] Limites de memória definidos por medição, não por estimativa

**Como verificar**
```bash
systemctl is-active rota-vital     # active
systemctl is-enabled rota-vital    # enabled
```

**Resultado em 29/09/2026:** `active`, `enabled`, no ar desde 27/09 00:28 UTC,
214 MB de memória (limite 256).

---

## Subtarefa 03 · Deploy automático por SSH

**Descrição**
Publicar automaticamente na VM a cada push aprovado na `main`, sem intervenção
manual, e sem derrubar a aplicação se a transferência falhar.

**Entregável**
Job `deploy` em [.github/workflows/ci.yml](../.github/workflows/ci.yml)

**Critério de aceite**
- [x] Roda só na `main`, nunca em pull request
- [x] Chave SSH vem de segredo, nunca do código
- [x] `ssh-keyscan` registra o host (sem isto o ssh travaria esperando resposta)
- [x] Jar enviado para nome temporário e só depois movido
- [x] Deploy pulado com aviso, não com falha, se faltarem segredos
- [x] Verificação de saúde após reiniciar

**Detalhe de projeto**
O envio para `/tmp/rota-vital-novo.jar` antes de mover é deliberado: se a
transferência cair no meio, o jar em uso continua intacto e a aplicação segue
no ar.

**Como verificar**
Acompanhar o job `Deploy na VM` na execução mais recente da `main`.

---

## Subtarefa 04 · Publicação com domínio e HTTPS

**Descrição**
Tornar a aplicação acessível publicamente por domínio próprio, com conexão
cifrada e renovação automática de certificado.

**Entregável**
[infra/nginx-rota-vital.conf](../infra/nginx-rota-vital.conf) · certificado Let's Encrypt

**Critério de aceite**
- [x] Responde em https://rsc3-rotavital.duckdns.org
- [x] Certificado válido, renovação automática pelo timer do Certbot
- [x] Nginx roteia pelo cabeçalho `Host`, convivendo com as outras aplicações
- [x] `/h2-console` bloqueado na borda

**Como verificar**
```bash
curl -s https://rsc3-rotavital.duckdns.org/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

**Resultado em 29/09/2026:** todos os endpoints de negócio em `200`,
`/h2-console` em `404`, certificado válido até 07/12/2026.

---

## Subtarefa 05 · Perfil de produção e endurecimento

**Descrição**
Separar a configuração de produção da de desenvolvimento, fechando o que não
deve ficar exposto na internet.

**Entregável**
[application-prod.properties](../src/main/resources/application-prod.properties)

**Critério de aceite**
- [x] Console do H2 desligado
- [x] Stack trace fora das respostas de erro
- [x] Actuator restrito a `/health`
- [x] `server.address=127.0.0.1`, porta não exposta na internet
- [x] SQL fora do log

**Por que cada um**
Console de banco aberto é acesso direto aos dados. Stack trace entrega
estrutura interna a quem sondar a API. Os demais endpoints do Actuator expõem
configuração e variáveis de ambiente. Escutar em `127.0.0.1` garante que só o
Nginx alcança a aplicação.

**Como verificar**
```bash
curl -o /dev/null -w "%{http_code}\n" https://rsc3-rotavital.duckdns.org/h2-console
# 404
```

---

## Subtarefa 06 · Documentação das evidências

**Descrição**
Reunir num documento o que o checkpoint pede, com valores verificados e
comandos que qualquer pessoa consegue repetir.

**Entregável**
[docs/evidencias-unidade-1-so.md](evidencias-unidade-1-so.md)

**Critério de aceite**
- [x] URL da aplicação e resposta de cada endpoint
- [x] Descrição dos quatro jobs do pipeline
- [x] Estado do serviço na VM, com data da verificação
- [x] Comandos reproduzíveis, não só afirmações
- [x] Limitações conhecidas registradas

**Observação registrada**
O documento anota o cold start de ~20s na primeira requisição após ociosidade
(uma chegou a devolver `504`), caindo para menos de 1s nas seguintes. Não é
defeito, é o JIT da JVM com heap pequeno e `UseSerialGC`, mas atrapalha
demonstração ao vivo. Convém aquecer os endpoints antes de apresentar.

---

## Subtarefa 07 · Captura dos prints de evidência

**Estado:** pendente · **Responsável:** Renato

**Descrição**
Capturar as duas telas que o checkpoint exige como comprovação visual.

**Entregável**
`docs/img/pipeline-verde.png` · `docs/img/aplicacao-no-ar.png`

**Critério de aceite**
- [ ] Print do pipeline com os quatro jobs em verde, mostrando o commit
- [ ] Print do navegador em `/actuator/health`, com o cadeado do HTTPS visível
      junto do `{"status":"UP"}`
- [ ] Imagens commitadas em `docs/img/` e referenciadas no documento de evidências

**Passo a passo**
1. Abrir a aba Actions do repositório
2. Entrar na execução mais recente da `main`
3. Enquadrar o nome do commit e os quatro jobs
4. Abrir https://rsc3-rotavital.duckdns.org/actuator/health no navegador
5. Enquadrar a barra de endereço junto da resposta

**Opcional, reforça a parte de SO:** terminal com `systemctl status rota-vital`,
que evidencia o serviço gerenciado pelo sistema operacional.

---

## Subtarefa 08 · Registro no ticket

**Estado:** pendente · **Depende da:** 07

**Descrição**
Publicar as evidências no ticket, fechando o checkpoint.

**Critério de aceite**
- [ ] Comentário com URL da aplicação e resumo do pipeline
- [ ] Os dois prints anexados
- [ ] Link para `docs/evidencias-unidade-1-so.md`
- [ ] Ticket movido para concluído

**Texto pronto para colar**
`C:\tmp\rotavital-jira\comentario-checkpoint-so.md`

---

## O que estas subtarefas não resolvem

O professor levantou dois pontos que ficam **fora** do alcance deste
checkpoint e precisam de trabalho próprio:

**Cobertura dos demais integrantes.** As oito subtarefas acima são de um único
responsável porque foi ele quem as executou, e o professor aceita isso
explicitamente: *"Caso essa distribuição represente a verdade, sem problema."*
O ponto que continua aberto é outro: o trabalho dos demais integrantes
precisa de tickets próprios. A atividade de paralelismo, por exemplo, foi
implementada por outro integrante, está mergeada na `main` com 26 testes, e
sem ticket não aparece na avaliação de quem a fez.

**Tickets antigos incompletos.** A orientação foi revisar, na visão de Lista
com a coluna Sprint visível, os tickets já concluídos que estão sem
Descrição, Responsável, Disciplina, Unidade ou Evidência. É um mutirão
separado, provavelmente maior que esta quebra.

---

## Pendência fora do escopo do checkpoint

O Certbot mantém certificado para `rsc3-boolean.duckdns.org`, cuja aplicação
já saiu da VM. A renovação vai falhar quando o DNS deixar de resolver. Não
afeta o Rota Vital e não bloqueia o checkpoint, mas convém abrir ticket
próprio:

```bash
sudo certbot delete --cert-name rsc3-boolean.duckdns.org
```
