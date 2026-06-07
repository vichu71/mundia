# SDD - Simulador de torneo completo

Fecha: 2026-06-06
Estado: especificacion, sin implementar

## Objetivo

Crear un modo de ensayo real para Mundia que permita simular una porra completa antes de produccion:

- usuarios simulados como participantes reales
- pagos confirmados
- fase de grupos
- generacion de eliminatorias
- campeon
- ranking final
- reparto del bote

El simulador actual solo cubre fase de grupos. Este SDD define lo que falta sin cambiar el codigo actual.

## Principios

- El flujo real de la app no debe romperse por datos simulados.
- Los datos simulados deben estar claramente marcados y poder borrarse.
- La fuente real futura (`WC26_IR` o API-Football) debe poder convivir o sustituir a la simulacion.
- La tab Partidos debe seguir mostrando los partidos reales/importados como hasta ahora.
- El modo simulador es una herramienta admin/dev, no parte del flujo normal del jugador.

## Alcance funcional

### Usuarios simulados

Al crear usuarios simulados:

- crear `users.is_sim = TRUE`
- crear `pool_members` activos como `PLAYER`
- crear predicciones `LIVE`
- crear predicciones `INITIAL`
- crear pago confirmado por `pools.entry_fee_cents`
- evitar duplicados si se pulsa varias veces

Pago simulado propuesto:

- `payments.method = 'SIM'`
- `payments.status = 'CONFIRMED'`
- `payments.notes = 'Pago simulado'`
- `payments.confirmed_at = CURRENT_TIMESTAMP`

El reset debe borrar estos pagos antes de borrar los `pool_members`.

### Fase de grupos

La fase de grupos sigue usando los 72 partidos existentes.

El simulador debe:

- avanzar dia a dia por `kickoff_at`
- asignar resultados aleatorios
- recalcular scoring tras cada dia
- calcular clasificaciones de grupo al terminar

Clasifican:

- 1 y 2 de cada grupo: 24 equipos
- 8 mejores terceros: 8 equipos
- total: 32 equipos

Ordenacion minima para clasificar:

1. puntos
2. diferencia de goles
3. goles a favor
4. nombre del equipo, como desempate deterministicamente estable para simulacion

No hace falta implementar todos los criterios FIFA para el primer ensayo, pero debe quedar documentado si se simplifica.

## Arbol de enfrentamientos tras fase de grupos

El Mundial 2026 tiene Round of 32. El bracket base debe seguir estos slots.

### Round of 32

| Partido | Cruce |
|---------|-------|
| M73 | 2A vs 2B |
| M74 | 1E vs 3 ABCDF |
| M75 | 1F vs 2C |
| M76 | 1C vs 2F |
| M77 | 1I vs 3 CDFGH |
| M78 | 2E vs 2I |
| M79 | 1A vs 3 CEFHI |
| M80 | 1L vs 3 EHIJK |
| M81 | 1D vs 3 BEFIJ |
| M82 | 1G vs 3 AEHIJ |
| M83 | 2K vs 2L |
| M84 | 1H vs 2J |
| M85 | 1B vs 3 EFGIJ |
| M86 | 1J vs 2H |
| M87 | 1K vs 3 DEIJL |
| M88 | 2D vs 2G |

Nota sobre terceros:

Los slots `3 ABCDF`, `3 CEFHI`, etc. dependen de que combinacion de terceros clasifique. Para una primera version de simulador se puede resolver con una tabla determinista simplificada:

- obtener los 8 mejores terceros
- asignarlos en orden a los slots de tercero disponibles
- no repetir grupo si el slot no permite ese grupo
- si hay conflicto, probar el siguiente tercero disponible

Para una version fiel al torneo real, se debe implementar la tabla oficial de asignacion de mejores terceros.

### Round of 16

| Partido | Cruce |
|---------|-------|
| M89 | W74 vs W77 |
| M90 | W73 vs W75 |
| M91 | W76 vs W78 |
| M92 | W79 vs W80 |
| M93 | W83 vs W84 |
| M94 | W81 vs W82 |
| M95 | W86 vs W88 |
| M96 | W85 vs W87 |

### Cuartos

| Partido | Cruce |
|---------|-------|
| M97 | W89 vs W90 |
| M98 | W93 vs W94 |
| M99 | W91 vs W92 |
| M100 | W95 vs W96 |

### Semifinales

| Partido | Cruce |
|---------|-------|
| M101 | W97 vs W98 |
| M102 | W99 vs W100 |

### Tercer puesto

| Partido | Cruce |
|---------|-------|
| M103 | L101 vs L102 |

### Final

| Partido | Cruce |
|---------|-------|
| M104 | W101 vs W102 |

## Fechas simuladas

Para que el simulador avance por dias, los partidos generados deben tener `kickoff_at`.

Fechas aproximadas:

- Round of 32: 2026-06-29 a 2026-07-03
- Round of 16: 2026-07-04 a 2026-07-07
- Cuartos: 2026-07-09 a 2026-07-11
- Semifinales: 2026-07-14 y 2026-07-15
- Tercer puesto: 2026-07-18
- Final: 2026-07-19

## Modelo de datos propuesto

### Opcion A: extender `matches`

Agregar:

- `matches.winner_team_id BIGINT NULL`

Uso:

- En fase de grupos puede ser `NULL`.
- En eliminatorias guarda el equipo que pasa.
- Si el marcador no es empate, coincide con el ganador por goles.
- Si el marcador es empate, representa ganador por penaltis/prorroga.

Ventajas:

- simple
- facil de consultar desde scoring y bracket

Riesgos:

- requiere migracion
- hay que adaptar admin/manual result para KO

### Opcion B: tabla separada para resultado KO

Crear `match_knockout_results`:

- `match_id`
- `winner_team_id`
- `resolution`: `REGULAR`, `EXTRA_TIME`, `PENALTIES`, `SIM`

Ventajas:

- separa marcador de clasificado
- mas expresivo

Riesgos:

- mas joins
- mas codigo

Recomendacion inicial: Opcion A.

## Predicciones de eliminatorias

En eliminatorias, el usuario debe predecir:

- goles local
- goles visitante
- equipo clasificado

Reglas UI:

- Si el marcador predicho no es empate, autoseleccionar clasificado segun el marcador.
- Si el marcador predicho es empate, obligar a elegir clasificado.
- Permitir cambiar el clasificado antes del cierre.

`match_predictions.predicted_winner_team_id` ya existe y puede usarse para esto.

## Scoring

Mantener maximo de 6 puntos por partido.

### Fase de grupos

- +2 por acertar ganador o empate
- +2 por resultado exacto
- +1 por goles local
- +1 por goles visitante

### Eliminatorias

- +2 por acertar equipo clasificado
- +2 por resultado exacto de goles
- +1 por goles local
- +1 por goles visitante

Ejemplos:

| Real | Prediccion | Puntos |
|------|------------|--------|
| 1-1, pasa Espana | 1-1, pasa Espana | 6 |
| 1-1, pasa Espana | 1-1, pasa Francia | 4 |
| 2-1, pasa Espana | 1-1, pasa Espana | 2 |
| 1-1, pasa Espana | 2-1, pasa Espana | 2 |

## Generacion de partidos

Al completar grupos:

1. calcular clasificados
2. resolver slots del Round of 32
3. crear partidos de Round of 32
4. generar predicciones para usuarios simulados

Al completar cada ronda:

1. leer `winner_team_id`
2. crear la siguiente ronda
3. generar predicciones para usuarios simulados

No crear todas las rondas desde el principio con equipos placeholder, salvo que se quiera mostrar el bracket completo vacio.

## Fuente de datos

Partidos generados por simulador:

- `matches.result_source = 'SIM'`

La tab Partidos no debe mezclarlos por defecto si eso rompe el flujo actual. Opciones:

- mostrar `WC26_IR` en Partidos y `SIM` solo en Cuadro/Simulador
- o mostrar ambas fuentes solo cuando el modo simulador este activo

Recomendacion:

- mantener Partidos filtrado por fuente activa como ahora
- mostrar eliminatorias simuladas en Cuadro y panel Admin
- no mezclar `SIM` en consultas globales sin revisar UI

## Reset

El reset debe borrar en orden:

1. predicciones de usuarios simulados
2. predicciones asociadas a partidos `SIM`
3. score_breakdowns de usuarios simulados
4. score_breakdowns de partidos simulados
5. pagos de usuarios simulados
6. prediction_sets de usuarios simulados
7. pool_members simulados
8. users simulados
9. partidos `SIM`
10. resultados de partidos `WC26_IR`
11. sim_state

Debe tener especial cuidado con foreign keys.

## Premios

Para validar reparto real hace falta un motor de adjudicacion.

Categorias:

- `PERFECT_WINNERS`
- `GENERAL`
- `INITIAL_BET`
- `EXACT_RESULTS`
- `WINNERS`
- `CHAMPION`

Regla de empate:

- si varios usuarios empatan en una categoria, dividir el importe entre ellos
- si hay centimos sobrantes, asignar el resto por orden estable de `pool_member_id`

Validacion:

- suma de premios adjudicados = bote repartido segun reglas activas
- bote = suma de pagos confirmados

## Botones admin propuestos

- Crear usuarios simulados
- Simular siguiente dia
- Simular hasta fin de grupos
- Generar eliminatorias
- Simular siguiente ronda
- Simular torneo completo
- Reset simulacion

## Tests de aceptacion

- Crear 5 usuarios simulados sube el bote en 50 EUR si entrada = 10 EUR.
- Reset borra usuarios simulados y sus pagos.
- Al acabar grupos hay exactamente 32 clasificados.
- Se crean 16 partidos de Round of 32.
- Un empate KO siempre tiene clasificado.
- Un empate KO no muestra ganador `Empate` en el cuadro.
- Al acabar Round of 32 se crean 8 partidos de Round of 16.
- Al acabar semifinales se crean final y tercer puesto.
- Al acabar final hay campeon.
- El reparto final no supera el bote.
- La tab Partidos no queda vacia por mezclar fuentes `SIM`.

## Fuera de alcance por ahora

- Implementar todos los criterios FIFA oficiales de desempate.
- Implementar tabla oficial completa de mejores terceros.
- Integrar datos reales de eliminatorias antes de que existan en API.
- Redisenar la tab Partidos.

