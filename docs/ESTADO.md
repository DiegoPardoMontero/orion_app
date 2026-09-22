# Estado de Orión

Resumen vivo de qué hay construido y desplegado. Se actualiza al cerrar cada paso/brief.

## Desplegado en producción (`master`)

**Backend** (Spring Boot 4.1, `co.orion`): identidad + sesión, disponibilidad + `SlotCalculator`,
reservas, asistencia, notificaciones por correo (con `.ics` + link a Google Calendar), panel admin
(usuarios, reservas, métricas). **Migraciones Flyway V1–V28.**

Módulos: `identity`, `scheduling`, `catalog`, `billing`, `messaging`, `notifications`, `reputation`,
`lifecycle`, `admin`, `engagement`, `legal`, `support`, `shared`. La dependencia que sorprende es `identity → reputation`
(el perfil público muestra la calificación), y por eso existe `lifecycle`: es el único sitio que
necesita reserva, pago e historial a la vez. `engagement` es el contrario: depende de casi todos y
nadie depende de él, así que se puede borrar entero sin tocar el marketplace.

**Frontend** (Next 16, React 19, Tailwind v4): sistema de diseño **v2 "Amanecer cálido premium"**,
mascota **Rigel** (6 poses, 2 tonos), PWA instalable (service worker + íconos de marca), y las
pantallas del MVP (login, registro, profesores, reserva, mis clases, disponibilidad, perfil de
profesor, admin) más las del Bloque 8 (`/logros`, `/logros/avatar`, `/estudiantes/[id]` y «Mi ficha»
dentro de `/cuenta`).

### Features añadidas sobre el MVP base
- **Auto-registro de estudiantes** (`POST /auth/register`) + pantalla `/registro`.
- **Perfil del estudiante** (`/me/account`) + pantalla `/cuenta`.
- **Reprogramar reserva** (`POST /bookings/{id}/reschedule`).
- **Recuperar contraseña** (`/auth/forgot-password` + `/auth/reset-password`, token con hash y
  expiración) + páginas `/recuperar` y `/restablecer`. Migración V5.
- **Landing pública** en `/` (server-rendered, SEO, OG, sitemap/robots), con Rigel de protagonista.

## Verificación
Al 08/09/2026, sobre `master` ya con el Bloque 9 mezclado:
- Backend: `./mvnw verify` (Testcontainers) — **161 unitarios + 380 de integración**, verde.
- Frontend: `next build` + `tsc` + `lint` verdes; **50 tests de Vitest**.
- **E2E Playwright: 15 de 16**, sobre base recreada (`docker compose down -v`). El que falta sigue
  siendo el paso por la pasarela: exige llaves de *sandbox* de Wompi en el entorno.
  Actualizados en el Bloque 9: las tres casillas del registro, y la verificación de correo —que
  ahora se hace por el camino real, leyendo el enlace del buzón de Mailpit, y no marcando la
  cuenta por SQL. El `timeout` por test subió a 60 s: estos tests manejan un `next dev` que
  compila cada ruta la primera vez, y fallar por eso solo enseña a volver a correrlos.
- El build de producción **no** se ha vuelto a recorrer a mano en navegador tras el Bloque 9.

> **Lo que costó ese repaso a mano.** El diálogo de mayoría de edad del Bloque 9 se quedaba puesto
> después de confirmarlo: la sesión guarda el usuario tal como estaba al entrar y `/auth/me`
> respondía con ese recuerdo, así que la declaración se escribía en la base y la pantalla no se
> enteraba. Como el diálogo no tiene salida, la cuenta quedaba encerrada — el admin incluido, que
> es anterior a la V24 y por tanto nunca la declaró. `FreshPrincipalFilter` ya releía la fila en
> cada petición, pero solo adoptaba la nueva si cambiaba el rol o la intención de alta; ahora la
> adopta siempre. Cubierto por `AdulthoodConfirmationIT`.

## Pagos (Bloque 4, 02/09/2026)
Reservar ya no confirma: la reserva nace `PENDING_PAYMENT` con el cupo bloqueado y solo pasa a
`CONFIRMED` cuando el webhook firmado de Wompi confirma el cobro (o cuando el saldo del estudiante
cubre la clase entera). Correo, `.ics` y sala de Jitsi salen en ese momento. Incluye créditos con
consumo FIFO, "Mis ganancias" del profesor, y conciliación + liquidación manual con CSV para el
admin. Migración V16.

**Requiere en Railway:** `WOMPI_PUBLIC_KEY`, `WOMPI_INTEGRITY_SECRET`, `WOMPI_EVENTS_SECRET` y
`WOMPI_API_BASE_URL=https://production.wompi.co/v1`, más el webhook apuntando a
`/api/v1/webhooks/payments/wompi` desde el panel de Wompi.

## Registro de profesores (02/09/2026)
`/registro?rol=profesor` con selector visible: quien viene a enseñar aterriza en `/aplicacion` en vez
del buscador. Entradas desde la portada, `/login` y "Enseña en Orión".

