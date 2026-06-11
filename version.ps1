param(
    [string]$Version
)

$ErrorActionPreference = "Stop"

if (-not $Version) {
    $Version = Read-Host "Nueva version (ej: 1.0.1)"
}

if ($Version -notmatch "^\d+\.\d+\.\d+$") {
    Write-Host "Formato invalido. Usa: MAJOR.MINOR.PATCH" -ForegroundColor Red
    exit 1
}

Write-Host "Aplicando version $Version..." -ForegroundColor Yellow

$ROOT = $PSScriptRoot
$POM  = Join-Path $ROOT "backend\pom.xml"
$PKG  = Join-Path $ROOT "frontend\package.json"

# pom.xml — solo la version del proyecto (la que sigue a <artifactId>backend</artifactId>)
$pom = Get-Content $POM -Raw
$pom = $pom -replace '(<artifactId>backend</artifactId>\s*<version>)\d+\.\d+\.\d+(</version>)', "`${1}$Version`${2}"
$pom | Out-File $POM -Encoding utf8
Write-Host "  pom.xml actualizado" -ForegroundColor Green

# package.json
$pkg = Get-Content $PKG -Raw | ConvertFrom-Json
$pkg.version = $Version
$pkg | ConvertTo-Json -Depth 10 | Out-File $PKG -Encoding utf8
Write-Host "  package.json actualizado" -ForegroundColor Green

# git
git add backend/pom.xml frontend/package.json
git commit -m "chore: bump version to $Version"
git tag -a "v$Version" -m "Release $Version"
git push
git push origin "v$Version"

Write-Host "Listo! Version $Version publicada." -ForegroundColor Green
