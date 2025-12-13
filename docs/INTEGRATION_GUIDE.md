# Integration Guide: Inventory & Catalog Services

**Status:** Deployed on AWS EC2 (Free Tier)
**Environment:** Development / Testing

## 1. Access & Base URLs
Replace `<EC2_PUBLIC_IP>` with your instance IP (e.g., `13.250.xxx.xxx`).

*   **Inventory Service:** `http://<EC2_PUBLIC_IP>:8081`
*   **Catalog Service:** `http://<EC2_PUBLIC_IP>:8082`

## 2. Authentication (Important!)
**Inventory Service** is protected by default Spring Security.
*   **Username:** `user`
*   **Password:** Check the server logs to find the generated password:
    ```bash
    ssh -i key.pem ec2-user@<IP>
    sudo docker compose logs inventory-service | grep "Using generated security password"
    ```
*   *Note: For the next deployment, we can disable this or configure a fixed password.*

**Catalog Service** is currently **open** (no auth required).

## 3. Key API Endpoints

### 📦 Inventory Service (`:8081`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/inventory/sku/{sku}` | Get stock level for a SKU |
| `POST` | `/api/v1/inventory/reserve` | Reserve stock for an order |
| `POST` | `/api/v1/inventory/stock/add` | Add stock (Inbound) |
| `POST` | `/api/v1/inventory/availability` | Bulk check availability for multiple SKUs |
| `POST` | `/api/v1/inventory/nearest-store` | Find nearest store with stock (Geo-search) |
| `GET` | `/api/v1/inventory/low-stock?storeId=1` | List items running low |

### 📚 Catalog Service (`:8082`)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/v1/catalog/products` | List all products |
| `GET` | `/api/v1/catalog/products/sku/{sku}` | Get product details by SKU |
| `GET` | `/api/v1/catalog/products/search?q=apple` | Search products |
| `GET` | `/api/v1/catalog/categories/root` | Get top-level categories |
| `GET` | `/api/v1/catalog/categories/{id}/children` | Get subcategories |

## 4. Database Access
*   **Host:** `inventorydatabase.cpswwoukkbi2.ap-southeast-1.rds.amazonaws.com`
*   **Port:** `3306`
*   **Database:** `inventory`
*   **Username:** `admin`
*   **Password:** `mysqlInv2025`
*   *Tables include: `products`, `categories`, `inventory_items`, `stores`*
