# Simulador de Torneo · Mundia
> Última actualización: 2026-06-06

---

## ¿Qué hace ahora?

El simulador permite probar la app como si el Mundial estuviera en curso, día a día, sin esperar a que empiecen los partidos reales.

### Flujo actual

1. **Crear usuarios simulados** — genera N usuarios fake (Pepito, Manolita, Curro...) con predicciones LIVE + INITIAL aleatorias para los 72 partidos
2. **Avanzar día** — mete resultados aleatorios en todos los partidos de ese día calendario y recalcula el scoring
3. **Reset** — borra usuarios simulados, sus predicciones, sus puntos y los resultados de todos los partidos

### Lo que funciona ✅
- Crear 3/5/8/10 usuarios simulados con predicciones aleatorias en LIVE e INITIAL
- Avanzar día a día (18 días de fase de grupos disponibles, 11 jun → 28 jun)
- Barra de progreso con chips de días (verde=completado, dorado=siguiente)
- Scoring recalculado tras cada día (WINNER+2, EXACT_RESULT+2, HOME_GOALS+1, AWAY_GOALS+1)
- Reset completo respetando foreign keys (orden: match_predictions → score_breakdowns → prediction_sets → pool_members → users)
- Los usuarios reales coexisten con los simulados — el reset solo borra los simulados

### Endpoints REST
```
GET    /api/admin/sim/status/{poolId}   — estado actual (día, usuarios, timeline)
POST   /api/admin/sim/users/{poolId}    — crear usuarios simulados { count: N }
POST   /api/admin/sim/advance/{poolId}  — simular siguiente día
DELETE /api/admin/sim/reset/{poolId}    — borrar toda la simulación
```

---

## Problemas conocidos / pendientes

### 🔴 Ensayo real incompleto
El simulador actual sirve para probar fase de grupos y scoring por partidos, pero todavía no cubre un caso real completo de porra:
- Los usuarios simulados no se marcan como pagados
- No se genera el bracket completo de eliminatorias
- No se simula campeón
- No se adjudican premios finales por categoría
- No se puede validar el reparto real del bote de principio a fin

### 🔴 No hay eliminatorias
La DB solo tiene los 72 partidos de fase de grupos (worldcup26.ir aún no tiene los cruces de octavos).
Cuando lleguen del API, habrá que:
- Generar predicciones aleatorias para esos partidos también al crear usuarios simulados
- El simulador avanzará automáticamente esos días porque busca por fecha

### 🟡 El cuadro no muestra eliminatorias en simulación
La tab "Cuadro" solo tiene datos de fase de grupos porque no hay partidos de eliminatorias en la DB.
Se llenará sola cuando worldcup26.ir publique los cruces y se haga sync.

### 🟡 Apuesta inicial en simulación
Los usuarios simulados SÍ tienen predicciones INITIAL generadas.
Pero el ranking de "Apuesta inicial" en la tab Ranking mostrará 0 puntos hasta que haya resultados — es correcto, se irá llenando conforme se avancen días.

### 🟡 Puntuación de usuarios reales con días simulados
Los resultados simulados afectan también a los usuarios reales de la porra.
Al hacer reset, los resultados vuelven a NULL y los puntos a 0 para todos.
**Cuidado:** si hay usuarios reales con predicciones reales, el reset borra sus puntos también (aunque no sus predicciones).

### 🟡 Eliminatorias empatadas
En fase de grupos el resultado `1-1` es suficiente porque el empate es un resultado válido.
En eliminatorias no basta: puede haber `1-1` y que un equipo pase por penaltis.

Decisión propuesta:
- Permitir empate en goles en eliminatorias
- Añadir `predicted_winner_team_id` / clasificado para partidos KNOCKOUT
- En resultados reales de eliminatoria, guardar también el equipo clasificado si el marcador queda empatado
- Mantener el máximo de 6 puntos por partido:
  - +2 por acertar ganador o, en eliminatorias, equipo clasificado
  - +2 por acertar resultado exacto de goles
  - +1 por goles del local
  - +1 por goles del visitante

Ejemplos:
- Real `1-1`, pasa España. Predicción `1-1`, pasa España → 6 puntos
- Real `1-1`, pasa España. Predicción `1-1`, pasa Francia → 4 puntos
- Real `2-1`, pasa España. Predicción `1-1`, pasa España → 2 puntos
- Real `1-1`, pasa España. Predicción `2-1`, pasa España → 2 puntos

### 🟡 Progreso del simulador no se resetea visualmente
Tras el reset, el frontend a veces no refresca el panel hasta que se recarga la página.
Workaround: hard refresh (Ctrl+Shift+R) tras el reset.

---

## Plan de implementación: simulador de torneo completo

Objetivo: poder hacer un ensayo real de la porra antes de producción, desde crear participantes hasta simular la final y comprobar bote, ranking y reparto.

### 1. Usuarios simulados como participantes reales
- [ ] Al crear usuarios simulados, crear también un pago confirmado por el importe de `pools.entry_fee_cents`
- [ ] Usar método `SIM` o `BIZUM` con nota clara: `Pago simulado`
- [ ] Evitar pagos duplicados si se vuelve a ejecutar la creación de usuarios
- [ ] Confirmar que el contador `pagados` y el bote suben inmediatamente
- [ ] Ampliar reset para borrar pagos de usuarios simulados antes de borrar `pool_members`

