# Modelagem Estocástica de Demanda e Risco de Desabastecimento em C++ (PI3-32)

> **Projeto Integrador 3 (Rota Vital - 2026.2)**  
> **Fase 4 (CRISP-DM) — Modelagem**  
> **Linguagem:** C++ (GCC 15.2 / Padrão C++20)  
> **Integração de Disciplinas:** Programação Orientada a Objetos (POO), Algoritmos e Estrutura de Dados (AED), Estatística / Ciência de Dados.

---

## 1. Visão Geral e Contexto de Engenharia

No planejamento do ciclo CRISP-DM do **Rota Vital**, a **Fase 4 (Modelagem)** estava prevista para a semana 12 sob o ticket **PI3-32**. Conforme estabelecido no briefing do projeto (`dados/crisp-dm-briefing.md`), treinar modelos de *Machine Learning* preditivo sobre bases sintéticas seria redundante e contraproducente: o algoritmo apenas aprenderia de volta as premissas paramétricas declaradas em `premissas.py`.

A verdadeira modelagem estocástica com valor de negócio para a hemorrede consiste em:
1. **Modelar a demanda por tipo de hemocomponente e grupo sanguíneo como variável aleatória** a partir dos dados acumulados do projeto;
2. **Calcular a probabilidade analítica e simulada de desabastecimento (*stockout risk*)**, confrontando a distribuição de demanda com a disponibilidade física de bolsas válidas;
3. **Registrar formalmente a distribuição estatística escolhida e sua justificativa clínica/operacional**;
4. **Disponibilizar os resultados para o painel consolidado de indicadores (PI3-32)**, integrando os fundamentos conceituais de **Dados Acumulados (POO)** e **Saídas (AED)**.

---

## 2. Fundamentos Conceituais das Disciplinas

### 2.1. DADOS ACUMULADOS (Programação Orientada a Objetos - POO)

> **Definição:**  
> *Dados acumulativos são valores que vão sendo somados ou atualizados ao longo da execução do programa, conforme novas informações são recebidas.*  
>  
> **Analogia Pedagógica:**  
> Imagine um sistema de uma loja que registra o preço de vários produtos. A cada produto cadastrado ou lido pelo leitor de código de barras no caixa, o seu valor é adicionado progressivamente ao total da compra.

#### Aplicação na Arquitetura C++ do Rota Vital:
No sistema desenvolvido em C++, implementamos duas classes com o padrão de projeto *Accumulator*:
- **`AcumuladorEstoque`:** A cada bolsa física inspecionada (código, grupo ABO/Rh, hemocomponente, dias até vencer e status de vencimento), os acumuladores privados são incrementados (`totalBolsas++`, `totalValidas++`, contadores em mapas associativos `std::map`).
- **`AcumuladorDemanda`:** A cada requisição emitida por uma unidade hospitalar e a cada item de transfusão requisitado, o volume solicitado é somado dinamicamente:
  $$\text{TotalDemandado} \leftarrow \text{TotalDemandado} + Q_{\text{item}}$$
- **Sobrecarga de Operadores em POO:** As classes implementam `operator+=`, permitindo acumulação idiomática e segura:
  ```cpp
  acumuladorDemanda += requisicao; // Adiciona o lote ao total acumulado
  acumuladorEstoque += bolsa;      // Adiciona a bolsa física ao total acumulado
  ```
- **Encapsulamento:** Os totais acumulados não podem ser alterados arbitrariamente por código externo; o acesso ocorre unicamente por meio de métodos seletores (*getters*), garantindo a consistência dos dados contábeis da hemorrede.

---

### 2.2. SAÍDAS (Algoritmos e Estrutura de Dados - AED)

> **Definição:**  
> *As saídas são as informações que o programa apresenta ao usuário depois de executar uma instrução ou processar dados.*  
>  
> **Pipeline Clássico:**  
> O programa recebe dados (**Entrada**), processa esses dados através de algoritmos e estruturas de dados (**Processamento**) e apresenta um resultado (**Saída**).

