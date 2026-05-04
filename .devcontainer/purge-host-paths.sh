#!/usr/bin/env bash
# postStartCommand — exécuté à CHAQUE démarrage du dev container.
# Deux responsabilités :
#  1. Purger les chemins absolus de l'hôte dans .idea/workspace.xml (legacy IntelliJ).
#  2. Re-corriger le ownership du volume nommé node_modules (filet de sécurité au cas où
#     postCreateCommand a échoué ou que Gateway re-attache un container sans rerun de setup).
#
# Idempotent : si rien à corriger, ne fait rien.
set -euo pipefail

# --- 1. Filet de sécurité : ownership du volume node_modules ---
# Docker crée les volumes nommés en root:root au premier mount. Si setup.sh n'a pas tourné
# (Gateway re-attache un container existant) ou a planté, pnpm install échoue avec EACCES.
# On retente à chaque démarrage — coût quasi nul si déjà correct.
NM=/workspaces/ai-sentinel/pii-reporting-ui/node_modules
if [ -d "$NM" ] && [ "$(stat -c %U "$NM" 2>/dev/null)" != "vscode" ]; then
  echo "[postStart] node_modules appartient à $(stat -c %U "$NM") → chown -R vscode:vscode"
  sudo chown -R vscode:vscode "$NM"
fi

# --- 2. Purge des chemins hôte dans workspace.xml ---

# Chemin relatif : postStartCommand tourne depuis workspaceFolder du dev container.
# Testable aussi depuis la racine du repo sur l'hôte.
WORKSPACE_XML=".idea/workspace.xml"
[ -f "$WORKSPACE_XML" ] || exit 0

# Settings IntelliJ connus pour être pollués par des chemins absolus de l'hôte.
# On supprime la ligne entière si elle contient un drive letter Windows (C:, D:, etc.).
# IntelliJ régénère les valeurs par auto-détection au prochain démarrage.
POLLUTED_SETTINGS=(
  "nodejs_package_manager_path"
  "ts.external.directory.path"
)

CHANGED=0
for setting in "${POLLUTED_SETTINGS[@]}"; do
  # Pattern : ligne contenant le nom du setting ET un drive letter Windows (X:)
  # En sed -E pour ERE plus lisible.
  if grep -E "${setting}.*[A-Za-z]:" "$WORKSPACE_XML" >/dev/null 2>&1; then
    sed -i -E "/${setting}.*[A-Za-z]:/d" "$WORKSPACE_XML"
    echo "[purge-host-paths] Purge: $setting (chemin hôte détecté)"
    CHANGED=1
  fi
done

[ "$CHANGED" -eq 0 ] && echo "[purge-host-paths] Aucun chemin hôte à purger — clean"
