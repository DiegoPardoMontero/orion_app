# Estado de Orión

Resumen vivo de qué hay construido y desplegado. Se actualiza al cerrar cada paso/brief.

## Desplegado en producción (`master`)

**Backend** (Spring Boot 4.1, `co.orion`): identidad + sesión, disponibilidad + `SlotCalculator`,
reservas, asistencia, notificaciones por correo (con `.ics` + link a Google Calendar), panel admin
(usuarios, reservas, métricas). **Migraciones Flyway V1–V68.**

Módulos: `identity`, `scheduling`, `catalog`, `billing`, `messaging`, `notifications`, `reputation`,
`lifecycle`, `admin`, `engagement`, `legal`, `support`, `assessment`, `teaching`, `practice`,
`onboarding`, `shared`. La dependencia que sorprende es `identity → reputation`
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
Al 25/09/2026 de madrugada, tras la noche autónoma (el wireflow probado, la revisión de seguridad
del Bloque 11 y las sesiones en la base):
- Backend: `./mvnw verify` — **390 unitarios + 596 de integración**, verde.
- Frontend: `tsc` + `lint` verdes; **127 tests de Vitest**.
- **E2E Playwright: 92 de 93** sobre base recreada y con el caché de fetch de Next limpio (la de
  Wompi, fuera; la que se salta pide una clase dentro del plazo de reclamo, que la base recién
  sembrada no trae, y la cubre el backend). Incluye las cuatro suites del wireflow.
- **Sesiones**: con la cookie de un login, se apagó el backend y se arrancó otro proceso; la misma
  cookie siguió dentro (`/auth/me` 200).
- **Wireflow**: 232 de 253 casos probados por Claude (204 en el navegador, 28 en el backend); el
  resultado se ve en la página debajo de cada caso.
- **Celular y escritorio**: 47 pantallas de los cuatro roles recorridas a 360, 390 y 1280 px buscando
  desbordes laterales y textos cortados: ninguna.
- **Reporte de la noche**: https://claude.ai/artifact/LvkmUM9K9kvGMQyq4bMNxD

Al 24/09/2026 por la noche, con la tercera tanda del Bloque 11 (pasos 19–25: editar sin modo
edición, la clase minimizable, las estrellas de «Mi ficha», filtrar por horas exactas, los mensajes
de Rigel, la clase de prueba gratis y el wireflow):
- Backend: `./mvnw verify` — **387 unitarios + 580 de integración**, verde.
- Frontend: `tsc` + `lint` verdes; **118 tests de Vitest**.
- **E2E Playwright: 23 de 24** sobre base recreada (la de Wompi pide llaves de *sandbox*). La clase
  minimizable no la cubre la e2e —en local no hay JaaS—: se probó con el aula y Jitsi simulados en
  Playwright (el contador del iframe sigue al minimizar, navegar y volver; Jitsi se monta una vez).
- **Wireflow de toda la app**, con 85 pantallas capturadas y 253 casos para marcar entre dos:
  https://claude.ai/artifact/DneCWSQzDYH16wr7kYBqmj — reemplaza a la lista de flujos para probar.

Al 24/09/2026, con la segunda tanda del Bloque 11 (pasos 12–18: escritorio ancho, recordar la
práctica, país con bandera, la franja del perfil del profesor, horarios dentro del perfil, Rigel en
el hero del celular e «Invitar estudiantes»):
- Backend: `./mvnw verify` — **376 unitarios + 572 de integración**, verde.
- Frontend: `tsc` + `lint` verdes; **118 tests de Vitest**.
- **E2E Playwright: 23 de 24** sobre base recreada (la que falta es la de Wompi, que pide llaves de
  *sandbox*), con el enlace corto del profesor abierto sin cuenta: 20 en la corrida completa y las 3 de la práctica al repetirlas, después de arreglar una
  carrera vieja de esa prueba (respondía el ejercicio de escucha antes de que el navegador decidiera
  que no tiene voz en inglés). Las pantallas nuevas, además,
  revisadas en capturas a 390 y a 1280.

Al 24/09/2026 por la noche, con el refinamiento del Bloque 11 (pasos 1–10):
- Backend: `./mvnw verify` — **373 unitarios + 564 de integración**, verde.
- Frontend: `tsc` + `lint` verdes; **115 tests de Vitest**.
- **E2E Playwright: 22 de 23** sobre base recreada, con el recorrido nuevo (cambia de pantalla, se
  retoma tras recargar y termina) y la clase de prueba gratis de punta a punta. El recorrido del
  profesor y del estudiante, la bienvenida, Ayuda y el video se compararon además a ojo con las
  capturas del paquete, a 390 y a 1280.

Al 24/09/2026 por la tarde, con la práctica construida desde el diseño de Claude Design (los diez
tipos, el cierre, el logro nuevo, «Mi cielo» y la vista del profesor):
- Backend: `./mvnw verify` — **363 unitarios + 528 de integración**, verde.
- Frontend: `tsc` + `lint` verdes; **106 tests de Vitest**.
- **E2E Playwright: 22 de 23** sobre base recreada, con la práctica recorrida entera en la pantalla
  nueva (incluidos «Casi…» y el segundo intento), el cierre con sus logros y lo que ve María.
  Además, cada tipo en cada estado comparado a ojo con las capturas del paquete, a 390 y a 1280.

Por la mañana, con el Bloque 10 afinado para el lanzamiento a profesores (ensayo, vista de los
ejercicios, bienvenida y recorridos, acta v3, práctica v4 con revisión):
- Backend: `./mvnw verify` (Testcontainers) — **344 unitarios + 519 de integración**, verde.
- Frontend: `tsc` + `lint` verdes; **90 tests de Vitest**.
- **E2E Playwright: 22 de 23**, con el recorrido del estudiante nuevo y el ensayo del acta desde
  Sistema. Además, el Bloque 10 entero recorrido en el navegador con OpenAI de verdad.

Al 23/09/2026, con el Bloque 10 completo (Partes A y B) y su revisión, eran 328 + 501, 77 de Vitest
y 21 de 22 (la última, el 23/09 con la portada nueva y el catálogo sin cuenta),
  sobre base recreada (`docker compose down -v`): el acta escrita,
  editada y publicada, la práctica de Ana con su cierre y lo que ve María, y el acta a mano con la IA
  caída. El que falta sigue siendo el paso por la pasarela: exige llaves de *sandbox* de Wompi en el
  entorno. Los del acta necesitan las clases que la semilla local cierra «ahora» (las únicas
  posteriores a la V48).

Al 22/09/2026, con la revisión de seguridad y la Parte A, eran 280 + 477, 50 de Vitest y 18 de 19.

Al 08/09/2026, con el Bloque 9 recién mezclado, eran 161 + 380 y 15 de 16. En esa tanda se
actualizaron las tres casillas del registro y la verificación de correo —que se hace por el camino
real, leyendo el enlace del buzón de Mailpit, y no marcando la cuenta por SQL—, y el `timeout` por
test subió a 60 s: estos tests manejan un `next dev` que compila cada ruta la primera vez, y fallar
por eso solo enseña a volver a correrlos.
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

## Portada de Sofía y catálogo sin cuenta (23/09/2026)

**La portada es la que propuso Sofía**, con las decisiones de Pardo encima (ver la memoria
`landing-sofia-decisiones`): hero con su titular y el **diagnóstico como acción principal** («Buscar
profesor» de segunda; «Quiero enseñar» sale del hero), buscador rápido, «Por qué ahora», tres pasos,
**Método ORION™** (™ y no ®: la marca no está registrada), «Aquí las reglas juegan a tu favor» en
dos pestañas, bloque del diagnóstico, preguntas frecuentes en dos pestañas, cierre y pie. Se quedan
«Conoce a los profesores» y «Nosotros»; se va su sección de reclutamiento (la pestaña «Quiero
enseñar» ya la cubre) y la rejilla de objetivos (la reemplaza el buscador).

