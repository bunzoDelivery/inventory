# Inventory Service

A reactive microservice for managing inventory in the Quick Commerce platform, built with Java 17 and Spring WebFlux.

## Overview

This service provides real-time inventory management capabilities including:
- Stock tracking and reservations
- Reserve-on-checkout functionality
- Low stock alerts
- Audit trail for all stock movements
- Redis caching for performance
- Event-driven architecture

## Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring WebFlux** (Reactive programming)
- **Spring Data R2DBC** (Reactive database access)
- **MySQL 8.0** (Primary database)
- **Redis** (Caching layer)
- **RabbitMQ** (Message broker)
- **Docker** (Containerization)

## Features

### Core Functionality
- ✅ Real-time inventory tracking
- ✅ Stock reservations (15-minute TTL)
- ✅ Reserve-on-checkout mechanism
- ✅ Safety stock management
- ✅ Low stock alerts
- ✅ Stock movement audit trail
- ✅ Barcode scanning support
- ✅ Redis caching (5-minute TTL)

### API Endpoints
- `GET /api/v1/inventory/sku/{sku}` - Get inventory by SKU
- `POST /api/v1/inventory/reserve` - Reserve stock for checkout
- `POST /api/v1/inventory/reservations/{id}/confirm` - Confirm reservation
- `POST /api/v1/inventory/reservations/{id}/cancel` - Cancel reservation
- `POST /api/v1/inventory/stock/add` - Add stock to inventory
- `GET /api/v1/inventory/low-stock` - Get low stock items
- `GET /api/v1/inventory/replenishment` - Get items needing replenishment
- `GET /api/v1/inventory/barcode/{barcode}` - Get inventory by barcode
- `GET /api/v1/inventory/health` - Health check

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- Docker and Docker Compose

### Using Docker Compose (Recommended)

1. Clone the repository:
```bash
git clone <repository-url>
cd inventory-service
```

2. Start all services:
```bash
docker-compose up -d
```

This will start:
- MySQL database on port 3306
- Redis on port 6379
- RabbitMQ on port 5672 (Management UI on 15672)
- Inventory Service on port 8081

3. Check service health:
```bash
curl http://localhost:8081/actuator/health
```

### Manual Setup

1. Start required services:
```bash
# Start MySQL
docker run -d --name mysql \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -e MYSQL_DATABASE=quickcommerce \
  -e MYSQL_USER=inventory_user \
  -e MYSQL_PASSWORD=secure_password \
  -p 3306:3306 mysql:8.0

# Start Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Start RabbitMQ
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management-alpine
```

2. Initialize database:
```bash
# Copy the init script to MySQL container
docker cp scripts/init-db.sql mysql:/tmp/init-db.sql

# Execute the script
docker exec -i mysql mysql -uinventory_user -psecure_password quickcommerce < scripts/init-db.sql
```

3. Run the application:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | Database host | localhost |
| `DB_PORT` | Database port | 3306 |
| `DB_NAME` | Database name | quickcommerce |
| `DB_USERNAME` | Database username | inventory_user |
| `DB_PASSWORD` | Database password | secure_password |
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `RABBITMQ_HOST` | RabbitMQ host | localhost |
| `RABBITMQ_PORT` | RabbitMQ port | 5672 |
| `SERVER_PORT` | Application port | 8081 |

### Application Profiles

- `dev` - Development configuration with debug logging
- `prod` - Production configuration with optimized settings
- `test` - Test configuration with in-memory database

## API Usage Examples

### Reserve Stock
```bash
curl -X POST http://localhost:8081/api/v1/inventory/reserve \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "SKU001",
    "quantity": 2,
    "customerId": "12345",
    "orderId": "ORD-001"
  }'
```

### Confirm Reservation
```bash
curl -X POST http://localhost:8081/api/v1/inventory/reservations/RES_1234567890_ABC12345/confirm
```

### Add Stock
```bash
curl -X POST http://localhost:8081/api/v1/inventory/stock/add \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "SKU001",
    "quantity": 50,
    "reason": "Stock replenishment",
    "referenceId": "PO-002"
  }'
```

### Get Low Stock Items
```bash
curl http://localhost:8081/api/v1/inventory/low-stock?storeId=1
```

## Database Schema

### Core Tables
- `inventory_items` - Main inventory data
- `inventory_movements` - Audit trail for all stock changes
- `stock_reservations` - Temporary stock holds for checkout
- `stock_alerts` - Low stock alert configurations

### Sample Data
The database initialization script includes sample data with 10 inventory items for testing.

## Monitoring and Observability

### Health Checks
- Application health: `/actuator/health`
- Database connectivity
- Redis connectivity
- RabbitMQ connectivity

### Metrics
- Prometheus metrics: `/actuator/prometheus`
- Application metrics
- Database connection pool metrics
- Cache hit/miss ratios

### Logging
- Structured JSON logging
- Correlation IDs for request tracing
- Configurable log levels

## Development

### Running Tests
```bash
mvn test
```

### Code Quality
```bash
# Check code style
mvn checkstyle:check

# Run static analysis
mvn spotbugs:check
```

### Building Docker Image
```bash
mvn clean package -Pdocker
docker build -t quickcommerce/inventory-service:latest .
```

## Architecture

### Reactive Programming
The service uses Spring WebFlux for non-blocking I/O operations, providing better resource utilization and scalability.

### Caching Strategy
- Redis caching with 5-minute TTL for frequently accessed inventory items
- Automatic cache invalidation on stock updates
- Cache-aside pattern implementation

### Event-Driven Design
- Stock movement events for audit trail
- Low stock alert events for notifications
- Integration with message queue for other services

### Concurrency Handling
- Optimistic locking for inventory updates
- Version-based conflict resolution
- Transaction management with R2DBC

## Performance Considerations

### Database Optimization
- Proper indexing on frequently queried columns
- Connection pooling with optimal settings
- Query optimization for stock checks

### Caching Strategy
- Redis caching for hot inventory data
- Configurable cache TTL
- Cache invalidation on updates

### Resource Management
- Reactive streams for memory efficiency
- Connection pooling
- Proper timeout configurations

## Security

### Authentication
- JWT-based authentication (configurable)
- Role-based access control (RBAC)
- Per-store access control

### Data Protection
- Input validation with Bean Validation
- SQL injection prevention via R2DBC
- Sensitive data handling

## Troubleshooting

### Common Issues

1. **Database Connection Issues**
   - Check MySQL service status
   - Verify connection parameters
   - Check firewall settings

2. **Redis Connection Issues**
   - Verify Redis service status
   - Check Redis configuration
   - Monitor Redis memory usage

3. **High Memory Usage**
   - Check for memory leaks
   - Monitor JVM heap usage
   - Review cache settings

### Logs
Application logs are available in:
- Console output (development)
- `/var/log/inventory-service/` (production)
- Docker logs: `docker logs inventory-service`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions:
- Create an issue in the repository
- Contact the development team
- Check the documentation wiki