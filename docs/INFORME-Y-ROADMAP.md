# Orión — Informe de estado, mejoras, pendientes e ideas

> Escrito de corrido durante una sesión larga de trabajo. Refleja el estado real del repo
> (`master`, desplegado en Railway). Fecha: sept 2026.

---

## 0. ✅ Cloudinary — subida de fotos verificada de extremo a extremo

La subida de fotos (Paso 2 del brief de pulido) está **implementada, verificada y lista**. El
secreto correcto es el que probamos el 01/09/2026:

- **`CLOUDINARY_URL` correcta:** `cloudinary://987923776991672:<API_SECRET>@sifqjzh1`
  (el `API_SECRET` válido lo tienes tú; **nunca se commitea al repo**, vive solo como variable de
  entorno en Railway).
- **Verificado en tres niveles:** (1) ping directo a la API (`/usage` devolvió el uso real, no
  `api_secret mismatch`); (2) subida firmada con el esquema exacto del backend (`timestamp +
  transformation`, SHA-1) → imagen creada; (3) **flujo real por `POST /api/v1/me/photo`** con
  sesión + CSRF → `HTTP 200` con `photoUrl` de Cloudinary, avatar recortado a 400×400 (`c_fill,
  g_face`).
- **Qué falta hacer tú:** poner esa `CLOUDINARY_URL` (con el secreto correcto) en **Railway**
  (servicio backend) si aún no está. No hay que tocar código.
- El código de firma usa **SHA-1** (el algoritmo por defecto de Cloudinary) y es el correcto para
  esta cuenta.

Todo lo demás del flujo de fotos (validación de tipo/tamaño, endpoint, UI "Cambiar foto",
avatares en toda la app, fallback de iniciales) está probado y listo.

---

## 0.1 Cierre del piloto — verificación de extremo a extremo (01/09/2026)

Ronda de "cerrar el piloto de verdad". Tres frentes:

### a) Ensayo del flujo real (admin → profesor → estudiante) contra SMTP real (Mailpit)
Ejecutado por API de punta a punta con base de datos fresca:
1. Admin inicia sesión → **invita a un profesor** (`POST /admin/professors/invite`, 204).
2. **El correo de invitación llega** a Mailpit (asunto "Sofía te invita a enseñar en Orión").
3. Profesor **acepta la invitación** con el token del correo (`POST /auth/accept-invite`, 200):
   queda ACTIVE y con sesión.
4. Profesor **publica perfil** (`PUT /me/profile`, 200) y **carga disponibilidad**
   (`POST /me/availability/rules`, 201).
5. Estudiante **se auto-registra** (`POST /auth/register`, 201) y **reserva** un cupo
   (`POST /bookings`, 201 · status CONFIRMED).
6. **Llegan las 2 confirmaciones** (estudiante + profesor), cada una **con el adjunto `.ics`**.
7. **Recuperación de contraseña** (`POST /auth/forgot-password`, 204): **el correo llega**.

### b) 🐞 Bug encontrado y corregido — los correos de invitación y recuperación NUNCA salían
`SmtpProfessorInviteMailer` y `SmtpPasswordResetMailer` creaban el `MimeMessageHelper` en modo
simple (`multipart=false`) pero componían el cuerpo con `setText(plano, html)`, que **exige
multipart**. Lanzaban `"Not in multipart mode"`; como el error se traga y se loguea, el correo no
salía **sin que nada fallara visiblemente**. En producción, ni el profesor recibía su enlace de
invitación ni el estudiante el de recuperación. **Fix:** `MULTIPART_MODE_MIXED_RELATED` (igual que
`BookingMailSender`, que sí funcionaba porque adjunta el `.ics`). Se añadió `SmtpMailersTest`, que
ejercita las implementaciones SMTP reales y falla si `send()` no llega a invocarse. `./mvnw verify`:
**155 tests en verde**.

### c) Lighthouse de la landing (build de producción, `next start`)
Corrido con Chromium sobre `http://localhost:3000/`. **Todas las categorías cumplen el DoD (≥95):**

