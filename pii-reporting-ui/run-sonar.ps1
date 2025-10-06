#!/usr/bin/env pwsh
# Script PowerShell pour exécuter sonar-scanner sur le projet Angular

if (-not $Env:SONAR_QUBE_TOKEN) {
    Write-Error "La variable d'environnement SONAR_QUBE_TOKEN n'est pas définie"
    exit 1
}

Write-Host "Démarrage de sonar-scanner pour pii-reporting-ui (Angular)..." -ForegroundColor Green
sonar-scanner "-Dsonar.token=$Env:SONAR_QUBE_TOKEN"
