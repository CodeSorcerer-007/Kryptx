#!/usr/bin/env pwsh
# Kryptx Test Count Verification Script
# Counts all JUnit test cases across all modules and validates against documentation

Write-Host "`n=== Kryptx Test Count Verification ===" -ForegroundColor Cyan
Write-Host "Analyzing test results from all modules...`n" -ForegroundColor Gray

$modules = @(
    @{Name="app"; Path="app"},
    @{Name="core:crypto"; Path="core\crypto"},
    @{Name="core:database"; Path="core\database"},
    @{Name="core:model"; Path="core\model"},
    @{Name="core:security"; Path="core\security"}
)

$grandTotal = 0
$moduleResults = @()

foreach ($module in $modules) {
    $testResultsPath = Join-Path $module.Path "build\test-results\testDebugUnitTest"
    
    if (Test-Path $testResultsPath) {
        $xmlFiles = Get-ChildItem -Path $testResultsPath -Filter "TEST-*.xml"
        $moduleTotal = 0
        
        foreach ($xmlFile in $xmlFiles) {
            $content = Get-Content $xmlFile.FullName -Raw
            if ($content -match 'tests="(\d+)"') {
                $moduleTotal += [int]$matches[1]
            }
        }
        
        $moduleResults += @{
            Name = $module.Name
            Count = $moduleTotal
        }
        
        $grandTotal += $moduleTotal
        
        $color = if ($moduleTotal -gt 0) { "Green" } else { "Yellow" }
        Write-Host "  $($module.Name.PadRight(20)) : $moduleTotal tests" -ForegroundColor $color
    } else {
        Write-Host "  $($module.Name.PadRight(20)) : No test results found" -ForegroundColor Yellow
    }
}

Write-Host "`n  $('=' * 44)" -ForegroundColor Cyan
Write-Host "  GRAND TOTAL              : $grandTotal tests" -ForegroundColor Green -BackgroundColor DarkGreen
Write-Host "  $('=' * 44)`n" -ForegroundColor Cyan

# Verify against expected count
$expectedCount = 715
if ($grandTotal -eq $expectedCount) {
    Write-Host "Test count verified: $grandTotal matches expected $expectedCount" -ForegroundColor Green
    exit 0
} else {
    Write-Host "X Test count mismatch: Expected $expectedCount, found $grandTotal" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please update documentation in:" -ForegroundColor Yellow
    Write-Host "  - README.md (badges and build section)" -ForegroundColor Gray
    Write-Host "  - CHANGELOG.md (latest version section)" -ForegroundColor Gray
    exit 1
}