#### O Pipeline de Três Estágios do Módulo C++:
```
┌─────────────────────────────────┐
│     1. ENTRADA DE DADOS         │  Leitura do arquivo estruturado dados_sinteticos.json
│     (Input / Leitor JSON)       │  (2.000 bolsas de hemocomponentes e 300 requisições hospitalares)
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│     2. PROCESSAMENTO            │  • Acumulação linear O(N) nas classes POO
│     (Estruturas & Algoritmos)   │  • Estruturas de busca rápida: std::map, std::vector, std::pair
│                                 │  • Algoritmos probabilísticos: Poisson, Normal (TCL) e Monte Carlo
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│     3. SAÍDA DE DADOS           │  • Saída 1: Relatório Tabular Rico no Terminal (std::cout formatado)
│     (Output / Painel PI3-32)    │  • Saída 2: Arquivo JSON Consolidado (painel_consolidado_pi3_32.json)
└─────────────────────────────────┘
```

---

## 3. Modelagem Estatística: Demanda como Variável Aleatória

### 3.1. Definição da Variável Aleatória de Demanda ($D$)
A demanda hospitalar por bolsas de sangue não é um valor estático determinístico. Em qualquer intervalo de tempo, o número de pacientes admitidos que requerem transfusões varia de forma estocástica.

Definimos a demanda total por um tipo sanguíneo $t$ em um horizonte $T$ como uma **variável aleatória discreta não-negativa**:
$$D_t = \sum_{j=1}^{N_{\text{req}}} Q_{j, t}, \quad D_t \in \{0, 1, 2, 3, \dots\}$$
onde $N_{\text{req}}$ é a variável aleatória que conta o número de requisições hospitalares recebidas, e $Q_{j, t}$ é a quantidade de bolsas do tipo $t$ solicitadas na $j$-ésima requisição.

---

### 3.2. Distribuição Escolhida: Distribuição de Poisson $D_t \sim \text{Poisson}(\lambda_t)$ e Aproximação Normal $\mathcal{N}(\mu_t, \sigma_t^2)$

#### 1. Distribuição Primária: Poisson
Para variáveis de contagem de eventos independentes em intervalos de tempo, a distribuição padrão é a **Distribuição de Poisson**:
$$P(D_t = k) = \frac{e^{-\lambda_t} \lambda_t^k}{k!}$$
onde $\lambda_t = \mathbb{E}[D_t]$ é a demanda média esperada acumulada no período.

#### 2. Justificativa Teórica e Matemática:
1. **Natureza Discreta e Contagem Não-Negativa:** Não existem frações nem valores negativos de bolsas transfundidas ($k \in \mathbb{N}$).
2. **Independência dos Eventos:** Requisições provenientes de diferentes hospitais (emergências cirúrgicas, UTIs e transfusões programadas) originam-se de causas clínicas independentes.
3. **Teorema do Desmembramento de Poisson (*Poisson Thinning*):** Se a chegada total de requisições hospitalares ocorre segundo um Processo de Poisson com taxa $\lambda_{\text{global}}$ e cada bolsa solicitada tem probabilidade $p_t$ de pertencer ao tipo sanguíneo $t$ (baseada nas frequências genéticas populacionais), a teoria das probabilidades demonstra que o fluxo individual de cada tipo $t$ é **também estritamente um Processo de Poisson**:
   $$D_t \sim \text{Poisson}(\lambda_t), \quad \text{com } \lambda_t = \lambda_{\text{global}} \cdot p_t$$
4. **Literatura Científica Especializada:** Modelos estocásticos de bancos de sangue (e.g., *Prastacos, 1984*; *Pierskalla, 2004*; *Beliën & Forcé, 2012*) estabelecem a distribuição de Poisson como a referência canônica para dimensionamento de estoque hemoterápico.

