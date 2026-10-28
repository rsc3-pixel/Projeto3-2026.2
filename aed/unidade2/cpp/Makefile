# Rota Vital - AED Unidade 2
# Compilacao da fila FEFO por insercao ordenada em C++.

CXX      = g++
CXXFLAGS = -Wall -Wextra -std=c++17
ALVO     = rota_vital_u2
OBJETOS  = main.o fila_fefo.o

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
main.o:      main.cpp fila_fefo.hpp dominio.hpp
fila_fefo.o: fila_fefo.cpp fila_fefo.hpp dominio.hpp

# Remove os objetos e o executavel, deixando so os fontes.
clean:
	rm -f $(OBJETOS) $(ALVO)

# Avisa ao make que estes alvos sao comandos, nao arquivos a serem gerados.
.PHONY: all clean