- **Lo que la portada no promete**, porque el producto no lo hace: el diagnóstico dura lo que diga
  Ajustes (2 min) y da un punto de partida y tres profesores, no «tu nivel en 10 minutos»; no hay
  paquetes; el profesor aprueba el acta, no los ejercicios (salen de ella).
- **Cifras con fuente**: «hasta 24 % más probabilidad de un trabajo mejor pagado» y «2,2 % de quienes
  buscan empleo dice tener nivel alto», de Anif, British Council y Uniandes (2025) sobre datos del
  Servicio Público de Empleo 2019–2024. El «1 de cada 4 vacantes» del borrador no tenía fuente y el
  55 % que circula en prensa contradice el 5,74 % que la propia Anif midió en 2024.
- **Buscador rápido**: para qué (Trabajo = negocios y entrevistas, Viaje, Examen certificado,
  Conversación) y cuándo (Mañana, Tarde, Noche, Fin de semana = sábado y domingo), directo al
  directorio filtrado. Sin filtro de idioma: hoy solo hay inglés.
- **El catálogo y el perfil de un profesor se ven sin cuenta.** Ver horarios, reservar y escribir la
  siguen pidiendo, y al crearla o entrar se vuelve al mismo perfil (`?volver=`, solo rutas de la app).
  Es lo que hace funcionar el buscador de la portada y el enlace que un profesor comparte en sus redes.
- **Verificación de marca de Google** (rechazada el 23/09/2026 por dos cosas): la portada ahora dice
  qué es Orión Idiomas y qué datos toma el inicio de sesión con Google o Facebook (nombre, correo e
  identificador de la cuenta, nada más), y muestra el nombre «Orión Idiomas» —título, esa sección y
  el pie—, que es el que va en la pantalla de consentimiento de Google. **No quitar esa sección ni
  cambiar el nombre sin cambiarlo también en Google**: es lo que la verificación compara.
- El pie enlaza a la sección de cancelaciones de los términos (los documentos legales tienen ahora
  anclas por encabezado), y lleva Instagram, TikTok y LinkedIn, que también van al `sameAs` del
  JSON-LD.

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
- **Subtítulo y traducción** (23/09): el subtítulo se limpia al empezar cada turno de Meissa y
  la pregunta queda escrita mientras respondes; el contador dice «Pregunta N de 6». Debajo, la
  **traducción al español frase por frase mientras habla**, con `gpt-4.1-nano` (~1 s por frase,
  del orden de un peso por diagnóstico, cargado al presupuesto del diagnóstico), ocultable y
  recordado en el navegador. Solo para el dueño de un diagnóstico vivo y con tope de 80 frases;
  la rama en español no se traduce. Antes el subtítulo acumulaba la conversación entera: por
  WebRTC el evento con el que se detectaba el turno nuevo no llega nunca.
- **Guion v5 de Meissa** (23/09, pedido de Pardo): habla la mitad y pregunta en concreto,
  siguiendo el hilo de lo que la persona cuenta (a un ingeniero de software le pregunta por el
  último bug o por cómo verifica una migración). Medido contra el modelo con dos personas fijas:
  **~20 palabras por turno, contra 42 del v4**, y 24 en el saludo contra 44. Orión se nombra en
  el saludo, en la despedida y una vez en medio, cuando la pantalla le manda «[Orión now]» antes
  del cuarto turno: contando sola, lo nombraba en casi todos. A falta de 20 s la pantalla le manda
  «[20 seconds left]» y ella lo avisa en su siguiente turno, sin interrumpir; a los dos minutos,
  «[Time is up]»: se despide en cuanto nadie esté hablando y la pantalla cierra al terminar la
  despedida (corte duro a los 25 s si no llega). Un test comprueba que las tres notas digan lo
  mismo en la pantalla y en el guion. **El juicio final es de oído**: la prueba fue en texto.
- **Webhook de JaaS** (V46): 8x8 cuenta quién entró a cada sala, quién sigue dentro y cuánto habló
  cada uno. La antesala ya dice «María te espera» de verdad; el profesor ve en la ficha del
  estudiante qué parte de la palabra tuvo en sus últimas clases juntos; el admin ve en
  Administración → Aula el reparto de la palabra y la puntualidad de cada profesor (informativo:
  no genera sanciones). Firma HMAC verificada sobre el cuerpo tal cual, ventana de cinco minutos,
  idempotente por la llave de 8x8, y lo que no es de una reserva nuestra o de sus dos participantes
  se ignora.
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
  **Microsoft** (23/09/2026, en lugar de Facebook, que Pardo descartó por lo difícil de configurar):
  Outlook, Hotmail, Live y cuentas de trabajo o universidad, por el extremo «common». Su correo
  **nunca cuenta como verificado** —Microsoft no lo garantiza—, así que la cuenta nueva recibe el
  correo de verificación y una existente no se vincula sola; y como el emisor del token cambia con
  cada inquilino, `EmisorDeMicrosoft` lo comprueba contra el `tid` del propio token (V50 admite
  `MICROSOFT` en `social_identities`). Su secreto caduca: renovarlo en Entra antes de la fecha.
  **Requiere en Railway**, por proveedor: `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET`;
  `MICROSOFT_CLIENT_ID` y `MICROSOFT_CLIENT_SECRET`;
  `FACEBOOK_CLIENT_ID` y `FACEBOOK_CLIENT_SECRET`; `APPLE_CLIENT_ID` (Services ID),
  `APPLE_TEAM_ID`, `APPLE_KEY_ID` y `APPLE_PRIVATE_KEY` (el .p8). La dirección de vuelta que se da
  de alta en cada consola es `https://orionidiomas.com/login/oauth2/code/{google|microsoft|facebook|apple}`.

## Acta de clase · Bloque 10, Parte A (22/09/2026)

Brief en [`briefs/orion-bloque-10-acta-y-practica.md`](./briefs/orion-bloque-10-acta-y-practica.md).
Solo la Parte A: la práctica entre clases (Parte B) no está construida, y por eso el botón
«Practicar esto» no existe.

- **El profesor cuenta la clase en una caja de texto** («trabajamos past simple, sigue diciendo
  'I go yesterday'…», mínimo 20 caracteres) y `gpt-5-mini` la ordena en cuatro secciones: lo que
  trabajaron, para tener presente, palabras nuevas (máximo 12) y lo que sigue. La salida se valida
  —JSON con exactamente esas claves, dentro de sus límites y sin juicios sobre el estudiante— y un
  reintento; si falla, tarda más de `ai_note_timeout_seconds` o no hay presupuesto, aparecen los
  mismos campos vacíos con sus notas guardadas. Nunca un error técnico.
- **Nada llega al estudiante sin que el profesor publique.** Un borrador es 404 para él. Publicado,
  lo lee sin las notas en crudo, sin el origen y sin cuánto se corrigió (`edit_ratio`, la cifra que
  dirá si el prompt ayuda o estorba). Se puede corregir durante `lesson_note_edit_window_hours`
  (72) y el estudiante ve «Actualizada el…».
- **Avisos**: notificación en la app al publicar (sin correo) y **un** recordatorio al profesor
  `lesson_note_nudge_minutes` (60) después de cerrarse la clase, si no escribió el acta. Nunca
  insiste.
- **Sin actas retroactivas** (D4): solo las clases cerradas desde que se aplicó la V48.
- **Presupuesto propio**, `ai_daily_budget_cop` (30.000), aparte del diagnóstico, con aviso al 80 %.
  Apagarlo (`ai_lesson_notes_enabled`) no quita la función: el profesor la escribe a mano.
