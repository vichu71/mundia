# FEATURE · Porra Mundial familiar con bote, premios por categoria y simulador de premio posible

Progreso operativo: ver `TASKS.md`.

## Contexto

Crear una aplicacion web responsive, pensada primero para movil, para organizar una porra familiar del Mundial.

Los usuarios entran con Google/Gmail, pagan una inscripcion de 10 EUR y completan una prediccion del Mundial. La app debe ser muy visual, con estetica de torneo, banderas, partidos, marcadores, ranking, premios vivos y calculos claros de cuanto dinero puede ganar cada persona.

La app no debe ser una landing. La primera pantalla debe ser la experiencia real de la porra.

## Decisiones confirmadas

Stack confirmado:

- Frontend: Vue.
- Backend: Spring Boot.
- Database: MariaDB.
- Entorno: proyecto dockerizado con `docker-compose.yml` raiz que une frontend, backend y database.

Pagos confirmados:

- Pago por Bizum o metalico fuera de la app.
- El admin marca al participante como pagado.
- No implementar pasarela de pago en v1.

Resultados reales confirmados:

- Los resultados reales se actualizan desde la web/app mediante una pantalla o servicio de administracion.
- En v1 debe existir carga/actualizacion manual por admin.
- Usar API-Football como fuente automatizable para calendario, directos, resultados y clasificaciones.
- Fuente gratuita alternativa implementada: **worldcup26.ir** — sin API key, datos reales del Mundial 2026 en tiempo real (48 equipos, 104 partidos, grupos). El plan free de API-Football no cubre la temporada 2026.
- El administrador puede cambiar la fuente activa (worldcup26.ir / API-Football) desde el panel Admin sin reiniciar el servidor.
- Dejar preparado el diseno para que en el futuro un agente IA o servicio externo pueda supervisar o disparar actualizaciones automaticamente.
- La clave de API-Football debe guardarse solo en backend Spring Boot, nunca en Vue.

Modelo de juego confirmado:

- El admin crea una porra/apuesta.
- La app debe permitir crear y jugar varias porras.
- El admin puede invitar amigos.
- Los invitados deben aceptar la invitacion o unirse mediante enlace/codigo antes de participar.

## Objetivo del producto

Permitir que una familia o grupo privado cree una o varias porras del Mundial en las que:

- Cada participante se autentica con Google/Gmail.
- Cada participante paga 10 EUR de inscripcion.
- Un admin crea la porra y gestiona invitados, pagos y resultados.
- Los amigos invitados aceptan la invitacion o se unen con codigo/enlace.
- Cada participante completa una prediccion inicial del Mundial.
- La prediccion inicial queda guardada como historico inmutable.
- Durante el Mundial, la app compara resultados reales contra predicciones.
- La app distingue aciertos por ganador, empate, resultado exacto, goles y clasificaciones.
- La app permite corregir predicciones futuras que ya dependan de escenarios erroneos o imposibles.
- La app calcula los puntos actuales, puntos potenciales y premios monetarios posibles.
- La app muestra si el bote de pleno mundial sigue vivo o si debe redistribuirse.

## Definiciones

- Ganador/victoria/signo: equipo que gana un partido. En caso de empate, el empate cuenta como signo correcto. Esta metrica es independiente del resultado exacto.
- Resultado exacto: goles exactos de ambos equipos. Si el usuario acierta el ganador pero falla los goles, no obtiene puntos de resultado exacto, pero si obtiene los puntos de ganador/signo correcto.
- Goles acertados: acierto independiente de los goles marcados por cada equipo.
- Prediccion inicial: apuesta completa creada antes del inicio del Mundial. No se modifica nunca.
- Prediccion viva: version editable para eventos futuros, cuando la prediccion inicial ya no encaja con la realidad.
- Evento cerrado: partido o hito que ya ha empezado o terminado. No puede modificarse.
- Premio perfecto mundial: bote especial para quien acierte todo el Mundial bajo las reglas definidas.
- Premios por categoria: premios secundarios repartidos por metricas computables.
- Premio maximo posible: dinero maximo que un usuario todavia puede ganar considerando los eventos abiertos.

