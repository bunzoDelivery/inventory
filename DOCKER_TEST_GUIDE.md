# Docker Build and Test Guide for AWS Deployment

## What We're Testing

Before deploying to AWS, we need to verify:
1. ✅ Docker image builds successfully
2. ✅ Container runs with local MySQL (db name: `inventory`)
3. ✅ Application connects to database correctly
4. ✅ All APIs work as expected
5. ✅ Ready for AWS EC2 deployment

## Step 1: Build Docker Image

```bash
# Build product-service Docker image
docker build -t product-service:test --build-arg MODULE_NAME=product-service .
```

**Expected**: Build completes successfully, creates Docker image
**Time**: 5-10 minutes (first time, downloads Maven dependencies)

## Step 2: Run with Local MySQL

Assuming MySQL is running locally on:
- Host: `localhost` (or `host.docker.internal` from inside Docker)
- Port: `3306`
- Database: `inventory`
- User: `root`
- Password: `root`

```bash
# Run the container
docker run -d \
  --name product-service-test \
  -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=3306 \
  -e DB_NAME=inventory \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=root \
  product-service:test
```

## Step 3: Check Logs

```bash
# Watch logs in real-time
docker logs -f product-service-test

# Look for these success indicators:
# ✓ "Started ProductServiceApplication in X seconds"
# ✓ "Netty started on port 8081"
# ✓ "Flyway migration completed successfully"
# ✓ No connection errors
```

## Step 4: Test the APIs

```bash
# 1. Health Check
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP",...}

# 2. Get All Categories
curl http://localhost:8081/api/v1/catalog/categories
# Expected: JSON array of categories

# 3. Get All Products
curl http://localhost:8081/api/v1/catalog/products/all
# Expected: JSON array of products

# 4. Create a Category
curl -X POST http://localhost:8081/api/v1/catalog/categories \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Category",
    "slug": "test-category",
    "description": "Testing Docker setup",
    "displayOrder": 1,
    "isActive": true
  }'
# Expected: JSON with created category and ID

# 5. Bulk Sync Products
curl -X POST http://localhost:8081/api/v1/catalog/products/sync \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": 1,
    "items": [{
      "sku": "TEST-DOCKER-001",
      "name": "Test Product",
      "categoryId": 1,
      "brand": "TestBrand",
      "basePrice": 99.99,
      "currentStock": 100,
      "unitOfMeasure": "pcs",
      "isActive": true
    }]
  }'
# Expected: {"successCount": 1, "failureCount": 0, ...}
```

## Step 5: Run Comprehensive Tests

```powershell
# Run the full test suite
./test-final.ps1
```

**Expected**: All 25 tests pass

## Step 6: Clean Up

```bash
# Stop container
docker stop product-service-test

# Remove container
docker rm product-service-test

# Optional: Remove image
docker rmi product-service:test
```

## Troubleshooting

### Issue: Build Fails

**Check**:
```bash
# View build logs
docker build --no-cache -t product-service:test --build-arg MODULE_NAME=product-service .
```

**Common Causes**:
- Network issues downloading dependencies
- Out of disk space
- Docker daemon not running

### Issue: Container Won't Start

**Check logs**:
```bash
docker logs product-service-test
```

**Common Causes**:
1. **Database connection failure**
   - Make sure MySQL is running
   - Use `host.docker.internal` instead of `localhost` on Windows/Mac
   - Check credentials are correct

2. **Port already in use**
   ```bash
   # Check if port 8081 is in use
   netstat -ano | findstr :8081
   
   # Use different port
   docker run -p 8082:8081 ...
   ```

3. **Flyway migration error**
   - Database must exist (`inventory`)
   - User must have CREATE/ALTER permissions

### Issue: Health Check Fails

**Symptoms**: `curl http://localhost:8081/actuator/health` times out or returns error

**Debug Steps**:
```bash
# 1. Check if container is running
docker ps

# 2. Check container logs
docker logs product-service-test

# 3. Check if service is listening
docker exec product-service-test netstat -an | grep 8081

# 4. Try from inside container
docker exec product-service-test curl http://localhost:8081/actuator/health
```

## Success Criteria

Before deploying to AWS, verify:

- [x] Docker image builds successfully
- [x] Container starts without errors
- [x] Health endpoint returns `{"status":"UP"}`
- [x] Database migrations complete
- [x] All CRUD operations work (create category, create product, etc.)
- [x] Comprehensive test suite passes (25/25 tests)
- [x] No memory/CPU issues (check with `docker stats`)
- [x] Logs show no errors or warnings

## Next Steps: AWS Deployment

Once all tests pass locally:

1. **Tag the image** (optional, for versioning):
   ```bash
   docker tag product-service:test product-service:v1.0.0
   ```

2. **Export image** (for manual upload):
   ```bash
   docker save product-service:test -o product-service.tar
   ```

3. **Or push to registry** (recommended):
   ```bash
   # Tag for your registry
   docker tag product-service:test your-registry/product-service:v1.0.0
   
   # Push
   docker push your-registry/product-service:v1.0.0
   ```

4. **Follow AWS_DEPLOYMENT.md** for EC2 setup

## Quick Reference

```bash
# Build
docker build -t product-service:test --build-arg MODULE_NAME=product-service .

# Run
docker run -d --name product-service-test -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=host.docker.internal \
  -e DB_NAME=inventory \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=root \
  product-service:test

# Test
curl http://localhost:8081/actuator/health
./test-final.ps1

# Cleanup
docker stop product-service-test && docker rm product-service-test
```