- Pantalla: `/mis-clases/{id}/acta`. Lleva a ella el cierre del aula (al marcar que el estudiante
  asistió, mientras la clase está fresca; «Ahora no» vuelve a Mis clases), la tarjeta de la clase
  cerrada («Contar cómo estuvo», «Terminar el acta», «Ver el acta»; el estudiante, «Resumen de la
  clase» cuando hay uno publicado) y las notificaciones. Qué tarjeta admite acta lo dice el
  servidor (`/me/lesson-notes/summary`), con el mismo criterio con que la acepta.
- En «Mis clases → Pasadas», arriba, la lista: al profesor, «Actas por escribir» (o «Estás al
  día»); al estudiante, sus resúmenes (o «Todavía no hay resúmenes»). Sale de `/me/lesson-notes/index`.
- **Dictado del profesor** (añadido por Pardo; el brief lo dejaba fuera): botón «Dictar» en la
  caja de «Cuéntanos cómo estuvo». Graba hasta tres minutos en el navegador, el audio va a
  `gpt-4o-mini-transcribe` (~10 pesos el minuto, al presupuesto del acta) con una pista que le
  dice que espera español con términos en inglés, y el texto se suma a la caja para revisarlo
  antes de generar. **El audio no se guarda.** Mismas puertas que el acta (solo el profesor de la
  clase y solo clases cerradas), 40 al día por profesor, y depende solo del tope: apagar el
  borrador con IA no le quita el micrófono. Probado contra OpenAI con un dictado sintetizado:
  transcripción exacta, «I go yesterday» y «used to» incluidos, en 1,5 s. **La política de datos
  debería decir que la voz del profesor va a OpenAI** (hoy no nombra a OpenAI en absoluto).
- **Panel de calidad (C1)**: en Administración → Panel, la fila «Acta de clase»: publicadas hoy,
  **% de clases cerradas con acta** (30 días), **cuántas se reescriben** (cortes de `edit_ratio`:
  sin editar hasta 0,05, reescrita desde 0,5; si las reescritas pasan de la mitad, se revisa el
  prompt antes de ampliar), el gasto de hoy contra el tope y las llamadas al proveedor por
  resultado. El profesor ve en su Desempeño cuántas de sus clases de los últimos 90 días tienen
  acta, con el aviso de que no cuenta para el buscador ni para sanciones.
- Frontera: fuera de `teaching` solo se importan sus eventos (`FronterasDeTeachingTest`).
- Pruebas del paso C2 completas (23/09): el redactor contra un servidor local que tarda (fila
  `TIMEOUT`, sin reintento), que devuelve algo que no es JSON (un reintento y dos filas
  `INVALID_OUTPUT`) o sin presupuesto (no llama a nadie); el tope del día apaga la IA; ninguna
  pantalla, aviso ni prompt del acta usa las frases prohibidas; y en el navegador, el profesor
  corrige una sección antes de publicar y, con la IA apagada, escribe el acta a mano y la publica.
  La prueba del corte mostró que con `SimpleClientHttpRequestFactory` el corte **sí corta** a
  tiempo, pero llega como un error al leer la respuesta y no como corte: el registro de gasto
  anotaba `ERROR` donde era `TIMEOUT`. Los tres clientes de OpenAI (acta, resumen y traducción del
  diagnóstico) usan ahora el cliente HTTP del JDK, que lo informa como corte. (La primera lectura
  de la prueba, «el corte no cortaba», era errónea: se corrigió con un experimento aislado.)
- El borrador lo arma `gpt-5-mini` solo con `ORION_VOICE_PROVIDER=openai` y `OPENAI_API_KEY`, las
  mismas del diagnóstico; sin ellas (local y tests) lo arma una regla simple sin red: las notas
  enteras en «lo que trabajaron» y cada término entre comillas como palabra nueva. Cuál de los dos
  está activo se ve en Administración → Sistema, «Borrador del acta (OpenAI)».

## Práctica entre clases · Bloque 10, Parte B (23/09/2026)

Pardo decidió desplegarla **encendida** y que **practicar también avance la racha** (el brief
pedía confirmarlo con Sofía; lo decidió él).

- **Nace del acta publicada** (V49, `practice_sets` y `practice_items`): publicar encola un set
  `PENDING` con lo que el acta decía ese día (el evento del acta trae su contenido; la práctica no
  importa nada de `teaching` salvo sus eventos). Un trabajo cada minuto lo genera y lo deja
  `READY`; nunca se genera al abrir la pantalla. Un acta, un set, para siempre.
- **Cinco tipos** (completar, corregir, emparejar, ordenar un diálogo, frase propia), generados
  por `gpt-5-mini` y **anclados al acta**: lo que no sale del vocabulario, de los errores
  recurrentes o de lo trabajado se descarta (`ValidadorDeEjercicios`), como mucho dos del mismo
  tipo, y con menos de dos ejercicios el set queda `FAILED` y no se ofrece nada. Sin IA (local y
  pruebas), un generador determinista que solo usa lo literal del acta.
- **Prompt v2 (23/09).** Con v1, doce generaciones contra OpenAI eligieron siempre los mismos
  cuatro tipos: el diálogo no salió nunca. Ahora la entrada dice qué tipos pedir —los que el acta
  alcanza a anclar, rotando por clase el que se queda fuera—, un diálogo que llega ya en orden se
  desordena en vez de perderse, y la pista entre paréntesis de un término («get used to (+ing)») no
  impide anclarlo. Con v2: los cinco tipos en seis actas, 23 de 24 ejercicios válidos. En el
  navegador, con OpenAI de verdad: acta en 3,9 s, set listo 9 s después de publicar, y el diálogo
  —que ningún estudiante había visto— se ordena y se comprueba bien.
- **Reglas**: solo su dueño opera el set (404 a cualquier otro), dos intentos por ejercicio
  (después se muestra la respuesta con su explicación), un set vencido no acepta respuestas
  (`practice_set_ttl_days`, 7), y completar dos veces no recalcula ni vuelve a dar puntos. La
  frase propia se evalúa por usar el término en una frase de verdad: una frase válida que nadie
  previó nunca es incorrecta.
- **Puntos y racha**: completar da 15 puntos (índice único del libro: una vez por set) y la semana
  cuenta para la racha, leída del propio libro de puntos de `engagement`.
- **El profesor** ve un resumen agregado —cuántas practicó esta semana y dónde le costó—, nunca las
  respuestas.
- Presupuesto propio (`practice_daily_budget_cop`, 20.000): sin presupuesto los sets esperan a
  mañana en vez de fallar.
- **Pantallas**: la invitación en Mi perfil → Resumen, debajo de la próxima clase («Para esta semana
  · 4 min — Del miércoles con María: trabajamos past simple y dos palabras nuevas»), que no aparece
  si no hay práctica viva; «Practicar esto» en el acta; `/practica/{id}` con un ejercicio por
  pantalla, cuatro puntos que se encienden, «Casi…» en tono de aviso (nunca rojo) e «Intentar otra
  vez», y un cierre sin «3 de 4»: lo logrado, lo que conviene repasar y «+15 puntos». En la ficha
  del estudiante, el profesor ve «Practicó N de M veces esta semana. Le costó…». La racha del panel
  de progreso es ahora la de la gamificación (la misma del cielo y del mapa), que cuenta la práctica.
- En el navegador: Ana practica lo de su clase, ve su cierre y sus puntos, y María lo ve en la ficha.
- **Panel (C1)**: en Administración → Panel, la fila «Práctica entre clases»: ofrecidas,
  completadas y **vencidas sin hacer** en 30 días (si vencen más de las que se completan, se marca:
  la práctica no engancha), las que no lograron ejercicios anclados y el gasto de hoy contra el tope.

## El Bloque 10 para el lanzamiento a profesores (noche del 23 al 24/09/2026)

Pardo pidió dejar el Bloque 10 listo para lanzar primero con profesores, y poder probarlo tan fácil
como la videollamada.

