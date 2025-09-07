@echo off
pushd %~dp0

REM limpa e recria pasta out
if exist out rd /s /q out
mkdir out

echo Compiling MoneyMiner with Spigot-API and Vault...
javac -classpath "spigot-api-1.21.6-R0.1-SNAPSHOT.jar;Vault.jar" -d out ^
src\com\foxsrv\moneyminer\Main.java ^
src\com\foxsrv\moneyminer\Miner.java ^
src\com\foxsrv\moneyminer\Storage.java

if errorlevel 1 (
    echo Erro na compilacao!
    pause
    popd
    exit /b 1
)

echo Copying resources...
copy /Y plugin.yml out >nul
copy /Y config.yml out >nul

echo Packing JAR...
cd out
jar cvf MoneyMiner.jar *
move /Y MoneyMiner.jar "%~dp0"

echo Build concluido! MoneyMiner.jar gerado na raiz.
pause
popd
