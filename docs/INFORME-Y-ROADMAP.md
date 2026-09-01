# Orión — Informe de estado, mejoras, pendientes e ideas

> Escrito de corrido durante una sesión larga de trabajo. Refleja el estado real del repo
> (`master`, desplegado en Railway). Fecha: sept 2026.

---

## 0. ⚠️ ACCIÓN URGENTE — el `api_secret` de Cloudinary es incorrecto

La subida de fotos (Paso 2 del brief de pulido) está **implementada y desplegada**, pero **no
funcionará** hasta que arregles esto:

- Probé la `CLOUDINARY_URL` que me diste **directamente contra la API de Cloudinary** (no solo
  desde la app). El `cloud_name` (`sifqjzh1`) y el `api_key` son válidos, **pero el `api_secret`
  no coincide**: el endpoint de Cloudinary responde literalmente `{"error":{"message":"api_secret
  mismatch"}}`.
- **Qué hacer:** entra a tu **Cloudinary Dashboard → Product Environment Credentials** (o Settings
  → API Keys), copia el **API Secret** exacto de la cuenta `sifqjzh1`, arma la URL
  `cloudinary://<api_key>:<api_secret>@sifqjzh1` y actualiza la variable `CLOUDINARY_URL` en
  **Railway** (servicio backend). No necesitas tocar código.
- **Cómo verificar rápido** (reemplaza `SECRET`):
  ```
  curl -s -u "987923776991672:SECRET" "https://api.cloudinary.com/v1_1/sifqjzh1/usage"
  ```
  Si devuelve un JSON con tu uso (no `api_secret mismatch`), el secreto es correcto.
- El código de firma usa **SHA-1** (el algoritmo por defecto de Cloudinary). Si tu cuenta está
  configurada en **SHA-256**, avísame y lo cambio en una línea (`CloudinaryPhotoUploader`).

Todo lo demás del flujo de fotos (validación de tipo/tamaño, endpoint, UI "Cambiar foto",
avatares en toda la app, fallback de iniciales) está probado y listo.

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

### Brief de pulido (`orion-brief-pulido-v1`) — Pasos 0, 1, 2, 3 y 6 hechos
- **Paso 0**: foto + titular en Mis Clases, tarjetas de profesores con bio (clamp 3 líneas), fix
  de capas de Rigel (mano detrás del cuerpo).
- **Paso 1**: teléfonos en **E.164** con `PhoneInput` (país + número), normalización central +
  backfill (V6), `wa.me` robusto con números extranjeros.
- **Paso 2**: **fotos para todos** vía Cloudinary (migración V7, `POST /me/photo`, UI "Cambiar
  foto"). ⚠️ pendiente del secreto correcto (sección 0).
- **Paso 3**: **link de videollamada automático** (Jitsi) — migración V8, `MeetingLinkProvider`,
  botón "Unirse a la clase", link en correo y `.ics`. El campo de link manual desapareció.
- **Paso 6**: **invitación de profesores** — migración V9 (tabla `professor_invites` dedicada),
  `POST /admin/professors/invite`, pantalla `/invitacion`, el profesor nace INACTIVE y se activa al
  aceptar. Mata el flujo de claves temporales por WhatsApp.

---

## 2. Lo que falta por implementar (brief de pulido, Pasos 4 y 5)

Son **rediseños de layout de desktop**. El propio brief los marca como *"propuesta tuya con
captura"* + DETENTE: quiere ver y aprobar la dirección visual antes de que se despliegue al piloto.
Por eso los dejo propuestos aquí en vez de empujarlos a producción sin tu visto bueno.

- **Paso 4 · Reserva desktop: perfil compacto + semana navegable** — en ≥1024px, grilla de 7 días
  × franjas horarias (semana navegable ← →) en vez de chips de día+hora; **móvil conserva los
  chips** (la grilla semanal en 390px es scroll horizontal infernal — decisión consciente). Perfil
  rediseñado para bios cortas (foto grande, chips de modalidad/nivel, sin océanos de espacio vacío).
- **Paso 5 · Disponibilidad sin scroll (desktop)** — los 7 días como columnas + panel de fechas
  bloqueadas al costado, todo visible sin scroll en 1280×800; móvil con filas densas (los días sin
  franjas colapsan a una línea).

Ambos son de bajo riesgo (frontend puro, el e2e móvil no los toca) pero se benefician de tu ojo.
Dime "dale a 4 y 5" y los hago con capturas para tu revisión.

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
- **Tests de componentes frontend**: hoy la cobertura frontend es solo e2e de humo. Vitest +
  Testing Library para `SlotCalculator`-equivalentes del cliente (agrupar cupos, `fuerzaClave`,
  `PhoneInput` parse/emit) sería barato y valioso.

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
- `CLOUDINARY_URL` — ⚠️ **con el secreto correcto** (sección 0).
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
