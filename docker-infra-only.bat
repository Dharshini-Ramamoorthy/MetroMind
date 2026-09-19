@echo off
echo ===================================================
echo   Starting MetroMind KMRL Hybrid Dev Mode
echo   (Only Databases + Eureka Server in Docker)
echo ===================================================
docker compose up -d mongodb oracle-db eureka-server
echo.
echo Databases and Eureka are ready!
echo You can now launch and debug your services in STS and UI in VS Code.
pause
