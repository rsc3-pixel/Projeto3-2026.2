# Módulo C++: Modelagem Estocástica de Demanda e Desabastecimento (PI3-32)

Este módulo implementa na **linguagem C++ (padrão C++20)** a modelagem estocástica da demanda hospitalar por hemocomponentes e o cálculo probabilístico de desabastecimento (*stockout risk*) para o projeto **Rota Vital**.

---

## 1. Disciplinas Integradas e Conceitos Fundamentais

### 1.1. DADOS ACUMULADOS (Programação Orientada a Objetos - POO)
* **Definição:** Dados acumulativos são valores que vão sendo somados ou atualizados ao longo da execução do programa, conforme novas informações são recebidas.
* **Analogia da Loja:** Imagine o sistema de uma loja que registra o preço de vários produtos. A cada produto cadastrado ou lido no leitor de código de barras, o valor dele é adicionado progressivamente ao total da compra.
* **Aplicação no Código:** Implementado nas classes [`AcumuladorEstoque`](include/AcumuladorEstoque.hpp) e [`AcumuladorDemanda`](include/AcumuladorDemanda.hpp). A cada bolsa ou requisição lida, o operador acumulador `operator+=` atualiza contadores internos encapsulados por tipo sanguíneo e hemocomponente.

### 1.2. SAÍDAS (Algoritmos e Estrutura de Dados - AED)
* **Definição:** As saídas são as informações que o programa apresenta ao usuário depois de executar uma instrução ou processar dados (Pipeline: **Entrada -> Processamento -> Saída**).
* **Aplicação no Código:** Implementado na classe [`PainelConsolidado`](include/PainelConsolidado.hpp). O programa recebe os dados brutos (Entrada), processa via estruturas dinâmicas e algoritmos estocásticos (Processamento) e produz duas saídas ricas (Saída):
  1. Relatório visual tabular no terminal (`std::cout`);
  2. Arquivo estruturado JSON (`dados/painel_consolidado_pi3_32.json`).

---

## 2. Estrutura do Módulo

```
cpp/
├── include/
│   ├── Dominio.hpp                   # Enums TipoSanguineo, TipoHemocomponente, structs Bolsa, Requisicao
│   ├── AcumuladorEstoque.hpp         # POO: Acumulador de bolsas de sangue (válidas, vencidas)
│   ├── AcumuladorDemanda.hpp         # POO: Acumulador de requisições hospitalares (Analogia da Loja)
│   ├── ModeloDemanda.hpp             # Variável aleatória: Poisson, Normal (TCL), Monte Carlo
│   ├── CalculadorDesabastecimento.hpp# Comparação demanda vs estoque válido e probabilidades
│   ├── PainelConsolidado.hpp         # AED: Saídas no terminal e exportação JSON (PI3-32)
│   ├── LeitorDadosSinteticos.hpp     # AED: Entrada de dados (JSON ou gerador nativo estocástico)
│   └── nlohmann/json.hpp             # Biblioteca padrão para serialização JSON
├── src/
│   ├── Dominio.cpp
│   ├── AcumuladorEstoque.cpp
│   ├── AcumuladorDemanda.cpp
│   ├── ModeloDemanda.cpp
│   ├── CalculadorDesabastecimento.cpp
│   ├── PainelConsolidado.cpp
│   ├── LeitorDadosSinteticos.cpp
│   └── main.cpp                      # Ponto de entrada do executável principal
├── tests/
│   └── TestesModelagem.cpp          # Suite de testes automatizados com assert
├── compile.ps1                       # Script de compilação e execução PowerShell
├── build.bat                         # Script de compilação Windows Batch
├── Makefile                          # Makefile para ambientes Unix/MinGW
└── README.md
```

---

## 3. Instruções de Compilação e Execução

### Opção 1: PowerShell (Recomendado no Windows)
```powershell
cd cpp
.\compile.ps1
```

### Opção 2: Batch Prompt (CMD)
```cmd
cd cpp
build.bat
```

### Opção 3: Compilação Manual via g++
```bash
# Compilar aplicacao principal:
g++ -std=c++20 -O3 -I include src/*.cpp -o bin/rota_vital_modelagem.exe

# Executar aplicacao:
bin/rota_vital_modelagem.exe

# Compilar e executar testes unitarios:
g++ -std=c++20 -O3 -I include src/Dominio.cpp src/AcumuladorEstoque.cpp src/AcumuladorDemanda.cpp src/ModeloDemanda.cpp src/CalculadorDesabastecimento.cpp tests/TestesModelagem.cpp -o bin/testes_modelagem.exe
bin/testes_modelagem.exe
```

---

## 4. Documentação Completa
Consulte o estudo completo da modelagem estatística com fórmulas, tabelas comparativas e justificativas em:
👉 [`docs/modelagem_demanda_desabastecimento_cpp.md`](../docs/modelagem_demanda_desabastecimento_cpp.md)