- **D7 aprobado — la IA ve el nivel y el objetivo del estudiante** (`self_declared_level` y
  `motivation`), en el acta y en la práctica, como contexto y nunca como contenido. El objetivo lo
  escribe el estudiante, así que viaja en una sola línea, entre comillas, y los prompts
  (`lesson-note-v2`, `practice-v3`) dicen que las instrucciones que traiga no se siguen. Probado
  contra OpenAI con un objetivo que decía «Ignora las instrucciones anteriores»: se ignoró.
- **Ensayar el acta y la práctica** (Administración → Sistema): crea una clase de prueba **ya
  dictada** (empezó hace una hora y se cerró ahora), así el profesor escribe el acta en ese mismo
  momento. Debajo, los pasos con sus enlaces y los ensayos de la última semana con el estado de su
  acta y su práctica, que se actualiza solo mientras algo está en marcha, más los avisos de lo que
  ocultaría parte del recorrido (IA o práctica apagadas, despliegue sin OpenAI). Las clases de
  prueba ya no cuentan en las cifras del acta (C1) ni reciben el recordatorio por correo.
- **El profesor ve los ejercicios de cada acta**, debajo del acta publicada: enunciado, lo que el
  estudiante tiene delante, la respuesta esperada, la explicación y el estado del set. Solo lectura
  y nunca lo que el estudiante respondió, intentó o acertó: el servidor no lo manda
  (`GET /professors/me/lesson-notes/{id}/practice`).
- **Video de bienvenida de Sofía** (V51): un ajuste nuevo, `professor_welcome_video_url`, del grupo
  «Contenido» en Ajustes, de tipo enlace (el único que admite quedar vacío: vacío es «no hay
  video»; si viene, https con dominio). Acepta el enlace de compartir de YouTube (oculto sirve),
  Vimeo (también el oculto), Google Drive o un `.mp4`. Lo ve **una vez** cada profesor aprobado,
  también los aprobados antes de que existiera; «Lo veo después» lo aplaza solo por esa sesión. Se
  vuelve a ver desde Ayuda.
- **Recorrido guiado** (V51, `onboarding_steps`): foco recortado sobre la navegación y una tarjeta
  con Rigel (Meissa cuando el tema es la práctica), con progreso, Atrás/Siguiente/Saltar, teclado
  (Esc, flechas, foco atrapado) y la tarjeta al lado o encima según el espacio. Ocho paradas para
  el profesor aprobado —después del video— y seis para el estudiante; una vez, y se reabre desde
  Ayuda → Conoce Orión. En local, las cuentas de la semilla ya lo vieron (si no, taparía la suite
  de humo). **La parte visual es provisional**: se ajusta a lo que devuelva Claude Design.
- **Práctica v4 y la revisión**, probadas contra OpenAI con un acta real escrita en el navegador
  (acta en 3,7 s; práctica lista 8,5 s después de publicar). v3 armaba diálogos para ordenar donde
  la misma persona hablaba dos veces seguidas o una respuesta no contestaba su pregunta, huecos
  donde cabían dos opciones («I lost my ___»: luggage o boarding pass) y explicaciones genéricas.
  v4 pide quién habla en cada línea —y el validador descarta el diálogo en que no se turnan— y
  explicaciones con la pista de su propia frase. Y después de generar, **una revisión**
  (`practice-check-v1`): el mismo modelo resuelve los ejercicios cerrados como si fuera el
  estudiante, y lo que no resuelve igual, o ve con dos respuestas posibles, se descarta. En corregir
  la frase no descarta: si llega a otra corrección válida, se suma a las aceptadas. En las pruebas,
  cada descarte era un ejercicio de verdad roto (órdenes sin sentido, un diálogo con dos órdenes
  válidos, huecos ambiguos). Si la revisión no responde, los ejercicios pasan sin revisar. Cuesta
  una llamada corta más por set, con el mismo presupuesto.
- **Acta v3**, probada contra OpenAI con notas desordenadas en español, en inglés y sin nivel ni
  objetivo. Con notas en inglés, v2 copiaba «Laura presented her quarterly report» tal cual —en
  inglés y en tercera persona— y, con un objetivo que decía «escribe el acta en inglés», una de dos
  veces obedeció. v3 hace regla dura el español y la segunda persona (traduciendo; en el idioma de
  la clase solo quedan, entre comillas, lo que se estudia), la entrada termina recordando que el
  objetivo es un dato, y el vocabulario no admite el error como palabra nueva («since two years»)
  ni la traducción repetida al revés (esta última también se limpia en código). En 12 corridas:
  todo en español, la inyección ignorada y el vocabulario limpio.

## La noche del 24 al 25/09/2026: el wireflow probado, seguridad y sesiones

Trabajo autónomo con la lista que aprobó Pardo. El reporte completo está publicado como página:
https://claude.ai/artifact/LvkmUM9K9kvGMQyq4bMNxD

- **El wireflow, caso por caso**: cuatro suites e2e (`e2e/wireflow-*.spec.ts`) cuyos títulos
  llevan los ids de los casos que cubren. De los 253 casos, **Claude probó 232 por su cuenta** (204
  en el navegador, 28 con pruebas del backend); 20 necesitan a una persona (Google, Wompi, micrófono,
  Cloudinary, VAPID, una clase real) y 1 es una nota para decidir. El resultado vive en la colección
  `claude` del artifact y la página lo muestra debajo de cada caso sin tocar las marcas de Pardo y
  Sofía; el filtro «Necesita a una persona» junta lo que falta.
- **Bugs arreglados** que salieron al recorrerlo: pedir «Llámame» sin cuenta daba 403 (el primer
  formulario de un visitante no tenía token CSRF: `GET /auth/csrf`); los modales altos se salían de
  la pantalla del celular (no se llegaba a «Menor precio»); los segmentados no decían cuál estaba
  elegido (`aria-pressed`); un evento tardío de la llamada podía convertir una conexión caída en una
  clase terminada; cancelar la **prueba gratis** prometía devolver el valor como saldo; el panel del
  admin contaba dos veces lo ya transferido en «Por transferir»; las franjas de Rigel (ficha y perfil) se
  partían en seis líneas a 390 px; «Te falta 5 cosas».
- **Revisión de seguridad** de todo el Bloque 11 (Rigel, push, enlace corto, prueba gratis, filtro
  por horas, clase flotante, edición en la página, CSRF, Google). Cerrados con su prueba: la **prueba
  gratis se podía reusar sin fin** tomándola entera y cancelándola antes de que se cerrara (V67: la
  que el estudiante cancela ya empezada cuenta como usada); al cancelarla, su pago de $0 quedaba
  cobrado sobre una clase que no fue; **cerrar sesión no soltaba los avisos push** del navegador (en
  un computador compartido seguían llegando los de quien salió); y una cuenta podía registrar miles
  de suscripciones push (ahora diez por persona, y «Probar» cinco por hora). Rigel, el enlace corto,
  el filtro por horas, el aula y el CSRF salieron limpios.
- **Las sesiones sobreviven a los despliegues** (Spring Session JDBC, V68): viven en Postgres y no en
  la memoria del proceso. `User` es serializable con `serialVersionUID` fijo, y una sesión guardada
  que un despliegue deje ilegible se lee como vacía —la persona vuelve a entrar— en vez de dar un 500
  (`SesionesEnLaBase`). La cookie sigue siendo `ORION_SESSION` y el tiempo de inactividad, 30 min.
  Un 401 anónimo ya no abre sesión (sin «petición guardada»): antes cada visitante sin cuenta
  habría dejado una fila.
