#!/usr/bin/env pwsh
# =============================================================================
# bb-form build script cho Windows (PowerShell)
# Usage: pwsh scripts/build.ps1 [-Version "0.1.0"]
# =============================================================================
param(
    [string]$Version = "0.1.0"
)

$ErrorActionPreference = "Stop"

$RootDir   = Split-Path $PSScriptRoot
$DistDir   = Join-Path $RootDir "dist"
$ReleaseDir = Join-Path $RootDir "release"

Write-Host "══════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  bb-form build — v$Version" -ForegroundColor Cyan
Write-Host "══════════════════════════════════════════════" -ForegroundColor Cyan

New-Item -ItemType Directory -Force -Path $DistDir   | Out-Null
New-Item -ItemType Directory -Force -Path $ReleaseDir | Out-Null

# ──────────────────────────────────────────────
# Bước 1: Build uberscript
# ──────────────────────────────────────────────
Write-Host ""
Write-Host "📦 Building uberscript..." -ForegroundColor Yellow
Set-Location $RootDir
bb uberscript dist/bb-form.clj -m com.drbinhthanh.bb-form
Write-Host "✅ Uberscript: $DistDir\bb-form.clj" -ForegroundColor Green

# ──────────────────────────────────────────────
# Bước 2: Đóng gói Windows x86_64
# ──────────────────────────────────────────────
Write-Host ""
Write-Host "📦 Đang đóng gói windows-x86_64..." -ForegroundColor Yellow

$TempDir = Join-Path $env:TEMP "bb-form-pkg-$(Get-Random)"
New-Item -ItemType Directory -Force -Path $TempDir | Out-Null

# Copy uberscript
Copy-Item "$DistDir\bb-form.clj" "$TempDir\"

# Tạo .bat launcher
$BatContent = "@echo off`r`nsetlocal EnableDelayedExpansion`r`nset `"SCRIPT_DIR=%~dp0`"`r`nbb `"%SCRIPT_DIR%bb-form.clj`" %*`r`n"
[System.IO.File]::WriteAllText("$TempDir\bb-form.bat", $BatContent, [System.Text.Encoding]::ASCII)

# Tạo PowerShell launcher
$Ps1Content = "`$ScriptDir = Split-Path -Parent `$MyInvocation.MyCommand.Path`n& bb `"`$ScriptDir\bb-form.clj`" @args`n"
Set-Content -Path "$TempDir\bb-form.ps1" -Value $Ps1Content -Encoding UTF8

# Đóng gói ZIP
$ZipOutput = "$ReleaseDir\bb-form-windows-x86_64.zip"
Compress-Archive -Force `
    -Path "$TempDir\bb-form.bat", "$TempDir\bb-form.ps1", "$TempDir\bb-form.clj" `
    -DestinationPath $ZipOutput

Remove-Item -Recurse -Force $TempDir

$ZipSize = [math]::Round((Get-Item $ZipOutput).Length / 1KB, 1)
Write-Host "✅ $ZipOutput ($ZipSize KB)" -ForegroundColor Green

# ──────────────────────────────────────────────
# Tổng kết + SHA256
# ──────────────────────────────────────────────
Write-Host ""
Write-Host "══════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ✅ Hoàn thành! Package đã tạo:" -ForegroundColor Cyan
Write-Host "══════════════════════════════════════════════" -ForegroundColor Cyan
Get-ChildItem $ReleaseDir | Format-Table Name, @{L='Size(KB)';E={[math]::Round($_.Length/1KB,1)}}

Write-Host "SHA256:" -ForegroundColor Yellow
Get-ChildItem $ReleaseDir -Filter "*.zip" | ForEach-Object {
    $Hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    Write-Host "$Hash  $($_.Name)"
}
