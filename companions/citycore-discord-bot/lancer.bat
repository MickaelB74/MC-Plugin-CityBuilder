@echo off
rem Lance le bot Discord de CityCore depuis ce dossier (bot.properties et etat-bot.json y restent).
rem Construire d'abord le jar : gradlew build
chcp 65001 >nul
if not exist "build\libs\citycore-discord-bot-1.0.0.jar" (
    echo Jar introuvable : lancez d'abord "gradlew build" dans ce dossier.
    pause
    exit /b 1
)
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "build\libs\citycore-discord-bot-1.0.0.jar"
pause
