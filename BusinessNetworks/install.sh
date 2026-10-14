
#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="insurancebusinessnetwork"
FRONTEND_DIR="$PROJECT_DIR/frontend"

echo "=========================================="
echo " Insurance Business Network - Installation"
echo "=========================================="

# Vérifier le projet
if [[ ! -d "$PROJECT_DIR" ]]; then
    echo "Erreur : dossier '$PROJECT_DIR' introuvable."
    echo "Lance ce script depuis le dossier parent du projet."
    exit 1
fi

# Vérifier Node.js et npm
if ! command -v node >/dev/null 2>&1; then
    echo "Erreur : Node.js n'est pas installé."
    echo "Installe une version LTS depuis https://nodejs.org/"
    exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
    echo "Erreur : npm n'est pas installé."
    exit 1
fi

echo "Node.js : $(node --version)"
echo "npm     : $(npm --version)"

# Ne pas écraser un frontend existant
if [[ -e "$FRONTEND_DIR" ]]; then
    echo "Erreur : '$FRONTEND_DIR' existe déjà."
    echo "Sauvegarde-le ou choisis un autre emplacement."
    exit 1
fi

# Créer le frontend React + TypeScript avec Vite
echo
echo "[1/4] Création du frontend React..."

npm create vite@latest "$FRONTEND_DIR" \
    -- --template react-ts

cd "$FRONTEND_DIR"

# Installer les dépendances
echo
echo "[2/4] Installation des dépendances..."

npm install

# Préparer la configuration de l'API
echo
echo "[3/4] Configuration du backend..."

cat > .env.example <<'EOF'
# URL du backend REST servant de passerelle vers Corda
VITE_API_BASE_URL=http://localhost:8080/api
EOF

if [[ ! -f .env ]]; then
    cp .env.example .env
fi

# Vérifier que le frontend compile
echo
echo "[4/4] Vérification de la compilation..."

npm run build

echo
echo "Installation terminée !"
echo
echo "Pour démarrer le frontend :"
echo "  cd $FRONTEND_DIR"
echo "  npm run dev"
echo
echo "Vite affichera l'adresse locale du frontend."
echo
echo "Attention : le backend REST doit être développé"
echo "ou configuré séparément pour communiquer avec Corda."
