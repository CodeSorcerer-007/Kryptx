#!/usr/bin/env pwsh
# Kryptx Documentation Consistency Validation Script
# Ensures test counts and version numbers are synchronized across all docs

Write-Host "`n=== Kryptx Documentation Validation ===" -ForegroundColor Cyan
Write-Host "Checking consistency across README, CHANGELOG, and docs...`n" -ForegroundColor Gray

$errors = @()
$expectedTestCount = 715
$expectedVersion = "2.5.1"
$expectedVersionCode = 31

# Files to check
$files = @(
    @{Path="README.md"; Patterns=@("715", "2.5.1", "Code 31")},
    @{Path="CHANGELOG.md"; Patterns=@("715", "[2.5.1]", "versionCode 31")},
    @{Path="docs\TEST_COVERAGE.md"; Patterns=@("715", "2.5.1")},
    @{Path="scripts\verify-test-counts.ps1"; Patterns=@('$expectedCount = 715')}
)

foreach ($file in $files) {
    Write-Host "Checking $($file.Path)..." -ForegroundColor Yellow
    
    if (-not (Test-Path $file.Path)) {
        $errors += "  X File not found: $($file.Path)"
        Write-Host "  X File not found" -ForegroundColor Red
        continue
    }
    
    $content = Get-Content $file.Path -Raw
    $allPatternsFound = $true
    
    foreach ($pattern in $file.Patterns) {
        if ($content -notlike "*$pattern*") {
            $errors += "  X Missing pattern '$pattern' in $($file.Path)"
            Write-Host "  X Missing: $pattern" -ForegroundColor Red
            $allPatternsFound = $false
        }
    }
    
    if ($allPatternsFound) {
        Write-Host "  OK All patterns found" -ForegroundColor Green
    }
}

# Check AndroidManifest for zero-network guarantee
Write-Host "`nValidating Zero-Network Security Guarantee..." -ForegroundColor Yellow
$manifest = Get-Content "app\src\main\AndroidManifest.xml" -Raw

$forbiddenPermissions = @(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE"
)

$networkViolations = $false
foreach ($perm in $forbiddenPermissions) {
    if ($manifest -like "*$perm*") {
        $errors += "  X SECURITY VIOLATION: $perm found in AndroidManifest.xml"
        Write-Host "  X Found: $perm" -ForegroundColor Red
        $networkViolations = $true
    }
}

if (-not $networkViolations) {
    Write-Host "  OK Zero network permissions verified" -ForegroundColor Green
}

# Summary
Write-Host "`n$('=' * 50)" -ForegroundColor Cyan
if ($errors.Count -eq 0) {
    Write-Host "SUCCESS: All documentation is consistent!" -ForegroundColor Green -BackgroundColor DarkGreen
    Write-Host "  - Test count: $expectedTestCount" -ForegroundColor Gray
    Write-Host "  - Version: $expectedVersion (code $expectedVersionCode)" -ForegroundColor Gray
    Write-Host "  - Zero-network guarantee: VERIFIED" -ForegroundColor Gray
    exit 0
} else {
    Write-Host "FAILED: Found $($errors.Count) inconsistencies" -ForegroundColor Red -BackgroundColor DarkRed
    Write-Host ""
    foreach ($error in $errors) {
        Write-Host $error -ForegroundColor Red
    }
    exit 1
}
