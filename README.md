# Orión — Orion Language Academy

Marketplace de clases particulares de idiomas. El estudiante busca profesor, reserva una hora de
su disponibilidad y **paga por la pasarela**; la clase se da por videollamada. Orión retiene una
comisión y liquida al profesor cuando la clase ya se dictó.

> **Ojo si vienes del MVP 1.** Aquello era agendar y hablar por WhatsApp, sin dinero. Hoy hay
> pagos, saldo a favor, reclamos, reputación, gamificación, textos legales y soporte con plazos de
> ley. Este archivo dice cómo levantarlo y cuál es la superficie del API; **qué hay construido y
> por qué está en [`docs/ESTADO.md`](docs/ESTADO.md)**.

## Stack

- **Backend:** Java 21, Spring Boot 4.1, Maven (wrapper), monolito modular bajo `co.orion`.
- **Base de datos:** PostgreSQL 16. Flyway es el dueño del esquema (`ddl-auto=validate`).
- **Autenticación:** sesión de servidor con cookie httpOnly + CSRF (sin JWT).
- **Frontend:** Next.js 16 (App Router), React 19, TanStack Query, Tailwind v4.

## Estructura

```
backend/    Aplicación Spring Boot
frontend/   Aplicación Next.js (App Router)
docs/       Briefs de las tareas y documentación
```

El backend es un **monolito modular**: trece módulos bajo `co.orion`, cada uno con `api/`
(controladores y DTO), `application/` (servicios), `domain/` (entidades) y `persistence/`
(repositorios).

| Módulo | De qué responde |
|---|---|
| `identity` | Cuentas, sesión, perfiles, postulaciones de profesor |
| `scheduling` | Disponibilidad, cálculo de cupos, reservas, reprogramación |
| `catalog` | Idiomas, objetivos y los **ajustes de plataforma** (comisión, ventanas, umbrales) |
| `billing` | Pagos, saldo a favor, comisión, ganancias y liquidaciones |
| `lifecycle` | Cierre de clases, reclamos, retracto y devoluciones |
| `reputation` | Reseñas, métricas del profesor, ranking y sanciones |
| `messaging` | Conversaciones internas y notificaciones dentro de la app |
| `notifications` | Correos (se acopla por eventos, nunca por llamadas) |
| `engagement` | Gamificación: logros, puntos, rachas y avatar |
| `legal` | Términos, política de datos y constancias de aceptación |
| `support` | Solicitudes de soporte, con los plazos que fija la ley |
| `admin` | Panel, purga definitiva e historial de ajustes |
| `shared` | Reloj, seguridad, errores, tiempo y observabilidad |

La dependencia que sorprende es `identity → reputation` (el perfil público muestra la
calificación), y por eso existe `lifecycle`: es el único sitio que necesita reserva, pago e
historial a la vez. Entre módulos **no hay relaciones JPA**: se guarda el `UUID` plano y la
integridad la garantiza la FK de la base.

**[docs/ESTADO.md](docs/ESTADO.md)** — qué hay construido y desplegado, con las reglas y sus
valores. Empieza por ahí si vienes nuevo al proyecto.

## 1. Levantar la infraestructura

Requisitos: Docker (con Compose) y JDK 21 (el **JDK**, no solo el JRE: `javac -version` debe responder).

```bash
docker compose up -d
docker compose ps      # postgres debe quedar "healthy"
```

| Servicio | Puerto | Para qué |
|---|---|---|
| PostgreSQL 16 | 5432 | Base de datos (`orion` / `orion` / `orion_local`) |
| Mailpit (SMTP) | 1025 | Servidor SMTP falso para desarrollo |
| Mailpit (web) | 8025 | Bandeja de entrada en http://localhost:8025 |

Apagar: `docker compose down` (conserva los datos) o `docker compose down -v` (borra el volumen).

## 2. Levantar el backend

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Arranca en http://localhost:8080. Al arrancar con el perfil `local`, Flyway aplica las
migraciones y la semilla crea los usuarios de desarrollo (es idempotente: reiniciar no duplica).

