# ADR 0001: Decomposição do Rota Vital

Status: proposto (24/11/2026)

## Contexto

O Rota Vital tem seis bounded contexts (Cadastro da Rede, Estoque, Requisição, Expedição, Roteirização, Indicadores) e roda hoje como um JAR numa VM. As premissas de carga da Parte 2 vão de 4 a 140 req/s no pico; pela Lei de Little, mesmo o cenário alto pede 28 requisições simultâneas, que uma JVM com 4 vCPU atende. O squad tem oito estudantes, sem plantão de operação, e o orçamento do cenário alto é de US$ 234/mês. A regra mais crítica do domínio (reservar bolsa e registrar a alocação) hoje é uma transação ACID única.

## Decisão

Manter um **monolito modular**: um deploy, com os pacotes organizados por bounded context e, dentro de cada um, em apresentação, negócio e dados. Os contextos conversam por interfaces de aplicação e eventos internos, nunca lendo as tabelas uns dos outros, para que a extração de um serviço seja mecânica. O primeiro candidato a serviço próprio é a **Expedição com telemetria**, e só quando a medição do cenário alto mostrar a ingestão de leituras disputando CPU ou banco com o resto.

## Consequências

- **Melhora:** a alocação continua consistente numa transação, sem saga; um processo, um log e um banco para operar; custo dentro da tabela da seção E.
- **Piora:** escala só da aplicação inteira; um bug num contexto pode derrubar todos; disciplina de fronteira depende de revisão de código (nada impede um import indevido entre contextos, salvo um teste de arquitetura a criar).
- **Alternativa descartada:** microsserviços por contexto desde já. Exigiria pelo menos seis processos, fila e quatro bancos, transformaria a alocação numa saga com compensação e multiplicaria o custo, para uma carga que cabe numa VM.
