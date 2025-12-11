#!/bin/bash

# Complete Fresh Ubuntu Server Setup Script for PaymentSMS
# For a brand new Digital Ocean Ubuntu droplet
# Run as root: curl -s https://your-script-url.com/setup.sh | bash

set -e

echo "=========================================="
echo "🚀 PaymentSMS Fresh Server Setup"
echo "=========================================="
echo ""

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   echo "❌ This script must be run as root (use sudo)" 
   exit 1
fi

echo "📋 System Information:"
echo "   OS: $(lsb_release -d | cut -f2)"
echo "   Server: $(hostname -I | awk '{print $1}')"
echo "   Time: $(date)"
echo ""

# Update system packages first
echo "🔄 Step 1: Updating system packages..."
apt update -y
apt upgrade -y

# Install essential packages
echo "📦 Step 2: Installing essential packages..."
apt install -y curl wget nano unzip htop tree git software-properties-common apt-transport-https ca-certificates gnupg lsb-release

# Install Java 21 (OpenJDK)
echo "☕ Step 3: Installing Java 21..."
apt install -y openjdk-21-jdk

# Verify Java installation
echo "   Java version:"
java -version
echo ""

# Install PostgreSQL
echo "🐘 Step 4: Installing PostgreSQL..."

# Clean up PostgreSQL repository completely
apt-key del 7FCC7D46ACCC4CF8 2>/dev/null || true
rm -f /etc/apt/sources.list.d/pgdg.list
rm -f /etc/apt/sources.list.d/postgresql.list
apt update

# Install from Ubuntu repository (will be latest available version)
apt install -y postgresql postgresql-contrib

# Start and enable PostgreSQL
systemctl start postgresql
systemctl enable postgresql
echo "   PostgreSQL Status: $(systemctl is-active postgresql)"

# Install Nginx
echo "🌐 Step 5: Installing Nginx..."
apt install -y nginx

# Start and enable Nginx
systemctl start nginx
systemctl enable nginx
echo "   Nginx Status: $(systemctl is-active nginx)"

# Install additional tools
echo "🛠️  Step 6: Installing additional tools..."
apt install -y ufw fail2ban certbot python3-certbot-nginx

# Create pipeline user (this will be our deployment user)
echo "👤 Step 7: Creating pipeline user..."
if ! id -u pipeline >/dev/null 2>&1; then
    useradd -m -s /bin/bash pipeline
    echo "   ✅ Created user: pipeline"
else
    echo "   ℹ️  User 'pipeline' already exists"
fi

# Add pipeline user to sudo group
usermod -aG sudo pipeline

# Create complete directory structure
echo "📁 Step 8: Creating directory structure..."

# Application directories
mkdir -p /home/pipeline/releases/paymentsms_dev
mkdir -p /home/pipeline/releases/paymentsms_prod
mkdir -p /home/pipeline/production/paymentsms_dev
mkdir -p /home/pipeline/production/paymentsms_prod

# Log directories
mkdir -p /home/pipeline/logs/paymentsms_dev
mkdir -p /home/pipeline/logs/paymentsms_prod

# Environment files directory
mkdir -p /etc/env

# SSH directory for pipeline user
mkdir -p /home/pipeline/.ssh
chmod 700 /home/pipeline/.ssh

# Set proper ownership for all pipeline directories
chown -R pipeline:pipeline /home/pipeline

echo "   ✅ Directory structure created"

# Configure PostgreSQL databases and users
echo "🗄️  Step 9: Setting up PostgreSQL databases..."

# Get PostgreSQL version
PG_VERSION=$(sudo -u postgres psql -t -c "SELECT version();" | grep -oP "PostgreSQL \K\d+")
echo "   PostgreSQL version: $PG_VERSION"

# Create database user and databases
sudo -u postgres psql -c "CREATE USER paymentsms_user WITH PASSWORD 'PaymentSMS#2024#Secure';" 2>/dev/null || echo "   ℹ️  User paymentsms_user already exists"
sudo -u postgres psql -c "CREATE DATABASE prod_paymentsms OWNER paymentsms_user;" 2>/dev/null || echo "   ℹ️  Database prod_paymentsms already exists"
sudo -u postgres psql -c "CREATE DATABASE dev_paymentsms OWNER paymentsms_user;" 2>/dev/null || echo "   ℹ️  Database dev_paymentsms already exists"

# Grant privileges
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE prod_paymentsms TO paymentsms_user;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE dev_paymentsms TO paymentsms_user;"

# Configure PostgreSQL for local connections
PG_CONFIG="/etc/postgresql/${PG_VERSION}/main/postgresql.conf"
PG_HBA="/etc/postgresql/${PG_VERSION}/main/pg_hba.conf"

