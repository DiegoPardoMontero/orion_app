# Brief · Tarea 3 — Reservas, regla de 24 horas y notificaciones

**Prerequisito:** Tareas 1 y 2 completas (DoD en verde). Sigue vigente el adendum de Spring Boot 4.1.x.
**Resultado de esta tarea:** un estudiante reserva un cupo real, ambas partes ven sus clases con datos de contacto, las cancelaciones respetan la regla institucional de 24 horas, cada reserva y cancelación dispara correos con invitación de calendario (.ics + link a Google Calendar), y el profesor registra asistencia. Con esto el MVP 1 queda completo por API — solo faltará el frontend (Tarea 4).

---

## 0. Modo de trabajo

Rigen las mismas reglas del `CLAUDE.md` (sección 0 del brief 1): **un paso a la vez**, explicar los conceptos nuevos, **DETENERTE al final de cada paso** y esperar confirmación de Pardo, `./mvnw verify` verde antes de declarar un paso terminado, un commit por paso, cero features futuras.

---

## 1. Decisiones de dominio — LEER ANTES DE CODIFICAR

1. **Máquina de estados de la reserva:** `CONFIRMED → CANCELLED_BY_STUDENT | CANCELLED_BY_PROFESSOR | CANCELLED_BY_ADMIN | COMPLETED | NO_SHOW`. Solo `CONFIRMED` admite transiciones; los demás estados son terminales. Cualquier transición inválida → `409`.
2. **La doble reserva se previene en la base, no en el código.** La fuente de verdad es el índice único parcial sobre `(professor_id, starts_at) WHERE status = 'CONFIRMED'`. El servicio hace un pre-chequeo amable (cupo no disponible → `422`), pero si dos requests pasan el chequeo a la vez, uno inserta y el otro recibe la violación de constraint, que se traduce a `409` con mensaje claro ("alguien acaba de tomar este cupo"). Nada de locks pesimistas.
3. **Una sola fuente de verdad de disponibilidad:** al crear una reserva, el cupo solicitado se valida contra el **mismo** `SlotQueryService` del endpoint de cupos. Si el cupo no está en la lista calculada para esa fecha → `422`.
4. **Regla institucional de 24 horas:** una reserva es cancelable si `starts_at − now ≥ 24 horas` (comparación entre instantes con el `Clock` de `shared` — las duraciones entre instantes no dependen de zonas horarias, explicarlo). Un intento con menos de 24 h → `422` con el mensaje institucional: *"Con menos de 24 horas de anticipación la clase se considera impartida (política Orión)"*. **`ADMIN` está exento** — es la válvula de "fuerza mayor" del manual corporativo.
5. **`startsAt` del request** llega como ISO-8601 con offset (exactamente como lo devuelve el endpoint de cupos, p. ej. `2026-07-20T18:00:00-05:00`) y debe coincidir con un cupo disponible por igualdad de instante.
6. **Un estudiante no puede tener dos reservas `CONFIRMED` que se intersecten** en el tiempo, aunque sean con profesores distintos (chequeo de servicio, semántica semiabierta → `422`).
7. **`created_by` = quien ejecuta la acción.** Un `ADMIN` puede reservar en nombre de un estudiante (`studentId` en el body, permitido solo para ese rol). Esto alimenta la métrica estrella del MVP: % de reservas autoservicio (`created_by == student_id`).
8. **Los correos se envían después del commit, nunca dentro de la transacción.** El servicio publica eventos de dominio (`BookingCreatedEvent`, `BookingCancelledEvent`) y un listener con `@TransactionalEventListener(phase = AFTER_COMMIT)` envía los correos envuelto en try/catch con log de error: un fallo del servidor de correo **jamás** tumba ni revierte una reserva, y jamás se notifica una reserva que hizo rollback. Sin `@Async` por ahora (mantiene los tests deterministas).
9. **El .ics se genera a mano** con un componente propio `IcsGenerator` (RFC 5545 básico: `VCALENDAR`/`VEVENT`, `UID = <bookingId>@orion`, `DTSTAMP`, `DTSTART`/`DTEND` **en UTC**, `SUMMARY`, `DESCRIPTION`, `LOCATION`; método `PUBLISH`). Cero dependencias nuevas y 100 % testeable con asserts de string. La cancelación notifica por correo **sin** adjunto; la sincronización real de calendario llega en el MVP 2 con la API de Google.
10. **`canCancel` se calcula en el servidor** y viaja en la respuesta de "mis clases": la regla de 24 h vive una sola vez; el frontend solo pinta.
11. **Voz de marca en los correos** (manual corporativo de Sofía): cercana, clara, positiva; nada de lenguaje de miedo. La hora visible siempre en Bogotá ("mié 16 jul, 10:00 a. m., hora de Bogotá"); el link de WhatsApp de la contraparte (`https://wa.me/<phone>`) siempre presente.