- OpenAPI (solo perfil `local`): http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

### Credenciales de la semilla

| Email | Rol | Clave |
|---|---|---|
| `admin@orion.local` | ADMIN | `admin123*` |
| `maria@orion.local` | PROFESSOR (perfil publicado) | `orion123*` |
| `juan@orion.local` | PROFESSOR (sin publicar) | `orion123*` |
| `ana@orion.local` | STUDENT | `orion123*` |
| `carlos@orion.local` | STUDENT | `orion123*` |

El admin se puede sobreescribir con `ORION_ADMIN_EMAIL` y `ORION_ADMIN_PASSWORD`.

La semilla también crea disponibilidad: María los lunes 18:00–21:00 y los miércoles 08:00–11:00;
Juan los martes 15:00–18:00.

**Ana arranca con saldo a favor** ($500.000, vía `BillingDevSeeder`) y con cuatro clases pasadas en
semanas distintas. Las dos cosas son a propósito: sin saldo no se puede reservar en local —haría
falta pasar por Wompi—, y sin historial la gamificación se ve vacía. Todos los usuarios sembrados
nacen con la mayoría de edad declarada y el correo verificado, que desde el Bloque 9 son las dos
condiciones para reservar.

### Autenticarse contra la API

```bash
# Login: guarda la cookie de sesión (ORION_SESSION) y la de CSRF (XSRF-TOKEN)
curl -i -c /tmp/orion.txt -H "Content-Type: application/json" \
  -d '{"email":"ana@orion.local","password":"orion123*"}' \
  http://localhost:8080/api/v1/auth/login

curl -b /tmp/orion.txt http://localhost:8080/api/v1/auth/me
```

Toda petición **mutante** (POST/PUT/PATCH/DELETE) exige el header `X-XSRF-TOKEN` con el
valor de la cookie `XSRF-TOKEN`. El login está exento (se protege con las credenciales mismas).

### Endpoints

Autenticación e identidad:

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/v1/auth/login` | público | Abre sesión |
| GET | `/api/v1/auth/me` | autenticado | Usuario de la sesión |
| POST | `/api/v1/auth/logout` | autenticado | Cierra sesión (204) |
| GET | `/api/v1/admin/ping` | ADMIN | Smoke test de rol |

Perfil y directorio de profesores:

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| PUT | `/api/v1/me/profile` | PROFESSOR | Edita y publica/despublica su perfil |
| GET | `/api/v1/professors` | autenticado | Profesores publicados y activos |
| GET | `/api/v1/professors/{id}` | autenticado | Detalle (404 si no está publicado) |

Disponibilidad (todo el módulo exige rol PROFESSOR y opera sobre el profesor de la sesión):

| Método | Ruta | Qué hace |
|---|---|---|
| GET / POST | `/api/v1/me/availability/rules` | Franjas semanales recurrentes |
| DELETE | `/api/v1/me/availability/rules/{id}` | Borra una franja (404 si es ajena) |
| GET / POST | `/api/v1/me/availability/exceptions` | Bloqueos puntuales (día completo o parcial) |
| DELETE | `/api/v1/me/availability/exceptions/{id}` | Borra un bloqueo (404 si es ajeno) |

Cupos disponibles:

```bash
GET /api/v1/professors/{id}/slots?from=2026-07-15&to=2026-07-15
```

Cualquier usuario autenticado. Sin parámetros el rango es hoy → hoy+6 (7 días); el máximo son
31 días. Un profesor no publicado responde 404 aunque tenga disponibilidad.

```json
{
  "professorId": "…",
  "timezone": "America/Bogota",
  "slots": [
    { "startsAt": "2026-07-15T08:00:00-05:00", "endsAt": "2026-07-15T09:00:00-05:00" }
  ]
}
```

Reglas del cálculo: clases de 60 minutos que empiezan en punto; las franjas y bloqueos se
expresan en hora local de Bogotá; los intervalos son semiabiertos `[inicio, fin)`, así que un
bloqueo de 10:00–11:00 elimina el cupo de las 10:00 pero no el de las 11:00; nunca se devuelven
cupos que ya empezaron.

Reservas y ciclo de vida de la clase:

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/v1/bookings` | STUDENT, ADMIN | Reserva un cupo. Devuelve la reserva **y el ticket de pago** |
| POST | `/api/v1/bookings/{id}/cancel` | dueño o ADMIN | Cancela (body opcional `{"reason": "..."}`) |
| POST | `/api/v1/bookings/{id}/attendance` | PROFESSOR | Registra asistencia de una clase ya terminada |
| GET | `/api/v1/me/bookings?scope=upcoming\|past` | STUDENT, PROFESSOR | Mis clases, con la contraparte |
| POST | `/api/v1/bookings/{id}/reschedule-requests` | dueño | Propone otro horario del mismo profesor |
| POST | `/api/v1/reschedule-requests/{id}/accept` | la otra parte | Acepta y mueve la clase |
| POST | `/api/v1/bookings/{id}/disputes` | STUDENT | «Reportar un problema» |
| GET / POST | `/api/v1/me/bookings/{id}/retraction` | STUDENT | Si el retracto aplica, y ejercerlo |