#### 3. Teorema Central do Limite (TCL) e Aproximação Normal:
Para grupos sanguíneos de alta prevalência (como $\text{A+}$ onde $\lambda = 549$ bolsas, e $\text{O+}$ onde $\lambda = 518$ bolsas), $\lambda > 30$. Pelo Teorema Central do Limite, a soma de múltiplas requisições independentes converge para a **Distribuição Normal**:
$$D_t \approx \mathcal{N}(\mu_t, \sigma_t^2), \quad \text{onde } \mu_t = \lambda_t \text{ e } \sigma_t = \sqrt{\lambda_t}$$
Para o cálculo contínuo, aplica-se a **Correção de Continuidade de Yates**:
$$P(D_t > S_t) = P\left(Z > \frac{S_t + 0.5 - \mu_t}{\sigma_t}\right) = \frac{1}{2} \text{erfc}\left(\frac{S_t + 0.5 - \mu_t}{\sigma_t \sqrt{2}}\right)$$
onde $\text{erfc}$ é a função erro complementar de precisão dupla (`double`) da biblioteca padrão `<cmath>` de C++.

#### 4. Validação por Simulação de Monte Carlo:
Para testar o comportamento empírico do modelo em condições reais de loteamento de pedidos (onde requisições podem solicitar entre 1 e 4 bolsas por item), o módulo C++ executa **$100.000$ iterações de Monte Carlo com semente pseudoaleatória determinística (`2026`)**, permitindo checagem cruzada das probabilidades analíticas.

---

## 4. Cálculo da Probabilidade de Desabastecimento (*Stockout Risk*)

### 4.1. Regra Fundamental de Negócio: Estoque Físico Válido ($S$)
No gerenciamento de sangue, **bolsas vencidas não constituem estoque útil**. Elas representam descarte biológico obrigatório.  
Portanto, a variável de disponibilidade física $S_t$ considera **exclusivamente as bolsas válidas**:
$$S_t = N_{\text{total}}(t) - N_{\text{vencidas}}(t)$$

### 4.2. Formulação Probabilística
O evento de **desabastecimento** ocorre quando a demanda estocástica $D_t$ é estritamente maior que o estoque disponível $S_t$:
$$\text{Desabastecimento} = \{ D_t > S_t \}$$
$$\mathbb{P}(\text{Desabastecimento}_t) = 1 - \mathbb{P}(D_t \le S_t) = 1 - F_{D_t}(S_t)$$

- **Via Poisson:**
  $$\mathbb{P}(\text{Desabastecimento}_t) = 1 - \sum_{k=0}^{S_t} \frac{e^{-\lambda_t} \lambda_t^k}{k!}$$
- **Via Normal (TCL):**
  $$\mathbb{P}(\text{Desabastecimento}_t) = 1 - \Phi\left(\frac{S_t + 0.5 - \mu_t}{\sigma_t}\right)$$
- **Via Monte Carlo:**
  $$\hat{\mathbb{P}}(\text{Desabastecimento}_t) = \frac{1}{N_{\text{sim}}} \sum_{m=1}^{N_{\text{sim}}} \mathbb{I}\left(D_t^{(m)} > S_t\right)$$

---

## 5. Resultados Consolidados sobre os Dados Sintéticos Oficiais

A execução do modelo em C++ sobre a base sintética reproduzível do Rota Vital (**2.000 bolsas coletadas em 60 dias e 300 requisições hospitalares totalizando 1.487 bolsas solicitadas**) produziu os seguintes resultados:

### 5.1. Tabela de Desabastecimento por Tipo Sanguíneo