Ubicación: reservas y asistencia en `scheduling`; los correos estrenan el módulo **`notifications`** (`application/` con el listener y los composers). Remitente configurable: `orion.mail.from` (default `no-reply@orion.local`).

---

## 2. Paso 0 — Migración V3, entidad y repositorio

`V3__bookings.sql` con contenido **exacto**:

```sql
CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          UUID NOT NULL REFERENCES users(id),
    professor_id        UUID NOT NULL REFERENCES users(id),
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    modality            VARCHAR(20) NOT NULL
                        CHECK (modality IN ('VIRTUAL', 'IN_PERSON')),
    status              VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED'
                        CHECK (status IN ('CONFIRMED', 'CANCELLED_BY_STUDENT',
                                          'CANCELLED_BY_PROFESSOR', 'CANCELLED_BY_ADMIN',
                                          'COMPLETED', 'NO_SHOW')),
    location_note       VARCHAR(300),
    package_id          UUID,
    created_by          UUID NOT NULL REFERENCES users(id),
    cancelled_by        UUID REFERENCES users(id),
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(300),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (starts_at < ends_at)
);

CREATE UNIQUE INDEX uq_bookings_professor_slot
    ON bookings(professor_id, starts_at)
    WHERE status = 'CONFIRMED';

CREATE INDEX idx_bookings_student   ON bookings(student_id, starts_at);
CREATE INDEX idx_bookings_professor ON bookings(professor_id, starts_at);
```

Notas de diseño para la entrega: las FK a `users` **sin** `ON DELETE CASCADE` — las reservas son registros de negocio y los usuarios no se borran, se inactivan; `package_id` es una columna reservada para el MVP 2 (paquetes), sin FK porque la tabla aún no existe; el índice único **parcial** permite re-reservar un cupo cuya reserva anterior fue cancelada.

Entidad `Booking` en `scheduling/domain` (enums `BookingModality`, `BookingStatus`; sin relaciones JPA — solo UUIDs, igual que en la Tarea 2). Repositorio con al menos: `List<Booking> findByProfessorIdAndStatusAndStartsAtBetween(...)`, `List<Booking> findByStudentId...` equivalentes para "mis clases", y un método de detección de solape de estudiante.

**Verificación:** `\d bookings` en psql muestra la tabla y los tres índices; `flyway_schema_history` con V1–V3 en success; `./mvnw verify` sigue verde.

**DETENTE aquí y espera confirmación.**

---

## 3. Paso 1 — Conectar las reservas al cálculo de cupos

Resolver el `TODO Tarea 3` de `SlotQueryService`: cargar las reservas `CONFIRMED` del profesor en el rango consultado y pasarlas al `SlotCalculator` como intervalos ocupados. El calculador **no se toca** — su contrato C6 ya probó esta resta con datos sintéticos.

Tests de integración nuevos: una reserva `CONFIRMED` elimina exactamente su cupo del endpoint; una reserva `CANCELLED_BY_STUDENT` en el mismo horario **no** lo elimina.

**Verificación (la ejecuta Pardo) — a mano, sin endpoint todavía:**

```bash
# 1. consultar cupos de María y anotar uno (p. ej. el lunes 18:00)
# 2. insertar una reserva CONFIRMED por psql en ese horario exacto:
docker exec -it orion-postgres psql -U orion -d orion -c "
insert into bookings (student_id, professor_id, starts_at, ends_at, modality, created_by)
select s.id, p.id,
       '<FECHA_LUNES>T18:00:00-05:00'::timestamptz,
       '<FECHA_LUNES>T19:00:00-05:00'::timestamptz,
       'VIRTUAL', s.id
from users s, users p
where s.email = 'ana@orion.local' and p.email = 'maria@orion.local';"
# 3. reconsultar cupos → las 18:00 de ese lunes desapareció; 19:00 y 20:00 siguen
# 4. update bookings set status = 'CANCELLED_BY_STUDENT' ... → el cupo reaparece
# 5. dejar la tabla limpia: delete from bookings;
```

**DETENTE aquí y espera confirmación.**

---

## 4. Paso 2 — Crear reserva

`POST /api/v1/bookings` — body `CreateBookingRequest(UUID professorId, OffsetDateTime startsAt, String modality, String locationNote, UUID studentId)`:

- Roles: `STUDENT` reserva para sí mismo (`studentId` debe venir nulo o ser el propio → si no, `403`); `ADMIN` reserva en nombre de otro (`studentId` obligatorio y debe ser un STUDENT activo); `PROFESSOR` → `403`.
- Validaciones en orden, cada una con su error claro:
  1. Profesor existe y está publicado → si no, `404`.
  2. `modality` válida; `locationNote` máx. 300 (opcional en ambas modalidades — la logística fina la acuerdan por WhatsApp).
  3. El instante `startsAt` coincide exactamente con un cupo disponible de ese profesor ese día (vía `SlotQueryService`) → si no, `422` "el cupo no está disponible".
  4. El estudiante no tiene otra reserva `CONFIRMED` que se intersecte → `422`.
