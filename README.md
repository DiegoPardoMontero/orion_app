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

## 3. Tests

```bash
cd backend
./mvnw verify                          # todo: unitarios + integración
./mvnw test                            # solo los unitarios (*Test)
./mvnw verify -Dit.test=AuthFlowIT     # un solo test de integración
```

Los tests levantan un **PostgreSQL real** con Testcontainers (no H2), así que Docker debe
estar corriendo. No hace falta que la infra de `docker compose` esté arriba: Testcontainers
crea y destruye sus propios contenedores.

## 4. Variables de entorno

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/orion` |
| `DB_USER` | `orion` |
| `DB_PASSWORD` | `orion_local` |
| `ORION_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` |
| `ORION_ADMIN_EMAIL` | `admin@orion.local` |
| `ORION_ADMIN_PASSWORD` | `admin123*` |
