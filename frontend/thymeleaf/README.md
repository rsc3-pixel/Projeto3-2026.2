# Front-end em Thymeleaf

**História:** PI3-133 · **Responsável:** Renato

Ainda não iniciado. Identidade visual de jogo arcade, conforme definido com a
equipe.

## Onde os arquivos realmente ficam

Diferente do Angular, o Thymeleaf não é um projeto separado: ele é renderizado
pelo próprio Spring Boot. Os arquivos nascem dentro de `src/main/resources`:

| O que | Onde |
|---|---|
| Templates `.html` | `src/main/resources/templates/` |
| CSS, imagens, fontes | `src/main/resources/static/` |
| Controllers web | `src/main/java/com/rotavital/web/` |

Esta pasta existe para documentar as decisões de interface e servir de ponto de
entrada de quem procura o front. O código mora no lugar que o Spring espera.

## Por que não dá para separar em outro repositório

O Thymeleaf resolve os templates no classpath da aplicação, em tempo de
renderização. Tirá-lo daqui significaria empacotar os templates à parte e
carregá-los por outro mecanismo, o que troca uma coisa que funciona por
complexidade sem ganho.

## Subtarefas

| Subtarefa | O que faz |
|---|---|
| PI3-136 | dependência do Thymeleaf e estrutura de pastas |
| PI3-137 | camada de controllers web |
| PI3-138 | identidade visual arcade |
| PI3-139 | mapa da rede com bolsas animadas |
| PI3-140 | estoque, fila FEFO, requisições e painel |
| PI3-141 | atualização automática das telas |

O painel estático atual, que estas telas substituem, está em
`src/main/resources/static/painel.html`.
