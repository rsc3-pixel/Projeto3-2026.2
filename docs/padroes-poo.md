# Padrões de projeto — Rota Vital (U1 de POO)

**Projeto:** Rota Vital — rede de distribuição de hemocomponentes
**Entrega:** Unidade 1 — padrões aplicados no design do sistema
**Repositório:** `rsc3-pixel/Projeto3-2026.2`

Este documento nomeia e explica os padrões de projeto aplicados no código de produção do Rota Vital. Cada padrão é acompanhado da referência direta ao arquivo onde aparece, para que a afirmação possa ser verificada.

---

## 1. Arquitetura em Camadas (Layered Architecture)

**Padrão:** separação de responsabilidades em três camadas distintas — apresentação, negócio e persistência.

| Camada | Pacote | Responsabilidade |
|---|---|---|
| Apresentação | `com.rotavital.api` | Receber HTTP, validar entrada, montar resposta |
| Negócio | `com.rotavital.servico` | Regras de domínio, orquestração, transações |
| Persistência | `com.rotavital.repositorio` | Acesso ao banco de dados |

O fluxo de chamada segue sempre a mesma direção: `Controller → Servico → Repository`. Nenhuma classe de negócio importa um Controller, e nenhum Controller chama diretamente um Repository — a dependência flui em um único sentido.

**Referências:**

- [`api/BolsaController.java`](../src/main/java/com/rotavital/api/BolsaController.java)
- [`servico/BolsaServico.java`](../src/main/java/com/rotavital/servico/BolsaServico.java)
- [`repositorio/BolsaRepository.java`](../src/main/java/com/rotavital/repositorio/BolsaRepository.java)

---

## 2. Repository Pattern (Spring Data JPA)

**Padrão:** encapsula a lógica de acesso a dados atrás de uma interface, desacoplando o domínio de negócio do mecanismo de persistência.

No Rota Vital cada entidade de domínio tem um repositório próprio que estende `JpaRepository<T, ID>`. O Spring gera a implementação em tempo de execução — o código de produção só vê a interface, nunca o SQL diretamente.

```java
// repositorio/BolsaRepository.java
public interface BolsaRepository extends JpaRepository<Bolsa, String> { … }

// repositorio/RotaRepository.java
public interface RotaRepository extends JpaRepository<Rota, UUID> { … }
```

Isso permite trocar o banco de dados (H2 em dev, PostgreSQL em prod) sem alterar uma linha de código de negócio — o `application-prod.properties` já prepara essa migração.

**Referências:**

- [`repositorio/BolsaRepository.java`](../src/main/java/com/rotavital/repositorio/BolsaRepository.java)
- [`repositorio/RotaRepository.java`](../src/main/java/com/rotavital/repositorio/RotaRepository.java)
- [`repositorio/HemocentroRepository.java`](../src/main/java/com/rotavital/repositorio/HemocentroRepository.java)
- [`repositorio/HospitalRepository.java`](../src/main/java/com/rotavital/repositorio/HospitalRepository.java)

---

## 3. Template Method via Classe Abstrata (herança de domínio)

**Padrão:** define a estrutura comum de um conceito de domínio em uma classe abstrata, deixando as subclasses especializarem apenas o que é diferente entre elas.

`Local` é a raiz da hierarquia de unidades da rede. Ela concentra os atributos compartilhados (id, nome, telefone, endereço) e o mapeamento JPA com estratégia `SINGLE_TABLE`. `Hemocentro` e `Hospital` herdam tudo isso e acrescentam apenas os atributos que os diferenciam.

```java
// dominio/Local.java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class Local { … }

// dominio/Hemocentro.java
public class Hemocentro extends Local { … }

// dominio/Hospital.java
public class Hospital extends Local { … }
```

O uso de `SINGLE_TABLE` é uma decisão deliberada: mantém a consulta polimorfica em uma única tabela e elimina JOINs para o caso mais frequente (listar todas as unidades da rede independentemente do tipo).

**Referências:**

- [`dominio/Local.java`](../src/main/java/com/rotavital/dominio/Local.java)
- [`dominio/Hemocentro.java`](../src/main/java/com/rotavital/dominio/Hemocentro.java)
- [`dominio/Hospital.java`](../src/main/java/com/rotavital/dominio/Hospital.java)

---

## 4. State Machine (ciclo de vida da Rota)

**Padrão:** modela o ciclo de vida de um objeto como um conjunto explícito de estados e transições válidas entre eles, rejeitando transições ilegais no próprio domínio.

`RotaServico` define a tabela de transições como um `EnumMap` imutável. Toda chamada ao método `atualizarStatus` consulta esse mapa antes de persistir; se a transição não está registrada, uma exceção é lançada antes de qualquer escrita no banco.

```java
// servico/RotaServico.java — linhas 34–40
private static final Map<StatusRota, Set<StatusRota>> TRANSICOES = new EnumMap<>(StatusRota.class);

static {
    TRANSICOES.put(StatusRota.PLANEJADA,   Set.of(StatusRota.EM_TRANSITO, StatusRota.CANCELADA));
    TRANSICOES.put(StatusRota.EM_TRANSITO, Set.of(StatusRota.CONCLUIDA,   StatusRota.CANCELADA));
    TRANSICOES.put(StatusRota.CONCLUIDA,   Set.of());
    TRANSICOES.put(StatusRota.CANCELADA,   Set.of());
}
```

Os estados `CONCLUIDA` e `CANCELADA` têm conjuntos de transição vazios — são estados finais. O invariante é reforçado em tempo de execução sem necessidade de `if/else` espalhado pelo código.

**Referências:**

- [`servico/RotaServico.java`](../src/main/java/com/rotavital/servico/RotaServico.java) — linhas 34–40 e 117–128
- [`dominio/enums/StatusRota.java`](../src/main/java/com/rotavital/dominio/enums/StatusRota.java)

---

## 5. Dependency Injection por Construtor

**Padrão:** dependências declaradas explicitamente no construtor, sem anotação `@Autowired` em campo — o objeto não pode ser instanciado sem suas dependências.

Todos os serviços do projeto seguem essa convenção. O Spring injeta as dependências automaticamente pelo construtor único. Além de deixar o grafo de dependências visível no código (não implícito por reflexão), esse estilo permite instanciar os serviços em testes unitários sem inicializar o contexto Spring.

```java
// servico/RotaServico.java
public RotaServico(RotaRepository rotas,
                   BolsaRepository bolsas,
                   HemocentroServico hemocentroServico,
                   HospitalServico hospitalServico) {
    this.rotas = rotas;
    …
}
```

**Referências:**

- [`servico/RotaServico.java`](../src/main/java/com/rotavital/servico/RotaServico.java) — construtor
- [`servico/BolsaServico.java`](../src/main/java/com/rotavital/servico/BolsaServico.java) — construtor
- [`servico/IndicadorServico.java`](../src/main/java/com/rotavital/servico/IndicadorServico.java) — construtor

---

## 6. Resumo dos padrões

| Padrão | Onde aparece | Categoria |
|---|---|---|
| Arquitetura em Camadas | Pacotes `api`, `servico`, `repositorio` | Arquitetural |
| Repository | `*Repository extends JpaRepository` | Acesso a dados |
| Template Method / Herança | `Local` → `Hemocentro`, `Hospital` | GoF — Comportamental |
| State Machine | `RotaServico.TRANSICOES` | GoF — Comportamental |
| Dependency Injection por Construtor | Todos os `*Servico` | IoC / Estrutural |
