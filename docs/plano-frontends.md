# Plano dos dois front-ends

Dois front-ends sobre a mesma aplicação: um em **Thymeleaf**, renderizado no
servidor, com identidade visual de arcade; outro em **Angular**, consumindo a
API REST, com cara de painel operacional.

Planejamento escrito em 08/10/2026. Nenhum código ainda.

---

## Por que dois, e por que tão diferentes

Não é redundância: são **duas arquiteturas distintas** sobre o mesmo domínio,
e é justamente o contraste que torna o exercício didático.

```
Thymeleaf   navegador → @Controller → Serviço → Banco
                        (HTML pronto vem do servidor)

Angular     navegador → fetch → @RestController → Serviço → Banco
                        (navegador monta a tela com JSON)
```

O Thymeleaf **não consome a API REST**. Ele chama os serviços Java
diretamente e devolve HTML montado. Isso significa controllers próprios,
anotados com `@Controller` em vez de `@RestController`, e nenhuma linha
compartilhada com o Angular.

Consequência prática: o trabalho **não se reaproveita** entre os dois. Vale
saber disso antes de estimar prazo.

---

## Como ficam no mesmo domínio

Decisão: subpasta, um só domínio, um só certificado, sem CORS.

```
rsc3-rotavital.duckdns.org/          → Thymeleaf   (porta 8081)
rsc3-rotavital.duckdns.org/app       → Angular     (arquivos estáticos)
rsc3-rotavital.duckdns.org/api/v1    → API REST    (porta 8081)
```

O Angular compila para arquivos estáticos que o próprio Nginx serve. Não há
processo Node rodando na VM, o que importa numa máquina compartilhada com
outras duas aplicações.

Como a origem é a mesma, o navegador não trata as chamadas do Angular à API
como cross-origin, e **nada de CORS precisa ser configurado**.

### O que muda no Nginx

Um `location` novo, antes do `location /`:

```nginx
location /app {
    alias /var/www/rota-vital-app;
    try_files $uri $uri/ /app/index.html;
}
```

O `try_files` terminando em `index.html` é o que faz o roteamento do Angular
funcionar: ao abrir `/app/estoque` direto, o Nginx não acha esse arquivo e
entrega o `index.html`, deixando o Angular resolver a rota. Sem isso, F5 em
qualquer tela interna devolve 404.

### Correção pendente no Nginx, independente dos fronts

```nginx
proxy_read_timeout 30s;   # atual
```

É esta linha que causa os 504 observados em 29/09 e 30/09. O cold start da
JVM passa de 30 segundos, e o Nginx desiste antes de a aplicação responder.
Não é a aplicação que falha, é o proxy cortando.

Correção: subir para `90s`. Melhor ainda, somar uma chamada de aquecimento ao
fim do deploy, para que o primeiro usuário nunca pague o cold start.

---

# Front 1 · Thymeleaf arcade

Identidade visual de fliperama dos anos 80: pixel art, paleta neon sobre
fundo escuro, fonte bitmap, e as bolsas de sangue percorrendo linhas
tracejadas entre hemocentros e hospitais.

## O cuidado que o tema exige

O domínio é distribuição de sangue para hospitais. O visual pode ser lúdico
**sem** banalizar o assunto: nada de "pontuação", "vidas", "game over" ou
morte de paciente como derrota. O arcade entra como linguagem visual, não
como competição sobre desfecho clínico.

Onde a gamificação cabe bem:

| Elemento arcade | Significado real |
|---|---|
| Bolsa animada na linha tracejada | rota em trânsito |
| Barra de energia | nível de estoque por tipo |
| Piscar em vermelho | bolsa próxima do vencimento |
| Contador regressivo | dias até a validade |
| Mapa de nós conectados | grafo da rede de distribuição |

Tudo isso já existe no back-end: rota, estoque, validade, grafo. O arcade
**representa** dado real, não inventa mecânica.

## Telas

| Tela | Rota | O que mostra | Vem de |
|---|---|---|---|
| Mapa da rede | `/` | hemocentros e hospitais como nós, rotas como linhas tracejadas, bolsas em trânsito animadas | `Grafo`, `Rota` |
| Estoque | `/estoque` | barras de energia por tipo sanguíneo, piscando quando crítico | `IndicadorServico` |
| Fila FEFO | `/fefo` | bolsas enfileiradas por validade, a próxima destacada | `SelecaoFefo` |
| Requisições | `/requisicoes` | pedidos dos hospitais, com estado | `RequisicaoServico` |
| Painel | `/painel` | indicadores da rede em estilo placar | `IndicadorServico` |

## Como a animação funciona

A linha tracejada entre dois nós é um `path` SVG. A bolsa percorre esse
caminho por animação CSS sobre `offset-path`, sem JavaScript de animação.

