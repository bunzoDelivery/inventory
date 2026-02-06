# Comprehensive E2E API Testing Script - CORRECTED VERSION
# Tests ALL APIs with proper validation

$productUrl = "http://localhost:8081"
$searchUrl = "http://localhost:8083"
$adminUser = "admin"
$adminPass = "admin123"
$base64Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${adminUser}:${adminPass}"))
$adminHeaders = @{
    Authorization = "Basic $base64Auth"
    "Content-Type" = "application/json"
}

$totalTests = 0
$passedTests = 0
$failedTests = 0

function Test-API {
    param(
        [string]$Name,
        [scriptblock]$TestBlock
    )
    $script:totalTests++
    Write-Host "`n[$script:totalTests] $Name" -ForegroundColor Cyan
    try {
        & $TestBlock
        Write-Host "  PASS" -ForegroundColor Green
        $script:passedTests++
        return $true
    } catch {
        Write-Host "  FAIL: $_" -ForegroundColor Red
        $script:failedTests++
        return $false
    }
}

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "COMPREHENSIVE API TESTING - FIXED" -ForegroundColor Magenta
Write-Host "========================================`n" -ForegroundColor Magenta

# ========================================
# PRODUCT SERVICE - CATALOG APIs
# ========================================

Write-Host "`n=== PRODUCT SERVICE - CATALOG APIs ===" -ForegroundColor Yellow

Test-API "Get All Categories" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/categories" -Method GET
    if ($response.Count -lt 1) { throw "No categories found" }
    if ($response.Count -ne 10) { throw "Expected 10 categories, got $($response.Count)" }
    Write-Host "    Found $($response.Count) categories" -ForegroundColor Gray
}

Test-API "Get Category by ID" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/categories/1" -Method GET
    if (-not $response.id) { throw "Category not found" }
    if ($response.id -ne 1) { throw "Wrong category ID" }
    Write-Host "    Category: $($response.name) (ID: $($response.id))" -ForegroundColor Gray
}

Test-API "Get Product by SKU" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/sku/AMUL-MILK-500ML" -Method GET
    if (-not $response.sku) { throw "Product not found" }
    if ($response.sku -ne "AMUL-MILK-500ML") { throw "Wrong SKU" }
    if ($response.basePrice -le 0) { throw "Invalid price" }
    Write-Host "    Product: $($response.name) - Rs.$($response.basePrice)" -ForegroundColor Gray
}

Test-API "Get All Products" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/all" -Method GET
    if ($response.Count -lt 10) { throw "Expected at least 10 products, got $($response.Count)" }
    Write-Host "    Found $($response.Count) products" -ForegroundColor Gray
}

Test-API "Get Products by Category" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/category/1" -Method GET
    Write-Host "    Found $($response.Count) products in category 1" -ForegroundColor Gray
}

Test-API "Get Products by Brand" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/brand/Amul" -Method GET
    if ($response.Count -lt 1) { throw "No Amul products found" }
    Write-Host "    Found $($response.Count) Amul products" -ForegroundColor Gray
}

# ========================================
# PRODUCT SERVICE - INVENTORY APIs
# ========================================

Write-Host "`n=== PRODUCT SERVICE - INVENTORY APIs ===" -ForegroundColor Yellow

Test-API "Check Inventory Availability (Bulk)" {
    $body = @{
        storeId = 1
        skus = @("AMUL-MILK-500ML", "AMUL-BUTTER-100G", "PARLE-G-BISCUIT")
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/availability" -Method POST -Body $body -ContentType "application/json"
    if (-not $response.availability) { throw "No availability data" }
    if ($response.availability.Count -ne 3) { throw "Expected 3 products, got $($response.availability.Count)" }
    Write-Host "    Checked $($response.availability.Count) products" -ForegroundColor Gray
    $availableCount = ($response.availability.GetEnumerator() | Where-Object { $_.Value -eq $true }).Count
    Write-Host "    Available: $availableCount" -ForegroundColor Gray
}

Test-API "Check Single Product Availability" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/availability/single?sku=AMUL-MILK-500ML&storeId=1" -Method GET
    if ($response.available -ne $true) { throw "Product should be available" }
    Write-Host "    SKU: $($response.sku), Available: $($response.available)" -ForegroundColor Gray
}

Test-API "Get Inventory by SKU" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/sku/AMUL-MILK-500ML?storeId=1" -Method GET
    if (-not $response.sku) { throw "Inventory not found" }
    if ($response.currentStock -lt 0) { throw "Invalid stock level" }
    Write-Host "    Stock: $($response.currentStock) units (Reserved: $($response.reservedStock))" -ForegroundColor Gray
}

