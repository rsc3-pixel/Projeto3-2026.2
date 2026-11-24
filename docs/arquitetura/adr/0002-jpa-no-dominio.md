# ADR 0002: Anotações JPA nas entidades de domínio

Status: aceito como temporário (24/11/2026); revisar na Parte 3

## Contexto

A regra de dependência das três camadas diz que o modelo de domínio não depende de banco nem de framework. No Rota Vital, as entidades (`Bolsa`, `Requisicao`, `Rota`...) são ao mesmo tempo o modelo de domínio e o mapeamento JPA (`@Entity`, `@ManyToOne`, `@Version`), e os contratos de repositório estendem `JpaRepository`. Em 24/11 o squad passou as regras de negócio para dentro dessas entidades (modelo rico, PR #115), com 549 testes passando, e precisava decidir se separava também o modelo de persistência no mesmo prazo.

## Decisão

Manter, por enquanto, as anotações JPA nas entidades de domínio, e registrar isso como **desvio consciente** da regra de dependência. O comportamento de negócio já fica no domínio, sem nenhuma chamada a repositório ou a Spring dentro das entidades. Na Parte 3, os contratos de repositório passam a ser interfaces puras no domínio, implementadas na camada de dados, e o mapeamento JPA vai para classes da camada de dados quando um contexto for extraído ou quando os testes de domínio precisarem rodar sem o JPA no classpath.

## Consequências

- **Melhora:** entrega a parte que mais muda o comportamento (regras no domínio) sem dobrar o número de classes; o histórico e os testes continuam válidos; o Hibernate segue cuidando de lazy loading e de bloqueio otimista.
- **Piora:** o domínio ainda compila contra `jakarta.persistence`; detalhes de persistência (construtor protegido, coleções gerenciadas, proxies) influenciam o desenho das entidades; trocar o mecanismo de persistência obrigaria a mexer no domínio.
- **Alternativa descartada:** separar já, com uma entidade de domínio e uma entidade JPA por agregado e mapeadores entre elas. Seria o desenho mais limpo, mas dobraria as classes e os pontos de erro em mapeamento num prazo de um dia, sem mudar nenhuma regra de negócio.
