@echo off

set JARFILE=target/saitekpanel-1.0-SNAPSHOT-jar-with-dependencies.jar

if not exist %JARFILE% (
mvn package
@echo.
@echo.
@echo.
@echo RESTART PROGRAM
pause
)

java -jar %JARFILE%