- **El dinero, dicho igual en todas partes**: en «Ganancias», la clase de un pago ya liquidado dice
  «En camino» o «Transferido» (decía «Por cobrar»), y una devolución en curso ya no sale como
  `REFUND_PENDING`; en «Pagos» del admin cada fila dice su estado en español. Las tres tablas
  (estudiante, profesor, admin) viven en `lib/estadosDePago.ts` y una prueba exige que cubran todos
  los estados del backend. Las preguntas frecuentes leen de Ajustes el plazo para pagar (decían «20
  minutos» fijo).
- **Carrera al crear la cuenta**: el navegador lanza varias peticiones a la vez con la cookie del
  diagnóstico sin cuenta, y cada una mudaba lo mismo; ahora el reclamo es un `UPDATE` condicional
  y solo muda quien lo gana.
- **Pruebas nuevas del backend** para los casos que solo él puede probar: la comisión nueva no toca
  lo ya reservado, confirmar o descartar una sanción propuesta, el tope de «Llámame» y el panel
  cuadrando con las liquidaciones.

## Refinamiento · Bloque 11 (24/09/2026)

Un pedido largo de Pardo, con sus respuestas a tres preguntas; el brief y la auditoría del recorrido
están en [`orion-bloque-11-refinamiento.md`](./briefs/orion-bloque-11-refinamiento.md). La lista de
**todos los flujos para probarlos** está publicada como página: https://claude.ai/artifact/DsCqoUx3mT15GnmEk5AviM

- **Navegación y perfil**: «Mis clases» también para el estudiante, «Ver clases pasadas» debajo de
  la lista, fuera «Otro horario». «Mi ficha» y «Mis datos» son una sola sección que separa lo que ven
  los profesores, quién más la ve y lo que es solo tuyo; fuera «Si hay que cancelar» y «Preguntas»
  del perfil. El Confidence Score va al final del resumen.
- **Google**: el `state` y el registro a medias viajan en cookies firmadas (sobreviven a un
  despliegue y a dos pestañas), `www` redirige al dominio, los navegadores de Instagram o TikTok
  avisan, y cada fallo tiene nombre. Las cuentas de Google pueden **crear** su contraseña (V56).
- **Saludo al reservar** (V57): a nombre del profe, con ⭐, en el chat del estudiante y marcado
  «Enviado por Orión»; uno por reserva por índice. Reservar y cancelar llegan también a la campana.
- **La ficha con énfasis** (V58): franja de Rigel en todas las pantallas (se cierra por un día),
  campana el día 1 y 3, correo el día 2, logro «Ficha completa» (+25).
- **Avatar**: dos opciones de cada cosa desde el primer día (V59).
- **Puntos** junto al nombre en todas partes (solo estudiantes) y «Tus puntos» con lo último y cómo
  se hacen. Fuentes nuevas: primer mensaje a cada profe, llegar a tiempo al aula, ficha visible,
  recorrido y diagnóstico. **Arreglado**: calificar una clase nunca daba sus 20 puntos (nadie
  llamaba a `onReviewCreated`); ahora la reseña publica un evento.
- **Recordatorios y avisos** (V60): la clase de mañana (campana y correo a los dos), una hora antes
  (campana y dispositivo), calificar al terminar y al día siguiente; práctica lista, reseña recibida,
  reserva vencida, respuesta de soporte, sanción aplicada o levantada y liquidación pagada. Sistema
  manda un **correo de prueba** real por el transporte de producción.
- **Avisos en el dispositivo** (Web Push, V61): opt-in desde la campana, cifrado RFC 8291 y VAPID
  solo con el JDK, y solo hacia servicios de push conocidos (sin SSRF). Suenan lo urgente y lo
  esperado; logros y confirmaciones se quedan en la campana.
- **Bienvenida y recorrido idénticos al diseño**, y el recorrido **cambia de pantalla** en cada paso.
- **Clase de prueba** (V62, Q7; desde la tercera tanda, **siempre gratis**, ver abajo): precio del profe (0 = gratis, o entre `trial_min_price_cop` y su
  tarifa), la misma comisión, una por pareja por índice, solo para quien aún no tiene clases con
  él. La marca vieja de los ensayos del admin pasó a llamarse `is_rehearsal`.

**Segunda tanda** (pasos 12–18 del brief, pedidos el mismo día):
- **Escritorio**: las pantallas usan el ancho como «Mensajes» —del lateral al borde, hasta `5xl`—
  en lugar de una columna angosta al centro.
- **Recordar la práctica sin invadir** (V64): una tarjeta compacta en «Mis clases» y en «Buscar
  profesor» mientras haya un set listo o a medias, que se oculta con su ✕ hasta el siguiente set; y
  **un solo** aviso en la campana, dos días después de estar lista, si nadie la empezó y le queda
  tiempo (`practice_sets.reminded_at`). Sin correo ni aviso al dispositivo.
- **País con lista y bandera** en la postulación y en el perfil del profesor: todos los países del
  mundo con su nombre en español (`Intl.DisplayNames`) y la bandera como emoji, los más frecuentes
  arriba. Se sigue guardando el código ISO. Windows no dibuja banderas como emoji: ahí sale el
  código de dos letras, y el nombre igual.
- **La franja para el profesor aprobado**, igual a la de la ficha del estudiante: foto, titular,
  descripción, tarifa, idiomas, horarios y publicar (`GET /me/profile/pending`, lo decide
  `scheduling.PerfilDelProfesor`). Franja de Rigel en todas las pantallas menos su perfil (se cierra
  por un día), campana el día 1 y 3 contados desde la aprobación y correo el día 2 (reusa
  `profile_reminders` de la V58). Si ya está publicado y con horarios, el texto pide terminar el
  perfil en vez de decir que «no recibe estudiantes».
- **Horarios dentro del perfil**: «Mi perfil» tiene dos pestañas, «Perfil público» y «Mis
  horarios»; `/disponibilidad` redirige a la segunda (los enlaces y correos viejos siguen sirviendo).
  En la barra del celular «Horarios» se cambió por **«Desempeño»**, que no tenía entrada ahí.
- **Rigel en el hero del celular**, lo primero que se ve, antes del titular.
- **«Invitar estudiantes»** (V63): un enlace corto propio (`/p/maria-gomez`) que lleva a su perfil
  público —se ve sin cuenta, y al crearla para reservar vuelve a él—, un mensaje ya escrito que se
  puede cambiar (menciona la clase de prueba si la ofrece) y botones para WhatsApp, Facebook,
  LinkedIn, X, Telegram, correo, compartir desde el celular y copiar. Instagram y TikTok no dejan
  compartir un enlace desde afuera: se copia el mensaje.

**Tercera tanda** (pasos 19–25, esa noche):
- **La clase de prueba es GRATIS** si el profesor la ofrece (V65): un interruptor, «Ofrezco la
  primera clase gratis», sin precio; se confirma en el acto, sin pasarela ni comisión. Regalar una
  hora es decisión del profesor, así que nace apagada: la V65 la deja encendida solo a quien ya la
  había puesto en 0. Se fueron `trial_price_cop`, `trial_min_price_cop` y `PUT /me/profile/trial`.
- **Las estrellas de «Mi ficha»** se explican solas: «Tus últimos logros», con el nombre y la
  familia de cada una (el color es el de la familia), y llevan a Mi cielo.
- **Filtrar por horas exactas, varias a la vez**: chips de 5 AM a 11 PM; «Mañana · todas» marca o
  desmarca las suyas. El backend recibe `hour=HH:00` repetible: una hora es el cupo en punto, así que
  la clase entera tiene que caber en la franja publicada. `schedule=` de la portada sigue sirviendo.
- **Editar sin «modo edición»**: los perfiles (profesor, ficha y datos del estudiante) se editan
  directo; al cambiar algo aparece una barra fija «Tienes cambios sin guardar · Descartar · Guardar
  cambios», y salir con cambios pregunta (los enlaces de la app se atajan en captura). La tarifa del
  profesor entra en el mismo guardado, antes que el resto (publicar exige tarifa).