## Alcance v1

### Autenticacion y usuarios

- Login con Google/Gmail.
- Crear o unirse a una porra privada familiar mediante invitacion, codigo o enlace.
- Perfil basico: nombre, email, avatar, estado de pago.
- Roles:
  - Admin: crea porras, invita amigos, configura porra, confirma pagos, carga resultados reales, cierra fases.
  - Participante: paga, predice, consulta ranking y premios.

### Porras multiples e invitaciones

- La app debe permitir que un usuario admin cree varias porras.
- Una porra tiene nombre, descripcion opcional, importe de inscripcion, estado, codigo de invitacion y reglas de premios.
- Un usuario puede pertenecer a varias porras.
- Cada porra mantiene sus propios participantes, pagos, predicciones, rankings, premios y resultados.
- El admin puede invitar amigos por email o mediante enlace/codigo de invitacion.
- Una invitacion puede estar pendiente, aceptada, rechazada o caducada.
- Un usuario invitado debe aceptar la invitacion para aparecer como participante.
- Solo participantes aceptados y marcados como pagados optan a premios.

### Inscripcion

- Importe fijo por participante: 10 EUR.
- En v1, pago manual por Bizum o metalico.
- El admin marca usuarios como pagados.
- Solo usuarios pagados pueden optar a premios.
- La app muestra bote total por porra: participantes pagados x 10 EUR.

### Prediccion inicial

Cada participante debe completar antes del bloqueo inicial:

- Resultados de todos los partidos de fase de grupos.
- Clasificacion final de cada grupo.
- Cruces de eliminatorias derivados de sus predicciones.
- Ganador de cada eliminatoria.
- Resultado de cada eliminatoria si aplica.
- Finalistas.
- Campeon.

La app debe validar que la prediccion sea completa antes de aceptarla como definitiva.

### Prediccion viva y correcciones

La app mantiene dos capas:

1. Prediccion inicial inmutable.
2. Prediccion viva editable solo para eventos futuros.

Cuando un resultado real invalida una parte de la prediccion:

- La app marca que aciertos se mantienen.
- La app marca que aciertos se han perdido.
- La app detecta cruces, clasificados o escenarios imposibles.
- La app indica al usuario que debe cambiar para seguir optando a puntos futuros.
- La app no permite editar eventos cerrados.
- Las correcciones solo pueden puntuar desde eventos futuros.

### Sistema de puntos

El sistema debe ser configurable, pero v1 debe separar claramente dos conceptos:

1. Acierto de ganador/signo: se puntua cuando el usuario acierta quien gana o si el partido acaba empatado.
2. Acierto de resultado exacto: se puntua solo cuando el marcador completo coincide.

Un resultado erroneo con ganador correcto no da puntos de resultado exacto, pero si cuenta como ganador/signo correcto.

Ejemplos:

| Prediccion | Real | Ganador/signo | Resultado exacto |
|---|---|---:|---:|
| Espana 2-1 Alemania | Espana 3-1 Alemania | Correcto | Incorrecto |
| Espana 2-1 Alemania | Alemania 1-0 Espana | Incorrecto | Incorrecto |
| Espana 1-1 Alemania | Espana 2-2 Alemania | Correcto, empate | Incorrecto |
| Espana 2-1 Alemania | Espana 2-1 Alemania | Correcto | Correcto |

Valores por defecto:

| Categoria | Puntos |
|---|---:|
| Acertar ganador/signo del partido | 2 |
| Acertar resultado exacto | 2 adicionales |
| Acertar goles de un equipo | 1 por equipo |
| Acertar clasificado de grupo | 5 |
| Acertar posicion exacta en grupo | 8 |
| Acertar pase de ronda | 5 |
| Acertar semifinalista | 10 |
| Acertar finalista | 15 |
| Acertar campeon | 25 |

Regla recomendada v1: puntuacion acumulada y desglosada. El resultado exacto suma puntos adicionales al ganador/signo, pero no sustituye al ganador/signo.

Ejemplo con valores por defecto:

```text
Prediccion: Espana 2-1 Alemania
Real:       Espana 3-1 Alemania

Ganador/signo correcto: +2
Resultado exacto:       +0
Goles Espana:           +0
Goles Alemania:         +1
Total partido:          +3
```

