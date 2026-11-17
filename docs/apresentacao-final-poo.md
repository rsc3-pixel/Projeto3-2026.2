# Apresentação final de POO · roteiro de 8 minutos

**Tarefa:** PI3-93 (subtarefas PI3-218 a PI3-221)
**Quando:** no dia da aula, entre 09/11 e 13/11; vale 10% do Módulo II.
**Regra:** todos os integrantes presentes; até 8 minutos; slides cobrindo os
tópicos obrigatórios; slides dizendo que os dados são sintéticos, a telemetria
é simulada e a compatibilidade ABO/Rh é didática.

Os slides estão em [`apresentacao/apresentacao-final-poo.html`](../apresentacao/apresentacao-final-poo.html)
(abrir no navegador; setas para navegar, `F` para tela cheia). Este documento é
o roteiro de quem fala, com o tempo de cada slide.

## Cronômetro

| Slide | Tópico obrigatório | Tempo | Acumulado |
|---|---|---|---|
| 1 | Capa | 0:20 | 0:20 |
| 2 | Problema | 1:00 | 1:20 |
| 3 | Solução: características e público | 0:50 | 2:10 |
| 4 | Solução: histórias implementadas | 0:50 | 3:00 |
| 5 | Solução: diferencial | 0:30 | 3:30 |
| 6 | Fluxo de trabalho | 1:30 | 5:00 |
| 7 | Ferramentas, com links | 0:20 | 5:20 |
| 8 | Demo (vídeo curto) | 1:00 | 6:20 |
| 9 | Lições aprendidas | 0:50 | 7:10 |
| 10 | Limites: sintético, simulado, didático | 0:30 | 7:40 |
| 11 | Encerramento | 0:20 | 8:00 |

Margem zero: se o ensaio passar de 7:30, cortar do slide 6 (fluxo) antes de
cortar do 2 (problema) ou do 10 (limites). O 10 é critério de aceite.

## Fala por slide

**1 · Capa (0:20).** "Rota Vital, plataforma de distribuição de hemocomponentes.
Equipe E2: [nomes]. Tudo que vamos mostrar usa dados sintéticos e telemetria
simulada."

**2 · Problema (1:00).** "A rede de sangue precisa do componente certo, no lugar
certo, no tempo certo, na temperatura certa. Quando falha, há desabastecimento
de um lado e descarte do outro. Na nossa base sintética, 90% das plaquetas
venciam sem gestão de estoque; 187 bolsas venciam em 7 dias; a cobertura ia de
50% em AB− a 122% em O−; e Jaboatão tinha déficit de 350 bolsas enquanto o
Recife tinha folga de 157. Não é só volume: é distribuição."

**3 · Solução (0:50).** "Uma aplicação web que gerencia estoque, recebe
requisições, aloca bolsas compatíveis priorizando validade, calcula rotas por
tempo respeitando cadeia fria e mostra painéis. Quatro perfis: técnico do
hemocentro, agência transfusional, coordenador da rede e motorista."

**4 · Histórias (0:50).** "Oito histórias, todas implementadas e em produção.
Entrega 2: FEFO e indicadores. Entrega 3: temperatura por componente e
compatibilidade ABO/Rh. Entrega 4, esta: prazo pela prioridade, bolsas em
risco com janela configurável, e cálculo de rota com previsão de chegada.
Cada uma lê e escreve no banco."

**5 · Diferencial (0:30).** "A regra clínica está no código, e na ordem certa:
filtro antes da ordenação no FEFO, compatibilidade assimétrica, faixa de
temperatura por componente, rota por tempo e não por distância. E duas
alocações simultâneas não perdem contagem: bloqueio otimista com repetição."

**6 · Fluxo de trabalho (1:30).** "Planejamento no Jira, sprints semanais com
story points. Requisitos como histórias com cenários BDD, story map, protótipo
no Figma e contrato de API antes do código. Desenvolvimento em Java 21 e
Spring Boot 4, três camadas, sem Lombok, GitHub Flow com PR revisado.
Gerência de configuração: branches por tipo, issues como bug tracker, pipeline
no GitHub Actions com build, testes, imagem e deploy, numa VM do Google Cloud
atrás de Nginx com HTTPS, e Docker Compose para subir do zero. Qualidade: a
suíte em três níveis, unitário, integração e aceitação; um teste vermelho
barra o deploy, e provamos isso de propósito no PR 72."

**7 · Ferramentas (0:20).** Ler os links do slide. Não detalhar.

**8 · Demo (1:00).** Rodar o vídeo curto (sem depender da rede): painel de
estoque crítico com 7/15/30 dias; cálculo de rota HEMOPE → HC UFPE; criação
da rota com chegada calculada; embarque e saída. Narrar por cima do vídeo.

**9 · Lições (0:50).** "Primeira: a ordem das operações é regra de negócio;
filtrar depois de ordenar poria a bolsa vencida no topo. Segunda: o teste
achou uma condição de corrida real que nenhum log mostrava. Terceira:
verificar antes de afirmar; a documentação dizia que a colisão do hash era
rara, e a medição mostrou o contrário. Quarta: o histórico do git não prova
TDD; a evidência está nos testes e na reprodução. Quinta: a infraestrutura
compartilhada moldou decisões, da porta 8081 ao timeout do proxy."

**10 · Limites (0:30).** "Três avisos que valem para tudo que mostramos: os
dados são sintéticos, sem doador ou paciente real; a telemetria de temperatura
e GPS é simulada; e a compatibilidade ABO/Rh é didática e não substitui
protocolo clínico."

**11 · Encerramento (0:20).** "Repositório, aplicação no ar e documentação
estão nos links. Obrigado."

## Checklist das subtarefas

- [x] PI3-218 · slides montados (`apresentacao/apresentacao-final-poo.html`)
- [ ] PI3-219 · demo gravada em vídeo curto (reaproveitar o trecho de 1:00 a
      3:30 do screencast de uso, ver `roteiro-screencasts-e04.md`)
- [x] PI3-220 · slide 1 e slide 10 declaram dados sintéticos, telemetria
      simulada e compatibilidade didática
- [ ] PI3-221 · ensaio com todos, cronometrado; anotar o tempo real aqui:
      ______ (alvo 8:00, corte a partir do slide 6 se passar de 7:30)

## Quem fala o quê

Sugestão para os oito estarem presentes e falarem: dividir por slide, dois
slides por dupla, e a demo narrada por quem gravou. Preencher antes do ensaio:

| Slides | Quem |
|---|---|
| 1, 2 | |
| 3, 4, 5 | |
| 6 | |
| 7, 8 | |
| 9, 10, 11 | |
