# Comprehensive E2E API Testing Script
# Tests ALL APIs in product-service and search-service

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
Write-Host "COMPREHENSIVE API TESTING" -ForegroundColor Magenta
Write-Host "========================================`n" -ForegroundColor Magenta

# ========================================
# PRODUCT SERVICE - CATALOG APIs
# ========================================

Write-Host "`n=== PRODUCT SERVICE - CATALOG APIs ===" -ForegroundColor Yellow

Test-API "Get All Categories" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/categories" -Method GET
    if ($response.Count -lt 1) { throw "No categories found" }
    Write-Host "    Found $($response.Count) categories" -ForegroundColor Gray
}

Test-API "Get Category by ID" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/categories/1" -Method GET
    if (-not $response.id) { throw "Category not found" }
    Write-Host "    Category: $($response.name)" -ForegroundColor Gray
}

Test-API "Get Product by SKU" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/sku/AMUL-MILK-500ML" -Method GET
    if (-not $response.sku) { throw "Product not found" }
    Write-Host "    Product: $($response.name) - Rs.$($response.basePrice)" -ForegroundColor Gray
}

Test-API "Get All Products" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/all" -Method GET
    if ($response.Count -lt 1) { throw "No products found" }
    Write-Host "    Found $($response.Count) products" -ForegroundColor Gray
}

Test-API "Get Bestsellers" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/bestsellers?limit=5" -Method GET
    Write-Host "    Found $($response.Count) bestsellers" -ForegroundColor Gray
}

Test-API "Get Products by Category" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/categories/1/products" -Method GET
    Write-Host "    Found $($response.Count) products in category" -ForegroundColor Gray
}

# ========================================
# PRODUCT SERVICE - INVENTORY APIs
# ========================================

Write-Host "`n=== PRODUCT SERVICE - INVENTORY APIs ===" -ForegroundColor Yellow