| Categoría | Score | DoD |
|---|---|---|
| Performance | **98** | ≥95 ✅ |
| Accessibility | **95** | ≥95 ✅ |
| SEO | **100** | ≥95 ✅ |
| Best Practices | **96** | — ✅ |

Métricas de perf: FCP 0.8 s · LCP 2.0 s · TBT 120 ms · CLS 0 · Speed Index 0.8 s.

**Hallazgos por encima del DoD:**
1. **Contraste del coral de marca.** *(Parcialmente resuelto — 01/09/2026.)* El coral como *texto*
   pequeño sobre cremas/durazno quedaba bajo AA (3.17–3.48). Se oscureció `primary-strong` a
   `#C0341F` (imperceptible) y se pasaron a esa variante los coral-texto de la landing (wordmark del
   footer, número de paso) y del banner de instalación: ahora pasan 4.5:1. **Lo que queda:** el coral
   de *fondo* del hero/botones (`#E8503A` + texto crema = 3.48) se dejó igual por decisión de diseño,
   así que el score agregado de A11y sigue en **95** (pasa el DoD). Subirlo por encima de 95 exigiría
   oscurecer el fondo de los botones → cambia la cara de la marca. Disponible cuando quieras.
2. **Un 401 en consola.** La landing pública consulta `/auth/me` para decidir el CTA; sin sesión
   responde 401 (correcto) y el navegador lo loguea solo. El JS ya lo maneja (`redirectOn401:false`),
   pero el log de red no se puede silenciar sin evitar la petición (chequeo de sesión server-side, que
   volvería dinámica la página hoy estática) o un endpoint público de sesión que devuelva 200. Best
   Practices ya pasa en 96.

### d) Pendiente que sigue siendo tuyo (no automatizable desde aquí)
- **Lighthouse sobre la URL real de Railway** (este corrió en local sobre el build de prod; Perf real
  depende del edge/CDN, pero SEO/A11y son idénticos).
- **Verificar el SMTP real de producción** (Resend u otro): que `MAIL_*` estén en Railway y que un
  correo real de invitación/recuperación llegue a una bandeja de verdad. El código ya está probado
  contra un SMTP real (Mailpit); falta el proveedor de producción.

---

## 0.2 Brief maestro marketplace — progreso (02/09/2026)

Avance autónomo del `orion-brief-maestro-marketplace.md`. Decisiones Q1–Q10 acordadas y guardadas
en memoria (comisión 20% para todos, UI es-CO, cancelación 12h ambos, Wompi + liquidación manual,
etc.). Orden ejecutado: 1 → 2 → 3 → 7 → 6(reseñas). **Todo lo de abajo está en `master` y verificado
(`./mvnw verify` 211 tests + `tsc`/`lint`/`build` + e2e 10/10).**

### ✅ Completado y desplegado
- **Bloque 1 — Marketplace base:** catálogo de idiomas/objetivos (tablas, no enums), precio del
  profesor con desglose de comisión (recibe/retiene), buscador con filtros combinables
  (idioma·nivel·objetivo·precio·nativo·certificado), perfil enriquecido. Migraciones V10/V11.
- **Bloque 2 — Teacher Application:** postulación abierta con wizard, revisión del admin
  (bandeja + ficha con preview del perfil), documentos privados (Cloudinary authenticated + URL
  firmada 5 min + auditoría), máquina de estados auditada, y el **gate de acceso**
  (`assertCanTeach`) que impide que un no-aprobado aparezca en el marketplace, publique o reciba
  reservas. El invitado por admin nace APPROVED (D3). Migración V12.
- **Bloque 3 — Mensajería interna + notificaciones (D4):** conversaciones estudiante↔profesor,
  enmascarado de datos de contacto (teléfonos/correos/menciones a canales externos), notificaciones
  in-app + campana, y **retiro de WhatsApp** de los correos (contacto dentro de Orión). Migración V13.
