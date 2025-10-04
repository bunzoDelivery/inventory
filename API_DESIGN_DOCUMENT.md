# Inventory Service API Design Document

## Overview

The Inventory Service is a reactive microservice built with Spring WebFlux that manages real-time inventory tracking, stock reservations, and availability checks for the Quick Commerce platform. It supports reserve-on-checkout functionality, safety stock management, and provides scalable APIs for bulk operations.

## Table of Contents

1. [Service Architecture](#service-architecture)
2. [Technology Stack](#technology-stack)
3. [API Endpoints](#api-endpoints)
4. [Data Models](#data-models)
5. [Error Handling](#error-handling)
6. [Performance Considerations](#performance-considerations)
7. [Security](#security)
8. [Testing Strategy](#testing-strategy)

## Service Architecture

### Core Features
- ✅ Real-time inventory tracking with optimistic locking
- ✅ Reserve-on-checkout mechanism (15-minute TTL)
- ✅ Safety stock management and low stock alerts
- ✅ Stock movement audit trail
- ✅ Redis caching for performance (5-minute TTL)
- ✅ Event-driven architecture for stock updates and alerts
- ✅ RESTful API with reactive endpoints
- ✅ Docker containerization

### Database Schema
- **inventory_items**: Main inventory data with version control
- **inventory_movements**: Audit trail for all stock changes
- **stock_reservations**: Temporary stock holds for checkout
- **stock_alerts**: Low stock and replenishment alerts

## Technology Stack

- **Framework**: Spring Boot 3.2.5 with Spring WebFlux
- **Language**: Java 17
- **Database**: MySQL 8.0 with R2DBC (reactive)
- **Cache**: Redis 7.0
- **Message Queue**: RabbitMQ 3.12
- **Build Tool**: Maven
- **Containerization**: Docker & Docker Compose

## API Endpoints

### Base URL
```
http://localhost:8081/api/v1/inventory
```

### 1. Get Inventory by SKU
**GET** `/sku/{sku}`

Retrieves inventory information for a specific SKU.

**Parameters:**
- `sku` (path): SKU identifier

**Response:**
```json
{
  "id": 1,
  "sku": "SKU001",
  "productId": 101,
  "storeId": 1,
  "currentStock": 25,
  "reservedStock": 2,
  "safetyStock": 5,
  "maxStock": 100,
  "unitCost": 15.99,
  "version": 1,
  "lastUpdated": "2023-12-01T10:30:00"
}
```

**Error Responses:**
- `404 Not Found`: SKU not found

---

### 2. Check Inventory Availability (Multiple SKUs) ⭐ **NEW**
**POST** `/availability`

Checks availability for multiple products in a store. This is the scalable API for bulk availability checks.

**Request Body:**
```json
{
  "storeId": 1,
  "skus": ["SKU001", "SKU002", "SKU003"]
}
```

**Response:**
```json
{
  "storeId": 1,
  "products": [
    {
      "sku": "SKU001",
      "productId": 101,
      "currentStock": 25,
      "availableStock": 23,
      "reservedStock": 2,
      "safetyStock": 5,
      "inStock": true,
      "lowStock": false,
      "availabilityStatus": "AVAILABLE"
    },
    {
      "sku": "SKU002",
      "productId": 102,
      "currentStock": 3,
      "availableStock": 3,
      "reservedStock": 0,
      "safetyStock": 5,
      "inStock": true,
      "lowStock": true,
      "availabilityStatus": "LOW_STOCK"
    }
  ]
}
```

**Validation:**
- `storeId`: Required, must be positive
- `skus`: Required, must not be empty array

---

### 3. Check Single Inventory Availability
**GET** `/availability/single`

Convenience endpoint for single product availability checks.

**Query Parameters:**
- `storeId` (required): Store identifier
- `sku` (required): SKU identifier

**Response:**
Same format as bulk availability check, but with single product in array.

---

### 4. Reserve Stock
**POST** `/reserve`

Reserves stock for checkout process with 15-minute TTL.

**Request Body:**
```json
{
  "sku": "SKU001",
  "quantity": 2,
  "customerId": "12345",
  "orderId": "ORD-001"
}
```

**Response:**
```json
{
  "reservationId": "RES_1678901234_abcdefgh",
  "sku": "SKU001",
  "quantity": 2,
  "customerId": "12345",
  "orderId": "ORD-001",
  "status": "ACTIVE",
  "expiresAt": "2023-12-01T10:45:00"
}
```

**Error Responses:**
- `400 Bad Request`: Insufficient stock
- `404 Not Found`: SKU not found

---

### 5. Confirm Reservation
**POST** `/reservations/{reservationId}/confirm`

Confirms a reservation, converting it to an actual sale.

**Parameters:**
- `reservationId` (path): Reservation identifier

**Response:**
- `200 OK`: Reservation confirmed successfully

**Error Responses:**
- `404 Not Found`: Reservation not found
- `400 Bad Request`: Invalid reservation status

---

### 6. Cancel Reservation
**POST** `/reservations/{reservationId}/cancel`

Cancels a reservation, releasing the reserved stock.

**Parameters:**
- `reservationId` (path): Reservation identifier

**Response:**
- `200 OK`: Reservation cancelled successfully

**Error Responses:**
- `404 Not Found`: Reservation not found

---

### 7. Add Stock
**POST** `/stock/add`

Adds stock to inventory (for replenishments, returns, etc.).

**Request Body:**
```json
{
  "sku": "SKU001",
  "quantity": 50,
  "reason": "Stock replenishment",
  "referenceId": "PO-002"
}
```

**Response:**
- `200 OK`: Stock added successfully

**Error Responses:**
- `404 Not Found`: SKU not found

---

### 8. Get Low Stock Items
**GET** `/low-stock`

Retrieves items with stock below safety stock level.

**Query Parameters:**
- `storeId` (required): Store identifier

**Response:**
```json
[
  {
    "id": 2,
    "sku": "SKU002",
    "productId": 102,
    "storeId": 1,
    "currentStock": 3,
    "reservedStock": 0,
    "safetyStock": 5,
    "maxStock": 100,
    "unitCost": 12.99,
    "version": 1,
    "lastUpdated": "2023-12-01T10:30:00"
  }
]
```

---

### 9. Get Items Needing Replenishment
**GET** `/replenishment`

Retrieves items that need replenishment (stock <= 1.5 * safety stock).

**Query Parameters:**
- `storeId` (required): Store identifier

**Response:**
Same format as low stock items.

---

### 10. Get Inventory by Barcode
**GET** `/barcode/{barcode}`

Retrieves inventory information by barcode (assumes barcode maps to SKU).

**Parameters:**
- `barcode` (path): Barcode identifier

**Response:**
Same format as "Get Inventory by SKU".

---

### 11. Health Check
**GET** `/health`

Service health check endpoint.

**Response:**
```json
"Inventory Service is healthy"
```

## Data Models

### InventoryAvailabilityRequest
```java
{
  "storeId": Long,        // Required: Store identifier
  "skus": List<String>    // Required: List of SKU identifiers
}
```

### InventoryAvailabilityResponse
```java
{
  "storeId": Long,
  "products": [
    {
      "sku": String,
      "productId": Long,
      "currentStock": Integer,
      "availableStock": Integer,
      "reservedStock": Integer,
      "safetyStock": Integer,
      "inStock": Boolean,
      "lowStock": Boolean,
      "availabilityStatus": String  // "AVAILABLE", "LOW_STOCK", "OUT_OF_STOCK"
    }
  ]
}
```

### ReserveStockRequest
```java
{
  "sku": String,          // Required: SKU identifier
  "quantity": Integer,    // Required: Quantity to reserve (positive)
  "customerId": String,   // Required: Customer identifier
  "orderId": String       // Required: Order identifier
}
```

### StockReservationResponse
```java
{
  "reservationId": String,
  "sku": String,
  "quantity": Integer,
  "customerId": String,
  "orderId": String,
  "status": String,       // "ACTIVE", "CONFIRMED", "CANCELLED", "EXPIRED"
  "expiresAt": String
}
```

### AddStockRequest
```java
{
  "sku": String,          // Required: SKU identifier
  "quantity": Integer,    // Required: Quantity to add (positive)
  "reason": String,       // Optional: Reason for stock addition
  "referenceId": String   // Optional: Reference ID (PO, return, etc.)
}
```

## Error Handling

### HTTP Status Codes
- `200 OK`: Successful operation
- `400 Bad Request`: Invalid request data or business logic violation
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Unexpected server error

### Error Response Format
```json
{
  "timestamp": "2023-12-01T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient stock available",
  "path": "/api/v1/inventory/reserve"
}
```

### Business Logic Errors
- **InsufficientStockException**: Not enough stock available
- **InventoryNotFoundException**: SKU not found
- **ReservationNotFoundException**: Reservation not found
- **InvalidReservationException**: Invalid reservation status
- **OptimisticLockingException**: Concurrent modification detected

## Performance Considerations

### Caching Strategy
- **Redis Cache**: 5-minute TTL for frequently accessed inventory data
- **Cache Key Pattern**: `inventory:{storeId}:{sku}`
- **Cache Invalidation**: On stock updates, reservations, and confirmations

### Database Optimization
- **Optimistic Locking**: Version field prevents concurrent modification issues
- **Bulk Queries**: `IN` clause for multiple SKU lookups
- **Indexes**: On `sku`, `store_id`, and composite indexes for common queries

### Reactive Programming
- **Non-blocking I/O**: All database operations are reactive
- **Backpressure Handling**: Built-in flow control for high-load scenarios
- **Connection Pooling**: Optimized R2DBC connection pool settings

## Security

### Authentication
- **JWT-based Authentication**: OAuth2 Resource Server integration
- **Role-based Access Control**: Store-specific access control
- **API Gateway Integration**: Kong/Traefik for centralized auth

### Data Protection
- **Input Validation**: Comprehensive validation on all endpoints
- **SQL Injection Prevention**: Parameterized queries with R2DBC
- **XSS Protection**: Request/response sanitization

## Testing Strategy

### Unit Tests
- Service layer business logic
- Repository layer data access
- Controller layer API contracts

### Integration Tests
- **Testcontainers**: MySQL, Redis, RabbitMQ for realistic testing
- **WebTestClient**: Reactive web layer testing
- **Database Transactions**: Rollback for test isolation

### Performance Tests
- **Load Testing**: Bulk availability checks
- **Concurrency Testing**: Optimistic locking scenarios
- **Cache Performance**: Redis hit/miss ratios

## API Usage Examples

### Bulk Availability Check (Recommended)
```bash
curl -X POST http://localhost:8081/api/v1/inventory/availability \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <jwt-token>" \
  -d '{
    "storeId": 1,
    "skus": ["SKU001", "SKU002", "SKU003"]
  }'
```

### Reserve Stock for Checkout
```bash
curl -X POST http://localhost:8081/api/v1/inventory/reserve \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <jwt-token>" \
  -d '{
    "sku": "SKU001",
    "quantity": 2,
    "customerId": "12345",
    "orderId": "ORD-001"
  }'
```

### Confirm Reservation
```bash
curl -X POST http://localhost:8081/api/v1/inventory/reservations/RES_1234567890_ABC12345/confirm \
  -H "Authorization: Bearer <jwt-token>"
```

## Deployment

### Docker Compose Setup
```yaml
services:
  inventory-service:
    build: .
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_HOST: mysql
      REDIS_HOST: redis
      RABBITMQ_HOST: rabbitmq
    depends_on:
      - mysql
      - redis
      - rabbitmq
```

### Environment Variables
| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_R2DBC_URL` | Database connection URL | `r2dbc:mysql://localhost:3306/quickcommerce` |
| `SPRING_REDIS_HOST` | Redis host | `localhost` |
| `SPRING_REDIS_PORT` | Redis port | `6379` |
| `SPRING_RABBITMQ_HOST` | RabbitMQ host | `localhost` |
| `SERVER_PORT` | Application port | `8081` |

## Monitoring & Observability

### Health Checks
- **Actuator Endpoints**: `/actuator/health`, `/actuator/prometheus`
- **Database Connectivity**: MySQL connection health
- **Cache Status**: Redis connection health
- **Message Queue**: RabbitMQ connection health

### Metrics
- **Custom Metrics**: Stock levels, reservation counts, cache hit rates
- **Micrometer Integration**: Prometheus-compatible metrics
- **Business Metrics**: Low stock alerts, reservation expirations

### Logging
- **Structured Logging**: JSON format for log aggregation
- **Log Levels**: Configurable per environment
- **Audit Trail**: All stock movements and reservations logged

---

## Review Checklist

### API Design
- [ ] RESTful endpoint design follows conventions
- [ ] Request/response models are well-defined
- [ ] Error handling is comprehensive
- [ ] Validation rules are clear

### Performance
- [ ] Bulk operations are supported
- [ ] Caching strategy is appropriate
- [ ] Database queries are optimized
- [ ] Reactive programming is properly implemented

### Security
- [ ] Authentication is properly integrated
- [ ] Input validation prevents injection attacks
- [ ] Authorization controls are in place
- [ ] Sensitive data is protected

### Maintainability
- [ ] Code is well-structured and documented
- [ ] Configuration is externalized
- [ ] Error messages are user-friendly
- [ ] API versioning strategy is clear

---

**Document Version**: 1.0  
**Last Updated**: December 2023  
**Review Date**: TBD

