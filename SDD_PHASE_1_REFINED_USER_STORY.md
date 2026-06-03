# PHASE 1 · Refined User Story

## US-001 · Crear y jugar varias porras familiares del Mundial

### As a admin de una porra familiar
### I want to crear porras privadas del Mundial, invitar amigos, confirmar pagos y gestionar resultados
### So that el grupo pueda competir con predicciones, ranking, premios por categoria y bote calculado automaticamente

### Acceptance criteria

| # | Criteria |
|---|----------|
| AC-1 | Un usuario puede autenticarse con Google/Gmail. |
| AC-2 | Un usuario autenticado puede crear una o varias porras privadas. |
| AC-3 | Cada porra tiene nombre, importe de inscripcion, codigo/enlace de invitacion, reglas de puntuacion y reglas de premios. |
| AC-4 | El admin puede invitar amigos mediante codigo, enlace o email. |
| AC-5 | Un invitado puede aceptar una invitacion y pasar a ser participante de la porra. |
| AC-6 | Un usuario puede pertenecer a varias porras. |
| AC-7 | El admin puede marcar participantes como pagados tras recibir Bizum o metalico fuera de la app. |
| AC-8 | Solo participantes aceptados y marcados como pagados optan a premios. |
| AC-9 | La app calcula el bote de cada porra como participantes pagados x 10 EUR. |
| AC-10 | Un participante puede completar una prediccion inicial completa del Mundial antes del bloqueo. |
| AC-11 | La prediccion inicial queda inmutable tras bloquearse. |
| AC-12 | El admin puede introducir o actualizar resultados reales desde la web/app. |
| AC-13 | El backend puede sincronizar fixtures, rondas, standings y partidos en directo desde API-Football para `league=1` y `season=2026`. |
| AC-14 | La clave de API-Football se guarda solo en Spring Boot y nunca se expone al frontend Vue. |
| AC-15 | El sistema queda preparado para que un futuro agente IA supervise o dispare actualizaciones reales mediante un servicio/adaptador. |
| AC-16 | La app calcula puntos por ganador/signo, resultado exacto, goles acertados, clasificados, rondas y campeon. |
| AC-17 | La regla de partido es: ganador/signo correcto +2, resultado exacto +2 adicionales, goles acertados +1 por equipo. |
| AC-18 | La diferencia de goles no se puntua, no se muestra como categoria y no se usa como desempate. |
| AC-19 | Un resultado erroneo con ganador correcto no suma resultado exacto, pero si suma ganador/signo. |
| AC-20 | La app mantiene prediccion inicial inmutable y prediccion viva editable solo para eventos futuros. |
| AC-21 | La app detecta predicciones imposibles y recomienda cambios futuros. |
| AC-22 | La app no permite modificar partidos o eventos cerrados. |
| AC-23 | La app muestra ranking general y rankings por categoria. |
| AC-24 | El ganador final de la porra normal se decide por puntos acumulados, no por el Premio Perfecto Mundial. |
| AC-25 | El Premio Perfecto Mundial es un premio especial separado basado en acertar todos los ganadores/signos y avances requeridos, no resultados exactos. |
| AC-26 | Si nadie puede lograr el Premio Perfecto Mundial, el 75% reservado se redistribuye automaticamente entre premios por categoria. |
| AC-27 | La app muestra premios activos, premios extinguidos y reparto monetario actual. |
| AC-28 | La app calcula premio actual estimado y premio maximo posible por usuario. |
| AC-29 | La app muestra a que premios sigue optando cada usuario y cuales ya no puede ganar. |
| AC-30 | La app calcula una puntuacion separada de Apuesta Inicial usando solo la prediccion original inmutable. |
| AC-31 | La app puede premiar Mejor Apuesta Inicial como categoria separada y/o bonus configurable para el ranking general. |
| AC-32 | Todos los cambios importantes quedan auditados: pagos, resultados, bloqueos y modificaciones de prediccion viva. |
| AC-33 | La interfaz es responsive, mobile-first y visual, con dashboard funcional como primera pantalla. |
| AC-34 | La app usa Vue en frontend, Spring Boot en backend y MariaDB como base de datos. |

### Edge cases

- Un usuario acepta dos invitaciones a la misma porra.
- Un usuario intenta participar sin estar pagado.
- Un admin intenta cambiar un resultado ya usado para calcular puntuaciones.
- Un participante intenta editar una prediccion inicial bloqueada.
- Un participante intenta editar un partido ya empezado o cerrado.
- Dos o mas usuarios empatan en ranking general o premio por categoria.
- Varios usuarios consiguen el Premio Perfecto Mundial.
- Ningun usuario sigue vivo para el Premio Perfecto Mundial y hay que redistribuir el bote.
- Un usuario no completa la prediccion inicial antes del bloqueo.
- Un invitado rechaza o ignora una invitacion.
- Un codigo de invitacion caduca o no existe.
- El admin corrige un resultado real introducido por error.
- Un usuario pertenece a varias porras con estados de pago distintos.
- Un partido se anula, cambia de fecha o cambia de equipos.
- El futuro agente IA intenta actualizar un resultado duplicado o contradictorio.

### Out of scope (v1)

- Pasarela de pago real.
- Cobro directo por Bizum dentro de la app.
- Agente IA de resultados completamente implementado.
- API deportiva automatica obligatoria.
- Notificaciones push.
- Chat familiar.
- App nativa movil.
- Historico avanzado de torneos pasados.
- Panel publico de marketing o landing page.

### Definition of done

- Existe una app funcional con frontend Vue, backend Spring Boot y MariaDB.
- Un admin puede crear varias porras privadas.
- Los usuarios pueden autenticarse con Google/Gmail.
- El admin puede invitar participantes y confirmar pagos manuales.
- Los participantes pueden completar predicciones iniciales.
- El admin puede actualizar resultados reales desde la web/app.
- El sistema calcula puntuaciones segun las reglas confirmadas.
- El sistema calcula ranking general, ranking de apuesta inicial, categorias, bote, premios y premio maximo posible.
- El Premio Perfecto Mundial y su redistribucion funcionan segun reglas.
- La prediccion inicial, prediccion viva y bloqueos respetan reglas anti trampas.
- La UI es responsive, mobile-first y empieza en un dashboard funcional.
- Hay tests para scoring, premios, invitaciones, pagos, permisos y bloqueos.