**Reservar no confirma.** La reserva nace `PENDING_PAYMENT` con el cupo apartado 20 minutos
(`payment_hold_minutes`) y pasa a `CONFIRMED` cuando el webhook firmado de Wompi confirma el cobro
—o de inmediato si el saldo del estudiante cubre la clase entera—. Si nadie paga, un job libera el
cupo. Reservar exige **correo verificado y mayoría de edad declarada**; si no, 422.

**Cancelar siempre se puede**, a cualquier hora y por las dos partes. La ventana
(`student_cancel_hours` / `professor_cancel_hours`, hoy **12 h**) ya no bloquea: decide el dinero.
Fuera de ella, el estudiante recupera el valor completo como saldo; dentro, la clase se considera
prestada y el pago se le libera al profesor. Si cancela el profesor, el estudiante recupera todo
sea cuando sea — y hacerlo dentro de la ventana le registra una **falta** que alimenta la escalera
de sanciones. `canCancel` y `lateCancel` vienen resueltos desde el servidor.

**Todas las clases son virtuales** (V30). El campo `modality` sigue existiendo pero solo admite
`VIRTUAL`; mandar `IN_PERSON` responde 400 en vez de degradar la clase en silencio. Al confirmarse
se crea la sala de videollamada y su enlace viaja por correo.

Registrar asistencia (`{"present": true, "notes": "..."}`) cierra la clase: pasa a `COMPLETED` o a
`NO_SHOW`. Si nadie registra nada, un job la cierra sola a las 24 h (`auto_complete_hours`) y libera
el pago igual.

Dinero:

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/v1/bookings/{id}/payment` | dueño | Estado del pago (acepta `?transactionId=` de Wompi) |
| POST | `/api/v1/webhooks/payments/wompi` | Wompi (firmado) | **La fuente de verdad del cobro** |
| GET | `/api/v1/me/payments`, `/api/v1/me/credits` | STUDENT | Historial y saldo a favor |
| GET | `/api/v1/me/earnings` | PROFESSOR | Retenido, por transferir y transferido |
| GET / POST | `/api/v1/admin/payments`, `/api/v1/admin/payouts` | ADMIN | Conciliación y liquidaciones |
| GET / POST | `/api/v1/admin/refunds` | ADMIN | Devoluciones al medio de pago (retracto) |

La comisión (`commission_rate_bps`, hoy 20 %) se congela en cada reserva y se calcula sobre el
**precio**, nunca sobre lo cobrado: el saldo a favor es un pasivo de Orión y no sale del bolsillo
del profesor. La base garantiza que comisión + ganancia = precio, que saldo + cobrado = precio, que
una clase entre en una sola liquidación y que un webhook reenviado se procese una vez.

Reputación, mensajería y gamificación:

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/v1/bookings/{id}/reviews` | STUDENT | Califica de 1 a 5, una sola vez |
| GET | `/api/v1/me/performance` | PROFESSOR | Métricas, cumplimiento y sanciones activas |
| GET / POST | `/api/v1/conversations` | STUDENT, PROFESSOR | Hilos internos |
| GET | `/api/v1/me/notifications` | autenticado | Campana |
| GET | `/api/v1/me/achievements`, `/api/v1/me/streak` | STUDENT | Logros, racha y avatar |

