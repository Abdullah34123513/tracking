#!/bin/bash
# ============================================================
# Tracker Backend - Hostinger VPS Deployment Script
# Run this script via SSH on your Hostinger VPS
# Usage: bash deploy.sh
# ============================================================

set -e

echo "============================================"
echo "  Tracker Backend Deployment"
echo "============================================"

# --- Configuration ---
REPO_URL="https://github.com/Abdullah34123513/tracking.git"
APP_DIR="/var/www/tracker"
DOMAIN="_"  # Change this to your domain if you have one

# --- Detect PHP version ---
PHP_VERSION=""
for v in 8.3 8.2 8.1; do
    if command -v "php${v}" &> /dev/null || dpkg -l "php${v}" &> /dev/null 2>&1; then
        PHP_VERSION="$v"
        break
    fi
done

echo ""
echo "[1/8] Installing required packages..."
sudo apt update -y
sudo apt install -y nginx mysql-server git curl unzip software-properties-common

# Install PHP if not found
if [ -z "$PHP_VERSION" ]; then
    echo "Installing PHP 8.3..."
    sudo add-apt-repository -y ppa:ondrej/php
    sudo apt update -y
    PHP_VERSION="8.3"
fi

sudo apt install -y php${PHP_VERSION}-fpm php${PHP_VERSION}-cli php${PHP_VERSION}-mysql \
    php${PHP_VERSION}-mbstring php${PHP_VERSION}-xml php${PHP_VERSION}-curl \
    php${PHP_VERSION}-zip php${PHP_VERSION}-gd php${PHP_VERSION}-bcmath \
    php${PHP_VERSION}-tokenizer php${PHP_VERSION}-sqlite3

echo "Using PHP version: ${PHP_VERSION}"

echo ""
echo "[2/8] Installing Composer..."
if ! command -v composer &> /dev/null; then
    curl -sS https://getcomposer.org/installer | php
    sudo mv composer.phar /usr/local/bin/composer
fi

echo ""
echo "[3/8] Cloning repository..."
if [ -d "$APP_DIR" ]; then
    echo "Directory exists, pulling latest..."
    cd "$APP_DIR"
    git pull origin main
else
    sudo git clone "$REPO_URL" "$APP_DIR"
    cd "$APP_DIR"
fi

cd "$APP_DIR/backend"

echo ""
echo "[4/8] Installing Laravel dependencies..."
sudo composer install --optimize-autoloader --no-dev --no-interaction

echo ""
echo "[5/8] Setting up environment..."
if [ ! -f .env ]; then
    cp .env.example .env
    php artisan key:generate
fi

# Setup SQLite database (simple, no MySQL config needed)
touch database/database.sqlite

# Update .env for production with SQLite
sudo sed -i 's/APP_ENV=local/APP_ENV=production/' .env
sudo sed -i 's/APP_DEBUG=true/APP_DEBUG=false/' .env
sudo sed -i 's|APP_URL=http://localhost|APP_URL=http://'"$(curl -s ifconfig.me)"'|' .env

echo ""
echo "[6/8] Running migrations and seeding..."
php artisan migrate --force --seed
php artisan optimize
php artisan storage:link 2>/dev/null || true

echo ""
echo "[7/8] Setting permissions..."
sudo chown -R www-data:www-data "$APP_DIR"
sudo chmod -R 775 "$APP_DIR/backend/storage"
sudo chmod -R 775 "$APP_DIR/backend/bootstrap/cache"

echo ""
echo "[8/8] Configuring Nginx..."
sudo tee /etc/nginx/sites-available/tracker > /dev/null <<NGINX
server {
    listen 80;
    server_name ${DOMAIN};
    root ${APP_DIR}/backend/public;

    add_header X-Frame-Options "SAMEORIGIN";
    add_header X-Content-Type-Options "nosniff";

    index index.php;

    charset utf-8;

    client_max_body_size 20M;

    location / {
        try_files \$uri \$uri/ /index.php?\$query_string;
    }

    location = /favicon.ico { access_log off; log_not_found off; }
    location = /robots.txt  { access_log off; log_not_found off; }

    error_page 404 /index.php;

    location ~ \.php$ {
        fastcgi_pass unix:/var/run/php/php${PHP_VERSION}-fpm.sock;
        fastcgi_param SCRIPT_FILENAME \$realpath_root\$fastcgi_script_name;
        include fastcgi_params;
    }

    location ~ /\.(?!well-known).* {
        deny all;
    }
}
NGINX

# Enable the site
sudo ln -sf /etc/nginx/sites-available/tracker /etc/nginx/sites-enabled/tracker
sudo rm -f /etc/nginx/sites-enabled/default

# Test and restart
sudo nginx -t
sudo systemctl restart nginx
sudo systemctl restart php${PHP_VERSION}-fpm

# Get the server IP
SERVER_IP=$(curl -s ifconfig.me)

echo ""
echo "============================================"
echo "  DEPLOYMENT COMPLETE!"
echo "============================================"
echo ""
echo "  Admin Panel: http://${SERVER_IP}/admin"
echo "  Login:       admin@tracker.app"
echo "  Password:    admin123"
echo ""
echo "  API Base:    http://${SERVER_IP}/api/v1"
echo ""
echo "  IMPORTANT: Copy your firebase-service-account.json to:"
echo "  ${APP_DIR}/backend/storage/app/firebase-service-account.json"
echo ""
echo "============================================"
