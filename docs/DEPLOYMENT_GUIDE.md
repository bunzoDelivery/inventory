# AWS EC2 Deployment Guide

This guide details the steps to deploy the Inventory & Catalog services to an AWS EC2 instance.

## Prerequisites
*   An AWS EC2 Instance (Amazon Linux 2023).
*   Your `.pem` key file for SSH access.
*   An active AWS RDS MySQL database.

## Deployment Steps

### Step 1: On Your Local Computer (Login)
Open your terminal (PowerShell or CMD) and log into your AWS server:
```bash
ssh -i "path/to/your-key.pem" ec2-user@<your-ec2-public-ip>
```
*Note: Run this from the folder where your key file is located.*
*Note: If you get a "Permissions too open" error on Windows, run these commands:*
```cmd
icacls "your-key.pem" /reset
icacls "your-key.pem" /grant:r "%USERNAME%":(R)
icacls "your-key.pem" /inheritance:r
```

### Step 2: On The EC2 Server (Install & Deploy)
**Once you are logged in** (your prompt will look like `[ec2-user@ip-...]`), run these commands inside that server:

#### A. Install & Setup
Run this simple one-liner to download and run the setup script:

```bash
curl -O https://raw.githubusercontent.com/bunzoDelivery/inventory/main/scripts/deployment-script.sh
sudo bash deployment-script.sh
```
*(This command uses `curl` to fetch the script directly from your GitHub, so you don't need git installed yet!)*
*(The script installs Git, Docker, Docker Compose Plugin, and shadow-utils)*

#### B. Fix Permissions & Pull Code
Ensure you own the application folder and have the latest code:
```bash
sudo chown -R ec2-user:ec2-user /app
cd /app
git pull
```

#### C. Configure & Start
Export your database credentials and start the containers:
```bash
# Export your RDS connection details (Remote environment variables)
export DB_HOST=inventorydatabase.cpswwoukkbi2.ap-southeast-1.rds.amazonaws.com
export DB_USERNAME=admin
export DB_PASSWORD=mysqlInv2025

# Create the containers
sudo -E docker compose up -d --build
```

## Verification
Verify the services are running:
```bash
sudo docker ps
```
You should see `inventory-service` and `catalog-service` listed.
