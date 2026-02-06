# End-to-End Testing Script for Product & Search Services
# Tests all APIs with proper data flow

$productUrl = "http://localhost:8081"
$searchUrl = "http://localhost:8083"
$adminUser = "admin"
$adminPass = "admin123"

# Admin auth header
$base64Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${adminUser}:${adminPass}"))
$adminHeaders = @{
    Authorization = "Basic $base64Auth"
    "Content-Type" = "application/json"
}

Write-Host "`n============================================" -ForegroundColor Cyan
Write-Host "END-TO-END TESTING - Product & Search Services" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

# ============================================
# PART 1: PRODUCT SERVICE SETUP
# ============================================

Write-Host "`n[PART 1] PRODUCT SERVICE - Setup Test Data" -ForegroundColor Yellow
Write-Host "============================================`n" -ForegroundColor Yellow

# Test 1: Add Products & Inventory via Bulk Sync
Write-Host "Test 1.1: Bulk Sync - Add 10 Products with Inventory" -ForegroundColor Green
$syncBody = @{
    storeId = 1
    items = @(
        @{
            sku = "AMUL-MILK-500ML"
            name = "Amul Taaza Milk 500ml"
            brand = "Amul"
            category = "Dairy"
            price = 25.0
            mrp = 28.0
            quantity = 100
            unit = "ml"
            unitValue = 500
        },
        @{
            sku = "AMUL-BUTTER-100G"
            name = "Amul Butter 100g"
            brand = "Amul"
            category = "Dairy"
            price = 50.0
            mrp = 55.0
            quantity = 50
            unit = "g"
            unitValue = 100
        },
        @{
            sku = "BRITANNIA-BREAD"
            name = "Britannia Bread 400g"
            brand = "Britannia"
            category = "Bakery"
            price = 35.0
            mrp = 40.0
            quantity = 75
            unit = "g"
            unitValue = 400
        },
        @{
            sku = "LAYS-CHIPS-50G"
            name = "Lays Classic Chips 50g"
            brand = "Lays"
            category = "Snacks"
            price = 20.0
            mrp = 20.0
            quantity = 200
            unit = "g"
            unitValue = 50
        },
        @{
            sku = "COCA-COLA-750ML"
            name = "Coca Cola 750ml"
            brand = "Coca Cola"
            category = "Beverages"
            price = 40.0
            mrp = 45.0
            quantity = 150
            unit = "ml"
            unitValue = 750
        },
        @{
            sku = "SURF-EXCEL-1KG"
            name = "Surf Excel Detergent 1kg"
            brand = "Surf Excel"
            category = "Household"
            price = 180.0
            mrp = 200.0
            quantity = 30
            unit = "kg"
            unitValue = 1
        },
        @{
            sku = "MAGGI-NOODLES-70G"
            name = "Maggi Masala Noodles 70g"
            brand = "Maggi"
            category = "Instant Food"
            price = 12.0
            mrp = 14.0
            quantity = 300
            unit = "g"
            unitValue = 70
        },
        @{
            sku = "COLGATE-TOOTHPASTE"
            name = "Colgate Total Toothpaste 150g"
            brand = "Colgate"
            category = "Personal Care"
            price = 95.0
            mrp = 110.0
            quantity = 80
            unit = "g"
            unitValue = 150
        },
        @{
            sku = "FORTUNE-OIL-1L"
            name = "Fortune Sunflower Oil 1L"
            brand = "Fortune"
            category = "Cooking Essentials"
            price = 150.0
            mrp = 165.0
            quantity = 60
            unit = "L"
            unitValue = 1
        },
        @{
            sku = "PARLE-G-BISCUIT"
            name = "Parle-G Biscuits 200g"
            brand = "Parle"
            category = "Biscuits"
            price = 15.0
            mrp = 18.0
            quantity = 250
            unit = "g"
            unitValue = 200
        }
    )
} | ConvertTo-Json -Depth 10

try {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/sync" -Method POST -Body $syncBody -ContentType "application/json"
    Write-Host "  ✓ Bulk Sync Success" -ForegroundColor Green
    Write-Host "    Total Items: $($response.totalItems)" -ForegroundColor Gray
    Write-Host "    Successful: $($response.successCount)" -ForegroundColor Gray
    Write-Host "    Failed: $($response.failureCount)" -ForegroundColor Gray
    Start-Sleep -Seconds 3  # Wait for indexing
} catch {
    Write-Host "  ✗ Bulk Sync Failed: $_" -ForegroundColor Red
    exit 1
}

