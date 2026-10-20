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

> A preencher no merge do PR #71. O job `imagem` tem
> `if: github.ref == 'refs/heads/main'`, então ele só executa na `main`, e a
> primeira imagem no GHCR nasce desse merge.

| | |
|---|---|
| Execução | _a preencher_ |
| Imagem publicada | `ghcr.io/rsc3-pixel/rota-vital:<sha>` |
| Aplicação respondendo | <https://rsc3-rotavital.duckdns.org/actuator/health> |

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
