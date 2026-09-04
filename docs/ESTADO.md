# Estado de Orión

Resumen vivo de qué hay construido y desplegado. Se actualiza al cerrar cada paso/brief.

## Desplegado en producción (`master`)

**Backend** (Spring Boot 4.1, `co.orion`): identidad + sesión, disponibilidad + `SlotCalculator`,
reservas, asistencia, notificaciones por correo (con `.ics` + link a Google Calendar), panel admin
(usuarios, reservas, métricas). **Migraciones Flyway V1–V19.**

Módulos: `identity`, `scheduling`, `catalog`, `billing`, `messaging`, `notifications`, `reputation`,
`lifecycle`, `admin`, `shared`. La dependencia que sorprende es `identity → reputation` (el perfil
público muestra la calificación), y por eso existe `lifecycle`: es el único sitio que necesita
reserva, pago e historial a la vez.

**Frontend** (Next 16, React 19, Tailwind v4): sistema de diseño **v2 "Amanecer cálido premium"**,
mascota **Rigel** (6 poses, 2 tonos), PWA instalable (service worker + íconos de marca), y las
pantallas del MVP (login, registro, profesores, reserva, mis clases, disponibilidad, perfil de
profesor, admin).

### Features añadidas sobre el MVP base
- **Auto-registro de estudiantes** (`POST /auth/register`) + pantalla `/registro`.
- **Perfil del estudiante** (`/me/account`) + pantalla `/cuenta`.
- **Reprogramar reserva** (`POST /bookings/{id}/reschedule`).
- **Recuperar contraseña** (`/auth/forgot-password` + `/auth/reset-password`, token con hash y
  expiración) + páginas `/recuperar` y `/restablecer`. Migración V5.
- **Landing pública** en `/` (server-rendered, SEO, OG, sitemap/robots), con Rigel de protagonista.

## Verificación
- Backend: `./mvnw verify` (Testcontainers) verde.
- Frontend: `next build` + `tsc` + `lint` verdes; **e2e Playwright** verde (semilla fresca; la suite
  muta estado, `docker compose down -v` entre corridas completas).

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
del buscador. Entradas desde la portada, `/login` y "Enseña en Orión". No hay rol nuevo: la cuenta es
la misma y en Orión se es profesor cuando la postulación se aprueba.

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
semanas, mejor racha, próxima clase, mapa del último año y con quién ha practicado. **Todo sale de
reservas que ya existen**: ninguna métrica inventada, ningún campo que rellenar a mano.

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

## Pendiente / bloqueos conocidos
- **Reservas anteriores a V20 sin idioma**: las que tenía un profesor de dos idiomas quedaron con
  `language_code` en nulo a propósito, para revisión manual. La migración deja el conteo en un
  `RAISE NOTICE`.
- **Rotar la llave privada de Wompi**: viajó por chat. Nunca estuvo en el código ni se usa en este
  flujo, pero conviene rotarla.
- **Política de cancelación de una clase ya pagada**: el pago se queda retenido y aparece marcado en
  la conciliación. Decidir entre abonar saldo o devolver desde Wompi es política comercial.
- **Banderas de idioma**: los emoji de bandera no se renderizan en Windows. Habría que cambiarlos por
  códigos ("EN", "FR") o SVG.
- **Config de producción**: `ORION_APP_BASE_URL`, `WOMPI_*`, `RESEND_API_KEY`,
  `NEXT_PUBLIC_SUPPORT_WHATSAPP`, `NEXT_PUBLIC_SITE_URL` en Railway.
- Testimonios de la landing: ocultos hasta tener citas reales de Sofía.

## Mascota
La tabla viva de apariciones está en [`orion-mascota-guia.md`](./orion-mascota-guia.md).
