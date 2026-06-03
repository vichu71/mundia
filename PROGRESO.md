# Estado del proyecto — 2026-06-03

## ✅ Completado en esta sesión

### Backend
- **`SportsSyncService`** — sync real contra API-Football (fixtures, equipos, rondas). Upsert a DB.
- **`WorldCup26Client` + `WorldCup26SyncService`** — integración con https://worldcup26.ir (gratuito, sin key, datos reales 2026). IDs negativos para evitar colisiones.
- **`SyncSourceConfig`** — singleton en memoria que guarda la fuente activa (`WC26_IR` | `API_FOOTBALL`).
- **`SportsSyncController`** — endpoints de sync para ambas fuentes + `POST /source` para cambiar la fuente activa.
- **`DashboardService`** — filtrado por fuente activa, excluye pools DRAFT, ordena partidos (grupos primero A→L, luego rondas KO en orden).
- **`AiPredictionService`** — predicción via GPT-4o-mini con fallback aleatorio (máx 3-3, distribución realista).
- **`PredictionController`** — `POST /predictions/ai/{matchId}` (individual) + `POST /predictions/bulk-random` (todos sin resultado).
- **`application.yml`** — añadida config `openai.api-key`.
- **`docker-compose.yml`** — variables `OPENAI_API_KEY`, `API_FOOTBALL_SEASON`, `API_FOOTBALL_LEAGUE`.
- **`.env`** — `OPENAI_API_KEY=` (vacío, rellenar para activar ChatGPT), `API_FOOTBALL_SEASON=2026`.

### Frontend
- Pantalla inicio reorganizada: grupos (A→L) en grid, luego rondas KO en orden.
- Eliminada "La porra de los primos" (pools DRAFT ocultos).
- "Siguiente que mueve el bote" → **"Próximo partido"**.
- **Modal de predicción** por partido: stepper +/-, botón IA (llama a GPT), guardar.
- **Toolbar en Partidos**: contador + botón "Predecir todos al azar" (bulk random con animación flash).
- **Toggle de fuente** en Admin: worldcup26.ir / API-Football.
- Kickoff formateado en español (hora Madrid).

### DB
- Sort_order de rondas corregido (Grupos 1-12, Round of 32=15, R16=20, QF=30, SF=40, 3rd=50, Final=60).
- Round of 32 insertado.
- Stages KO marcados correctamente.

---

## 🐛 Fix aplicado al final de sesión
- **Animejs ghost en Vite cache**: borrado `/app/node_modules/.vite` dentro del contenedor `mundia-frontend` + restart. La app arranca limpia sin el error `animejs.js does not provide an export named 'default'`.
- **Type mismatch** en mocks estáticos de `matches`: añadidos `kickoff: null, source: null`.

---

## ⚠️ Pendiente / Para mañana

### 1. Probar el flujo completo en el navegador
- Ir a la pestaña **Partidos** y verificar que aparece el botón "Predecir todos al azar".
- Hacer click en un partido → comprobar modal de predicción.
- Botón IA en el modal (funciona con random si `OPENAI_API_KEY` está vacío).

### 2. Activar ChatGPT real (opcional)
- Rellenar `OPENAI_API_KEY=sk-...` en el fichero `.env`.
- `docker compose up -d --force-recreate backend` para que lo pille.
- El botón IA del modal pasará a llamar a GPT-4o-mini.

### 3. Cosas menores que pueden surgir
- El badge "IA" en el modal muestra la fuente (`gpt-4o-mini` / `random`). Revisar que se ve bien.
- La animación flash WAAPI en bulk puede ajustarse si es demasiado sutil o intensa.
- Internacionalización de nombres de ronda (actualmente en inglés, vienen de la API).

### 4. Posibles mejoras futuras (no urgente)
- Persistir predicciones en DB (ahora solo son en memoria/local).
- Página de ranking real conectada a puntuaciones calculadas.
- Notificaciones push cuando empieza un partido (hay un `PushNotification` tool disponible).
- Tests de integración.

---

## Arquitectura rápida

```
frontend (Vue 3 + Vite)  :5173
    ↕ /api/*
backend (Spring Boot 4)  :8080
    ↕ JDBC
database (MariaDB 11)    :3307
```

- **Datos reales**: worldcup26.ir (fuente activa por defecto)
- **Datos backup**: API-Football (necesita plan de pago para temporada 2026)
- **IA**: OpenAI GPT-4o-mini (necesita key) → fallback random automático
- **Flags**: `flag-icons` CSS library (`fi fi-{iso2}`)
- **Animaciones**: Web Animations API (WAAPI) — sin librerías externas

---

## Comandos útiles

```bash
# Ver logs en vivo
docker compose logs -f backend
docker compose logs -f frontend

# Reconstruir backend tras cambios Java
docker compose up -d --build backend

# Limpiar cache Vite si vuelve el fantasma de animejs
docker exec mundia-frontend rm -rf /app/node_modules/.vite
docker compose restart frontend

# Sync manual de worldcup26.ir (desde Postman o curl)
curl -X POST http://localhost:8080/api/admin/sports-sync/wc26/all

# Cambiar fuente activa
curl -X POST http://localhost:8080/api/admin/sports-sync/source \
  -H "Content-Type: application/json" \
  -d '{"source":"WC26_IR"}'
```
