# Brief · Tarea 5 — Despliegue a producción y arranque del piloto

**Prerequisito:** Tareas 1–4 completas (DoD en verde). Plataforma elegida: Railway.
**Resultado de esta tarea:** el MVP 1 corriendo en una URL pública con HTTPS y dominio propio, correos reales que llegan a bandejas reales, PWA instalable desde internet, backend inaccesible desde afuera (solo vía el proxy del frontend), admin de producción creado, runbook operativo y el piloto con usuarios reales de Sofía definido y arrancado.

---

## 0. Modo de trabajo — división nueva: código vs. consola

1. **Pasos de CÓDIGO (0 y 1):** los ejecuta Claude Code con las reglas de siempre (un paso a la vez, DETENTE, explicar, un commit por paso, `./mvnw verify` verde).
2. **Pasos de CONSOLA (2, 3 y 4):** los ejecuta **Pardo** siguiendo la checklist; Claude Code asiste (genera valores, verifica configs, diagnostica logs) pero los clicks y las credenciales son de Pardo.
3. **Secretos:** jamás se commitean ni se pegan en el chat de Claude Code. Viven solo en las variables de entorno de Railway y en el gestor de contraseñas de Pardo.
4. Los detalles de UI de los paneles (Railway, Resend, registrador de dominio) cambian con el tiempo: la checklist define el **qué**; el **dónde exacto está el botón** se resuelve con la documentación vigente de cada plataforma.

---

## 1. Decisiones de despliegue — LEER ANTES DE TOCAR NADA

1. **Plataforma del piloto: Railway**, un proyecto con 3 servicios: Postgres gestionado, backend y frontend. Docker-first = cero lock-in: la futura migración a AWS (ECS + RDS), prevista para cuando haya tracción, reutiliza estas mismas imágenes.
2. **Topología — el backend no existe para internet.** Solo el frontend tiene dominio público. El backend no recibe dominio: el frontend le habla por la red privada de Railway (`API_URL=http://backend.railway.internal:8080`). Consecuencia de la arquitectura same-origin de la Tarea 4: la API solo es alcanzable a través del proxy de Next → superficie de ataque mínima sin escribir una línea extra.
3. **TLS termina en el edge de Railway**; el backend ve HTTP plano. Por eso el perfil prod activa `server.forward-headers-strategy: framework`: Spring confía en los headers `X-Forwarded-*` y así las cookies `Secure` y las URLs generadas salen correctas. Sin esto, todo parece funcionar hasta que las cookies no llegan.
4. **Cookies en producción:** `Secure=true`, `SameSite=Lax`, mismo nombre. El CSRF no cambia (seguimos siendo same-origin).
5. **Correo real por SMTP (Resend):** mismas propiedades `spring.mail.*` de siempre — solo cambian los valores por variables de entorno. Requisito no negociable: dominio verificado con SPF/DKIM, o los correos de Orión mueren en spam.
6. **La semilla local no existe en producción** (`@Profile("local")`). El admin de producción lo crea un `AdminBootstrap` idempotente desde variables de entorno. Todos los demás usuarios de producción son personas reales creadas desde el panel admin.
7. **Flyway migra solo en el primer arranque.** Nada de SQL manual contra producción, ni ahora ni nunca.
8. **Config 100 % por variables de entorno.** Tabla completa en el Paso 2. Ningún valor de producción en el repo.
9. **Región:** US East (la más cercana a Colombia entre las disponibles).
10. **Un solo entorno (producción) durante el piloto.** Staging llega post-piloto si el proyecto lo amerita; a este volumen sería burocracia.

---

## 2. Paso 0 — Backend listo para producción (código)

**`application-prod.yml`** — contenido exacto:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

server:
  forward-headers-strategy: framework
  servlet:
    session:
      cookie:
        name: ORION_SESSION
        http-only: true
        secure: true
        same-site: lax

orion:
  mail:
    from: ${ORION_MAIL_FROM}
  cors:
    allowed-origins: ${ORION_CORS_ALLOWED_ORIGINS:}

management:
  endpoints:
    web:
      exposure:
        include: health

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

**`AdminBootstrap`** (`identity`, `@Profile("prod")`, `ApplicationRunner`):
- Si no existe ningún usuario con rol `ADMIN` → lo crea con `ORION_ADMIN_EMAIL` y `ORION_ADMIN_PASSWORD` (BCrypt).
- Si las variables faltan y no hay admin → **falla el arranque** con mensaje claro (fail fast: mejor un deploy caído que una plataforma sin puerta de entrada).
- Idempotente: con un admin existente no hace nada (aunque cambien las env).
- Tests: crea cuando no hay admin; no duplica cuando ya hay; falla sin variables.