Test-API "Check Inventory Availability" {
    $body = @{
        storeId = 1
        productIds = @(118, 119, 120)
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/availability" -Method POST -Body $body -ContentType "application/json"
    if (-not $response.availability) { throw "No availability data" }
    Write-Host "    Checked $($response.availability.Count) products" -ForegroundColor Gray
}

Test-API "Get Inventory by SKU" {
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/sku/AMUL-MILK-500ML?storeId=1" -Method GET
    if (-not $response.sku) { throw "Inventory not found" }
    Write-Host "    Stock: $($response.currentStock) units" -ForegroundColor Gray
}

Test-API "Reserve Stock" {
    $body = @{
        storeId = 1
        sku = "PARLE-G-BISCUIT"
        quantity = 2
        orderId = "TEST-ORDER-001"
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/reserve" -Method POST -Body $body -ContentType "application/json"
    if (-not $response.reservationId) { throw "Reservation failed" }
    Write-Host "    Reserved: $($response.quantity) units (ID: $($response.reservationId))" -ForegroundColor Gray
    $script:reservationId = $response.reservationId
}

Test-API "Confirm Reservation" {
    if (-not $script:reservationId) { throw "No reservation to confirm" }
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/reservation/$($script:reservationId)/confirm" -Method POST
    if ($response.status -ne "CONFIRMED") { throw "Confirmation failed" }
    Write-Host "    Status: $($response.status)" -ForegroundColor Gray
}

Test-API "Add Stock" {
    $body = @{
        storeId = 1
        sku = "PARLE-G-BISCUIT"
        quantity = 5
        reason = "TEST_RESTOCK"
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/add-stock" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    New stock: $($response.currentStock) units" -ForegroundColor Gray
}

# ========================================
# PRODUCT SERVICE - HEALTH & METRICS
# ========================================

Write-Host "`n=== PRODUCT SERVICE - HEALTH & METRICS ===" -ForegroundColor Yellow

Test-API "Product Service Health" {
    $response = Invoke-RestMethod -Uri "$productUrl/actuator/health" -Method GET
    if ($response.status -ne "UP") { throw "Service not healthy" }
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

Test-API "Simple Product Search" {
    $body = @{
        query = "milk"
        storeId = 1
        page = 1
        pageSize = 10
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Found: $($response.meta.totalHits) results in $($response.meta.processingTimeMs)ms" -ForegroundColor Gray
}

Test-API "Brand Search" {
    $body = @{
        query = "amul"
        storeId = 1
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Found: $($response.meta.totalHits) Amul products" -ForegroundColor Gray
}

Test-API "Category Search" {
    $body = @{
        query = "snacks"
        storeId = 1
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Found: $($response.meta.totalHits) snacks" -ForegroundColor Gray
}

Test-API "Typo Tolerance" {
    $body = @{
        query = "coka cola"
        storeId = 1
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Found: $($response.meta.totalHits) results (with typo)" -ForegroundColor Gray
}

Test-API "Empty Search (Fallback)" {
    $body = @{
        query = "xyz123notfound"
        storeId = 1
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Fallback results: $($response.meta.returned)" -ForegroundColor Gray
}

Test-API "Pagination" {
    $body = @{
        query = ""
        storeId = 1
        page = 1
        pageSize = 3
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Page $($response.meta.page) of $($response.meta.totalPages)" -ForegroundColor Gray
}

# ========================================
# SEARCH SERVICE - ADMIN APIs
# ========================================

Write-Host "`n=== SEARCH SERVICE - ADMIN APIs ===" -ForegroundColor Yellow

Test-API "Create Synonym" {
    $body = @{
        term = "aata"
        synonyms = @("atta", "wheat flour", "gehun")
    } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/synonyms" -Method POST -Body $body -Headers $adminHeaders
    if (-not $response.term) { throw "Synonym creation failed" }
    Write-Host "    Created synonym for: $($response.term)" -ForegroundColor Gray
}

Test-API "Get All Synonyms" {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/synonyms" -Method GET -Headers $adminHeaders
    Write-Host "    Total synonyms: $($response.Count)" -ForegroundColor Gray
}

Test-API "Get All Settings" {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/settings" -Method GET -Headers $adminHeaders
    Write-Host "    Total settings: $($response.Count)" -ForegroundColor Gray
}

Test-API "Trigger Config Sync" {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/sync" -Method POST -Headers $adminHeaders
    if ($response.status -ne "ACCEPTED") { throw "Sync not triggered" }
    Write-Host "    Status: $($response.status)" -ForegroundColor Gray
}

Test-API "Get Index Stats" {
    $response = Invoke-RestMethod -Uri "$searchUrl/admin/search/index/stats" -Method GET -Headers $adminHeaders
    Write-Host "    Documents: $($response.numberOfDocuments), Indexing: $($response.isIndexing)" -ForegroundColor Gray
}

# ========================================
# SEARCH SERVICE - HEALTH & METRICS
# ========================================

Write-Host "`n=== SEARCH SERVICE - HEALTH & METRICS ===" -ForegroundColor Yellow

Test-API "Search Service Health" {
    $response = Invoke-RestMethod -Uri "$searchUrl/actuator/health" -Method GET
    if ($response.status -ne "UP") { throw "Service not healthy" }
    Write-Host "    Status: $($response.status)" -ForegroundColor Gray
}

Test-API "Search Metrics" {
    $response = Invoke-RestMethod -Uri "$searchUrl/actuator/metrics/search.requests.total" -Method GET
    Write-Host "    Total requests: $($response.measurements[0].value)" -ForegroundColor Gray
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
Write-Host "Success Rate: $([math]::Round(($passedTests/$totalTests)*100, 2))%" -ForegroundColor $(if ($failedTests -eq 0) { "Green" } else { "Yellow" })
Write-Host "`nServices:" -ForegroundColor White
Write-Host "  Product Service: $productUrl" -ForegroundColor Gray
Write-Host "  Search Service: $searchUrl" -ForegroundColor Gray
Write-Host "  Admin Credentials: admin/admin123`n" -ForegroundColor Gray
