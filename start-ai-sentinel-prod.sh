#!/bin/bash
# Script de lancement AI Sentinel - Production
# Ce script télécharge et lance AI Sentinel sans avoir besoin de cloner le dépôt

set -e

echo "🚀 AI Sentinel - Lancement en production"
echo "=========================================="
echo ""

# Vérifier si Docker est installé
if ! command -v docker &> /dev/null; then
    echo "❌ Docker n'est pas installé. Veuillez installer Docker Desktop: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Vérifier si Docker Compose est installé
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "❌ Docker Compose n'est pas installé. Veuillez installer Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

# Créer le répertoire de travail
WORK_DIR="${HOME}/.ai-sentinel"
mkdir -p "${WORK_DIR}"
cd "${WORK_DIR}"

echo "📥 Téléchargement de la configuration Docker Compose..."
curl -fsSL https://raw.githubusercontent.com/Softcom-Technologies-Organization/ai-sentinel/main/docker-compose.prod.yml -o docker-compose.prod.yml

echo "📥 Téléchargement du fichier d'exemple de configuration..."
curl -fsSL https://raw.githubusercontent.com/Softcom-Technologies-Organization/ai-sentinel/main/.env.example -o .env.example

# Vérifier si le fichier .env existe
if [ ! -f .env ]; then
    echo ""
    echo "⚙️  Fichier .env non trouvé. Création à partir de .env.example..."
    cp .env.example .env
    echo ""
    echo "⚠️  IMPORTANT: Veuillez éditer le fichier .env avec vos credentials Confluence:"
    echo "   ${WORK_DIR}/.env"
    echo ""
    read -p "Appuyez sur Entrée une fois que vous avez configuré le fichier .env..."
fi

echo ""
echo "🐳 Démarrage des conteneurs Docker..."
echo "   (Les images seront téléchargées automatiquement si nécessaire)"
echo ""

# Utiliser docker compose (v2) ou docker-compose (v1)
if docker compose version &> /dev/null; then
    docker compose -f docker-compose.prod.yml up -d
else
    docker-compose -f docker-compose.prod.yml up -d
fi

echo ""
echo "✅ AI Sentinel est en cours de démarrage!"
echo ""
echo "📊 Vérification du statut des services..."
sleep 5

if docker compose version &> /dev/null; then
    docker compose -f docker-compose.prod.yml ps
else
    docker-compose -f docker-compose.prod.yml ps
fi

echo ""
echo "🎉 AI Sentinel est maintenant accessible sur:"
echo "   📱 Interface Web: http://localhost:4200"
echo "   🔌 API Backend:  http://localhost:8080/sentinelle"
echo "   📈 Metrics:      http://localhost:8090/internal/metrics"
echo ""
echo "📝 Pour voir les logs:"
if docker compose version &> /dev/null; then
    echo "   docker compose -f ${WORK_DIR}/docker-compose.prod.yml logs -f"
else
    echo "   docker-compose -f ${WORK_DIR}/docker-compose.prod.yml logs -f"
fi
echo ""
echo "🛑 Pour arrêter l'application:"
if docker compose version &> /dev/null; then
    echo "   docker compose -f ${WORK_DIR}/docker-compose.prod.yml down"
else
    echo "   docker-compose -f ${WORK_DIR}/docker-compose.prod.yml down"
fi
echo ""