Regla de simplicidad para ninos: la diferencia de goles no se puntua, no se muestra como categoria y no se usa como desempate. Solo importa ganador/signo, resultado exacto y goles de cada equipo.

### Ganador final de la porra

El ganador final de la porra normal no es el ganador del Premio Perfecto Mundial.

El ganador final de la porra se decide por la clasificacion general de puntos acumulados:

- puntos por ganador/signo
- puntos por resultado exacto
- puntos por goles acertados
- puntos por clasificaciones
- puntos por rondas
- puntos por campeon
- puntos bonus de apuesta inicial, si la porra los tiene activados

El Premio Perfecto Mundial es un premio especial separado. No decide automaticamente el ganador general salvo que sus puntos acumulados tambien le coloquen primero.

Si el pleno mundial se paga, puede coexistir con el ganador general:

- Un usuario puede ganar el Premio Perfecto Mundial y tambien la clasificacion general.
- Otro usuario podria ganar la clasificacion general aunque nadie consiga el Premio Perfecto Mundial.
- Si el pleno mundial queda extinguido, el ganador final sigue siendo el primero por puntos acumulados.

### Apuesta inicial

La apuesta inicial debe tener valor propio para premiar al participante que se acerco mas desde el principio, antes de poder corregir nada.

La app debe mantener dos lecturas:

1. Puntuacion viva: puntos del juego actual, incluyendo correcciones permitidas para eventos futuros.
2. Puntuacion de apuesta inicial: puntos calculados solo contra la prediccion original inmutable.

La puntuacion de apuesta inicial sirve para:

- Ranking de Mejor Apuesta Inicial.
- Premio por categoria de Mejor Apuesta Inicial.
- Bonus configurable dentro de la clasificacion general, si la porra lo activa.

Regla recomendada v1:

- La apuesta inicial se puntua con las mismas reglas de partido y torneo.
- Las correcciones vivas no modifican esta puntuacion.
- El ranking general puede mostrar el desglose: puntos vivos + bonus/aporte de apuesta inicial.
- El admin puede decidir si Mejor Apuesta Inicial es solo premio separado o si tambien suma bonus al ranking general.

Bonus recomendado para ninos/familia:

```text
Top 1 apuesta inicial: +10 puntos al ranking general
Top 2 apuesta inicial: +6 puntos al ranking general
Top 3 apuesta inicial: +3 puntos al ranking general
```

Este bonus es opcional por porra. Si se activa, debe mostrarse claramente para que todos entiendan de donde salen esos puntos.

### Sistema de premios

Cada liga tiene un bote:

```text
bote_total = usuarios_pagados * 10 EUR
```

Distribucion inicial:

```text
75% Premio Perfecto Mundial
25% Premios por categoria
```

El Premio Perfecto Mundial solo se paga si al menos un usuario acierta todos los ganadores/signos requeridos del Mundial segun la definicion configurada.

Para v1, el Premio Perfecto Mundial debe tener en cuenta solo ganadores/signos y avances correctos, no resultados exactos. Es decir, un usuario puede fallar marcadores exactos y seguir vivo para el pleno si todos los ganadores/signos y clasificados siguen siendo correctos.

Si nadie puede acertar ya el pleno mundial:

- El premio perfecto se considera extinguido.
- El 75% reservado se redistribuye automaticamente entre premios por categoria.
- La app debe mostrar el cambio de estado y el nuevo reparto.

Propuesta de reparto si el pleno sigue vivo:

| Premio | Porcentaje |
|---|---:|
| Pleno Mundial | 75% |
| Clasificacion general | 10% |
| Mejor apuesta inicial | 5% |
| Mas resultados exactos | 5% |
| Mas ganadores/empates acertados | 5% |
| Campeon acertado / mejor campeon | 5% |

Propuesta de reparto si el pleno ya no es posible:

| Premio | Porcentaje |
|---|---:|
| Clasificacion general | 40% |
| Mejor prediccion inicial | 20% |
| Mas resultados exactos | 15% |
| Mas ganadores/empates acertados | 10% |
| Mejor eliminatorias | 10% |
| Campeon acertado / mejor campeon | 5% |