# Test 2: Get Product by SKU
Write-Host "`nTest 1.2: Get Product by SKU" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/sku/AMUL-MILK-500ML" -Method GET
    Write-Host "  ✓ Get Product Success" -ForegroundColor Green
    Write-Host "    Name: $($response.name)" -ForegroundColor Gray
    Write-Host "    Price: ₹$($response.price)" -ForegroundColor Gray
    Write-Host "    Brand: $($response.brand)" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Get Product Failed: $_" -ForegroundColor Red
}

# Test 3: Check Inventory Availability
Write-Host "`nTest 1.3: Check Inventory Availability" -ForegroundColor Green
$availabilityBody = @{
    storeId = 1
    productIds = @(1, 2, 3, 4, 5)
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/availability" -Method POST -Body $availabilityBody -ContentType "application/json"
    Write-Host "  ✓ Availability Check Success" -ForegroundColor Green
    Write-Host "    Store ID: $($response.storeId)" -ForegroundColor Gray
    Write-Host "    Products Checked: $($response.availability.Count)" -ForegroundColor Gray
    $availableCount = ($response.availability.GetEnumerator() | Where-Object { $_.Value -eq $true }).Count
    Write-Host "    Available: $availableCount" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Availability Check Failed: $_" -ForegroundColor Red
}

# Test 4: Reserve Stock
Write-Host "`nTest 1.4: Reserve Stock" -ForegroundColor Green
$reserveBody = @{
    storeId = 1
    sku = "AMUL-MILK-500ML"
    quantity = 2
    orderId = "ORD-TEST-001"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/reserve" -Method POST -Body $reserveBody -ContentType "application/json"
    Write-Host "  ✓ Stock Reserved" -ForegroundColor Green
    Write-Host "    Reservation ID: $($response.reservationId)" -ForegroundColor Gray
    Write-Host "    Quantity: $($response.quantity)" -ForegroundColor Gray
    Write-Host "    Expires At: $($response.expiresAt)" -ForegroundColor Gray
    $reservationId = $response.reservationId
} catch {
    Write-Host "  ✗ Reserve Stock Failed: $_" -ForegroundColor Red
}

# Test 5: Confirm Reservation
Write-Host "`nTest 1.5: Confirm Reservation" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/reservation/$reservationId/confirm" -Method POST
    Write-Host "  ✓ Reservation Confirmed" -ForegroundColor Green
    Write-Host "    Status: $($response.status)" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Confirm Reservation Failed: $_" -ForegroundColor Red
}

# ============================================
# PART 2: SEARCH SERVICE TESTING
# ============================================

Write-Host "`n`n[PART 2] SEARCH SERVICE - Search Functionality" -ForegroundColor Yellow
Write-Host "============================================`n" -ForegroundColor Yellow

# Wait for search indexing
Write-Host "Waiting 5 seconds for search indexing..." -ForegroundColor Gray
Start-Sleep -Seconds 5

# Test 6: Simple Product Search
Write-Host "`nTest 2.1: Simple Search - 'milk'" -ForegroundColor Green
$searchBody = @{
    query = "milk"
    storeId = 1
    page = 1
    pageSize = 10
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "  ✓ Search Success" -ForegroundColor Green
    Write-Host "    Query: $($response.query)" -ForegroundColor Gray
    Write-Host "    Total Hits: $($response.meta.totalHits)" -ForegroundColor Gray
    Write-Host "    Returned: $($response.meta.returned)" -ForegroundColor Gray
    Write-Host "    Processing Time: $($response.meta.processingTimeMs)ms" -ForegroundColor Gray
    if ($response.results.Count -gt 0) {
        Write-Host "    Results:" -ForegroundColor Gray
        foreach ($result in $response.results) {
            Write-Host "      - $($result.name) (₹$($result.price))" -ForegroundColor DarkGray
        }
    }
} catch {
    Write-Host "  ✗ Search Failed: $_" -ForegroundColor Red
}

# Test 7: Brand Search
Write-Host "`nTest 2.2: Brand Search - 'amul'" -ForegroundColor Green
$searchBody = @{
    query = "amul"
    storeId = 1
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "  ✓ Brand Search Success" -ForegroundColor Green
    Write-Host "    Total Hits: $($response.meta.totalHits)" -ForegroundColor Gray
    Write-Host "    Results:" -ForegroundColor Gray
    foreach ($result in $response.results) {
        Write-Host "      - $($result.name) - $($result.brand)" -ForegroundColor DarkGray
    }
} catch {
    Write-Host "  ✗ Brand Search Failed: $_" -ForegroundColor Red
}

