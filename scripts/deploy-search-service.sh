#!/bin/bash
# Deploy search-service to EC2

set -e

EC2_HOST="${EC2_HOST:-ec2-user@54.253.97.113}"
SSH_KEY="${SSH_KEY:-~/.ssh/quickcommerce-sydney.pem}"
IMAGE_NAME="inventory-search-service"

echo "=== Saving Docker image ==="
docker save $IMAGE_NAME:latest | gzip > /tmp/search-service.tar.gz

echo ""
echo "=== Copying image to EC2 ==="
scp -i "$SSH_KEY" /tmp/search-service.tar.gz "$EC2_HOST:/tmp/"

echo ""
echo "=== Loading image and restarting service on EC2 ==="
ssh -i "$SSH_KEY" "$EC2_HOST" << 'ENDSSH'
cd /home/ec2-user/app
echo "Loading Docker image..."
sudo docker load < /tmp/search-service.tar.gz
rm /tmp/search-service.tar.gz

echo "Restarting search-service..."
sudo docker compose stop search-service
sudo docker compose rm -f search-service
sudo docker compose up -d search-service

echo "Waiting for service to be healthy..."
sleep 10

echo "Checking service status..."
sudo docker ps | grep search-service
sudo docker logs search-service --tail 20
ENDSSH

echo ""
echo "=== Cleaning up local temp file ==="
rm /tmp/search-service.tar.gz

echo ""
echo "✅ Deployment complete!"
echo "Trigger sync with: curl -u admin:admin123 -X POST http://54.253.97.113:8083/admin/search/index/sync-data"
