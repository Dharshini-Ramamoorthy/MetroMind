@echo off
setlocal

set JAVA_HOME=C:\sqldeveloper\jdk
set PATH=%JAVA_HOME%\bin;%PATH%

echo Java version:
java -version 2>&1

set BASE=C:\Users\DELL\Documents\kmrl_backend
set SERVICES=alert-service api-gateway approver-service eureka-server fleet-service maintenance-service report-service schedule-service user-service

set FAIL=0

for %%S in (%SERVICES%) do (
    echo.
    echo ==================================================================
    echo Building: %%S
    echo ==================================================================
    cd /d "%BASE%\%%S"
    call mvnw.cmd clean package -DskipTests -q
    if errorlevel 1 (
        echo [FAILED] %%S
        set FAIL=1
    ) else (
        echo [SUCCESS] %%S
    )
)

echo.
if "%FAIL%"=="1" (
    echo *** One or more services FAILED to build ***
) else (
    echo *** All services built successfully ***
)
endlocal
