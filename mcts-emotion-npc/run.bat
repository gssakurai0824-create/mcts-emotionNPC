@echo off
cd /d "%~dp0"

echo 実験を開始します...
echo Player 1: MctsAi23iEmotion
echo Player 2: MctsAi23i
echo HP Limit: 1000

java -cp "./lib/lwjgl/*;./lib/*;./bin" -Djava.library.path="./lib/lwjgl/natives/windows/amd64" Main --a1 MctsAi23iEmotion --a2 MctsAi23i --limithp 1000 1000

pause