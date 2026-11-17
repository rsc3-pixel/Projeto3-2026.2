# Demonstração do deploy contínuo: do commit à produção

**Tarefa:** PI3-110 (SO U2, rubrica "Deploy via CI/CD demonstrado", 25%)
**Subtarefas:** PI3-182 (roteiro), PI3-183 (registro de uma execução), PI3-184 (passo a passo reproduzível), PI3-185 (portão de qualidade)

Este documento permite que qualquer integrante, ou o professor, repita a demonstração sem ajuda e veja a mudança chegar à aplicação no ar. O critério de aceite pede uma demonstração reproduzível, não um print: por isso cada etapa diz o que observar e como conferir.

---

## 1. O que a demonstração prova

| Afirmação | Como a demonstração mostra |
|---|---|
| Um commit chega à produção sem intervenção manual | A mudança é feita, o PR é mesclado e ninguém acessa a VM: a alteração aparece em `rsc3-rotavital.duckdns.org` |
| O pipeline compila, testa e só então publica | Os jobs rodam em ordem: `Build`, depois `Testes`, depois `Empacotamento`, depois `Deploy na VM` |
| Um commit quebrado não chega à produção | A esteira vermelha do PR #72 para nos testes e não publica nada (seção 5) |
| O deploy confere o próprio resultado | O job de deploy só termina verde se a aplicação responder dentro da VM e pela URL pública |

---

## 2. O roteiro (PI3-182)

A mudança escolhida é **o texto do rodapé**, que aparece em todas as telas do painel Thymeleaf:

```
src/main/resources/templates/layout/base.html, linha 71
<span> · dados sinteticos, telemetria simulada</span>
```

Por que essa mudança:

- **É visível sem explicação.** Basta abrir qualquer tela e olhar o rodapé; não depende de conhecer a API.
- **É pequena e segura.** Não muda regra de negócio, então a suíte de testes passa e a demonstração não trava num teste que nada tem a ver com o deploy.
- **Dá para conferir pela linha de comando.** Um `curl` com `grep` mostra o texto novo, o que serve de prova objetiva além do navegador.
- **Cabe na apresentação.** O ciclo inteiro leva cerca de três minutos (seção 4).

---

## 3. Passo a passo reproduzível (PI3-184)

**Pré-requisitos:** acesso de escrita ao repositório, `git` e o GitHub CLI (`gh`) autenticado. Não é preciso acesso à VM: esse é justamente o ponto.

### 3.1 Conferir o estado atual da produção

```bash
curl -s https://rsc3-rotavital.duckdns.org/ | grep -o "dados sinteticos[^<]*"
```

**Observe:** a saída é o texto atual do rodapé. Guarde-o para comparar no final.

### 3.2 Fazer a mudança numa branch

```bash
git checkout main && git pull
git checkout -b demo/deploy-continuo
```

Edite a linha 71 de `src/main/resources/templates/layout/base.html`, acrescentando um marcador com a data, por exemplo:

```html
<span> · dados sinteticos, telemetria simulada · demo 17/11</span>
```

```bash
git commit -am "demo: marca o rodape para demonstrar o deploy continuo"
git push -u origin demo/deploy-continuo
```

### 3.3 Abrir o PR e acompanhar o portão de qualidade

```bash
gh pr create --fill
gh pr checks --watch
```

**Observe:** no PR rodam `Build`, `Testes` e `Front-end Angular`. `Imagem Docker`, `Deploy na VM` e `Deploy do front-end` aparecem como **SKIPPED**: código de PR aberto nunca vai para produção. A `main` exige `Build` e `Testes` verdes antes de aceitar o merge.

### 3.4 Mesclar e acompanhar o deploy

```bash
gh pr merge --merge
gh run watch "$(gh run list --branch main --limit 1 --json databaseId -q '.[0].databaseId')"
```

**Observe:** na `main` o pipeline roda completo. O job `Deploy na VM` só começa depois de `Empacotamento`, e executa três verificações antes de terminar verde:

| Verificação | O que confere |
|---|---|
| Confirmar que a aplicação subiu (dentro da VM) | `/actuator/health` em `127.0.0.1:8081`, até 36 tentativas de 5 s, porque a JVM leva cerca de 22 s para subir |
| Confirmar que o Nginx está encaminhando | o mesmo `/actuator/health`, agora pela URL pública com HTTPS |
| Confirmar que o console do H2 está inacessível | `/h2-console` não pode responder 200 em produção |

Se qualquer uma falhar, o job termina vermelho: o deploy não se declara concluído sem provar que a aplicação está no ar.

### 3.5 Conferir a produção

```bash
curl -s https://rsc3-rotavital.duckdns.org/ | grep -o "dados sinteticos[^<]*"
```

**Observe:** a saída agora contém o marcador `demo 17/11`. A mudança chegou à produção, e em nenhum momento alguém entrou na VM.

### 3.6 Desfazer

Reverta a alteração por outro PR (`git revert`), que passa pelo mesmo pipeline e devolve o rodapé ao texto original. Isso também demonstra que voltar uma versão segue o mesmo caminho seguro de publicar uma.

---

## 4. Registro de uma execução real (PI3-183)

Execução de 17/11/2026, disparada pelo merge do PR #95 na `main`:
[actions/runs/44005970734](https://github.com/rsc3-pixel/Projeto3-2026.2/actions/runs/44005970734)

| Job | Início (UTC) | Fim (UTC) | Resultado |
|---|---|---|---|
| Build | 02:51:27 | 02:51:44 | success |
| Front-end Angular | 02:51:27 | 02:52:09 | success |
| Testes (498) | 02:51:46 | 02:52:29 | success |
| Empacotamento | 02:52:32 | 02:53:01 | success |
| Deploy na VM | 02:53:04 | 02:53:51 | success |
| Imagem Docker | 02:52:33 | 02:54:16 | success |

**Do push (02:51:09) à aplicação atualizada e verificada em produção (02:53:51): 2 min 42 s.**

O encadeamento aparece nos horários: `Testes` só começa depois que `Build` termina, `Empacotamento` só depois de `Testes`, e `Deploy na VM` só depois de `Empacotamento`. `Front-end Angular` roda em paralelo, de propósito: uma quebra no front não deve impedir o deploy da API.

---

## 5. O portão barrando um commit ruim (PI3-185)

O [PR #72](https://github.com/rsc3-pixel/Projeto3-2026.2/pull/72) foi mantido aberto como evidência permanente. Ele contém uma asserção errada em `MatrizCompatibilidadeTest` e o resultado está registrado em [evidencias/e3-pipeline.md](evidencias/e3-pipeline.md):

| Job | Resultado |
|---|---|
| Build | success |
| Testes | **failure** |
| Empacotamento | skipped |
| Deploy na VM | skipped |

O teste vermelho interrompe a cadeia: nenhum artefato é gerado e nada chega à VM. Esse PR **nunca deve ser mesclado**.

---

## 6. Limites desta demonstração

- **O deploy envia o jar por SSH, não o container.** A imagem é construída e publicada no GitHub Container Registry a cada push na `main`, mas a VM tem 955 MB de memória e é compartilhada com outra aplicação; rodar os containers somaria cerca de 510 MB. A decisão está registrada no README.
- **O front-end Angular tem um deploy próprio.** O job `Deploy do front-end` publica em `/app/` o bundle que o job `Front-end Angular` compilou, independente do deploy da API: uma falha num não impede o outro. O registro da seção 4 é de antes desse job existir, por isso ele não aparece na tabela.
- **O H2 é em memória.** Cada deploy reinicia a aplicação e recarrega a carga inicial; dados criados antes do deploy somem. Para a demonstração isso não importa, mas vale saber antes de criar dados à mão e fazer um deploy em seguida.
- **O pipeline depende de três segredos** (`VM_HOST`, `VM_USUARIO`, `VM_CHAVE_SSH`). Num fork sem eles, o deploy é pulado com aviso, e o resto do pipeline continua valendo.