Test-API "Reserve Stock" {
    $body = @{
        sku = "PARLE-G-BISCUIT"
        quantity = 2
        customerId = "CUST-001"
        orderId = "TEST-ORDER-$(Get-Random -Maximum 9999)"
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/reserve" -Method POST -Body $body -ContentType "application/json"
    if (-not $response.reservationId) { throw "Reservation failed" }
    if ($response.quantity -ne 2) { throw "Wrong quantity reserved" }
    Write-Host "    Reserved: $($response.quantity) units (ID: $($response.reservationId))" -ForegroundColor Gray
    $script:reservationId = $response.reservationId
}

Test-API "Confirm Reservation" {
    if (-not $script:reservationId) { throw "No reservation to confirm" }
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/reservations/$($script:reservationId)/confirm" -Method POST
    if ($response.status -ne "CONFIRMED") { throw "Confirmation failed, status: $($response.status)" }
    Write-Host "    Status: $($response.status)" -ForegroundColor Gray
}

Test-API "Add Stock" {
    $body = @{
        sku = "PARLE-G-BISCUIT"
        storeId = 1
        quantity = 5
        reason = "TEST_RESTOCK"
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/stock/add" -Method POST -Body $body -ContentType "application/json"
    if ($response.currentStock -lt 5) { throw "Stock not added properly" }
    Write-Host "    New stock: $($response.currentStock) units" -ForegroundColor Gray
}

Test-API "Get Low Stock Items" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/low-stock?storeId=1" -Method GET
    Write-Host "    Low stock items: $($response.Count)" -ForegroundColor Gray
}

# ========================================
# PRODUCT SERVICE - HEALTH & METRICS
# ========================================

Write-Host "`n=== PRODUCT SERVICE - HEALTH & METRICS ===" -ForegroundColor Yellow

Test-API "Product Service Health" {
    $response = Invoke-RestMethod -Uri "$productUrl/actuator/health" -Method GET
    if ($response.status -ne "UP") { throw "Service not healthy: $($response.status)" }
    Write-Host "    Status: $($response.status)" -ForegroundColor Gray
}

Test-API "Product Service Metrics" {
    $response = Invoke-RestMethod -Uri "$productUrl/actuator/metrics" -Method GET
    if ($response.names.Count -lt 1) { throw "No metrics found" }
    Write-Host "    Available metrics: $($response.names.Count)" -ForegroundColor Gray
}

# ========================================
# SEARCH SERVICE - SEARCH APIs
# ========================================

Write-Host "`n=== SEARCH SERVICE - SEARCH APIs ===" -ForegroundColor Yellow

# Wait for indexing
Write-Host "  Waiting 5 seconds for search indexing..." -ForegroundColor Gray
Start-Sleep -Seconds 5

Test-API "Simple Product Search - 'milk'" {
    $body = @{
        query = "milk"
        storeId = 1
        page = 1
        pageSize = 10
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    if ($response.meta.totalHits -lt 1) { throw "No results for 'milk' search" }
    Write-Host "    Found: $($response.meta.totalHits) results in $($response.meta.processingTimeMs)ms" -ForegroundColor Gray
    if ($response.results.Count -gt 0) {
        Write-Host "    Top result: $($response.results[0].name)" -ForegroundColor DarkGray
    }
}

Test-API "Brand Search - 'amul'" {
    $body = @{
        query = "amul"
        storeId = 1
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    if ($response.meta.totalHits -lt 1) { throw "No Amul products found" }
    Write-Host "    Found: $($response.meta.totalHits) Amul products" -ForegroundColor Gray
}

Test-API "Category Search - 'snacks'" {
    $body = @{
        query = "snacks"
        storeId = 1
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Found: $($response.meta.totalHits) snacks" -ForegroundColor Gray
}

Test-API "Typo Tolerance - 'coka cola'" {
    $body = @{
        query = "coka cola"
        storeId = 1
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Found: $($response.meta.totalHits) results (with typo)" -ForegroundColor Gray
}

Test-API "Wildcard Search - all products" {
    $body = @{
        query = "*"
        storeId = 1
        page = 1
        pageSize = 20
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    if ($response.meta.totalHits -lt 10) { throw "Expected at least 10 products" }
    Write-Host "    Total products: $($response.meta.totalHits)" -ForegroundColor Gray
}

Test-API "Pagination - Page 1" {
    $body = @{
        query = "*"
        storeId = 1
        page = 1
        pageSize = 3
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    if ($response.meta.page -ne 1) { throw "Wrong page number" }
    if ($response.meta.returned -gt 3) { throw "Too many results returned" }
    Write-Host "    Page $($response.meta.page) of $($response.meta.totalPages), returned $($response.meta.returned)" -ForegroundColor Gray
}

# ========================================
# SEARCH SERVICE - ADMIN APIs
# ========================================

Write-Host "`n=== SEARCH SERVICE - ADMIN APIs ===" -ForegroundColor Yellow

Test-API "Create Synonym" {
    $body = @{
        term = "doodh"
        synonyms = @("milk", "doodh", "dudh")
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/synonyms" -Method POST -Body $body -Headers $adminHeaders
    if (-not $response.term) { throw "Synonym creation failed" }
    if ($response.term -ne "doodh") { throw "Wrong term" }
    Write-Host "    Created synonym for: $($response.term)" -ForegroundColor Gray
}

Test-API "Get All Synonyms" {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/synonyms" -Method GET -Headers $adminHeaders
    if ($response.Count -lt 1) { throw "No synonyms found" }
    Write-Host "    Total synonyms: $($response.Count)" -ForegroundColor Gray
}

Test-API "Get All Settings" {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/settings" -Method GET -Headers $adminHeaders
    Write-Host "    Total settings: $($response.Count)" -ForegroundColor Gray
}

Test-API "Trigger Config Sync" {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/sync" -Method POST -Headers $adminHeaders
    if (-not $response.status) { throw "No status in response" }
    Write-Host "    Status: $($response.status), Task: $($response.taskUid)" -ForegroundColor Gray
}

Test-API "Get Index Stats" {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/index/stats" -Method GET -Headers $adminHeaders
    if ($response.numberOfDocuments -lt 10) { throw "Expected at least 10 documents, got $($response.numberOfDocuments)" }
    Write-Host "    Documents: $($response.numberOfDocuments), Indexing: $($response.isIndexing)" -ForegroundColor Gray
}

# ========================================
# SEARCH SERVICE - HEALTH & METRICS
# ========================================

Write-Host "`n=== SEARCH SERVICE - HEALTH & METRICS ===" -ForegroundColor Yellow

Test-API "Search Service Health" {
    $response = Invoke-RestMethod -Uri "$searchUrl/actuator/health" -Method GET
    if ($response.status -ne "UP") { throw "Service not healthy: $($response.status)" }
    Write-Host "    Status: $($response.status)" -ForegroundColor Gray
    if ($response.components.syncHealthIndicator) {
        Write-Host "    Sync: $($response.components.syncHealthIndicator.details.state)" -ForegroundColor DarkGray
    }
}

Test-API "Search Metrics - Total Requests" {
    $response = Invoke-RestMethod -Uri "$searchUrl/actuator/metrics/search.requests.total" -Method GET
    $totalRequests = $response.measurements[0].value
    if ($totalRequests -lt 1) { throw "No search requests recorded" }
    Write-Host "    Total requests: $totalRequests" -ForegroundColor Gray
}

Test-API "Search Metrics - Search Duration" {
    $response = Invoke-RestMethod -Uri "$searchUrl/actuator/metrics/search.duration" -Method GET
    Write-Host "    Avg duration: $([math]::Round($response.measurements[0].value, 2))ms" -ForegroundColor Gray
}

# ========================================
# SUMMARY
# ========================================

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "TEST SUMMARY" -ForegroundColor Magenta
Write-Host "========================================" -ForegroundColor Magenta
Write-Host "Total Tests: $totalTests" -ForegroundColor White
Write-Host "Passed: $passedTests" -ForegroundColor Green
Write-Host "Failed: $failedTests" -ForegroundColor $(if ($failedTests -gt 0) { "Red" } else { "Green" })
$successRate = [math]::Round(($passedTests/$totalTests)*100, 2)
Write-Host "Success Rate: $successRate%" -ForegroundColor $(if ($successRate -ge 90) { "Green" } elseif ($successRate -ge 70) { "Yellow" } else { "Red" })
Write-Host "`nServices:" -ForegroundColor White
Write-Host "  Product Service: $productUrl" -ForegroundColor Gray
Write-Host "  Search Service: $searchUrl" -ForegroundColor Gray
Write-Host "  Admin Credentials: admin/admin123`n" -ForegroundColor Gray

if ($failedTests -eq 0) {
    Write-Host "ALL TESTS PASSED!" -ForegroundColor Green
} else {
    Write-Host "Some tests failed. Review the output above for details." -ForegroundColor Yellow
}
