# W06 — Diferenças na arquitetura de rede em relação à W04 (PI3-22)

Este documento registra, por escrito, o que mudou no diagrama de arquitetura de rede (`diagrama_arquitetura_redes.drawio`) entre a versão da W04 (PI3-16) e a atualização da W06 (PI3-22), e por quê. A W04 desenhava uma arquitetura-alvo (VPC segmentada, load balancer, mensageria, cache, banco gerenciado); a W06 confronta esse desenho com o que o deploy da PI3-21 efetivamente colocou no ar.

| Elemento do diagrama W04 | Situação real na W06 | Justificativa |
|---|---|---|
| VPC segmentada em 3 sub-redes (pública / aplicação / dados) | Não existe: a VM roda na rede padrão do GCP, sem segmentação de sub-redes | Decisão pragmática: a VM compartilhada já existia antes deste projeto; segmentar a rede não trazia benefício para o escopo acadêmico desta entrega |
| Load Balancer (Nginx) na frente de múltiplas instâncias | Existe 1 Nginx, mas roteando por domínio entre duas aplicações distintas na mesma VM — não balanceia réplicas do Rota Vital | A previsão de um Nginx como único ponto de entrada estava correta; o papel real é de reverse proxy, não de load balancer de alta disponibilidade |
| API Backend com múltiplas réplicas | Instância única do Spring Boot | Sem necessidade demonstrada de escalar; a VM tem memória limitada (~577 MB disponíveis) compartilhada com outra aplicação |
| RabbitMQ + Worker Notifica | Não implementados | Nenhuma história de usuário até a W06 exige mensageria assíncrona; mantidos no diagrama-alvo para quando houver notificações (ex.: alerta de temperatura da HU-07) |
| PostgreSQL | Não implementado; em uso o H2 em memória | Migração prevista para a Entrega 02, conforme `application-prod.properties` e o README do repositório |
| Redis | Não implementado | Nenhum caso de uso de cache foi definido até esta entrega |
| NAT Gateway + Servidor de e-mail | Não implementados | Não há envio de e-mail ou notificação externa implementado no sistema |
| CI/CD via GitHub Actions (não existia na W04) | Implementado (PI3-21): build → teste → empacotar → deploy via SSH/SCP | Elemento novo em relação ao desenho da W04; passou a ser central no fluxo real e foi incorporado nesta atualização |