- **Mensajes de Rigel** (V66): un hilo fijo arriba de «Mensajes», de solo lectura, con la
  bienvenida (al profesor, cuando ya está aprobado), la primera reserva y la primera clase de cada
  lado —una vez cada uno, por índice— y «¿Seguimos?» al estudiante sin clase en dos semanas, a lo
  sumo una vez al mes. Botones a rutas de la app; sin campana, correo ni push: solo el número sin
  leer de «Mensajes». La bienvenida se deja al abrir el hilo, así llega también a quien ya existía.
- **La clase sigue al salir del aula**: la videollamada vive en el armazón (`ClaseEnCurso`), en un
  contenedor fijo que no se mueve del DOM (mover un iframe lo recarga). Minimizar, «atrás» o el menú
  la encogen, deslizándose, a una ventana flotante arrastrable con micrófono, cámara, volver y colgar;
  volver la agranda sin reconectar. En el aula, la barra «Quedan X min» con una estrella que avanza y
  el aviso «María entró a la clase». Recargar o salir de la zona autenticada sí corta (se advierte).
- **Arreglado al capturar el wireflow**: el login decía «Invalid credentials» y la validación
  «Validation failed», en inglés.

## La práctica, rediseñada y gamificada (24/09/2026)

Pardo pidió ejercicios más interactivos, cinco distintos por set y en varias categorías, correcciones
y logros muy gamificados, y que el profesor vea lo que hizo su estudiante. La parte visual la hizo
Claude Design (paquete `design_handoff_orion_practica`, «Diseño Acta y Ejercicios.zip») y está
construida al píxel —ver la sección siguiente—; esta es la lógica.

- **Diez tipos en cinco categorías** (V52): Palabras (Parejas, Completa), Frases (Corrige, Caza el
  error, Arma la frase), Conversación (Ordena la conversación, Responde en el chat), Escucha
  (Escucha y elige, Escucha y escribe) y Tu turno (Tu frase). Cada set trae **cinco ejercicios de
  cinco categorías**, uno de cada tipo, en ese orden. `practice_items_per_set` pasó de 4 a 5.
- **Escucha con la voz del dispositivo** (síntesis de voz del navegador: no cuesta nada), normal y
  más despacio. Sin voz en inglés, el ejercicio se **salta sin contar** como acierto ni fallo
  (`skipped_at`). En «Escucha y escribe» se perdona una letra.
- **Generación v5**: el código elige los tipos —uno por categoría que el acta alcanza a anclar,
  rotando según la clase— y pide dos de repuesto, para que la revisión o el validador puedan
  descartar sin dejar el set en cuatro. La revisión (`practice-check-v2`) resuelve también los tipos
  nuevos. Contra OpenAI, en tres actas distintas, cada ronda dio cinco de cinco categorías.
- **Gamificación** (V53):
  - portada del set con Rigel y la constelación apagada;
  - Rigel que reacciona en cada ejercicio (señala, espera, celebra, anima);
  - la racha dentro del set («¡Tres seguidas!»);
  - el cierre con la constelación completa;
  - **+15 puntos**, y **+5 por constelación perfecta** (todo al primer intento).
  - Familia nueva de logros **«Práctica»** en «Mi cielo»: Primera constelación, Constelación
    perfecta, Cinco y Veinte constelaciones, Oído fino (10 de escucha acertados) y Segunda
    oportunidad (10 al segundo intento). engagement guarda lo que necesita en su propia tabla
    (`practice_tallies`), alimentada por el evento: sigue sin leer las tablas de la práctica.
- **El profesor ve lo que hizo su estudiante** (V54; decisión de Pardo que cambia el «nunca las
  respuestas» del brief, B5.4):
  - debajo del acta, «Cómo le fue a Ana»: cuántos al primer intento, al segundo, mostrados y
    saltados, y cada ejercicio con su primera respuesta, la segunda y el resultado;
  - en la ficha del estudiante, el historial de prácticas.
  - **El estudiante lo sabe**: la portada de su práctica dice «María verá cómo te fue, así
    prepara tu próxima clase».

### El diseño, construido (24/09/2026)

Cada set es una **constelación de cinco estrellas**: cada ejercicio enciende la suya y al terminar
se dibujan las líneas. Comparado lado a lado con las capturas del paquete, a 390 y a 1280.

- **La pantalla de ejercicio**: los diez tipos con todos sus estados (inicial, interactuando,
  comprobando, «Así es.», «Casi…» con su pista, mostrada, sin voz y saltado), hechos con las piezas
  del paquete —ficha, hueco, tarjeta de pareja, burbujas de chat, reproductor, panel de respuesta,
  botón principal, chip de categoría— y la constelación con sus cuatro formas (cada set tiene la
  suya, sacada de su id). Rigel acompaña con tres poses nuevas (racha, atento, sello); en Escucha
  habla Meissa, nunca los dos juntos. En el celular la práctica va a pantalla completa, sin la barra
  de abajo; en escritorio queda el lateral de la app.
- **Lo que rodea al juego**: el inicio del set, la transición entre ejercicios (con la racha
  «¡Tres seguidas!»), «Salir y seguir luego» con el aviso al volver («Sigues donde ibas. Tus dos
  primeras estrellas ya están encendidas.»), y los estados preparándose y vencida. La invitación,
  en el perfil y al pie del resumen de la clase, en sus cuatro momentos.
- **El cierre** en el amanecer: la constelación que se dibuja, los puntos que se cuentan solos (solo
  las líneas que ocurrieron: el set, el bono de perfecta y cada logro que encendió), lo que logró,
  lo que conviene repasar, la racha semanal y «María ya puede ver cómo te fue».
- **El logro nuevo** es ahora un momento reutilizable en toda la app: el sello se estampa, con su
  nombre, lo que lo encendió y sus puntos, y se sigue con un botón; si son varios, van en cola. Los
  de Práctica llevan su sello dorado (tres estados). **«Mi cielo»** muestra arriba las constelaciones
  completas y abajo los logros por familia, en pestañas.
- **El profesor**: el acta publicada se lee (con «Hecha con IA», hasta cuándo se puede corregir y
  «Corregir»), los errores se leen tachados → en negrita, y el borrador se edita con el aviso de la
  IA. Debajo, «Cómo le fue a Ana» con el estado del set, los contadores, «Le costó» y una tarjeta por
  ejercicio (lo que vio, lo que respondió en cada intento, lo que se esperaba). En la ficha del
  estudiante, «Practicó 2 de 3 veces este mes» y el historial con la constelación de cada set.
- **Backend que pidió el diseño**:
  - la pista del «Casi…» aparte de la explicación (V55, prompt v6, con un filtro que cambia las
    pistas que dan la respuesta);
  - Parejas se une par por par (un par que no va gasta un intento);
  - «Tu frase» la revisa la IA (¿es una frase de verdad en inglés con el término?) y trae un ejemplo
    para mostrar si no sale (prompt v7);
  - I'm = I am al corregir y en el dictado;
  - el set que se prepara ya se le ofrece al estudiante;
  - el acta del profesor dice con quién fue la clase, cuándo, y hasta cuándo se puede corregir.
- **Decisiones propias**, para que Pardo las confirme:
  - Rigel conserva la mano que Pardo aprobó (sin el pulgar suelto del diseño).
  - En el dictado Meissa no muestra la frase mientras suena: sería dar la respuesta.
  - En Ordena y en Arma la frase, «Casi…» marca toda la respuesta en ámbar, no solo lo que está
    mal: el servidor no dice qué posiciones fallaron mientras quedan intentos.
  - En el segundo intento la pista se queda a la vista, y no se puede comprobar sin cambiar algo.
  - En Responde en el chat se elige la respuesta y luego «Comprobar», como en las capturas.
  - La hora va como en el resto de la app («8:12 PM»), no «8:12 p. m.».
  - «Mi cielo» usa las seis familias reales; el diseño mostraba cuatro.