O dado vem do servidor: o Thymeleaf renderiza um `path` por rota ativa, com
a duração proporcional ao tempo estimado. Quando não há rota em trânsito,
não há bolsa animada; a tela reflete o estado real.

Para a tela não ficar estática entre recargas, a opção mais simples é
recarregar sozinha a cada N segundos. Suficiente para demonstração, e evita
trazer WebSocket para o escopo.

## O que precisa existir no back

- Dependência `spring-boot-starter-thymeleaf` no `pom.xml`
- Pasta `src/main/resources/templates/` com os `.html`
- Pasta `src/main/resources/static/` com CSS, fonte bitmap e sprites
- Controllers novos anotados com `@Controller`, devolvendo nome de template
  e preenchendo o `Model`, sem tocar nos `@RestController` existentes

Nada do que existe hoje precisa mudar. As duas camadas convivem.

---

# Front 2 · Angular

Painel operacional, sóbrio, consumindo a API REST. Aqui o objetivo declarado
é **aprender Angular**, então o plano privilegia encontrar os conceitos do
framework, não terminar rápido.

## Por que o contraste visual ajuda

Com o Thymeleaf arcade e o Angular sóbrio, a apresentação mostra dois
registros visuais e duas arquiteturas. É mais forte do que dois fronts
parecidos.

## Conceitos de Angular que o projeto vai exercitar

| Conceito | Onde aparece |
|---|---|
| Componentes standalone | cada tela |
| Serviços e injeção de dependência | cliente HTTP da API |
| `HttpClient` | consumo dos endpoints |
| Roteamento | navegação entre telas |
| Signals | estado reativo das listas |
| Formulários reativos | cadastro e filtros |
| Pipes | formatação de data e validade |

A injeção de dependência do Angular é parente próxima da do Spring que você
já escreve no back. Vale usar essa ponte ao estudar.

## Telas

| Tela | Rota | Endpoint |
|---|---|---|
| Painel | `/app` | `/api/v1/indicadores/*` |
| Estoque | `/app/bolsas` | `/api/v1/bolsas` |
| Hemocentros | `/app/hemocentros` | `/api/v1/hemocentros` |
| Hospitais | `/app/hospitais` | `/api/v1/hospitais` |
| Requisições | `/app/requisicoes` | `/api/v1/requisicoes` |
| Rotas | `/app/rotas` | `/api/v1/rotas` |

A API já tem os sete recursos e seis endpoints de indicadores prontos. Nenhum
endpoint novo é necessário para o Angular funcionar.

## Configuração que o deploy exige

O Angular precisa saber que vive sob `/app`:

```
ng build --base-href /app/
```

Sem isso, os caminhos de script e estilo apontam para a raiz e nada carrega.
É o erro mais comum ao servir Angular em subpasta.

## No pipeline

O job de deploy ganha um passo: compilar o Angular e enviar a pasta `dist`
para `/var/www/rota-vital-app` na VM. Node 24 já está disponível na máquina
local; no runner, basta `actions/setup-node`.

---

# Ordem sugerida

A ordem abaixo é sugestão, não imposição. O critério é entregar cedo o que o
professor pediu e deixar o aprendizado de Angular com folga.

| # | Etapa | Depende de |
|---|---|---|
| 1 | Corrigir `proxy_read_timeout` no Nginx | nada |
| 2 | Thymeleaf: dependência, estrutura, uma tela | 1 |
| 3 | Thymeleaf: identidade arcade e animação | 2 |
| 4 | Thymeleaf: telas restantes | 3 |
| 5 | Angular: projeto, estrutura, serviço HTTP | nada |
| 6 | Angular: telas | 5 |
| 7 | Nginx: `location /app` | 6 |
| 8 | Pipeline: build e envio do Angular | 7 |

As etapas 1 a 4 e 5 a 6 são independentes entre si: duas pessoas podem tocar
em paralelo.

---

# Riscos

**Escopo do Thymeleaf indefinido.** O professor pediu Thymeleaf sem dizer se
precisa cobrir todas as telas ou só demonstrar a técnica. A resposta muda o
tamanho de dias para semanas. **Vale perguntar antes de começar.**

**Angular com curva de aprendizado em período cheio.** São cinco disciplinas
rodando juntas até dezembro. Se o Angular atrasar, o Thymeleaf sozinho já
cumpre o que foi pedido explicitamente.

**Cold start na demonstração.** Enquanto o `proxy_read_timeout` não subir, a
primeira requisição após ociosidade pode devolver 504. Aquecer antes de
qualquer apresentação:

```bash
curl -s -o /dev/null https://rsc3-rotavital.duckdns.org/api/v1/hemocentros
curl -s -o /dev/null https://rsc3-rotavital.duckdns.org/api/v1/bolsas
```

**VM compartilhada.** A máquina hospeda três aplicações. O Angular compilado
é estático e não acrescenta processo, mas o build no runner, não na VM, é o
que mantém isso verdadeiro.
