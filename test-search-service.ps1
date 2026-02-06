# Search Service Testing Script
# Tests all endpoints of the search-service

$baseUrl = "http://localhost:8083"
$productUrl = "http://localhost:8081"
$adminUser = "admin"
$adminPass = "admin123"

# Create base64 auth
$base64AuthInfo = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${adminUser}:${adminPass}"))
$headers = @{
    Authorization = "Basic $base64AuthInfo"
    "Content-Type" = "application/json"
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Search Service Testing" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

# Test 1: Health Check (Public)
Write-Host "Test 1: Health Check" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method GET
    Write-Host "✓ Health Check: $($response.status)" -ForegroundColor Green
    Write-Host "  Sync Status: $($response.components.syncHealthIndicator.details.state)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Health Check Failed: $_" -ForegroundColor Red
}

# Test 2: Search (Public) - Simple Query
Write-Host "`nTest 2: Simple Search - 'milk'" -ForegroundColor Yellow
try {
    $searchBody = @{
        query = "milk"
        storeId = 1
        page = 1
        pageSize = 10
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$baseUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "✓ Search Success" -ForegroundColor Green
    Write-Host "  Query: $($response.query)" -ForegroundColor Gray
    Write-Host "  Total Hits: $($response.meta.totalHits)" -ForegroundColor Gray
    Write-Host "  Returned: $($response.meta.returned)" -ForegroundColor Gray
    Write-Host "  Processing Time: $($response.meta.processingTimeMs)ms" -ForegroundColor Gray
    if ($response.results.Count -gt 0) {
        Write-Host "  First Result: $($response.results[0].name)" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ Search Failed: $_" -ForegroundColor Red
}

# Test 3: Search - Brand Search
Write-Host "`nTest 3: Brand Search - 'amul'" -ForegroundColor Yellow
try {
    $searchBody = @{
        query = "amul"
        storeId = 1
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$baseUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "✓ Brand Search Success" -ForegroundColor Green
    Write-Host "  Total Hits: $($response.meta.totalHits)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Brand Search Failed: $_" -ForegroundColor Red
}

# Test 4: Search - No Results (Fallback Test)
Write-Host "`nTest 4: No Results / Fallback - 'xyz123notfound'" -ForegroundColor Yellow
try {
    $searchBody = @{
        query = "xyz123notfound"
        storeId = 1
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$baseUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
    Write-Host "✓ Fallback Working" -ForegroundColor Green
    Write-Host "  Returned (fallback): $($response.meta.returned)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Fallback Test Failed: $_" -ForegroundColor Red
}

# Test 5: Admin - Get All Synonyms
Write-Host "`nTest 5: Admin - Get All Synonyms" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/search/synonyms" -Method GET -Headers $headers
    Write-Host "✓ Get Synonyms Success" -ForegroundColor Green
    Write-Host "  Total Synonyms: $($response.Count)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Get Synonyms Failed: $_" -ForegroundColor Red
}

# Test 6: Admin - Create Synonym
Write-Host "`nTest 6: Admin - Create Synonym" -ForegroundColor Yellow
try {
    $synonymBody = @{
        term = "aata"
        synonyms = @("atta", "wheat flour", "flour")
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$baseUrl/admin/search/synonyms" -Method POST -Body $synonymBody -Headers $headers
    Write-Host "✓ Create Synonym Success" -ForegroundColor Green
    Write-Host "  Term: $($response.term)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Create Synonym Failed: $_" -ForegroundColor Red
}

# Test 7: Admin - Get All Settings
Write-Host "`nTest 7: Admin - Get All Settings" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/search/settings" -Method GET -Headers $headers
    Write-Host "✓ Get Settings Success" -ForegroundColor Green
    Write-Host "  Total Settings: $($response.Count)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Get Settings Failed: $_" -ForegroundColor Red
}

# Test 8: Admin - Trigger Configuration Sync
Write-Host "`nTest 8: Admin - Trigger Config Sync" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/search/sync" -Method POST -Headers $headers
    Write-Host "✓ Config Sync Triggered" -ForegroundColor Green
    Write-Host "  Status: $($response.status)" -ForegroundColor Gray
    Write-Host "  Task UID: $($response.taskUid)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Config Sync Failed: $_" -ForegroundColor Red
}

# Test 9: Admin - Get Index Stats
Write-Host "`nTest 9: Admin - Get Index Stats" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/search/index/stats" -Method GET -Headers $headers
    Write-Host "✓ Get Index Stats Success" -ForegroundColor Green
    Write-Host "  Documents: $($response.numberOfDocuments)" -ForegroundColor Gray
} catch {
    Write-Host "✗ Get Index Stats Failed: $_" -ForegroundColor Red
}

# Test 10: Metrics Endpoint
Write-Host "`nTest 10: Metrics - Search Metrics" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/actuator/metrics" -Method GET
    Write-Host "✓ Metrics Available" -ForegroundColor Green
    Write-Host "  Metrics Count: $($response.names.Count)" -ForegroundColor Gray
    
    # Check specific search metrics
    if ($response.names -contains "search.requests.total") {
        $searchMetrics = Invoke-RestMethod -Uri "$baseUrl/actuator/metrics/search.requests.total" -Method GET
        Write-Host "  Search Requests: $($searchMetrics.measurements[0].value)" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ Metrics Failed: $_" -ForegroundColor Red
}

# Test 11: Rate Limiting Test (Send multiple requests)
Write-Host "`nTest 11: Rate Limiting (sending 5 requests quickly)" -ForegroundColor Yellow
$rateLimitResults = @()
for ($i = 1; $i -le 5; $i++) {
    try {
        $searchBody = @{
            query = "test"
            storeId = 1
        } | ConvertTo-Json
        
        $response = Invoke-RestMethod -Uri "$baseUrl/search" -Method POST -Body $searchBody -ContentType "application/json"
        $rateLimitResults += "✓"
    } catch {
        if ($_.Exception.Response.StatusCode -eq 429) {
            $rateLimitResults += "429"
        } else {
            $rateLimitResults += "✗"
        }
    }
}
Write-Host "  Results: $($rateLimitResults -join ', ')" -ForegroundColor Gray

# Test 12: Circuit Breaker Status
Write-Host "`nTest 12: Circuit Breaker Status (via logs)" -ForegroundColor Yellow
Write-Host "  Note: Circuit breaker logs appear in console output" -ForegroundColor Gray
Write-Host "  Look for 'CB: CLOSED' or 'CB: OPEN' in logs" -ForegroundColor Gray

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Testing Complete!" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

Write-Host "Summary:" -ForegroundColor Yellow
Write-Host "- Search Service running on port 8083" -ForegroundColor Gray
Write-Host "- Product Service running on port 8081" -ForegroundColor Gray
Write-Host "- Meilisearch running on port 7700" -ForegroundColor Gray
Write-Host "- Admin credentials: admin/admin123" -ForegroundColor Gray
