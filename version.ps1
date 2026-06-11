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

# Normalizar paths a Windows absoluto independientemente de como se invoque el script
$ROOT = [System.IO.Path]::GetFullPath($PSScriptRoot)
$POM  = [System.IO.Path]::Combine($ROOT, "backend", "pom.xml")
$PKG  = [System.IO.Path]::Combine($ROOT, "frontend", "package.json")
$NoBom = New-Object System.Text.UTF8Encoding $false

# pom.xml — solo la version del proyecto (la que sigue a <artifactId>backend</artifactId>)
$pom = [System.IO.File]::ReadAllText($POM, $NoBom)
$pom = $pom -replace '(<artifactId>backend</artifactId>\s*<version>)\d+\.\d+\.\d+(</version>)', "`${1}$Version`${2}"
[System.IO.File]::WriteAllText($POM, $pom, $NoBom)
Write-Host "  pom.xml actualizado" -ForegroundColor Green

# package.json
$pkg = [System.IO.File]::ReadAllText($PKG, $NoBom) | ConvertFrom-Json
$pkg.version = $Version
$json = $pkg | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($PKG, $json, $NoBom)
Write-Host "  package.json actualizado" -ForegroundColor Green

# git
git add backend/pom.xml frontend/package.json
git commit -m "chore: bump version to $Version"
git tag -a "v$Version" -m "Release $Version"
git push
git push origin "v$Version"

Write-Host "Listo! Version $Version publicada." -ForegroundColor Green