# Enable local connections
if ! grep -q "listen_addresses = 'localhost'" $PG_CONFIG; then
    sed -i "s/#listen_addresses = 'localhost'/listen_addresses = 'localhost'/" $PG_CONFIG
    echo "   ✅ Configured PostgreSQL to listen on localhost"
fi

# Add authentication rule if not exists
if ! grep -q "local   all             paymentsms_user" $PG_HBA; then
    echo "local   all             paymentsms_user                             md5" >> $PG_HBA
    echo "   ✅ Added authentication rule for paymentsms_user"
fi

# Restart PostgreSQL to apply changes
systemctl restart postgresql
echo "   ✅ PostgreSQL configured and restarted"

# Test database connections
echo "🔍 Testing database connections..."
if sudo -u postgres psql -d prod_paymentsms -c "SELECT 1;" >/dev/null 2>&1; then
    echo "   ✅ Production database connection: OK"
else
    echo "   ❌ Production database connection: FAILED"
fi

if sudo -u postgres psql -d dev_paymentsms -c "SELECT 1;" >/dev/null 2>&1; then
    echo "   ✅ Development database connection: OK"
else
    echo "   ❌ Development database connection: FAILED"
fi

# Create environment files
echo "🔧 Step 10: Creating environment configuration files..."

# Production environment file
cat > /etc/env/paymentsms_prod.env << 'EOF'
# Production Environment Variables for PaymentSMS
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/prod_paymentsms
SPRING_DATASOURCE_USERNAME=paymentsms_user
SPRING_DATASOURCE_PASSWORD=PaymentSMS#2024#Secure
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=true

# Application settings
SPRING_APPLICATION_NAME=paymentsms
SERVER_ERROR_INCLUDE_MESSAGE=always
SERVER_ERROR_INCLUDE_BINDING_ERRORS=always

# Logging
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_PAYMENTSMS=INFO
LOGGING_FILE_PATH=/home/pipeline/logs/paymentsms_prod

# Additional Production Settings
SPRING_PROFILES_ACTIVE=prod
EOF

# Development environment file
cat > /etc/env/paymentsms_dev.env << 'EOF'
# Development Environment Variables for PaymentSMS
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/dev_paymentsms
SPRING_DATASOURCE_USERNAME=paymentsms_user
SPRING_DATASOURCE_PASSWORD=PaymentSMS#2024#Secure
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=true
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect
SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=true

# Application settings
SPRING_APPLICATION_NAME=paymentsms-dev
SERVER_ERROR_INCLUDE_MESSAGE=always
SERVER_ERROR_INCLUDE_BINDING_ERRORS=always

# Logging
LOGGING_LEVEL_ROOT=DEBUG
LOGGING_LEVEL_COM_PAYMENTSMS=DEBUG
LOGGING_FILE_PATH=/home/pipeline/logs/paymentsms_dev

# Additional Development Settings
SPRING_PROFILES_ACTIVE=dev
EOF