# Test 8: Category Search
Write-Host "`nTest 2.3: Category Search - 'snacks'" -ForegroundColor Green
$searchBody = @{
    query = "snacks"
    storeId = 1
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "  ✓ Category Search Success" -ForegroundColor Green
    Write-Host "    Total Hits: $($response.meta.totalHits)" -ForegroundColor Gray
    if ($response.results.Count -gt 0) {
        foreach ($result in $response.results) {
            Write-Host "      - $($result.name)" -ForegroundColor DarkGray
        }
    }
} catch {
    Write-Host "  ✗ Category Search Failed: $_" -ForegroundColor Red
}

# Test 9: Typo Tolerance
Write-Host "`nTest 2.4: Typo Tolerance - 'coka cola' (typo)" -ForegroundColor Green
$searchBody = @{
    query = "coka cola"
    storeId = 1
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "  ✓ Typo Search Success" -ForegroundColor Green
    Write-Host "    Total Hits: $($response.meta.totalHits)" -ForegroundColor Gray
    if ($response.results.Count -gt 0) {
        Write-Host "    Found despite typo:" -ForegroundColor Gray
        foreach ($result in $response.results) {
            Write-Host "      - $($result.name)" -ForegroundColor DarkGray
        }
    }
} catch {
    Write-Host "  ✗ Typo Search Failed: $_" -ForegroundColor Red
}

# Test 10: No Results Fallback
Write-Host "`nTest 2.5: No Results Fallback - 'xyz123notfound'" -ForegroundColor Green
$searchBody = @{
    query = "xyz123notfound"
    storeId = 1
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "  ✓ Fallback Working" -ForegroundColor Green
    Write-Host "    Query: $($response.query)" -ForegroundColor Gray
    Write-Host "    Returned (fallback): $($response.meta.returned)" -ForegroundColor Gray
    if ($response.results.Count -gt 0) {
        Write-Host "    Fallback Results:" -ForegroundColor Gray
        foreach ($result in $response.results[0..2]) {
            Write-Host "      - $($result.name)" -ForegroundColor DarkGray
        }
    }
} catch {
    Write-Host "  ✗ Fallback Test Failed: $_" -ForegroundColor Red
}

# Test 11: Pagination
Write-Host "`nTest 2.6: Pagination - Page 1 & 2" -ForegroundColor Green
$searchBody = @{
    query = ""
    storeId = 1
    page = 1
    pageSize = 3
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "  ✓ Page 1 Success" -ForegroundColor Green
    Write-Host "    Page: $($response.meta.page)" -ForegroundColor Gray
    Write-Host "    Page Size: $($response.meta.pageSize)" -ForegroundColor Gray
    Write-Host "    Total Pages: $($response.meta.totalPages)" -ForegroundColor Gray
    Write-Host "    Returned: $($response.meta.returned)" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Pagination Failed: $_" -ForegroundColor Red
}

# ============================================
# PART 3: ADMIN APIS
# ============================================

Write-Host "`n`n[PART 3] ADMIN APIs - Configuration Management" -ForegroundColor Yellow
Write-Host "============================================`n" -ForegroundColor Yellow

# Test 12: Create Synonym
Write-Host "Test 3.1: Create Synonym" -ForegroundColor Green
$synonymBody = @{
    term = "aata"
    synonyms = @("atta", "wheat flour", "gehun ka atta")
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/synonyms" -Method POST -Body $synonymBody -Headers $adminHeaders
    Write-Host "  ✓ Synonym Created" -ForegroundColor Green
    Write-Host "    Term: $($response.term)" -ForegroundColor Gray
    Write-Host "    Updated By: $($response.updatedBy)" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Create Synonym Failed: $_" -ForegroundColor Red
}

# Test 13: Get All Synonyms
Write-Host "`nTest 3.2: Get All Synonyms" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/synonyms" -Method GET -Headers $adminHeaders
    Write-Host "  ✓ Get Synonyms Success" -ForegroundColor Green
    Write-Host "    Total Synonyms: $($response.Count)" -ForegroundColor Gray
    if ($response.Count -gt 0) {
        foreach ($syn in $response) {
            Write-Host "      - $($syn.term)" -ForegroundColor DarkGray
        }
    }
} catch {
    Write-Host "  ✗ Get Synonyms Failed: $_" -ForegroundColor Red
}

