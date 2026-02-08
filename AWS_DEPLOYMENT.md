# AWS Deployment Guide

This guide covers deploying the Quick Commerce services to AWS.

## Architecture

### Option 1: Single Product Service (Recommended for MVP)
- **EC2 Instance**: Run product-service
- **RDS MySQL**: Managed database
- **Application Load Balancer**: For high availability (optional)

### Option 2: With Search Service
- **EC2 Instance 1**: Product-service
- **EC2 Instance 2**: Search-service + Meilisearch
- **RDS MySQL**: Shared database
- **ALB**: Route traffic to services

## Prerequisites

1. AWS Account
2. EC2 key pair created
3. Security groups configured
4. RDS MySQL instance provisioned

## Step 1: Setup RDS MySQL

1. Create RDS MySQL 8.0 instance:
   - Instance type: db.t3.micro (or larger)
   - Storage: 20 GB SSD
   - Multi-AZ: Optional (recommended for production)
   - Public accessibility: No (access via EC2 only)

2. Security group inbound rules:
   - Port 3306 from EC2 security group

3. Note the endpoint: `your-rds-instance.xxxxx.us-east-1.rds.amazonaws.com`

## Step 2: Setup EC2 Instance

1. Launch EC2 instance:
   - AMI: Amazon Linux 2023 or Ubuntu 22.04
   - Instance type: t3.small or larger
   - Storage: 20 GB
   - Security group ports: 22 (SSH), 8081 (product-service)

2. SSH to instance:
```bash
ssh -i your-key.pem ec2-user@your-instance-ip
```

3. Install Java 17:
```bash
# Amazon Linux
sudo yum install -y java-17-amazon-corretto-headless

# Ubuntu
sudo apt update
sudo apt install -y openjdk-17-jre-headless
```

4. Verify Java:
```bash
java -version
```

## Step 3: Deploy Product Service

1. Build JAR locally:
```bash
mvn clean package -DskipTests
```

2. Upload to EC2:
```bash
scp -i your-key.pem \
  product-service/target/product-service-1.0.0-SNAPSHOT.jar \
  ec2-user@your-instance-ip:/home/ec2-user/
```

3. Create systemd service file:
```bash
sudo nano /etc/systemd/system/product-service.service
```

Add:
```ini
[Unit]
Description=Quick Commerce Product Service
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=/home/ec2-user
ExecStart=/usr/bin/java -jar /home/ec2-user/product-service-1.0.0-SNAPSHOT.jar
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_HOST=your-rds-endpoint.us-east-1.rds.amazonaws.com"
Environment="DB_PORT=3306"
Environment="DB_NAME=quickcommerce"
Environment="DB_USERNAME=admin"
Environment="DB_PASSWORD=your-secure-password"
Environment="SERVER_PORT=8081"
Restart=on-failure
RestartSec=10s

[Install]
WantedBy=multi-user.target
```

4. Start service:
```bash
sudo systemctl daemon-reload
sudo systemctl start product-service
sudo systemctl enable product-service
sudo systemctl status product-service
```

5. Check logs:
```bash
sudo journalctl -u product-service -f
```

6. Verify:
```bash
curl http://localhost:8081/actuator/health
```

## Step 4: Configure Application Load Balancer (Optional)

1. Create target group:
   - Protocol: HTTP
   - Port: 8081
   - Health check path: `/actuator/health`
   - Health check interval: 30s

2. Register EC2 instance to target group

3. Create ALB:
   - Scheme: Internet-facing
   - Listener: HTTP:80 → Target Group

4. Update security groups to allow ALB → EC2

## Step 5: Environment Variables

### Production Checklist

Set these on EC2:
```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=your-rds-endpoint.us-east-1.rds.amazonaws.com
export DB_USERNAME=admin
export DB_PASSWORD=your-secure-password
export SERVER_PORT=8081
```

Or use systemd service file (recommended - see Step 3).

## Step 6: Database Initialization

Flyway will automatically run migrations on startup. Tables will be created:
- `categories`
- `products`
- `inventory_items`
- `stock_movements`
- `stock_reservations`
- `stores`

To manually run migrations:
```bash
# SSH to EC2
mysql -h your-rds-endpoint -u admin -p quickcommerce < common/src/main/resources/db/migration/V*.sql
```

## Testing Deployment

1. Test health:
```bash
curl http://your-alb-dns/actuator/health
# or
curl http://your-ec2-public-ip:8081/actuator/health
```

2. Test API:
```bash
# Get all categories
curl http://your-alb-dns/api/v1/catalog/categories

# Get all products
curl http://your-alb-dns/api/v1/catalog/products/all
```