> **Corregido el 04/09.** Aquí decía «no hay rol nuevo: la cuenta es la misma», y eso era justo el
> problema: la cuenta era la misma que la de un estudiante, con buscador y reservas incluidos.
> Ver [El aspirante a profesor](#el-aspirante-a-profesor-04092026).

## Ciclo de vida y reputación (Bloques 5 y 6, 03/09/2026)
Reprogramación por propuesta + aceptación (el endpoint directo desapareció), reclamos por no-show
que congelan el dinero hasta que el admin resuelve, y el job horario que cierra las clases y libera
los pagos. Métricas de desempeño con ventana de 90 días, ranking nocturno con arranque en frío para
que un profesor nuevo no quede último para siempre, y sanciones progresivas en **modo observación**
(se proponen, las confirma una persona). Cancelación: **12 h para ambos**. Migraciones V17 y V18.

Panel de admin con las cifras reales del sistema y **purga definitiva** de clases y usuarios, con
vista previa de qué se destruye y confirmación escrita. Filtros del marketplace en horizontal.

**Manual de operación:** todos los flujos por rol, reglas con sus valores y cómo probarlos —
publicado como página web.

## Interfaz y horas (03/09/2026)
Horas en **formato de 12 h con AM/PM** en toda la app (`horaBogota`, `rangoHoras`, `hora12`,
`rangoCompacto`); un rango que no cruza el mediodía dice el meridiano una sola vez ("6–9 PM"). En
disponibilidad los `input[type=time]` dieron paso a selects, porque el navegador los pinta según su
locale y eso queda fuera de nuestro control.

"Mis clases" se agrupa por día sobre una línea de tiempo, con "Hoy"/"Mañana"/"Hace 3 días" y la
marca "La siguiente". Navegación lateral por secciones, barra móvil limitada a cinco, controles más
pequeños que dejan de estirarse a lo ancho de la tarjeta en escritorio.

**Se retiró el último resto de WhatsApp**: el botón de contacto directo y el teléfono de la
contraparte en `MyBookingResponse`. Con la comisión encendida ese campo no era una fuga de contacto
sino la clase siguiente acordada por fuera; el test lo afirma ahora sobre el JSON crudo.

## Correos con marca (03/09/2026)
Todo correo pasa por `EmailLayout`: cabecera con el logo, tarjeta de 600 px y pie con el eslogan. Se
aplica en `BrandedMailTransport`, un decorador `@Primary`, y no en cada redactor — hay cinco sitios
que envían correo y el sexto que se escriba saldría sin marca si dependiera de acordarse.

El logo va por URL pública (`{orion.app.base-url}/email/orion-logo.png`) y no como adjunto `cid:`:
la API HTTP de Resend no entrega los embebidos igual que el SMTP local. El PNG viene aplanado sobre
el crema de marca, porque los clientes en modo oscuro invierten el HTML pero no las imágenes.

## Clases gratuitas (03/09/2026)
Una tarifa de **0 COP** hace que la reserva se confirme sin pasar por la pasarela: el importe a
cobrar es 0 y `CheckoutService` ya confirmaba en el acto en ese caso (el mismo camino de un crédito
que cubre la clase entera). Lo único que lo impedía era el CHECK de la tarifa. Migración **V19**.

El 0 es un valor aparte, no una rebaja del piso: entre 1 y 19.999 sigue prohibido. Solo lo pone un
administrador desde *Usuarios → Tarifa*; el formulario del profesor conserva su piso de 20.000. En
la interfaz se dice "Gratis", no "$0". Sirve para probar el flujo completo en producción sin mover
dinero.

## Calendarios (03/09/2026)
**Disponibilidad del profesor**: rejilla de horas por días en escritorio, donde la duración de una
franja es su altura y pulsar una celda vacía propone una franja que empieza ahí. En móvil siguen las
tarjetas apiladas.

**Mis clases**: interruptor Agenda / Calendario. La lista contesta "qué tengo ahora" y el calendario
mensual "cómo va mi mes"; elegir un día muestra sus clases debajo, sin cambiar de pantalla.

**Filtros del marketplace**: de entrada solo *Ordenar por* y *Precio*; el resto detrás de un botón
*Avanzado* que indica cuántos filtros avanzados hay activos.

## Mensajería en los dos sentidos (03/09/2026)
El profesor ya puede iniciar la conversación, no solo responder. Las dos direcciones tienen gates
distintos y por eso la regla vive en `ConversationService` y no en `SecurityConfig`: el estudiante
escribe a cualquier profesor aprobado, el profesor **solo a estudiantes que ya reservaron con él**
(`existsByProfessorIdAndStudentId`, cualquier estado, canceladas incluidas). Sin esa asimetría, la
bandeja de cualquier estudiante quedaría abierta a mensajes no pedidos de todo el directorio.

`CreateConversationRequest` pasó de `professorId` a `counterpartId`: el endpoint es uno solo y lo que
cambia es quién lo llama.

## Ficha del profesor con mínimos (03/09/2026)
Titular de **5 palabras**, descripción entre **20 y 100**, con contador en vivo en el perfil y en la
postulación. La regla vive en `ProfessorProfile.describe()`, que es la puerta por la que pasan los
cuatro caminos que escriben la ficha —perfil propio, postulación, invitación del admin y sembrador—;
ponerla en un servicio dejaba fuera a los otros tres. Lanza `UnprocessableException` desde el
dominio, como ya hacía `TeacherApplication`. Vacío sigue valiendo; lo que no vale es escribir poco.

## Panel de progreso del estudiante (03/09/2026)
`GET /api/v1/me/progress` y el panel en `/cuenta`: clases tomadas, horas de práctica, racha de
semanas, mejor racha, próxima clase, mapa de constancia y con quién ha practicado. **Todo sale de
reservas que ya existen**: ninguna métrica inventada, ningún campo que rellenar a mano.

> El mapa era anual hasta el 04/09; ahora son **12 semanas** (`GET /me/streak`). Con una clase por
> semana, una cuadrícula de un año está vacía en un 98 % y comunica abandono en vez de progreso.

Qué cuenta como clase tomada: `COMPLETED`, y `CONFIRMED` que ya terminó. Los dos no-show quedan
fuera —si faltó el estudiante no la tomó, si faltó el profesor no la hubo—.

La aritmética vive en `LearningProgress`, clase pura como `SlotCalculator`: sin Spring, sin
repositorios y con el "ahora" por parámetro. La racha sigue viva si la última semana con clase es
esta o la pasada, y todo se decide en Bogotá (una clase del domingo a las 23:00 en Bogotá cae en
lunes UTC y partiría la racha en dos).

## Landing (03/09/2026)
Fuera el Método ORION; en su lugar **Nosotros**, con cuatro cosas que la plataforma hace de verdad
hoy. "Cómo funciona" encadena los cuatro pasos con flechas que se encienden en bucle (CSS con
retardos, sin JavaScript). Seis objetivos en portada, cada sección con su descripción, y menos aire
entre el hero, idiomas y cómo funciona.

Rigel estrena **pose de profesor** (birrete, gafas y tiza) para `/registro?rol=profesor`.

**Eslogan vigente: «Find your right teacher, learn your way»**, en login, landing, metadatos,
manifest y pie de los correos.

## Gamificación · Bloque 8 (04/09/2026)
Módulo nuevo **`engagement`**, el único que depende de todos y del que no depende nadie: se puede
borrar entero sin tocar el marketplace. Entra por eventos (`LessonCompletedEvent`,
`BookingCompletedEvent`, `BookingCreatedEvent`, `StudentProfileUpdatedEvent`) y no llama a ningún
otro módulo.

Migraciones **V20** (`bookings.language_code`, poblado solo donde el profesor enseña un único
idioma), **V21** (`student_profiles`, `student_goals`, `student_accessories`) y **V22**
(`point_events` append-only con índice único por origen, `achievements` con los 20 del diseño,
`user_achievements`, `cosmetics` con PK compuesta `(kind, code)`, `streak_protections`).

**Todo estudiante tiene ficha**: nivel autodeclarado, idioma, motivación y objetivos, más su avatar
compuesto (marco, paleta, cielo y hasta tres accesorios, todo CSS). El perfil es **privado por
defecto** y los menores de 18 no lo pueden publicar. Tres capas de visibilidad, y cuando no hay
derecho a ver se responde **404, nunca 403**: un 403 confirmaría que el perfil existe.

**20 logros en cinco familias**, con progreso y tres niveles de brillo. La lógica de racha y de
criterios vive en clases puras (`StreakCalculator`, `AchievementEvaluators`) con el "ahora" por
parámetro, como `SlotCalculator`. Regla que conviene recordar: **una semana protegida puentea la
racha pero no la suma**. Las clases gratuitas no puntúan salvo que
`gamification_count_free_lessons` diga lo contrario.

`recompute` es idempotente y deja el mismo estado exacto que el procesamiento incremental — es el
test que protege el bloque. El `EngagementBackfillRunner` corre el último de todos los
`ApplicationRunner` y enciende lo que ya estaba ganado.

Pantallas: **/logros** (el cielo, cinco constelaciones), **/logros/avatar** (lo bloqueado a la vista
con su condición en español), **/estudiantes/[id]** (la vista del profesor, enlazada desde la
tarjeta de clase y desde el hilo de mensajes), **Mi ficha** en `/cuenta`, y **el encendido**, la
celebración de 720 ms que vive en el armazón de la app —una clase se cierra casi nunca mientras
miras el tablero—. El mapa anual del panel fue **reemplazado por doce semanas**: con una clase por
semana, una cuadrícula anual está vacía en un 98 % y comunica abandono.

**Semilla de desarrollo**: Ana nace con cuatro clases pasadas en cuatro semanas distintas, dos
idiomas y una presencial, para que la gamificación se pueda ver en local. `DevDataSeeder` va con
`@Order(0)`: sin ese orden explícito, sobre una base recién creada `BillingDevSeeder` corría antes
y Ana nacía sin saldo.

## El aspirante a profesor (04/09/2026)
Registrarse por «Postúlate para dar clases» ya no crea una cuenta de estudiante. `users.signup_intent`
(V23) distingue las dos puertas de entrada, y **`OrionUserDetails` no le da `ROLE_STUDENT`** a quien
entró por la de enseñar: como toda la experiencia del estudiante cuelga de esa autoridad en
`SecurityConfig`, no dársela cierra las decenas de puertas de una vez.

Su rol efectivo es `TEACHER_APPLICANT`, y eso es lo que devuelve `/auth/me`: el frontend dibuja el
menú del aspirante contra una API que autoriza igual, en vez de dos versiones de la verdad.

**Aprobar una postulación ahora promueve la cuenta a `PROFESSOR`** y le crea su perfil vacío. Antes
no lo hacía, así que se aprobaba a alguien para que siguiera sin poder publicar su perfil ni abrir
su disponibilidad. **Rechazarla devuelve la cuenta a estudiante**: rechazada no puede significar
cuenta inservible.

`FreshPrincipalFilter` relee al usuario en cada petición autenticada. Sin él, la aprobación no
valdría hasta el siguiente login — y una baja de cuenta tampoco, que es peor.

Las cuentas anteriores se quedaron en `LEARN` a propósito: marcar `TEACH` a un estudiante que
postuló le cerraría hoy las clases que ya reservó y el saldo que ya pagó. Un estudiante que postula
sigue siendo estudiante, con todo lo suyo.

**Consecuencia que conviene tener presente:** al promover a alguien que ya usaba Orión como
estudiante, su saldo a favor deja de ser alcanzable (`/me/credits` es de estudiantes). A la escala
actual es raro y se resuelve a mano; si se vuelve frecuente, hay que decidir qué pasa con ese saldo.

## Cumplimiento legal y operación · Bloque 9 (08/09/2026)

Orión estaba construido y no se podía lanzar: cobraba sin contrato con el cliente y guardaba fecha
de nacimiento sin autorización de tratamiento. El brief está en
[`orion-bloque-9-cumplimiento-y-operacion.md`](./briefs/orion-bloque-9-cumplimiento-y-operacion.md).

**Solo mayores de 18 (V24).** El art. 7 de la Ley 1581 de 2012 prohíbe tratar datos de menores
salvo con autorización del representante legal, y ese flujo no existe. Se cierra en el registro, se
revalida al reservar y las cuentas anteriores lo declaran en un diálogo al entrar. Con eso
`student_profiles.birth_date` se queda sin finalidad y **se borra** por minimización.

**Términos y Política de tratamiento (V25), módulo `legal`.** Versionados en base, no en el código:
cuando alguien pregunte qué aceptó en marzo, la respuesta tiene que ser el texto de marzo. El cuerpo
guarda marcadores `{{...}}` que se rellenan al leerlo, así cambiar de domicilio no obliga a publicar
una versión nueva. Las aceptaciones reutilizan `agreement_acceptances` (V12), que ya tenía la forma
de la constancia del art. 9; la entidad se mudó de `identity` a `legal` para romper el ciclo.

Tres casillas separadas en el registro: empaquetar la autorización de datos con los términos la
viciaría. `PoliticaDeTratamientoTest` comprueba las seis secciones del art. 13 del Decreto 1377.

**Verificación de correo (V26)** con reenvío frenado a tres por hora, **límites de intentos** en
login/alta/recuperación, **tickets de soporte (V27)** con los plazos legales de habeas data y
retracto, **pantalla de ajustes (V28)** con validación en servidor, historial y confirmación escrita
en lo sensible, **alertas por correo** de errores y de procesos caídos, y **filtro del buscador por
día y franja** (el `schedule=` que el hero llevaba tiempo mandando sin que nadie lo recogiera).

> **El retracto está escrito pero no automatizado.** Pardo aplazó el paso 5 el 08/09 («no toques
> dinero por ahora»). El derecho rige desde los Términos —5 días hábiles, con su excepción, y
> devolución al medio de pago en 15 días calendario— y se ejerce abriendo un ticket de categoría
> `RETRACTO`, que vence visiblemente en la bandeja. La devolución la hace una persona en Wompi.
> Cumplimiento manual y trazable, no automático.

**Config nueva en Railway:** `ORION_LEGAL_*` (nombre, documento, domicilio, ciudad, correo,
whatsapp, horario) — **sin ellas el perfil `prod` no arranca**, a propósito — y `ORION_ALERTS_TO`.

## Portal del estudiante — revisión de Sofía (08/09/2026)

Doce puntos revisados por producto. Lo que se hizo, y lo que no.

**Hecho.** Orión es **solo virtual**: fuera el selector de modalidad, fuera los distintivos que
anunciaban presencial, y la **V30** reescribe las reservas presenciales como virtuales y estrecha el
CHECK. Pedir `IN_PERSON` ahora es un 400. Con ello se retiró el logro «Cara a cara» —el cielo queda
en **19 estrellas** y AMPLITUD en dos—, porque una estrella imposible es peor que ninguna.

El **historial de pagos** ya no repite la misma clase: los intentos de cobro abandonados no son
pagos y dejaron de listarse. Cada tarjeta muestra **dos fechas rotuladas** (la clase y el cobro) en
vez de una sin nombre, y `REFUND_PENDING` —que faltaba en el mapa de estados— ya no le enseña a
nadie el nombre crudo de un enum.

**Cancelar y retractarse son un solo botón.** La diferencia que importa —adónde va el dinero— es
ahora la pregunta del diálogo: al saldo, enseguida; o al medio de pago, hasta 15 días. El derecho de
retracto no se fue: se ofrece donde se decide. La **política de cancelación** está visible en la
cuenta del estudiante y en la del profesor, y «soltar el cupo» pasó a llamarse cancelar, como todo
lo demás.

**Preguntas frecuentes** separadas por rol, en la cuenta del estudiante, en el perfil del profesor y
en la portada, cada una con salida a WhatsApp. La **semana protegida** por fin se explica donde se
ve la racha. Y **Rigel dejó de hacer ese gesto**: la tiza cruza el puño en horizontal en vez de
salir hacia arriba.

**Strikes al profesor (V31):** cancelar dentro de las 12 h ya no sale gratis. Queda una falta con su
tipo —`LATE_CANCELLATION`, distinta de `NO_SHOW`— que alimenta la misma escalera de sanciones, en
modo observación como el resto.

**Videollamada:** apretado lo que se podía sin cuenta nueva (sala de 32 caracteres en vez de 8,
antesala obligatoria, sin botón de invitar). Lo que no se puede sin pagar es impedir que el
estudiante sea moderador. Comparación y recomendación en
[docs/videollamada-opciones.md](videollamada-opciones.md).

**Ya existía, no hacía falta construirlo.** El **polling de Wompi** en la pantalla de retorno está
implementado y consulta cada 3 s mientras el pago siga pendiente; si en producción no ocurre, el
sospechoso es `ORION_APP_BASE_URL` en Railway, no el código. La **calificación al profesor** también
existe: botón «Calificar» en Mis clases → Pasadas. Y el flujo de **«Reportar un problema»** está
probado de punta a punta en `LessonLifecycleIT`, con sus dos ventanas (15 min y 24 h), el estado «en
revisión» y el pago congelado.

**No se hizo, y por qué.** El flujo de **menores de edad** con el padre como responsable choca con
el Bloque 9 —Orión es 18+ por el art. 7 de la Ley 1581— y exige autorización del representante
legal, textos legales nuevos y datos de un tercero: es un bloque entero y necesita abogado antes.

## Ajustes del 15 de septiembre (16/09/2026)

Tanda grande de producto, encargada el 15/09 y desplegada el 16. El brief está en
[`briefs/ajustes-15-septiembre-2026.md`](./briefs/ajustes-15-septiembre-2026.md).

- **Orión enseña solo inglés.** Se apagó, no se borró: `languages.is_active` en false para francés
  y español (V42), y las ofertas de profesores que los usaban, eliminadas. Todo lo que ofrece
  idiomas lee `activeLanguages()`, así que el backend se volvió monoidioma sin tocar código.
  Reabrir un idioma es una bandera y unos selectores, no una migración de vuelta.
  - Salió un hueco: la validación de escritura comprobaba contra `findAll()` y no contra lo activo,
    así que un profesor podía guardarse enseñando francés aunque ya no se ofreciera. Los objetivos
    tenían el mismo agujero.
- **Cupos cada media hora.** `SLOT_CADENCE` pasa de 60 a 30 minutos. **La clase sigue durando 55 y
  el precio no cambia**: lo que cambia es cuándo puede empezar. Tomar las 5:00 retira las 5:30
  —se solapan— y deja libres las 6:00, con los mismos cinco minutos de respiro. El formulario de
  disponibilidad también estaba atado a horas en punto por convención propia, y ya no.
- **Un número, un solo sitio.** `PublicFiguresService` sirve las cifras a `/api/v1/catalog/figures`
  para las pantallas y como `{{marcadores}}` para los documentos legales. Cambiar la comisión en
  Ajustes cambia el SEO de la página pública, el JSON-LD, las preguntas frecuentes y la cláusula 5
  de los Términos. **La comisión bajó al 15 %** (V40).
  - Dos mentiras salieron al cablearlo: dos pantallas anunciaban clases de 60 minutos, y
    `AvailabilityRuleLookup` tenía su propio `CLASS_LENGTH` de una hora que filtraba fuera del
    buscador al profesor cuya franja mide justo lo que dura una clase.
  - Se retiró la versión 1.1 de los Términos (V41): congelar un porcentaje como cláusula era el
    error. Ahora la 1.0 dice `{{comision}}`.
- **Notificaciones**: se borran de verdad —una a una o vaciando las leídas, que respeta lo no
  visto— y el panel dejó de abrirse por detrás. Era un `absolute` dentro de la campana, y un
  `z-50` solo compite dentro de su contexto de apilamiento; ahora va en un portal sobre
  `document.body` y queda resuelto en todas las pantallas a la vez.
- **Los perfiles se leen antes de editarse.** Botón «Editar», Cancelar que restaura, y los campos
  apagados con un `fieldset disabled` en vez de quince `disabled` sueltos.
- **Perfiles troceados en secciones**, con la sección en la URL para poder enlazarla. «Mi cielo»
  vive dentro del perfil y `/logros` redirige, porque las notificaciones de logros ya apuntaban ahí.
- **Landing**: héroe hacia el diagnóstico, fuera la sección de Idiomas, entra el Método ORION® con
  su cita por letra, «Nosotros» minimalista y color en los cuatro pasos.
- **Rigel**: el pulgar era un círculo suelto junto a una palma con tres costuras largas, y juntos se
  leían como cuatro dedos. Los gestos de un dedo van ahora sobre un puño cerrado. Hay además una
  mano negra de dibujo para donde la mano es el gesto.
- **El aula dentro de Orión** (JaaS): el profesor entra como moderador y el estudiante no, que es
  lo que no se podía hacer en la sala pública. Antesala, cierre y hoja de conexión caída, del
  handoff de diseño.
- **Administración → Sistema**: qué integraciones están vivas en este despliegue, y un ensayo del
  aula que crea una clase de prueba sin cobrar ni mandar correos.

## Diagnóstico de confianza · Bloque 9 (17/09/2026)

Dos minutos de conversación por voz que terminan en un Confidence Score, un diagnóstico escrito y
tres profesores. **No es una prueba de nivel**: mide los marcadores de confianza al hablar, así que
alguien con gramática impecable y pánico escénico puntúa bajo — y eso es correcto.

- **El puntaje vive en una clase pura** (`ConfidenceScoreCalculator`), usa medianas y no promedios,
  y exige cuatro turnos mínimos. Por debajo se cierra como `ABANDONED`: un número sacado de dos
  frases parece un dato y no lo es.
- **Las señales las deduce el servidor** (`SignalExtractor`) de la transcripción. El cliente solo
  manda lo que únicamente el navegador puede medir —latencia y duración—, porque un puntaje
  construido sobre números que manda el navegador no es reproducible.
- **Las tres recomendaciones no las decide la IA.** Consulta determinista que reusa el filtro del
  buscador y el `ranking_score` de `reputation`, más una comprobación de cupos reales en 7 días.
  Con menos de tres candidatos devuelve los que haya: nada se rellena bajando los criterios.
- **El audio no pasa por Orión.** Se emite una credencial efímera y el navegador habla directo con
  el proveedor. La transcripción caduca al año y revocar el consentimiento la borra sin esperar.
- **`FROM_ZERO`**: dos turnos seguidos en español cierran sin número y sin constelación.
- **Tope de gasto** con aviso al 80 % y apagado automático: al llegar al tope el bloque desaparece
  de la portada, sin mensaje de error ni botón gris.

**Requiere en Railway:** `OPENAI_API_KEY` y **`ORION_VOICE_PROVIDER=openai`**. Sin la segunda, el
proveedor por defecto es el falso y el diagnóstico *funciona* con una conversación simulada — el
peor fallo posible, porque no se nota. El estado real se ve en Administración → Sistema.

> **Corregido el 22/09: Meissa hablaba en español, y el puntaje no podía salir.** Probado contra
> OpenAI, tres saludos de tres salían en español: el guion está escrito en español y el idioma iba
> enterrado en la segunda frase. La persona contestaba en español y a los dos turnos el propio
> guion la mandaba a la rama en español. El **guion v4** abre con la regla del idioma, escrita en
> inglés, y un saludo de ejemplo (15 de 15 saludos en inglés en la prueba).
>
> La misma prueba sacó otros dos fallos. La sesión **no pedía transcribir a la persona**, así que
> el servidor nunca recibía un turno suyo y todo diagnóstico se habría cerrado sin número; ahora
> se pide con `gpt-4o-mini-transcribe`, sin fijar idioma. Y los `gpt-realtime-2.x` **rellenaban
> en voz alta** («déjame pensar un momento») antes de hablar: `reasoning.effort` va en `minimal`.
>
> Queda un desvío conocido: ante «Perdón, no entiendo», Meissa a veces pasa a español al primer
> turno en vez de al segundo (2 de 3 en la prueba).

## Diagnóstico sin cuenta y Meissa (22/09/2026)

Brief en [`briefs/diagnostico-sin-cuenta-y-login-social.md`](./briefs/diagnostico-sin-cuenta-y-login-social.md),
con las decisiones que tomó Pardo.

- **Portada**: dice primero qué es Orión («una academia de inglés especializada») y después
  propone el diagnóstico, con un botón a todo el ancho; profesores, crear cuenta y entrar van en
  una fila de secundarios iguales.
- **Sin cuenta hasta reservar.** El diagnóstico lo hace un *lead*: nombre de pila y dos casillas
  separadas (mayor de 18; autorización de voz), ligado al dispositivo con la cookie httpOnly
  `ORION_LEAD`, de la que la base solo guarda el hash (V43). Al crear cuenta o entrar,
  `LeadClaimFilter` muda sus diagnósticos a la cuenta y copia la autorización de voz a
  `voice_consents` con su fecha original. Lo que nadie reclama se borra a los
  `assessment_lead_retention_days` (30). Freno de cinco diagnósticos anónimos por IP al día, además
  del tope de gasto. El correo verificado dejó de exigirse para el diagnóstico: pedírselo a quien
  tiene cuenta y no a quien no la tiene no protegía nada.
- **Siempre tres profesores**, por escalones (encajan y tienen agenda → cualquiera del idioma con
  agenda → cualquiera), y la razón nunca exagera: al relleno le corresponde `VERIFIED`.
- **Resumen personalizado** de lo que la persona contó, escrito por `gpt-5-mini` con razonamiento
  mínimo y revisado antes de mostrarse (palabras prohibidas → frase de plantilla). Va al presupuesto
  del diagnóstico. Todas las salidas lo llevan, también la rama en español y la conversación corta.
- **Etiqueta del resultado** por tramos del handoff («Ya te defiendes», «Con soltura»…); nunca
  letras del MCER. La rama en español muestra «Primeros pasos» sin número.
- **Meissa**, la segunda mascota, en todo el flujo: `/diagnostico` (sin pasos y sin scroll), la
  conversación a pantalla completa con sus estados habla/escucha/piensa y el subtítulo de lo que
  dice, la espera y el cierre. Rigel ya no aparece en el diagnóstico.
- **«¿Prefieres que te llame una persona?»** (V44): nombre, WhatsApp y su autorización en casilla
  propia. Avisa por correo a la academia (`ORION_LEGAL_CORREO`) y queda en Administración →
  Llamadas, con el chat de WhatsApp a un clic y el botón de marcar atendida.
- **Entrar con Google, Apple o Facebook** (V45, `social_identities`). Cada botón aparece solo si su
  proveedor tiene sus variables, y su estado se ve en Administración → Sistema. Quien vuelve con
  una identidad ya vinculada entra; si su correo ya existe **y el proveedor lo verificó**, se
  vincula y entra (sin verificación nunca: sería quedarse con una cuenta ajena); si es nuevo, pasa
  por `/registro/completar` a marcar las tres casillas del alta, y la cuenta nace ahí, sin
  contraseña utilizable y con el correo verificado si el proveedor lo garantizó. Apple: su secreto
  es un JWT ES256 que se firma en cada intercambio, y como vuelve con un POST entre sitios, su
  solicitud viaja en una cookie firmada (HMAC) de cinco minutos en vez de en la sesión.
  **Requiere en Railway**, por proveedor: `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET`;
  `FACEBOOK_CLIENT_ID` y `FACEBOOK_CLIENT_SECRET`; `APPLE_CLIENT_ID` (Services ID),
  `APPLE_TEAM_ID`, `APPLE_KEY_ID` y `APPLE_PRIVATE_KEY` (el .p8). La dirección de vuelta que se da
  de alta en cada consola es `https://orionidiomas.com/login/oauth2/code/{google|facebook|apple}`.

## Pendiente / bloqueos conocidos
- **Reservas anteriores a V20 sin idioma**: las que tenía un profesor de dos idiomas quedaron con
  `language_code` en nulo a propósito, para revisión manual. La migración deja el conteo en un
  `RAISE NOTICE`.
- **Rotar la llave privada de Wompi**: viajó por chat. Nunca estuvo en el código ni se usa en este
  flujo, pero conviene rotarla.
- **Política de cancelación de una clase ya pagada**: el pago se queda retenido y aparece marcado en
  la conciliación. Decidir entre abonar saldo o devolver desde Wompi es política comercial.
- ~~Los textos legales no los ha revisado un abogado.~~ **Revisados**: Pardo confirma el 22/09/2026
  que un abogado los leyó y están bien. Queda un dato para la próxima versión: la sección 4 de la
  política (con quién se comparten los datos) no nombra a OpenAI, que recibe la voz del
  diagnóstico, ni a 8x8, que aloja las clases.
- **Retracto sin flujo propio**: ver el Bloque 9. Se atiende por ticket, a mano.
- **Subida de fotos y documentos en local**: exige `CLOUDINARY_URL` en el entorno. Sin ella la API
  responde 503 con un mensaje legible (antes era un 500 sin explicación), pero el wizard de
  postulación no se puede terminar en local: le faltarán siempre la foto y el CV.
- **Config de producción**: `ORION_APP_BASE_URL`, `WOMPI_*`, `RESEND_API_KEY`,
  `NEXT_PUBLIC_SUPPORT_WHATSAPP`, `NEXT_PUBLIC_SITE_URL`, `ORION_LEGAL_*` y `ORION_ALERTS_TO`
  en Railway.
- Testimonios de la landing: ocultos hasta tener citas reales de Sofía.
- **El texto del resultado del diagnóstico no lo ha revisado Sofía.** Se desplegó con autorización
  de Pardo (17/09/2026). Es el único momento del producto en que Orión le dice a una persona algo
  sobre sí misma, y si se siente como un juicio la función hace más daño que bien.
- **Las anclas del Confidence Score se calibraron contra cero conversaciones reales.** La primera
  grabada dio 85 con un «can you repeat that?» y un cambio a español de por medio, que es
  generoso. Con dos o tres más se mueven con algo que no sea intuición.
- **Las heurísticas de `SignalExtractor` reconocen lo que aparece en esa única conversación.** Se
  equivocarán en casos que aún no hemos visto; por eso el cálculo usa medianas.
- **El secreto del webhook de JaaS**: sin él la antesala siempre dice «aún no ha entrado», y no se
  puede calcular la tardanza del profesor (punto 12 de Sofía) porque no hay registro de a qué hora
  entró cada uno.
- **Rotar la llave de OpenAI**: viajó por la terminal y quedó en el transcript de la sesión.
- **Cuánto se guardan las solicitudes de llamada ya atendidas**: hoy, indefinidamente. Conviene
  fijar un plazo (y un job que lo cumpla) antes de que se acumulen teléfonos sin finalidad vigente.
- **Límite de tasa de OpenAI**: la organización tiene 40.000 tokens por minuto en el modelo de
  voz, y cada respuesta de Meissa gasta unos 2.800 porque relee el guion entero. Con dos o tres
  diagnósticos a la vez se alcanza, y entonces la respuesta llega como `response.done` con estado
  `failed`: el navegador no lo trata, así que Meissa simplemente se calla. Visto el 22/09 en la
  prueba del guion v4.
- **El avatar personalizado solo lo ve su dueño.** Que otros lo vean en sus listas exige embeber la
  personalización en dos DTOs y añade una consulta a los endpoints que pintan listas.
- **`LegalDocumentService.pendientes()` no lo llama nadie**: publicar una versión nueva de los
  Términos no le pide a nadie que la acepte, aunque la cláusula 15 promete justo eso.

## Repaso de flujos (04/09/2026)
Se recorrieron con navegador los flujos del manual —estudiante, profesor y administración, en
escritorio y en móvil— y se corrigió lo que salió:

- La grilla semanal ordenaba sus filas de hora con `.sort()` sobre la etiqueta: «10:00 AM» encima
  de «9:00 AM». Ahora ordena por minuto del día (`minutoDelDiaBogota`, con prueba).
- Las banderas de idioma salían como caja vacía en Windows. El **disco con el código ISO** del
  Bloque 8 ya existía por esa razón y ahora se usa también fuera de la gamificación.
- La ficha del estudiante le mostraba al profesor `CONVERSATION` en vez de «Conversación», no tenía
  salida y no decía nada cuando estaba vacía.
- El diálogo modal se dibujaba dentro de su contexto de apilamiento, así que en móvil la barra
  inferior quedaba encima. Va en un **portal a `document.body`**.
- El panel de notificaciones se salía por el borde izquierdo en escritorio; **Escape** no cerraba
  ningún desplegable; y los avisos sobre una clase llevaban a la lista completa en vez de a esa
  clase (`?clase=<id>`).
- El **recálculo de logros anunciaba**: ocho avisos idénticos de «Encendiste 7 estrellas», uno por
  arranque, con todas las estrellas fechadas el último. Ahora es silencioso y conserva
  `unlocked_at`.
- **Registrar asistencia no encendía nada**: solo lo hacía el trabajo horario. Es el camino que
  ocurre de verdad casi siempre.
- Tres errores que salían como 500 «Unexpected error» ahora dicen qué pasa: verbo equivocado (405),
  código de catálogo inexistente (422 con los válidos) e integración sin configurar (503).
- Las dos fechas del período de liquidación no tenían rótulo visible: nada decía cuál era el inicio
  del período y cuál el final, y con eso se liquida el mes equivocado.
- El panel del admin escribía la hora de los procesos como «6:30:35 a. m.», que no es como Orión
  escribe una hora en ninguna otra pantalla.
- **`retry: false` global** en TanStack Query era demasiado ancho. Un 404 o un 422 son la respuesta
  y no mejoran repitiéndose, pero un corte de red o un 500 sí: sin reintentar, un único paquete
  perdido dejaba a la persona en «No pudimos cargar…» con un botón que tiene que descubrir. Ahora
  se reintenta dos veces lo que puede mejorar, y nunca un 4xx.

Lo que **no** se pudo recorrer en local: el wizard de postulación completo (foto y CV exigen
Cloudinary) y el paso por la pasarela (exige llaves de sandbox de Wompi). Los dos están cubiertos
por tests de integración.

## Mascota
La tabla viva de apariciones está en [`orion-mascota-guia.md`](./orion-mascota-guia.md).
