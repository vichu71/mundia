# Mundia backend

Proyecto reservado para el backend Spring Boot.

Responsabilidades previstas:

- API REST para porras, invitaciones, pagos, predicciones, resultados, rankings y premios.
- Autenticacion Google cuando se implemente la integracion real.
- Servicios de scoring y calculo de premios.
- Adaptador API-Football para fixtures, directos, rondas y standings.
- Puerto/adaptador para futura supervision de resultados mediante agente IA.

## Integracion deportiva

Ver [API_FOOTBALL_INTEGRATION.md](./API_FOOTBALL_INTEGRATION.md).

## Persistencia

El esquema de base de datos se diseña en `database/SCHEMA.md`.

Hibernate debe mapear entidades contra ese esquema. Recomendacion:

- Local temprano: `spring.jpa.hibernate.ddl-auto=update` solo si estamos prototipando entidades.
- Desarrollo estable/produccion: `spring.jpa.hibernate.ddl-auto=validate`.
- Migraciones: Flyway o Liquibase.

## Endpoints iniciales

```text
GET  /api/health
GET  /api/admin/sports-sync/status
POST /api/admin/sports-sync/fixtures/live
POST /api/admin/sports-sync/fixtures/today
POST /api/admin/sports-sync/rounds
POST /api/admin/sports-sync/standings
```