3. Load test data:
```bash
# Create categories
curl -X POST http://your-alb-dns/api/v1/catalog/categories \
  -H "Content-Type: application/json" \
  -d @test-categories.json

# Bulk sync products
curl -X POST http://your-alb-dns/api/v1/catalog/products/sync \
  -H "Content-Type: application/json" \
  -d @test-data-sync.json
```

## Monitoring

### CloudWatch Integration

Add to systemd service file:
```ini
Environment="MANAGEMENT_METRICS_EXPORT_CLOUDWATCH_ENABLED=true"
Environment="MANAGEMENT_METRICS_EXPORT_CLOUDWATCH_NAMESPACE=QuickCommerce"
Environment="MANAGEMENT_METRICS_EXPORT_CLOUDWATCH_STEP=1m"
```

### Key Metrics to Monitor

- **CPU Utilization**: Target < 70%
- **Memory Usage**: Target < 80%
- **Request Latency**: p99 < 500ms
- **Error Rate**: Target < 1%
- **Database Connections**: Monitor pool usage

### CloudWatch Alarms

Set alarms for:
- High CPU (> 80% for 5 minutes)
- High memory (> 90%)
- High error rate (> 5%)
- Health check failures

## Scaling

### Vertical Scaling
Upgrade EC2 instance type:
- t3.small → t3.medium → t3.large

### Horizontal Scaling
1. Create AMI from configured EC2 instance
2. Create Auto Scaling Group:
   - Min: 2, Desired: 2, Max: 4
   - Target tracking: CPU 70%
3. Attach to ALB target group

## Security Hardening

1. **Security Groups**:
   - Only allow ALB → EC2:8081
   - Only allow EC2 → RDS:3306
   - SSH only from your IP

2. **IAM Roles**:
   - Attach role to EC2 for CloudWatch, Systems Manager

3. **Secrets Management**:
   - Use AWS Secrets Manager for DB_PASSWORD
   - Update systemd to fetch from secrets

4. **HTTPS**:
   - Use ACM certificate on ALB
   - Redirect HTTP → HTTPS

## Backup Strategy

1. **RDS Automated Backups**:
   - Retention: 7-30 days
   - Backup window: Low-traffic hours

2. **Manual Snapshots**:
   - Before major updates
   - Keep for compliance

## Troubleshooting

### Service Won't Start

```bash
# Check logs
sudo journalctl -u product-service -n 100

# Check if port is available
sudo netstat -tlnp | grep 8081

# Check database connectivity
mysql -h your-rds-endpoint -u admin -p
```

### High CPU Usage

```bash
# Get thread dump
sudo -u ec2-user jstack $(pgrep -f product-service) > thread-dump.txt

# Check heap usage
sudo -u ec2-user jmap -heap $(pgrep -f product-service)
```

### Database Connection Pool Exhausted

Increase pool size in application.yml:
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 50
```

## Cost Optimization

### Development/Staging
- EC2: t3.micro ($7/month)
- RDS: db.t3.micro ($15/month)
- Total: ~$25/month

### Production (Low Traffic)
- EC2: t3.small ($15/month) x2 with ALB
- RDS: db.t3.small ($30/month)
- ALB: $16/month
- Total: ~$75/month

### Production (High Traffic)
- EC2: t3.medium ($30/month) x4 with Auto Scaling
- RDS: db.t3.medium ($60/month) Multi-AZ
- ALB: $16/month
- Total: ~$200/month

## Deployment Checklist

- [ ] RDS MySQL instance created and accessible
- [ ] EC2 instance launched with Java 17
- [ ] Security groups configured
- [ ] JAR file uploaded to EC2
- [ ] Systemd service file created
- [ ] Environment variables set
- [ ] Service started and enabled
- [ ] Health check passing
- [ ] Sample data loaded
- [ ] CloudWatch alarms configured
- [ ] Backup strategy implemented
- [ ] DNS/ALB configured (if applicable)

## Rollback Plan

If deployment fails:

1. Stop new service:
```bash
sudo systemctl stop product-service
```

2. Revert to previous JAR:
```bash
mv product-service-1.0.0-SNAPSHOT.jar.backup product-service-1.0.0-SNAPSHOT.jar
```

3. Start service:
```bash
sudo systemctl start product-service
```

4. Verify health

## Support

For deployment issues:
- Check logs: `sudo journalctl -u product-service`
- Check health: `curl http://localhost:8081/actuator/health`
- Check metrics: `curl http://localhost:8081/actuator/metrics`