- **Bloque 7 — Portada marketplace pública:** nueva `/` (server-rendered, SEO/JSON-LD) con nav
  pública, buscador de 3 campos, idiomas destacados, profesores reales (se ocultan si <4), método
  ORION, y `/ensena-con-orion`.
- **Bloque 6 (parcial) — Reseñas:** reseñar una clase pasada (una vez), promedio solo con ≥3
  reseñas ("Nuevo en Orión" si no), reporte del profesor + ocultado del admin. Migración V14.

### ⏸️ Bloqueado / pendiente (NO implementado a propósito)
- **Bloque 4 — Pagos, comisión, créditos, liquidación (Q4):** requiere tu conversación con el
  **contador** (retener plata de terceros en Colombia: facturación, retenciones, contrato de
  mandato) y credenciales reales de **Wompi**. Es dinero real: no se toca sin tu visto bueno.
- **Bloque 5 — No-show y disputas:** depende de los pagos/créditos del Bloque 4.
- **Bloque 6 (resto) — métricas, ranking nocturno, sanciones:** dependen del ciclo de vida del
  Bloque 5 (no-show). El agregado de rating actual es incremental, sin job nocturno.

### Deudas menores anotadas (no bloquean)
- `TeacherApplicationView` no expone datos de perfil → un estudiante postulante arranca el wizard
  en blanco (el profesor re-editando sí siembra). Se resuelve exponiendo el perfil en la vista.
- Falta UI de "reportar reseña" (profesor) y "ocultar reseña" (admin); los endpoints ya existen.
- Filtros `availableDay`/`availableTime` del buscador quedaron documentados como aproximación
  pendiente (filtro contra `availability_rules`).

---

## 1. Qué tenemos hoy (desplegado y verificado)

Cada pieza pasa `./mvnw verify` (backend, Testcontainers) + `next build`/`tsc`/`lint` (frontend) +
**suite e2e Playwright** (móvil, 9 casos) en verde sobre semilla fresca.

### Producto base (MVP 1, briefs tareas 1–5)
- **Backend** (Spring Boot 4.1, monolito modular `co.orion`): identidad + sesión (cookie httpOnly
  + CSRF), disponibilidad + `SlotCalculator` puro, reservas con regla de 24 h e índice único
  parcial anti-doble-reserva, asistencia, notificaciones por correo con **`.ics` adjunto + link a
  Google Calendar**, panel admin (usuarios, reservas, métricas de autoservicio).
- **Frontend** (Next 16, React 19, Tailwind v4): PWA, TanStack Query, guardas por rol.

### Rediseño y marca
- **Sistema de diseño v2 "Amanecer cálido premium"**: tokens del amanecer, Bricolage Grotesque +
  Figtree, biblioteca de componentes (botones pill, chips durazno/lavanda, switch, badges, cards),
  constelación de Orión, shell responsive (tab bar móvil + sidebar desktop).
- **Mascota Rigel**: estrella antropomorfa, 5 poses × 2 tonos, animaciones; en login, registro,
  banner de reserva, estados vacíos, 404 y hero de la landing. Tabla viva en
  `orion-mascota-guia.md`.
- **Favicon + íconos PWA** con la cara de Rigel; **PWA instalable** (service worker + banner
  "Instalar Orión" + soporte iOS).

### Features añadidas sobre el MVP
- **Auto-registro de estudiantes** (`POST /auth/register`) + `/registro`.
- **Perfil del estudiante** (`/me/account`) + `/cuenta`.
- **Reprogramar reserva** (`POST /bookings/{id}/reschedule`).
- **Recuperar contraseña** (token con hash SHA-256 + expiración 30 min, un solo uso) + `/recuperar`
  y `/restablecer`.
- **Landing pública** en `/` (server-rendered, SEO, OG, sitemap/robots, Rigel protagonista, CTA
  consciente de sesión, WhatsApp).

### Brief de pulido (`orion-brief-pulido-v1`) — Pasos 0–6 hechos (completo)
- **Paso 0**: foto + titular en Mis Clases, tarjetas de profesores con bio (clamp 3 líneas), fix
  de capas de Rigel (mano detrás del cuerpo).
