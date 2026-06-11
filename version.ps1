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

$ROOT    = (Get-Location).Path
$pomPath = Join-Path $ROOT "backend\pom.xml"
$pkgPath = Join-Path $ROOT "frontend\package.json"

# pom.xml — solo la version del proyecto
$pomXml = Get-Content -LiteralPath $pomPath -Raw
$pomXml = $pomXml -replace '(<artifactId>backend</artifactId>\s*<version>)\d+\.\d+\.\d+(</version>)', "`${1}$Version`${2}"
Set-Content -LiteralPath $pomPath -Value $pomXml
Write-Host "  pom.xml actualizado" -ForegroundColor Green

# package.json
$pkgObj = Get-Content -LiteralPath $pkgPath -Raw | ConvertFrom-Json
$pkgObj.version = $Version
$pkgObj | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $pkgPath
Write-Host "  package.json actualizado" -ForegroundColor Green

# git
git add backend/pom.xml frontend/package.json
git commit -m "chore: bump version to $Version"
git tag -a "v$Version" -m "Release $Version"
git push
git push origin "v$Version"

Write-Host "Listo! Version $Version publicada." -ForegroundColor Green
