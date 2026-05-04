#!/usr/bin/env bash
# postCreateCommand — exécuté une fois après création du dev container.
# Idempotent : peut être relancé sans casser quoi que ce soit.
set -euo pipefail

echo "=== ai-sentinel dev container setup ==="

# Permissions sur les volumes nommés (créés en root par défaut par Docker au premier mount).
# IMPORTANT : on NE masque PAS les erreurs — un échec ici cascade en EACCES sur pnpm/maven/pip.
echo ">> Correction ownership des volumes nommés"
sudo mkdir -p \
  /home/vscode/.claude \
  /home/vscode/.m2 \
  /home/vscode/.npm \
  /home/vscode/.cache/pip
sudo chown -R vscode:vscode \
  /home/vscode/.claude \
  /home/vscode/.m2 \
  /home/vscode/.npm \
  /home/vscode/.cache/pip

# Volume nommé node_modules : mount en root par défaut. chown RÉCURSIF obligatoire :
# si le volume contient déjà du contenu (rebuild après échec), seul -R corrige les sous-dirs
# créés par pnpm (.pnpm, .modules.yaml, etc.).
NODE_MODULES_DIR=/workspaces/ai-sentinel/pii-reporting-ui/node_modules
sudo mkdir -p "$NODE_MODULES_DIR"
sudo chown -R vscode:vscode "$NODE_MODULES_DIR"

cd /workspaces/ai-sentinel

# --- Java/Maven : précharger les dépendances en offline ---
if [ -f pii-reporting-api/pom.xml ]; then
  echo ">> Maven dependency:go-offline (pii-reporting-api)"
  (cd pii-reporting-api && mvn -B -q dependency:go-offline -DskipTests) || \
    echo "!! Maven prefetch a échoué — non bloquant, sera retenté au build"
fi

# --- Python : venv local détectable par IntelliJ Gateway (auto-config du SDK projet) ---
# Pourquoi un venv et pas pip --user : IntelliJ Gateway détecte automatiquement les venv
# situés à la racine d'un module Python et propose de les enregistrer comme SDK projet.
# Sans venv, le SDK nommé "Python 3.13" référencé par .run/server.run.xml ne résout rien
# et on tombe sur "<No interpreter>" dans le run config dialog.
if [ -f pii-detector-service/pyproject.toml ]; then
  VENV_DIR=pii-detector-service/.venv
  # Test : le venv existant est-il un vrai venv Linux fonctionnel ?
  # Cas problématique : dev qui ouvre le projet sous Windows hors conteneur, crée un venv
  # local (Scripts/python.exe), puis ouvre via Gateway → le bind-mount expose ce venv
  # Windows que python3.13 ne sait pas réutiliser. On wipe et on recrée.
  if [ ! -x "$VENV_DIR/bin/python" ] || ! "$VENV_DIR/bin/python" --version >/dev/null 2>&1; then
    if [ -d "$VENV_DIR" ]; then
      echo ">> Venv $VENV_DIR invalide ou non-Linux → suppression"
      rm -rf "$VENV_DIR"
    fi
    echo ">> Création du venv $VENV_DIR (Python 3.13)"
    python3.13 -m venv "$VENV_DIR" || python3 -m venv "$VENV_DIR"
  fi
  echo ">> pip install pii-detector-service[dev] (dans $VENV_DIR)"
  "$VENV_DIR/bin/pip" install --upgrade pip
  # --extra-index-url torch CPU : recommandé par pyproject.toml:31 pour éviter de tirer
  # les wheels CUDA (plusieurs Go inutiles dans un dev container).
  # Pas de 2>/dev/null : on veut voir les erreurs si l'install plante (cas vécu :
  # transformers/torch absents → server.py crash au démarrage avec "No module named transformers").
  if ! (cd pii-detector-service && .venv/bin/pip install -e ".[dev]" \
        --extra-index-url https://download.pytorch.org/whl/cpu); then
    echo "!! pip install -e .[dev] a échoué — fallback sur install sans extras"
    (cd pii-detector-service && .venv/bin/pip install -e . \
        --extra-index-url https://download.pytorch.org/whl/cpu) || \
      echo "!! pip install minimal a aussi échoué — investigation manuelle requise"
  fi
fi

# --- Angular : pnpm via Corepack (le projet a un pnpm-lock.yaml et "packageManager": "pnpm@10.12.1") ---
if [ -f pii-reporting-ui/package.json ]; then
  echo ">> Activation pnpm via Corepack"
  corepack enable >/dev/null 2>&1 || sudo corepack enable
  (cd pii-reporting-ui && corepack prepare pnpm@10.12.1 --activate)
  echo ">> pnpm install (pii-reporting-ui)"
  (cd pii-reporting-ui && pnpm install --frozen-lockfile) || \
    echo "!! pnpm install a échoué — vérifier manuellement dans pii-reporting-ui/"
fi

# --- Angular : run config dédiée au dev container ---
# Pourquoi : la run config par défaut "start" lance `ng serve` qui bind sur 127.0.0.1
# DANS le container. Le port-forwarding du devcontainer (cf. devcontainer.json forwardPorts)
# ne peut pas joindre cette interface — il a besoin que le serveur écoute sur 0.0.0.0.
# Symptôme : http://localhost:4200 sur l'hôte → ERR_EMPTY_RESPONSE.
# Solution : générer une config "start (devcontainer)" qui exécute le script `start:dev`
# (déjà défini dans pii-reporting-ui/package.json avec --host 0.0.0.0).
# .run/ est dans .gitignore donc ce fichier reste local au container.
RUN_CONFIG=".run/start (devcontainer).run.xml"
if [ -f pii-reporting-ui/package.json ] && [ ! -f "$RUN_CONFIG" ]; then
  echo ">> Génération run config Angular dédiée devcontainer ($RUN_CONFIG)"
  mkdir -p .run
  cat > "$RUN_CONFIG" <<'EOF'
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="start (devcontainer)" type="js.build_tools.npm">
    <package-json value="$PROJECT_DIR$/pii-reporting-ui/package.json" />
    <command value="run" />
    <scripts>
      <script value="start:dev" />
    </scripts>
    <node-interpreter value="project" />
    <envs />
    <method v="2" />
  </configuration>
</component>
EOF
fi

# --- Claude Code : info connexion ---
echo ""
echo "=== Claude Code ==="
echo "Première utilisation : lance 'claude' puis suis le flow OAuth (abonnement Anthropic)."
echo "Les credentials sont persistés dans le volume 'ai-sentinel-claude' → pas besoin de re-loginer après rebuild."
echo ""
echo "=== Setup terminé ==="
echo "Postgres : DB_HOST=postgres DB_PORT=5433 (depuis le workspace via DNS interne, pas d'expo hôte)"
echo "pgAdmin  : http://localhost:5050 (admin@local / admin)"
