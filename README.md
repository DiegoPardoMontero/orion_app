# Orión — Orion Language Academy

Plataforma web de agendamiento de clases de inglés. Los estudiantes reservan clases
con profesores según su disponibilidad; estudiante y profesor se contactan por WhatsApp.
Sin pagos en el MVP 1.

## Stack

- **Backend:** Java 21, Spring Boot 4.1, Maven (wrapper), monolito modular bajo `co.orion`.
- **Base de datos:** PostgreSQL 16. Flyway es el dueño del esquema (`ddl-auto=validate`).
- **Frontend:** pendiente (Tarea 4).

## Estructura

```
backend/    Aplicación Spring Boot
frontend/   Pendiente (Tarea 4)
docs/       Briefs de las tareas
```

## Infraestructura local

Requisitos: Docker (con Compose) y JDK 21.

```bash
docker compose up -d
docker compose ps      # postgres debe quedar "healthy"
```

Servicios:

| Servicio | Puerto | Para qué |
|---|---|---|
| PostgreSQL 16 | 5432 | Base de datos (`orion` / `orion` / `orion_local`) |
| Mailpit (SMTP) | 1025 | Servidor SMTP falso para desarrollo |
| Mailpit (web) | 8025 | Bandeja de entrada en http://localhost:8025 |

Mailpit captura todo correo que la aplicación "envíe" en desarrollo, sin configurar
nada real. Se usará desde la Tarea 3.

Para apagar la infraestructura:

```bash
docker compose down       # conserva los datos
docker compose down -v    # borra también el volumen de Postgres
```

## Backend

Pendiente (Paso 1 de la Tarea 1).