Legal, soporte y administración:

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/v1/legal/**` | **público** | Términos, política de datos y datos de contacto |
| POST | `/api/v1/auth/verify-email` | público | Confirma el correo con el token del enlace |
| POST | `/api/v1/me/account/adulthood` | autenticado | Declara la mayoría de edad |
| GET / POST | `/api/v1/me/support` | autenticado | Solicitudes de soporte |
| GET / POST | `/api/v1/admin/support` | ADMIN | Bandeja, ordenada por lo que vence antes |
| GET / PUT | `/api/v1/admin/settings` | ADMIN | Ajustes de plataforma, con historial |
| GET / POST | `/api/v1/admin/teacher-applications` | ADMIN | Revisión de postulaciones |

Tres categorías de soporte traen **plazo fijado por ley** y no por nosotros: consulta de datos
personales (10 días hábiles), reclamo de datos personales (15 hábiles) y retracto (15 calendario).

> La lista completa y siempre al día está en Swagger, con el perfil `local`:
> http://localhost:8080/swagger-ui/index.html

**Correos:** cada reserva y cada cancelación envía un correo a cada participante. En desarrollo los
captura Mailpit — ábrelos en http://localhost:8025. Los de confirmación llevan adjunto un `.ics` y
un link para añadir la clase a Google Calendar. Un fallo del servidor de correo **no** afecta a la
reserva: el envío ocurre después del commit y su error solo se registra en el log.

## 3. Levantar el frontend

Requiere Node 20+ (probado con 22). Con el backend arriba:

```bash
cd frontend
npm install
npm run dev          # http://localhost:3000
```

El navegador **solo habla con `:3000`**: Next reescribe `/api/*` y `/actuator/*` hacia el backend
(`API_URL`, por defecto `http://localhost:8080`). Al ser un único origen para el navegador, las
cookies de sesión y de CSRF fluyen sin CORS ni preflights.

Los tipos de TypeScript del API **se generan del backend vivo**, no se escriben a mano:

```bash
npm run types:api    # openapi-typescript contra /v3/api-docs → src/lib/api/schema.d.ts
```

Regenéralos cada vez que cambie un DTO del backend.

Es una **PWA instalable** (Chrome: "Instalar aplicación"): manifest, iconos de marca, theme-color
y un service worker mínimo (`public/sw.js`). No cachea la aplicación —un caché offline es la fuente
clásica de "veo una versión vieja"—; está para que el navegador la considere instalable.

### Suite de humo (Playwright)

```bash
cd frontend
npm run e2e          # levanta next dev solo; requiere backend + docker con la semilla arriba
```

**16 tests** en dos archivos: los caminos que no pueden romperse (`humo.spec.ts`) y la gamificación
(`gamificacion.spec.ts`). Login/logout de cada rol, Ana reserva y el cupo desaparece, María la ve,
Ana cancela y el cupo vuelve, el registro con sus tres casillas y la verificación de correo leyendo
el enlace real del buzón de Mailpit. Corre en viewport móvil.

Dos cosas que hay que saber antes de correrla: **muta estado compartido**, así que una corrida
completa pide `docker compose down -v` antes; y el que pasa por la pasarela **falla sin llaves de
sandbox de Wompi** en el entorno — sin ellas son 15 de 16.

## 4. Tests

```bash
cd backend
./mvnw verify                          # todo: unitarios + integración
./mvnw test                            # solo los unitarios (*Test)
./mvnw verify -Dit.test=AuthFlowIT     # un solo test de integración
```

Hoy son **161 unitarios + 386 de integración**. Los de integración levantan un **PostgreSQL real**
con Testcontainers (no H2), así que Docker debe estar corriendo. No hace falta que la infra de
`docker compose` esté arriba: Testcontainers crea y destruye sus propios contenedores.

En el frontend: `npx tsc --noEmit`, `npm run lint` y `npm run test:unit` (50 tests de Vitest sobre
lógica pura).

## 5. Variables de entorno

### Para desarrollo

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/orion` |
| `DB_USER` | `orion` |
| `DB_PASSWORD` | `orion_local` |
| `ORION_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` |
| `ORION_ADMIN_EMAIL` | `admin@orion.local` |
| `ORION_ADMIN_PASSWORD` | `admin123*` |

En local hay dos cosas que **no se pueden terminar sin credenciales**: reservar exige llaves de
*sandbox* de Wompi (prefijo `_test_`) —la excepción es Ana, que arranca con saldo—, y el asistente
de postulación exige `CLOUDINARY_URL` para la foto y el CV. Sin ella la API responde 503 con un
mensaje legible, no un 500 mudo.

### Obligatorias en producción

El perfil `prod` **se niega a arrancar** sin estas. Es deliberado: publicar Orión sin domicilio de
notificaciones incumple el art. 50 de la Ley 1480, y preferimos no arrancar a arrancar mintiendo.

| Variable | Para qué |
|---|---|
| `ORION_APP_BASE_URL` | Origen del frontend. De aquí salen los enlaces de los correos **y la URL de retorno de Wompi**: si está mal, el pago no vuelve a Orión |
| `ORION_LEGAL_NOMBRE` · `_DOCUMENTO` · `_DOMICILIO` · `_CIUDAD` · `_CORREO` · `_WHATSAPP` · `_HORARIO` | Quién responde por Orión. Sin las siete, no arranca |
| `WOMPI_PUBLIC_KEY` · `WOMPI_INTEGRITY_SECRET` · `WOMPI_EVENTS_SECRET` | Pasarela. Sin ellas, reservar responde 422 |
| `WOMPI_API_BASE_URL` | `https://production.wompi.co/v1`. **El default es el sandbox a propósito** |
| `RESEND_API_KEY` | Producción envía por la API HTTP de Resend, no por SMTP: Railway bloquea el 587 |
| `ORION_MAIL_FROM` | Remitente de todo lo que sale |
| `CLOUDINARY_URL` | Fotos y documentos de las postulaciones |
| `NEXT_PUBLIC_SITE_URL` | Dominio público, para sitemap y OG absolutos |

Con default, pero conviene revisarlas: `ORION_ALERTS_TO` (adónde llegan las alertas de errores y de
procesos caídos) y `NEXT_PUBLIC_SUPPORT_WHATSAPP`.

**El webhook de Wompi** hay que configurarlo en su panel apuntando a
`https://<dominio>/api/v1/webhooks/payments/wompi` — al **dominio del frontend**, no al del backend:
en Railway el backend vive en la red interna y Wompi no lo alcanza. El `rewrite` de Next reenvía
`/api/*` tal cual, así que el evento llega íntegro. Sin esto **ningún pago confirma una clase**: la
redirección del navegador no es la fuente de verdad.

**Producción no corre el `DevDataSeeder`**: la base arranca solo con el administrador.

---

## Y si vienes nuevo

1. [`docs/ESTADO.md`](docs/ESTADO.md) — qué hay construido y desplegado, con las reglas y sus valores.
2. [`CLAUDE.md`](CLAUDE.md) — las decisiones de arquitectura que no se negocian y por qué.
3. [`docs/INFORME-Y-ROADMAP.md`](docs/INFORME-Y-ROADMAP.md) — deuda técnica conocida e ideas.
4. [`docs/briefs/`](docs/briefs) — el alcance cerrado de cada tarea.