Verificar además que en perfil prod: swagger devuelve 404, el seeder no corre, `/actuator/health` responde sin autenticación y nada más del actuator está expuesto.

**Verificación local (Pardo):** con el compose de dev arriba, arrancar con `SPRING_PROFILES_ACTIVE=prod` y las env mínimas exportadas (DB_*, ORION_ADMIN_*, MAIL_* apuntando a Mailpit) → arranca, crea el admin, login del admin funciona, swagger 404.

**DETENTE aquí y espera confirmación.**

---

## 3. Paso 1 — Dockerfiles y ensayo general local (código)

**`backend/Dockerfile`** — exacto:

```dockerfile
# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S orion && adduser -S orion -G orion
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER orion
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

Notas didácticas: multi-stage (la imagen final no lleva Maven ni código fuente), usuario no-root, `MaxRAMPercentage` para que la JVM respete la memoria del contenedor, `-DskipTests` porque los tests ya corrieron en `verify` (la imagen no es el lugar para descubrir tests rotos).

**`frontend/`:** añadir `output: 'standalone'` en `next.config` y crear el Dockerfile exacto:

```dockerfile
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:24-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
RUN addgroup -S orion && adduser -S orion -G orion
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
USER orion
EXPOSE 3000
CMD ["node", "server.js"]
```

Gotchas a explicar: `HOSTNAME=0.0.0.0` (el server standalone de Next escucha en localhost por defecto y dentro de un contenedor eso significa "nadie me alcanza"); `API_URL` se lee **al arrancar** el servidor, no al compilar — el mismo build sirve en cualquier entorno. Crear `.dockerignore` en ambos (`target/`, `node_modules/`, `.next/`, `.env*`).

**`docker-compose.prod.yml`** (raíz, solo para el ensayo local): postgres + mailpit + los **dos servicios construidos desde sus Dockerfiles**, backend con `SPRING_PROFILES_ACTIVE=prod` y env completas (mail → mailpit), frontend con `API_URL=http://backend:8080` y puerto 3000 publicado.

**Verificación (el ensayo general):** `docker compose -f docker-compose.prod.yml up --build` → en `http://localhost:3000`, con imágenes de producción: login del admin (creado por el bootstrap, no por semilla), crear un profesor, configurar disponibilidad, reservar como estudiante, correo en Mailpit, cancelar. Si esto pasa, Railway es solo cambiar dónde corren estas mismas imágenes.

**DETENTE aquí y espera confirmación.**

---

## 4. Paso 2 — Proyecto en Railway (consola, Pardo)

Checklist en orden:

1. Repo en GitHub (privado) con todo pusheado.
2. Cuenta Railway con método de pago; **configurar el límite/alerta de gasto** desde el primer día.
3. Proyecto `orion`, región US East.
4. Añadir **Postgres** (servicio gestionado del catálogo).
5. Servicio **backend**: desde el repo, root directory `/backend` (detecta el Dockerfile). Healthcheck: `/actuator/health`. **No generarle dominio público.**
6. Servicio **frontend**: root directory `/frontend`. Generar el dominio temporal `*.up.railway.app`.
7. Variables de entorno — tabla exacta:

**Backend:**

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DB_USER` | `${{Postgres.PGUSER}}` |
| `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `ORION_ADMIN_EMAIL` | el correo real de Pardo |
| `ORION_ADMIN_PASSWORD` | generado fuerte (gestor de contraseñas) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | valores dummy por ahora (el Paso 3 los vuelve reales)* |
| `ORION_MAIL_FROM` | `no-reply@orion.local` por ahora |
| `ORION_CORS_ALLOWED_ORIGINS` | la URL pública del frontend |

\* Con SMTP dummy, enviar correos falla — pero por la decisión 8 de la Tarea 3 (AFTER_COMMIT + try/catch) las reservas sobreviven y el error solo queda en logs. Es la primera verificación en producción de esa decisión.

**Frontend:**

| Variable | Valor |
|---|---|
| `API_URL` | `http://backend.railway.internal:8080` |

**Verificación:** en la URL `*.up.railway.app`: login con el admin (lo creó el bootstrap — revisar logs del backend), crear un profesor de prueba, disponibilidad, reservar como estudiante de prueba. El correo aún no llega: esperado. Confirmar en logs que Flyway aplicó V1–V4 y que el backend **no** responde desde internet (no tiene dominio).

**DETENTE aquí y espera confirmación.**

---

## 5. Paso 3 — Correo real (consola, Pardo)