# Test 14: Get All Settings
Write-Host "`nTest 3.3: Get All Settings" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/settings" -Method GET -Headers $adminHeaders
    Write-Host "  ✓ Get Settings Success" -ForegroundColor Green
    Write-Host "    Total Settings: $($response.Count)" -ForegroundColor Gray
    if ($response.Count -gt 0) {
        foreach ($setting in $response) {
            Write-Host "      - $($setting.key)" -ForegroundColor DarkGray
        }
    }
} catch {
    Write-Host "  ✗ Get Settings Failed: $_" -ForegroundColor Red
}

# Test 15: Trigger Config Sync
Write-Host "`nTest 3.4: Trigger Configuration Sync" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/sync" -Method POST -Headers $adminHeaders
    Write-Host "  ✓ Config Sync Triggered" -ForegroundColor Green
    Write-Host "    Status: $($response.status)" -ForegroundColor Gray
    Write-Host "    Task UID: $($response.taskUid)" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Config Sync Failed: $_" -ForegroundColor Red
}

# Test 16: Get Index Stats
Write-Host "`nTest 3.5: Get Index Statistics" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/index/stats" -Method GET -Headers $adminHeaders
    Write-Host "  ✓ Index Stats Retrieved" -ForegroundColor Green
    Write-Host "    Documents: $($response.numberOfDocuments)" -ForegroundColor Gray
    Write-Host "    Indexing: $($response.isIndexing)" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Get Index Stats Failed: $_" -ForegroundColor Red
}

# ============================================
# PART 4: HEALTH & METRICS
# ============================================

Write-Host "`n`n[PART 4] HEALTH & METRICS" -ForegroundColor Yellow
Write-Host "============================================`n" -ForegroundColor Yellow

# Test 17: Product Service Health
Write-Host "Test 4.1: Product Service Health" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$productUrl/actuator/health" -Method GET
    Write-Host "  ✓ Product Service: $($response.status)" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Product Service Health Failed: $_" -ForegroundColor Red
}

# Test 18: Search Service Health
Write-Host "`nTest 4.2: Search Service Health" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$searchUrl/actuator/health" -Method GET
    Write-Host "  ✓ Search Service: $($response.status)" -ForegroundColor Green
    if ($response.components.syncHealthIndicator) {
        Write-Host "    Sync State: $($response.components.syncHealthIndicator.details.state)" -ForegroundColor Gray
        Write-Host "    Items Synced: $($response.components.syncHealthIndicator.details.itemsSynced)" -ForegroundColor Gray
    }
} catch {
    Write-Host "  ✗ Search Service Health Failed: $_" -ForegroundColor Red
}

# Test 19: Search Metrics
Write-Host "`nTest 4.3: Search Metrics" -ForegroundColor Green
try {
    $response = Invoke-RestMethod -Uri "$searchUrl/actuator/metrics/search.requests.total" -Method GET
    Write-Host "  ✓ Search Metrics Available" -ForegroundColor Green
    Write-Host "    Total Requests: $($response.measurements[0].value)" -ForegroundColor Gray
} catch {
    Write-Host "  ✗ Search Metrics Failed: $_" -ForegroundColor Red
}

# ============================================
# SUMMARY
# ============================================

Write-Host "`n`n============================================" -ForegroundColor Cyan
Write-Host "TESTING COMPLETE!" -ForegroundColor Cyan
Write-Host "============================================`n" -ForegroundColor Cyan

Write-Host "Summary:" -ForegroundColor Yellow
Write-Host "  ✓ Product Service: 5 APIs tested" -ForegroundColor Green
Write-Host "  ✓ Search Service: 6 search tests" -ForegroundColor Green
Write-Host "  ✓ Admin APIs: 5 tests" -ForegroundColor Green
Write-Host "  ✓ Health & Metrics: 3 tests" -ForegroundColor Green
Write-Host "`n  Total: 19 end-to-end tests executed`n" -ForegroundColor White

Write-Host "Services Status:" -ForegroundColor Yellow
Write-Host "  - Product Service: http://localhost:8081" -ForegroundColor Gray
Write-Host "  - Search Service: http://localhost:8083" -ForegroundColor Gray
Write-Host "  - Meilisearch: http://localhost:7700" -ForegroundColor Gray
Write-Host "  - Admin Credentials: admin/admin123`n" -ForegroundColor Gray