La app debe calcular:

- Premios activos.
- Premios extinguidos.
- Reparto monetario actual.
- Usuarios que optan a cada premio.
- Premio actual estimado por usuario.
- Premio maximo posible por usuario.
- Categorias donde el usuario ya no puede ganar.
- Categorias donde el usuario todavia puede ganar.

### Simulador de premio posible

Esta es una funcionalidad central.

Para cada usuario, la app debe mostrar:

- Puntos actuales.
- Puntos maximos posibles.
- Posicion actual.
- Mejor posicion matematicamente posible.
- Peor posicion razonable o matematica si es viable.
- Premio actual estimado.
- Premio maximo posible.
- Lista de acciones recomendadas:
  - partidos futuros pendientes de rellenar
  - cruces imposibles que debe corregir
  - predicciones que siguen vivas
  - categorias de premio a las que todavia puede optar

Ejemplo de mensaje:

```text
Todavia puedes ganar 165 EUR.
Sigues optando a Clasificacion general, Resultados exactos y Campeon.
Sigues vivo para Pleno Mundial porque has acertado todos los ganadores/signos.
Debes corregir 2 cruces de cuartos y elegir nuevo finalista.
```

### Ranking

La app debe tener rankings:

- Ranking general vivo.
- Ranking de apuesta inicial.
- Ranking de resultados exactos.
- Ranking de ganadores/empates.
- Ranking de fase de grupos.
- Ranking de eliminatorias.
- Ranking de premios posibles.

### Admin

Panel de administracion para:

- Crear porra.
- Gestionar varias porras.
- Invitar amigos por email, enlace o codigo.
- Aceptar/rechazar participantes si se requiere moderacion.
- Configurar importe de inscripcion.
- Configurar reparto de premios.
- Confirmar pagos.
- Cargar calendario del Mundial.
- Cargar resultados reales.
- Ver estado de sincronizacion con API-Football.
- Lanzar sincronizacion manual de fixtures, directos, rondas o standings.
- Bloquear predicciones iniciales.
- Cerrar eventos.
- Recalcular puntuaciones y premios.

### Integracion API-Football

La app puede usar API-Football como proveedor principal de datos deportivos del Mundial 2026.

Configuracion base:

```text
league=1
season=2026
base_url=https://v3.football.api-sports.io
```

Endpoints previstos:

```text
GET /fixtures?live=all&league=1&season=2026
GET /fixtures?date={yyyy-mm-dd}&league=1&season=2026
GET /fixtures/rounds?league=1&season=2026
GET /standings?league=1&season=2026
```

Uso recomendado:

- Spring Boot hace las llamadas a API-Football.
- MariaDB guarda/cachea fixtures, equipos, resultados, standings y estado de sincronizacion.
- Vue consume solo nuestra API interna.
- En directo, el backend puede hacer polling controlado cada 30s solo mientras haya partidos vivos.
- Fuera de directo, usar polling mucho menos frecuente o sincronizacion manual para respetar limites.
- El admin conserva carga manual como fallback si la API falla o hay que corregir datos.

Cabeceras:

- Acceso directo API-Sports/API-Football: usar `x-apisports-key`.
- Acceso via RapidAPI: usar `x-rapidapi-key` y `x-rapidapi-host=api-football-v1.p.rapidapi.com`.
- Mantener modo configurable por entorno para no acoplar el codigo a un proveedor de acceso.

Estados relevantes de fixture:

| Codigo | Significado |
|---|---|
| NS | No empezado |
| 1H | Primera parte |
| HT | Descanso |
| 2H | Segunda parte |
| ET | Prorroga |
| P | Penaltis |
| FT | Finalizado |

## Experiencia visual esperada

Mobile-first, pero usable en escritorio.

La app debe sentirse como una app de torneo, no como una tabla basica.

Pantallas principales:

