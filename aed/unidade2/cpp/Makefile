# Rota Vital - AED Unidade 2
# Compilacao da fila FEFO por insercao ordenada (W10) e da roteirizacao
# por vizinho mais proximo, o indice de estoque por hash e a matriz de
# compatibilidade ABO/Rh (W12), em C++.

CXX      = g++
CXXFLAGS = -Wall -Wextra -std=c++17
ALVO     = rota_vital_u2
OBJETOS  = main.o fila_fefo.o grafo.o dijkstra.o vizinho_mais_proximo.o \
           indice_estoque.o matriz_compatibilidade.o

# Alvo padrao: e o primeiro do arquivo, entao "make" sozinho gera o executavel.
all: $(ALVO)

$(ALVO): $(OBJETOS)
	$(CXX) $(CXXFLAGS) -o $(ALVO) $(OBJETOS)

# Regra generica: cada .cpp vira o .o de mesmo nome.
%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

# Dependencias de cabecalho: sem estas linhas, alterar um .hpp nao
# recompilaria os .cpp que o incluem, e o executavel ficaria com codigo
# desatualizado.
main.o:                  main.cpp fila_fefo.hpp dominio.hpp grafo.hpp dijkstra.hpp vizinho_mais_proximo.hpp indice_estoque.hpp matriz_compatibilidade.hpp
fila_fefo.o:             fila_fefo.cpp fila_fefo.hpp dominio.hpp
grafo.o:                 grafo.cpp grafo.hpp dominio.hpp
dijkstra.o:              dijkstra.cpp dijkstra.hpp grafo.hpp dominio.hpp
vizinho_mais_proximo.o:  vizinho_mais_proximo.cpp vizinho_mais_proximo.hpp dijkstra.hpp grafo.hpp dominio.hpp
indice_estoque.o:        indice_estoque.cpp indice_estoque.hpp matriz_compatibilidade.hpp dominio.hpp
matriz_compatibilidade.o: matriz_compatibilidade.cpp matriz_compatibilidade.hpp

# Remove os objetos e o executavel, deixando so os fontes.
clean:
	rm -f $(OBJETOS) $(ALVO)

# Avisa ao make que estes alvos sao comandos, nao arquivos a serem gerados.
.PHONY: all clean
