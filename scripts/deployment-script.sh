#!/bin/bash
# QuickCommerce Deployment Script
# Tested on Amazon Linux 2023

set -e

echo "Starting system setup..."

# 1. Update system
sudo yum update -y

# 2. Install Git and Java 17
sudo yum install -y git java-17-amazon-corretto-devel

# 3. Install Docker
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

# 4. Install Docker Compose
sudo mkdir -p /usr/local/lib/docker/cli-plugins/
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 5. Install Docker Buildx (Required for compose build)
sudo curl -SL https://github.com/docker/buildx/releases/latest/download/buildx-linux-amd64 -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx

# 5. Application Setup
sudo mkdir -p /app
sudo chown ec2-user:ec2-user /app
cd /app

# 6. Clone Repository
# IMPORTANT: Replace the URL below with YOUR actual GitHub repository URL
REPO_URL="https://github.com/bunzoDelivery/inventory.git"
echo "Cloning repository from $REPO_URL..."

if [ -d ".git" ]; then
    echo "Repo already exists, pulling latest..."
    git pull
else
    git clone $REPO_URL .
fi

echo "----------------------------------------------------------------"
echo "✅ Setup Complete!"
echo "----------------------------------------------------------------"
echo "NEXT STEPS:"
echo "1. Export your RDS Connection Details:"
echo "   export DB_HOST=your-rds-endpoint.rds.amazonaws.com"
echo "   export DB_USERNAME=admin"
echo "   export DB_PASSWORD=yourpassword"
echo ""
echo "2. Start the services:"
echo "   docker compose up -d --build"
echo "----------------------------------------------------------------"
