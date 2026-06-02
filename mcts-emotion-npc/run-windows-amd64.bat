@echo off
cd /d %~dp0
java -Djava.library.path="lib/lwjgl/natives/windows/amd64" -cp "bin;lib/*;lib/lwjgl/*" Main
pause