- Inicio: resumen de posicion, bote, premio maximo posible, proximos partidos.
- Inicio tambien debe mostrar un esquema clasico de cruces con partidos, predicciones y resultados reales.
- Mi Mundial: constructor visual de prediccion completa.
- Corregir: lista guiada de cambios necesarios.
- Partidos: calendario con banderas, marcadores y estado.
- Ranking: clasificacion visual con podio y categorias.
- Premios: bote, pleno mundial, redistribucion y opciones de premio por usuario.
- Admin: gestion de porras, invitaciones, pagos y resultados.

Requisitos visuales:

- Navegacion inferior en movil.
- Cards de partido compactas y visuales.
- Banderas o codigos visuales de equipos.
- Marcadores editables tipo estadio.
- Estados claros: abierto, bloqueado, acertado, fallado, pendiente, imposible.
- Animaciones sutiles para puntos, cambios de posicion y premios vivos.
- No usar una landing como primera pantalla.

## Reglas de visibilidad y anti trampas

- La prediccion inicial se bloquea antes del primer partido.
- No se pueden editar partidos cerrados.
- Las predicciones de otros usuarios pueden ocultarse hasta el cierre del evento o fase.
- Todo cambio en prediccion viva debe guardar fecha, usuario y motivo.
- El admin no debe poder alterar predicciones de participantes sin auditoria.
- Los resultados reales deben quedar auditados.

## Casos especiales

- Empate en premios: definir desempate.
- Varios usuarios aciertan pleno mundial: dividir premio perfecto entre ellos.
- Nadie acierta campeon: el premio de campeon puede redistribuirse a clasificacion general o quedar dentro de premios secundarios.
- Usuario no pagado: puede usar la app si se decide, pero no opta a premios hasta que admin confirme pago.
- Usuario que no completa prediccion inicial: no opta a pleno mundial.
- Cambios de calendario o partidos anulados: admin puede editar calendario y recalcular.

## Desempates recomendados

Para clasificacion general:

1. Mas resultados exactos.
2. Mas ganadores/empates acertados.
3. Mas puntos en eliminatorias.
4. Campeon acertado.
5. Empate monetario repartido.

Para premios por categoria:

- Si hay empate, dividir el premio entre empatados salvo que se defina otra regla.

## Tareas sugeridas de implementacion

### Fase 0 - Decisiones previas

- Usar stack confirmado: Vue + Spring Boot + MariaDB.
- Usar pagos manuales por Bizum/metalico con confirmacion de admin.
- Usar resultados actualizados desde web/app por admin en v1.
- Preparar puerto/adaptador para futuro agente IA de resultados.
- Confirmar reparto exacto de premios.
- Confirmar puntuacion exacta y si es acumulada.

### Fase 1 - Base de proyecto

- Crear estructura separada frontend/backend.
- Crear frontend Vue.
- Crear backend Spring Boot.
- Configurar MariaDB.
- Configurar TypeScript si se usa Vue con TS.
- Configurar estilos globales y sistema responsive.
- Configurar autenticacion con Google.
- Crear layout principal mobile-first.

### Fase 2 - Dominio y datos

- Modelar entidades principales:
  - User
  - Pool
  - PoolInvitation
  - PoolMember
  - Payment
  - Team
  - Match
  - PredictionSet
  - MatchPrediction
  - BracketPrediction
  - RealResult
  - ScoreBreakdown
  - PrizePool
  - PrizeCategory
  - PrizeProjection
  - AuditLog
- Implementar reglas de dominio de bloqueo, edicion y validacion.
- Implementar repositorios o acceso a datos segun stack.

### Fase 3 - Porras, usuarios, invitaciones y pagos

- Crear flujo de login con Google.
- Crear porra privada con codigo/enlace.
- Invitar amigos por email, codigo o enlace.
- Aceptar invitacion.
- Rechazar o caducar invitacion.
- Unirse a porra por codigo/enlace.
- Mostrar miembros.
- Admin confirma pagos.
- Calcular bote total.

### Fase 4 - Calendario y prediccion inicial

- Cargar equipos y partidos.
- Crear formulario visual de resultados de grupos.
- Generar clasificaciones de grupo desde predicciones.
- Generar cruces desde predicciones.
- Guardar prediccion inicial inmutable.
- Validar prediccion completa.
- Bloquear predicciones al cierre.

