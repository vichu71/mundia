# TASKS · Mundia

Estado de progreso operativo para construir la app siguiendo el SDD.

Leyenda:

- `[x]` Hecho
- `[~]` En progreso
- `[ ]` Pendiente

## Fase 0 · Decisiones Base

- [x] Confirmar stack: Vue + Spring Boot + MariaDB.
- [x] Confirmar estructura en tres proyectos: `frontend`, `backend`, `database`.
- [x] Confirmar pagos manuales por Bizum o metalico.
- [x] Confirmar que el admin marca participantes como pagados.
- [x] Confirmar varias porras/apuestas por usuario.
- [x] Confirmar invitaciones por codigo/enlace/email con aceptacion.
- [x] Confirmar carga manual de resultados como fallback.
- [x] Confirmar API-Football como proveedor automatizable de fixtures/resultados.
- [x] Confirmar que la API key vive solo en Spring Boot.
- [x] Definir proyecto dockerizado con `docker-compose.yml`.

## Fase 1 · SDD y Producto

- [x] Crear `FEATURE.md`.
- [x] Crear user story refinada en `SDD_PHASE_1_REFINED_USER_STORY.md`.
- [x] Documentar reglas de puntuacion.
- [x] Documentar Premio Perfecto Mundial.
- [x] Documentar Apuesta Inicial y bonus opcional.
- [x] Documentar cruces clasicos en pantalla inicial.
- [x] Documentar integracion API-Football.
- [ ] Crear artefactos SDD fase 2: stack detallado, modelo, endpoints, componentes, estructura, wireframe, ADR y DDD.

## Fase 2 · Frontend Prototipo

- [x] Crear proyecto Vue + TypeScript + Vite en `frontend`.
- [x] Instalar dependencias del front.
- [x] Crear Dockerfile dev del frontend.
- [x] Crear pantalla inicial visual mobile-first.
- [x] Crear selector de porras.
- [x] Crear resumen de bote, premio posible y estado de pleno.
- [x] Crear navegacion inferior: Inicio, Partidos, Ranking, Premios, Admin.
- [x] Crear vista de partidos mockeada.
- [x] Crear ranking general mockeado.
- [x] Crear ranking de apuesta inicial mockeado.
- [x] Crear pantalla de premios mockeada.
- [x] Crear panel admin mockeado.
- [x] Crear esquema clasico de cruces con partidos/resultados.
- [x] Crear panel admin de sincronizacion API-Football.
- [x] Panel admin de sincronizacion worldcup26.ir con boton "Sync todo WC26".
- [x] Toggle de fuente activa en panel Admin (worldcup26.ir / API-Football).
- [x] Tarjeta de partido muestra fecha/hora real formateada en espanol.
- [x] Partidos ordenados cronologicamente por kickoff_at.
- [x] Partidos de demo (result_source=NONE) excluidos de la vista.
- [x] Revisar ajustes visuales recientes y dejar el build del front en verde.
- [~] Pulir textos con problemas de codificacion/acento en algunos strings.
- [~] Revisar responsive completo en movil y escritorio.
- [ ] Extraer componentes Vue reutilizables.
- [x] Sustituir datos mock por servicios internos cuando exista backend.

## Fase 3 · Backend Spring Boot

