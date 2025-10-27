# AI Sentinel - Script de démarrage rapide (Windows PowerShell)
# Ce script facilite le démarrage de l'application avec Docker Compose

$ErrorActionPreference = "Stop"

# Fonction pour afficher des messages avec couleurs
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning-Custom {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# Vérifier que Docker est installé
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error-Custom "Docker n'est pas installé. Veuillez installer Docker Desktop."
    exit 1
}

# Vérifier que Docker Compose est installé
if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Error-Custom "Docker Compose n'est pas installé. Veuillez installer Docker Compose."
    exit 1
}

# Vérifier que Docker est en cours d'exécution
try {
    docker info | Out-Null
} catch {
    Write-Error-Custom "Docker n'est pas en cours d'exécution. Veuillez démarrer Docker Desktop."
    exit 1
}

Write-Info "🚀 Démarrage de AI Sentinel..."

# Vérifier si le fichier .env existe
if (-not (Test-Path .env)) {
    Write-Warning-Custom "Le fichier .env n'existe pas."
    Write-Info "Création du fichier .env à partir de .env.example..."
    Copy-Item .env.example .env
    Write-Warning-Custom "⚠️  Veuillez éditer le fichier .env avec vos informations Confluence avant de continuer."
    Write-Info "Ouvrez le fichier .env et remplissez les variables suivantes :"
    Write-Info "  - CONFLUENCE_BASE_URL"
    Write-Info "  - CONFLUENCE_USERNAME"
    Write-Info "  - CONFLUENCE_API_TOKEN"
    Write-Host ""
    Read-Host "Appuyez sur Entrée une fois que vous avez configuré le fichier .env"
}

# Construire et démarrer les services
Write-Info "📦 Construction des images Docker..."
docker-compose -f docker-compose.dev.yml build

Write-Info "🔄 Démarrage des services..."
docker-compose -f docker-compose.dev.yml up -d

# Attendre que les services soient prêts
Write-Info "⏳ Attente du démarrage des services (cela peut prendre 2-3 minutes)..."

# Attendre PostgreSQL
Write-Info "   Attente de PostgreSQL..."
$pgReady = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $result = docker-compose -f docker-compose.dev.yml exec -T postgres pg_isready -U postgres -d ai-sentinel 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Success "   ✓ PostgreSQL est prêt"
            $pgReady = $true
            break
        }
    } catch {
        # Continue waiting
    }
    Start-Sleep -Seconds 2
}

if (-not $pgReady) {
    Write-Error-Custom "   ✗ PostgreSQL n'a pas démarré dans le délai imparti"
    Write-Info "   Vérifiez les logs avec: docker-compose -f docker-compose.dev.yml logs postgres"
    exit 1
}

# Attendre le PII Detector (peut prendre du temps pour le téléchargement des modèles)
Write-Info "   Attente du PII Detector (téléchargement des modèles ML)..."
Start-Sleep -Seconds 10
Write-Success "   ✓ PII Detector est démarré"

# Attendre l'API Backend
Write-Info "   Attente du Backend API..."
$apiReady = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8090/internal/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            Write-Success "   ✓ Backend API est prêt"
            $apiReady = $true
            break
        }
    } catch {
        # Continue waiting
    }
    Start-Sleep -Seconds 2
}

if (-not $apiReady) {
    Write-Error-Custom "   ✗ Le Backend API n'a pas démarré dans le délai imparti"
    Write-Info "   Vérifiez les logs avec: docker-compose -f docker-compose.dev.yml logs pii-reporting-api"
    exit 1
}

# Attendre le Frontend
Write-Info "   Attente du Frontend..."
$uiReady = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:4200/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            Write-Success "   ✓ Frontend est prêt"
            $uiReady = $true
            break
        }
    } catch {
        # Continue waiting
    }
    Start-Sleep -Seconds 2
}

if (-not $uiReady) {
    Write-Error-Custom "   ✗ Le Frontend n'a pas démarré dans le délai imparti"
    Write-Info "   Vérifiez les logs avec: docker-compose -f docker-compose.dev.yml logs pii-reporting-ui"
    exit 1
}

Write-Host ""
Write-Success "🎉 AI Sentinel est maintenant accessible !"
Write-Host ""
Write-Host "📱 Accès à l'application :" -ForegroundColor White
Write-Host "   • Application Web     : http://localhost:4200" -ForegroundColor White
Write-Host "   • API Backend         : http://localhost:8080/sentinelle" -ForegroundColor White
Write-Host "   • Swagger UI          : http://localhost:8080/sentinelle/swagger-ui.html" -ForegroundColor White
Write-Host "   • Health Check        : http://localhost:8090/internal/health" -ForegroundColor White
Write-Host "   • PgAdmin (optionnel) : http://localhost:5050 (admin@pgadmin.com / admin)" -ForegroundColor White
Write-Host ""
Write-Host "📋 Commandes utiles :" -ForegroundColor White
Write-Host "   • Voir les logs       : docker-compose -f docker-compose.dev.yml logs -f" -ForegroundColor White
Write-Host "   • Arrêter l'app       : docker-compose -f docker-compose.dev.yml down" -ForegroundColor White
Write-Host "   • Redémarrer un svc   : docker-compose -f docker-compose.dev.yml restart <service-name>" -ForegroundColor White
Write-Host ""
Write-Info "Pour plus d'informations, consultez DOCKER_DEPLOYMENT.md"
