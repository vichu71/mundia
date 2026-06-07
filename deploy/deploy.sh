#!/bin/bash
# deploy.sh — Despliegue de Mundia en producción
# Ejecutar desde el directorio raíz del proyecto en el servidor IONOS
set -e

echo "🚀 Desplegando Mundia..."

# 1. Compilar y levantar contenedores
docker compose -f docker-compose.prod.yml --env-file .env.prod pull --ignore-pull-failures 2>/dev/null || true
docker compose -f docker-compose.prod.yml --env-file .env.prod build --no-cache
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d

echo "✅ Contenedores levantados"
docker compose -f docker-compose.prod.yml ps
