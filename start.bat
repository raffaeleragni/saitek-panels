@echo off

set _SCRIPT_DRIVE=%~d0
set _SCRIPT_PATH=%~p0

%_SCRIPT_DRIVE%
cd %_SCRIPT_PATH%

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