### Fase 5 - Resultados reales y scoring

- Admin carga resultados reales.
- Exponer servicio interno para actualizacion futura por agente IA.
- Calcular puntos por partido.
- Calcular puntos por grupos.
- Calcular puntos por eliminatorias.
- Guardar desglose de puntuacion.
- Mostrar ranking general y rankings por categoria.

### Fase 6 - Prediccion viva y correcciones

- Detectar predicciones imposibles.
- Crear pantalla de correcciones recomendadas.
- Permitir editar solo eventos futuros.
- Guardar cambios auditados.
- Recalcular puntos potenciales.

### Fase 7 - Premios

- Configurar premios.
- Calcular estado del Premio Perfecto Mundial.
- Detectar si todavia hay usuarios vivos para pleno.
- Redistribuir bote cuando el pleno sea imposible.
- Calcular premio actual estimado.
- Calcular premio maximo posible por usuario.
- Mostrar categorias vivas y extinguidas.

### Fase 8 - UI visual

- Construir dashboard principal.
- Construir pantalla Mi Mundial.
- Construir pantalla Corregir.
- Construir pantalla Ranking.
- Construir pantalla Premios.
- Construir panel Admin.
- Pulir estados visuales, animaciones y responsive.

### Fase 9 - Tests

- Tests de puntuacion por partido.
- Tests de resultado exacto, ganador, empate y goles.
- Tests de clasificacion de grupos.
- Tests de generacion de cruces.
- Tests de bloqueo de predicciones.
- Tests de redistribucion de premios.
- Tests de premio maximo posible.
- Tests de permisos admin/participante.

## Acceptance criteria iniciales

| # | Criterio |
|---|---|
| AC-1 | Un usuario puede entrar con Google/Gmail. |
| AC-2 | Un admin puede crear varias porras privadas y compartir codigo/enlace de invitacion. |
| AC-3 | Un usuario puede aceptar una invitacion o unirse a una porra privada mediante codigo/enlace. |
| AC-4 | El admin puede marcar participantes como pagados. |
| AC-5 | La app calcula el bote total a partir de participantes pagados x 10 EUR. |
| AC-6 | Un participante puede completar una prediccion inicial completa del Mundial. |
| AC-7 | La prediccion inicial queda inmutable tras bloquearse. |
| AC-8 | El admin puede introducir o actualizar resultados reales desde la web/app. |
| AC-9 | La app calcula puntos por ganador/empate, resultado exacto, goles, clasificados, rondas y campeon. |
| AC-10 | La app muestra ranking general y rankings por categoria. |
| AC-11 | La app detecta predicciones imposibles y recomienda cambios futuros. |
| AC-12 | El usuario puede modificar solo predicciones futuras dentro de la prediccion viva. |
| AC-13 | La app calcula si el Premio Perfecto Mundial sigue vivo. |
| AC-14 | Si nadie puede lograr el pleno mundial, la app redistribuye el 75% a premios por categoria. |
| AC-15 | La app muestra premio actual estimado y premio maximo posible por usuario. |
| AC-16 | La app muestra a que premios sigue optando cada usuario y cuales ya no puede ganar. |
| AC-17 | La interfaz es responsive y prioriza experiencia movil. |
| AC-18 | La primera pantalla es el dashboard funcional de la porra, no una landing. |
| AC-19 | Los cambios importantes quedan auditados. |
| AC-20 | Los usuarios no pagados no optan a premios. |

## Fuera de alcance v1

- Pasarela de pago real, salvo que el usuario la pida explicitamente.
- API deportiva automatica o agente IA de resultados en v1. El diseno debe quedar preparado para integrarlo despues.
- Notificaciones push.
- Chat familiar.
- App nativa.
- Torneos historicos avanzados.

## Entregable esperado

Una aplicacion web funcional en Vue + Spring Boot + MariaDB que permita crear y jugar varias porras familiares del Mundial, con autenticacion Google, invitaciones, pagos manuales por Bizum/metalico confirmados por admin, prediccion inicial, prediccion viva, scoring, bote, premios por categoria, redistribucion automatica del premio perfecto y una interfaz visual mobile-first.
