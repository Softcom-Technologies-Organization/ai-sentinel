# Script de lancement AI Sentinel - Production (Windows)
# Ce script télécharge et lance AI Sentinel sans avoir besoin de cloner le dépôt

$ErrorActionPreference = "Stop"

Write-Host "🚀 AI Sentinel - Lancement en production" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier si Docker est installé
try {
    $null = docker --version
} catch {
    Write-Host "❌ Docker n'est pas installé. Veuillez installer Docker Desktop: https://www.docker.com/products/docker-desktop" -ForegroundColor Red
    exit 1
}

# Vérifier si Docker Compose est installé
$dockerComposeV2 = $false
try {
    $null = docker compose version
    $dockerComposeV2 = $true
} catch {
    try {
        $null = docker-compose --version
    } catch {
        Write-Host "❌ Docker Compose n'est pas installé. Veuillez installer Docker Compose: https://docs.docker.com/compose/install/" -ForegroundColor Red
        exit 1
    }
}

# Créer le répertoire de travail
$WorkDir = Join-Path $env:USERPROFILE ".ai-sentinel"
if (-not (Test-Path $WorkDir)) {
    New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
}
Set-Location $WorkDir

Write-Host "📥 Téléchargement de la configuration Docker Compose..." -ForegroundColor Yellow
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/Softcom-Technologies-Organization/ai-sentinel/main/docker-compose.prod.yml" -OutFile "docker-compose.prod.yml"

Write-Host "📥 Téléchargement du fichier d'exemple de configuration..." -ForegroundColor Yellow
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/Softcom-Technologies-Organization/ai-sentinel/main/.env.example" -OutFile ".env.example"

# Vérifier si le fichier .env existe
if (-not (Test-Path ".env")) {
    Write-Host ""
    Write-Host "⚙️  Fichier .env non trouvé. Création à partir de .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
    Write-Host ""
    Write-Host "⚠️  IMPORTANT: Veuillez éditer le fichier .env avec vos credentials Confluence:" -ForegroundColor Yellow
    Write-Host "   $WorkDir\.env" -ForegroundColor White
    Write-Host ""
    Write-Host "Appuyez sur Entrée une fois que vous avez configuré le fichier .env..." -ForegroundColor Yellow
    $null = Read-Host
}

Write-Host ""
Write-Host "🐳 Démarrage des conteneurs Docker..." -ForegroundColor Green
Write-Host "   (Les images seront téléchargées automatiquement si nécessaire)" -ForegroundColor Gray
Write-Host ""

# Utiliser docker compose (v2) ou docker-compose (v1)
if ($dockerComposeV2) {
    docker compose -f docker-compose.prod.yml up -d
} else {
    docker-compose -f docker-compose.prod.yml up -d
}

Write-Host ""
Write-Host "✅ AI Sentinel est en cours de démarrage!" -ForegroundColor Green
Write-Host ""
Write-Host "📊 Vérification du statut des services..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

if ($dockerComposeV2) {
    docker compose -f docker-compose.prod.yml ps
} else {
    docker-compose -f docker-compose.prod.yml ps
}

Write-Host ""
Write-Host "🎉 AI Sentinel est maintenant accessible sur:" -ForegroundColor Green
Write-Host "   📱 Interface Web: http://localhost:4200" -ForegroundColor White
Write-Host "   🔌 API Backend:  http://localhost:8080/sentinelle" -ForegroundColor White
Write-Host "   📈 Metrics:      http://localhost:8090/internal/metrics" -ForegroundColor White
Write-Host ""
Write-Host "📝 Pour voir les logs:" -ForegroundColor Cyan
if ($dockerComposeV2) {
    Write-Host "   docker compose -f $WorkDir\docker-compose.prod.yml logs -f" -ForegroundColor White
} else {
    Write-Host "   docker-compose -f $WorkDir\docker-compose.prod.yml logs -f" -ForegroundColor White
}
Write-Host ""
Write-Host "🛑 Pour arrêter l'application:" -ForegroundColor Cyan
if ($dockerComposeV2) {
    Write-Host "   docker compose -f $WorkDir\docker-compose.prod.yml down" -ForegroundColor White
} else {
    Write-Host "   docker-compose -f $WorkDir\docker-compose.prod.yml down" -ForegroundColor White
}
Write-Host ""
