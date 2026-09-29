# Checkpoint Unidade 1 · Sistemas Operacionais

Evidências de pipeline CI/CD, deploy automático e aplicação em produção.

**Verificado em:** 29/09/2026
**Repositório:** `rsc3-pixel/Projeto3-2026.2` (privado, autorizado pelo professor)

---

## 1. Aplicação no ar

**URL pública:** https://rsc3-rotavital.duckdns.org

| Endpoint | Resposta |
|---|---|
| [`/actuator/health`](https://rsc3-rotavital.duckdns.org/actuator/health) | `200` · `{"groups":["liveness","readiness"],"status":"UP"}` |
| [`/api/v1/hemocentros`](https://rsc3-rotavital.duckdns.org/api/v1/hemocentros) | `200` |
| [`/api/v1/bolsas`](https://rsc3-rotavital.duckdns.org/api/v1/bolsas) | `200` |
| [`/api/v1/hospitais`](https://rsc3-rotavital.duckdns.org/api/v1/hospitais) | `200` |
| `/h2-console` | `404` (bloqueado em produção, como esperado) |

Verificação reproduzível:

```bash
curl -s https://rsc3-rotavital.duckdns.org/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

HTTPS por Let's Encrypt, certificado válido até **07/12/2026**, renovação
automática pelo timer do Certbot.

---

## 2. Pipeline CI/CD

**GitHub Actions** · [.github/workflows/ci.yml](../.github/workflows/ci.yml)

### Gatilhos

| Evento | O que dispara |
|---|---|
| `push` na `main` | pipeline completo, incluindo deploy |
| `pull_request` para a `main` | build e testes, sem deploy |
| `workflow_dispatch` | execução manual pela aba Actions |

O gatilho de `pull_request` é o que impede código quebrado de entrar na `main`:
o resultado aparece no próprio PR antes do merge.

### Os quatro jobs

```
build  ──▶  teste  ──▶  empacotar  ──▶  deploy
```

| Job | O que faz | Condição |
|---|---|---|
| **Build** | `mvnw clean compile` | sempre |
| **Testes** | `mvnw test` (151 testes) | `needs: build` |
| **Empacotamento** | gera o jar, publica como artefato | `needs: teste` |
| **Deploy na VM** | envia por SSH, reinicia o serviço, verifica saúde | `needs: empacotar` + só na `main`, nunca em PR |

O encadeamento por `needs` garante que um jar só é gerado se os testes
passaram, e que um deploy só acontece se o jar existe.

### Controle de concorrência

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

Dois pushes seguidos disputariam o deploy, e o mais antigo poderia
sobrescrever o mais recente na VM. Com isto, a execução anterior é cancelada.

---

## 3. Deploy automático

### Como o jar chega na VM

1. O job baixa o artefato gerado pelo `empacotar`
2. Monta a chave SSH a partir do segredo `VM_CHAVE_SSH`
3. Registra a impressão digital do servidor com `ssh-keyscan` (sem isto o
   `ssh` perguntaria "deseja continuar?" e travaria, pois não há ninguém
   para responder)
4. Envia por `scp` para `/tmp/rota-vital-novo.jar`, **nome temporário**
5. Só depois move para o lugar final e reinicia o serviço

Enviar para um nome temporário é deliberado: se a transferência cair no meio,
o jar em uso continua intacto e a aplicação segue no ar.

### Segredos

| Segredo | Para quê |
|---|---|
| `VM_HOST` | endereço da VM |
| `VM_USUARIO` | usuário do SSH |
| `VM_CHAVE_SSH` | chave privada de deploy |

Se algum faltar, o job não falha: emite `::warning::` e pula o deploy. Assim
um fork ou um clone sem segredos ainda roda build e testes.

### Verificação pós-deploy

O pipeline não considera o deploy concluído só porque o serviço reiniciou.
Ele confere, em ordem:

1. saúde em `127.0.0.1:8081`, por dentro da VM
2. saúde pela URL pública, atravessando o Nginx
3. que `/h2-console` responde 404, ou seja, o perfil de produção está ativo

---

## 4. Infraestrutura

### Serviço systemd

Estado verificado na VM em 29/09/2026:

```
systemctl is-active rota-vital    →  active
systemctl is-enabled rota-vital   →  enabled
ActiveEnterTimestamp              →  Sun 2026-09-27 00:28:56 UTC
MemoryCurrent                     →  214 MB
```

`enabled` significa que a aplicação sobe sozinha se a VM reiniciar.
`Restart=always` na unidade traz o processo de volta se ele morrer.

Endurecimento: `NoNewPrivileges`, `ProtectSystem=strict`, usuário próprio
`rotavital` sem shell.

### VM compartilhada

A VM hospeda três aplicações. A convivência é resolvida no Nginx, que decide
o destino pelo cabeçalho `Host`:

| Domínio | Aplicação | Porta interna |
|---|---|---|
| `rsc3-rotavital.duckdns.org` | **Rota Vital** | 8081 |
| `rsc3-flux.duckdns.org` | Flux | — |
| `rsc3-boolean.duckdns.org` | (certificado órfão) | — |

A aplicação escuta em `server.address=127.0.0.1`, ou seja, **não é alcançável
de fora** a não ser pelo Nginx. A porta 8081 não está exposta na internet.

### Limites de memória

```
-Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC
```

Estes valores vieram de medição, não de estimativa: com heap de 256 MB o
processo real ficou em 331 MB de RSS. O `UseSerialGC` evita as threads extras
do G1, que não se pagam em uma VM pequena compartilhada.

---

## 5. Perfil de produção

[`application-prod.properties`](../src/main/resources/application-prod.properties)

| Configuração | Valor | Por quê |
|---|---|---|
| `spring.h2.console.enabled` | `false` | console de banco aberto na internet é acesso direto aos dados |
| `server.error.include-stacktrace` | `never` | stack trace entrega estrutura interna a quem sondar a API |
| `management.endpoints.web.exposure.include` | `health` | só o health; os demais expõem configuração e ambiente |
| `spring.jpa.open-in-view` | `false` | evita segurar conexão do pool durante a renderização da resposta |
| `server.address` | `127.0.0.1` | fecha a porta para fora, só o Nginx alcança |
| `springdoc.swagger-ui.enabled` | `false` | a documentação é servida pelo Scalar em `/docs` |

---

## 6. Observação: primeira requisição lenta

Medido em 29/09/2026, com a aplicação parada havia horas:

| Requisição | Tempo |
|---|---|
| 1ª a `/api/v1/hemocentros` | **20,3 s** (uma chegou a dar `504`) |
| 2ª em diante | **0,8 s a 1,3 s** |

É *cold start*: a JVM paga o JIT e a inicialização do contexto JPA na
primeira chamada de cada rota. Com `UseSerialGC` e heap pequeno, o efeito é
mais visível.

Não é defeito, mas **afeta demonstração ao vivo**. Antes de apresentar, vale
aquecer:

```bash
curl -s -o /dev/null https://rsc3-rotavital.duckdns.org/api/v1/hemocentros
curl -s -o /dev/null https://rsc3-rotavital.duckdns.org/api/v1/bolsas
```

Correção possível, se virar incômodo: `proxy_read_timeout` maior no Nginx, ou
uma chamada de aquecimento no fim do deploy.

---

## 7. Pendência conhecida

O Certbot mantém certificado para `rsc3-boolean.duckdns.org`, domínio cuja
aplicação já foi removida da VM. A renovação vai falhar quando o DNS não
resolver mais. Não afeta o Rota Vital. Remoção:

```bash
sudo certbot delete --cert-name rsc3-boolean.duckdns.org
```

---

## Referências

| Documento | O que cobre |
|---|---|
| [docs/deploy.md](deploy.md) | processo de deploy passo a passo |
| [.github/workflows/ci.yml](../.github/workflows/ci.yml) | o pipeline |
| [infra/provisionar.sh](../infra/provisionar.sh) | provisionamento da VM |
| [infra/nginx-rota-vital.conf](../infra/nginx-rota-vital.conf) | proxy reverso |
