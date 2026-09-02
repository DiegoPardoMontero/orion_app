# Estado de Orión

Resumen vivo de qué hay construido y desplegado. Se actualiza al cerrar cada paso/brief.

## Desplegado en producción (`master`)

**Backend** (Spring Boot 4.1, `co.orion`): identidad + sesión, disponibilidad + `SlotCalculator`,
reservas con regla de 24 h, asistencia, notificaciones por correo (con `.ics` + link a Google
Calendar), panel admin (usuarios, reservas, métricas). Migraciones Flyway V1–V5.

**Frontend** (Next 16, React 19, Tailwind v4): sistema de diseño **v2 "Amanecer cálido premium"**,
mascota **Rigel** (5 poses, 2 tonos), PWA instalable (service worker + íconos de marca), y las
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

## Pendiente / bloqueos conocidos
- **Brief `orion-brief-pulido-v1`** (pasos 0–6): fotos en Mis Clases, `PhoneInput` E.164, fotos
  para todos (**requiere `CLOUDINARY_URL`**), link Jitsi automático, reserva desktop semanal,
  disponibilidad sin scroll, invitación de profesores. En curso.
- **Config de producción**: SMTP real (`MAIL_*`), `ORION_APP_BASE_URL`, `NEXT_PUBLIC_SUPPORT_WHATSAPP`
  (= `573023063447`), `NEXT_PUBLIC_SITE_URL` — variables de entorno en Railway.
- Testimonios de la landing: ocultos hasta tener citas reales de Sofía.

## Mascota
La tabla viva de apariciones está en [`orion-mascota-guia.md`](./orion-mascota-guia.md).
