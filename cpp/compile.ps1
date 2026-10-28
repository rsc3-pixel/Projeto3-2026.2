Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Compilando Sistema Rota Vital: Probabilidade de Desabastecimento C++ (PI3-102)" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

if (!(Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

Write-Host "Compilando aplicacao principal com g++ (C++20)..." -ForegroundColor Yellow
g++ -std=c++20 -O3 -I include src/Dominio.cpp src/AcumuladorEstoque.cpp src/AcumuladorDemanda.cpp src/ModeloDemanda.cpp src/CalculadorDesabastecimento.cpp src/PainelConsolidado.cpp src/LeitorDadosSinteticos.cpp src/main.cpp -o bin/rota_vital_modelagem.exe

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Falha na compilacao da aplicacao principal!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[SUCESSO] bin/rota_vital_modelagem.exe gerado com sucesso!" -ForegroundColor Green

Write-Host "Compilando testes automatizados..." -ForegroundColor Yellow
g++ -std=c++20 -O3 -I include src/Dominio.cpp src/AcumuladorEstoque.cpp src/AcumuladorDemanda.cpp src/ModeloDemanda.cpp src/CalculadorDesabastecimento.cpp tests/TestesModelagem.cpp -o bin/testes_modelagem.exe

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Falha na compilacao dos testes!" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[SUCESSO] bin/testes_modelagem.exe gerado com sucesso!" -ForegroundColor Green

Write-Host "`nExecutando testes unitarios..." -ForegroundColor Cyan
& "bin/testes_modelagem.exe"

Write-Host "`nExecutando aplicacao principal..." -ForegroundColor Cyan
& "bin/rota_vital_modelagem.exe"