- **Paso 1**: teléfonos en **E.164** con `PhoneInput` (país + número), normalización central +
  backfill (V6), `wa.me` robusto con números extranjeros.
- **Paso 2**: **fotos para todos** vía Cloudinary (migración V7, `POST /me/photo`, UI "Cambiar
  foto"). ✅ verificado de extremo a extremo con el secreto correcto (sección 0).
- **Paso 3**: **link de videollamada automático** (Jitsi) — migración V8, `MeetingLinkProvider`,
  botón "Unirse a la clase", link en correo y `.ics`. El campo de link manual desapareció.
- **Paso 4**: **reserva desktop — perfil compacto + semana navegable**. En ≥1024px la agenda del
  profesor es una grilla de 7 días navegable (← →, con query por semana `["slots", id, "semana", from]`);
  **móvil conserva los chips día+hora** (la grilla en 390px sería scroll horizontal). Layout
  `lg:grid-cols-[340px_minmax(0,1fr)]` (el `minmax(0,1fr)` evita que la card desborde el sidebar).
- **Paso 5**: **disponibilidad sin scroll (desktop)**. Los 7 días como columnas (`lg:grid-cols-7`) +
  panel de fechas bloqueadas al costado (`lg:grid-cols-[1fr_260px]`), todo visible en 1280×800; móvil
  con tarjetas apiladas y pills compactas ("18–21"). `useMediaQuery` con `useSyncExternalStore`.
- **Paso 6**: **invitación de profesores** — migración V9 (tabla `professor_invites` dedicada),
  `POST /admin/professors/invite`, pantalla `/invitacion`, el profesor nace INACTIVE y se activa al
  aceptar. Mata el flujo de claves temporales por WhatsApp.

---

## 2. Lo que falta por implementar (brief de pulido)

**Nada — el brief de pulido está completo (Pasos 0–6).** Los Pasos 4 y 5 (rediseños de layout de
desktop) se implementaron y verificaron con capturas en 1280×800 (desktop, sin scroll) y 390px
(móvil, sin regresiones): reserva con semana navegable y disponibilidad en columnas. La suite e2e
móvil (10/10) sigue en verde sobre semilla fresca.

---

## 3. Oportunidades de mejora (deuda y pulido)

### Calidad / tests
- **La suite e2e muta estado compartido**: exige `docker compose down -v` entre corridas completas.
  *Mejora:* aislar cada test (usuarios/reservas propios con sufijo aleatorio) o un reset por test
  vía endpoint de test-only. Hoy es "una corrida por semilla".
- **Falta e2e del happy-path completo de recuperación** (con token real desde Mailpit) y de
  **subir foto** (bloqueado por Cloudinary). El backend sí los cubre con ITs.
- **Lighthouse de la landing** (DoD del minibrief: ≥95 en Perf/SEO/A11y) no se ha corrido — hazlo
  desde Chrome DevTools sobre la URL de producción.
- **Tests de componentes frontend**: ✅ se añadió **Vitest** con 15 tests de lógica pura
  (`lib/phone`, `fuerzaClave`, helpers de fecha/iniciales). Falta cobertura de *componentes* con
  render (Testing Library) — p. ej. `PhoneInput`, `CambiarFoto`, los modales.

### Accesibilidad
- Repaso formal con lector de pantalla y teclado de las pantallas nuevas (registro, /cuenta,
  reprogramar, recuperar). Los tokens ya garantizan foco visible y contraste AA, pero conviene
  auditar `aria-live` en toasts/errores y el orden de tabulación en los modales.
- Las **banderas emoji** del `PhoneInput` no renderizan en algunos SO (se ve el prefijo `+57`, que
  basta). Si molesta, cambiar a banderas SVG inline.

### UX / producto
- **Animación de deleite** de la confirmación de reserva (check dibujado + estrellas) del handoff
  v2 no se implementó; hoy usamos el banner de Rigel celebrando (suficiente, pero menos "wow").
- **Vista de reserva móvil**: la barra de confirmación no es sticky (es in-flow) para no chocar con
  el tab bar; se puede pulir con un sticky por encima del tab bar.
- **Estados de error de red** globales: hoy cada pantalla maneja el suyo; un interceptor de
  "sin conexión" global daría consistencia.

### Backend / arquitectura
- `professor_profiles.photo_url` quedó **deprecada** (se lee de `users.photo_url`): eliminarla en
  una migración futura cuando estemos seguros.
- **Rate limiting** del endpoint de fotos: el brief pedía uno ligero; hoy solo validamos
  tipo/tamaño. Añadir un throttle por usuario (p. ej. en memoria o con la caché) para proteger la
  cuota de Cloudinary.
- **Índices**: revisar que `bookings` tenga índices para las consultas de "mis clases" (por
  student_id/professor_id + starts_at). A escala de decenas está bien; documentarlo.

---

## 4. Ideas nuevas (que surgieron trabajando)

Ninguna implementada — son propuestas para futuros briefs, alineadas con el negocio (confianza al
hablar, adultos, curaduría de academia).

1. **Confidence Score® de verdad** (MVP 2). Ya lo teaseamos en la landing. Un score simple por
   estudiante que sube con clases completadas + una micro-encuesta post-clase ("¿qué tan cómodo te
   sentiste hablando hoy?"). Es el gancho de retención y el diferenciador real.
2. **Recordatorio automático 24 h / 1 h antes** por correo (y opcional WhatsApp). El backend de
   notificaciones ya existe; es añadir un job programado que dispare `BookingReminderEvent`.
3. **"Reservar de nuevo con este profesor"** en Mis Clases pasadas — un botón que lleva directo a
   su agenda. Aumenta la recompra con cero fricción.
4. **Racha de clases** ("llevas 3 semanas seguidas") con Rigel en pose de ánimo. Barato,
   emocional, on-brand.
5. **Página pública del profesor** (`/p/<slug>`) para que Sofía comparta perfiles en redes — SEO
   + funnel, reutiliza el detalle que ya existe.
6. **Onboarding del estudiante** de 2 pasos tras el registro (nivel aproximado + objetivo) para
   pre-filtrar profesores y personalizar el saludo de Rigel.
7. **Modo "sin cámara"** / preferencias de clase en el perfil, para bajar la ansiedad de hablar
   (coherente con la misión).
8. **Métricas para Sofía** en el panel admin: tasa de recompra, no-shows por profesor, cupos
   desaprovechados. Datos para decidir a quién invitar.
9. **Bloque de disponibilidad recurrente "copiar semana"** para profesores — hoy se cargan franjas
   una a una.
10. **i18n-ready**: el copy está en español hardcodeado; extraerlo facilitaría una versión en
    inglés para el mercado que "aprende inglés en inglés".

---

## 5. Notas de operación / despliegue

Variables de entorno que deben estar en Railway (producción):
- `CLOUDINARY_URL` — ✅ secreto correcto ya verificado (sección 0); asegúrate de que esté puesta en Railway.
- `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `ORION_MAIL_FROM` — SMTP real para
  que salgan los correos (confirmación, recuperación).
- `ORION_APP_BASE_URL` — origen del frontend (para los enlaces de los correos, p. ej. recuperación).
- `NEXT_PUBLIC_SUPPORT_WHATSAPP` — `573023063447` (ya cableado con default).
- `NEXT_PUBLIC_SITE_URL` — dominio público (para sitemap/OG absolutos), p. ej. `https://orionidiomas.com`.
- `ORION_ADMIN_EMAIL` / `ORION_ADMIN_PASSWORD` — bootstrap del admin.

Recordatorios:
- **Producción no corre el `DevDataSeeder`** (perfil `local`): la base arranca solo con el admin.
  Para el piloto: admin crea profesor → profesor publica perfil + disponibilidad → estudiante se
  registra y reserva.
- Flyway va por V7. Cada cambio de esquema es una migración nueva; nunca editar una aplicada.
- La suite de humo asume semilla fresca por corrida completa.
