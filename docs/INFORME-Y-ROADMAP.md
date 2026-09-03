# Orión — Deuda, ideas y notas de despliegue

> Lo que no cabe en otro sitio: deuda técnica conocida, ideas que surgieron trabajando y las
> notas de operación. **Qué hay construido está en [`ESTADO.md`](./ESTADO.md)**; el alcance de
> cada tarea, en [`briefs/`](./briefs).

---

## Deuda técnica conocida

### Calidad / tests
- **La suite e2e muta estado compartido**: exige `docker compose down -v` entre corridas completas.
  *Mejora:* aislar cada test (usuarios/reservas propios con sufijo aleatorio) o un reset por test
  vía endpoint de test-only. Hoy es "una corrida por semilla".
- **Falta e2e del happy-path completo de recuperación** (con token real desde Mailpit) y de
  **subir foto** (bloqueado por Cloudinary). El backend sí los cubre con ITs.
- **Lighthouse de la landing** (DoD del minibrief: ≥95 en Perf/SEO/A11y) no se ha corrido — hazlo
  desde Chrome DevTools sobre la URL de producción.
- **Tests de componentes frontend**: hay **Vitest** con 36 tests de lógica pura (`lib/phone`,
  `fuerzaClave`, formato de fechas y horas, límites de la ficha del profesor). Falta cobertura de
  *componentes* con render (Testing Library) — p. ej. `PhoneInput`, `CambiarFoto`, los modales.

### Accesibilidad
- Repaso formal con lector de pantalla y teclado de las pantallas nuevas (registro, /cuenta,
  reprogramar, recuperar). Los tokens ya garantizan foco visible y contraste AA, pero conviene
  auditar `aria-live` en toasts/errores y el orden de tabulación en los modales.
- Las **banderas emoji** no renderizan en Windows: ni en el `PhoneInput` (ahí basta el prefijo
  `+57`) ni en los chips de idioma del marketplace, donde sí se nota. Cambiar a códigos («EN»,
  «FR») o a SVG inline.

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

## Ideas para futuros briefs

Ninguna implementada — son propuestas para futuros briefs, alineadas con el negocio (confianza al
hablar, adultos, curaduría de academia).

1. **Confidence Score® de verdad** (MVP 2). Ya lo teaseamos en la landing. Un score simple por
   estudiante que sube con clases completadas + una micro-encuesta post-clase ("¿qué tan cómodo te
   sentiste hablando hoy?"). Es el gancho de retención y el diferenciador real.
2. **Recordatorio automático 24 h / 1 h antes** por correo (y opcional WhatsApp). El backend de
   notificaciones ya existe; es añadir un job programado que dispare `BookingReminderEvent`.
3. ~~**"Reservar de nuevo con este profesor"**~~ — **hecho** (03/09/2026), en el panel de progreso
   del estudiante: la lista "Con quién has practicado" lleva directo a su agenda.
4. ~~**Racha de clases**~~ — **hecha** (03/09/2026): racha actual, mejor racha y mapa del último año
   en el panel del estudiante, con Rigel cambiando de pose según el estado.
5. **Página pública del profesor** (`/p/<slug>`) para que Sofía comparta perfiles en redes — SEO
   + funnel, reutiliza el detalle que ya existe.
6. **Onboarding del estudiante** de 2 pasos tras el registro (nivel aproximado + objetivo) para
   pre-filtrar profesores y personalizar el saludo de Rigel. **Aprobado por producto (03/09/2026),
   sin construir**: las decisiones de arquitectura están en un documento aparte. La primera es que
   hoy un estudiante no tiene ficha propia.
7. **Modo "sin cámara"** / preferencias de clase en el perfil, para bajar la ansiedad de hablar
   (coherente con la misión).
8. **Métricas para Sofía** en el panel admin: tasa de recompra, no-shows por profesor, cupos
   desaprovechados. Datos para decidir a quién invitar.
9. **Bloque de disponibilidad recurrente "copiar semana"** para profesores — hoy se cargan franjas
   una a una.
10. **i18n-ready**: el copy está en español hardcodeado; extraerlo facilitaría una versión en
    inglés para el mercado que "aprende inglés en inglés".

---

## Notas de operación y despliegue

Variables de entorno que deben estar en Railway (producción):
- `WOMPI_PUBLIC_KEY` / `WOMPI_INTEGRITY_SECRET` / `WOMPI_EVENTS_SECRET` — pasarela de pagos. **Las
  tres son obligatorias**: sin ellas reservar responde 422. La **llave privada NO se usa** y por eso
  no está en la configuración: el Web Checkout se firma con el secreto de integridad, el webhook se
  verifica con el de eventos y la consulta de transacciones va con la llave pública. Un secreto de
  producción que no se usa es superficie de ataque gratis.
- `WOMPI_API_BASE_URL` — `https://production.wompi.co/v1` en producción. **El default es el sandbox
  a propósito**: apuntar a producción tiene que ser un acto deliberado.
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
- Flyway va por **V19**. Cada cambio de esquema es una migración nueva; nunca editar una aplicada.
- **Configurar el webhook en el panel de Wompi.** Sin eso ningún pago confirma una clase: la
  redirección del navegador NO es la fuente de verdad, el webhook sí. La URL correcta es la del
  **dominio público del frontend** —`https://<dominio>/api/v1/webhooks/payments/wompi`—, no la del
  backend: en Railway el backend vive en la red interna y Wompi no lo alcanza. El `rewrite` de Next
  reenvía `/api/*` al backend tal cual, así que el evento llega íntegro.
- **Desarrollo local necesita claves de SANDBOX de Wompi** (prefijo `_test_`) o no se puede
  reservar. La excepción es la estudiante sembrada `ana@orion.local`, que arranca con saldo a favor
  (`BillingDevSeeder`) y por eso puede reservar sin pasarela.
- La suite de humo asume semilla fresca por corrida completa.
