# Rota Vital - AED Unidade 1
# Compilacao das estruturas basicas em C++.

CXX      = g++
CXXFLAGS = -Wall -Wextra -std=c++17
ALVO     = rota_vital_u1
OBJETOS  = main.o lista_estoque.o fila_requisicoes.o pilha_historico.o

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
main.o:             main.cpp dominio.hpp lista_estoque.hpp fila_requisicoes.hpp pilha_historico.hpp
lista_estoque.o:    lista_estoque.cpp lista_estoque.hpp dominio.hpp
fila_requisicoes.o: fila_requisicoes.cpp fila_requisicoes.hpp dominio.hpp
pilha_historico.o:  pilha_historico.cpp pilha_historico.hpp dominio.hpp

# Remove os objetos e o executavel, deixando so os fontes.
clean:
	rm -f $(OBJETOS) $(ALVO)

# Avisa ao make que estes alvos sao comandos, nao arquivos a serem gerados.
.PHONY: all clean
