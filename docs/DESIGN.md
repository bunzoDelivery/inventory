# Quick Commerce Platform - Design Document

**Version:** 1.0
**Last Updated:** 2025-01-04
**Status:** Active Development

---

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Business Requirements](#business-requirements)
3. [Architecture Overview](#architecture-overview)
4. [Pre-Order Services](#pre-order-services)
5. [Database Design](#database-design)
6. [API Specifications](#api-specifications)
7. [Technology Stack](#technology-stack)
8. [Development Phases](#development-phases)
9. [Non-Functional Requirements](#non-functional-requirements)

---

## Executive Summary

### Product Overview
Quick commerce platform delivering groceries and daily essentials in 20-25 minutes to customers in Lusaka, Zambia.

### Launch Specifications
- **Geographic Coverage:** Lusaka, Zambia (single dark store)
- **Catalog Size:** 1500 SKUs
- **Delivery Promise:** 20-25 minutes
- **Serviceable Radius:** 5 km from dark store
- **Architecture:** Modular monolith (Spring Boot 3.2)

### Technical Approach
- **Monolith First:** Single deployable application with well-defined module boundaries
- **Reactive Stack:** Spring WebFlux + R2DBC for high throughput
- **Cost Optimization:** Minimal infrastructure footprint, MySQL-based search (no Elasticsearch initially)
- **Future-Proof:** Designed for easy extraction into microservices when scaling beyond 5 dark stores

---

## Business Requirements

### Functional Requirements

#### Pre-Order (Customer-Facing)
1. **Product Discovery**
   - Browse 1500 SKUs organized by categories
   - Search products by name, brand, category
   - View product details (image, price, description, availability)
   - Filter and sort (price, popularity)

2. **Account Management**
   - Register/Login with phone number
   - Manage delivery addresses
   - Validate address serviceability (within 5km radius)
   - View order history

3. **Shopping Cart**
   - Add/remove items
   - Update quantities
   - Persistent cart across sessions
   - Real-time stock validation
   - Price calculation with promotions

4. **Checkout Preparation**
   - Apply promo codes
   - Calculate delivery fee (free for v1)
   - Validate cart before order placement
   - Reserve inventory during checkout

#### Post-Order (Phase 2 - Deferred)
- Order placement and payment
- Order fulfillment (picker assignment, packing)
- Delivery management (rider assignment, tracking)
- Customer notifications

### Non-Functional Requirements

#### Performance
- API response time: <200ms (p95)
- Product search: <300ms (p95)
- Cart operations: <150ms (p95)
- Concurrent users: 100 simultaneous customers
- Order throughput: 100 orders/hour

#### Availability
- Uptime: 99.5% (Target for v1)
- Planned downtime: After midnight for deployments

#### Scalability
- Horizontal scaling capability for application tier
- Database connection pooling (10-20 connections)
- Redis caching for hot data

#### Security
- JWT-based authentication
- BCrypt password hashing
- Rate limiting (100 requests/minute per user)
- HTTPS only in production

---

## Architecture Overview

### Architecture Style: Modular Monolith

```
┌──────────────────────────────────────────────────────────┐
│         QUICK COMMERCE MONOLITH APPLICATION              │
│                                                          │
│  ┌────────────────────────────────────────────────┐    │
│  │         API LAYER (REST Controllers)           │    │
│  │  /catalog  /inventory  /customer  /cart        │    │
│  │  /pricing  /search     /auth                   │    │
│  └────────────────────────────────────────────────┘    │
│                       ↓                                  │
│  ┌────────────────────────────────────────────────┐    │
│  │        SERVICE LAYER (Business Logic)          │    │
│  │                                                │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐   │    │
│  │  │ Catalog  │  │Inventory │  │ Customer │   │    │
│  │  │ Service  │  │ Service  │  │ Service  │   │    │
│  │  └──────────┘  └──────────┘  └──────────┘   │    │
│  │                                                │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐   │    │
│  │  │   Cart   │  │ Pricing  │  │  Search  │   │    │
│  │  │ Service  │  │ Service  │  │ Service  │   │    │
│  │  └──────────┘  └──────────┘  └──────────┘   │    │
│  └────────────────────────────────────────────────┘    │
│                       ↓                                  │
│  ┌────────────────────────────────────────────────┐    │
│  │      REPOSITORY LAYER (Data Access)            │    │
│  │  ProductRepo  InventoryRepo  CustomerRepo      │    │
│  │  CartRepo     PriceRepo      StoreRepo         │    │
│  └────────────────────────────────────────────────┘    │
│                       ↓                                  │
│  ┌────────────────────────────────────────────────┐    │
│  │        DOMAIN MODELS (Entities)                │    │
│  │  Product, Inventory, Customer, Cart, Store     │    │
│  └────────────────────────────────────────────────┘    │
│                                                          │
└──────────────────────────────────────────────────────────┘
                       ↓
┌──────────────────────────────────────────────────────────┐
│              INFRASTRUCTURE LAYER                        │
│  - MySQL (Primary Database)                              │
│  - Redis (Cache + Session)                               │
│  - RabbitMQ (Event Bus)                                  │
│  - Prometheus (Metrics)                                  │
│  - Zipkin (Tracing)                                      │
└──────────────────────────────────────────────────────────┘
```

### Module Structure

```
quick-commerce-monolith/
├── src/main/java/com/quickcommerce/
│   ├── catalog/                    # Module 1: Product Catalog
│   │   ├── controller/             # REST endpoints
│   │   ├── service/                # Business logic
│   │   ├── repository/             # Data access
│   │   ├── domain/                 # Entities (Product, Category)
│   │   └── dto/                    # Request/Response DTOs
│   │
│   ├── inventory/                  # Module 2: Inventory Management
│   │   ├── controller/             # Already built ✓
│   │   ├── service/                # Already built ✓
│   │   ├── repository/             # Already built ✓
│   │   ├── domain/                 # Already built ✓
│   │   └── dto/                    # Already built ✓
│   │
│   ├── customer/                   # Module 3: Customer Management
│   │   ├── controller/             # Registration, login, profile
│   │   ├── service/                # Authentication, address validation
│   │   ├── repository/             # Customer, Address data access
│   │   ├── domain/                 # Customer, Address entities
│   │   └── dto/                    # DTOs
│   │
│   ├── cart/                       # Module 4: Shopping Cart
│   │   ├── controller/             # Cart CRUD operations
│   │   ├── service/                # Cart management, validation
│   │   ├── repository/             # Cart data access
│   │   ├── domain/                 # Cart, CartItem entities
│   │   └── dto/                    # DTOs
│   │
│   ├── pricing/                    # Module 5: Pricing & Promotions
│   │   ├── controller/             # Price, promo endpoints
│   │   ├── service/                # Pricing calculation, promo validation
│   │   ├── repository/             # Price, Promotion data access
│   │   ├── domain/                 # ProductPrice, Promotion entities
│   │   └── dto/                    # DTOs
│   │
│   ├── search/                     # Module 6: Search & Discovery
│   │   ├── controller/             # Search endpoints
│   │   ├── service/                # Search logic, trending products
│   │   └── dto/                    # DTOs
│   │
│   ├── auth/                       # Cross-cutting: Authentication
│   │   ├── config/                 # Security configuration
│   │   ├── filter/                 # JWT filter
│   │   └── service/                # Token generation/validation
│   │
│   ├── common/                     # Shared utilities
│   │   ├── exception/              # Global exception handler
│   │   ├── config/                 # Common configurations
│   │   └── util/                   # Utility classes
│   │
│   └── QuickCommerceApplication.java
│
├── src/main/resources/
│   ├── application.yml             # Configuration
│   └── db/migration/               # Flyway migrations
│       ├── V1__create_inventory_tables.sql
│       ├── V2__create_stores_table.sql
│       ├── V3__create_catalog_tables.sql
│       ├── V4__create_customer_tables.sql
│       ├── V5__create_cart_tables.sql
│       └── V6__create_pricing_tables.sql
│
└── src/test/java/com/quickcommerce/
    └── [mirror structure for tests]
```

---

## Pre-Order Services

Pre-order services encompass everything that happens BEFORE a customer places an order.

### 1. Catalog Service

**Responsibility:** Product master data management and browsing

**Core Features:**
- Product CRUD operations (admin)
- Category management
- Product listing with pagination
- Product details with availability
- Integration with Inventory for stock status

**Key Entities:**
- `Product`: SKU, name, description, brand, unit, price
- `Category`: Hierarchical categories with parent-child relationships

**APIs:**
```
GET  /api/v1/catalog/products              # Browse products
GET  /api/v1/catalog/products/{id}         # Product details
GET  /api/v1/catalog/categories            # Category tree
POST /api/v1/catalog/products              # Admin: Create product
PUT  /api/v1/catalog/products/{id}         # Admin: Update product
```

### 2. Inventory Service (Already Built ✓)

**Responsibility:** Real-time stock tracking and reservation

**Core Features:**
- Stock level management
- Stock reservation with 15-min TTL
- Bulk availability checks
- Low stock alerts
- Atomic stock operations

**Enhancements Needed:**
- Add Store entity with geospatial data
- Add nearest-store-with-inventory API
- Enhance availability response with distance/ETA

**APIs:**
```
POST /api/v1/inventory/availability        # Bulk stock check
GET  /api/v1/inventory/nearest-store       # Find nearest store (NEW)
POST /api/v1/inventory/reserve             # Reserve stock
POST /api/v1/inventory/reservations/{id}/confirm
POST /api/v1/inventory/reservations/{id}/cancel
```

### 3. Customer Service

**Responsibility:** Customer identity and address management

**Core Features:**
- Registration with phone number
- JWT-based authentication
- Profile management
- Delivery address CRUD
- Address serviceability validation (5km radius check)

**Key Entities:**
- `Customer`: Phone, email, name, password
- `CustomerAddress`: GPS coordinates, landmark, serviceability status

**APIs:**
```
POST /api/v1/customers/register            # Sign up
POST /api/v1/customers/login               # JWT login
GET  /api/v1/customers/{id}/profile        # Profile
POST /api/v1/customers/{id}/addresses      # Add address
GET  /api/v1/customers/{id}/addresses      # List addresses
POST /api/v1/customers/addresses/validate  # Check serviceability
```

### 4. Cart Service

**Responsibility:** Shopping cart management

**Core Features:**
- Cart CRUD operations
- Persistent cart (Redis + MySQL)
- Real-time inventory validation
- Cart total calculation
- Pre-checkout validation

**Key Entities:**
- `Cart`: Customer association, status
- `CartItem`: Product, quantity, price snapshot

**APIs:**
```
GET  /api/v1/cart?customerId={id}          # Get active cart
POST /api/v1/cart/items                    # Add to cart
PUT  /api/v1/cart/items/{id}               # Update quantity
DELETE /api/v1/cart/items/{id}             # Remove from cart
POST /api/v1/cart/{id}/validate            # Pre-checkout validation
```

### 5. Pricing Service

**Responsibility:** Product pricing and promotions

**Core Features:**
- Base product pricing
- Promotional pricing (time-bound)
- Promo code validation
- Delivery fee calculation
- Cart total calculation with discounts

**Key Entities:**
- `ProductPrice`: Base price, sale price, validity period
- `Promotion`: Discount type, value, min order, usage limit

**APIs:**
```
GET  /api/v1/pricing/products/{id}         # Get product price
POST /api/v1/pricing/promotions/validate   # Validate promo code
POST /api/v1/pricing/calculate             # Calculate cart total
```

### 6. Search Service

**Responsibility:** Product search and discovery

**Core Features:**
- Full-text product search (MySQL initially)
- Category filtering
- Price range filtering
- Sorting (price, popularity)
- Trending products

**Implementation:**
- v1.0: MySQL FULLTEXT index
- v1.5+: Elasticsearch (when catalog > 5K SKUs)

**APIs:**
```
GET  /api/v1/search/products?q={query}&category={id}&sort={field}
GET  /api/v1/search/trending?limit=10
GET  /api/v1/search/popular?categoryId={id}
```

### Service Interaction Pattern

```
Customer Mobile App
        ↓
    API Gateway
        ↓
┌───────┴────────┐
│                │
│  Browse        │ → CatalogService → InventoryService (check stock)
│  Products      │
│                │
│  Add to        │ → CartService → InventoryService (validate availability)
│  Cart          │              → PricingService (get price)
│                │
│  Apply         │ → PricingService (validate promo)
│  Promo         │
│                │
│  Validate      │ → CartService → InventoryService (final stock check)
│  Cart          │              → PricingService (final price calc)
│                │
└────────────────┘
```

---

## Database Design

### ER Diagram Overview

```
┌─────────────┐       ┌─────────────┐
│  stores     │◄──────│inventory_   │
│             │       │   items     │
└─────────────┘       └─────────────┘
                             ▲
                             │
┌─────────────┐              │
│ categories  │              │
│             │              │
└──────┬──────┘              │
       │                     │
       │                     │
       ▼                     │
┌─────────────┐       ┌──────┴──────┐
│  products   │◄──────│stock_       │
│             │       │reservations │
└──────┬──────┘       └─────────────┘
       │
       │
       │              ┌─────────────┐
       │              │ customers   │
       │              └──────┬──────┘
       │                     │
       │                     │
       ▼                     ▼
┌─────────────┐       ┌─────────────┐
│product_     │       │customer_    │
│  prices     │       │ addresses   │
└─────────────┘       └─────────────┘
       │
       │                     │
       │                     │
       │                     ▼
       │              ┌─────────────┐
       │              │   carts     │
       │              └──────┬──────┘
       │                     │
       │                     │
       ▼                     ▼
┌─────────────┐       ┌─────────────┐
│ promotions  │       │ cart_items  │
└─────────────┘       └─────────────┘
```

### Schema Details

#### Stores Table (NEW - Phase 1)
```sql
CREATE TABLE stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    serviceable_radius_km INT DEFAULT 5,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_active (is_active),
    INDEX idx_location (latitude, longitude)
);

-- Initial data
INSERT INTO stores (id, name, address, latitude, longitude, serviceable_radius_km, is_active)
VALUES (1, 'Lusaka Dark Store 1', 'Plot 5, Great East Road, Lusaka', -15.3875, 28.3228, 5, TRUE);
```

#### Products Table (Phase 2)
```sql
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id BIGINT,
    brand VARCHAR(100),
    unit_type VARCHAR(20) COMMENT 'kg, liter, piece, pack',
    unit_value DECIMAL(10,2) COMMENT '1.5 for 1.5kg',
    image_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_category (category_id),
    INDEX idx_sku (sku),
    INDEX idx_active (is_active),
    FULLTEXT INDEX idx_search (name, description, brand)
);
```

#### Categories Table (Phase 2)
```sql
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT COMMENT 'For subcategories',
    display_order INT DEFAULT 0,
    icon_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (parent_id) REFERENCES categories(id),
    INDEX idx_parent (parent_id),
    INDEX idx_active (is_active)
);
```

#### Inventory Items Table (Already Exists ✓)
```sql
CREATE TABLE inventory_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(255) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    current_stock INT NOT NULL DEFAULT 0,
    reserved_stock INT NOT NULL DEFAULT 0,
    safety_stock INT NOT NULL DEFAULT 0,
    max_stock INT,
    unit_cost DECIMAL(10, 2),
    version BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (store_id) REFERENCES stores(id),
    INDEX idx_store_sku (store_id, sku),
    INDEX idx_product (product_id),
    INDEX idx_low_stock (current_stock, safety_stock)
);
```

#### Customers Table (Phase 3)
```sql
CREATE TABLE customers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_phone (phone_number),
    INDEX idx_email (email),
    INDEX idx_active (is_active)
);
```

#### Customer Addresses Table (Phase 3)
```sql
CREATE TABLE customer_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    label VARCHAR(50) COMMENT 'Home, Office, etc.',
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    landmark VARCHAR(255),
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    is_serviceable BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE,
    INDEX idx_customer (customer_id),
    INDEX idx_location (latitude, longitude)
);
```

#### Carts Table (Phase 4)
```sql
CREATE TABLE carts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE, CHECKED_OUT, ABANDONED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (store_id) REFERENCES stores(id),
    INDEX idx_customer (customer_id),
    INDEX idx_status (status)
);
```

#### Cart Items Table (Phase 4)
```sql
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL COMMENT 'Price snapshot at add time',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id),
    UNIQUE KEY unique_cart_product (cart_id, product_id),
    INDEX idx_cart (cart_id)
);
```

#### Product Prices Table (Phase 5)
```sql
CREATE TABLE product_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    base_price DECIMAL(10,2) NOT NULL,
    sale_price DECIMAL(10,2) COMMENT 'Promotional price',
    effective_from TIMESTAMP,
    effective_to TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (store_id) REFERENCES stores(id),
    INDEX idx_product_store (product_id, store_id),
    INDEX idx_active (is_active),
    INDEX idx_validity (effective_from, effective_to)
);
```

#### Promotions Table (Phase 5)
```sql
CREATE TABLE promotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) UNIQUE,
    name VARCHAR(255) NOT NULL,
    discount_type VARCHAR(20) COMMENT 'PERCENTAGE, FIXED_AMOUNT',
    discount_value DECIMAL(10,2) NOT NULL,
    min_order_value DECIMAL(10,2),
    max_discount_amount DECIMAL(10,2),
    valid_from TIMESTAMP NOT NULL,
    valid_to TIMESTAMP NOT NULL,
    usage_limit INT COMMENT 'Max total uses',
    usage_count INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_code (code),
    INDEX idx_validity (valid_from, valid_to, is_active)
);
```

---

## API Specifications

### Base URL
```
Production: https://api.quickcommerce.zm/api/v1
Development: http://localhost:8081/api/v1
```

### Authentication
All customer-facing APIs (except register/login) require JWT token in header:
```
Authorization: Bearer <jwt_token>
```

### Common Response Format
```json
{
  "success": true,
  "data": { /* response payload */ },
  "error": null,
  "timestamp": "2025-01-04T10:30:00Z"
}
```

### Error Response Format
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVENTORY_NOT_FOUND",
    "message": "Product not available in your area",
    "status": 404
  },
  "timestamp": "2025-01-04T10:30:00Z"
}
```

### API Endpoints Summary

#### Catalog Service
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | /catalog/products | Browse products | No |
| GET | /catalog/products/{id} | Product details | No |
| GET | /catalog/categories | Category tree | No |

#### Inventory Service
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | /inventory/availability | Bulk stock check | No |
| GET | /inventory/nearest-store | Find nearest store | No |
| POST | /inventory/reserve | Reserve stock | Yes |

#### Customer Service
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | /customers/register | Sign up | No |
| POST | /customers/login | Login (get JWT) | No |
| GET | /customers/{id}/profile | Get profile | Yes |
| POST | /customers/{id}/addresses | Add address | Yes |
| POST | /customers/addresses/validate | Check serviceability | No |

#### Cart Service
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | /cart?customerId={id} | Get active cart | Yes |
| POST | /cart/items | Add to cart | Yes |
| PUT | /cart/items/{id} | Update quantity | Yes |
| DELETE | /cart/items/{id} | Remove from cart | Yes |
| POST | /cart/{id}/validate | Pre-checkout validation | Yes |

#### Pricing Service
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | /pricing/products/{id} | Get product price | No |
| POST | /pricing/promotions/validate | Validate promo code | Yes |
| POST | /pricing/calculate | Calculate cart total | Yes |

#### Search Service
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | /search/products | Search products | No |
| GET | /search/trending | Trending products | No |

---

## Technology Stack

### Backend Framework
- **Spring Boot:** 3.2.0
- **Java Version:** 17
- **Reactive:** Spring WebFlux (Non-blocking I/O)
- **Database Access:** Spring Data R2DBC (Reactive)
- **Security:** Spring Security + JWT
- **Build Tool:** Maven

### Databases & Caching
- **Primary Database:** MySQL 8.0 with R2DBC driver (io.asyncer:r2dbc-mysql)
- **Cache:** Redis 7.0
  - Session management
  - Product catalog cache (TTL: 5 min)
  - Inventory cache (TTL: 1 min)
  - Cart persistence
- **Search:** MySQL Full-Text Index (v1.0)
  - Migration to Elasticsearch planned for v1.5+ (catalog > 5K SKUs)

### Messaging & Events
- **Message Broker:** RabbitMQ 3.12
  - Stock movement events
  - Low stock alerts
  - Future: Order events, notification events

### Monitoring & Observability
- **Metrics:** Prometheus + Grafana
- **Distributed Tracing:** Zipkin
- **Logging:** Logback with JSON format
- **Health Checks:** Spring Actuator

### External Services (Future)
- **Payment Gateway:** TBD (M-Pesa, Airtel Money, Zamtel Money)
- **SMS:** TBD (for OTP and notifications)
- **Object Storage:** AWS S3 / CloudFlare R2 (for product images)

### Development Tools
- **API Documentation:** SpringDoc OpenAPI
- **Database Migration:** Flyway
- **Testing:** JUnit 5, Reactor Test, Testcontainers
- **Code Quality:** SonarQube (optional)

---

## Development Phases

### Phase 1: Foundation & Inventory Enhancement (Week 1)
**Status:** In Progress

**Tasks:**
- [x] Design document creation
- [ ] Create stores table with geospatial support
- [ ] Add Store entity and repository
- [ ] Implement nearest-store API with distance calculation
- [ ] Enhance inventory availability API with ETA
- [ ] Write integration tests

**Deliverables:**
- Store management foundation
- Geospatial queries for store selection
- Updated inventory APIs

### Phase 2: Catalog Service (Week 2)
**Status:** Planned

**Tasks:**
- [ ] Create catalog module structure
- [ ] Implement Product and Category entities
- [ ] Create repositories with R2DBC
- [ ] Add MySQL FULLTEXT index for search
- [ ] Implement CatalogService with inventory integration
- [ ] Create REST APIs (browse, details, categories)
- [ ] Write integration tests

**Deliverables:**
- Complete catalog browsing
- Product details with stock availability
- Category hierarchy

### Phase 3: Customer Service (Week 3)
**Status:** Planned

**Tasks:**
- [ ] Create customer module structure
- [ ] Implement Customer and CustomerAddress entities
- [ ] Add JWT authentication configuration
- [ ] Implement registration and login
- [ ] Add address CRUD with geospatial validation
- [ ] Write integration tests

**Deliverables:**
- Customer registration/login
- JWT-based authentication
- Address management with serviceability check

### Phase 4: Cart Service (Week 4)
**Status:** Planned

**Tasks:**
- [ ] Create cart module structure
- [ ] Implement Cart and CartItem entities
- [ ] Add Redis caching for active carts
- [ ] Implement cart CRUD operations
- [ ] Add real-time inventory validation
- [ ] Add pre-checkout validation
- [ ] Write integration tests

**Deliverables:**
- Persistent shopping cart
- Real-time stock validation
- Cart total calculation

### Phase 5: Pricing Service (Week 5)
**Status:** Planned

**Tasks:**
- [ ] Create pricing module structure
- [ ] Implement ProductPrice and Promotion entities
- [ ] Add pricing calculation service
- [ ] Implement promo code validation
- [ ] Add delivery fee calculation
- [ ] Write integration tests

**Deliverables:**
- Product pricing
- Promotional pricing
- Promo code engine

### Phase 6: Search Service (Week 5-6)
**Status:** Planned

**Tasks:**
- [ ] Create search module structure
- [ ] Implement MySQL FULLTEXT search
- [ ] Add filters (category, price range, brand)
- [ ] Add sorting (price, popularity)
- [ ] Add trending products feature
- [ ] Write integration tests

**Deliverables:**
- Product search with filters
- Trending products
- Search analytics

### Phase 7: Integration & Testing (Week 6-7)
**Status:** Planned

**Tasks:**
- [ ] End-to-end integration testing
- [ ] Load testing (target: 100 concurrent users)
- [ ] Security testing
- [ ] Performance optimization
- [ ] API documentation (OpenAPI/Swagger)
- [ ] Deployment scripts

**Deliverables:**
- Production-ready application
- Complete API documentation
- Deployment guide

---

## Non-Functional Requirements

### Performance Targets
- **API Response Time:** <200ms (p95)
- **Product Search:** <300ms (p95)
- **Cart Operations:** <150ms (p95)
- **Database Queries:** <100ms (p95)

### Scalability Targets
- **Concurrent Users:** 100 simultaneous customers
- **Order Throughput:** 100 orders/hour
- **Product Catalog:** 1500 SKUs (v1.0), scalable to 10K+
- **Database Connections:** 10-20 connections in pool

### Availability
- **Uptime:** 99.5% (target for v1)
- **Planned Downtime:** After midnight (1-3 AM) for deployments
- **RTO (Recovery Time Objective):** < 15 minutes
- **RPO (Recovery Point Objective):** < 5 minutes

### Security Requirements
- **Authentication:** JWT with 24-hour expiry
- **Password Storage:** BCrypt (cost factor: 12)
- **HTTPS:** TLS 1.2+ in production
- **Rate Limiting:** 100 requests/minute per user
- **API Security:** CORS configuration for mobile apps

### Monitoring & Alerting
- **Uptime Monitoring:** Pingdom / UptimeRobot
- **Error Rate Alert:** > 1% error rate
- **Response Time Alert:** p95 > 500ms
- **Database Connection Pool Alert:** > 80% utilization
- **Disk Space Alert:** > 80% usage

---

## Future Enhancements (Post-Launch)

### v1.1 (3 months post-launch)
- Expiry management for perishables (FEFO)
- Customer favorites/wishlist
- Product ratings and reviews
- Push notifications

### v1.5 (6 months post-launch)
- Elasticsearch for advanced search
- Recommendation engine
- Loyalty program
- Referral system

### v2.0 (1 year post-launch)
- Multi-store support (2-5 dark stores)
- Multi-city expansion
- Rider mobile app
- Admin dashboard for inventory management

### Microservices Migration (When needed)
**Triggers:**
- 5+ dark stores
- Team size > 10 developers
- Independent scaling requirements

**Extraction Priority:**
1. Order Service (high transaction volume)
2. Delivery Service (independent scaling for riders)
3. Notification Service (async, high volume)
4. Search Service (resource-intensive)

---

## Appendix

### Glossary
- **Dark Store:** Warehouse optimized for rapid order fulfillment (not open to customers)
- **SKU:** Stock Keeping Unit (unique product identifier)
- **FEFO:** First Expired First Out (inventory rotation strategy)
- **JWT:** JSON Web Token (authentication mechanism)
- **TTL:** Time To Live (expiry duration)
- **R2DBC:** Reactive Relational Database Connectivity

### References
- Spring Boot Documentation: https://spring.io/projects/spring-boot
- R2DBC Documentation: https://r2dbc.io/
- MySQL Geospatial Functions: https://dev.mysql.com/doc/refman/8.0/en/spatial-analysis-functions.html

---

**Document Version History:**
- v1.0 (2025-01-04): Initial design document
- Future versions will track major architectural changes

**Maintained By:** Engineering Team
**Review Cycle:** Quarterly or on major architectural changes
