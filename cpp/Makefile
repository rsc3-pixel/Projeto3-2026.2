CXX = g++
CXXFLAGS = -std=c++20 -O3 -Wall -Wextra -I include
BIN_DIR = bin

SRC = src/Dominio.cpp \
      src/AcumuladorEstoque.cpp \
      src/AcumuladorDemanda.cpp \
      src/ModeloDemanda.cpp \
      src/CalculadorDesabastecimento.cpp \
      src/PainelConsolidado.cpp \
      src/LeitorDadosSinteticos.cpp

TEST_SRC = src/Dominio.cpp \
           src/AcumuladorEstoque.cpp \
           src/AcumuladorDemanda.cpp \
           src/ModeloDemanda.cpp \
           src/CalculadorDesabastecimento.cpp \
           tests/TestesModelagem.cpp

all: $(BIN_DIR)/rota_vital_modelagem.exe $(BIN_DIR)/testes_modelagem.exe

$(BIN_DIR):
	mkdir -p $(BIN_DIR)

$(BIN_DIR)/rota_vital_modelagem.exe: $(SRC) src/main.cpp | $(BIN_DIR)
	$(CXX) $(CXXFLAGS) $^ -o $@

$(BIN_DIR)/testes_modelagem.exe: $(TEST_SRC) | $(BIN_DIR)
	$(CXX) $(CXXFLAGS) $^ -o $@

test: $(BIN_DIR)/testes_modelagem.exe
	./$(BIN_DIR)/testes_modelagem.exe

run: $(BIN_DIR)/rota_vital_modelagem.exe
	./$(BIN_DIR)/rota_vital_modelagem.exe

clean:
	rm -rf $(BIN_DIR)

.PHONY: all test run clean
