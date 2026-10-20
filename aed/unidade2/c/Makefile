# Rota Vital - AED Unidade 2
# Compilacao da fila FEFO por insercao ordenada em C.

CC      = gcc
CFLAGS  = -Wall -Wextra -std=c11
ALVO    = rota_vital_u2
OBJETOS = main.o fila_fefo.o

# Alvo padrao: e o primeiro do arquivo, entao "make" sozinho gera o executavel.
all: $(ALVO)

$(ALVO): $(OBJETOS)
	$(CC) $(CFLAGS) -o $(ALVO) $(OBJETOS)

# Regra generica: cada .c vira o .o de mesmo nome.
%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

# Dependencias de cabecalho: sem estas linhas, alterar um .h nao recompilaria
# os .c que o incluem, e o executavel ficaria com codigo desatualizado.
main.o:      main.c fila_fefo.h dominio.h
fila_fefo.o: fila_fefo.c fila_fefo.h dominio.h

# Remove os objetos e o executavel, deixando so os fontes.
clean:
	rm -f $(OBJETOS) $(ALVO)

# Avisa ao make que estes alvos sao comandos, nao arquivos a serem gerados.
.PHONY: all clean
