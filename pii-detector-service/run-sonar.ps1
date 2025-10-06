#!/usr/bin/env pwsh
# Script PowerShell pour exécuter sonar-scanner

if (-not $Env:SONAR_QUBE_TOKEN) {
    Write-Error "La variable d'environnement SONAR_QUBE_TOKEN n'est pas définie"
    exit 1
}

Write-Host "Démarrage de sonar-scanner..." -ForegroundColor Green
sonar-scanner "-Dsonar.token=$Env:SONAR_QUBE_TOKEN"