- Inserción con `ends_at = starts_at + 1h`, `status = CONFIRMED`, `created_by` = principal. La violación del índice único (carrera) se captura (`DataIntegrityViolationException`) y se traduce a `409` "alguien acaba de tomar este cupo" — explicar a Pardo el patrón *check amable + constraint como árbitro final*.
- Respuesta `201` con `BookingResponse(id, professorId, studentId, startsAt, endsAt, modality, status, locationNote)`.
- Publicar `BookingCreatedEvent` (el listener llega en el Paso 5 — por ahora nadie lo escucha y eso está bien; explicar por qué publicar eventos desde ya deja el dominio listo).

Tests: camino feliz (con `created_by` correcto), cada validación, `403` de profesor, admin en nombre de estudiante, y **el test de carrera**: dos inserciones concurrentes al mismo cupo → exactamente una gana y la otra recibe `409` (usar `ExecutorService` con barrera, o como mínimo un test que inserte directo por repo y verifique que el segundo `save` + flush lanza la excepción de integridad).

**Verificación curl** (patrón CSRF de la Tarea 2): reservar como Ana un cupo real de María → `201`; repetir el mismo cupo → `422`; reservar un horario inexistente → `422`; consultar cupos → el reservado ya no aparece. Probar la métrica:

```bash
docker exec -it orion-postgres psql -U orion -d orion -c "
select count(*) filter (where created_by = student_id) * 100.0 / count(*)
       as pct_autoservicio
from bookings where status = 'CONFIRMED';"
```

**DETENTE aquí y espera confirmación.**

---

## 5. Paso 3 — Mis clases

`GET /api/v1/me/bookings?scope=upcoming|past` (default `upcoming`; roles `STUDENT` y `PROFESSOR`):

- `upcoming`: `status = CONFIRMED` y `starts_at > now`, orden ascendente.
- `past`: todo lo demás (ya ocurridas o en estado terminal), orden descendente.
- Item: `MyBookingResponse(id, startsAt, endsAt, modality, status, locationNote, canCancel, counterpart)` donde `counterpart` es `{id, fullName, whatsappPhone}` — el profesor para el estudiante, el estudiante para el profesor. `canCancel` aplica la decisión 4 (estado + 24 h) en el servidor.
- Componer el counterpart consultando `UserRepository` desde el servicio de aplicación (a nuestro volumen no se optimiza con joins; anotarlo como decisión consciente).

Tests: cada rol ve sus reservas con el counterpart correcto; `scope` filtra y ordena; `canCancel` es `false` para una clase a menos de 24 h (con `Clock` fijo) y para estados terminales.

**Verificación curl:** como Ana, `GET /me/bookings` muestra la clase con María, su WhatsApp y `canCancel: true`; como María, la misma clase con los datos de Ana.

**DETENTE aquí y espera confirmación.**

---

## 6. Paso 4 — Cancelar reserva

`POST /api/v1/bookings/{id}/cancel` — body opcional `{ "reason": "..." }` (máx. 300):

- `STUDENT`/`PROFESSOR`: solo sus propias reservas (ajena o inexistente → `404`), solo `CONFIRMED` (→ `409`), solo con ≥ 24 h (→ `422` con el mensaje institucional). Estado resultante según quién cancela (`CANCELLED_BY_STUDENT` / `CANCELLED_BY_PROFESSOR`).
- `ADMIN`: cualquier reserva `CONFIRMED`, sin restricción de 24 h → `CANCELLED_BY_ADMIN`.
- Registrar `cancelled_by`, `cancelled_at` (del `Clock`) y `cancellation_reason`. Respuesta `200` con el booking actualizado. Publicar `BookingCancelledEvent`.

Tests (con `Clock` fijo): cancelación válida de cada rol y su estado resultante; `422` a menos de 24 h para estudiante y profesor; admin cancela a cualquier hora; `409` al cancelar dos veces; `404` ajena; **el cupo liberado reaparece** en el endpoint de cupos.

**Verificación curl:** cancelar la reserva del Paso 2 como Ana (si está a más de 24 h) → `200`; reconsultar cupos de María → el cupo volvió; intentar cancelarla de nuevo → `409`.

**DETENTE aquí y espera confirmación.**

---

## 7. Paso 5 — Notificaciones por correo (.ics + Google Calendar)

Nace el módulo `notifications`:

- `BookingNotificationListener` con `@TransactionalEventListener(phase = AFTER_COMMIT)` para ambos eventos; envía a **estudiante y profesor**; try/catch con log de error (decisión 8).
- `BookingEmailComposer`: asunto y cuerpo (HTML sencillo + alternativa de texto) con la voz de marca (decisión 11). Creación: fecha/hora en Bogotá, modalidad, nota de lugar, nombre y **link de WhatsApp** de la contraparte, adjunto `clase-orion.ics` y botón/link "Añadir a Google Calendar". Cancelación: quién canceló, motivo si existe, sin adjunto.
- `IcsGenerator` (decisión 9): cuidado con los detalles del RFC — líneas terminadas en CRLF y escape de `,` `;` y saltos en `DESCRIPTION`/`LOCATION`.
- `GoogleCalendarLinkBuilder`: `https://calendar.google.com/calendar/render?action=TEMPLATE&text=...&dates=<inicioUTC>/<finUTC>&details=...&location=...` con fechas en formato básico UTC (`yyyyMMdd'T'HHmmss'Z'`) y parámetros URL-encoded.
- Envío con `JavaMailSender` (ya configurado contra Mailpit desde la Tarea 1).

Tests: unit tests de string para `IcsGenerator` (UID, DTSTART/DTEND en UTC correctos, CRLF, escapes) y para el builder del link (fechas UTC, encoding); integración con **`@MockitoBean`** sobre `JavaMailSender` — ojo: `@MockBean` fue eliminado en Boot 4, usar `@MockitoBean` — verificando que crear una reserva envía exactamente 2 correos con los destinatarios correctos y que un fallo del sender no rompe la creación (la reserva queda `CONFIRMED`).

**Verificación (la parte más satisfactoria):**

```bash
# crear una reserva por curl y abrir http://localhost:8025
# → 2 correos; abrir el de Ana: hora en Bogotá, WhatsApp de María,
#   adjunto .ics (descargarlo y abrirlo con el calendario local),
#   link de Google Calendar que precarga el evento con la hora correcta
# cancelar → 2 correos más, sin adjunto

curl -s http://localhost:8025/api/v1/messages | python3 -c \
  "import sys, json; print(json.load(sys.stdin)['total'])"
# → 4
```

**DETENTE aquí y espera confirmación.**

---

## 8. Paso 6 — Registro de asistencia

`V4__attendance.sql` **exacto**:

```sql
CREATE TABLE attendance_records (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
    present     BOOLEAN NOT NULL,
    notes       VARCHAR(500),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`POST /api/v1/bookings/{id}/attendance` — body `{ "present": bool, "notes": "..." }` (rol `PROFESSOR`, solo sus reservas → `404`):

- Solo clases ya terminadas (`ends_at <= now`) → si no, `422` "la clase aún no termina".
- Solo estado `CONFIRMED` → `409` (canceladas o ya registradas).
- Efecto: crea el registro y transiciona el booking a `COMPLETED` (`present = true`) o `NO_SHOW` (`present = false`).

Tests (con `Clock` fijo): camino feliz en ambos sentidos con su estado resultante; `422` clase futura; `409` doble registro; `404` ajena; `403` estudiante.

**Verificación curl:** con una reserva pasada (insertarla por psql con fecha de ayer), registrar asistencia como María → `201`; el booking aparece `COMPLETED` en `GET /me/bookings?scope=past`; segundo intento → `409`.

**DETENTE aquí. Fin de la Tarea 3.**

---

## 9. Definition of done (checklist final)

- [ ] V3 y V4 en `flyway_schema_history`; índice único parcial verificado con `\d bookings`
- [ ] Una reserva `CONFIRMED` oculta su cupo; cancelada, lo libera (probado por API y por tests)
- [ ] Crear reserva: validaciones completas, carrera → `409`, admin en nombre de estudiante, `created_by` correcto
- [ ] Mis clases con counterpart, WhatsApp y `canCancel` calculado en servidor
- [ ] Cancelación con regla de 24 h, estados por actor, exención de admin y mensaje institucional
- [ ] 4 correos verificados en Mailpit (2 de creación con .ics + link de Google, 2 de cancelación); fallo de correo no rompe reservas
- [ ] Asistencia: transiciones a `COMPLETED`/`NO_SHOW` con sus validaciones
- [ ] `./mvnw verify` verde; README actualizado; un commit por paso; cero features futuras

## 10. Fuera de alcance de esta tarea — NO construir

Frontend, endpoints de admin (usuarios y listado global de reservas — Tarea 4), recordatorios programados 24 h antes (candidato post-MVP), integración real con la API de Google Calendar y links de Meet (MVP 2), pagos y paquetes (MVP 2), reprogramación como operación atómica (en el MVP se cancela y se vuelve a reservar). Si algo parece faltar, pregunta antes de agregarlo.
