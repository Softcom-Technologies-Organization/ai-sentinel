#!/usr/bin/env pwsh
# Script PowerShell pour exécuter sonar-scanner
# Activation du virtual environment
& ".\.venv\Scripts\Activate.ps1"
if (-not $Env:SONAR_QUBE_TOKEN) {
    Write-Error "La variable d'environnement SONAR_QUBE_TOKEN n'est pas définie"
    exit 1
}

Write-Host "Exécution des tests unitaires avec coverage..." -ForegroundColor Green
pytest tests/unit --cov=pii_detector --cov-report=html --cov-report=term-missing --cov-report=xml

if ($LASTEXITCODE -ne 0) {
    Write-Error "Les tests ont échoué"
    exit 1
}

Write-Host "Tests réussis ! Démarrage de sonar-scanner..." -ForegroundColor Green
sonar-scanner "-Dsonar.token=$Env:SONAR_QUBE_TOKEN"
