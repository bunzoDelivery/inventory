# Updated Product Service APIs (Bulk Support)

The following APIs have been updated/added to support the Order Service requirements.

## 1. Inventory Service

### Reserve Stock (Bulk)
**Endpoint:** `POST /api/v1/inventory/reserve`
**Description:** Atomically reserves stock for multiple items. If one fails, all fail (Transactional).

**Request Body:**
```json
{
  "orderId": "ORDER_12345",
  "customerId": 101,
  "items": [
    { "sku": "MILK-001", "quantity": 2 },
    { "sku": "BREAD-002", "quantity": 1 }
  ]
}
```

**Response:** `200 OK`
```json
[
  {
    "reservationId": "RES_...",
    "sku": "MILK-001",
    "status": "ACTIVE",
    ...
  },
  {
    "reservationId": "RES_...",
    "sku": "BREAD-002",
    "status": "ACTIVE",
    ...
  }
]
```

### Confirm Reservation (Bulk by Order)
**Endpoint:** `POST /api/v1/inventory/reservations/order/{orderId}/confirm`
**Description:** Confirms all active reservations for a given Order ID.

**Response:** `200 OK`
```json
{
  "orderId": "ORDER_12345",
  "status": "CONFIRMED",
  "message": "Order reservations confirmed successfully"
}
```

---

## 2. Catalog Service

### Get Products by SKU List
**Endpoint:** `POST /api/v1/catalog/products/skus`
**Description:** Retrieves full product details for a list of SKUs.

**Request Body:**
```json
[ "MILK-001", "BREAD-002" ]
```

**Response:** `200 OK`
```json
[
  {
    "sku": "MILK-001",
    "name": "Fresh Milk",
    "basePrice": 15.00,
    ...
  },
  {
    "sku": "BREAD-002",
    "name": "Whole Wheat Bread",
    "basePrice": 25.00,
    ...
  }
]
```
