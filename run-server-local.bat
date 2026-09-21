@echo off
cd /d "%~dp0"

echo ============================
echo  Iniciando servidor Minecraft (local, sin tunel)
echo ============================
java -Xmx2G -Xms1G -jar spigot-26.3.jar nogui

echo.
echo El servidor se detuvo. Presiona una tecla para cerrar.
pause >nul
