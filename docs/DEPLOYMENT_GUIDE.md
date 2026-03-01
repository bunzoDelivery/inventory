# AWS EC2 Deployment Guide

This guide details the steps to deploy QuickCommerce services (product-service, order-service, search-service, meilisearch) to an AWS EC2 instance.

## Prerequisites
*   An AWS EC2 Instance (Amazon Linux 2023).
*   Your `.pem` key file for SSH access.
*   An active AWS RDS MySQL database.
*   Security group allows inbound traffic on ports **8081**, **8082**, **8083** (and optionally 7700 for Meilisearch).

## Deployment Steps

### Step 1: On Your Local Computer (Login)

**macOS/Linux:**
```bash
chmod 400 your-key.pem
ssh -i "path/to/your-key.pem" ec2-user@<your-ec2-public-ip>
```

**Windows (PowerShell/CMD):**
```bash
ssh -i "path/to/your-key.pem" ec2-user@<your-ec2-public-ip>
```
*Note: Run from the folder where your key file is located.*

*If you get a "Permissions too open" error on Windows:*
```cmd
icacls "your-key.pem" /reset
icacls "your-key.pem" /grant:r "%USERNAME%":(R)
icacls "your-key.pem" /inheritance:r
```

### Step 2: On The EC2 Server (Install & Deploy)

**Once you are logged in** (prompt: `[ec2-user@ip-...]`), run these commands:

#### A. Install & Setup (first-time only)

```bash
curl -O https://raw.githubusercontent.com/bunzoDelivery/inventory/main/scripts/deployment-script.sh
sudo bash deployment-script.sh
```
*(Installs Git, Docker, Docker Compose, and clones the repo to /app)*

#### B. Fix Permissions & Pull Code

```bash
sudo chown -R ec2-user:ec2-user /app
cd /app
git pull
```

#### C. Configure Environment (if needed)

The repo includes a `.env` file with DB and Meilisearch config. After `git pull`, it will be in `/app`. If you need to change credentials, edit it:

```bash
cd /app
nano .env
```

*Note: `MEILI_MASTER_KEY` must be at least 16 characters for production (e.g. `QuickCommerce2024Prod`).*

#### D. Start Services

```bash
cd /app
sudo docker compose down
sudo docker compose up -d --build
```

*(Use `sudo -E docker compose up -d --build` if you exported variables and want them passed to containers.)*

## Verification

```bash
sudo docker ps
```

You should see these containers running and healthy:
| Container        | Port | Service         |
|------------------|------|-----------------|
| product-service  | 8081 | Inventory/Catalog |
| order-service    | 8082 | Orders          |
| search-service   | 8083 | Search          |
| meilisearch      | 7700 | Search engine   |

**Health checks:**
```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## Updating After Code Changes

```bash
cd /app
git pull
sudo docker compose down
sudo docker compose up -d --build
```

## Meilisearch & Search Troubleshooting

**If search returns 0 results or "meilisearch: Name or service not known":**

1. **Ensure Meilisearch container is running:**
   ```bash
   sudo docker ps | grep meilisearch
   ```

2. **Restart all services** (search-service depends on meilisearch):
   ```bash
   cd /app
   sudo docker compose down
   sudo docker compose up -d --build
   ```

3. **Trigger index sync** (after services are up):
   ```bash
   curl -X POST "http://localhost:8083/admin/search/index/rebuild" -u admin:admin123
   ```

4. **Verify index has data:**
   ```bash
   curl "http://localhost:8083/admin/search/index/stats" -u admin:admin123
   ```

5. **Test search:**
   ```bash
   curl -X POST "http://localhost:8083/search" \
     -H "Content-Type: application/json" \
     -d '{"query":"milk","storeId":1,"page":1,"pageSize":20}'
   ```