- [x] Crear carpeta `backend`.
- [x] Crear `backend/README.md`.
- [x] Crear `backend/API_FOOTBALL_INTEGRATION.md`.
- [x] Incluir backend en `docker-compose.yml` como servicio preparado para Spring Boot.
- [x] Scaffold Spring Boot.
- [x] Configurar perfiles de entorno.
- [x] Configurar MariaDB datasource.
- [ ] Implementar autenticacion Google.
- [ ] Implementar entidades y repositorios.
- [ ] Implementar API de porras.
- [ ] Implementar API de invitaciones.
- [ ] Implementar API de pagos manuales.
- [ ] Implementar API de predicciones.
- [ ] Implementar API de resultados reales.
- [ ] Implementar scoring.
- [ ] Implementar calculo de premios.
- [x] Implementar adaptador API-Football (FootballApiClient + SportsSyncService).
- [x] Crear cliente API-Football base en backend.
- [x] Implementar sync real: fixtures live, fixtures del dia, rondas, standings (upsert en DB, registro en sports_sync_runs).
- [x] Implementar cliente worldcup26.ir (WorldCup26Client + WorldCup26SyncService): 48 equipos, 104 partidos, grupos.
- [x] Implementar selector de fuente activa en memoria (SyncSourceConfig): WC26_IR | API_FOOTBALL.
- [x] Endpoint POST /api/admin/sports-sync/source para cambiar fuente desde el frontend.
- [x] Dashboard filtra partidos por fuente activa y los devuelve ordenados por kickoff_at.
- [x] kickoff_at expuesto en MatchDto y formateado en la tarjeta de partido del frontend.
- [ ] Implementar cache de fixtures/standings.
- [x] Implementar endpoint de estado de sincronizacion.
- [ ] Implementar auditoria.

## Fase 4 · Database MariaDB

- [x] Crear carpeta `database`.
- [x] Crear `database/README.md`.
- [x] Crear borrador de esquema relacional en `database/SCHEMA.md`.
- [x] Decidir estrategia: migraciones primero, Hibernate `validate` en entornos compartidos.
- [x] Crear `docker-compose.yml` con MariaDB.
- [x] Crear `.env.example`.
- [x] Crear `DOCKER.md`.
- [x] Crear migraciones iniciales.
- [x] Crear tablas de usuarios, porras, miembros e invitaciones.
- [x] Crear tablas de pagos.
- [x] Crear tablas de equipos, partidos, resultados y rondas.
- [x] Crear tablas de prediccion inicial y prediccion viva.
- [x] Crear tablas de puntuacion y premios.
- [x] Crear tablas de sincronizacion API-Football.
- [x] Crear seed de datos de ejemplo.
- [x] Verificar Flyway en Docker cuando Docker Desktop este arrancado.

## Fase 5 · Integracion Frontend-Backend

- [x] Definir contrato REST inicial.
- [x] Crear cliente API en Vue.
- [x] Conectar selector de porras.
- [x] Conectar dashboard.
- [x] Conectar partidos.
- [x] Conectar ranking.
- [x] Conectar premios.
- [ ] Conectar admin de pagos.
- [ ] Conectar admin de resultados.
- [x] Conectar estado de API-Football.

## Fase 6 · Tests y Verificacion

- [x] Verificar build del frontend con `npm run build`.
- [x] Verificar tests del backend con `mvnw test`.
- [x] Verificar sintaxis de Docker Compose con `docker compose config`.
- [x] Verificar `docker compose up -d database backend`.
- [ ] Tests unitarios de scoring.
- [ ] Tests de resultado exacto, ganador/signo y goles.
- [ ] Tests de apuesta inicial.
- [ ] Tests de premio perfecto.
- [ ] Tests de redistribucion de premios.
- [ ] Tests de invitaciones.
- [ ] Tests de pagos manuales.
- [ ] Tests de bloqueo de predicciones.
- [ ] Tests de adaptador API-Football con mocks.
- [ ] E2E basico del flujo admin.
- [ ] E2E basico del flujo participante.

## Fase 7 · Pendientes De Producto

- [ ] Decidir si el bonus de Apuesta Inicial suma siempre o es configurable por porra.
- [ ] Decidir reparto definitivo cuando el pleno se extingue.
- [ ] Decidir si usuarios no pagados pueden rellenar prediccion pero no optar a premio.
- [ ] Decidir formato final de invitacion por email/enlace.
- [ ] Decidir si el admin puede corregir resultados API-Football con override manual.
- [ ] Decidir frecuencia real de polling segun plan contratado.
- [x] Confirmar proveedor: API-Football directo o RapidAPI.
- [x] Confirmar fuente gratuita alternativa: worldcup26.ir (sin key, datos reales 2026).
