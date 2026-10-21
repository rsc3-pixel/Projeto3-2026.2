# Front-ends

Os dois front-ends do Rota Vital vivem aqui. Eles consomem a mesma API Java,
que fica em [`src/main/java`](../src/main/java).

| Pasta | Stack | História | Quem toca |
|---|---|---|---|
| [`angular/`](angular/) | Angular, consumindo a API REST | PI3-134 | Victor e Guilherme |
| [`thymeleaf/`](thymeleaf/) | Thymeleaf, renderizado pelo Spring | PI3-133 | Renato |

## Por que ficam no mesmo repositório

Três razões práticas, não preferência de estilo:

**Sem CORS.** O Angular é servido pela mesma origem da API, em `/app`. Se
morasse em outro domínio, toda chamada viraria preflight e o Spring precisaria
de configuração de CORS, que quebra diferente em desenvolvimento e produção
assim que entra cookie ou header de autenticação.

**Um deploy só.** O merge na `main` dispara o pipeline que já entrega na VM.
Separado, seriam dois pipelines, dois conjuntos de segredos e a necessidade de
manter as duas versões em sincronia.

**O Thymeleaf não sai.** Ele é renderizado pelo próprio Spring Boot, a partir de
`src/main/resources/templates`. É parte da aplicação Java, não um projeto
separado.

A PI3-135 pede justamente servir os dois na mesma VM. Com tudo junto, isso é
configuração de Nginx.

## Trabalhando só no front

Não é preciso instalar Java nem rodar o back na mão. Suba a API em container:

```bash
docker compose up -d        # API em http://localhost:8080
```

Depois trabalhe na sua pasta normalmente. O Angular aponta para
`http://localhost:8080` em desenvolvimento.

Para não esbarrar no trabalho de ninguém, use uma branch por frente:

```bash
git checkout -b feat/front-angular      # ou feat/front-thymeleaf
```

Como as duas pastas são independentes, dois PRs simultâneos não conflitam.

## Onde o back-end expõe os dados

O contrato dos endpoints está em [`docs/contrato_api.md`](../docs/contrato_api.md).
Com a aplicação no ar, o Swagger fica em `/swagger-ui.html` no perfil de
desenvolvimento.