### 2. Bracket de eliminatorias simulado
- [ ] Al terminar la fase de grupos, calcular clasificación de los 12 grupos
- [ ] Clasificar top 2 de cada grupo + 8 mejores terceros
- [ ] Crear 16 partidos de `Round of 32`
- [ ] Crear después, ronda a ronda, los partidos de:
  - `Round of 16`
  - `Quarter-finals`
  - `Semi-finals`
  - `3rd Place Final`
  - `Final`
- [ ] Usar fechas oficiales aproximadas del calendario 2026 para que el simulador pueda seguir avanzando por días
- [ ] Marcar los partidos generados con `result_source = 'SIM'` o una fuente equivalente para distinguirlos de API real
- [ ] No bloquear que una sync real futura de worldcup26/API-Football pueda reemplazar o convivir con datos reales

### 3. Predicciones para partidos generados
- [ ] Cuando se cree una ronda nueva, generar predicciones LIVE + INITIAL para todos los usuarios simulados
- [ ] Para usuarios reales, dejar predicción pendiente salvo que exista una lógica explícita de autocompletado de prueba
- [ ] En eliminatorias, guardar también el equipo clasificado predicho
- [ ] Añadir UI de predicción KNOCKOUT: marcador + selector de clasificado
- [ ] Si el marcador predicho no es empate, autoseleccionar clasificado según el marcador y permitir revisión

### 4. Resultados de eliminatorias
- [ ] Simular resultados sin dejar la eliminatoria sin ganador
- [ ] Permitir marcador empatado y elegir clasificado por penaltis de forma aleatoria
- [ ] Guardar el clasificado real de la eliminatoria
- [ ] Usar el clasificado para construir la siguiente ronda
- [ ] Evitar que el cuadro muestre `Empate` como ganador en KNOCKOUT

### 5. Scoring KNOCKOUT
- [ ] Adaptar `ScoringService` para distinguir GROUP_STAGE y KNOCKOUT
- [ ] En grupos, mantener lógica actual: +2 por signo correcto, incluyendo empate
- [ ] En eliminatorias, +2 por equipo clasificado correcto
- [ ] Mantener +2 exacto, +1 goles local, +1 goles visitante
- [ ] Cubrir con tests casos de empate + penaltis

### 6. Premios y reparto real
- [ ] Definir motor de adjudicación de premios al menos al final del torneo
- [ ] Calcular ganadores por categoría:
  - General
  - Mejor apuesta inicial
  - Más exactos
  - Más ganadores/clasificados
  - Campeón acertado
  - Pleno de ganadores, si aplica
- [ ] Decidir reparto en caso de empate dentro de una categoría
- [ ] Rellenar o reemplazar `prize_projections` con importes actuales/finales
- [ ] Mostrar en la tab Premios candidatos/ganadores e importe asignado
- [ ] Validar que la suma repartida coincide con el bote confirmado

### 7. Admin y UX de prueba
- [ ] Añadir botón `Simular torneo completo`
- [ ] Añadir botón `Simular hasta eliminatorias`
- [ ] Mostrar ronda/día actual con total de partidos simulados
- [ ] Mostrar aviso claro cuando se creen partidos `SIM`
- [ ] Refrescar dashboard y panel de simulador tras reset sin hard refresh

### 8. Tests mínimos
- [ ] Crear usuarios simulados → aparecen como pagados y sube el bote
- [ ] Reset → borra usuarios, pagos simulados, predicciones, resultados y partidos SIM si procede
- [ ] Fase de grupos completa → genera 32 clasificados
- [ ] Round of 32 completa → genera Round of 16
- [ ] Final completa → hay campeón
- [ ] Empate en eliminatoria → puntúa exacto y clasificado por separado
- [ ] Reparto final → importes por categoría suman el bote esperado

---

## Cómo probar (paso a paso)

```
1. Admin → scroll abajo → panel "Simulador de torneo"
2. Seleccionar 5 usuarios → "Crear usuarios"
3. Verificar que aparecen en Admin → Participantes
4. Pulsar "▶ Simular día 1" (11 jun, 1 partido)
5. Ir a Ranking → verificar que hay puntos
6. Ir a Partidos → verificar que el partido del día 1 aparece cerrado
7. Volver a Admin → Simular día 2 (12 jun, 3 partidos)
8. Repetir hasta día 5-6 para ver el ranking moverse
9. Verificar tab Cuadro → clasificaciones de grupo actualizadas
10. Borrar simulación → verificar que todo vuelve a 0
```

---

## Archivos clave

| Archivo | Descripción |
|---------|-------------|
| `backend/.../simulator/SimulatorService.java` | Lógica completa: crear usuarios, avanzar día, reset |
| `backend/.../simulator/SimulatorController.java` | Endpoints REST |
| `backend/.../db/migration/V6__simulator.sql` | `is_sim` en users + tabla `sim_state` |
| `frontend/src/App.vue` | Panel simulador en tab Admin (buscar `sim-panel`) |
| `frontend/src/style.css` | Estilos `.sim-*` |

---

## Mejoras futuras

- [ ] Botón "Simular todos los días de una vez" para pruebas rápidas
- [ ] Modo "Simular con resultados reales históricos" (usar datos de mundiales anteriores)
- [ ] Mostrar en el panel qué equipos clasificaron de cada grupo simulado
- [ ] Generar cruces de eliminatorias automáticamente al terminar fase de grupos
