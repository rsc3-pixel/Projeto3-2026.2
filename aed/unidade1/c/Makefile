# Rota Vital - AED Unidade 1
# Compilacao das estruturas basicas em C.

CC      = gcc
CFLAGS  = -Wall -Wextra -std=c11
ALVO    = rota_vital_u1
OBJETOS = main.o lista_estoque.o fila_requisicoes.o pilha_historico.o

# Alvo padrao: e o primeiro do arquivo, entao "make" sozinho gera o executavel.
all: $(ALVO)

$(ALVO): $(OBJETOS)
	$(CC) $(CFLAGS) -o $(ALVO) $(OBJETOS)

# Regra generica: cada .c vira o .o de mesmo nome.
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

# Dependencias de cabecalho: sem estas linhas, alterar um .h nao recompilaria
# os .c que o incluem, e o executavel ficaria com codigo desatualizado.
main.o:             main.c dominio.h lista_estoque.h fila_requisicoes.h pilha_historico.h
lista_estoque.o:    lista_estoque.c lista_estoque.h dominio.h
fila_requisicoes.o: fila_requisicoes.c fila_requisicoes.h dominio.h
pilha_historico.o:  pilha_historico.c pilha_historico.h dominio.h

# Remove os objetos e o executavel, deixando so os fontes.
clean:
	rm -f $(OBJETOS) $(ALVO)

# Avisa ao make que estes alvos sao comandos, nao arquivos a serem gerados.
.PHONY: all clean
