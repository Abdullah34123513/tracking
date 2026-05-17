#!/bin/bash
# ============================================================
# Tracker Backend - Hostinger Shared Hosting Deploy Script
# Safe to run multiple times. Handles fresh install & updates.
# Usage: bash deploy.sh
# ============================================================

set -e

REPO_URL="https://github.com/Abdullah34123513/tracking.git"
DOMAIN_DIR="$HOME/domains/api.abdullahsourcing.com"
APP_DIR="$DOMAIN_DIR"
DB_FILE="$APP_DIR/database/database.sqlite"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err()  { echo -e "${RED}[✗]${NC} $1"; exit 1; }

echo ""
echo "============================================"
echo "  Tracker Backend Deploy (Hostinger)"
echo "============================================"
echo ""

# --- Check domain dir exists ---
[ -d "$DOMAIN_DIR" ] || err "Domain directory not found: $DOMAIN_DIR"

cd "$DOMAIN_DIR"

# --- Step 1: Clone or Pull ---
if [ -f "$APP_DIR/artisan" ]; then
    # Already deployed — pull latest
    log "Existing install found. Pulling latest from GitHub..."
    
    # Save .env and firebase key before pull
    [ -f .env ] && cp .env /tmp/.env.tracker.bak
    [ -f storage/app/firebase-service-account.json ] && cp storage/app/firebase-service-account.json /tmp/firebase.bak
    
    # Pull latest backend files
    cd /tmp
    rm -rf tracker_update
    git clone --depth 1 "$REPO_URL" tracker_update 2>/dev/null
    
    # Sync backend files (skip .env and database)
    rsync -a --exclude='.env' --exclude='database/database.sqlite' --exclude='storage/app/firebase-service-account.json' --exclude='storage/logs' --exclude='storage/framework/sessions' /tmp/tracker_update/backend/ "$APP_DIR/"
    rm -rf /tmp/tracker_update
    
    cd "$APP_DIR"
    
    # Restore .env and firebase key
    [ -f /tmp/.env.tracker.bak ] && mv /tmp/.env.tracker.bak .env
    [ -f /tmp/firebase.bak ] && mkdir -p storage/app && mv /tmp/firebase.bak storage/app/firebase-service-account.json
    
    log "Code updated from GitHub."
else
    # Fresh install
    log "Fresh install. Cloning repository..."
    cd /tmp
    rm -rf tracker_clone
    git clone --depth 1 "$REPO_URL" tracker_clone 2>/dev/null || err "Failed to clone repo. Push to GitHub first!"
    
    # Copy backend files to domain dir
    cp -r tracker_clone/backend/* "$APP_DIR/"
    cp tracker_clone/backend/.env.example "$APP_DIR/.env" 2>/dev/null || true
    cp tracker_clone/backend/.gitignore "$APP_DIR/" 2>/dev/null || true
    rm -rf /tmp/tracker_clone
    
    cd "$APP_DIR"
    log "Repository cloned successfully."
fi

# --- Step 2: public_html symlink ---
if [ -L "$DOMAIN_DIR/public_html" ]; then
    log "public_html symlink already exists."
elif [ -d "$DOMAIN_DIR/public_html" ]; then
    # Backup and replace
    warn "Replacing public_html directory with symlink..."
    rm -rf "$DOMAIN_DIR/public_html"
    ln -s "$DOMAIN_DIR/public" "$DOMAIN_DIR/public_html"
    log "public_html → public symlink created."
else
    ln -s "$DOMAIN_DIR/public" "$DOMAIN_DIR/public_html"
    log "public_html → public symlink created."
fi

# --- Step 3: Composer install ---
log "Installing dependencies..."
if command -v composer &> /dev/null; then
    composer install --optimize-autoloader --no-dev --no-interaction --quiet 2>/dev/null
    log "Composer dependencies installed."
else
    # Try php composer.phar
    if [ -f composer.phar ]; then
        php composer.phar install --optimize-autoloader --no-dev --no-interaction --quiet
    else
        warn "Downloading Composer..."
        curl -sS https://getcomposer.org/installer | php -- --quiet
        php composer.phar install --optimize-autoloader --no-dev --no-interaction --quiet
    fi
    log "Composer dependencies installed."
fi

# --- Step 4: Environment setup ---
if [ ! -f .env ]; then
    cp .env.example .env
    log "Created .env from example."
fi

# Generate key if not set
if grep -q "APP_KEY=$" .env || grep -q "APP_KEY=base64:$" .env 2>/dev/null; then
    php artisan key:generate --force --quiet
    log "App key generated."
else
    log "App key already set."
fi

# Set production values
sed -i 's/APP_ENV=local/APP_ENV=production/' .env
sed -i 's/APP_DEBUG=true/APP_DEBUG=false/' .env
sed -i 's|APP_URL=http://localhost|APP_URL=https://api.abdullahsourcing.com|' .env

# --- Step 5: Database ---
mkdir -p database
if [ ! -f "$DB_FILE" ]; then
    touch "$DB_FILE"
    log "SQLite database created."
else
    log "SQLite database already exists."
fi

# Run migrations
php artisan migrate --force --quiet 2>/dev/null
log "Migrations complete."

# Seed only if admin doesn't exist
ADMIN_EXISTS=$(php artisan tinker --execute="echo App\Models\User::where('email','admin@tracker.app')->exists() ? 'yes' : 'no';" 2>/dev/null | tail -1)
if [ "$ADMIN_EXISTS" != "yes" ]; then
    php artisan db:seed --force --quiet 2>/dev/null
    log "Admin user seeded."
else
    log "Admin user already exists, skipping seed."
fi

# --- Step 6: Optimize ---
php artisan optimize:clear --quiet 2>/dev/null
php artisan optimize --quiet 2>/dev/null
log "Laravel optimized for production."

# --- Step 7: Permissions ---
chmod -R 775 storage 2>/dev/null
chmod -R 775 bootstrap/cache 2>/dev/null
chmod 664 "$DB_FILE" 2>/dev/null
log "Permissions set."

# --- Step 8: Storage link ---
php artisan storage:link --quiet 2>/dev/null || true
log "Storage linked."

# --- Done ---
echo ""
echo "============================================"
echo -e "  ${GREEN}DEPLOYMENT COMPLETE!${NC}"
echo "============================================"
echo ""
echo "  Admin Panel: https://api.abdullahsourcing.com/admin"
echo "  Login:       admin@tracker.app"
echo "  Password:    admin123"
echo ""
echo "  API Base:    https://api.abdullahsourcing.com/api/v1"
echo ""

# Check firebase key
if [ -f storage/app/firebase-service-account.json ]; then
    echo -e "  Firebase:    ${GREEN}Configured ✓${NC}"
else
    echo -e "  Firebase:    ${YELLOW}Missing! Upload firebase-service-account.json${NC}"
    echo "               to: $APP_DIR/storage/app/firebase-service-account.json"
fi

echo ""
echo "============================================"
