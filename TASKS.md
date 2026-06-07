# TASKS · Mundia
> Última actualización: 2026-06-06
> Mundial empieza en ~5 días. Prioridad máxima en avisos y cierre de predicciones.

Leyenda: `[x]` Hecho · `[~]` En progreso · `[ ]` Pendiente · `[!]` Urgente pre-mundial

---

## ✅ Completado

### Auth & Usuarios
- [x] Google OAuth login + JWT (30d)
- [x] Login/registro por email+contraseña
- [x] Código de acceso global configurable
- [x] /api/me endpoint
- [x] Onboarding: crear porra al primer login

### Porras & Miembros
- [x] Crear porra desde onboarding
- [x] Selector de porras en topbar
- [x] Admin puede añadir/eliminar participantes
- [x] Confirmar pagos desde panel admin
- [x] Roles ADMIN / PLAYER

### Predicciones
- [x] Modal de predicción con stepper (max 4 goles)
- [x] Guardar predicción en DB (match_predictions)
- [x] Predicción con IA (OpenAI)
- [x] Bulk random predictions
- [x] Predicciones cargadas desde DB al abrir la app

### Partidos & Sync
- [x] Cliente API-Football (SportsSyncService)
- [x] Cliente worldcup26.ir gratuito (WorldCup26SyncService)
- [x] Toggle fuente activa WC26_IR / API_FOOTBALL
- [x] Sync: equipos, partidos, grupos, rondas, standings
- [x] kickoff_at en UTC en DB
- [x] Partidos ordenados por kickoff_at
- [x] Panel admin de sync con botones por job

### Scoring & Premios
- [x] Motor de scoring: WINNER +2, EXACT_RESULT +2, goles +1 por equipo
- [x] Recálculo automático al guardar resultado o sincronizar
- [x] Botón "Recalcular puntos" en admin
- [x] Tabla de premios con porcentajes configurables

### Dashboard & UI
- [x] Home redesign: banner posición/puntos/bote, próximo partido dinámico, pendientes, top 5 ranking
- [x] Tab Partidos: grupos colapsables con clasificación en cabecera
- [x] Clasificación de grupo calculada desde resultados (0 pts si no hay resultados)
- [x] Tab Ranking: podio + tabla general + apuesta inicial
- [x] Tab Premios: pleno, simulador, tabla de reparto
- [x] Tab Admin: participantes, pagos, resultados, scoring, sync
- [x] Topbar mobile-first (brand+user fila 1, pool-switch fila 2)
- [x] Próximo partido sale de datos reales (no hardcodeado)
- [x] Cambiar porra recarga dashboard completo
- [x] Focus-trap + Escape en modales
- [x] Animaciones WAAPI (hero, cards, contadores, barras)

### Base de datos & Migraciones
- [x] Flyway configurado y activo
- [x] V1: schema inicial completo
- [x] V2: demo baseline
- [x] V3: prize rule campeón
- [x] V4: password hash
- [x] V5: fix country_code selecciones británicas (England gb-eng, Scotland gb-sct)

### DevOps
- [x] docker-compose.yml (dev, volúmenes montados, hot reload)
- [x] docker-compose.prod.yml (imágenes construidas)
- [x] Dockerfiles frontend (dev + prod con nginx)
- [x] Dockerfile backend (Maven multi-stage)

---

## 🚨 Urgente — Pre-mundial (esta semana)

### Cierre de predicciones
- [!] Añadir `TZ=UTC` en docker-compose.yml y docker-compose.prod.yml (backend + frontend)
- [!] Frontend: deshabilitar botón "Editar predicción" si `kickoff - 60min < Date.now()`
- [!] Frontend: mostrar countdown "Cierra en Xh Ymin" en card cuando falten < 24h
- [!] Frontend: banner de alerta en home si hay partidos que cierran hoy sin predecir
- [!] Backend: validar en endpoint /predictions/match que kickoff - 60min > now() antes de guardar

### Email de aviso (SendGrid)
- [!] Crear cuenta SendGrid gratuita (100 emails/día)
- [!] Añadir dependencia `spring-boot-starter-mail` al pom.xml
- [!] Configurar SendGrid SMTP en application.yml
- [!] Variables de entorno: SENDGRID_API_KEY, MAIL_FROM
- [!] Servicio NotificationService con método sendMatchReminder()
- [!] Job @Scheduled diario a las 9:00 UTC: buscar partidos de las próximas 24h, mandar email a quien no haya predicho
- [!] Plantilla email: lista de partidos del día con hora de cierre y enlace directo a la app
- [!] Añadir SENDGRID_API_KEY a docker-compose.prod.yml

---

## 📋 Pendiente — Post-mundial o cuando haya tiempo

### Frontend
- [ ] Responsive completo en móvil (cards de partidos, bracket, admin)
- [ ] Extraer componentes Vue reutilizables (MatchCard, RankingRow, etc.)
- [ ] Pulir textos con problemas de codificación/acentos
- [ ] Web Push notifications (service worker) — baja prioridad

### Backend
- [ ] Cache de fixtures/standings en memoria
- [ ] Auditoría de cambios de resultado
- [ ] Override manual de resultado (admin) con registro de auditoría
- [ ] Endpoint para ver historial de predicciones de un usuario

### Producto
- [ ] Decidir si usuarios no pagados pueden predecir pero no optar a premio
- [ ] Decidir reparto definitivo cuando el pleno se extingue
- [ ] Decidir si el bonus de Apuesta Inicial suma siempre o es configurable
- [ ] Tests unitarios de scoring (WINNER, EXACT_RESULT, goles)
- [ ] Tests E2E flujo participante y flujo admin

---

## 🗓 Orden de trabajo recomendado hoy/mañana

1. `TZ=UTC` en composes → deploy
2. Countdown + cierre client-side frontend → deploy
3. Banner alerta home → deploy
4. Backend validación 60min en endpoint predicciones → deploy
5. Cuenta SendGrid + integración Spring Boot → deploy
6. Job diario email aviso → deploy y probar
