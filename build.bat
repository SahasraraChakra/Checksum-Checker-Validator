@echo off
rem Requires JDK 26 on PATH
if exist out rmdir /s /q out
mkdir out
javac --release 26 -d out src\hashcheck\*.java || exit /b 1
jar --create --file ChecksumChecker.jar --main-class hashcheck.Main -C out .
echo Built ChecksumChecker.jar
