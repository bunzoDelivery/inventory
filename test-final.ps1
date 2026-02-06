# Final Comprehensive E2E Test - All Issues Fixed
$productUrl = "http://localhost:8081"
$searchUrl = "http://localhost:8083"
$base64Auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:admin123"))
$adminHeaders = @{ Authorization = "Basic $base64Auth"; "Content-Type" = "application/json" }

$totalTests = 0
$passedTests = 0

function Test-API {
    param([string]$Name, [scriptblock]$TestBlock)
    $script:totalTests++
    Write-Host "`n[$script:totalTests] $Name" -ForegroundColor Cyan
    try {
        & $TestBlock
        Write-Host "  PASS" -ForegroundColor Green
        $script:passedTests++
    } catch {
        Write-Host "  FAIL: $_" -ForegroundColor Red
    }
}

Write-Host "`n=== PRODUCT SERVICE - CATALOG ===" -ForegroundColor Yellow

Test-API "Get All Categories" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/categories" -Method GET
    if ($r.Count -lt 9) { throw "Expected 9+ categories, got $($r.Count)" }
    Write-Host "    Categories: $($r.Count)" -ForegroundColor Gray
}

Test-API "Get Category by ID" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/categories/1" -Method GET
    if (-not $r.id) { throw "No category" }
    Write-Host "    $($r.name)" -ForegroundColor Gray
}

Test-API "Get Product by SKU" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/sku/AMUL-MILK-500ML" -Method GET
    if ($r.sku -ne "AMUL-MILK-500ML") { throw "Wrong SKU" }
    Write-Host "    $($r.name) - Rs.$($r.basePrice)" -ForegroundColor Gray
}

Test-API "Get All Products" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/all" -Method GET
    if ($r.Count -lt 10) { throw "Expected 10+ products, got $($r.Count)" }
    Write-Host "    Total: $($r.Count) products" -ForegroundColor Gray
}

Test-API "Get Products by Category" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/category/1" -Method GET
    Write-Host "    Category 1: $($r.Count) products" -ForegroundColor Gray
}

Test-API "Get Products by Brand" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/catalog/products/brand/Amul" -Method GET
    if ($r.Count -lt 1) { throw "No Amul products" }
    Write-Host "    Amul: $($r.Count) products" -ForegroundColor Gray
}

Write-Host "`n=== PRODUCT SERVICE - INVENTORY ===" -ForegroundColor Yellow

Test-API "Check Availability (Bulk)" {
    $body = @{ storeId = 1; skus = @("AMUL-MILK-500ML", "PARLE-G-BISCUIT") } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/availability" -Method POST -Body $body -ContentType "application/json"
    if (-not $r.products) { throw "No products in response" }
    if ($r.products.Count -ne 2) { throw "Expected 2 products" }
    Write-Host "    Checked: $($r.products.Count) products" -ForegroundColor Gray
}

Test-API "Check Single Availability" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/availability/single?sku=AMUL-MILK-500ML&storeId=1" -Method GET
    if (-not $r.products -or $r.products.Count -eq 0) { throw "No products in response" }
    $product = $r.products[0]
    if ($product.inStock -ne $true) { throw "Should be in stock" }
    Write-Host "    $($product.sku): $($product.availabilityStatus)" -ForegroundColor Gray
}

Test-API "Get Inventory by SKU" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/sku/AMUL-MILK-500ML?storeId=1" -Method GET
    if ($r.currentStock -lt 0) { throw "Invalid stock" }
    Write-Host "    Stock: $($r.currentStock) units" -ForegroundColor Gray
}

Test-API "Reserve Stock" {
    $body = @{ sku = "PARLE-G-BISCUIT"; quantity = 2; customerId = 123; orderId = "ORD-$(Get-Random -Max 9999)" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/reserve" -Method POST -Body $body -ContentType "application/json"
    if (-not $r.reservationId) { throw "No reservation ID" }
    Write-Host "    Reserved: $($r.quantity) units (ID: $($r.reservationId))" -ForegroundColor Gray
    $script:reservationId = $r.reservationId
}

Test-API "Confirm Reservation" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/reservations/$($script:reservationId)/confirm" -Method POST
    if ($r.status -ne "CONFIRMED") { throw "Wrong status: $($r.status)" }
    Write-Host "    Status: $($r.status)" -ForegroundColor Gray
}