## Revisión de lo construido en la noche del 22 al 23/09/2026

Una revisión de solo lectura de todo lo de esa noche (traducción, v5, dictado, C1, Parte B)
encontró diez defectos, y buscar el patrón del primero por todo el backend encontró otros tres que
venían de antes. Todos quedaron arreglados, cada uno con su test. Los más serios:

- **Las dos retenciones del diagnóstico nunca habían corrido.** `LeadRetentionJob` y
  `TranscriptRetentionJob` tenían la transacción en el método que llamaba el propio `run()`, y una
  llamada dentro de la misma clase se salta el proxy de Spring: cada madrugada morían con «No active
  transaction for update or delete query». Ni se borraban los leads sin reclamar a los 30 días ni
  se limpiaban las transcripciones al vencer el plazo o al revocar el consentimiento. Los tests
  llamaban a `purgar()` directamente y por eso pasaban. Ahora la transacción va por
  `TransactionTemplate`, y los tests entran por `run()`, como el programador de tareas. **La
  primera corrida en producción se pone al día sola** (borra todo lo vencido de una vez).
- **El cierre automático de clases** tenía el mismo defecto, y es el job del que depende que el
  profesor cobre. El cierre y la liberación del pago iban cada uno en su transacción —si la segunda
  fallaba, la clase quedaba cerrada con el dinero retenido y el job ya no volvía a pasar por ella— y
  `LessonCompletedEvent` salía sin transacción: una clase cerrada por el job, y no por el profesor,
  no le daba sus puntos al estudiante. Ahora cada clase se cierra en una sola transacción, como
  decía su comentario. Se buscó el mismo patrón en todo el backend: no queda otro caso.
- **El recordatorio del acta** tenía el mismo defecto: marcaba la clase como recordada y el aviso
  —que sale después del commit, y sin transacción no hay commit— se perdía. Como no insiste nunca,
  no le llegaba a ningún profesor.
- **Una caída de OpenAI ya no deja sin práctica a nadie.** Un timeout o un 5xx contaba como intento
  de generación, y con el trabajo cada minuto, tres minutos de caída marcaban FAILED todos los sets
  pendientes. Ahora el set sigue pendiente sin gastar intento, la corrida se detiene, y el gasto se
  escribe en su propia transacción (antes un rollback lo borraba y el tope no lo veía).
- **Práctica**: la frase propia con un término con guion (`check-in`, `T-shirt`) o con pista entre
  paréntesis ya se puede acertar; «emparejar» se descarta si el modelo parafrasea un significado
  (no habría forma de acertar); en «corregir», las otras correcciones válidas ya no viajan mientras
  el ejercicio está abierto; y terminar un set exige haber respondido todos (422): los puntos y la
  semana de racha son por practicar, no por llamar al endpoint.
- **Pantallas**: la invitación dejaba ver un set ya terminado hasta recargar (el 204 de
  `/me/practice` no es un dato para TanStack); un dictado largo podía pasar las notas de 2.000
  caracteres y «Generar acta» fallaba sin decir por qué; salir a mitad de un dictado igual lo subía
  y lo cobraba.
- **Traducción de Meissa**: una frase en inglés con un nombre o un lugar con tilde («Tell me more,
  Sofía», «the traffic in Bogotá») se tomaba por español y no se traducía.
- **Una segunda revisión, sobre los propios arreglos**, encontró cuatro más: un 400 del proveedor
  (por el contenido de un acta) se trataba como caída, así que ese set no gastaba nunca sus intentos
  y, siendo el más viejo, frenaba la generación de todos los demás; el validador relajado aceptaba
  ejercicios con la pista entre paréntesis que el evaluador nunca daría por buenos; un reintento de
  Meissa pendiente podía hacerla hablar sin que nadie se lo pidiera; y frases cortas en español
  («Sí, claro.», «Hola, Sofía.») pasaban por inglés. Arreglados, cada uno con su test.
- **El acta en la purga.** El brief pide que el estudiante pueda pedir el borrado del acta «como
  cualquier otro dato suyo». Ya se iba con la cuenta y con la clase (FK en cascada, también su
  práctica), pero la vista previa que el admin confirma no lo decía: ahora lista las actas y los sets
  de práctica. **La descarga** de sus datos sigue siendo la de siempre para todo, el acta incluida:
  una solicitud «Consulta sobre mis datos personales» en soporte (10 días hábiles), que se atiende a
  mano; no hay exportación automática.

## Revisión de seguridad y permisos (22/09/2026)

Recorrido de todo el backend y el frontend: autorización por endpoint, IDOR, CSRF, cookies,
límites de tasa, validación de entrada, secretos y cabeceras. Lo claro se arregló, cada cosa con
su test; lo que cambia el comportamiento o pide una decisión está abajo, en Pendiente.

- **El limitador ya no abre la puerta cuando se llena.** Antes, con diez mil claves inventadas
  dejaba pasar todo (login, altas, recuperación). Ahora caben cien mil, cada clave recuerda su
  propia ventana (purgar con la de otra borraba contadores diarios) y, lleno, desaloja un lote de
  las más quietas empezando por las que no frenan a nadie.
- **Recuperar contraseña** tiene tope por conexión (20/hora), además del de tres por correo: rotar
  correos convertía nuestro remitente en un cañón de spam.
- **Cambiar o recuperar la contraseña cierra las demás sesiones** en su siguiente petición; la de
  quien la cambió sigue, con id nuevo. Una sesión de una cuenta desactivada ahora se invalida de
  verdad (antes solo se vaciaba el contexto de esa petición). Y el id de sesión se renueva al
  entrar, con contraseña o con proveedor.
- **Cuenta preparada**: si alguien registró el correo de otra persona sin poder confirmarlo y la
  dueña real entra luego con Google, la cuenta pasa a ella —correo verificado y contraseña
  anulada—. Quien sí la había creado recupera su contraseña con «olvidé mi contraseña».
- **Propuestas de reprogramación**: listarlas exigía solo sesión; ahora solo los dos de la clase
  (y el admin) ven el motivo. **Soporte**: un ticket solo puede citar una clase propia.
- **Correos**: todo lo que escribe un usuario (nombre, motivo de cancelación, nota de lugar) se
  escapa antes de entrar al HTML. Un nombre con un enlace llegaba al buzón del otro como enlace con
  nuestro remitente.
- **Diagnóstico**: cada sesión de voz se carga al presupuesto al abrirse, por lo máximo que puede
  durar (antes los minutos de voz no se contaban nunca: solo el resumen), y cada persona —cuenta o
  lead— puede abrir diez al día. Las constraints de `ai_usage_log` y `assessment_recommendations`
  bloqueaban la purga de cuentas; la V47 les pone su `ON DELETE`.
- **Errores**: un id que no es UUID, un parámetro que falta, un cuerpo que no es JSON o una
  constraint sin traducir ya no son 500 (cada 500 manda correo de alerta: era una forma gratis de
  llenar la bandeja). Contraseñas de más de 72 bytes —el límite de bcrypt— se rechazan con 400.
- **Menores**: completar el alta social ya exige CSRF; tope de tamaño en la bio y en los turnos y
  objetivos del diagnóstico; el id de transacción de Wompi que llega por la URL se valida antes de
  pegarlo a una petición saliente; el frontend deja de anunciar Next.js y manda `X-Frame-Options`,
  `X-Content-Type-Options` y `Referrer-Policy`.

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
  política (con quién se comparten los datos) no dice que, al entrar con Google o Facebook, de
  la cuenta se toman el nombre, el correo y su identificador; Google lo revisa al verificar la marca.
  Tampoco nombra a OpenAI, que recibe la voz del
  diagnóstico, ni a 8x8, que aloja las clases. Desde el 23/09 OpenAI recibe además el dictado del
  profesor (su voz), sus notas de la clase y el contenido del acta publicada, del que sale la
  práctica; nunca el nombre ni el correo del estudiante, pero sí lo que el profesor diga de él.
