# Order Service API Integration Guide

This guide explains how to integrate with the Order Service APIs for creating orders and processing Airtel Money payments. It includes curl examples and step-by-step instructions for UI/frontend integration.

## Quick Reference

| API | Method | Endpoint |
|-----|--------|----------|
| Create Order | POST | `/api/v1/orders` |
| Initiate Payment | POST | `/api/v1/orders/{orderUuid}/pay` |
| Poll Payment Status | GET | `/api/v1/orders/{orderUuid}/payment-status` |
| Get Order | GET | `/api/v1/orders/{orderUuid}` |
| Preview Order | POST | `/api/v1/orders/preview` |
| Cancel Order | POST | `/api/v1/orders/{orderUuid}/cancel` |

**Required headers:** `X-Customer-Id` for `/pay` and `/payment-status`

---

## Base URL

```
http://localhost:8082   (local)
https://your-domain.com (production)
```

---

## API Flow Overview

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│ 1. Create   │────▶│ 2. Initiate  │────▶│ 3. Poll     │────▶│ 4. Order     │
│    Order    │     │    Payment   │     │    Status   │     │    Confirmed │
└─────────────┘     └──────────────┘     └─────────────┘     └──────────────┘
       │                     │                    │
       │                     │                    │  (every 3 sec)
       │                     │                    └── CONFIRMED / FAILED
       │                     │
       │                     └── STK push sent to customer's phone
       │
       └── COD orders skip steps 2–3 (confirmed immediately)
```

---

## Step 1: Create Order

**Endpoint:** `POST /api/v1/orders`

**Headers:**
- `Content-Type: application/json`
- `Idempotency-Key` (optional): Unique key for safe retries

**Request Body:**

```json
{
  "storeId": 1,
  "customerId": "CUST_123",
  "items": [
    { "sku": "MILK-500ML-1772823529", "quantity": 2 },
    { "sku": "BREAD-WHITE-1772823529", "quantity": 1 }
  ],
  "paymentMethod": "AIRTEL_MONEY",
  "delivery": {
    "latitude": -15.4167,
    "longitude": 28.2833,
    "address": "123 Cairo Road, Lusaka",
    "phone": "0977123456",
    "notes": "Gate code: 5678"
  }
}
```

**Payment methods:** `COD` | `AIRTEL_MONEY` | `MTN_MONEY`

**Phone validation (delivery.phone):**
- Indian: `9876543210`, `+919876543210`, `09876543210`
- Zambian: `0977123456`, `0771234567`, `+260977123456`

**cURL:**

```bash
curl -X POST http://localhost:8082/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-$(date +%s)" \
  -d '{
    "storeId": 1,
    "customerId": "CUST_123",
    "items": [
      { "sku": "MILK-500ML-1772823529", "quantity": 2 },
      { "sku": "BREAD-WHITE-1772823529", "quantity": 1 }
    ],
    "paymentMethod": "AIRTEL_MONEY",
    "delivery": {
      "latitude": -15.4167,
      "longitude": 28.2833,
      "address": "123 Cairo Road, Lusaka",
      "phone": "0977123456",
      "notes": "Gate code: 5678"
    }
  }'
```

**Response:** `201 Created`

```json
{
  "orderId": "528776e3-d90c-42c9-993d-259d7afacf5a",
  "status": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "paymentMethod": "AIRTEL_MONEY",
  "message": "Please proceed to payment",
  "itemsTotal": 25.00,
  "deliveryFee": 15.00,
  "grandTotal": 40.00,
  "currency": "ZMW",
  "delivery": { "address": "...", "phone": "097****456", ... },
  "items": [...],
  "createdAt": "2026-03-10T00:37:04.175058"
}
```

**COD orders:** Response will have `status: "CONFIRMED"` and `paymentStatus: "COD_PENDING"` — no payment step needed.

---

## Step 2: Initiate Payment (Digital Payment Orders Only)

**Endpoint:** `POST /api/v1/orders/{orderUuid}/pay`

**Headers:**
- `Content-Type: application/json`
- `X-Customer-Id`: **Required** — must match the `customerId` from the order

**Request Body:**

```json
{
  "paymentPhone": "0971234567"
}
```

**Phone validation (paymentPhone):**
- Indian: `9876543210`, `+919876543210`
- Zambian: `0971234567`, `0771234567`, `+260977123456`

**cURL:**

```bash
curl -X POST http://localhost:8082/api/v1/orders/528776e3-d90c-42c9-993d-259d7afacf5a/pay \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: CUST_123" \
  -d '{"paymentPhone": "0971234567"}'
```

**Response:** `200 OK`

```json
{
  "orderUuid": "528776e3-d90c-42c9-993d-259d7afacf5a",
  "orderStatus": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "paymentPhone": "097****567",
  "pushStatus": "PUSH_SENT",
  "message": "Airtel Money prompt sent to 097****567. Please enter your PIN."
}
```

**Error responses:**
- `400` — Missing `X-Customer-Id`, wrong customer, duplicate payment, or invalid phone
- `404` — Order not found
- `429` — Rate limit exceeded (3 pay requests per 60 seconds)

---

## Step 3: Poll Payment Status

**Endpoint:** `GET /api/v1/orders/{orderUuid}/payment-status`

**Headers:**
- `X-Customer-Id`: **Required** — must match the order's customerId

**cURL:**

```bash
curl -X GET http://localhost:8082/api/v1/orders/528776e3-d90c-42c9-993d-259d7afacf5a/payment-status \
  -H "X-Customer-Id: CUST_123"