# Set proper permissions for environment files
chmod 640 /etc/env/*.env
chown root:pipeline /etc/env/*.env

echo "   ✅ Environment files created with secure permissions"

# Create systemd service templates
echo "⚙️  Step 11: Creating systemd service templates..."

# Production service template
cat > /etc/systemd/system/paymentsms@.service << 'EOF'
[Unit]
Description=PaymentSMS Spring Boot Production Service (Port %i)
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
ExecStart=/usr/bin/java -jar /home/pipeline/production/paymentsms_prod/paymentsms
WorkingDirectory=/home/pipeline/production/paymentsms_prod
User=pipeline
Group=pipeline
Environment="SERVER_PORT=%i"
EnvironmentFile=/etc/env/paymentsms_prod.env
StandardOutput=append:/home/pipeline/logs/paymentsms_prod/output-%i.log
StandardError=append:/home/pipeline/logs/paymentsms_prod/error-%i.log
LimitNOFILE=65536
Restart=on-failure
RestartSec=10
TimeoutStopSec=30
PrivateTmp=true
ProtectSystem=full
NoNewPrivileges=true
AmbientCapabilities=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
EOF

# Development service template
cat > /etc/systemd/system/paymentsms_dev@.service << 'EOF'
[Unit]
Description=PaymentSMS Spring Boot Development Service (Port %i)
After=network.target postgresql.service
Requires=postgresql.service

[Service]
Type=simple
ExecStart=/usr/bin/java -jar /home/pipeline/production/paymentsms_dev/paymentsms
WorkingDirectory=/home/pipeline/production/paymentsms_dev
User=pipeline
Group=pipeline
Environment="SERVER_PORT=%i"
EnvironmentFile=/etc/env/paymentsms_dev.env
StandardOutput=append:/home/pipeline/logs/paymentsms_dev/output-%i.log
StandardError=append:/home/pipeline/logs/paymentsms_dev/error-%i.log
LimitNOFILE=65536
Restart=on-failure
RestartSec=10
TimeoutStopSec=30
PrivateTmp=true
ProtectSystem=full
NoNewPrivileges=true
AmbientCapabilities=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd to recognize new services
systemctl daemon-reload
echo "   ✅ Systemd service templates created"

# Configure Nginx
echo "🌐 Step 12: Configuring Nginx..."

# Remove default site
rm -f /etc/nginx/sites-enabled/default

# Create production site configuration
cat > /etc/nginx/sites-available/paymentsms-prod << 'EOF'
upstream paymentsms_prod {
    server 127.0.0.1:8000;
    server 127.0.0.1:8001;
}

server {
    listen 80;
    server_name prod.paymentsms.com;

    location / {
        proxy_pass http://paymentsms_prod;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
EOF

# Create development site configuration
cat > /etc/nginx/sites-available/paymentsms-dev << 'EOF'
upstream paymentsms_dev {
    server 127.0.0.1:7000;
    server 127.0.0.1:7001;
}

server {
    listen 80;
    server_name dev.paymentsms.com;

    location / {
        proxy_pass http://paymentsms_dev;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
EOF

# Enable sites
ln -sf /etc/nginx/sites-available/paymentsms-prod /etc/nginx/sites-enabled/
ln -sf /etc/nginx/sites-available/paymentsms-dev /etc/nginx/sites-enabled/

# Test nginx configuration
if nginx -t; then
    echo "   ✅ Nginx configuration is valid"
    systemctl reload nginx
else
    echo "   ❌ Nginx configuration has errors"
    exit 1
fi

# Configure firewall
echo "🔥 Step 13: Configuring firewall..."
ufw --force enable
ufw allow ssh
ufw allow 'Nginx Full'
ufw allow 7000:7001/tcp  # Development ports
ufw allow 8000:8001/tcp  # Production ports
ufw allow 5432/tcp       # PostgreSQL (if needed for external connections)

echo "   ✅ Firewall configured"

# Setup log rotation
echo "📝 Step 14: Setting up log rotation..."
cat > /etc/logrotate.d/paymentsms << 'EOF'
/home/pipeline/logs/**/*.log {
    daily
    missingok
    rotate 30
    compress
    delaycompress
    notifempty
    copytruncate
    su pipeline pipeline
}
EOF

echo "   ✅ Log rotation configured"

# Create a simple health check script
echo "🏥 Step 15: Creating health check script..."
cat > /home/pipeline/health-check.sh << 'EOF'
#!/bin/bash
# Health check script for PaymentSMS services

echo "=== PaymentSMS Health Check ==="
echo "Time: $(date)"
echo ""

echo "🌐 Nginx Status:"
systemctl is-active nginx
echo ""

echo "🐘 PostgreSQL Status:"
systemctl is-active postgresql
echo ""

echo "🏭 Production Services:"
for port in 8000 8001; do
    if systemctl is-active paymentsms@${port} >/dev/null 2>&1; then
        echo "  ✅ paymentsms@${port}: $(systemctl is-active paymentsms@${port})"
    else
        echo "  ❌ paymentsms@${port}: $(systemctl is-active paymentsms@${port})"
    fi
done
echo ""

echo "🧪 Development Services:"
for port in 7000 7001; do
    if systemctl is-active paymentsms_dev@${port} >/dev/null 2>&1; then
        echo "  ✅ paymentsms_dev@${port}: $(systemctl is-active paymentsms_dev@${port})"
    else
        echo "  ❌ paymentsms_dev@${port}: $(systemctl is-active paymentsms_dev@${port})"
    fi
done
echo ""

echo "💾 Disk Usage:"
df -h /home/pipeline | tail -1
echo ""

echo "🔗 Network Ports:"
ss -tlnp | grep -E ":(7000|7001|8000|8001|80|443|5432) " | head -10
echo ""

echo "📊 Database Status:"
echo "  Production DB:"
sudo -u postgres psql -d prod_paymentsms -c "SELECT pg_database_size('prod_paymentsms') AS size;" 2>/dev/null || echo "    ❌ Cannot connect to prod_paymentsms"
echo "  Development DB:"
sudo -u postgres psql -d dev_paymentsms -c "SELECT pg_database_size('dev_paymentsms') AS size;" 2>/dev/null || echo "    ❌ Cannot connect to dev_paymentsms"
EOF

chmod +x /home/pipeline/health-check.sh
chown pipeline:pipeline /home/pipeline/health-check.sh

# Create a service restart helper script
echo "🔧 Step 16: Creating service management scripts..."
cat > /home/pipeline/restart-services.sh << 'EOF'
#!/bin/bash
# Helper script to restart PaymentSMS services

