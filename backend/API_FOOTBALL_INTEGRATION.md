# API-Football integration

## Decision

Use API-Football as the external sports data provider for World Cup 2026 fixtures, live scores, rounds and standings.

The frontend must never call API-Football directly. Spring Boot owns the API key, rate limits, cache and reconciliation with local data.

## World Cup 2026 identifiers

```text
league=1
season=2026
```

## External endpoints

```text
GET https://v3.football.api-sports.io/fixtures?live=all&league=1&season=2026
GET https://v3.football.api-sports.io/fixtures?date={yyyy-mm-dd}&league=1&season=2026
GET https://v3.football.api-sports.io/fixtures/rounds?league=1&season=2026
GET https://v3.football.api-sports.io/standings?league=1&season=2026
```

## Authentication modes

Support both modes via configuration:

```yaml
mundia:
  football-api:
    mode: direct # direct | rapidapi
    base-url: https://v3.football.api-sports.io
    api-key: ${API_FOOTBALL_KEY}
    rapidapi-host: api-football-v1.p.rapidapi.com
```

Header rules:

- `direct`: send `x-apisports-key: ${API_FOOTBALL_KEY}`.
- `rapidapi`: send `x-rapidapi-key: ${API_FOOTBALL_KEY}` and `x-rapidapi-host: api-football-v1.p.rapidapi.com`.

## Internal backend endpoints

These are the endpoints Vue should call:

```text
POST /api/admin/sports-sync/fixtures/today
POST /api/admin/sports-sync/fixtures/live
POST /api/admin/sports-sync/rounds
POST /api/admin/sports-sync/standings
GET  /api/admin/sports-sync/status
GET  /api/pools/{poolId}/matches
GET  /api/pools/{poolId}/bracket
```

## Polling strategy

- During live matches: poll live fixtures every 30 seconds.
- On match days without live matches: sync today's fixtures every 10-15 minutes.
- Outside match days: sync rounds/standings manually or hourly at most.
- Cache all external responses needed to compute scores.
- Never let every browser tab poll the external API.

## Data mapping

Relevant external fields:

```json
{
  "fixture": {
    "id": 123456,
    "status": { "short": "1H", "elapsed": 34 }
  },
  "teams": {
    "home": { "id": 9, "name": "Spain" },
    "away": { "id": 21, "name": "France" }
  },
  "goals": { "home": 1, "away": 0 },
  "score": {
    "halftime": { "home": 1, "away": 0 }
  }
}
```

Local fields to persist:

```text
external_fixture_id
external_home_team_id
external_away_team_id
status_short
elapsed
home_goals
away_goals
round_name
kickoff_at
last_synced_at
sync_source
raw_payload_hash
```

## Fixture status mapping

| External | Local meaning |
|---|---|
| NS | Scheduled |
| 1H | Live first half |
| HT | Halftime |
| 2H | Live second half |
| ET | Extra time |
| P | Penalties |
| FT | Finished |

## Plan gratuito — limitaciones conocidas

El plan free de API-Football cubre solo temporadas **2022–2024**. Para la temporada 2026 se requiere plan de pago.

Alternativa gratuita implementada: **worldcup26.ir** (ver sección siguiente).

---

## Fuente alternativa gratuita: worldcup26.ir

Proveedor open-source sin API key que ofrece datos en tiempo real del Mundial 2026.

**Repositorio:** https://github.com/rezarahiminia/worldcup2026  
**Documentación:** https://worldcup26.ir/api-docs/

### Endpoints usados

```text
GET https://worldcup26.ir/get/teams    → 48 selecciones con iso2 para flag-icons
GET https://worldcup26.ir/get/games    → 104 partidos con marcador en tiempo real
GET https://worldcup26.ir/get/groups   → clasificacion por grupos A-L
```

### Implementacion en backend

| Clase | Responsabilidad |
|---|---|
| `WorldCup26Client` | RestClient sin auth hacia worldcup26.ir |
| `WorldCup26SyncService` | Parsea JSON, upserta teams/matches/rounds, registra en sports_sync_runs |
| `SyncSourceConfig` | Bean singleton que guarda la fuente activa: `WC26_IR` o `API_FOOTBALL` |

### Endpoints internos nuevos

```text
POST /api/admin/sports-sync/wc26/teams     → upserta 48 equipos
POST /api/admin/sports-sync/wc26/fixtures  → upserta 104 partidos
POST /api/admin/sports-sync/wc26/groups    → snapshot de clasificacion de grupos
POST /api/admin/sports-sync/wc26/all       → los tres en un solo call
POST /api/admin/sports-sync/source         → cambia fuente activa { "source": "WC26_IR" | "API_FOOTBALL" }
```

### Mapeo de campos

| Campo worldcup26 | Campo DB |
|---|---|
| `id` (negativo) | `external_fixture_id` |
| `home_team_id` / `away_team_id` | resueltos via `external_team_id` negativo |
| `home_score` / `away_score` | `home_goals` / `away_goals` |
| `finished` + `time_elapsed` | `status` / `status_short` |
| `local_date` (MM/dd/yyyy HH:mm) | `kickoff_at` (UTC-5 aproximado) |
| `type` + `group` | `rounds.name` (ej: "Group A", "Final") |
| `iso2` | `teams.country_code` (compatible con flag-icons) |
| proveedor | `result_source = 'WC26_IR'` |

### IDs negativos — convension anti-colision

Los IDs externos de worldcup26 se almacenan como negativos (`-id`) en `external_team_id` y `external_fixture_id` para evitar colision con los IDs positivos que usa API-Football.

### Fuente activa

`DashboardService` lee `SyncSourceConfig.getActive()` y filtra `matches.result_source` en consecuencia. El toggle en el panel Admin llama a `POST /api/admin/sports-sync/source` y recarga el dashboard.

---

## Manual override

Manual admin result entry remains available and wins over provider data when explicitly confirmed.

When a manual override exists:

- Keep the external payload for audit.
- Mark result source as `MANUAL`.
- Recalculate scoring and prizes.
- Show the override in the admin audit trail.
