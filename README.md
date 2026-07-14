# Orión — Orion Language Academy

Plataforma web de agendamiento de clases de inglés. Los estudiantes reservan clases
con profesores según su disponibilidad; estudiante y profesor se contactan por WhatsApp.
Sin pagos en el MVP 1.

## Stack

- **Backend:** Java 21, Spring Boot 4.1, Maven (wrapper), monolito modular bajo `co.orion`.
- **Base de datos:** PostgreSQL 16. Flyway es el dueño del esquema (`ddl-auto=validate`).
- **Autenticación:** sesión de servidor con cookie httpOnly + CSRF (sin JWT).
- **Frontend:** pendiente (Tarea 4).

## Estructura

```
backend/    Aplicación Spring Boot
frontend/   Pendiente (Tarea 4)
docs/       Briefs de las tareas
```

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

Reservas:

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/v1/bookings` | STUDENT, ADMIN | Reserva un cupo (el admin, en nombre de un estudiante) |
| POST | `/api/v1/bookings/{id}/cancel` | dueño o ADMIN | Cancela (body opcional `{"reason": "..."}`) |
| POST | `/api/v1/bookings/{id}/attendance` | PROFESSOR | Registra asistencia de una clase ya terminada |
| GET | `/api/v1/me/bookings?scope=upcoming\|past` | STUDENT, PROFESSOR | Mis clases, con la contraparte y su WhatsApp |

Una reserva `CONFIRMED` oculta su cupo; al cancelarla, el cupo reaparece. **Regla de las 24
horas:** estudiantes y profesores solo pueden cancelar con 24 h o más de anticipación (si no,
422); el ADMIN está exento. El campo `canCancel` de "mis clases" ya trae esa decisión resuelta
desde el servidor.

Registrar asistencia (`{"present": true, "notes": "..."}`) cierra la clase: pasa a `COMPLETED`
o a `NO_SHOW`. Solo se puede sobre clases ya terminadas (si no, 422) y una sola vez (si no, 409).

**Correos:** cada reserva y cada cancelación envía un correo a cada participante. En desarrollo
los captura Mailpit — ábrelos en http://localhost:8025. Los de confirmación llevan adjunto un
`.ics` y un link para añadir la clase a Google Calendar. Un fallo del servidor de correo **no**
afecta a la reserva: el envío ocurre después del commit y su error solo se registra en el log.

## 3. Probar a mano

Guía paso a paso con Postman, con todos los casos de error: [docs/postman-guide.md](docs/postman-guide.md)

## 4. Tests

```bash
cd backend
./mvnw verify                          # todo: unitarios + integración
./mvnw test                            # solo los unitarios (*Test)
./mvnw verify -Dit.test=AuthFlowIT     # un solo test de integración
```

Los tests levantan un **PostgreSQL real** con Testcontainers (no H2), así que Docker debe
estar corriendo. No hace falta que la infra de `docker compose` esté arriba: Testcontainers
crea y destruye sus propios contenedores.

## 5. Variables de entorno

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/orion` |
| `DB_USER` | `orion` |
| `DB_PASSWORD` | `orion_local` |
| `ORION_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` |
| `ORION_ADMIN_EMAIL` | `admin@orion.local` |
| `ORION_ADMIN_PASSWORD` | `admin123*` |