1. Cuenta en Resend → añadir el dominio → publicar los registros **SPF y DKIM** que indique (en el DNS del registrador) → esperar verificación. Añadir un DMARC básico (`v=DMARC1; p=none;`).
2. Crear credenciales SMTP y reemplazar en Railway: `MAIL_HOST=smtp.resend.com`, `MAIL_PORT=587`, usuario/clave según el panel, `ORION_MAIL_FROM=notificaciones@<dominio>`.
3. Redeploy del backend.

**Verificación:** reserva de prueba con un estudiante cuyo email sea el Gmail real de Pardo → el correo llega a la bandeja (no a spam), con el .ics adjunto (abrirlo en el teléfono: crea el evento) y el link de Google Calendar precargando la hora correcta de Bogotá. Cancelar → llega el segundo correo.

**DETENTE aquí y espera confirmación.**

---

## 6. Paso 4 — Dominio propio, HTTPS, PWA y vigilancia (consola, Pardo)

1. En el DNS: `CNAME app.<dominio>` → el dominio del frontend en Railway; añadir `app.<dominio>` como custom domain del servicio (TLS automático).
2. Actualizar `ORION_CORS_ALLOWED_ORIGINS` a `https://app.<dominio>`.
3. En el navegador (devtools → Application → Cookies): `ORION_SESSION` y `XSRF-TOKEN` con el flag **Secure** ✓ tras iniciar sesión por HTTPS.
4. Instalar la **PWA desde el teléfono** con la URL pública (ahora con HTTPS real es instalable de verdad) y hacer una reserva desde la app instalada.
5. Monitor gratuito (UptimeRobot o similar) → `https://app.<dominio>/actuator/health` cada 5 minutos, alerta al correo de Pardo.

**Verificación:** checklist 1–5 completa.

**DETENTE aquí y espera confirmación.**

---

## 7. Paso 5 — Runbook y arranque del piloto

**`docs/RUNBOOK.md`** (lo redacta Claude Code, lo valida Pardo ejecutando cada procedimiento una vez):
- Ver logs de cada servicio; forzar redeploy; hacer rollback al deploy anterior.
- Backup manual: comando `pg_dump` exacto contra el Postgres de Railway y dónde guardar el archivo. Verificar en el panel qué backups automáticos incluye el plan y anotarlo.
- Restaurar un backup (procedimiento escrito, aunque ojalá nunca se use).
- Rotar `ORION_ADMIN_PASSWORD` y credenciales SMTP.
- Qué hacer si el sitio no responde (orden de diagnóstico: monitor → logs frontend → logs backend → estado Postgres).

**Preparación del piloto:**
- Tomar el **primer backup manual** y guardarlo.
- Crear los usuarios reales: los 2–3 profesores de Sofía (con sus fotos vía URL, perfil publicado) y una cuenta ADMIN para Sofía **si** va a operar el panel (decisión de Pardo).
- Definir con Sofía el guion: 2–3 profesores, 5–10 estudiantes reales, 3–4 semanas; métrica de éxito = **% de reservas autoservicio** (la trae el panel admin) + feedback cualitativo semanal; canal de soporte: WhatsApp.
- Agendar con Sofía la conversación pendiente que destraba el MVP 2: **precio por clase, compensación real a profesores y volumen exacto actual.**

**Fin de la Tarea 5: la primera reserva hecha por un usuario real sin intervención de nadie.** 🎉

---

## 8. Definition of done (checklist final)

- [ ] Ensayo general local con imágenes de producción completo (Paso 1)
- [ ] Railway: 3 servicios arriba, backend sin dominio público, Flyway V1–V4 aplicado, admin creado por bootstrap
- [ ] Correo real verificado: bandeja de entrada (no spam), .ics funcional, link de Calendar correcto
- [ ] `https://app.<dominio>` operativo; cookies con Secure ✓; CORS actualizado
- [ ] PWA instalada en un teléfono real desde la URL pública, con una reserva hecha desde ella
- [ ] Monitor de uptime activo con alertas
- [ ] RUNBOOK.md commiteado y cada procedimiento ejecutado una vez; primer backup guardado
- [ ] Usuarios reales creados; guion del piloto acordado con Sofía; conversación de precios agendada
- [ ] Límite/alerta de gasto configurado en Railway; ningún secreto en el repo

## 9. Fuera de alcance de esta tarea — NO construir

CI/CD (GitHub Actions), entorno de staging, migración a AWS (ECS/RDS — llega con tracción), CDN/WAF, APM o Sentry, rate limiting, recordatorios programados de clase, autoescalado, subida de archivos. Si algo parece faltar para "producción seria", se anota para post-piloto y se pregunta antes de agregarlo.
