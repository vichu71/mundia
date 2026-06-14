#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
  echo "Uso: ./version.sh <VERSION>  (ej: 1.0.3)"
  exit 1
fi

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Formato invalido. Usa: MAJOR.MINOR.PATCH (ej: 1.0.3)"
  exit 1
fi

echo "Aplicando version $VERSION..."

# pom.xml — solo la version del proyecto (linea tras <artifactId>backend</artifactId>)
sed -i '/<artifactId>backend<\/artifactId>/{
  n
  s/<version>[0-9][0-9.]*<\/version>/<version>'"$VERSION"'<\/version>/
}' backend/pom.xml
echo "  pom.xml actualizado"

# package.json
sed -i 's/"version": "[0-9][0-9.]*"/"version": "'"$VERSION"'"/' frontend/package.json
echo "  package.json actualizado"

# git — incluir todos los cambios pendientes + los archivos de versión
git add -A
git commit -m "chore: bump version to $VERSION"
git tag -a "v$VERSION" -m "Release $VERSION"
git push
git push origin "v$VERSION"

echo "Listo! Version $VERSION publicada."
