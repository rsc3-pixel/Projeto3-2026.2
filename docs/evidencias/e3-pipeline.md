# Evidências — E3 · Deploy automatizado

Capturado em 20/10/2026.

O desafio pede duas provas: que a esteira barra uma entrega quando um teste
falha, e que ela entrega quando tudo passa. As duas estão abaixo.

## 1. Esteira vermelha: o portão barrou o deploy

Experimento deliberado na branch `test/esteira-vermelha`, no
[PR #72](https://github.com/rsc3-pixel/Projeto3-2026.2/pull/72). Uma única
linha foi alterada em `MatrizCompatibilidadeTest`, no teste que verifica que
O− doa para os oito grupos:

```java
// de:
assertTrue(MatrizCompatibilidade.compativel(O_NEG, receptor, CONCENTRADO_HEMACIAS), ...)
// para:
assertFalse(MatrizCompatibilidade.compativel(O_NEG, receptor, CONCENTRADO_HEMACIAS), ...)
```

[Execução 39463188571](https://github.com/rsc3-pixel/Projeto3-2026.2/actions/runs/39463188571)
· commit `9321a47` · 20/10/2026 17:06

```
Build             SUCCESS
Testes            FAILURE
Empacotamento     SKIPPED
Deploy na VM      SKIPPED
```

O que essa saída prova: **Empacotamento e Deploy não foram reprovados, eles não
chegaram a iniciar**. Os jobs são encadeados por `needs`, e o GitHub Actions não
inicia um job cuja dependência falhou. A versão que estava no ar permaneceu
intacta durante todo o experimento.

A corrente é transitiva:

```
build ← teste ← empacotar ← deploy
```

O job `deploy` declara `needs: empacotar`, e `empacotar` declara `needs: teste`.
Quebrar qualquer elo para tudo o que vem depois.

Esse PR não deve ser mergeado: existe apenas como evidência.

## 2. Esteira verde: a entrega aconteceu

[Execução 39466760242](https://github.com/rsc3-pixel/Projeto3-2026.2/actions/runs/39466760242)
· commit `712b63b` · 20/10/2026 17:34

```
Build             SUCCESS
Testes            SUCCESS
Empacotamento     SUCCESS
Imagem Docker     SUCCESS
Deploy na VM      SUCCESS
```

| | |
|---|---|
| Imagem publicada | `ghcr.io/rsc3-pixel/rota-vital:712b63b` e `:latest` |
| Digest | `sha256:252eb50af8cd3ebf85f04fe46b7c1c9bdca6a195f9fb5a8210f303288d62fd2f` |
| Aplicação respondendo | <https://rsc3-rotavital.duckdns.org/actuator/health> |

Os dois nomes apontam para o mesmo digest: `latest` para quem quer a última
versão, e a tag do SHA para quem precisa de uma específica, que é o que torna o
rollback possível.

### A primeira tentativa falhou, e vale registrar

O job `imagem` tem `if: github.ref == 'refs/heads/main'`, então ele nunca
executou em PR algum. Sua estreia foi no merge do PR #71
([execução 39466040276](https://github.com/rsc3-pixel/Projeto3-2026.2/actions/runs/39466040276)),
e ele falhou:

```
ERROR: failed to build: Cache export is not supported for the docker driver
```

O passo de build usa `cache-to: type=gha` para não rebaixar as dependências do
Maven a cada merge, e esse cache exige o driver `docker-container` do Buildx. O
runner vem com o driver `docker` padrão, que não exporta cache. Faltava o
`setup-buildx-action`, corrigido no
[PR #73](https://github.com/rsc3-pixel/Projeto3-2026.2/pull/73).

O que essa falha demonstra, e por isso ela fica registrada: **o dano ficou
contido em um job**. Build, testes, empacotamento e deploy concluíram, e a
aplicação seguiu no ar durante todo o período. Um pipeline com os jobs
encadeados corretamente falha onde o defeito está, e não em cascata.

## Rollback

O jar anterior fica guardado na VM a cada deploy:

```bash
sudo systemctl stop rota-vital
sudo cp /opt/rota-vital/rota-vital.jar.anterior /opt/rota-vital/rota-vital.jar
sudo systemctl start rota-vital
```

Pela imagem, quando o deploy por container estiver ativo, qualquer commit já
mergeado pode ser restaurado, porque cada imagem carrega a tag do seu SHA:

```bash
docker compose pull ghcr.io/rsc3-pixel/rota-vital:<sha-anterior>
docker compose up -d
```

## Por que o deploy entrega o jar, e não a imagem

A VM tem 955 MB de memória e 296 livres. Os containers da aplicação e do banco,
mais o daemon do Docker, somariam cerca de 510 MB, e subi-los lá derrubaria a
própria aplicação e o outro projeto que divide a máquina.

O pipeline publica a imagem a cada merge, versionada por commit. O deploy por
container entra quando a infraestrutura comportar. A decisão está registrada na
tabela do README.