| Tipo | Estoque Válido ($S$) | Demanda Média ($\lambda$) | Cobertura Pontual | Déficit Pontual | $\mathbb{P}(\text{Poisson})$ | $\mathbb{P}(\text{Normal})$ | $\mathbb{P}(\text{M. Carlo})$ | Nível de Risco | Diretriz de Ação |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|---|
| **A+** | 454 | 549 | 82,7% | 95 un | **100,0%** | **100,0%** | **100,0%** | **CRÍTICO** | Déficit massivo de 95 bolsas. Requer convocação de doadores e uso de O+. |
| **AB-** | 5 | 10 | 50,0% | 5 un | **93,3%** | **92,3%** | **93,2%** | **CRÍTICO** | Menor cobertura da rede (50%). Raridade biológica extrema. |
| **B+** | 105 | 118 | 89,0% | 13 un | **88,4%** | **88,7%** | **88,5%** | **CRÍTICO** | Risco de ruptura elevado; demanda supera estoque com frequência. |
| **AB+** | 30 | 44 | 68,2% | 14 un | **98,3%** | **98,1%** | **98,4%** | **CRÍTICO** | Cobertura de apenas 68%. Necessidade de remanejamento regional. |
| **O+** | 492 | 518 | 95,0% | 26 un | **87,1%** | **86,6%** | **87,0%** | **ALTO** | Maior volume da rede; déficit de 26 bolsas ameaça cirurgias gerais. |
| **A-** | 110 | 122 | 90,2% | 12 un | **86,4%** | **85,8%** | **86,3%** | **ALTO** | Tipo negativo escasso; requer monitoramento de requisições de urgência. |
| **B-** | 30 | 29 | 103,4% | 0 un | **38,4%** | **39,1%** | **38,6%** | **MODERADO** | **Atenção:** Cobertura > 100%, mas ainda há 38% de chance de desabastecimento! |
| **O-** | 119 | 97 | 122,7% | 0 un | **1,5%** | **1,4%** | **1,6%** | **BAIXO** | Superávit de 22 bolsas; atua como "pulmão" estratégico universal. |

---

### 5.2. A Grande Lição Estatística: Cobertura vs. Probabilidade de Desabastecimento

Observe atentamente o comportamento do grupo **B-**:
- **Cobertura pontual:** $\frac{30}{29} \times 100\% = 103,4\%$ (aparenta estar plenamente abastecido, com déficit zero).
- **Probabilidade de Desabastecimento:** **38,4%**!

> [!IMPORTANT]
> **Por que um estoque com cobertura maior que 100% ainda tem 38% de risco de faltar sangue?**  
> Porque a demanda é uma **variável aleatória estocástica**, não um número fixo. Como a média esperada é $\lambda = 29$, existe uma probabilidade de aproximadamente 38,4% de que, devido a flutuações amostrais e picos de demanda hospitalar, o hospital solicite 31, 32 ou mais bolsas.  
> Essa constatação científica justifica plenamente por que a gestão moderna de hemorredes **não pode confiar apenas na taxa de cobertura estática**, sendo imperativo calcular a probabilidade estocástica de desabastecimento.

---

### 5.3. Tabela de Desabastecimento por Hemocomponente

| Hemocomponente | Total Coletado | Bolsas Válidas | Bolsas Vencidas | Taxa de Descarte | Demanda $\lambda$ | $\mathbb{P}(\text{Desabastecimento})$ | Diagnóstico Operacional |
|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| **Concentrado de Plaquetas** | 407 | 40 | 367 | **90,2%** | 298 | **100,0%** | **Catástrofe de Validade (5 dias):** 90% venceu antes do uso; desabastecimento certo sem algoritmo FEFO. |
| **Concentrado de Hemácias** | 1.128 | 840 | 288 | **25,5%** | 818 | **22,8%** | **Equilíbrio Operacional:** Descarte moderado (42 dias de validade); estoque cobre a maior parte da demanda. |
| **Plasma Fresco Congelado** | 369 | 369 | 0 | **0,0%** | 298 | **0,002%** | **Segurança Total (365 dias):** Validade longa impede perdas; abastecimento estável. |
| **Crioprecipitado** | 96 | 96 | 0 | **0,0%** | 73 | **0,4%** | **Abastecido (365 dias):** Demanda pequena e validade longa garantem abastecimento seguro. |

---

## 6. Estrutura do Arquivo de Saída JSON (`painel_consolidado_pi3_32.json`)

O executável C++ exporta automaticamente todos os indicadores calculados para o arquivo `dados/painel_consolidado_pi3_32.json`, viabilizando o consumo pela API Java Spring Boot e a renderização nos painéis do frontend React:

```json
{
  "meta": {
    "projeto": "Rota Vital",
    "modulo": "PI3-32 - Painel Consolidado de Modelagem e Desabastecimento",
    "linguagem": "C++20",
    "fase_crisp_dm": "Fase 4 - Modelagem",
    "data_execucao": "2026-10-26T16:50:00-03:00",
    "reprodutibilidade_semente": 2026
  },
  "fundamentos_disciplinas": {
    "dados_acumulados_poo": "Dados acumulativos sao valores que vao sendo somados ou atualizados ao longo da execucao do programa, conforme novas informacoes sao recebidas. Exemplo: caixa de loja que adiciona o preco de cada produto ao total.",
    "saidas_algoritmos_estrutura_dados": "As saidas sao as informacoes que o programa apresenta ao usuario depois de executar uma instrucao ou processar dados. Pipeline: Entrada -> Processamento (Estruturas de Dados) -> Saida."
  },
  "modelagem_estatistica": {
    "distribuicao_escolhida": "DISTRIBUICAO_DE_POISSON (com extensao Normal via TCL e validacao por Monte Carlo)",
    "justificativa": "A demanda hospitalar por hemocomponentes constitui um processo estocastico pontual de contagem...",
    "metodologias_calculo": [
      "Poisson Exato (Processo pontual discreto com Poisson Thinning)",
      "Aproximacao Normal via Teorema Central do Limite com correcao de continuidade de Yates",
      "Simulacao Estocastica de Monte Carlo com 100.000 iteracoes reproduziveis"
    ]
  },
  "painel_consolidado": {
    "desabastecimento_por_tipo": [
      {
        "tipo_sanguineo": "A_POS",
        "nome_exibicao": "A+",
        "estoque_valido_disponivel": 454,
        "demanda_esperada_lambda": 549,
        "cobertura_pontual_pct": 82.7,
        "deficit_unidades": 95,
        "probabilidade_desabastecimento_poisson": 1.0,
        "nivel_risco": "CRITICO",
        "recomendacao_operacional": "EMERGENCIA DE SUPRIMENTO: Deficit severo. Convocar doadores compativeis ou alocar O-."
      }
    ]
  }
}
```

---

## 7. Como Compilar e Reproduzir em C++

A arquitetura foi desenvolvida em C++ padrão (C++20) sem dependências externas obrigatórias além do compilador `g++`:

### 7.1. Compilação e Execução via PowerShell:
```powershell
cd cpp
.\compile.ps1
```

### 7.2. Compilação e Execução via Prompt Batch (Windows):
```cmd
cd cpp
build.bat
```

### 7.3. Compilação Manual via g++:
```bash
# Compilar aplicacao principal
g++ -std=c++20 -O3 -I include src/*.cpp -o bin/rota_vital_modelagem.exe

# Executar
bin/rota_vital_modelagem.exe

# Compilar e rodar testes unitarios
g++ -std=c++20 -O3 -I include src/Dominio.cpp src/AcumuladorEstoque.cpp src/AcumuladorDemanda.cpp src/ModeloDemanda.cpp src/CalculadorDesabastecimento.cpp tests/TestesModelagem.cpp -o bin/testes_modelagem.exe
bin/testes_modelagem.exe
```

---

## 8. Conclusão

A modelagem estocástica desenvolvida em C++ cumpre com exatidão todos os critérios de aceite da história **PI3-32**:
1. **Modelagem Matemática Sólida:** Poisson e Normal (TCL) devidamente justificadas para a natureza discreta e assíncrona das requisições hospitalares;
2. **Avaliação Fiel Demanda vs. Estoque:** Exclusão sistemática de bolsas vencidas no cálculo do estoque válido;
3. **Cálculo Preciso da Probabilidade de Desabastecimento:** Concordância entre soluções analíticas e empíricas (Monte Carlo);
4. **Articulação Pedagógica:** Integração explícita dos conceitos de **Dados Acumulados (POO)** e **Saídas (AED)**;
5. **Prontidão para o Painel Consolidado:** Exportação automatizada para o formato JSON consumível pelo ecossistema do Rota Vital.
