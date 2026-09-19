@echo off
echo ===================================================
echo   Starting MetroMind KMRL - Full Stack Containers
echo ===================================================
docker compose up -d --build
echo.
echo All services launched!
echo Access MetroMind UI at: http://localhost:5173 or http://localhost
echo Access API Gateway at:   http://localhost:8080
echo Access Eureka Portal at: http://localhost:8761
pause
