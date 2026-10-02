@echo off
start "" "C:\Users\DUY\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe"
timeout /t 5
tasklist | findstr /i docker
