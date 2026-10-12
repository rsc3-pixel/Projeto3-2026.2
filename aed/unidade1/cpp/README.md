# AED Unidade 1 — versão em C++

Lista encadeada de estoque, fila FIFO de requisições e pilha LIFO de
histórico, com alocação manual de memória.

Conversão da versão em C, que fica em [`../c/`](../c/) e continua no
repositório como contraponto para a tradução comentada exigida pela Unidade 2
de AED (PI3-98).

## Como compilar e rodar

```bash
make
./rota_vital_u1
```

Para limpar os objetos e o executável:

```bash
make clean
```

Compilado com `g++ -Wall -Wextra -std=c++17`, sem avisos.

## Equivalência com a versão em C

As duas versões produzem a mesma saída sobre os mesmos dados. A conferência é
reproduzível:

```bash
cd ../c  && make && ./rota_vital_u1 > /tmp/saida_c.txt
cd ../cpp && make && ./rota_vital_u1 > /tmp/saida_cpp.txt
diff /tmp/saida_c.txt /tmp/saida_cpp.txt
```

As únicas diferenças são textos descritivos: a linha "Versão em C++" no
cabeçalho e a etapa 6, que descreve RAII em vez de liberação manual. Todos os
números batem.

## O que mudou, e o que foi mantido de propósito

A conversão **não** trocou as estruturas por `std::list`, `std::queue` e
`std::stack`. Isso resolveria o problema em três linhas e apagaria justamente
o que o exercício ensina: encadeamento de nós, religamento de ponteiros na
remoção e liberação manual de memória.

Os nós continuam alocados com `new` e liberados com `delete`, e os três casos
de ponteiro da remoção em lista encadeada seguem explícitos no código.

| Aspecto | C | C++ |
|---|---|---|
| Texto | `char[16]` com `copiar_texto` para garantir o terminador | `std::string`, que cuida do próprio tamanho |
| Comparação | `strcmp(a, b) == 0` | `a == b` |
| Resultado | macros `RV_OK` / `RV_ERRO` como `int` | `enum class Resultado`, que não se converte em `int` por acidente |
| Valor de saída | parâmetro de saída `(fila, &saida)` + código de retorno | `std::optional<T>` como retorno |
| Criação | `lista_criar()`, podendo devolver `NULL` | construtor; `new` lança `std::bad_alloc` |
| Liberação | `lista_destruir()`, que quem usa precisa lembrar de chamar | destrutor, chamado sozinho ao sair do escopo (RAII) |
| Organização | `struct` + funções soltas com prefixo | `class` com métodos |
| Saída formatada | `printf("%-8s")` | `std::setw` com `std::left` |
| Cópia da estrutura | possível por acidente, causando dupla liberação | construtor de cópia `= delete`, recusado em compilação |

Cada arquivo traz comentários marcados com **DIFERENCA PARA A VERSAO EM C**
nos pontos em que a tradução tomou uma decisão. São esses trechos que
alimentam a tradução comentada da PI3-98.

## Pontes para o Java

A conversão aproxima o código do Java em alguns pontos e mantém distância em
outros. Isso é útil para a tradução comentada:

| | C++ | Java |
|---|---|---|
| Liberação | destrutor, em momento previsível | coletor de lixo, em momento indefinido |
| Ausência de valor | `std::optional<T>` | `Optional<T>` |
| Enum | `enum class`, sem conversão implícita | `enum`, que é uma classe |
| Texto | `std::string`, valor | `String`, referência imutável |
| Erro | valor de retorno ou exceção | exceção, às vezes verificada |

## Arquivos

| Arquivo | O que tem |
|---|---|
| `dominio.hpp` | `Bolsa`, `Requisicao`, `Operacao` e `enum class Resultado` |
| `lista_estoque.hpp` / `.cpp` | lista encadeada, com os três casos de remoção |
| `fila_requisicoes.hpp` / `.cpp` | fila FIFO, com ponteiros de frente e trás |
| `pilha_historico.hpp` / `.cpp` | pilha LIFO, com ponteiro de topo |
| `main.cpp` | demonstração em seis etapas |
| `Makefile` | compilação com um comando |
