#!/bin/bash
# EC2 User Data / Deployment Script
# Tested on Amazon Linux 2023

set -e

# 1. Update system
yum update -y

# 2. Install Git and Java 17
yum install -y git java-17-amazon-corretto-devel

# 3. Install Docker
yum install -y docker
systemctl start docker
systemctl enable docker
usermod -aG docker ec2-user

# 4. Install Docker Compose
mkdir -p /usr/local/lib/docker/cli-plugins/
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 5. Application Setup
mkdir -p /app
cd /app

echo "Installation Complete."
echo "----------------------------------------------------------------"
echo "To run the app, you need to export your RDS details:"
echo "export DB_HOST=your-rds-endpoint.us-east-1.rds.amazonaws.com"
echo "export DB_USERNAME=admin"
echo "export DB_PASSWORD=secret"
echo "----------------------------------------------------------------"
echo "Then copy your docker-compose.yml (and code) here and run:"
echo "docker compose up -d --build"
echo "Note: This will build and run BOTH Inventory and Catalog services."