if [ "$1" == "prod" ]; then
    echo "🔄 Restarting Production Services..."
    sudo systemctl restart paymentsms@8000
    sudo systemctl restart paymentsms@8001
    echo "✅ Production services restarted"
elif [ "$1" == "dev" ]; then
    echo "🔄 Restarting Development Services..."
    sudo systemctl restart paymentsms_dev@7000
    sudo systemctl restart paymentsms_dev@7001
    echo "✅ Development services restarted"
elif [ "$1" == "all" ]; then
    echo "🔄 Restarting All Services..."
    sudo systemctl restart paymentsms@8000
    sudo systemctl restart paymentsms@8001
    sudo systemctl restart paymentsms_dev@7000
    sudo systemctl restart paymentsms_dev@7001
    echo "✅ All services restarted"
else
    echo "Usage: $0 {prod|dev|all}"
    exit 1
fi
EOF

chmod +x /home/pipeline/restart-services.sh
chown pipeline:pipeline /home/pipeline/restart-services.sh

# Configure sudo for pipeline user to manage services without password
echo "🔐 Step 17: Configuring sudo permissions for pipeline user..."
cat > /etc/sudoers.d/pipeline << 'EOF'
# Allow pipeline user to manage paymentsms services without password
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart paymentsms@*
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart paymentsms_dev@*
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl start paymentsms@*
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl start paymentsms_dev@*
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl stop paymentsms@*
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl stop paymentsms_dev@*
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl status paymentsms@*
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl status paymentsms_dev@*
pipeline ALL=(ALL) NOPASSWD: /usr/bin/systemctl daemon-reload
EOF

chmod 0440 /etc/sudoers.d/pipeline
echo "   ✅ Sudo permissions configured"

# Final system status
echo ""
echo "=========================================="
echo "✅ SETUP COMPLETE!"
echo "=========================================="
echo ""
echo "📋 Summary:"
echo "   • Java $(java -version 2>&1 | head -1 | cut -d'"' -f2) ✅"
echo "   • PostgreSQL $(sudo -u postgres psql -t -c 'SELECT version();' | grep -oP 'PostgreSQL \K[0-9.]+') ✅"
echo "   • Nginx $(nginx -v 2>&1 | cut -d' ' -f3 | cut -d'/' -f2) ✅"
echo "   • Pipeline user created ✅"
echo "   • Databases created (prod_paymentsms, dev_paymentsms) ✅"
echo "   • Directory structure created ✅"
echo "   • Systemd services configured ✅"
echo "   • Nginx configured ✅"
echo "   • Firewall configured ✅"
echo ""
echo "🗄️  Database Information:"
echo "   Production DB: prod_paymentsms"
echo "   Development DB: dev_paymentsms"
echo "   DB User: paymentsms_user"
echo "   DB Password: PaymentSMS#2024#Secure"
echo ""
echo "🔧 Next Steps:"
echo "1. Add your SSH public key to /home/pipeline/.ssh/authorized_keys"
echo "   Example: echo 'your-ssh-public-key' >> /home/pipeline/.ssh/authorized_keys"
echo "2. Point your domains to this server:"
echo "   - dev.paymentsms.com -> $(hostname -I | awk '{print $1}')"
echo "   - prod.paymentsms.com -> $(hostname -I | awk '{print $1}')"
echo "3. Set up SSL certificates:"
echo "   sudo certbot --nginx -d dev.paymentsms.com -d prod.paymentsms.com"
echo "4. Configure GitHub repository secrets:"
echo "   - DROPLET_USER: pipeline"
echo "   - DROPLET: $(hostname -I | awk '{print $1}')"
echo "   - SSH_PRIVATE_KEY: (your SSH private key)"
echo "5. Test the CI/CD pipeline by pushing to dev or main branch"
echo ""
echo "🛠️  Useful commands:"
echo "   Health check:          /home/pipeline/health-check.sh"
echo "   Restart services:      /home/pipeline/restart-services.sh {prod|dev|all}"
echo "   View dev logs:         tail -f /home/pipeline/logs/paymentsms_dev/output-7000.log"
echo "   View prod logs:        tail -f /home/pipeline/logs/paymentsms_prod/output-8000.log"
echo "   Service status:        systemctl status paymentsms@8000"
echo "   Dev service status:    systemctl status paymentsms_dev@7000"
echo ""
echo "🌐 Your server is ready at: http://$(hostname -I | awk '{print $1}')"
echo "   Development will be at: http://dev.paymentsms.com (ports 7000-7001)"
echo "   Production will be at:  http://prod.paymentsms.com (ports 8000-8001)"
echo ""
echo "⚠️  IMPORTANT: Update the environment files in /etc/env/ with your actual configuration"
echo "   - /etc/env/paymentsms_prod.env"
echo "   - /etc/env/paymentsms_dev.env"
echo ""
