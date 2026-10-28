@echo off
setlocal
set "GRADLE_VERSION=8.9"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_ROOT=%GRADLE_USER_HOME%\cafeteria-gradle-distributions"
set "GRADLE_HOME=%DIST_ROOT%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
if exist "%GRADLE_BIN%" goto runGradle
where powershell >nul 2>&1
if errorlevel 1 (
  echo Se necesita PowerShell para descargar Gradle %GRADLE_VERSION%.
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $root=Join-Path $env:GRADLE_USER_HOME 'cafeteria-gradle-distributions'; New-Item -ItemType Directory -Force -Path $root | Out-Null; $zip=Join-Path $root 'gradle-%GRADLE_VERSION%-bin.zip'; Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath $root -Force; Remove-Item $zip -Force"
if errorlevel 1 exit /b 1
:runGradle
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
