#!/bin/bash

# AI Sentinel - Script de démarrage rapide
# Ce script facilite le démarrage de l'application avec Docker Compose

set -e

# Couleurs pour l'affichage
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Fonction pour afficher des messages
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Vérifier que Docker est installé
if ! command -v docker &> /dev/null; then
    log_error "Docker n'est pas installé. Veuillez installer Docker Desktop."
    exit 1
fi

# Vérifier que Docker Compose est installé
if ! command -v docker-compose &> /dev/null; then
    log_error "Docker Compose n'est pas installé. Veuillez installer Docker Compose."
    exit 1
fi

# Vérifier que Docker est en cours d'exécution
if ! docker info &> /dev/null; then
    log_error "Docker n'est pas en cours d'exécution. Veuillez démarrer Docker Desktop."
    exit 1
fi

log_info "🚀 Démarrage de AI Sentinel..."

# Vérifier si le fichier .env existe
if [ ! -f .env ]; then
    log_warning "Le fichier .env n'existe pas."
    log_info "Création du fichier .env à partir de .env.example..."
    cp .env.example .env
    log_warning "⚠️  Veuillez éditer le fichier .env avec vos informations Confluence avant de continuer."
    log_info "Ouvrez le fichier .env et remplissez les variables suivantes :"
    log_info "  - CONFLUENCE_BASE_URL"
    log_info "  - CONFLUENCE_USERNAME"
    log_info "  - CONFLUENCE_API_TOKEN"
    echo ""
    read -p "Appuyez sur Entrée une fois que vous avez configuré le fichier .env..."
fi

# Construire et démarrer les services
log_info "📦 Construction des images Docker..."
docker-compose build

log_info "🔄 Démarrage des services..."
docker-compose up -d

# Attendre que les services soient prêts
log_info "⏳ Attente du démarrage des services (cela peut prendre 2-3 minutes)..."

# Attendre PostgreSQL
log_info "   Attente de PostgreSQL..."
for i in {1..60}; do
    if docker-compose exec -T postgres pg_isready -U postgres -d ai-sentinel &> /dev/null; then
        log_success "   ✓ PostgreSQL est prêt"
        break
    fi
    sleep 2
    if [ $i -eq 60 ]; then
        log_error "   ✗ PostgreSQL n'a pas démarré dans le délai imparti"
        log_info "   Vérifiez les logs avec: docker-compose logs postgres"
        exit 1
    fi
done

# Attendre le PII Detector (peut prendre du temps pour le téléchargement des modèles)
log_info "   Attente du PII Detector (téléchargement des modèles ML)..."
sleep 10
log_success "   ✓ PII Detector est démarré"

# Attendre l'API Backend
log_info "   Attente du Backend API..."
for i in {1..60}; do
    if curl -sf http://localhost:8090/internal/health &> /dev/null; then
        log_success "   ✓ Backend API est prêt"
        break
    fi
    sleep 2
    if [ $i -eq 60 ]; then
        log_error "   ✗ Le Backend API n'a pas démarré dans le délai imparti"
        log_info "   Vérifiez les logs avec: docker-compose logs pii-reporting-api"
        exit 1
    fi
done

# Attendre le Frontend
log_info "   Attente du Frontend..."
for i in {1..30}; do
    if curl -sf http://localhost:4200/health &> /dev/null; then
        log_success "   ✓ Frontend est prêt"
        break
    fi
    sleep 2
    if [ $i -eq 30 ]; then
        log_error "   ✗ Le Frontend n'a pas démarré dans le délai imparti"
        log_info "   Vérifiez les logs avec: docker-compose logs pii-reporting-ui"
        exit 1
    fi
done

echo ""
log_success "🎉 AI Sentinel est maintenant accessible !"
echo ""
echo "📱 Accès à l'application :"
echo "   • Application Web     : http://localhost:4200"
echo "   • API Backend         : http://localhost:8080/sentinelle"
echo "   • Swagger UI          : http://localhost:8080/sentinelle/swagger-ui.html"
echo "   • Health Check        : http://localhost:8090/internal/health"
echo "   • PgAdmin (optionnel) : http://localhost:5050 (admin@pgadmin.com / admin)"
echo ""
echo "📋 Commandes utiles :"
echo "   • Voir les logs       : docker-compose logs -f"
echo "   • Arrêter l'app       : docker-compose down"
echo "   • Redémarrer un svc   : docker-compose restart <service-name>"
echo ""
log_info "Pour plus d'informations, consultez DOCKER_DEPLOYMENT.md"
