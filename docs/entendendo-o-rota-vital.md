# Entendendo o Rota Vital

Guia do código do projeto, do modelo de domínio até a aplicação no ar. A ideia
é que você abra este arquivo ao lado do editor e acompanhe: cada seção aponta o
arquivo e explica **por que** está daquele jeito, não só o que faz.

São duas partes:

- **Parte I (seções 1 a 12)** — o CRUD entregue na PI3-18. A ordem é a ordem em
  que uma requisição HTTP atravessa o sistema.
- **Parte II (seções 13 a 23)** — o pipeline e o deploy da PI3-21. A ordem é a
  ordem em que um commit vira URL pública.

---

## Sumário

### Parte I — O CRUD

1. [O caminho de uma requisição](#1-o-caminho-de-uma-requisição)
2. [JPA e Hibernate: quem é quem](#2-jpa-e-hibernate-quem-é-quem)
3. [As entidades](#3-as-entidades)
4. [Herança: por que SINGLE_TABLE](#4-herança-por-que-single_table)
5. [Relacionamentos e o `mappedBy`](#5-relacionamentos-e-o-mappedby)
6. [Repositórios: a mágica do Spring Data](#6-repositórios-a-mágica-do-spring-data)
7. [DTOs: por que não devolver a entidade](#7-dtos-por-que-não-devolver-a-entidade)
8. [Serviços: onde moram as regras](#8-serviços-onde-moram-as-regras)
9. [Controllers: só HTTP](#9-controllers-só-http)
10. [Tratamento de erros](#10-tratamento-de-erros)
11. [Os três bugs que apareceram](#11-os-três-bugs-que-apareceram)
12. [Glossário de anotações](#12-glossário-de-anotações)

### Parte II — Do código à URL no ar

13. [O caminho de um commit até a produção](#13-o-caminho-de-um-commit-até-a-produção)
14. [Por que quatro jobs e não um script](#14-por-que-quatro-jobs-e-não-um-script)
15. [O deploy que não roda sempre](#15-o-deploy-que-não-roda-sempre)
16. [O deploy, passo a passo](#16-o-deploy-passo-a-passo)
17. [systemd: quem mantém a aplicação viva](#17-systemd-quem-mantém-a-aplicação-viva)
18. [O perfil de produção](#18-o-perfil-de-produção)
19. [Nginx: o porteiro](#19-nginx-o-porteiro)
20. [HTTPS com Certbot](#20-https-com-certbot)
21. [Os bugs da Parte II](#21-os-bugs-da-parte-ii)
22. [O que aprender daqui](#22-o-que-aprender-daqui)
23. [Exercícios da Parte II](#23-exercícios-da-parte-ii)

---

# Parte I — O CRUD

Esta parte cobre o código entregue na PI3-18: como o domínio vira tabela, como
uma requisição HTTP atravessa as camadas e onde cada regra de negócio mora.

---

## 1. O caminho de uma requisição

Antes de qualquer detalhe, o mapa. Quando alguém faz:

```
POST /api/v1/bolsas
{"hemocentroId": "HC01", "tipoHemocomponente": "CONCENTRADO_HEMACIAS", ...}
```

o pedido atravessa cinco camadas:

```
        JSON entra
            │
            ▼
┌───────────────────────────┐
│  BolsaController          │  traduz HTTP: lê o corpo, escolhe o status
│  api/BolsaController.java │  de resposta. Nenhuma regra de negócio.
└───────────┬───────────────┘
            │  BolsaRequest (DTO)
            ▼
┌───────────────────────────┐
│  BolsaServico             │  as regras: bolsa pode nascer assim?
│  servico/BolsaServico.java│  o hemocentro existe? a validade fecha?
└───────────┬───────────────┘
            │  Bolsa (entidade)
            ▼
┌───────────────────────────┐
│  BolsaRepository          │  interface sem implementação:
│  repositorio/...java      │  o Spring Data gera o código
└───────────┬───────────────┘
            │
            ▼
┌───────────────────────────┐
│  Hibernate                │  monta o SQL, executa, devolve objetos
└───────────┬───────────────┘
            │  INSERT INTO bolsa ...
            ▼
        Banco (H2)
```

**Por que tantas camadas?** Cada uma tem um motivo prático:

- Se a regra de negócio estivesse no controller, você não conseguiria
  reaproveitá-la fora do HTTP (numa tarefa agendada, por exemplo).
- Se o controller falasse direto com o repositório, qualquer mudança no banco
  vazaria para a API.
- Se a entidade fosse devolvida direto no JSON, o formato da resposta ficaria
  amarrado ao formato da tabela.

A regra prática: **o controller não sabe o que é banco, o serviço não sabe o
que é HTTP.**

---

## 2. JPA e Hibernate: quem é quem

**JPA** é uma especificação. Um documento que define anotações (`@Entity`,
`@Id`, `@OneToMany`) e interfaces. Papel, não código executável.

**Hibernate** é a implementação: o código que lê essas anotações, gera o SQL,
abre conexão e transforma linha de tabela em objeto Java.

> Analogia: JPA é a regra do futebol, Hibernate é o time que joga. A regra
> diz que gol vale ponto; o time é quem chuta.

Você já viu o Hibernate se apresentar. Suba a aplicação e olhe o log:

```
org.hibernate.orm.core : Hibernate ORM core version 7.4.5.Final
Database dialect: H2Dialect
```

Aquele `H2Dialect` importa: cada banco tem SQL ligeiramente diferente, e o
Hibernate carrega um "dialeto" para falar a língua certa. É por isso que
trocar H2 por PostgreSQL na Entrega 02 vai ser quase indolor — muda a URL,
ele carrega outro dialeto, e o mesmo código Java gera SQL de Postgres.

**Onde isso está configurado:** `src/main/resources/application.properties`.

```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

- `ddl-auto=update` faz o Hibernate criar as tabelas a partir das suas
  entidades anotadas. É ótimo para desenvolvimento e **perigoso em produção**
  (uma renomeação de campo pode criar coluna nova em vez de renomear).
- `show-sql=true` imprime as queries no console. **Ligue e olhe** — é a melhor
  forma de entender o que acontece por baixo.

---

## 3. As entidades

Arquivos em `src/main/java/com/rotavital/dominio/`.

Uma entidade é uma classe Java que vira tabela. O exemplo mais simples é o
`Veiculo`:

```java
@Entity
public class Veiculo {

    @Id
    private String id;
    private String placa;
    // ...
}
```

- `@Entity` diz: "isto vira tabela".
- `@Id` marca a chave primária.

Campos sem anotação viram coluna automaticamente. Só o que foge do padrão
precisa ser dito.

### O construtor sem argumentos

Toda entidade tem um construtor assim:

```java
protected Veiculo() {
    // Construtor sem argumentos exigido pelo JPA. Nao usar no codigo.
}
```

**Por que ele existe?** Quando o Hibernate lê uma linha do banco, ele precisa
criar o objeto antes de saber o que colocar dentro. Ele faz isso em dois
passos: primeiro instancia vazio, depois preenche campo a campo por reflexão.
Sem construtor vazio, o primeiro passo é impossível.

**Por que `protected` e não `public`?** Para desencorajar o uso. Um
`new Veiculo()` no seu código criaria um objeto sem id, sem placa, inválido.
`protected` permite que o Hibernate use (ele está no mesmo mecanismo de
reflexão) mas sinaliza para o time: não chame isto.

### Por que os `final` sumiram

O domínio original tinha:

```java
private final String codigoRastreio;   // versão antes do JPA
```

`final` em Java significa: **este campo recebe valor uma vez e nunca mais
muda a referência**. É uma boa prática, porque um objeto imutável não pode ser
corrompido depois de criado.

O problema: o Hibernate cria o objeto vazio e preenche depois. Com `final`, o
campo teria que ser preenchido no construtor, e o construtor vazio não tem
como preencher. Conflito direto.

Então tirei o `final` dos campos que o Hibernate preenche. **Onde o `final`
ficou:** nas coleções, que são inicializadas na própria declaração:

```java
@OneToMany(mappedBy = "hemocentroOrigem")
private final List<Bolsa> estoque = new ArrayList<>();
```

Aqui `final` funciona e protege: ninguém pode trocar a lista inteira por
outra. O conteúdo dela continua mutável (dá para adicionar e remover), que é
exatamente o que se quer.

> **Detalhe que confunde:** `final` trava a *referência*, não o *conteúdo*.
> Uma `final List` não pode apontar para outra lista, mas aceita `add()` e
> `remove()` normalmente. É como um endereço fixo: a casa é sempre a mesma, os
> móveis mudam.

### Encapsulamento das coleções

Repare no getter:

```java
public List<Bolsa> getEstoque() {
    return Collections.unmodifiableList(estoque);
}

public void adicionarBolsa(Bolsa bolsa) {
    estoque.add(bolsa);
}
```

O getter devolve uma **visão só de leitura**. Se alguém tentar
`hemocentro.getEstoque().add(bolsa)`, recebe exceção. A única porta de entrada
é o `adicionarBolsa()`.

**Por que isso importa?** Porque amanhã você pode precisar validar algo antes
de aceitar a bolsa (capacidade máxima, tipo compatível com o hemocentro). Com
o getter aberto, não haveria onde colocar a validação — o código já estaria
espalhado por toda parte chamando `.add()` direto.

### `Endereco` é diferente: `@Embeddable`

```java
@Embeddable
public class Endereco {
    private String logradouro;
    private String cidade;
    // ...
}
```

`Endereco` **não é entidade**. Não tem `@Id`, não tem tabela própria.

A diferença conceitual: um endereço não tem identidade. Dois endereços com os
mesmos dados são o mesmo endereço. Já dois hemocentros com o mesmo nome são
duas instituições diferentes, porque cada um tem seu CNPJ.

Na prática, `@Embeddable` faz as colunas do endereço serem gravadas **dentro
da tabela da unidade**:

```
tabela LOCAL
┌──────┬──────────────┬─────────────┬──────────┬─────────┐
│ id   │ nome         │ logradouro  │ cidade   │ cep     │
├──────┼──────────────┼─────────────┼──────────┼─────────┤
│ HC01 │ HEMOPE       │ R. Joaquim… │ Recife   │ 52011…  │
└──────┴──────────────┴─────────────┴──────────┴─────────┘
       └── de Local ──┘└──── de Endereco (embutido) ─────┘
```

Sem tabela separada, sem chave estrangeira, sem `JOIN` para buscar o endereço.
Do lado do Java continuam sendo duas classes; do lado do banco, uma tabela só.

No `Local`, o campo é marcado com `@Embedded`:

```java
@Embedded
private Endereco endereco;
```

---

## 4. Herança: por que SINGLE_TABLE

Arquivo: `dominio/Local.java`.

O domínio tem `Local` como classe abstrata, e `Hemocentro` e `Hospital`
herdando dela. Bancos relacionais não têm herança, então o JPA oferece três
estratégias de tradução. Escolhi uma:

```java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class Local {
```

### As três opções

**`SINGLE_TABLE` (escolhida):** uma tabela só, com todas as colunas das duas
subclasses. Uma coluna extra (`DTYPE`) diz qual é qual.

```
tabela LOCAL
┌──────┬─────────────┬──────────────┬────────────┬─────────┐
│ id   │ DTYPE       │ nome         │ cnpj       │ cnes    │
├──────┼─────────────┼──────────────┼────────────┼─────────┤
│ HC01 │ Hemocentro  │ HEMOPE       │ 11222333…  │ (null)  │
│ HO04 │ Hospital    │ Real Port.   │ (null)     │ 2400001 │
└──────┴─────────────┴──────────────┴────────────┴─────────┘
```

Vantagem: consulta rápida, sem `JOIN`. Desvantagem: colunas nulas — o
hemocentro nunca preenche `cnes`, o hospital nunca preenche `cnpj`.

**`JOINED`:** uma tabela para `Local` e uma para cada subclasse, ligadas por
chave estrangeira. Sem colunas nulas, mas toda consulta precisa de `JOIN`.

**`TABLE_PER_CLASS`:** uma tabela completa por subclasse, sem tabela de base.
Rápido para buscar um tipo específico, péssimo para buscar "todos os locais".

### Por que SINGLE_TABLE aqui

Três razões, na ordem em que pesaram:

1. **As subclasses têm quase os mesmos campos.** `Hemocentro` acrescenta
   `cnpj`, `Hospital` acrescenta `cnes`. Duas colunas nulas é um custo
   pequeno. Se a diferença fosse de 15 campos cada, `JOINED` seria melhor.

2. **O grafo de distribuição trata todos como nós.** O `Grafo` (PI3-17) não
   distingue hemocentro de hospital: são vértices. Consultar todos os locais
   é operação frequente, e em `SINGLE_TABLE` é um `SELECT` simples.

3. **É a estratégia padrão do JPA.** Quando não há motivo forte para
   divergir, seguir o padrão facilita a vida de quem lê depois.

> **Se o professor perguntar:** o argumento contra é normalização. Uma coluna
> que só se aplica a metade das linhas é, tecnicamente, um cheiro de má
> modelagem. A defesa é pragmática: são duas colunas, o ganho de performance é
> real, e o banco é de um projeto acadêmico com dados sintéticos.

---

## 5. Relacionamentos e o `mappedBy`

Este é o ponto que mais gera confusão em JPA, então vale devagar.

### O problema

No mundo Java, um relacionamento tem dois lados:

```java
// Em Hemocentro
private List<Bolsa> estoque;      // "tenho muitas bolsas"

// Em Bolsa
private Hemocentro hemocentroOrigem;   // "pertenço a um hemocentro"
```

No mundo do banco, existe **uma coluna só**: `bolsa.hemocentro_origem_id`.

Se o Hibernate tratasse os dois lados como independentes, ele tentaria gravar
a mesma informação duas vezes, e poderia criar uma tabela de junção
desnecessária.

### A solução: um lado é dono

```java
// Bolsa.java — o lado DONO
@ManyToOne
@JoinColumn(name = "hemocentro_origem_id")
private Hemocentro hemocentroOrigem;

// Hemocentro.java — o lado INVERSO
@OneToMany(mappedBy = "hemocentroOrigem")
private final List<Bolsa> estoque = new ArrayList<>();
```

`mappedBy = "hemocentroOrigem"` diz: *"eu não controlo esta relação. O
controle está no campo chamado `hemocentroOrigem`, lá na classe `Bolsa`."*

**Como decidir quem é o dono?** Quem tem a chave estrangeira. Aqui a coluna
está na tabela `bolsa`, então `Bolsa` é o dono. Regra prática: em
`@OneToMany`/`@ManyToOne`, o lado `@ManyToOne` é sempre o dono.

### A armadilha do lado inverso

Isto **não** grava nada:

```java
hemocentro.adicionarBolsa(bolsa);   // só mexe na lista em memória
repositorio.save(hemocentro);       // o Hibernate ignora, não é o dono
```

O que grava de verdade é mexer no lado dono. Por isso, em
`Requisicao.adicionarItem()`, eu amarro os dois lados:

```java
public void adicionarItem(ItemRequisicao item) {
    itens.add(item);              // lado inverso: a lista em memória
    item.setRequisicao(this);     // lado dono: a chave estrangeira
}
```

Sem a segunda linha, o item seria gravado com `requisicao_id` nulo e ficaria
órfão. **Este é o erro mais comum de quem começa em JPA.**

### `cascade` e `orphanRemoval`

```java
@OneToMany(mappedBy = "requisicao", cascade = CascadeType.ALL, orphanRemoval = true)
private final List<ItemRequisicao> itens = new ArrayList<>();
```

- **`cascade = ALL`**: o que acontece com a requisição acontece com os itens.
  Salvou a requisição, salva os itens junto. Apagou, apaga os itens.
- **`orphanRemoval = true`**: item removido da lista é apagado do banco, não
  fica solto.

**Por que aqui e não em `Hemocentro`/`Bolsa`?** Porque um item de requisição
não existe sem a requisição — é parte dela. Já uma bolsa existe por si: apagar
o hemocentro **não** deve apagar o estoque, deve ser impedido. E é exatamente
o que o `HemocentroServico.remover()` faz.

### O caso especial da Rota

```java
@ManyToMany
@JoinTable(
        name = "rota_bolsa",
        joinColumns = @JoinColumn(name = "rota_id"),
        inverseJoinColumns = @JoinColumn(name = "bolsa_codigo_rastreio"))
private final List<Bolsa> bolsas = new ArrayList<>();
```

Aqui usei tabela de junção própria. **Por quê?** A `Bolsa` já tem um
`@ManyToOne` para o hemocentro de origem. Se a rota também gravasse na tabela
`bolsa`, seriam duas relações disputando o mesmo espaço. Uma tabela separada
(`rota_bolsa`, com duas colunas) resolve sem ambiguidade.

---

## 6. Repositórios: a mágica do Spring Data

Arquivos em `repositorio/`. Olhe o `HemocentroRepository`:

```java
public interface HemocentroRepository extends JpaRepository<Hemocentro, String> {

    List<Hemocentro> findByEnderecoCidadeIgnoreCase(String cidade);

    boolean existsByCnpj(String cnpj);
}
```

**É uma interface. Não existe classe implementando ela.** Nenhum arquivo
`HemocentroRepositoryImpl` foi escrito.

O Spring Data lê o nome dos métodos, entende o que você quer e gera a
implementação em tempo de execução. Você escreveu a *intenção*, ele escreveu
o SQL.

### Como o nome vira consulta

```
findByEnderecoCidadeIgnoreCase
│   │  │        │      └─ ignorando maiúscula/minúscula
│   │  │        └─ ...o campo cidade
│   │  └─ dentro do campo endereco...
│   └─ filtrando por
└─ me traga
```

Vira, mais ou menos:

```sql
SELECT * FROM local WHERE UPPER(cidade) = UPPER(?)
```

Repare que `EnderecoCidade` funciona porque `Endereco` é `@Embeddable`: as
colunas estão na própria tabela.

`JpaRepository` já traz de graça: `save`, `findById`, `findAll`, `delete`,
`count`, `existsById`. Você só declara o que foge disso.

**Vale testar:** ligue `spring.jpa.show-sql=true`, chame
`GET /api/v1/hemocentros?cidade=Recife` e veja o SQL aparecer no console.

### Por que os repositórios são tão pequenos

Na primeira versão eu tinha escrito uns 15 métodos de consulta. Depois
verifiquei quais eram realmente chamados e **apaguei 12**. Sobrou o que se
usa.

Método de repositório não usado não é "preparação para o futuro", é código
morto: mais uma coisa para ler, manter e explicar, sem nenhum ganho. Se
precisar depois, escreve-se em 30 segundos.

---

## 7. DTOs: por que não devolver a entidade

Arquivos em `api/dto/`.

DTO = *Data Transfer Object*. É a forma dos dados na fronteira da API, que não
precisa ser igual à forma no banco.

```java
public record BolsaRequest(
        @NotNull(message = "hemocentroId e obrigatorio")
        String hemocentroId,

        @Positive(message = "volumeMl deve ser maior que zero")
        int volumeMl,

        @PastOrPresent(message = "dataColeta nao pode ser futura")
        LocalDate dataColeta) {
}
```

### Três motivos para não devolver a entidade direto

**1. Segurança.** A entidade tem tudo. Se o cliente pudesse mandar qualquer
campo, poderia mandar `{"status": "ENTREGUE"}` numa bolsa recém-criada e
pular todo o fluxo. Por isso `BolsaRequest` **não tem** `status` nem
`dataValidade` — esses o servidor calcula:

```java
// Em Bolsa.java, no construtor:
this.dataValidade = dataColeta.plusDays(tipo.getValidadeDias());
this.status = StatusBolsa.DISPONIVEL;
```

O cliente não escolhe a validade. Ela vem do tipo de hemocomponente, conforme
a regra do domínio (plaquetas 5 dias, hemácias 42, plasma 365).

**2. Laço infinito.** `Hemocentro` tem lista de `Bolsa`, e cada `Bolsa` tem
referência ao `Hemocentro`. Se o Jackson serializasse a entidade direto, ele
entraria em recursão até estourar a memória.

**3. Desacoplamento.** Renomear uma coluna no banco não pode quebrar o
contrato com o front-end. Com DTO, você ajusta o mapeamento e a API continua
igual.

### Por que `record` e não classe

```java
public record EnderecoDto(String logradouro, String cidade, ...) { }
```

Um `record` gera automaticamente: construtor, getters, `equals`, `hashCode` e
`toString`. Como um DTO só carrega dados e não tem comportamento, é o formato
ideal — e são imutáveis por natureza.

A mesma coisa como classe teria umas 60 linhas de código repetitivo.

### O mapeamento

Cada `*Response` tem um método estático `de(...)`:

```java
public static BolsaResponse de(Bolsa bolsa) {
    return new BolsaResponse(
            bolsa.getCodigoRastreio(),
            bolsa.getHemocentroOrigem() == null ? null : bolsa.getHemocentroOrigem().getId(),
            // ...
    );
}
```

Repare no `== null ? null :`. Sem isso, uma bolsa sem hemocentro (possível em
teste) causaria `NullPointerException` na serialização.

---

## 8. Serviços: onde moram as regras

Arquivos em `servico/`. É a camada mais importante do que foi entregue, e a
que você mais vai precisar defender numa apresentação.

### Injeção por construtor

```java
@Service
public class HemocentroServico {

    private final HemocentroRepository hemocentros;
    private final BolsaRepository bolsas;

    public HemocentroServico(HemocentroRepository hemocentros, BolsaRepository bolsas) {
        this.hemocentros = hemocentros;
        this.bolsas = bolsas;
    }
```

`@Service` diz ao Spring: "crie uma instância disto e guarde". Quando ele cria,
vê que o construtor pede dois repositórios e passa os dois automaticamente.
Isso é **injeção de dependência**.

**Por que isso é melhor que `new HemocentroRepository()`?** Porque a classe
não precisa saber *como* construir suas dependências. Em teste, você pode
passar uma implementação falsa. E os campos podem ser `final`, porque só o
construtor os preenche.

### `@Transactional`

```java
@Transactional
public Alocacao alocar(String requisicaoId, String itemId, AlocacaoRequest dados) {
```

Uma transação é um bloco tudo-ou-nada. A alocação muda três coisas: cria a
`Alocacao`, incrementa o contador do item e muda o status da bolsa para
`RESERVADA`. Se o método falhar no meio, **nada** é gravado.

Sem transação, você poderia ter uma bolsa marcada como reservada sem alocação
correspondente — estoque fantasma.

`@Transactional(readOnly = true)` nas consultas: avisa o Hibernate que nada
vai mudar, e ele otimiza (não precisa rastrear alterações).

### A máquina de estados

`BolsaServico.java`, linhas iniciais:

```java
private static final Map<StatusBolsa, Set<StatusBolsa>> TRANSICOES = new EnumMap<>(StatusBolsa.class);

static {
    TRANSICOES.put(StatusBolsa.DISPONIVEL, Set.of(StatusBolsa.RESERVADA, StatusBolsa.DESCARTADA));
    TRANSICOES.put(StatusBolsa.RESERVADA, Set.of(StatusBolsa.DISPONIVEL, StatusBolsa.EM_TRANSITO, StatusBolsa.DESCARTADA));
    TRANSICOES.put(StatusBolsa.EM_TRANSITO, Set.of(StatusBolsa.ENTREGUE, StatusBolsa.DESCARTADA));
    TRANSICOES.put(StatusBolsa.ENTREGUE, Set.of());
    TRANSICOES.put(StatusBolsa.DESCARTADA, Set.of());
}
```

Lendo o mapa como diagrama:

```
   DISPONIVEL ──────► RESERVADA ──────► EM_TRANSITO ──────► ENTREGUE ✗
       │   ▲              │                   │
       │   └──────────────┘                   │
       │                                      │
       └──────────► DESCARTADA ✗ ◄────────────┘
                    (também a partir de RESERVADA)

   ✗ = estado final, não sai mais
```

**Por que um mapa e não uma sequência de `if`?** Porque a regra fica visível
num lugar só. Para saber o que pode acontecer com uma bolsa entregue, você lê
uma linha: `Set.of()` — nada. Com `if` espalhado, teria que caçar pelo código.

`EnumMap` em vez de `HashMap`: é otimizado para chaves enum, usa um array
interno indexado pela posição da constante.

### As regras de compatibilidade

`RequisicaoServico.alocar()` é o coração do sistema. É onde estoque e demanda
se encontram:

```java
if (bolsa.getStatus() != StatusBolsa.DISPONIVEL) { ... }
if (bolsa.estaVencida(LocalDate.now())) { ... }
if (bolsa.getTipo() != item.getTipo()) { ... }
if (bolsa.getGrupoSanguineo() != item.getGrupoSanguineo()) { ... }
```

Quatro guardas, na ordem do mais barato para o mais específico. Todas lançam
`OperacaoInvalidaException`, que vira HTTP 409.

> **Limitação consciente:** a compatibilidade aqui é por igualdade exata.
> Na prática transfusional, O− é doador universal e AB+ é receptor universal.
> Implementar isso é outra story, e o README já registra que a compatibilidade
> é didática.

### Status derivado, não informado

```java
public void recalcularStatus() {
    if (status == StatusRequisicao.CANCELADA) {
        return;
    }
    if (estaCompleta()) {
        status = StatusRequisicao.ATENDIDA;
    } else if (itens.stream().anyMatch(i -> i.getQuantidadeAlocada() > 0)) {
        status = StatusRequisicao.PARCIALMENTE_ATENDIDA;
    } else {
        status = StatusRequisicao.PENDENTE;
    }
}
```

O cliente **não escolhe** o status da requisição. Ele é calculado a partir das
alocações, e recalculado sempre que uma alocação é criada ou desfeita.

Por isso o `PATCH /api/v1/requisicoes/{id}` só aceita `CANCELADA`. Qualquer
outro valor é recusado com 409 e uma mensagem explicando.

**Por quê?** Se o cliente pudesse mandar `{"status": "ATENDIDA"}` numa
requisição sem nenhuma bolsa alocada, o sistema mentiria. O estado tem que ser
consequência dos fatos, não uma declaração.

---

## 9. Controllers: só HTTP

Arquivos em `api/`. Compare o tamanho: `HemocentroController` tem 109 linhas,
`RequisicaoServico` tem 269. Isso é proposital.

```java
@PostMapping
public ResponseEntity<HemocentroResponse> criar(@Valid @RequestBody HemocentroRequest dados) {
    Hemocentro criado = hemocentros.criar(dados);
    return ResponseEntity
            .created(URI.create("/api/v1/hemocentros/" + criado.getId()))
            .body(HemocentroResponse.de(criado));
}
```

O método faz três coisas, todas de protocolo:

1. `@Valid` dispara a validação das anotações do DTO
2. Chama o serviço
3. Monta a resposta 201 com o cabeçalho `Location`

**Nenhuma decisão de negócio.** Se você precisar dessa lógica fora do HTTP,
chama o serviço direto.

### `ResponseEntity.created()`

Devolve 201 e o cabeçalho `Location` apontando para o recurso criado. É
convenção REST: o cliente descobre onde o novo recurso mora sem precisar
adivinhar.

### Por que `PATCH` e não `PUT` nas bolsas

- `PUT` substitui o recurso inteiro
- `PATCH` altera parte dele

Uma bolsa não se edita por inteiro: o código de rastreio, o tipo e a data de
coleta são imutáveis por natureza. O que muda ao longo da vida dela é o
status. Por isso `PATCH /api/v1/bolsas/{id}` com `{"status": "..."}`, e
nenhum `PUT`.

Já hemocentro e hospital têm `PUT`, porque faz sentido reescrever os dados
cadastrais inteiros.

### Sub-recursos aninhados

```
/api/v1/requisicoes/{id}/itens/{itemId}/alocacoes
```

Um item de requisição não existe fora de uma requisição. Uma alocação não
existe fora de um item. A URL reflete essa hierarquia, em vez de ter
`/api/v1/itens` solto.

---

## 10. Tratamento de erros

Arquivo: `api/ManipuladorDeErros.java`.

```java
@RestControllerAdvice
public class ManipuladorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, e.getMessage());
        problema.setTitle("Recurso nao encontrado");
        return problema;
    }
```

`@RestControllerAdvice` é um interceptador global: qualquer exceção que suba
de qualquer controller passa por aqui.

**Sem isso**, cada método de cada controller precisaria de `try/catch`. Com
isso, o serviço só lança a exceção e alguém lá em cima traduz.

### O mapeamento

| Exceção | HTTP | Quando |
|---|---|---|
| `RecursoNaoEncontradoException` | 404 | id não existe |
| `OperacaoInvalidaException` | 409 | existe, mas o estado não permite |
| `MethodArgumentNotValidException` | 400 | corpo malformado |

A distinção entre 404 e 409 importa: 404 diz "não achei", 409 diz "achei, mas
não pode". São problemas diferentes e o cliente reage diferente.

### `ProblemDetail` (RFC 7807)

Formato padronizado de erro de API. A resposta sai assim:

```json
{
  "status": 409,
  "title": "Operacao nao permitida",
  "detail": "Transicao invalida: ENTREGUE -> DESCARTADA",
  "instance": "/api/v1/bolsas/fc2830af-..."
}
```

Para erro de validação, adicionei um campo com o detalhe por campo:

```json
{
  "status": 400,
  "title": "Dados invalidos",
  "detail": "Ha campos invalidos na requisicao",
  "campos": {
    "nome": "nome e obrigatorio",
    "endereco.uf": "uf deve ter 2 letras"
  }
}
```

O front-end consegue marcar exatamente o campo errado no formulário.

---

## 11. Os três bugs que apareceram

Esta seção existe porque errar e consertar ensina mais que acertar de primeira.

### Bug 1: o Jackson e o `toString()`

**Sintoma:** `POST /api/v1/bolsas` devolvia 400 sem explicação útil.

**Log:**

```
Cannot deserialize value of type TipoHemocomponente from String
"CONCENTRADO_HEMACIAS": not one of the values accepted for Enum class:
[Plasma Fresco Congelado, Crioprecipitado, Concentrado de Plaquetas, ...]
```

**Causa:** os enums do domínio sobrescrevem `toString()`:

```java
@Override
public String toString() {
    return descricao;    // "Concentrado de Hemacias"
}
```

O Jackson usa `toString()` para serializar enum. Então a API esperava
`"Concentrado de Hemacias"`, mas o contrato pede `CONCENTRADO_HEMACIAS`.

**Correção:** anotar um método com `@JsonValue`, que tem prioridade sobre o
`toString()`:

```java
@JsonValue
public String comoJson() {
    return name();
}
```

**Por que não simplesmente apagar o `toString()`?** Porque ele é dos colegas e
serve para exibição — `Bolsa.toString()` usa `getDescricao()` para log legível.
Apagar resolveria o meu problema criando outro. `@JsonValue` separa as duas
responsabilidades: `toString()` para humano, `@JsonValue` para máquina.

**A lição:** o Jackson usa `toString()` em enum. Se você sobrescreve
`toString()` num enum que trafega em JSON, ou anota `@JsonValue`, ou o
contrato quebra.

### Bug 2: uma regra de negócio minha estava errada

**Sintoma:** o teste `cancelamentoLiberaBolsa` falhou com `expected:<200> but
was:<409>`.

**O que eu tinha escrito:**

```java
if (requisicao.getStatus() == StatusRequisicao.ATENDIDA) {
    throw new OperacaoInvalidaException("Requisicao ja atendida nao pode ser cancelada");
}
```

Parecia razoável. Mas o teste criava uma requisição de **1 bolsa**, alocava
essa bolsa (o que deixa a requisição `ATENDIDA`) e tentava cancelar.

**O raciocínio errado:** confundi "atendida" com "entregue". `ATENDIDA` só
significa que todas as bolsas foram **reservadas**. Elas ainda estão no
hemocentro. Uma reserva se desfaz sem problema nenhum.

**A regra certa** não olha o status da requisição, olha o das bolsas:

```java
boolean bolsaJaSaiu = requisicao.getItens().stream()
        .flatMap(item -> item.getAlocacoes().stream())
        .anyMatch(a -> a.getBolsa().getStatus() != StatusBolsa.RESERVADA);

if (bolsaJaSaiu) {
    throw new OperacaoInvalidaException(
            "Ha bolsas ja despachadas para esta requisicao. Cancele a rota antes.");
}
```

Enquanto tudo está `RESERVADA`, cancela e devolve ao estoque. Se alguma já
está `EM_TRANSITO` ou `ENTREGUE`, aí não — seria fingir que a entrega não
aconteceu.

**A lição:** o teste não pegou um erro de digitação, pegou um erro de
raciocínio sobre o domínio. É exatamente para isso que teste de integração
serve. Um teste unitário com mock provavelmente teria passado, porque eu
mesmo teria montado o mock com a suposição errada.

### Bug 3: o que 46 testes verdes não pegaram

Este apareceu depois, na PI3-21, e é o mais interessante dos três.

**Sintoma:** `GET /api/v1/requisicoes` respondia **HTTP 500 em produção** e
funcionava normalmente em desenvolvimento. Todos os testes passando.

**Log:**

```
org.hibernate.LazyInitializationException: Cannot lazily initialize
collection of role 'com.rotavital.dominio.Requisicao.itens'
with key 'REQ00001' (no session)
```

**A causa.** Os controllers convertiam entidade em DTO:

```java
// RequisicaoController — como estava
return requisicoes.listar(status, hospitalId, prioridade).stream()
        .map(RequisicaoResponse::de)     // <- aqui
        .toList();
```

O `@Transactional` do serviço termina quando o método do serviço retorna. O
`.map()` roda **depois disso**, já no controller. E o `RequisicaoResponse.de()`
percorre `requisicao.getItens()`, que é uma coleção carregada por demanda:
o Hibernate só vai ao banco buscá-la quando alguém a acessa.

Nesse instante a sessão já fechou. Não há como buscar. Exceção.

**Por que só em produção?** Por causa de uma configuração chamada
`spring.jpa.open-in-view`, que vem **ligada por padrão**. Ela mantém a sessão
do banco aberta até a resposta HTTP terminar de ser escrita, o que faz o
acesso tardio funcionar — disparando uma consulta invisível, fora de qualquer
transação.

No perfil de produção eu desliguei:

```properties
spring.jpa.open-in-view=false
```

E o bug, que sempre esteve lá, apareceu.

**A correção:** converter para DTO **dentro** do serviço, com a transação
ainda aberta.

```java
// RequisicaoServico — como ficou
@Transactional(readOnly = true)
public List<RequisicaoResponse> listar(...) {
    return requisicoes.findAll().stream()
            .filter(...)
            .map(RequisicaoResponse::de)   // agora dentro da transação
            .toList();
}
```

O controller passou a só repassar o que recebe.

**Três lições, e a terceira é a que interessa:**

1. **`open-in-view` ligado esconde problema.** Ele é conveniente e por isso é
   o padrão, mas transforma erro de arquitetura em consulta silenciosa. Muita
   gente descobre isso quando o sistema fica lento e ninguém sabe por quê.

2. **A conversão para DTO pertence ao serviço.** Ela toca as entidades, e
   tocar entidade fora da transação é território de erro. O controller deve
   receber DTO pronto.

3. **Teste verde não é prova de que está certo.** Os 46 testes passavam porque
   `@Transactional` na classe de teste mantém uma transação aberta durante o
   método inteiro, incluindo a serialização. O ambiente de teste era mais
   permissivo que o de produção, e o bug morava justamente nessa diferença.

   O que pegou o erro não foi teste nenhum: foi rodar o jar de verdade, com o
   perfil de verdade, e clicar nos endpoints. É por isso que a PI3-21 termina
   com o workflow chamando `/actuator/health` e `/h2-console` na URL pública,
   em vez de confiar que o build verde basta.

---

## 12. Glossário de anotações

Referência rápida do que apareceu no código.

### JPA (persistência)

| Anotação | O que faz |
|---|---|
| `@Entity` | a classe vira tabela |
| `@Id` | marca a chave primária |
| `@Embeddable` | classe cujas colunas são embutidas em outra tabela |
| `@Embedded` | o campo é uma classe `@Embeddable` |
| `@Inheritance(strategy = ...)` | como traduzir herança para tabelas |
| `@Enumerated(EnumType.STRING)` | grava o enum como texto, não como número |
| `@OneToMany(mappedBy = "x")` | lado inverso: o dono é o campo `x` da outra classe |
| `@ManyToOne` | lado dono, tem a chave estrangeira |
| `@OneToOne` | um para um |
| `@ManyToMany` + `@JoinTable` | muitos para muitos, com tabela de junção |
| `@JoinColumn(name = "...")` | nome da coluna de chave estrangeira |
| `cascade = CascadeType.ALL` | operações se propagam para os filhos |
| `orphanRemoval = true` | filho removido da lista é apagado do banco |

### Spring (estrutura)

| Anotação | O que faz |
|---|---|
| `@Service` | componente de regra de negócio, gerenciado pelo Spring |
| `@Component` | componente genérico gerenciado pelo Spring |
| `@Transactional` | o método é tudo-ou-nada |
| `@Transactional(readOnly = true)` | só leitura, permite otimização |
| `@Profile("!test")` | só ativo fora do perfil de teste |

### Spring Web (HTTP)

| Anotação | O que faz |
|---|---|
| `@RestController` | classe que responde HTTP devolvendo JSON |
| `@RequestMapping("/caminho")` | prefixo de URL da classe |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping` | método HTTP |
| `@PathVariable` | pega valor da URL (`/bolsas/{id}`) |
| `@RequestParam` | pega valor da query string (`?cidade=Recife`) |
| `@RequestBody` | converte o JSON do corpo em objeto |
| `@Valid` | dispara a validação das anotações do DTO |
| `@RestControllerAdvice` | interceptador global de exceções |
| `@ExceptionHandler(X.class)` | trata a exceção X |

### Validação (Jakarta Bean Validation)

| Anotação | O que valida |
|---|---|
| `@NotNull` | não pode ser nulo |
| `@NotBlank` | texto não nulo e não vazio |
| `@NotEmpty` | coleção com pelo menos um item |
| `@Positive` | número maior que zero |
| `@Size(min, max)` | tamanho do texto ou coleção |
| `@PastOrPresent` | data não pode ser futura |

### Jackson (JSON)

| Anotação | O que faz |
|---|---|
| `@JsonValue` | o método marcado define como o objeto vira JSON |

---

### Exercícios da Parte I

Três exercícios que ensinam mais que ler:

**1. Ver o SQL.** Suba a aplicação com `show-sql=true` (já está ligado), faça
`GET /api/v1/hemocentros?cidade=Recife` e leia o `SELECT` que o Hibernate
gerou a partir do nome do método. É o momento em que a ficha costuma cair.

**2. Quebrar de propósito.** Em `Requisicao.adicionarItem()`, comente a linha
`item.setRequisicao(this)` e suba a aplicação. Depois olhe a coluna
`requisicao_id` no H2 console:

```sql
SELECT id, requisicao_id FROM ITEM_REQUISICAO;
```

Ela vem nula. O item foi gravado (o `cascade = ALL` cuidou disso), mas sem o
vínculo — órfão no banco.

> **Curiosidade que eu descobri testando:** os testes de integração continuam
> passando com a linha comentada. Motivo: dentro de uma transação, o Hibernate
> mantém os objetos em memória e responde as consultas dali, sem ir ao banco.
> O problema só aparece na próxima transação, quando ele tenta reconstruir o
> objeto a partir das colunas. É por isso que olhar a tabela ensina mais que
> olhar o teste, nesse caso específico.

**3. Olhar a tabela.** Abra `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:rotavital`, usuário `sa`, senha vazia) e rode
`SELECT * FROM LOCAL`. Você vai ver a coluna `DTYPE` e as colunas nulas da
estratégia `SINGLE_TABLE`, exatamente como no diagrama da seção 4.

---

### Onde cada coisa está (Parte I)

```
src/main/java/com/rotavital/
├── api/                    controllers + manipulador de erros
│   └── dto/                DTOs de entrada e saída
├── config/
│   └── CargaInicial.java   dados sintéticos ao subir
├── dominio/                entidades JPA
│   └── enums/
├── estruturas/             grafo, FEFO, índice (PI3-17)
├── repositorio/            interfaces JpaRepository
└── servico/                regras de negócio
    └── excecao/

src/test/java/com/rotavital/
├── api/                    testes de integração (MockMvc)
└── estruturas/             testes unitários das estruturas
```

---

# Parte II — Do código à URL no ar

A primeira parte terminou com a aplicação rodando na sua máquina. Esta parte
cobre o caminho até `https://rsc3-rotavital.duckdns.org`: o que precisa
existir num servidor, como o código chega lá sozinho, e o que muda quando a
aplicação passa a ser alcançável pela internet.

> **A diferença entre as duas partes, numa analogia.** Na Parte I você
> cozinhou: escolheu ingredientes, seguiu a receita, provou o resultado na
> própria cozinha. Na Parte II você abre um restaurante.
>
> São problemas diferentes. Cozinhar bem não basta: é preciso alguém que
> receba os clientes na porta (o Nginx), alguém que reabra a cozinha se o fogão
> apagar de madrugada (o systemd), um endereço que as pessoas encontrem (o
> DNS), uma placa dizendo que o lugar é confiável (o certificado HTTPS), e um
> processo que leve o prato novo do seu caderno de receitas até a cozinha sem
> você ter que ir lá pessoalmente (o pipeline).
>
> Cada seção desta parte é uma dessas peças.

---

## 13. O caminho de um commit até a produção

O mapa desta parte, como o da seção 1 foi para a requisição HTTP:

```
   você dá push na main
            │
            ▼
   ┌─────────────────┐
   │ GitHub Actions  │  máquina temporária do GitHub
   │                 │
   │  Build          │  compila
   │  Testes         │  48 testes
   │  Empacotamento  │  gera o rota-vital.jar
   │  Deploy         │  envia e reinicia
   └────────┬────────┘
            │  scp, pela porta 22
            ▼
   ┌─────────────────────────────────────┐
   │  VM no Google Cloud                 │
   │                                     │
   │  /tmp/rota-vital-novo.jar           │
   │            ↓ publicar.sh            │
   │  /opt/rota-vital/rota-vital.jar     │
   │            ↓ systemctl restart      │
   │  JVM escutando 127.0.0.1:8081       │
   │            ↓                        │
   │  Nginx nas portas 80 e 443          │
   └────────────────┬────────────────────┘
                    │  HTTPS
                    ▼
     https://rsc3-rotavital.duckdns.org
```

Cinco peças novas em relação à Parte I: **GitHub Actions**, **systemd**,
**Nginx**, **Certbot** e o **perfil de produção**. Cada uma resolve um
problema específico, e as próximas seções explicam qual.

---

## 14. Por que quatro jobs e não um script

Arquivo: `.github/workflows/ci.yml`.

Um pipeline poderia ser um script só: compila, testa, empacota, envia. O
workflow separa em quatro **jobs** encadeados:

```yaml
build:      # sem needs, começa primeiro
teste:
  needs: build
empacotar:
  needs: teste
deploy:
  needs: empacotar
```

`needs` cria a corrente: cada job só começa se o anterior terminou bem. É o
que cumpre o critério "falha em qualquer etapa impede o deploy".

> **Analogia: a linha de produção.** Imagine uma fábrica com quatro estações.
> A peça só passa para a estação seguinte se a anterior aprovou. Se a inspeção
> de qualidade reprova, a peça não segue para o caminhão de entrega — ela para
> ali, e o problema fica visível na estação exata onde aconteceu.
>
> O contrário seria uma bancada só, onde um operário faz tudo. Quando o
> produto sai errado, você não sabe em que momento errou.

Vale ver como isso aparece na prática. Uma execução real do pipeline:

```
✓ Build           18s
✓ Testes          35s
✓ Empacotamento   1m25s
✓ Deploy na VM    54s
                  ─────
                  3m27s no total
```

Se os testes falhassem, a tela mostraria:

```
✓ Build           18s
✗ Testes          22s     ← parou aqui
⊘ Empacotamento          (nem começou)
⊘ Deploy na VM           (nem começou)
```

Os dois últimos aparecem com um traço, não com X. Eles não falharam: nunca
foram executados, porque a corrente se rompeu antes.

### Três motivos para separar

**1. Falhar rápido é barato.** Erro de compilação aparece em 18 segundos, sem
esperar os 35 segundos da suite de testes. Num projeto maior a diferença vira
minutos.

**2. A aba Actions fica legível.** Você vê o quadrado vermelho e sabe onde
quebrou, sem abrir log.

**3. O jar publicado é o mesmo que passou nos testes.** Este é o motivo
técnico mais forte, e merece explicação.

### O artefato: por que não recompilar no deploy

O job de empacotamento faz isto:

```yaml
- name: Gerar o jar
  run: ./mvnw --batch-mode package -DskipTests

- name: Guardar o jar
  uses: actions/upload-artifact@v4
  with:
    name: aplicacao
    path: target/rota-vital.jar
```

E o deploy **baixa** esse arquivo em vez de compilar de novo:

```yaml
- name: Baixar o jar gerado
  uses: actions/download-artifact@v4
  with:
    name: aplicacao
```

**Por que isso importa?** Aqui há um detalhe do GitHub Actions que não é
óbvio: **cada job roda numa máquina virtual diferente, criada do zero e
destruída no fim**. O job de testes não vê os arquivos do job de build. É como
se cada etapa acontecesse num computador recém-formatado.

Isso significa que, sem o artefato, o job de deploy teria que clonar o
repositório e compilar de novo.

> **Analogia: a prova do bolo.** Você assa um bolo, prova um pedaço e está
> ótimo. Na hora de entregar ao cliente, em vez de mandar **aquele** bolo,
> você assa outro seguindo a mesma receita.
>
> Quase sempre vai dar igual. Mas o forno pode estar mais quente, o
> ingrediente pode ser de outro lote, e você entregou um bolo que **ninguém
> provou**.

A diferença raramente aparece. Quando aparece, é o pior tipo de bug: "passou
nos testes mas quebrou em produção", sem ninguém entender por quê, porque o
que quebrou não é o que foi testado.

Com o artefato, o binário testado e o binário publicado são **bit a bit o
mesmo arquivo**. O job de empacotamento guarda o bolo numa caixa
(`upload-artifact`), e o de deploy pega aquela caixa (`download-artifact`).

### O `-DskipTests` no empacotamento

```yaml
run: ./mvnw --batch-mode package -DskipTests
```

Parece errado pular testes, mas o job anterior já rodou a suite inteira.
Repetir aqui dobraria o tempo do pipeline sem descobrir nada novo.

### `concurrency`: evitando dois deploys ao mesmo tempo

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

Dois pushes seguidos disparam duas execuções. Sem isto, ambas tentariam
publicar, e a mais antiga poderia terminar **depois** da mais nova,
sobrescrevendo o código recente com o antigo.

`cancel-in-progress` cancela a execução anterior quando chega uma nova.

---

## 15. O deploy que não roda sempre

O job de deploy tem duas condições:

```yaml
if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
```

**Por que não rodar em pull request?** Publicar código em revisão derrubaria
o ambiente a cada PR aberto. O que interessa no PR é saber se compila e se os
testes passam — e os três primeiros jobs respondem isso.

Foi por isso que, ao abrir o PR da PI3-21, o job apareceu como
`This job was skipped`. Comportamento correto, não erro.

### A checagem de segredos

Dentro do job, o primeiro passo é este:

```yaml
- name: Verificar se os segredos estao configurados
  id: segredos
  run: |
    if [ -z "$VM_HOST" ] || [ -z "$VM_USUARIO" ] || [ -z "$VM_CHAVE_SSH" ]; then
      echo "configurado=false" >> "$GITHUB_OUTPUT"
      echo "::warning::Deploy pulado: faltam os segredos..."
    else
      echo "configurado=true" >> "$GITHUB_OUTPUT"
    fi
```

E todos os passos seguintes carregam `if: steps.segredos.outputs.configurado == 'true'`.

**O problema que isso resolve:** o pipeline foi escrito antes da VM estar
configurada. Sem essa checagem, todo push falharia no deploy, e o time
perderia a referência de "verde = está tudo bem". Com ela, o deploy é pulado
com um aviso amarelo apontando a documentação.

`::warning::` é a sintaxe do GitHub Actions para criar uma anotação visível
na interface. Existem também `::error::` e `::notice::`.

### Segredos: por que não estão no código

`VM_HOST`, `VM_USUARIO` e `VM_CHAVE_SSH` ficam em
**Settings > Secrets and variables > Actions**.

O GitHub os injeta como variáveis de ambiente em tempo de execução e
**mascara nos logs**: se um passo imprimir o valor por acidente, o log mostra
`***` no lugar.

> **Analogia: o cofre do hotel.** A camareira precisa entrar no quarto para
> limpar, então tem a chave. Mas ela não tem a combinação do cofre onde você
> guarda o passaporte.
>
> São dois níveis: acesso ao ambiente (o repositório, que a equipe vê) e
> acesso ao segredo (que só o processo em execução recebe, e ninguém consegue
> ler de volta pela interface — nem você).

Repare nessa última parte: depois de cadastrar um segredo, **o GitHub nunca
mais o mostra**. Você pode substituí-lo, não consultá-lo. É proposital — se a
interface exibisse o valor, qualquer pessoa com acesso à tela de configuração
poderia copiá-lo.

A chave privada é o caso mais crítico. Quem a tiver entra na VM. Por isso:

- não vai para o repositório
- foi gerada exclusivamente para o deploy (`ssh-keygen -f rota_vital_deploy`)
- pode ser revogada sozinha, sem afetar seu acesso pessoal

Se o repositório fosse público e a chave estivesse no código, bots que varrem
o GitHub a encontrariam em minutos.

---

## 16. O deploy, passo a passo

### Envio: nome temporário primeiro

```bash
scp -i ~/.ssh/deploy_key rota-vital.jar \
    usuario@host:/tmp/rota-vital-novo.jar
```

Repare no destino: `/tmp/rota-vital-novo.jar`, não o caminho final.

**Por quê?** Se a transferência cair no meio — rede instável, disco cheio, o
runner do GitHub sendo desligado — o arquivo em `/tmp` fica truncado. Mas o
jar **em uso** continua intacto, e a aplicação segue no ar com a versão
anterior.

Se o `scp` escrevesse direto em `/opt/rota-vital/rota-vital.jar`, uma
transferência interrompida deixaria ali um arquivo pela metade. No próximo
reinício, a aplicação não subiria — e o motivo seria difícil de descobrir,
porque o arquivo existe e tem tamanho.

> **Analogia: a troca do pneu.** Você não tira o pneu velho para só depois ir
> buscar o novo no porta-malas. Primeiro traz o novo para perto, confere que
> está cheio, e só então faz a troca — que leva segundos.
>
> O tempo em que o carro fica sem pneu é o menor possível, e se descobrir que
> o estepe está furado, você descobre **antes** de ficar sem nenhum.

Esse padrão tem nome: **preparar tudo fora, trocar de uma vez**. Aparece em
muitos lugares — bancos de dados fazem isso ao gravar, editores de texto
fazem ao salvar.

### `StrictHostKeyChecking` e o `ssh-keyscan`

```bash
ssh-keyscan -H "$VM_HOST" >> ~/.ssh/known_hosts
scp -o StrictHostKeyChecking=yes ...
```

Na primeira conexão a um servidor, o SSH pergunta:

```
The authenticity of host '...' can't be established.
Are you sure you want to continue connecting (yes/no)?
```

Numa automação não há ninguém para responder, e o comando travaria até o
tempo limite.

`ssh-keyscan` busca a impressão digital do servidor e a registra em
`known_hosts` **antes** da conexão. Com ela registrada, o SSH conecta sem
perguntar.

`StrictHostKeyChecking=yes` mantém a verificação ativa. A alternativa
preguiçosa (`=no`) aceitaria qualquer servidor, o que abriria espaço para
ataque de intermediário.

### A troca do jar

O `publicar.sh`, que vive na VM:

```bash
if [ -f "$ATUAL" ]; then
    cp "$ATUAL" "$ANTERIOR"      # guarda a versão em uso
fi

mv "$NOVO" "$ATUAL"              # promove a nova
chown rotavital:rotavital "$ATUAL"

systemctl restart rota-vital
sleep 3

if systemctl is-active --quiet rota-vital; then
    echo "Servico ativo."
else
    journalctl -u rota-vital -n 30 --no-pager >&2
    exit 1
fi
```

Três decisões:

**Guardar a versão anterior.** Se o deploy quebrar, voltar são dois comandos:

```bash
sudo cp /opt/rota-vital/rota-vital-anterior.jar /opt/rota-vital/rota-vital.jar
sudo systemctl restart rota-vital
```

**`mv` em vez de `cp`.** Essa escolha parece detalhe e não é.

`cp` **copia o conteúdo**: abre o destino, escreve byte a byte, fecha. Durante
esses milissegundos, o arquivo de destino existe mas está incompleto.

`mv`, dentro do mesmo sistema de arquivos, **não move dado nenhum**. Ele só
troca uma entrada na tabela de diretórios: o nome `rota-vital.jar` passa a
apontar para os blocos onde o novo arquivo já estava. É uma operação
**atômica** — acontece inteira ou não acontece.

> **Analogia: a etiqueta na prateleira.** Copiar é transferir o conteúdo de
> uma caixa para outra, item por item. Se alguém olhar no meio do processo, vê
> uma caixa parcialmente cheia.
>
> Mover (no mesmo sistema de arquivos) é trocar a etiqueta. A caixa já estava
> pronta no depósito; você só mudou qual prateleira ela ocupa. Não existe
> instante em que a etiqueta esteja "pela metade".

**Detalhe importante:** isso só vale **dentro do mesmo sistema de arquivos**.
Se `/tmp` estivesse num disco diferente de `/opt`, o `mv` teria que copiar de
verdade e perderia a atomicidade. Nesta VM ambos estão em `/dev/root`, então
funciona.

**`sleep 3` antes de verificar.** Dá tempo do systemd registrar falha imediata
(porta ocupada, jar corrompido). Sem a pausa, `is-active` responderia
"ativando" e o script reportaria sucesso indevido.

### Por que o script vive na VM

O `publicar.sh` poderia estar no workflow, como uma sequência de comandos SSH.
Está na VM porque o deploy precisa de `sudo` para reiniciar o serviço.

Com o script na VM, a regra do sudoers libera **um alvo específico**:

```
CHONGRENATOO ALL=(root) NOPASSWD: /opt/rota-vital/publicar.sh
```

Se estivesse no workflow, seria preciso `NOPASSWD: /bin/systemctl` ou algo
mais amplo. Se a chave SSH vazasse, o atacante teria mais poder.

### Duas verificações, e a ordem importa

```yaml
- name: Confirmar que a aplicacao subiu (dentro da VM)
  run: ssh ... 'curl http://127.0.0.1:8081/actuator/health'

- name: Confirmar que o Nginx esta encaminhando (pela URL publica)
  run: curl "$URL_PUBLICA/actuator/health"
```

**Por que duas?** Se houvesse só a pública e ela falhasse, a mensagem seria
"não respondeu" — e você não saberia onde procurar.

> **Analogia: o telefone que não toca.** Um cliente liga para a empresa e
> ninguém atende. O problema pode ser a linha da operadora, o PABX da recepção
> ou o ramal da sala. "Ninguém atendeu" não distingue os três.
>
> Se você primeiro testa o ramal por dentro (funciona) e depois liga de fora
> (não funciona), já eliminou uma possibilidade: o problema está entre a rua e
> a recepção, não na sala.

Separadas, o job aponta a camada:

| Interna | Pública | Diagnóstico |
|---|---|---|
| falha | — | a JVM não subiu; ler `journalctl` |
| passa | falha | o Nginx não encaminha; ler a config |
| passa | passa | está no ar |

A primeira roda **por SSH**, porque a aplicação escuta em `127.0.0.1` e não
aceita conexão de fora — assunto da seção 18.

### A espera de 3 minutos

```bash
for tentativa in $(seq 1 36); do
  resposta=$(curl -sf --max-time 5 ".../actuator/health" || true)
  if echo "$resposta" | grep -q '"status":"UP"'; then
    exit 0
  fi
  sleep 5
done
exit 1
```

Enquanto o Spring Boot sobe, o Actuator responde **503** com status `DOWN`: o
"readiness" ainda não ficou pronto. Isso é normal, não é falha.

O `|| true` impede que o `curl -f` (que retorna erro em 503) derrube o script
por causa do `set -e` implícito. O laço insiste até dar `UP`.

**36 tentativas de 5 segundos = 3 minutos.** Na sua máquina a aplicação sobe
em 12 segundos; numa VM modesta é bem mais. Margem curta transformaria deploy
lento em deploy vermelho.

---

## 17. systemd: quem mantém a aplicação viva

Suponha que você entre na VM por SSH e rode:

```bash
java -jar rota-vital.jar
```

A aplicação sobe, responde, tudo funciona. Aí você fecha o PuTTY para dormir.

**A aplicação morre junto.**

O motivo: no Linux, todo processo tem um "pai". Quando você entra por SSH, o
sistema cria uma sessão, e o `java` que você iniciou é filho dela. Fechando a
sessão, o sistema derruba os filhos.

> **Analogia: o funcionário e a empresa.** Rodar `java -jar` pelo terminal é
> como pedir um favor a um amigo que está te visitando: enquanto ele está lá,
> faz o serviço. Quando vai embora, acabou.
>
> O systemd é contratar um funcionário registrado. Ele não depende da sua
> presença, tem horário de entrada definido (sobe no boot), volta ao trabalho
> se passar mal (reinicia ao cair), e existe um lugar oficial onde o trabalho
> dele fica registrado (o journal).

O **systemd** é o gerenciador de serviços do Linux — o processo de número 1,
o primeiro que o kernel inicia e o pai de todos os outros. Ele resolve quatro
problemas de uma vez:

| Problema | Como resolve |
|---|---|
| processo morre ao fechar o terminal | roda desacoplado de qualquer sessão |
| aplicação cai | `Restart=always` sobe de novo |
| servidor reinicia | `WantedBy=multi-user.target` sobe no boot |
| onde vão os logs | `journalctl -u rota-vital` |

O arquivo, gerado pelo `provisionar.sh`:

```ini
[Unit]
Description=Rota Vital - plataforma de distribuicao de hemocomponentes
After=network.target

[Service]
Type=simple
User=rotavital
Group=rotavital
WorkingDirectory=/opt/rota-vital

Environment="SPRING_PROFILES_ACTIVE=prod"

ExecStart=/usr/lib/jvm/java-21-openjdk-amd64/bin/java \
          -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC \
          -jar /opt/rota-vital/rota-vital.jar

Restart=always
RestartSec=10

StandardOutput=journal
StandardError=journal
SyslogIdentifier=rota-vital

NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/rota-vital

[Install]
WantedBy=multi-user.target
```

### Lendo o arquivo por partes

Um arquivo `.service` tem três seções, e cada uma responde a uma pergunta
diferente:

| Seção | Pergunta que responde |
|---|---|
| `[Unit]` | o que é isto e do que depende? |
| `[Service]` | como executar? |
| `[Install]` | quando deve subir sozinho? |

**`After=network.target`** — só tenta subir depois que a rede está pronta.

`network.target` é um marco: o systemd o atinge quando as interfaces de rede
estão configuradas. Sem essa linha, a aplicação poderia tentar abrir a porta
8081 antes de existir rede, falhar, e o `Restart=always` entraria em ciclo
até a rede subir. Funcionaria, mas com erros no log e demora desnecessária.

**`User=rotavital`** — a aplicação **não roda como root**. O usuário foi
criado sem shell de login (`--shell /usr/sbin/nologin`) e sem diretório home.
Se alguém explorar uma falha na aplicação, fica limitado ao que esse usuário
pode fazer, que é quase nada.

**`Environment="SPRING_PROFILES_ACTIVE=prod"`** — é assim que o perfil de
produção é ativado. Sem esta linha, a aplicação subiria com o
`application.properties` de desenvolvimento, **com o console do H2 ligado**.

**`Restart=always` + `RestartSec=10`** — se cair, sobe de novo após 10
segundos. A pausa evita ciclo rápido de falha: sem ela, um erro na inicialização
faria o systemd tentar centenas de vezes por minuto.

**`ExecStart` com caminho absoluto** — `/usr/lib/jvm/java-21-.../bin/java`, não
`/usr/bin/java`.

O segundo é um **link simbólico** gerenciado pelo `update-alternatives`, o
mecanismo do Debian/Ubuntu para escolher qual versão de um programa é a
padrão. A cadeia é:

```
/usr/bin/java  →  /etc/alternatives/java  →  /usr/lib/jvm/java-21-.../bin/java
```

Se alguém instalar o Java 17 na máquina amanhã, o `update-alternatives` pode
mudar o meio dessa cadeia, e seu serviço passaria a rodar noutra versão **sem
nenhuma alteração no arquivo do serviço**. O erro apareceria em produção, sem
causa aparente.

Apontar direto para o binário elimina a cadeia. (E não localizar esse caminho
foi um bug real deste projeto: ver seção 21.)

**`WantedBy=multi-user.target`** — é o que faz o serviço subir no boot.

`multi-user.target` é o estado "sistema pronto para uso, com rede, sem
interface gráfica" — o normal de um servidor. Dizer que o serviço é "wanted
by" esse alvo significa: quando o sistema chegar nesse estado, suba isto
também.

Essa linha só tem efeito depois de `systemctl enable`, que é o que o
`provisionar.sh` faz. `enable` cria um link simbólico dentro de
`multi-user.target.wants/`, e é assim que o systemd sabe o que subir.

### As restrições de segurança

```ini
NoNewPrivileges=true      # não consegue escalar privilégio
PrivateTmp=true           # /tmp isolado, invisível para outros processos
ProtectSystem=strict      # sistema de arquivos somente leitura
ProtectHome=true          # /home inacessível
ReadWritePaths=/opt/rota-vital   # a única exceção
```

Isso é **defesa em profundidade**: mesmo que alguém consiga executar código
dentro da aplicação, o sistema operacional limita o estrago.

Numa VM compartilhada isso importa em dobro — protege também a aplicação
vizinha.

### Comandos do dia a dia

```bash
sudo systemctl status rota-vital     # estado atual
sudo systemctl restart rota-vital    # reiniciar
sudo journalctl -u rota-vital -f     # log ao vivo
sudo journalctl -u rota-vital -n 50  # últimas 50 linhas
```

O `-f` é o mais útil durante um deploy: você vê a aplicação subindo linha a
linha.

---

## 18. O perfil de produção

Arquivo: `src/main/resources/application-prod.properties`.

Ele **sobrescreve** o `application.properties`. O que não estiver aqui,
continua valendo de lá.

O mecanismo: quando o Spring sobe com `SPRING_PROFILES_ACTIVE=prod`, ele
carrega primeiro o `application.properties` e depois o
`application-prod.properties`, deixando o segundo vencer nos casos de
conflito. É por isso que o arquivo de produção só precisa listar as
**diferenças**, não a configuração inteira.

> **Analogia: a casa e o hotel.** Em casa você deixa a chave embaixo do tapete,
> a janela do banheiro entreaberta e o portão só encostado. É prático, e você
> conhece todo mundo da rua.
>
> Num hotel de beira de estrada, as mesmas escolhas seriam imprudentes. Não
> porque você virou outra pessoa — porque o ambiente mudou. Passa gente que
> você não conhece, e a maioria não tem interesse nenhum em você, mas basta
> uma.
>
> O `application-prod.properties` é a lista do que você tranca ao sair de
> casa.

A regra que guiou o arquivo: **tudo que é conveniente em desenvolvimento e
perigoso na internet fica desligado**.

### A linha mais importante

```properties
spring.h2.console.enabled=false
```

O console do H2 é uma página web com um terminal SQL embutido. Em
desenvolvimento é prático: você abre no navegador e roda `SELECT` para ver o
que está no banco.

Exposto na internet, é outra coisa. Qualquer pessoa que acesse `/h2-console`
recebe uma tela de login já preenchida — porque o H2 em memória sobe com
usuário `sa` e **senha vazia**. Um clique em "Connect" e a pessoa está dentro,
podendo rodar:

```sql
SELECT * FROM BOLSA;          -- ler tudo
UPDATE BOLSA SET status = ...; -- alterar tudo
DROP TABLE BOLSA;              -- apagar tudo
```

Não é preciso ser especialista. Basta encontrar a URL — e existem varredores
automáticos que testam `/h2-console` em toda a internet justamente porque
essa configuração é esquecida com frequência.

> **Analogia: a porta dos fundos.** Durante a obra, o pedreiro deixa a porta
> dos fundos aberta para carregar material. É prático e ninguém se importa,
> porque a casa está vazia e cercada de tapumes.
>
> Entregar a casa com aquela porta ainda aberta é outra história. E o pior é
> que ela não chama atenção: quem passa na frente vê tudo trancado.

**Quatro camadas** garantem que isso não volte por acidente:

1. o perfil desliga
2. `PerfilProducaoTest` quebra o build se alguém religar
3. o Nginx bloqueia o caminho (`location /h2-console { return 404; }`)
4. o workflow confere na URL pública depois do deploy

Uma linha de configuração é fácil de reverter numa sessão de depuração. Por
isso as outras três.

### Escutar só em localhost

```properties
server.port=8081
server.address=127.0.0.1
```

`server.address` é a decisão de arquitetura mais interessante do arquivo.

Por padrão o Spring escuta em `0.0.0.0`, que significa "qualquer interface de
rede" — a aplicação aceita conexão de qualquer lugar. Com `127.0.0.1`, o
**sistema operacional recusa** conexão vinda de outra máquina.

Vale entender o que são esses endereços. Uma máquina tem várias "portas de
entrada" de rede:

| Endereço | O que é |
|---|---|
| `127.0.0.1` (localhost) | a interface interna, que só a própria máquina alcança |
| `10.138.0.2` | a rede privada do Google Cloud |
| `136.109.172.191` | o IP público, alcançável da internet |
| `0.0.0.0` | "todas as acima" |

Escutar em `0.0.0.0` é abrir todas as portas do prédio. Escutar em `127.0.0.1`
é abrir só a porta que dá para o corredor interno.

> **Analogia: o ramal interno.** Um escritório tem um telefone com número
> público e ramais internos. O ramal 302 não recebe ligação de fora: quem liga
> do mundo cai na recepção, e a recepcionista transfere.
>
> Se o ramal 302 tivesse linha direta, alguém poderia discar para ele sem
> passar pela recepção — e todas as regras da recepção (registrar visitante,
> recusar indesejado) seriam contornadas.
>
> `server.address=127.0.0.1` é remover a linha direta. A única forma de falar
> com a aplicação é pela recepção, que é o Nginx.

**O ganho concreto:** mesmo que alguém erre e abra a porta 8081 no firewall do
Google Cloud, não há o que alcançar. O sistema operacional recusa a conexão
antes de qualquer regra de aplicação. São duas travas independentes, e a de
baixo não depende de ninguém lembrar de configurar a de cima.

O efeito prático: a única forma de alcançar a aplicação é pelo Nginx, que
roda na mesma máquina. Mesmo que a porta 8081 fosse aberta no firewall por
engano, não haveria o que alcançar.

É a mesma ideia da sub-rede privada no diagrama de arquitetura do PI3-16,
aplicada dentro de uma máquina só.

> **Como verificar:** `ss -tlnp | grep 8081` na VM. Se mostrar
> `127.0.0.1:8081`, está isolado. Se mostrar `0.0.0.0:8081`, está exposto.

**Por que 8081 e não 8080?** A VM é compartilhada, e 8080 é a porta mais
disputada. Duas aplicações na mesma porta não convivem: a segunda a subir
falha com `Port already in use` e o systemd entra em ciclo de reinício.

### O resto do arquivo

```properties
spring.jpa.show-sql=false                    # SQL enche o disco e vaza dado em log
spring.jpa.open-in-view=false                # ver Bug 3, na seção 11
server.error.include-stacktrace=never        # stack trace entrega estrutura interna
server.error.include-message=never
management.endpoints.web.exposure.include=health   # só o health
management.endpoint.health.show-details=never
logging.level.root=WARN                      # INFO global despeja centenas de linhas
logging.level.com.rotavital=INFO
```

**Sobre o Actuator:** em desenvolvimento é útil expor tudo. Em produção,
`/actuator/env` lista variáveis de ambiente e `/actuator/beans` mapeia a
aplicação inteira. Ambos são presente para quem está sondando o servidor.

### Os limites de memória da JVM

No serviço systemd:

```
-Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC
```

**A conta que importa: a JVM consome mais que o heap.** Metaspace (definições
de classe), pilhas de thread e buffers ficam **fora** dele.

O erro que cometi na primeira versão: escolhi `-Xmx384m` achando conservador.
Fazendo a conta com a VM real (978 MB totais, ~577 MB disponíveis):

| Heap | Total real | Sobra |
|---|---|---|
| 384m | ~530 MB | 43 MB ← perigoso |
| 256m | **331 MB** (medido) | **246 MB** |

Com 43 MB de folga, o kernel começaria a matar processos — e o morto poderia
ser a aplicação vizinha.

**`UseSerialGC`**: coletor de lixo de uma thread só. Numa VM com 2 vCPU
compartilhadas, o coletor paralelo gasta mais em coordenação do que ganha em
paralelismo, além de reservar mais memória.

> **Lição de método:** eu tinha estimado "heap + overhead". Medir deu 331 MB.
> A estimativa estava na direção certa mas errada no número — e em produção a
> diferença entre 43 MB e 246 MB de folga é a diferença entre estável e
> instável.

---

## 19. Nginx: o porteiro

A aplicação escuta em `127.0.0.1:8081`, inalcançável de fora. Quem atende a
internet é o **Nginx**, um servidor web que faz **proxy reverso**: recebe a
requisição e a repassa para outro processo.

```
internet → Nginx (:443, TLS) → 127.0.0.1:8081 (aplicação)
```

> **Analogia: a portaria do prédio.** Um prédio comercial tem várias empresas.
> Ninguém entra direto na sala 302: você chega na portaria, diz para quem vai,
> e o porteiro encaminha.
>
> A portaria faz mais do que encaminhar. Ela é o único ponto de entrada (as
> salas não têm porta para a rua), registra quem entrou (o log), pode recusar
> visitante indesejado (o bloqueio do `/h2-console`), e é onde fica a placa
> com o nome do prédio (o certificado HTTPS).
>
> No nosso caso, duas "empresas" dividem o prédio: o Flux na sala 3000 e o
> Rota Vital na 8081. Quem decide para onde encaminhar é o **nome que o
> visitante pergunta** — o domínio.

### Por que "reverso"?

Um proxy comum protege o **cliente**: você configura o navegador para passar
por ele, e os sites veem o proxy em vez de você.

Um proxy **reverso** protege o **servidor**: o cliente acha que está falando
com o Nginx, e não sabe que existe uma JVM atrás. A palavra "reverso" indica
de que lado da conversa o intermediário está.

Nesta VM ele já existia, servindo outra aplicação. O Rota Vital entrou como
mais um **site**, sem tocar no que já rodava.

### A estrutura de arquivos

```
/etc/nginx/
├── sites-available/     todos os sites configurados
│   ├── flux
│   └── rota-vital
└── sites-enabled/       os que estão ativos (links simbólicos)
    ├── flux -> ../sites-available/flux
    └── rota-vital -> ../sites-available/rota-vital
```

Desabilitar um site é remover o link de `sites-enabled/`, sem apagar a
configuração. É reversível, e foi assim que o site órfão do projeto antigo
saiu.

### A configuração

```nginx
server {
    listen 80;
    server_name rsc3-rotavital.duckdns.org;

    client_max_body_size 1M;

    location /h2-console {
        return 404;
    }

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;

        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 10s;
        proxy_read_timeout    90s;
        proxy_send_timeout    90s;
    }

    access_log /var/log/nginx/rota-vital-access.log;
    error_log  /var/log/nginx/rota-vital-error.log;
}
```

**`server_name`** decide qual site atende, e o mecanismo merece detalhe.

Os dois domínios apontam para o **mesmo IP**:

```
rsc3-flux.duckdns.org       →  136.109.172.191
rsc3-rotavital.duckdns.org  →  136.109.172.191
```

Como o Nginx sabe qual é qual, se a conexão TCP chega no mesmo endereço e na
mesma porta? Pelo cabeçalho `Host`, que o navegador envia em toda requisição
HTTP:

```
GET /api/v1/hemocentros HTTP/1.1
Host: rsc3-rotavital.duckdns.org     ← é isto que decide
```

O Nginx compara esse valor com os `server_name` dos sites habilitados e
escolhe o bloco correspondente. Sem esse cabeçalho, hospedar vários sites num
IP só seria impossível — e foi justamente para permitir isso que ele passou a
ser obrigatório no HTTP/1.1.

> **Teste que mostra o mecanismo:** de dentro da VM, force um `Host` diferente
> e veja o Nginx mudar de resposta:
>
> ```bash
> curl -H "Host: rsc3-rotavital.duckdns.org" http://127.0.0.1/actuator/health
> curl -H "Host: rsc3-flux.duckdns.org" http://127.0.0.1/
> ```
>
> Mesmo IP, mesma porta, respostas de aplicações diferentes.

**`proxy_set_header`** preserva a identidade do cliente, e sem isso a
aplicação ficaria cega.

Pense no caminho: um visitante de Recife acessa o site. O Nginx recebe a
conexão dele e abre uma **nova** conexão para `127.0.0.1:8081`. Do ponto de
vista da aplicação, quem está chamando é o próprio servidor.

Sem os cabeçalhos, o log da aplicação registraria `127.0.0.1` para toda
requisição do mundo. Um bloqueio por IP seria inútil, e uma auditoria,
impossível.

| Cabeçalho | O que carrega |
|---|---|
| `Host` | o domínio que o visitante pediu |
| `X-Real-IP` | o IP verdadeiro dele |
| `X-Forwarded-For` | a cadeia de proxies, se houver mais de um |
| `X-Forwarded-Proto` | se a conexão original era `http` ou `https` |

O último é o menos óbvio e o mais traiçoeiro. Entre o Nginx e a aplicação o
tráfego é HTTP puro (não precisa de TLS, é dentro da mesma máquina). Se a
aplicação precisar montar uma URL absoluta — num redirecionamento, por
exemplo — sem esse cabeçalho ela geraria `http://...` e quebraria o HTTPS do
visitante.

**`location /h2-console`** é defesa em profundidade. O perfil já desliga o
console, então a aplicação responderia 404 de qualquer forma. Este bloco
recusa **antes** de a requisição chegar lá.

**Log separado** evita misturar com o tráfego das outras aplicações da VM.

### `nginx -t`: o passo que não se pula

```bash
sudo nginx -t                    # testa sem aplicar
sudo systemctl reload nginx      # aplica
```

`nginx -t` valida a sintaxe. Se houver erro, ele recusa e o Nginx **atual
continua servindo normalmente**.

Numa máquina com outra aplicação em produção, isso não é opcional: um erro de
digitação aplicado direto derrubaria o vizinho junto.

**`reload` e não `restart`:** `reload` relê a configuração sem derrubar
conexões em andamento. `restart` mata o processo e sobe de novo, cortando
quem estava no meio de uma requisição.

---

## 20. HTTPS com Certbot

`http://` trafega em texto puro. Entre o navegador do visitante e o seu
servidor há dezenas de equipamentos: o roteador de casa, o provedor, os
cabos submarinos, o data center. **Qualquer um deles pode ler e alterar** o
conteúdo.

`https://` resolve duas coisas ao mesmo tempo, e a segunda costuma ser
esquecida:

1. **Sigilo** — o conteúdo é criptografado
2. **Identidade** — você tem garantia de estar falando com o servidor certo

> **Analogia: a carta e o lacre.** HTTP é um cartão-postal: todo mundo que
> manuseia consegue ler. HTTPS é uma carta lacrada, dentro de um envelope que
> só o destinatário abre.
>
> Mas há um detalhe. De que adianta lacrar a carta se você a entrega ao
> impostor errado? Por isso o certificado tem dois papéis: ele fornece a chave
> do lacre **e** prova que o destinatário é quem diz ser.
>
> A prova vem de um cartório reconhecido. No mundo digital, esses cartórios
> são as **autoridades certificadoras**, e os navegadores já vêm com a lista
> das confiáveis embutida.

Para isso é preciso um **certificado**, emitido por uma autoridade que os
navegadores reconheçam. O **Let's Encrypt** é uma dessas autoridades, emite de
graça, e o **Certbot** automatiza o processo.

> **Por que era pago antes.** Até 2015, certificados custavam de dezenas a
> centenas de dólares por ano. O Let's Encrypt mudou isso emitindo de graça e
> automaticamente — em troca, os certificados valem 90 dias em vez de um ano,
> o que só é viável porque a renovação é automática.

Um comando:

```bash
sudo certbot --nginx -d rsc3-rotavital.duckdns.org
```

O que acontece:

**1. Prova que você controla o domínio.**

Esta é a parte engenhosa. O Let's Encrypt precisa ter certeza de que quem
pede o certificado para `rsc3-rotavital.duckdns.org` realmente controla esse
domínio — senão qualquer um pediria um certificado para `banco.com.br`.

O método usado aqui chama-se **HTTP-01**:

```
Certbot → Let's Encrypt: "quero certificado para rsc3-rotavital.duckdns.org"
Let's Encrypt → Certbot: "prove. Coloque o texto ABC123 em
                          /.well-known/acme-challenge/xyz"
Certbot: cria o arquivo no servidor e configura o Nginx para servi-lo
Certbot → Let's Encrypt: "pronto"
Let's Encrypt: acessa http://rsc3-rotavital.duckdns.org/.well-known/... 
               pela internet, encontra ABC123 e emite o certificado
```

A lógica: só quem controla o DNS **e** o servidor consegue fazer aquele
endereço devolver o texto pedido.

É por isso que o domínio precisava estar resolvendo **antes** de rodar o
Certbot. Sem DNS, a autoridade não teria onde buscar a prova.

**2. Recebe e salva o certificado** em `/etc/letsencrypt/live/DOMINIO/`:

| Arquivo | O que é |
|---|---|
| `fullchain.pem` | o certificado + a cadeia até a autoridade raiz |
| `privkey.pem` | a chave privada — **nunca sai do servidor** |

**3. Edita o arquivo do Nginx sozinho**, acrescentando o bloco 443.

**4. Agenda a renovação automática** (um timer do systemd ou uma tarefa em
`/etc/cron.d/certbot`).

### O que ele acrescenta

```nginx
    listen 443 ssl; # managed by Certbot
    ssl_certificate /etc/letsencrypt/live/DOMINIO/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/DOMINIO/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
}

server {
    if ($host = DOMINIO) {
        return 301 https://$host$request_uri;
    }
    listen 80;
    server_name DOMINIO;
    return 404;
}
```

O segundo bloco redireciona HTTP para HTTPS com **301** (movido
permanentemente). Por isso `http://rsc3-rotavital.duckdns.org` devolve 301 e
o navegador segue para a versão segura.

### Por que o arquivo versionado só tem a porta 80

`infra/nginx-rota-vital.conf` no repositório contém apenas o bloco `listen 80`.

**O resto é gerado.** Versionar o resultado do Certbot causaria dois
problemas: os caminhos de certificado são específicos daquele servidor, e
sobrescrever o arquivo atrapalharia a renovação automática.

O padrão é: versionar a configuração base, deixar o Certbot completar.

### Renovação

Certificados do Let's Encrypt valem **90 dias**. O Certbot instala uma tarefa
agendada que renova sozinha quando falta um mês.

```bash
sudo certbot certificates        # o que existe e quando expira
sudo certbot renew --dry-run     # simula a renovação
```

> **Ponta solta deste projeto:** o certificado do domínio antigo continua
> registrado depois que o site saiu. A renovação vai falhar e gerar e-mail de
> erro. `sudo certbot delete --cert-name DOMINIO` resolve.

---

## 21. Os bugs da Parte II

A seção 11 tem três bugs do CRUD. Estes apareceram no deploy, e todos têm o
mesmo padrão: **só surgiram ao rodar de verdade**.

### Bug 4: `-maxdepth 2` no `find`

O script procurava o binário do Java assim:

```bash
JAVA_BIN=$(find /usr/lib/jvm -maxdepth 2 -type f -path "*21*/bin/java" | head -1)
```

Nunca encontrava. O caminho real é:

```
/usr/lib/jvm/java-21-openjdk-amd64/bin/java
             └──── 1 ────┘└─2─┘└─3─┘
```

Três níveis abaixo de `/usr/lib/jvm`, não dois.

O script caía no fallback e usava `/usr/bin/java`. **Funcionava** — e é por
isso que o bug passou despercebido: nada quebrou.

Mas anulava a proteção pretendida. `/usr/bin/java` é link gerenciado pelo
`update-alternatives`; instalar outro Java na máquina mudaria para onde
aponta, e o serviço passaria a rodar noutra versão sem ninguém saber.

**A correção**, com `-maxdepth 3` e uma segunda tentativa via `readlink -f`,
foi testada com estrutura simulada contendo Java 17 e 21: o `find` antigo não
achava nada, o novo acha e escolhe o 21.

**A lição:** um fallback silencioso esconde bug. Se eu não tivesse lido o
aviso amarelo na saída, o problema ficaria latente até alguém instalar outro
Java meses depois.

### Bug 5: CRLF quebrando heredoc

O `ci.yml` estava salvo com fim de linha do Windows (CRLF). Num heredoc:

```bash
ssh ... bash -s <<'REMOTO'
...
REMOTO
```

O marcador final vira `REMOTO\r`, que **não casa** com `REMOTO`. O script
ficaria esperando um fim que nunca chega, e o job travaria até o tempo limite.

A correção foi no `.gitattributes`:

```
*.sh    text eol=lf
*.yml   text eol=lf
*.conf  text eol=lf
```

**É a mesma família do problema que derrubou o CI no PI3-15**, quando o `mvnw`
foi commitado sem bit de execução. Windows e Linux discordam sobre convenções
de arquivo, e o Git é o lugar de resolver.

### Bug 6: `store~echo` no credential.helper

Ao clonar o repositório na VM:

```
remote: Invalid username or token.
fatal: Authentication failed
```

Investigando:

```bash
git config --global --get-all credential.helper
store
store~echo        ← isto não é um helper válido
```

O `store~echo` era resíduo de uma colagem quebrada em alguma sessão anterior.
O Git tentava usar um helper com esse nome, não encontrava, e a autenticação
falhava.

```bash
git config --global --replace-all credential.helper store
```

**A lição:** o sintoma ("token expirado") apontava para o lugar errado. O
token podia estar bom; o problema era a configuração que decidia como usá-lo.
Quando o sintoma óbvio não se confirma, vale olhar a camada de baixo.

---

## 22. O que aprender daqui

Cinco ideias que passam do projeto para qualquer sistema.

**1. O ambiente de teste é sempre mais permissivo que produção.** O Bug 3
(seção 11) existia porque `@Transactional` no teste mantinha a sessão aberta.
Os 46 testes passavam e a aplicação quebrava em produção. Rodar o artefato
real, com a configuração real, é uma etapa que testes não substituem.

**2. Defesa em profundidade.** O console do H2 está bloqueado em quatro
lugares independentes. Nenhum é redundante: cada um cobre uma forma diferente
de o anterior falhar (alguém edita o arquivo, alguém ignora o teste, alguém
troca o proxy).

**3. Menor privilégio.** A aplicação roda como usuário sem shell. O sudo do
deploy libera **um script**, não o `systemctl` inteiro. A chave SSH é
exclusiva do deploy. Cada limite reduz o estrago de um comprometimento.

**4. Falha barulhenta é melhor que silenciosa.** O `-maxdepth 2` falhava sem
quebrar nada, e por isso quase passou. Um fallback deve avisar o que perdeu,
não só seguir.

**5. Medir em vez de estimar.** Minha conta de memória estava na direção certa
e errada no número. A diferença entre 43 MB e 246 MB de folga é a diferença
entre um serviço instável e um estável.

---

## 23. Exercícios da Parte II

**1. Ver o deploy acontecendo.** Antes de um push na `main`, abra na VM:

```bash
sudo journalctl -u rota-vital -f
```

Você vê o serviço parando, o jar sendo trocado e a aplicação subindo de novo.

**2. Provar o isolamento de rede.** Na VM:

```bash
curl http://127.0.0.1:8081/actuator/health    # responde
curl http://136.109.172.191:8081/actuator/health   # recusa
```

O primeiro funciona, o segundo não. É o `server.address=127.0.0.1` agindo.

**3. Quebrar o Nginx de propósito** (e ver a proteção funcionando):

```bash
sudo cp /etc/nginx/sites-available/rota-vital /tmp/backup.conf
echo "linha invalida {" | sudo tee -a /etc/nginx/sites-available/rota-vital
sudo nginx -t                    # vai reprovar
```

O `nginx -t` recusa, e o site continua no ar porque nada foi aplicado.
Restaure:

```bash
sudo cp /tmp/backup.conf /etc/nginx/sites-available/rota-vital
sudo nginx -t
```

**4. Simular queda da aplicação.** Descubra o PID e mate:

```bash
sudo systemctl show -p MainPID --value rota-vital
sudo kill -9 <PID>
sudo systemctl status rota-vital
```

Em 10 segundos o systemd sobe de novo. É o `Restart=always`.

---

## Glossário da Parte II

| Termo | O que é |
|---|---|
| **CI/CD** | Integração Contínua (testar a cada push) e Entrega Contínua (publicar automaticamente) |
| **workflow** | arquivo YAML que define o pipeline no GitHub Actions |
| **job** | etapa do workflow, roda numa máquina virtual própria |
| **artefato** | arquivo gerado por um job e consumido por outro |
| **runner** | a máquina virtual que executa um job |
| **systemd** | gerenciador de serviços do Linux |
| **unit** | arquivo `.service` que define um serviço |
| **journalctl** | leitor de logs do systemd |
| **proxy reverso** | servidor que recebe requisições e repassa a outro processo |
| **TLS** | protocolo de criptografia; o "S" do HTTPS |
| **Certbot** | ferramenta que emite e renova certificados Let's Encrypt |
| **scp** | cópia de arquivo por SSH |
| **known_hosts** | arquivo com as impressões digitais dos servidores conhecidos |
| **sudoers** | configuração de quem pode rodar o quê como root |
| **CRLF / LF** | fim de linha do Windows / do Linux |
| **heap** | região de memória da JVM onde ficam os objetos |
| **metaspace** | memória da JVM para definições de classe, fora do heap |

---

## Onde cada coisa está (Parte II)

```
.github/workflows/ci.yml          o pipeline
.gitattributes                    normalização de fim de linha

infra/
├── provisionar.sh                prepara a VM (roda uma vez)
└── nginx-rota-vital.conf         configuração do site

src/main/resources/
└── application-prod.properties   perfil de produção

src/test/java/com/rotavital/config/
└── PerfilProducaoTest.java        trava as decisões de segurança

docs/deploy.md                     passo a passo operacional
```

**Na VM:**

```
/opt/rota-vital/
├── rota-vital.jar                 a aplicação em uso
├── rota-vital-anterior.jar        a versão anterior
└── publicar.sh                    troca o jar e reinicia

/etc/systemd/system/rota-vital.service
/etc/nginx/sites-available/rota-vital
/etc/sudoers.d/rota-vital-deploy
/etc/letsencrypt/live/rsc3-rotavital.duckdns.org/
```
