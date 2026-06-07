# Informe de Simulación · Mundia
> Rellena este documento mientras pruebas. Fecha: ___________

---

## Setup inicial

| # | Qué probar | Resultado | Notas |
|---|------------|-----------|-------|
| 1 | Backend arranca sin errores (`docker logs mundia-backend`) | ☐ OK / ☐ FALLO | |
| 2 | Flyway aplica V6 (columna `is_sim` + tabla `sim_state`) | ☐ OK / ☐ FALLO | |
| 3 | Login con Google funciona | ☐ OK / ☐ FALLO | |
| 4 | Login con email/contraseña funciona | ☐ OK / ☐ FALLO | |
| 5 | Email de bienvenida llega al registrarse | ☐ OK / ☐ FALLO | |
| 6 | Email de bienvenida llega al crear porra | ☐ OK / ☐ FALLO | |

---

## Panel Simulador (Admin → al final)

| # | Qué probar | Resultado | Notas |
|---|------------|-----------|-------|
| 7 | Panel simulador visible en Admin | ☐ OK / ☐ FALLO | |
| 8 | Crear 5 usuarios simulados | ☐ OK / ☐ FALLO | |
| 9 | Contador "X activos" se actualiza | ☐ OK / ☐ FALLO | |
| 10 | Los usuarios simulados aparecen en la lista de participantes | ☐ OK / ☐ FALLO | |

---

## Simulación día a día

| Día | Fecha | Partidos | Ranking se actualiza | Grupos se actualizan | Pleno vivo | Notas |
|-----|-------|----------|---------------------|---------------------|------------|-------|
| 1 | 11 jun | 1 | ☐ | ☐ | ☐ | |
| 2 | 12 jun | 3 | ☐ | ☐ | ☐ | |
| 3 | 13 jun | 2 | ☐ | ☐ | ☐ | |
| 4 | 14 jun | 4 | ☐ | ☐ | ☐ | |
| 5 | 15 jun | 6 | ☐ | ☐ | ☐ | |
| 6 | 16 jun | 2 | ☐ | ☐ | ☐ | |
| 7 | 17 jun | 4 | ☐ | ☐ | ☐ | |
| 8 | 18 jun | 5 | ☐ | ☐ | ☐ | |
| 9 | 19 jun | 3 | ☐ | ☐ | ☐ | |
| 10 | 20 jun | 4 | ☐ | ☐ | ☐ | |
| 11 | 21 jun | 6 | ☐ | ☐ | ☐ | |
| 12 | 22 jun | 2 | ☐ | ☐ | ☐ | |
| 13 | 23 jun | 4 | ☐ | ☐ | ☐ | |
| 14 | 24 jun | 6 | ☐ | ☐ | ☐ | |
| 15 | 25 jun | 6 | ☐ | ☐ | ☐ | |
| 16 | 26 jun | 5 | ☐ | ☐ | ☐ | |
| 17 | 27 jun | 5 | ☐ | ☐ | ☐ | |
| 18 | 28 jun | 4 | ☐ | ☐ | ☐ | |

---

## Tab Cuadro

| # | Qué probar | Resultado | Notas |
|---|------------|-----------|-------|
| 19 | Tab "Cuadro" visible en nav | ☐ OK / ☐ FALLO | |
| 20 | Grupos se muestran con resultados reales | ☐ OK / ☐ FALLO | |
| 21 | Ganadores destacados visualmente | ☐ OK / ☐ FALLO | |
| 22 | Sección Eliminatorias aparece (cuando haya datos) | ☐ OK / ☐ FALLO | |

---

## Tab Partidos

| # | Qué probar | Resultado | Notas |
|---|------------|-----------|-------|
| 23 | Grupos colapsables funcionan | ☐ OK / ☐ FALLO | |
| 24 | Clasificación de grupo aparece debajo | ☐ OK / ☐ FALLO | |
| 25 | Partidos con resultado muestran "Cerrado" | ☐ OK / ☐ FALLO | |
| 26 | Botón "Editar predicción" desactivado en cerrados | ☐ OK / ☐ FALLO | |
| 27 | Countdown aparece en partidos próximos | ☐ OK / ☐ FALLO | |

---

## Tab Ranking

| # | Qué probar | Resultado | Notas |
|---|------------|-----------|-------|
| 28 | Ranking muestra puntos reales | ☐ OK / ☐ FALLO | |
| 29 | Posiciones cambian al avanzar días | ☐ OK / ☐ FALLO | |
| 30 | Mi posición destacada en home | ☐ OK / ☐ FALLO | |

---

## Tab Premios

| # | Qué probar | Resultado | Notas |
|---|------------|-----------|-------|
| 31 | Bote se calcula correctamente | ☐ OK / ☐ FALLO | |
| 32 | Reparto por categorías se actualiza | ☐ OK / ☐ FALLO | |
| 33 | Pleno de ganadores: contador de supervivientes | ☐ OK / ☐ FALLO | |

---

## Cierre de predicciones

| # | Qué probar | Resultado | Notas |
|---|------------|-----------|-------|
| 34 | Intentar guardar predicción en partido cerrado → error 409 | ☐ OK / ☐ FALLO | |
| 35 | Botón frontend desactivado antes del cierre | ☐ OK / ☐ FALLO | |

---

## Reset y limpieza

| # | Qué probar | Resultado | Notas |
|---|------------|-----------|-------|
| 36 | Botón "Borrar simulación" pide confirmación | ☐ OK / ☐ FALLO | |
| 37 | Tras reset: usuarios simulados desaparecen | ☐ OK / ☐ FALLO | |
| 38 | Tras reset: resultados de partidos vuelven a NULL | ☐ OK / ☐ FALLO | |
| 39 | Tras reset: ranking vuelve a 0 puntos | ☐ OK / ☐ FALLO | |
| 40 | Se puede crear nueva simulación tras reset | ☐ OK / ☐ FALLO | |

---

## Bugs encontrados

| # | Descripción | Severidad | Estado |
|---|-------------|-----------|--------|
| 1 | | | |
| 2 | | | |
| 3 | | | |

---

## Notas generales

_Escribe aquí cualquier observación sobre el comportamiento general de la app durante la simulación._

```
[espacio libre]
```