- **Retracto sin flujo propio**: ver el Bloque 9. Se atiende por ticket, a mano.
- **Subida de fotos y documentos en local**: exige `CLOUDINARY_URL` en el entorno. Sin ella la API
  responde 503 con un mensaje legible (antes era un 500 sin explicación), pero el wizard de
  postulación no se puede terminar en local: le faltarán siempre la foto y el CV.
- **Avisos en el dispositivo apagados hasta poner las claves VAPID** en Railway:
  `npx web-push generate-vapid-keys` → `ORION_VAPID_PUBLIC_KEY`, `ORION_VAPID_PRIVATE_KEY` y
  `ORION_VAPID_SUBJECT` (mailto:). Cambiarlas invalida las suscripciones. En iPhone solo funcionan
  con Orión instalada en la pantalla de inicio.
- ~~Las sesiones se pierden en cada despliegue.~~ **Resuelto el 25/09** con Spring Session JDBC (V68).
  El despliegue que lo estrena saca a todo el mundo **una última vez** (las sesiones de memoria no
  pasan a la base). Si se quiere una sesión más larga que 30 min de inactividad, es
  `server.servlet.session.timeout` —decisión de producto—.
- **Para decidir (revisión de seguridad del 25/09)**: la prueba gratis cuenta como clase de verdad
  —se califica y suma al ranking—, así que un profesor con cuentas falsas consigue reseñas de 5★ sin
  pagar comisión (antes le costaba el 20 % por clase); opciones: que la reseña de una prueba no
  cuente en el promedio, o exigir una clase pagada. Una cuenta nacida de Google puede **crear**
  contraseña sin pedir otra vez la entrada con Google (quien robe esa sesión deja una contraseña
  suya). Cancelar la prueba dentro de las 12 h (antes de empezar) no tiene consecuencia: alguien
  podría apartar horas de un profesor y soltarlas tarde, gratis.
- **Cerrar sesión apaga los avisos push de ese navegador** (25/09): al volver a entrar hay que
  activarlos otra vez desde la campana.
- **La clase de prueba nace apagada** (V65): los profesores que no la habían puesto en 0 tienen que
  encender «Ofrezco la primera clase gratis» en su perfil si quieren ofrecerla.
- **El wireflow usa la base compartida del artifact**: solo lo abre quien esté en la organización de
  Pardo o sea invitado por correo, y alguien de fuera solo marca si lo invitan como **Editor** y el
  artifact no se comparte también por enlace (eso lo baja a solo ver).
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
- **Configurar el webhook de JaaS** (ya construido, 22/09): en la consola de JaaS → Webhooks, un
  endpoint a `https://orionidiomas.com/api/v1/webhooks/video/jaas` con los eventos
  `PARTICIPANT_JOINED`, `PARTICIPANT_LEFT` y `SPEAKER_STATS`, y su secreto en Railway como
  `JAAS_WEBHOOK_SECRET`. Sin eso la antesala sigue diciendo «aún no ha entrado». Ojo: el ejemplo de
  firma de la documentación de 8x8 no se reproduce a sí mismo; el verificador acepta las dos
  lecturas naturales del secreto y el primer evento real dirá cuál usan (si llegan 401, mirar el
  registro).
- **Rotar la llave de OpenAI**: viajó por la terminal y quedó en el transcript de la sesión.
- **Cuánto se guardan las solicitudes de llamada ya atendidas**: hoy, indefinidamente. Conviene
  fijar un plazo (y un job que lo cumpla) antes de que se acumulen teléfonos sin finalidad vigente.
- **Límite de tasa de OpenAI**: la organización tiene 40.000 tokens por minuto en el modelo de
  voz, y cada respuesta de Meissa gasta unos 2.800 porque relee el guion entero. Con dos o tres
  diagnósticos a la vez se alcanza, y entonces la respuesta llega como `response.done` con estado
  `failed`. Visto el 22/09 en la prueba del guion v4. **Desde el 23/09 el navegador lo trata**: la
  vuelve a pedir tras 2, 4 y 8 s y, a la tercera, avisa con su botón de reintentar, en vez de dejar a
  Meissa callada. El límite sigue ahí: subirlo es pedírselo a OpenAI (sube solo con el gasto).
- **El avatar personalizado solo lo ve su dueño.** Que otros lo vean en sus listas exige embeber la
  personalización en dos DTOs y añade una consulta a los endpoints que pintan listas.
- **Bloque 10 — dónde el código se aparta del brief** (auditoría del 23/09; lo que era un olvido
  —el enlace del acta a la mensajería (D3), la celebración del logro al terminar la práctica
  (B5.3), el botón de 32 px (A5) y la prueba del índice único de puntos— ya se arregló):
  - El acta se escribe en su propia pantalla y no dentro de la tarjeta de la clase.
  - Practicar avanza la racha (decisión de Pardo), y eso tocó `engagement`, que el brief pedía no
    tocar: su listener y el cálculo de semanas activas.
  - Un set con menos de dos ejercicios anclados queda `FAILED` al **tercer** intento, no al primero:
    hasta tres llamadas por set, porque el modelo a veces falla en una y acierta en la siguiente.
  - **D7, decidido por Pardo el 23/09: sí.** La IA ve el nivel (`self_declared_level`) y el
    objetivo (`motivation`) del estudiante, en el acta y en la práctica, como contexto y nunca como
    contenido. El objetivo es más dato personal hacia OpenAI: cuenta para el pendiente de nombrar a
    OpenAI en la política de datos. El idioma va como código («FR»), no como nombre, salvo el inglés.
  - El diálogo se acepta de 3 a 6 líneas (el prompt pide 4 o 5, como el brief).
  - Tras un JSON inválido del acta hay un reintento, así que la espera puede pasar de 25 s; y el
    profesor ve un único aviso neutro para IA apagada, sin presupuesto o caída (cero mensajes técnicos).
  - La e2e llega al acta desde Mis clases, no desde la notificación (la notificación la prueba
    `LessonNoteIT`). Las migraciones se llaman `V48__actas_de_clase` y `V49__practica`.
- **Seguridad — para decidir (revisión del 22/09)**:
  - **IP detrás del proxy de Railway.** `forward-headers-strategy: framework` confía en el primer
    valor de `X-Forwarded-For`, que el cliente puede inventar; si Railway no lo reescribe, todos los
    frenos por IP se esquivan cambiando esa cabecera. Probar en producción con
    `curl -H "X-Forwarded-For: 1.2.3.4"` contra el login y ver si el freno cuenta por esa IP.
    Cambiarlo a ciegas podría hacer que todo el mundo comparta la IP del proxy.
  - **Límite duro en OpenAI.** El freno de gasto es nuestro; una llave de voz robada puede hablar
    hasta el máximo de sesión del proveedor. Poner un tope mensual en el proyecto de OpenAI.
  - **CSP y Permissions-Policy** del frontend: pendientes a propósito, porque el aula necesita
    cámara y micrófono dentro del iframe de 8x8 y una política mal puesta la rompe sin ruido.
  - Conversaciones y reseñas ajenas responden 403 en vez de 404 (confirman que existen).
  - Las reseñas públicas muestran el nombre completo de quien las escribió.
  - Un profesor ve el resumen del diagnóstico de cualquier estudiante con quien tenga una reserva,
    aunque esté cancelada.
  - El JWT de la sala de JaaS lleva el correo del usuario (8x8 lo ve).
  - Facebook: su correo se trata como verificado (Meta solo entrega el principal, ya confirmado).
  - `certified` lo marca el propio profesor, sin revisión.
  - El alta responde 409 si el correo ya existe: revela qué correos tienen cuenta.
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