```

**Response:** `200 OK`

```json
{
  "orderUuid": "528776e3-d90c-42c9-993d-259d7afacf5a",
  "orderStatus": "PENDING_PAYMENT",
  "paymentStatus": "PENDING",
  "paymentPhone": "097****567",
  "pushStatus": "PUSH_SENT",
  "message": "Waiting for Airtel PIN confirmation"
}
```

**pushStatus values:**

| pushStatus    | Meaning                                      |
|---------------|----------------------------------------------|
| `AWAITING_PUSH` | Before `/pay` is called                      |
| `PUSH_SENT`   | STK push sent, waiting for customer PIN      |
| `CONFIRMED`   | Payment successful, order confirmed          |
| `FAILED`      | Payment failed or order cancelled            |

**Polling:** Call every 3 seconds until `pushStatus` is `CONFIRMED` or `FAILED`.

---

## Step 4: Get Order Details

**Endpoint:** `GET /api/v1/orders/{orderUuid}`

**cURL:**

```bash
curl -X GET http://localhost:8082/api/v1/orders/528776e3-d90c-42c9-993d-259d7afacf5a
```

**Response:** `200 OK` — Full order with items, delivery, status.

---

## Additional APIs

### Preview Order (Before Checkout)

**Endpoint:** `POST /api/v1/orders/preview`

```bash
curl -X POST http://localhost:8082/api/v1/orders/preview \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": 1,
    "items": [
      { "sku": "MILK-500ML-1772823529", "qty": 2 },
      { "sku": "BREAD-WHITE-1772823529", "qty": 1 }
    ]
  }'
```

### Cancel Order

**Endpoint:** `POST /api/v1/orders/{orderUuid}/cancel`

**Headers:** `Customer-Id` (required), `Content-Type: application/json`

```bash
curl -X POST http://localhost:8082/api/v1/orders/528776e3-d90c-42c9-993d-259d7afacf5a/cancel \
  -H "Content-Type: application/json" \
  -H "Customer-Id: CUST_123" \
  -d '{"reason": "Changed my mind"}'
```

---

## UI Integration Steps

### 1. Checkout Page

1. Call `POST /api/v1/orders/preview` to show totals and stock warnings.
2. On "Place Order", call `POST /api/v1/orders` with `Idempotency-Key` for retry safety.
3. Store `orderId` from response.

### 2. Payment Flow (AIRTEL_MONEY / MTN_MONEY)

1. If `status === "PENDING_PAYMENT"`, show payment screen.
2. User enters mobile money phone number.
3. Call `POST /api/v1/orders/{orderId}/pay` with `X-Customer-Id` and `paymentPhone`.
4. Show "Enter PIN on your phone" message.
5. Start polling `GET /api/v1/orders/{orderId}/payment-status` every 3 seconds.
6. When `pushStatus === "CONFIRMED"` → show success, redirect to order confirmation.
7. When `pushStatus === "FAILED"` → show "Payment failed", allow retry or switch to COD.

### 3. COD Flow

1. If `paymentMethod === "COD"`, order is already `CONFIRMED` after create.
2. No payment step — show order confirmation directly.

### 4. Error Handling

| HTTP | Scenario                    | UI Action                          |
|------|-----------------------------|------------------------------------|
| 400  | Validation error            | Show field errors from `errors`    |
| 400  | Wrong X-Customer-Id         | Redirect to login / show error     |
| 400  | Duplicate payment           | "Payment already in progress"      |
| 404  | Order not found             | "Order not found"                  |
| 409  | Insufficient stock          | "Some items out of stock"          |
| 429  | Rate limit                  | "Too many requests, try again"     |

---

## Phone Number Formats

### Valid Formats

**Indian:**
- `9876543210` (10 digits, starts with 6–9)
- `09876543210`
- `+919876543210`

**Zambian:**
- `0977123456` (Airtel)
- `0771234567` (MTN)
- `0967123456`
- `+260977123456`

### Invalid Examples

- `1234567890` — Indian numbers must start with 6–9
- `0211234567` — Landline not supported
- `1234` — Too short

---

## Mock Profile (Development)

With `SPRING_PROFILES_ACTIVE=mock-airtel`:

- No real Airtel API calls.
- `/pay` returns immediately with fake transaction ID.
- Failsafe scheduler auto-confirms after ~10 seconds (dev) or ~60 seconds (prod).
- UI can test full flow without real mobile money.

---

## Complete cURL Test Script

```bash
# 1. Create order
ORDER_RESP=$(curl -s -X POST http://localhost:8082/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "storeId": 1,
    "customerId": "CUST_123",
    "items": [{"sku": "MILK-500ML-1772823529", "quantity": 1}],
    "paymentMethod": "AIRTEL_MONEY",
    "delivery": {
      "latitude": -15.4167,
      "longitude": 28.2833,
      "address": "123 Test St",
      "phone": "0977123456",
      "notes": ""
    }
  }')

ORDER_ID=$(echo $ORDER_RESP | jq -r '.orderId')
echo "Order created: $ORDER_ID"

# 2. Initiate payment
curl -s -X POST "http://localhost:8082/api/v1/orders/$ORDER_ID/pay" \
  -H "Content-Type: application/json" \
  -H "X-Customer-Id: CUST_123" \
  -d '{"paymentPhone": "0971234567"}' | jq .

# 3. Poll status (run a few times)
curl -s -X GET "http://localhost:8082/api/v1/orders/$ORDER_ID/payment-status" \
  -H "X-Customer-Id: CUST_123" | jq .
```