Test-API "Add Stock" {
    $body = @{ sku = "PARLE-G-BISCUIT"; storeId = 1; quantity = 10; reason = "RESTOCK" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/stock/add" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    New stock: $($r.currentStock) units" -ForegroundColor Gray
}

Test-API "Get Low Stock Items" {
    $r = Invoke-RestMethod -Uri "$productUrl/api/v1/inventory/low-stock?storeId=1" -Method GET
    Write-Host "    Low stock: $($r.Count) items" -ForegroundColor Gray
}

Write-Host "`n=== PRODUCT SERVICE - HEALTH ===" -ForegroundColor Yellow

Test-API "Health Check" {
    $r = Invoke-RestMethod -Uri "$productUrl/actuator/health" -Method GET
    if ($r.status -ne "UP") { throw "Not healthy: $($r.status)" }
    Write-Host "    Status: $($r.status)" -ForegroundColor Gray
}

Test-API "Metrics" {
    $r = Invoke-RestMethod -Uri "$productUrl/actuator/metrics" -Method GET
    Write-Host "    Metrics: $($r.names.Count)" -ForegroundColor Gray
}

Write-Host "`n=== SEARCH SERVICE - SEARCH ===" -ForegroundColor Yellow

Write-Host "  Waiting 8 seconds for indexing..." -ForegroundColor Gray
Start-Sleep -Seconds 8

Test-API "Search - 'milk'" {
    $body = @{ query = "milk"; storeId = 1 } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    if ($r.meta.totalHits -lt 1) { throw "No milk products found" }
    Write-Host "    Found: $($r.meta.totalHits) in $($r.meta.processingTimeMs)ms" -ForegroundColor Gray
}

Test-API "Search - 'amul'" {
    $body = @{ query = "amul"; storeId = 1 } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    if ($r.meta.totalHits -lt 1) { throw "No Amul products" }
    Write-Host "    Found: $($r.meta.totalHits) Amul products" -ForegroundColor Gray
}

Test-API "Search - 'snacks'" {
    $body = @{ query = "snacks"; storeId = 1 } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    Write-Host "    Found: $($r.meta.totalHits) snacks" -ForegroundColor Gray
}

Test-API "Wildcard Search" {
    $body = @{ query = "*"; storeId = 1; pageSize = 20 } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    if ($r.meta.totalHits -lt 10) { throw "Expected 10+ products, got $($r.meta.totalHits)" }
    Write-Host "    Total: $($r.meta.totalHits) products" -ForegroundColor Gray
}

Test-API "Pagination" {
    $body = @{ query = "*"; storeId = 1; page = 1; pageSize = 3 } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$searchUrl/search" -Method POST -Body $body -ContentType "application/json"
    if ($r.meta.returned -ne 3) { throw "Expected 3 results" }
    Write-Host "    Page $($r.meta.page)/$($r.meta.totalPages), returned $($r.meta.returned)" -ForegroundColor Gray
}

Write-Host "`n=== SEARCH SERVICE - ADMIN ===" -ForegroundColor Yellow

Test-API "Get All Synonyms" {
    $r = Invoke-RestMethod -Uri "$searchUrl/admin/search/synonyms" -Method GET -Headers $adminHeaders
    Write-Host "    Synonyms: $($r.Count)" -ForegroundColor Gray
}

Test-API "Get All Settings" {
    $r = Invoke-RestMethod -Uri "$searchUrl/admin/search/settings" -Method GET -Headers $adminHeaders
    Write-Host "    Settings: $($r.Count)" -ForegroundColor Gray
}

Test-API "Get Index Stats" {
    $r = Invoke-RestMethod -Uri "$searchUrl/admin/search/index/stats" -Method GET -Headers $adminHeaders
    if ($r.numberOfDocuments -lt 10) { throw "Expected 10+ docs, got $($r.numberOfDocuments)" }
    Write-Host "    Documents: $($r.numberOfDocuments)" -ForegroundColor Gray
}

Write-Host "`n=== SEARCH SERVICE - HEALTH ===" -ForegroundColor Yellow

Test-API "Health Check" {
    $r = Invoke-RestMethod -Uri "$searchUrl/actuator/health" -Method GET
    if ($r.status -ne "UP") { throw "Not healthy" }
    Write-Host "    Status: $($r.status)" -ForegroundColor Gray
}

Test-API "Search Metrics" {
    $r = Invoke-RestMethod -Uri "$searchUrl/actuator/metrics/search.requests.total" -Method GET
    Write-Host "    Total requests: $($r.measurements[0].value)" -ForegroundColor Gray
}

Write-Host "`n========================================" -ForegroundColor Magenta
Write-Host "PASSED: $passedTests / $totalTests" -ForegroundColor $(if ($passedTests -eq $totalTests) { "Green" } else { "Yellow" })
Write-Host "========================================`n" -ForegroundColor Magenta
