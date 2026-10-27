@echo off
echo ==============================================================================
echo Compilando Sistema Rota Vital: Modelagem e Desabastecimento C++ (PI3-32)
echo ==============================================================================

if not exist bin mkdir bin

echo Compilando aplicacao principal...
g++ -std=c++20 -O3 -I include src/Dominio.cpp src/AcumuladorEstoque.cpp src/AcumuladorDemanda.cpp src/ModeloDemanda.cpp src/CalculadorDesabastecimento.cpp src/PainelConsolidado.cpp src/LeitorDadosSinteticos.cpp src/main.cpp -o bin/rota_vital_modelagem.exe
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] Falha na compilacao da aplicacao principal!
    exit /b %ERRORLEVEL%
)
echo [SUCESSO] Executavel gerado em bin/rota_vital_modelagem.exe

echo.
echo Compilando suite de testes unitarios...
g++ -std=c++20 -O3 -I include src/Dominio.cpp src/AcumuladorEstoque.cpp src/AcumuladorDemanda.cpp src/ModeloDemanda.cpp src/CalculadorDesabastecimento.cpp tests/TestesModelagem.cpp -o bin/testes_modelagem.exe
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] Falha na compilacao dos testes!
    exit /b %ERRORLEVEL%
)
echo [SUCESSO] Executavel de testes gerado em bin/testes_modelagem.exe

echo.
echo Executando testes unitarios...
bin\testes_modelagem.exe

echo.
echo ==============================================================================
echo Compilacao e testes concluidos com exito!
echo Para rodar o programa principal:
echo   bin\rota_vital_modelagem.exe
echo ==============================================================================
