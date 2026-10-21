# Front-end em Angular

**História:** PI3-134 · **Responsáveis:** Victor e Guilherme

Ainda não iniciado. Esta pasta é o território do projeto Angular: o
`package.json`, o `angular.json` e tudo o mais nascem aqui dentro, como num
projeto independente.

```bash
cd frontend/angular
ng new rota-vital-web --directory . --routing --style css
```

## O que já está decidido

**Build para `/app`.** A aplicação é servida como subpasta do mesmo domínio, e
não em outro endereço, o que elimina CORS. No `angular.json`, aponte
`baseHref` para `/app/`.

**A API responde na mesma origem.** Em produção o caminho é `/api/v1/...`,
sem host. Em desenvolvimento, use o proxy do Angular apontando para
`http://localhost:8080`, assim o código não precisa saber em qual ambiente está.

**Subir o back sem instalar Java:**

```bash
docker compose up -d
```

## Telas previstas

Pelas subtarefas da PI3-134:

| Subtarefa | Tela |
|---|---|
| PI3-142 | estrutura do projeto |
| PI3-143 | serviço HTTP da API |
| PI3-144 | roteamento |
| PI3-145 | painel de indicadores |
| PI3-146 | estoque, hemocentros, hospitais, requisições e rotas |
| PI3-147 | build para a subpasta `/app` |

O contrato dos endpoints está em [`docs/contrato_api.md`](../../docs/contrato_api.md).
