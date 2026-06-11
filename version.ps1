# Mundia Version Automation Script
# Uso: .\version.ps1 -Version "1.0.1" o .\version.ps1 (pedirá versión interactivamente)

param(
    [string]$Version
)

$ErrorActionPreference = "Stop"

# Colores para output
$GREEN = @{ ForegroundColor = "Green" }
$YELLOW = @{ ForegroundColor = "Yellow" }
$RED = @{ ForegroundColor = "Red" }

Write-Host "🚀 Mundia Version Manager" @GREEN
Write-Host ""

# Si no se proporciona versión, pedirla
if (-not $Version) {
    $Version = Read-Host "📌 Ingresa la nueva versión (ej: 1.0.1)"
}

# Validar formato semver básico
if ($Version -notmatch "^\d+\.\d+\.\d+(-[a-zA-Z0-9.]+)?$") {
    Write-Host "❌ Formato de versión inválido. Usa: MAJOR.MINOR.PATCH (ej: 1.0.1)" @RED
    exit 1
}

Write-Host "📦 Versión a aplicar: $Version" @YELLOW

# Obtener rutas
$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
$POM_FILE = Join-Path $ROOT "backend\pom.xml"
$PKG_FILE = Join-Path $ROOT "frontend\package.json"

# Verificar que existan los archivos
if (-not (Test-Path $POM_FILE)) {
    Write-Host "❌ No encontrado: $POM_FILE" @RED
    exit 1
}
if (-not (Test-Path $PKG_FILE)) {
    Write-Host "❌ No encontrado: $PKG_FILE" @RED
    exit 1
}

Write-Host ""
Write-Host "📝 Actualizando archivos..." @GREEN

# 1. Actualizar pom.xml
$pomContent = Get-Content $POM_FILE -Raw
$pomContent = $pomContent -replace '<version>[0-9.]+(-[a-zA-Z0-9.]+)?</version>', "<version>$Version</version>" -replace '<version>[0-9.]+(-[a-zA-Z0-9.]+)?</version>', "<version>$Version</version>"
$pomContent | Set-Content $POM_FILE -Encoding UTF8
Write-Host "   ✓ pom.xml actualizado" @GREEN

# 2. Actualizar package.json
$pkgContent = Get-Content $PKG_FILE | ConvertFrom-Json
$pkgContent.version = $Version
$pkgContent | ConvertTo-Json -Depth 10 | Set-Content $PKG_FILE -Encoding UTF8
Write-Host "   ✓ package.json actualizado" @GREEN

Write-Host ""
Write-Host "🔧 Procesando Git..." @GREEN

# 3. Git add
git add backend/pom.xml frontend/package.json
Write-Host "   ✓ Archivos añadidos al staging" @GREEN

# 4. Git commit
$commitMsg = "chore: version bump to $Version"
git commit -m $commitMsg
Write-Host "   ✓ Commit creado: $commitMsg" @GREEN

# 5. Git tag
$tagMsg = "Release version $Version"
git tag -a "v$Version" -m $tagMsg
Write-Host "   ✓ Tag creado: v$Version" @GREEN

# 6. Git push
Write-Host ""
Write-Host "📤 Haciendo push..." @GREEN
git push
git push origin "v$Version"
Write-Host "   ✓ Push completado" @GREEN

Write-Host ""
Write-Host "✅ ¡Versionado completado!" @GREEN
Write-Host "   - Nueva versión: $Version" @GREEN
Write-Host "   - Cambios pusheados a remote" @GREEN
Write-Host "   - Tag v$Version creado" @GREEN
Write-Host ""
Write-Host "🌐 El frontend mostrará automáticamente la versión desde /api/version" @YELLOW
