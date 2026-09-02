# Brief maestro — Orión Marketplace (MVP 2–4)

> **Autor:** arquitectura (Claude) · **Owner:** Pardo · **Ejecuta:** Claude Code
> **Origen:** documento de producto de Sofía (`ORIONIDIOMAS.COM`, secciones Portada,
> Homepage, Portal Estudiante, Portal Profesor, Reglas Orión).
> **Ubicación:** `docs/briefs/orion-brief-maestro-marketplace.md`

---

## 0. Cómo se usa este documento

**Esto no es un prompt para pegar de una sola vez.** El documento de Sofía describe
un producto entre 4 y 6 veces más grande que el MVP 1 que está en producción, y cambia
el modelo de negocio, no solo la funcionalidad. Pegarlo completo en Claude Code
produciría un diff imposible de revisar y un esquema de base de datos que nadie diseñó.

El brief está partido en **siete bloques ejecutables**. Cada bloque es autocontenido:
tiene su alcance, sus migraciones exactas, sus contratos de endpoints, sus reglas de
negocio, sus tests exigidos y sus **DETENTE**. La forma de trabajar es la de siempre:

1. Pardo abre una sesión de Claude Code y pega **solo el bloque que toca**, precedido
   de las secciones 1 a 4 de este documento (contexto, delta, decisiones y convenciones).
2. Claude Code ejecuta paso a paso y **se detiene** en cada DETENTE.
3. Pardo revisa el diff, aprueba o corrige, y solo entonces continúa.

**Regla permanente para Claude Code:** si algo de este brief contradice el código
existente, o si una decisión de producto no está escrita aquí, **pregunta antes de
inventar**. No improvises reglas de negocio, precios, textos legales ni estados.

---

## 1. Estado actual del sistema (lo que ya existe y no se rehace)

Para que Claude Code no reconstruya lo que ya funciona:

| Área | Estado |
|---|---|
| Backend | Spring Boot 4.1.x · Java 21 · monolito modular `co.orion` con módulos `identity`, `scheduling`, `notifications`, `shared` |
| Base de datos | PostgreSQL 16 · **Flyway es el único dueño del esquema** (`ddl-auto=validate`) · migraciones hasta ~V9 (Pulido v1) |
| Auth | Sesión de servidor con cookie httpOnly + CSRF (`XSRF-TOKEN` / `X-XSRF-TOKEN`) · registro público y verificación de email (Fase B del rediseño) |
| Dominio existente | `users`, `professor_profiles`, `availability_rules`, `availability_exceptions`, `bookings`, `attendance_records`, `auth_tokens` |
| Reglas vivas | Regla de 24 h para cancelación del estudiante · índice único parcial anti doble-reserva · UTC en almacenamiento, `America/Bogota` como zona de negocio · `Clock` inyectable |
| Correo | Resend (prod) / Mailpit (local) · disparado con `@TransactionalEventListener(AFTER_COMMIT)` — **un fallo de correo jamás revierte una escritura de negocio** |
| Frontend | Next.js PWA · proxy same-origin (el backend no tiene dominio público) · tipos generados del OpenAPI · Playwright E2E |
| Diseño | Sistema «Amanecer cálido premium» con tokens Tailwind v4 `@theme` + mascota |
| Media | Cloudinary (fotos públicas) · Jitsi tras `MeetingLinkProvider` |

**Todo lo anterior se conserva.** Este brief construye encima; no reescribe la
fundación.

---

## 2. El delta — qué cambia respecto a lo que hay hoy

Esta sección existe porque el documento de Sofía **contradice decisiones vigentes**.
Claude Code debe conocer estos conflictos para no romper cosas por accidente.

| # | Hoy | El documento pide | Consecuencia |
|---|---|---|---|
| D1 | Solo inglés | Inglés, francés, español, y "mañana alemán sin rediseñar" | El idioma deja de ser implícito y pasa a ser una **entidad de primera clase** (catálogo + relación con profesor + filtro + ruta pública) |
| D2 | Precio institucional; profesor con **tarifa fija** | El profesor fija su precio; Orión cobra **20 %** de comisión | Cambio de modelo económico. Afecta contratos con los profesores actuales, no solo el código |
| D3 | Profesores **por invitación** del admin (Pulido v1) | Registro abierto + **Teacher Application** con revisión del admin | Los dos flujos deben convivir: el invitado nace aprobado, el espontáneo pasa por revisión |
| D4 | Comunicación por **WhatsApp** (links `wa.me` en correos y perfiles) | Mensajería **dentro** de Orión; prohibido WhatsApp como canal | Hay que **retirar** los `wa.me` de correos y UI. Es una eliminación deliberada, no un olvido |
| D5 | Sin pagos: la clase se reserva y se paga fuera | Estudiante paga a Orión → Orión retiene → clase dictada → profesor cobra | Aparece un estado `PENDING_PAYMENT` en `bookings` y un módulo `billing` completo |
| D6 | Cancelación: **24 h** (estudiante) | Cancelación: **12 h** (profesor) + reprogramación | Son dos reglas distintas para dos actores distintos. **Ver decisión Q3** |
| D7 | Sin reseñas ni métricas | Reviews, métricas de desempeño, ranking, sanciones | Módulo nuevo `reputation` con job nocturno de recálculo |
| D8 | Home = landing de academia de inglés | Home = **marketplace de profesores** con buscador de 3 campos | Rediseño de la portada y de la navegación pública |
| D9 | UI en español (Colombia) | Todo el copy del documento está **en inglés** | **Ver decisión Q2** — es una decisión, no un detalle |

### Conflicto de secuencia que hay que resolver antes de empezar

El rediseño Design v2 (Fase C) planea **reimplementar** `/profesores` y
`/profesores/[id]`. Este brief **vuelve a cambiar esas mismas pantallas** (filtros,
precio, idioma, botón "Enviar mensaje", reseñas). Construirlas dos veces es dinero
tirado.

**Recomendación de arquitectura:**

- Terminar **Fase A** (integrar tokens y sistema de diseño) y **Fase B** (auth pública):
  ambas son fundación y no chocan con nada de aquí.
- Terminar **Pulido v1** (fotos, teléfonos E.164, Jitsi): son cimientos que este brief
  usa.
- **Fusionar Fase C con el Bloque 1** de este brief: que las pantallas del marketplace
  se dibujen y se construyan **una sola vez**, ya con precio, idioma y filtros.
- Pedirle a Claude Design las pantallas nuevas de este brief (aplicación de profesor,
  bandeja de mensajes, checkout, reseñas) **en un solo encargo**, no de a una.

---

## 3. Decisiones bloqueantes — responder antes de ejecutar

Cada una tiene mi recomendación por defecto. Si Pardo no responde, Claude Code **usa
la recomendación y lo deja anotado en el PR**; no elige por su cuenta otra cosa.

| # | Pregunta | Recomendación por defecto |
|---|---|---|
| **Q1** | ¿El profesor fija su precio y se le paga por comisión, reemplazando la tarifa fija actual? Esto **cambia el acuerdo con los profesores que ya trabajan con Sofía**. | Sí, pero con **transición**: los profesores actuales conservan su tarifa fija hasta una fecha acordada; el modelo de comisión aplica a los nuevos. Modelable con `professor_profiles.compensation_model` (`FIXED_FEE` \| `COMMISSION`) |
| **Q2** | ¿Idioma de la interfaz? El documento está en inglés; la app está en español y el mercado es Colombia. | **es-CO como idioma de la interfaz**; las frases en inglés (*"Find the right teacher. Learn your way."*) se usan como **eslogan de marca**, no como UI. Preparar el código con claves de i18n para no repetir el trabajo cuando llegue el inglés real |
| **Q3** | Cancelación: ¿12 h para todos o 24 h estudiante / 12 h profesor? | **Asimétrica y explícita:** estudiante 24 h (regla vigente, ya comunicada), profesor 12 h + reprogramación obligatoria por debajo. Ambos umbrales en `platform_settings`, no hardcodeados |
| **Q4** | ¿Qué pasarela de pago y **a nombre de quién** llega el dinero? Retener plata de terceros y pagarles después tiene implicaciones tributarias y contractuales en Colombia (facturación electrónica, retención en la fuente a personas naturales, contrato de mandato o de cuenta de recaudo). | **Wompi** para el recaudo (PSE + tarjeta + Nequi, integración local sencilla). **Los pagos a profesores NO se automatizan en este MVP**: el sistema genera un *reporte de liquidación* y Pardo/Sofía transfieren manualmente. Hablar con contador **antes** de escribir el Bloque 4 |
| **Q5** | ¿Verificación de teléfono obligatoria antes de aplicar como profesor? | **No en el lanzamiento** — el SMS/WhatsApp OTP cuesta e integrarlo bloquea oferta. Email verificado sí es obligatorio; el teléfono se marca verificado manualmente por el admin durante la revisión. Dejar el campo y el flag listos (`platform_settings.require_phone_verification = false`) |
| **Q6** | ¿La comisión es 20 % fija para todos? | Sí, pero **almacenada como snapshot por reserva** (`commission_rate_bps`) para que un cambio futuro no reescriba la historia contable |
| **Q7** | ¿"Trial lesson" existe en el lanzamiento? | Sí, como **flag** `is_trial` en la reserva, con precio libre del profesor y la misma comisión del 20 % (como pide el documento). Máximo una prueba por par estudiante–profesor |
| **Q8** | En código y base de datos hoy todo se llama `professor`; el documento dice `teacher`. | **Conservar `professor`** en código, tablas y API. "Profesor"/"teacher" es solo copy de interfaz. Renombrar 40 archivos no aporta valor y rompe el OpenAPI |
| **Q9** | ¿Los estudiantes pueden estar fuera de Colombia? | No en este ciclo. **Moneda única COP**, sin decimales. Si algún día hay multi-moneda, la columna de snapshot ya lo permite |
| **Q10** | ¿Quién puede ver el CV y los documentos de un profesor? | **Solo el admin.** Almacenamiento privado con URL firmada de corta duración. Nunca URL pública, ni siquiera "difícil de adivinar" |

---

## 4. Convenciones obligatorias para todos los bloques

**Dinero.** `BIGINT` con **pesos colombianos enteros** (`amount_cop`). Nunca `double`,
nunca `float`. La comisión se guarda en **puntos básicos** (`commission_rate_bps`,
20 % = `2000`) y se calcula con enteros: `commission = amount * bps / 10000`, redondeo
**hacia abajo** al peso; la diferencia queda a favor del profesor. Todo importe
relevante para la contabilidad se **congela en la fila** (snapshot) al momento del
hecho económico: si mañana cambia la comisión, el histórico no se mueve.

**Estados.** `VARCHAR` + `CHECK`, nunca `ENUM` nativo de Postgres (evolucionar un enum
nativo exige `ALTER TYPE` con restricciones molestas). En Java, enums con
`@Enumerated(EnumType.STRING)`.

**Transiciones de estado.** Toda máquina de estados de este brief (aplicación de
profesor, pago, reserva, disputa) se implementa con un **método de transición explícito
en el servicio de dominio** que valida el estado origen. Prohibido `setStatus()` público
desde un controlador.

**Auditoría.** Todo cambio de estado con consecuencia económica o reputacional deja
fila en una tabla de eventos con `actor_id`, `created_at` y nota. No se sobrescribe
historia.

**Tiempo.** `TIMESTAMPTZ` en UTC, `America/Bogota` para toda decisión y presentación.
El `Clock` inyectable es obligatorio en cualquier regla que compare contra "ahora"
(cancelación, expiración de reserva, ventana de no-show): sin él los tests son
imposibles de escribir bien.

**Concurrencia.** Cualquier regla que dependa de "no puede haber dos" se garantiza con
un **constraint en la base**, no con un `if` en Java. El índice único parcial de
`bookings` es el patrón a imitar.

**Correo y notificaciones.** Se disparan **siempre** con
`@TransactionalEventListener(phase = AFTER_COMMIT)`. Ningún fallo de notificación puede
revertir un pago, una reserva o una aprobación.

**Idempotencia.** Todo webhook y todo job programado es idempotente: clave única de
evento en base de datos, y columna `*_at` que marca "ya se hizo" (el patrón de
`reminder_sent_at`).

**Seguridad de autorización.** Cada endpoint nuevo declara explícitamente quién puede
llamarlo, y la prueba de que un rol ajeno recibe `403` es **parte de los tests
exigidos**, no opcional.

**Migraciones.** Los números `V10+` de este brief asumen que Pulido v1 está mergeado.
Claude Code **verifica el último número aplicado** antes de crear archivos y ajusta la
numeración conservando los nombres.

---

## 5. Mapa de los siete bloques

| Bloque | Contenido | Tamaño | Depende de |
|---|---|---|---|
| **1** | Idiomas, precio del profesor, taxonomía y buscador del marketplace | L | Pulido v1 · Fase A |
| **2** | Teacher Application + panel de revisión del admin + documentos | L | Bloque 1 |
| **3** | Mensajería interna, notificaciones y política de contacto | L | Fase B (auth pública) |
| **4** | Pagos, comisión, créditos y liquidación a profesores | XL | Bloques 1 y 2 · **Q4 respondida** |
| **5** | Ciclo de vida de la clase: reprogramación, no-show, disputas | M | Bloques 3 y 4 |
| **6** | Reseñas, métricas de desempeño, ranking y sanciones | M | Bloque 5 |
| **7** | Nueva portada marketplace, navegación y "Enseña en Orión" | M | Bloque 1 (para datos reales) |

**Orden recomendado:** 1 → 2 → 3 → 7 → 4 → 5 → 6.

El 7 se adelanta a propósito: la portada nueva es lo que hace que el dinero de Google
Ads valga la pena, y solo necesita el Bloque 1 para tener datos verdaderos que mostrar.
El 4 se retrasa a propósito: es el único bloqueado por una conversación que no es de
ingeniería (Q4).

---

# BLOQUE 1 — Idiomas, precio y buscador del marketplace

**Objetivo:** que Orión deje de ser "una academia de inglés" y pase a ser una
plataforma donde un profesor enseña *uno o más idiomas*, a *ciertos niveles*, para
*ciertos objetivos*, a *su propio precio* — y que un estudiante pueda encontrarlo
filtrando por todo eso.

## 1.1 Migración `V10__marketplace_taxonomy.sql`

```sql
-- Catálogo de idiomas: tabla, no enum, para que agregar alemán sea un INSERT
CREATE TABLE languages (
    code          VARCHAR(5)  PRIMARY KEY,
    name_es       VARCHAR(60) NOT NULL,
    name_en       VARCHAR(60) NOT NULL,
    flag_emoji    VARCHAR(8),
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    display_order SMALLINT    NOT NULL DEFAULT 0
);

INSERT INTO languages (code, name_es, name_en, flag_emoji, display_order) VALUES
    ('EN', 'Inglés',  'English', '🇬🇧', 1),
    ('FR', 'Francés', 'French',  '🇫🇷', 2),
    ('ES', 'Español', 'Spanish', '🇪🇸', 3);

-- Objetivos de aprendizaje (los del documento, sección FILTROS)
CREATE TABLE teaching_goals (
    code          VARCHAR(30) PRIMARY KEY,
    name_es       VARCHAR(60) NOT NULL,
    name_en       VARCHAR(60) NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    display_order SMALLINT    NOT NULL DEFAULT 0
);

INSERT INTO teaching_goals (code, name_es, name_en, display_order) VALUES
    ('CONVERSATION', 'Conversación',        'Conversation',     1),
    ('TRAVEL',       'Viajes',              'Travel',           2),
    ('BUSINESS',     'Negocios',            'Business',         3),
    ('ACADEMIC',     'Académico',           'Academic',         4),
    ('EXAMS',        'Exámenes',            'Exams',            5),
    ('INTERVIEW',    'Entrevistas',         'Interview',        6),
    ('GENERAL',      'Aprendizaje general', 'General learning', 7);

-- Qué idiomas enseña cada profesor
CREATE TABLE professor_languages (
    professor_id  UUID       NOT NULL REFERENCES professor_profiles(user_id) ON DELETE CASCADE,
    language_code VARCHAR(5) NOT NULL REFERENCES languages(code),
    is_native     BOOLEAN    NOT NULL DEFAULT false,
    PRIMARY KEY (professor_id, language_code)
);

-- A qué niveles, por idioma
CREATE TABLE professor_language_levels (
    professor_id  UUID        NOT NULL,
    language_code VARCHAR(5)  NOT NULL,
    level         VARCHAR(20) NOT NULL
                  CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    PRIMARY KEY (professor_id, language_code, level),
    FOREIGN KEY (professor_id, language_code)
        REFERENCES professor_languages(professor_id, language_code) ON DELETE CASCADE
);

-- Para qué objetivos
CREATE TABLE professor_goals (
    professor_id UUID        NOT NULL REFERENCES professor_profiles(user_id) ON DELETE CASCADE,
    goal_code    VARCHAR(30) NOT NULL REFERENCES teaching_goals(code),
    PRIMARY KEY (professor_id, goal_code)
);

-- Perfil enriquecido y precio
ALTER TABLE professor_profiles
    ADD COLUMN hourly_rate_cop     BIGINT,
    ADD COLUMN compensation_model  VARCHAR(20) NOT NULL DEFAULT 'FIXED_FEE'
                                   CHECK (compensation_model IN ('FIXED_FEE', 'COMMISSION')),
    ADD COLUMN country_code        VARCHAR(2),
    ADD COLUMN city                VARCHAR(80),
    ADD COLUMN native_language     VARCHAR(5) REFERENCES languages(code),
    ADD COLUMN years_experience    SMALLINT,
    ADD COLUMN education           VARCHAR(300),
    ADD COLUMN is_certified        BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN accepts_trial       BOOLEAN NOT NULL DEFAULT true,
    ADD CONSTRAINT chk_hourly_rate
        CHECK (hourly_rate_cop IS NULL
               OR (hourly_rate_cop >= 20000 AND hourly_rate_cop <= 500000));

CREATE INDEX idx_prof_lang ON professor_languages(language_code);
CREATE INDEX idx_prof_rate ON professor_profiles(hourly_rate_cop) WHERE is_published;

-- Ajustes de plataforma: todo umbral de negocio vive aquí, no en el código
CREATE TABLE platform_settings (
    key        VARCHAR(60) PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES users(id)
);

INSERT INTO platform_settings (key, value) VALUES
    ('commission_rate_bps',            '2000'),
    ('student_cancel_hours',           '24'),
    ('professor_cancel_hours',         '12'),
    ('no_show_report_minutes',         '15'),
    ('payment_hold_minutes',           '20'),
    ('auto_complete_hours',            '24'),
    ('require_phone_verification',     'false'),
    ('contact_policy_mode',            'MASK');
```

**Notas de diseño para explicarle a Pardo:**

- `languages` como tabla y no como enum es *exactamente* lo que pide el documento
  cuando dice "cuando mañana quieras agregar alemán, no tienes que rediseñar la
  identidad del negocio". Agregar alemán = un `INSERT`, cero despliegues de esquema.
- El `CHECK` del precio no es paternalismo: evita que un profesor publique
  `$50` o `$50.000.000` por un cero de más y contamine el buscador. Los límites viven
  en la migración porque son integridad, no política; la política (comisión, horas)
  vive en `platform_settings`.
- `compensation_model` es la respuesta a **Q1**: permite que los profesores actuales
  de Sofía sigan con tarifa fija mientras los nuevos entran por comisión, sin dos
  sistemas paralelos.

## 1.2 Backend

Módulo nuevo **`catalog`** (idiomas, objetivos, ajustes) y ampliación de `identity`
para el perfil enriquecido.

| Método y ruta | Quién | Para qué |
|---|---|---|
| `GET /api/v1/catalog/languages` | Público | Idiomas activos, ordenados |
| `GET /api/v1/catalog/goals` | Público | Objetivos activos |
| `GET /api/v1/professors` | Público | **Buscador con filtros** (ver abajo) |
| `GET /api/v1/professors/{id}` | Público | Perfil completo con idiomas, niveles, objetivos y precio |
| `PUT /api/v1/me/profile` | Profesor | Ampliado: idiomas, niveles, objetivos, país/ciudad, experiencia, educación |
| `PUT /api/v1/me/profile/rate` | Profesor | Fijar tarifa; **responde con el desglose** |
| `GET /api/v1/admin/settings` · `PUT` | Admin | Leer y ajustar `platform_settings` |

**Buscador — `GET /api/v1/professors`.** Parámetros, todos opcionales y combinables:

```
language=EN                     goal=CONVERSATION,BUSINESS
level=BEGINNER                  minPrice=30000   maxPrice=80000
native=true                     certified=true
availableDay=MON,TUE            availableTime=MORNING|AFTERNOON|EVENING
page=0  size=12  sort=RELEVANCE|PRICE_ASC|PRICE_DESC|RATING
```

Reglas del buscador:

- Solo devuelve profesores con `is_published = true` **y** (a partir del Bloque 2)
  aplicación aprobada. Nunca devuelve un perfil no publicado, ni siquiera con el id.
- `availableDay` / `availableTime` filtran contra `availability_rules`, **no** calculan
  cupos reales — el cálculo de cupos es caro y ya existe en la pantalla del perfil.
  Filtrar por regla es una aproximación honesta y rápida; documéntalo así en el
  OpenAPI.
- `sort=RELEVANCE` en este bloque = orden estable por `display_order` de idioma y luego
  por fecha de publicación. **El ranking real llega en el Bloque 6**; no lo inventes
  aquí.
- Paginación real (`Page<T>` de Spring Data), no listas completas.

**Desglose de tarifa — `PUT /api/v1/me/profile/rate`.** El documento es explícito:
el profesor debe ver cuánto recibe *antes* de publicar. La respuesta incluye:

```json
{
  "hourlyRateCop": 50000,
  "commissionRateBps": 2000,
  "commissionCop": 10000,
  "earningsCop": 40000
}
```

Y el mismo cálculo se expone en `GET /api/v1/me/profile/rate/preview?rate=…` para que
la UI lo muestre **mientras el profesor escribe**, sin guardar. Al estudiante se le
muestra **solo** `hourlyRateCop`: la comisión nunca viaja en un endpoint público.

## 1.3 Frontend

- `/profesores`: buscador con filtros (barra lateral en desktop, hoja inferior en
  móvil), tarjeta de profesor con foto, nombre, idiomas con bandera, país,
  especialidades, niveles, precio/hora y botón "Ver perfil".
- `/profesores/[id]`: perfil ampliado + agenda (estructura de dos columnas ya definida
  en el diseño v2).
- `/idiomas/[code]`: landing por idioma (`/idiomas/ingles`, `/idiomas/frances`,
  `/idiomas/espanol`) — es lo que hace que las tarjetas grandes de la portada tengan
  a dónde llevar, y da páginas indexables para SEO.
- Perfil del profesor (`/perfil`): secciones nuevas de idiomas/niveles/objetivos y el
  **widget de tarifa con desglose en vivo**.

**Honestidad de datos:** las tarjetas del documento muestran `⭐ 4.9` y número de
estudiantes. Hoy no hay reseñas. **No inventes ratings ni contadores.** Si no hay
datos, el espacio no se rellena: se omite el elemento, o se muestra "Profesor nuevo en
Orión". Inventar un 4.9 es fraude al estudiante y basura de datos para el Bloque 6.

## 1.4 Tests exigidos

- Migración: `flyway_schema_history` en verde; `./mvnw verify` sigue pasando.
- Cálculo de comisión: `50000 → 10000 / 40000`; casos de redondeo (`33333`,
  `1` peso); que un cambio de `commission_rate_bps` afecte cálculos nuevos.
- Buscador: cada filtro por separado, dos filtros combinados, filtro sin resultados,
  paginación, y que un profesor **no publicado nunca aparece**.
- Autorización: un estudiante recibe `403` al llamar `PUT /me/profile/rate`.
- E2E: profesor configura dos idiomas con niveles distintos → estudiante lo encuentra
  filtrando por el segundo idioma y ve el precio correcto.

## 1.5 Migración de datos existentes

Los profesores actuales quedan con `compensation_model = 'FIXED_FEE'`,
`hourly_rate_cop = NULL` y una fila en `professor_languages` con `EN`. **Un profesor
sin tarifa no puede publicarse bajo el modelo `COMMISSION`** — valídalo en el servicio
de publicación, no con un `CHECK` (la regla depende del modelo, no de la fila).

**DETENTE. Fin del Bloque 1.**

---

# BLOQUE 2 — Teacher Application y panel de revisión

**Objetivo:** que nadie enseñe en Orión sin que un humano lo haya aprobado, y que esa
decisión quede registrada con quién, cuándo y por qué.

## 2.1 Migración `V11__teacher_applications.sql`

```sql
CREATE TABLE teacher_applications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id),
    status        VARCHAR(25) NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'UNDER_REVIEW',
                                    'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')),
    submitted_at  TIMESTAMPTZ,
    reviewed_by   UUID REFERENCES users(id),
    reviewed_at   TIMESTAMPTZ,
    decision_note TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Una sola aplicación viva por persona; las cerradas quedan como historia
CREATE UNIQUE INDEX uq_application_open ON teacher_applications(user_id)
    WHERE status IN ('DRAFT', 'PENDING_REVIEW', 'UNDER_REVIEW', 'CHANGES_REQUESTED');

CREATE TABLE teacher_application_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES teacher_applications(id) ON DELETE CASCADE,
    event_type     VARCHAR(30) NOT NULL
                   CHECK (event_type IN ('CREATED', 'SUBMITTED', 'REVIEW_STARTED',
                                         'CHANGES_REQUESTED', 'RESUBMITTED',
                                         'APPROVED', 'REJECTED')),
    actor_id       UUID REFERENCES users(id),
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_events ON teacher_application_events(application_id, created_at);

CREATE TABLE teacher_documents (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    application_id UUID REFERENCES teacher_applications(id) ON DELETE SET NULL,
    doc_type       VARCHAR(30) NOT NULL
                   CHECK (doc_type IN ('CV', 'TEACHING_CERTIFICATE', 'UNIVERSITY_DEGREE',
                                       'LANGUAGE_CERTIFICATION', 'OTHER')),
    file_name      VARCHAR(200) NOT NULL,
    storage_key    VARCHAR(500) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     INTEGER      NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_teacher_docs ON teacher_documents(user_id, doc_type);

-- Aceptación versionada de términos (necesaria para el Teacher Agreement)
CREATE TABLE agreement_acceptances (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_code VARCHAR(40) NOT NULL,
    version       VARCHAR(20) NOT NULL,
    accepted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(300)
);

CREATE UNIQUE INDEX uq_acceptance ON agreement_acceptances(user_id, document_code, version);

ALTER TABLE users ADD COLUMN phone_verified_at TIMESTAMPTZ;

-- Bitácora de acciones del admin: quién hizo qué, para siempre
CREATE TABLE admin_audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID NOT NULL REFERENCES users(id),
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   UUID,
    detail      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_created ON admin_audit_log(created_at DESC);
```

## 2.2 La máquina de estados

```
DRAFT ──submit──▶ PENDING_REVIEW ──admin abre──▶ UNDER_REVIEW
                                                   │
                        ┌──────────────────────────┼──────────────────────┐
                        ▼                          ▼                      ▼
                    APPROVED                   REJECTED          CHANGES_REQUESTED
                        │                                                 │
                        ▼                                          resubmit│
              perfil publicable                                            ▼
                                                              PENDING_REVIEW
```

**Reglas duras, implementadas en `TeacherApplicationService`:**

- No se puede pasar a `PENDING_REVIEW` sin: email verificado, foto, bio, al menos un
  idioma con al menos un nivel, al menos un objetivo, CV subido y **Teacher Agreement
  aceptado**. La validación devuelve la **lista completa de lo que falta** en un solo
  `400`, no el primer error — un formulario que corrige de a un campo es una tortura.
- `APPROVED` y `REJECTED` son terminales. Para volver a intentar tras un rechazo se
  crea una **aplicación nueva** (el índice único parcial lo permite; la historia queda).
- Toda transición escribe fila en `teacher_application_events`. Sin excepción.
- El admin **no puede aprobarse a sí mismo**: si `reviewed_by == user_id`, `403`.

**El gate de visibilidad — la regla más importante del bloque.** Mientras la
aplicación no esté `APPROVED`, el profesor **no puede**: aparecer en el buscador o en
cualquier endpoint público, publicar su perfil, recibir reservas, recibir mensajes de
estudiantes ni configurar precio visible. Sí puede: entrar a su portal, editar su
aplicación y ver su estado.

Esto se implementa **en un solo lugar** — un método
`ProfessorAccessService.assertCanTeach(professorId)` invocado por todos los endpoints
afectados — y se prueba endpoint por endpoint. Regarlo por seis controladores es cómo
se filtra un profesor no aprobado al marketplace.

**Convivencia con la invitación del admin (D3):** un profesor creado por invitación
nace con una aplicación en estado `APPROVED`, `reviewed_by` = el admin que invitó, y un
evento `APPROVED` con nota *"Alta por invitación del administrador"*. Así hay **una
sola** regla de visibilidad en todo el sistema, y el panel muestra ambos orígenes.

## 2.3 Documentos privados

Cloudinary ya está integrado para fotos, pero **un CV no es una foto**:

- Subida con `type: authenticated` (no `upload`), carpeta `orion/documents/{userId}/`.
- La base guarda `storage_key`, **nunca una URL**.
- `GET /api/v1/admin/teachers/{id}/documents/{docId}/url` devuelve una **URL firmada de
  5 minutos**, y solo responde a `ADMIN`. Cada llamada deja fila en `admin_audit_log`
  (quién miró el CV de quién).
- Validación en la subida: `application/pdf` o imagen, ≤ 10 MB, máximo 6 documentos por
  profesor. Rechaza por *content type real*, no por la extensión del nombre.

## 2.4 Endpoints

| Método y ruta | Quién | Para qué |
|---|---|---|
| `POST /api/v1/teacher-applications` | Usuario autenticado | Crear/obtener el borrador |
| `GET /api/v1/me/teacher-application` | Aspirante | Estado, feedback y qué falta |
| `PUT /api/v1/me/teacher-application` | Aspirante | Guardar avance |
| `POST /api/v1/me/teacher-application/documents` | Aspirante | Subir documento (multipart) |
| `DELETE /api/v1/me/teacher-application/documents/{id}` | Aspirante | Borrar antes de enviar |
| `POST /api/v1/me/teacher-application/submit` | Aspirante | Enviar a revisión |
| `POST /api/v1/me/agreements/{code}/accept` | Aspirante | Aceptar el Teacher Agreement |
| `GET /api/v1/admin/teacher-applications?status=&page=` | Admin | Bandeja de solicitudes |
| `GET /api/v1/admin/teacher-applications/{id}` | Admin | Ficha completa + historial + **previsualización del perfil público** |
| `POST /api/v1/admin/teacher-applications/{id}/start-review` | Admin | → `UNDER_REVIEW` |
| `POST /api/v1/admin/teacher-applications/{id}/approve` | Admin | → `APPROVED` |
| `POST /api/v1/admin/teacher-applications/{id}/reject` | Admin | → `REJECTED` (**motivo obligatorio**) |
| `POST /api/v1/admin/teacher-applications/{id}/request-changes` | Admin | → `CHANGES_REQUESTED` (**qué cambiar, obligatorio**) |

`reject` y `request-changes` **rechazan con `400` si el motivo viene vacío o con menos
de 10 caracteres**. Un rechazo sin explicación es una queja garantizada y una decisión
que nadie podrá auditar en seis meses.

## 2.5 Frontend

- `/ensena-con-orion` — página pública de captación (contenido del Bloque 7).
- `/aplicacion` — formulario en pasos: datos personales → información de enseñanza →
  información profesional → documentos → acuerdo → revisar y enviar. Guarda borrador en
  cada paso; barra de progreso; lista visible de requisitos pendientes.
- `/aplicacion/estado` — el estado con su color y el feedback del admin cuando lo hay.
- `/admin/aplicaciones` — tabla (Profesor · Idioma · Estado · Fecha · Acción) y ficha
  de revisión con dos pestañas: **datos enviados** y **cómo se verá su perfil público**.
  Esa previsualización la pide el documento explícitamente y es lo que hace la revisión
  útil.
- Portal del profesor no aprobado: **estado, y nada más**. Si intenta llegar a
  disponibilidad o reservas, se le explica por qué todavía no; no se le muestra una
  pantalla rota ni un 403 crudo.

## 2.6 Notificaciones

`APPROVED`, `REJECTED` y `CHANGES_REQUESTED` disparan notificación **dentro de Orión**
(tabla `notifications` del Bloque 3 — si el Bloque 3 aún no está, este bloque la crea)
**y** correo con la voz de marca. Nada de lenguaje de castigo:

- Aprobado: *"¡Tu perfil quedó aprobado! Ya puedes publicarlo y empezar a recibir
  estudiantes."*
- Cambios: *"Tu solicitud necesita algunos ajustes. Revisa los comentarios y vuelve a
  enviarla."*
- Rechazado: *"Por ahora no podemos aprobar tu solicitud. Aquí están los comentarios del
  equipo."*

## 2.7 Tests exigidos

- Cada transición válida y **cada transición inválida** (aprobar un `DRAFT` → `409`).
- Enviar sin CV / sin idioma / sin acuerdo → `400` con la lista completa de faltantes.
- El gate: profesor `PENDING_REVIEW` no aparece en `GET /professors`, no puede publicar,
  no puede recibir reserva (`403` en cada uno, un test por endpoint).
- Documentos: un profesor no puede leer los documentos de otro; un estudiante recibe
  `403`; la URL firmada expira.
- Auditoría: aprobar deja evento y fila en `admin_audit_log`.
- E2E: registro → aplicación completa → admin revisa y aprueba → el profesor aparece en
  el buscador.

**DETENTE. Fin del Bloque 2.**

---

# BLOQUE 3 — Mensajería interna, notificaciones y política de contacto

**Objetivo:** que `Estudiante ↔ Orión ↔ Profesor` reemplace a
`Estudiante ↔ WhatsApp ↔ Profesor`, con historial auditable dentro de la plataforma.

## 3.1 Migración `V12__messaging.sql`

```sql
CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES users(id),
    professor_id    UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ,
    UNIQUE (student_id, professor_id)
);

CREATE INDEX idx_conv_student   ON conversations(student_id, last_message_at DESC);
CREATE INDEX idx_conv_professor ON conversations(professor_id, last_message_at DESC);

CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID REFERENCES users(id),      -- NULL = mensaje del sistema
    body            TEXT NOT NULL,                  -- lo que ven las partes
    body_original   TEXT,                           -- original si hubo enmascarado
    is_system       BOOLEAN NOT NULL DEFAULT false,
    flagged_reason  VARCHAR(40)
                    CHECK (flagged_reason IN ('CONTACT_INFO', 'OFF_PLATFORM', 'OTHER')),
    reviewed_by     UUID REFERENCES users(id),
    reviewed_at     TIMESTAMPTZ,
    read_at         TIMESTAMPTZ,
    notified_at     TIMESTAMPTZ,                    -- idempotencia del correo
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conv    ON messages(conversation_id, created_at);
CREATE INDEX idx_messages_flagged ON messages(created_at DESC) WHERE flagged_reason IS NOT NULL;
CREATE INDEX idx_messages_unread  ON messages(conversation_id) WHERE read_at IS NULL;

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(140) NOT NULL,
    body       VARCHAR(400),
    link_path  VARCHAR(200),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notif_user ON notifications(user_id, created_at DESC);
```

## 3.2 Reglas de la mensajería

- **Quién puede escribirle a quién:** solo pares estudiante–profesor, y solo si el
  profesor está aprobado y publicado. El estudiante inicia desde el perfil
  ("Enviar mensaje"). Un profesor **no puede iniciar** conversación con un estudiante
  que nunca lo contactó ni le reservó — esa asimetría previene que el marketplace se
  convierta en un canal de spam a estudiantes.
- **Una conversación por par** (constraint `UNIQUE`), no una por reserva. El historial
  con un profesor es uno solo.
- **Mensajes del sistema** (`is_system = true`, `sender_id = NULL`) para eventos:
  reserva confirmada, solicitud de reprogramación, clase cancelada. Es lo que el
  documento pide en "en el futuro" y sale casi gratis si la columna existe desde ahora.
- **Nada se borra.** Ni el emisor puede borrar su mensaje: el historial es la prueba en
  una disputa. Se puede ocultar por admin (`flagged_reason` + revisión), no eliminar.
- **Transporte:** *polling* cada 15–20 s en la conversación abierta y cada 60 s para el
  contador global. **No** WebSockets en este ciclo: con el volumen esperado (decenas de
  usuarios) no se justifica la complejidad operativa en Railway. Deja el servicio detrás
  de una interfaz para poder cambiarlo sin tocar la UI.
- **Correo de respaldo:** job cada 5 minutos que envía un correo por cada mensaje con
  `read_at IS NULL`, `notified_at IS NULL` y más de 10 minutos de antigüedad. Marca
  `notified_at` en la misma transacción (idempotencia). El correo **no incluye el texto
  del mensaje**, solo el remitente y un enlace a la conversación: evita que el correo se
  vuelva el canal real y evita filtrar contenido a una bandeja ajena.

## 3.3 Política de contacto — cómo implementarla bien

El documento pide restringir el intercambio de números de teléfono. Es una práctica
estándar de marketplace, y se implementa así:

- `ContactPolicyService.evaluate(text)` devuelve `(cleanText, flagged)`. Detecta:
  secuencias de 7+ dígitos con o sin separadores (`300 123 45 67`, `300-123-4567`),
  correos electrónicos, URLs, `@usuario`, y las palabras `whatsapp`, `wasap`, `telegram`,
  `instagram` acompañadas de dígitos o arroba.
- Comportamiento según `platform_settings.contact_policy_mode`:
  - `WARN`: se envía tal cual, se marca `flagged_reason` para el admin.
  - `MASK` **(por defecto)**: el fragmento se reemplaza por `•••` en `body`, el original
    se conserva en `body_original`, y el emisor ve un aviso explicando la política.
  - `BLOCK`: el mensaje se rechaza con `422` y un mensaje claro.
- El admin ve `/admin/mensajes-marcados` con el original y contexto.

**Tres cosas que Pardo debe saber, dichas sin rodeos:**

1. **Esto se evade fácil.** "tres cero cero, uno dos tres..." pasa cualquier regex.
   El filtro sirve para hacer la evasión *incómoda y trazable*, no imposible. Lo que de
   verdad retiene a los profesores es que la plataforma les traiga estudiantes que no
   conseguirían solos; el filtro es el cerrojo, no la casa.
2. **Hay que declararlo.** Revisar el contenido de mensajes privados exige avisarlo en
   los términos y en la política de tratamiento de datos (Ley 1581 de 2012). Un banner
   al abrir la primera conversación — *"Las conversaciones ocurren dentro de Orión y
   pueden ser revisadas ante un reporte"* — no es solo cumplimiento: baja la fricción
   cuando alguien vea sus dígitos enmascarados.
3. **El enmascarado tiene falsos positivos.** Un profesor diciendo "practicamos con el
   texto de la página 1234567" verá `•••`. Por eso el modo por defecto es `MASK` y no
   `BLOCK`: enmascarar molesta, bloquear expulsa.

## 3.4 Retirar WhatsApp del producto (D4)

Trabajo de limpieza que va en este bloque y es fácil de olvidar:

- Quitar los links `wa.me` de correos transaccionales, tarjetas de "Mis clases" y perfil.
- El teléfono sigue existiendo en `users` (verificación, contacto operativo de Sofía,
  soporte), pero **deja de exponerse** en cualquier endpoint que consuma la contraparte.
  Auditar cada DTO: un `phone` que se filtra al frontend hace inútil todo el Bloque 3.
- Los correos existentes cambian su CTA de "Escríbele por WhatsApp" a
  "Abrir conversación en Orión".

## 3.5 Endpoints

| Método y ruta | Quién | Para qué |
|---|---|---|
| `GET /api/v1/me/conversations` | Est./Prof. | Bandeja con último mensaje y no leídos |
| `POST /api/v1/conversations` | Estudiante | Abrir (o recuperar) conversación con un profesor |
| `GET /api/v1/conversations/{id}/messages?before=&size=` | Participantes | Historial paginado hacia atrás |
| `POST /api/v1/conversations/{id}/messages` | Participantes | Enviar (pasa por la política de contacto) |
| `POST /api/v1/conversations/{id}/read` | Participantes | Marcar leídos |
| `GET /api/v1/me/notifications?unreadOnly=` | Todos | Campana |
| `POST /api/v1/me/notifications/{id}/read` · `/read-all` | Todos | Marcar leídas |
| `GET /api/v1/admin/messages/flagged` | Admin | Revisión de marcados |

**Rate limit obligatorio:** 30 mensajes por usuario y hora, 10 conversaciones nuevas por
día. Reutiliza el patrón de rate limit de auth. Sin esto, la primera cuenta falsa que
llegue desde Ads convierte tu cuota de Resend en humo.

## 3.6 Frontend

- `/mensajes` — bandeja; en desktop dos paneles (lista + conversación), en móvil dos
  pantallas con navegación.
- Campana con contador en el app shell, para los tres roles.
- Botón "Enviar mensaje" en el perfil del profesor.
- Aviso de política al abrir la primera conversación.

## 3.7 Tests exigidos

- Un tercero no participante recibe `403` al leer o escribir en una conversación.
- Un profesor no aprobado no puede recibir mensajes.
- Un profesor no puede iniciar conversación con un estudiante desconocido.
- Política de contacto: teléfono con espacios, con guiones, correo, URL, y **un caso de
  falso positivo documentado** en el test para que quede visible el comportamiento.
- El correo de respaldo no se envía dos veces (idempotencia de `notified_at`).
- Contador de no leídos correcto tras leer.
- E2E: estudiante escribe → profesor recibe notificación → responde → contador en cero.

**DETENTE. Fin del Bloque 3.**

---

# BLOQUE 4 — Pagos, comisión, créditos y liquidación

> **No arrancar sin Q4 respondida.** Este bloque mueve dinero de terceros. La decisión
> de a nombre de quién se recauda, cómo se factura y cómo se le retiene a los profesores
> es contable y jurídica, no técnica, y determina el modelo de datos.

**Objetivo:** el flujo `Estudiante paga a Orión → Orión retiene → clase dictada →
clase confirmada → el profesor cobra → Orión conserva el 20 %`.

## 4.1 Alcance honesto de este bloque

Lo que **sí** se construye: recaudo real con pasarela, retención lógica del dinero,
libro contable por reserva, créditos del estudiante, y **reporte de liquidación** para
pagar a los profesores.

Lo que **no** se construye: transferencia automática a la cuenta del profesor. El
*split payment* real exige el producto de marketplace de la pasarela y aprobación
comercial, y en Colombia retener y dispersar fondos de terceros de forma automática
levanta requisitos regulatorios que no se resuelven con código. **En este ciclo el
sistema calcula, y una persona transfiere**, contra un reporte que cuadra al peso. Es la
decisión correcta para el volumen actual: automatizar pagos antes de tener 100 clases
al mes es construir un banco para no usarlo.

## 4.2 Migración `V13__billing.sql`

```sql
-- La reserva ahora puede existir sin estar pagada
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'RESCHEDULE_REQUESTED',
                      'CANCELLED_BY_STUDENT', 'CANCELLED_BY_PROFESSOR',
                      'CANCELLED_BY_ADMIN', 'EXPIRED', 'COMPLETED',
                      'UNDER_REVIEW', 'NO_SHOW_PROFESSOR', 'NO_SHOW_STUDENT'));

ALTER TABLE bookings
    ADD COLUMN is_trial   BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN expires_at TIMESTAMPTZ;   -- solo para PENDING_PAYMENT

-- El cupo se ocupa también mientras el pago está en curso
DROP INDEX IF EXISTS uq_bookings_professor_slot;
CREATE UNIQUE INDEX uq_bookings_professor_slot
    ON bookings(professor_id, starts_at)
    WHERE status IN ('CONFIRMED', 'PENDING_PAYMENT', 'RESCHEDULE_REQUESTED');

CREATE TABLE payments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id           UUID UNIQUE NOT NULL REFERENCES bookings(id),
    student_id           UUID   NOT NULL REFERENCES users(id),
    professor_id         UUID   NOT NULL REFERENCES users(id),
    amount_cop           BIGINT NOT NULL CHECK (amount_cop >= 0),
    credit_applied_cop   BIGINT NOT NULL DEFAULT 0 CHECK (credit_applied_cop >= 0),
    charged_cop          BIGINT NOT NULL CHECK (charged_cop >= 0),
    commission_rate_bps  INTEGER NOT NULL,
    commission_cop       BIGINT NOT NULL,
    professor_earnings_cop BIGINT NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'PAID', 'RELEASED',
                                           'REFUNDED', 'DISPUTED', 'CANCELLED')),
    provider             VARCHAR(20),
    provider_reference   VARCHAR(140),
    paid_at              TIMESTAMPTZ,
    released_at          TIMESTAMPTZ,
    refunded_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (commission_cop + professor_earnings_cop = amount_cop)
);

CREATE INDEX idx_payments_professor ON payments(professor_id, status);
CREATE INDEX idx_payments_student   ON payments(student_id, created_at DESC);

-- Auditoría cruda de la pasarela: nunca se borra, nunca se edita
CREATE TABLE payment_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id        UUID REFERENCES payments(id),
    provider          VARCHAR(20) NOT NULL,
    provider_event_id VARCHAR(140) NOT NULL,
    event_type        VARCHAR(60) NOT NULL,
    payload           JSONB NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_event_id)      -- idempotencia del webhook
);

CREATE TABLE student_credits (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID   NOT NULL REFERENCES users(id),
    amount_cop   BIGINT NOT NULL CHECK (amount_cop > 0),
    remaining_cop BIGINT NOT NULL CHECK (remaining_cop >= 0),
    reason       VARCHAR(40) NOT NULL
                 CHECK (reason IN ('PROFESSOR_NO_SHOW', 'CANCELLED_BY_PROFESSOR',
                                   'DISPUTE_RESOLVED', 'ADMIN_ADJUSTMENT')),
    booking_id   UUID REFERENCES bookings(id),
    expires_at   TIMESTAMPTZ,
    created_by   UUID REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_credits_student ON student_credits(student_id)
    WHERE remaining_cop > 0;

CREATE TABLE payouts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID   NOT NULL REFERENCES users(id),
    period_start DATE   NOT NULL,
    period_end   DATE   NOT NULL,
    amount_cop   BIGINT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    reference    VARCHAR(140),
    paid_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payout_items (
    payout_id  UUID NOT NULL REFERENCES payouts(id) ON DELETE CASCADE,
    payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),
    PRIMARY KEY (payout_id, payment_id)
);
```

**El `CHECK (commission_cop + professor_earnings_cop = amount_cop)` es deliberado:**
es la base de datos garantizando que la contabilidad cuadra. Ningún bug de redondeo en
Java puede crear plata que no existe.

## 4.3 El flujo de reserva, reescrito

```
Estudiante elige cupo
      │
      ▼
POST /bookings  →  booking PENDING_PAYMENT + payment PENDING
                   expires_at = now + payment_hold_minutes (20)
                   ← el cupo YA está bloqueado por el índice único
      │
      ▼
Estudiante paga en la pasarela (redirección o widget)
      │
      ├── webhook APPROVED  →  payment PAID · booking CONFIRMED
      │                        correo + .ics + link Jitsi (flujo actual intacto)
      │
      ├── webhook DECLINED  →  payment CANCELLED · booking EXPIRED · cupo liberado
      │
      └── sin respuesta     →  job cada 5 min expira las vencidas
```

**Reglas:**

- El precio del estudiante es `professor_profiles.hourly_rate_cop` **al momento de
  reservar**, copiado a `payments.amount_cop`. Si el profesor sube su tarifa mañana, la
  reserva de hoy no cambia.
- Los créditos se aplican **antes** de cobrar: `charged_cop = amount_cop -
  credit_applied_cop`. Si el crédito cubre todo, `charged_cop = 0` y la reserva se
  confirma sin pasar por la pasarela. La comisión se calcula siempre sobre `amount_cop`
  — el crédito es un pasivo de Orión, no un descuento al profesor.
- Consumo de créditos **FIFO por vencimiento**, dentro de una transacción con bloqueo de
  fila (`SELECT … FOR UPDATE`). Sin el bloqueo, dos pestañas gastan el mismo crédito dos
  veces.
- `RELEASED` (el profesor se ganó la plata) lo dispara el job de autocompletado del
  Bloque 5, no el pago. Antes de eso el dinero está retenido.

## 4.4 Integración con la pasarela

Interfaz `PaymentProvider` en el módulo nuevo `billing`, con
`WompiPaymentProvider` como implementación — el mismo patrón que `MeetingLinkProvider`,
por la misma razón: cambiar de pasarela no debe tocar el dominio.

```java
public interface PaymentProvider {
    PaymentIntent createIntent(Payment payment, String returnUrl);
    ProviderEvent parseWebhook(String rawBody, Map<String,String> headers); // valida firma
    RefundResult refund(Payment payment, long amountCop);
}
```

**Obligatorio:**

- **Verificar la firma del webhook** antes de tocar la base. Un webhook sin verificar es
  un endpoint público que confirma reservas gratis.
- Guardar el payload crudo en `payment_events` **antes** de procesarlo. Si la lógica
  falla, el hecho no se pierde y se puede reprocesar.
- Idempotencia por `UNIQUE (provider, provider_event_id)`: la pasarela reenvía eventos,
  es normal, y un doble procesamiento no puede duplicar nada.
- Claves por variable de entorno (`WOMPI_PUBLIC_KEY`, `WOMPI_PRIVATE_KEY`,
  `WOMPI_EVENTS_SECRET`). **Nunca en el repositorio.** Sandbox en `local`.
- La conciliación es una pantalla de admin, no un `SELECT` a mano en producción.

## 4.5 Endpoints

| Método y ruta | Quién | Para qué |
|---|---|---|
| `POST /api/v1/bookings` | Estudiante | Ahora crea `PENDING_PAYMENT` y devuelve datos de pago |
| `GET /api/v1/bookings/{id}/payment` | Estudiante | Estado del pago (para el *polling* del retorno) |
| `POST /api/v1/webhooks/payments/wompi` | Público, **firmado** | Eventos de la pasarela |
| `GET /api/v1/me/credits` | Estudiante | Saldo y detalle |
| `GET /api/v1/me/payments` | Estudiante | Historial |
| `GET /api/v1/me/earnings?from=&to=` | Profesor | Ganancias: retenidas, liberadas, pagadas |
| `GET /api/v1/admin/payments` | Admin | Conciliación con filtros |
| `POST /api/v1/admin/payouts/generate` | Admin | Generar liquidación del período |
| `POST /api/v1/admin/payouts/{id}/mark-paid` | Admin | Marcar transferida (referencia obligatoria) |
| `GET /api/v1/admin/payouts/{id}/export` | Admin | CSV para el banco / la contadora |

## 4.6 Frontend

- Checkout: resumen (profesor, fecha, modalidad, precio, crédito aplicado, total),
  redirección a la pasarela, y pantalla de retorno con estado en vivo y un mensaje
  claro para "pago pendiente" (PSE puede tardar).
- Profesor: pantalla "Mis ganancias" con retenido / liberado / pagado y el desglose de
  la comisión por clase.
- Admin: pagos, liquidaciones y exportación.

## 4.7 Tests exigidos

Este es el bloque donde los tests **no son negociables**:

- Cálculo de comisión con redondeo: `50000`, `33333`, `1`, `0` (clase cubierta por
  crédito). El invariante `comisión + ganancia = total` se verifica en cada caso.
- Webhook: firma inválida → `401`; evento duplicado → procesado una sola vez; evento de
  un pago inexistente → registrado y descartado sin explotar.
- Expiración: reserva `PENDING_PAYMENT` vencida libera el cupo y **otro estudiante puede
  reservarlo**.
- Concurrencia: dos estudiantes contra el mismo cupo → uno `201`, otro `409`.
- Créditos: consumo FIFO; crédito parcial; dos consumos simultáneos no gastan de más.
- Un profesor no ve las ganancias de otro; un estudiante no ve `commission_cop` en
  ninguna respuesta.

**DETENTE. Fin del Bloque 4.**

---

# BLOQUE 5 — Ciclo de vida de la clase: reprogramación, no-show y disputas

**Objetivo:** que toda clase tenga un final definido y auditable, y que el dinero se
mueva en consecuencia sin que nadie tenga que negociar por WhatsApp.

## 5.1 Migración `V14__lesson_lifecycle.sql`

```sql
CREATE TABLE reschedule_requests (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id         UUID NOT NULL REFERENCES bookings(id),
    requested_by       UUID NOT NULL REFERENCES users(id),
    proposed_starts_at TIMESTAMPTZ NOT NULL,
    proposed_ends_at   TIMESTAMPTZ NOT NULL,
    reason             VARCHAR(300),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    resolved_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_reschedule_open ON reschedule_requests(booking_id)
    WHERE status = 'PENDING';

CREATE TABLE disputes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    opened_by       UUID NOT NULL REFERENCES users(id),
    reason_code     VARCHAR(40) NOT NULL
                    CHECK (reason_code IN ('PROFESSOR_NO_SHOW', 'PROFESSOR_LATE',
                                           'TECHNICAL_PROBLEM', 'LESSON_NOT_HELD', 'OTHER')),
    description     VARCHAR(1000),
    status          VARCHAR(25) NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED_FOR_STUDENT',
                                      'RESOLVED_FOR_PROFESSOR', 'DISMISSED')),
    resolution_note VARCHAR(1000),
    resolved_by     UUID REFERENCES users(id),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_dispute_open ON disputes(booking_id)
    WHERE status IN ('OPEN', 'UNDER_REVIEW');

CREATE TABLE professor_absences (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID NOT NULL REFERENCES users(id),
    booking_id   UUID NOT NULL REFERENCES bookings(id),
    dispute_id   UUID REFERENCES disputes(id),
    occurred_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_absences_prof ON professor_absences(professor_id, occurred_at DESC);

CREATE TABLE professor_sanctions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID NOT NULL REFERENCES users(id),
    type         VARCHAR(30) NOT NULL
                 CHECK (type IN ('WARNING', 'VISIBILITY_REDUCED',
                                 'BOOKINGS_SUSPENDED', 'PROFILE_HIDDEN',
                                 'ACCOUNT_SUSPENDED')),
    reason       VARCHAR(300) NOT NULL,
    starts_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at      TIMESTAMPTZ,
    created_by   UUID REFERENCES users(id),   -- NULL = automática del sistema
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sanctions_active ON professor_sanctions(professor_id)
    WHERE ends_at IS NULL OR ends_at > now();

ALTER TABLE bookings ADD COLUMN completed_at TIMESTAMPTZ;
```

## 5.2 Reglas de cancelación (respuesta a Q3, asimétrica y explícita)

| Actor | Ventana | Puede |
|---|---|---|
| Estudiante | > 24 h (`student_cancel_hours`) | Cancelar; reembolso a **crédito** |
| Estudiante | ≤ 24 h | No cancela. Copy vigente: *"Faltan menos de 24 h — la clase se considera impartida"* |
| Profesor | > 12 h (`professor_cancel_hours`) | Cancelar; el estudiante recibe **crédito del 100 %** |
| Profesor | ≤ 12 h | **Solo solicitar reprogramación**, escogiendo un cupo libre suyo |
| Admin | Siempre | Cancelar con motivo; decide crédito o reembolso |

Los umbrales se leen de `platform_settings` en cada evaluación. Ninguno hardcodeado.

**Reembolso vs. crédito:** por defecto **crédito** (retiene valor en la plataforma y
evita costos y fricción de reversar en la pasarela). El reembolso a medio de pago
original existe solo como acción de admin, y en la práctica se dispara ante un reclamo
formal. Dejarlo escrito en los términos: el estudiante debe saberlo *antes* de pagar,
no después de cancelar.

## 5.3 Reprogramación

- El solicitante escoge un cupo real de la disponibilidad del profesor (mismo endpoint
  de cupos que ya existe).
- La contraparte recibe notificación + correo y puede **aceptar** (la reserva se mueve,
  el pago no se toca) o **proponer otro horario** (nueva solicitud, la anterior queda
  `DECLINED`).
- Si nadie responde y llega la hora original, la solicitud queda `EXPIRED` y la reserva
  sigue su curso normal.
- Mover una reserva ocupa el nuevo cupo con el mismo índice único: si el cupo voló entre
  la propuesta y la aceptación, se responde `409` con mensaje claro, no se pisa nada.

## 5.4 No-show y disputas

```
La clase termina
      │
      ├─ el estudiante reporta ("El profesor no se presentó"), permitido desde
      │  starts_at + 15 min (no_show_report_minutes) hasta ends_at + 24 h
      │        ▼
      │  booking UNDER_REVIEW · payment DISPUTED · dispute OPEN
      │        ▼
      │  el admin resuelve
      │     ├─ a favor del estudiante → crédito 100 % · payment REFUNDED ·
      │     │   fila en professor_absences · evaluación de sanción (Bloque 6)
      │     └─ a favor del profesor  → booking COMPLETED · payment RELEASED
      │
      └─ nadie reporta → job de autocompletado a las 24 h (auto_complete_hours):
         booking COMPLETED · payment RELEASED · se habilita la reseña
```

**El job de autocompletado es el corazón económico del sistema.** Cada hora, busca
reservas `CONFIRMED` con `ends_at < now - 24 h` y sin disputa abierta, y las cierra. Es
idempotente vía `completed_at`. Si este job se cae, nadie cobra: monitoréalo y déjalo
visible en el panel de admin con la marca de su última ejecución.

## 5.5 Endpoints

| Método y ruta | Quién |
|---|---|
| `POST /api/v1/bookings/{id}/reschedule-requests` | Profesor / Estudiante |
| `POST /api/v1/reschedule-requests/{id}/accept` · `/decline` | La contraparte |
| `POST /api/v1/bookings/{id}/report-problem` | Estudiante (abre disputa) |
| `GET /api/v1/admin/disputes?status=` | Admin |
| `POST /api/v1/admin/disputes/{id}/resolve` | Admin (resolución + nota obligatoria) |
| `GET /api/v1/admin/jobs/status` | Admin (última corrida de cada job) |

## 5.6 Tests exigidos

Con el `Clock` congelado, caso por caso:

- Profesor cancela a 13 h → permitido; a 11 h → `422` con la vía de reprogramación.
- Estudiante cancela a 25 h → crédito; a 23 h → `422`.
- Reporte de no-show a los 10 min → `422`; a los 16 min → abre disputa; a las 25 h →
  `422`.
- Autocompletado: no toca reservas con disputa abierta; corre dos veces sin duplicar
  liberaciones.
- Reprogramación: aceptar mueve la reserva; el cupo tomado en el intermedio da `409`.
- Resolver a favor del estudiante crea crédito exacto y una sola ausencia.

**DETENTE. Fin del Bloque 5.**

---

# BLOQUE 6 — Reseñas, métricas de desempeño, ranking y sanciones

**Objetivo:** que el buen comportamiento se premie con visibilidad en lugar de
administrarse a punta de castigos — que es, textualmente, lo que pide el documento.

## 6.1 Migración `V15__reputation.sql`

```sql
CREATE TABLE reviews (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   UUID UNIQUE NOT NULL REFERENCES bookings(id),
    student_id   UUID NOT NULL REFERENCES users(id),
    professor_id UUID NOT NULL REFERENCES users(id),
    rating       SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      VARCHAR(1000),
    is_visible   BOOLEAN NOT NULL DEFAULT true,
    hidden_by    UUID REFERENCES users(id),
    hidden_reason VARCHAR(300),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reviews_prof ON reviews(professor_id, created_at DESC)
    WHERE is_visible;

CREATE TABLE professor_metrics (
    professor_id        UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    rating_avg          NUMERIC(3,2),
    rating_count        INTEGER NOT NULL DEFAULT 0,
    lessons_completed   INTEGER NOT NULL DEFAULT 0,
    attendance_rate     NUMERIC(5,2),
    cancellation_rate   NUMERIC(5,2),
    reschedule_rate     NUMERIC(5,2),
    response_rate       NUMERIC(5,2),
    avg_response_minutes INTEGER,
    active_students     INTEGER NOT NULL DEFAULT 0,
    profile_completeness SMALLINT,
    ranking_score       NUMERIC(6,2),
    window_days         SMALLINT NOT NULL DEFAULT 90,
    computed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## 6.2 Reseñas

- Solo el estudiante de una reserva `COMPLETED` puede reseñarla, **una sola vez**
  (constraint `UNIQUE` en `booking_id`), dentro de los 30 días siguientes.
- Se pide al terminar la clase, con notificación y correo. Nada de reseñas sin clase:
  eso es lo que garantiza que el 4.9 signifique algo.
- El profesor **no puede** borrar ni editar reseñas. Puede reportarlas; el admin decide
  ocultarlas (`is_visible = false` + motivo). Nunca se eliminan de la tabla.
- El perfil público muestra promedio, cantidad y las últimas reseñas visibles. **Con
  menos de 3 reseñas no se muestra promedio**, se muestra "Profesor nuevo en Orión":
  un 5.0 con una sola reseña engaña más de lo que informa.

## 6.3 Métricas y ranking

Job nocturno, ventana móvil de **90 días** (el estándar que menciona el documento), que
recalcula `professor_metrics` para todo profesor aprobado:

```
attendance_rate    = completadas / (completadas + no-show del profesor confirmados)
cancellation_rate  = canceladas por el profesor / total reservadas
reschedule_rate    = reprogramaciones pedidas por el profesor / total reservadas
response_rate      = conversaciones respondidas en < 24 h / conversaciones recibidas
active_students    = estudiantes distintos con clase completada en la ventana
profile_completeness = % de campos de perfil diligenciados (foto, bio, idiomas,
                       niveles, objetivos, experiencia, educación, tarifa)
```

Y el puntaje, con pesos en `platform_settings` (`ranking_weight_*`) para poder
ajustarlo sin desplegar:

```
ranking_score = 0.30·rating_norm      + 0.25·attendance_rate
              + 0.15·response_rate    + 0.15·lessons_norm
              + 0.10·completeness     + 0.05·retention_norm
              − penalización por sanciones activas
```

**Tres advertencias de diseño que valen más que la fórmula:**

1. **Arranque en frío.** Con cero clases, todo profesor puntúa 0 y el orden queda
   arbitrario. Solución: los profesores con menos de 5 clases completadas reciben un
   puntaje neutro (la mediana del grupo) y una rotación aleatoria estable entre ellos.
   Sin esto, el profesor nuevo nunca recibe su primera reserva y la oferta se estanca.
2. **El ranking se recalcula de noche, no en cada búsqueda.** El buscador ordena por
   una columna indexada.
3. **La fórmula es una hipótesis.** Revísala cuando haya 200 clases reales, no antes.

## 6.4 Sanciones progresivas

Automáticas, evaluadas cuando se registra una ausencia confirmada (ventana de 90 días):

| Ausencia | Consecuencia |
|---|---|
| 1.ª | `WARNING` — notificación, sin efecto en visibilidad |
| 2.ª | `VISIBILITY_REDUCED` 14 días — penalización en `ranking_score` |
| 3.ª | `BOOKINGS_SUSPENDED` 7 días — no recibe reservas nuevas; **las ya confirmadas se respetan** |
| 4.ª o más | `PROFILE_HIDDEN` + revisión manual del admin |

`ACCOUNT_SUSPENDED` es **siempre manual**. Ningún sistema automático debe poder cerrarle
la cuenta a alguien que depende de ella para vivir; eso lo decide una persona con un
motivo escrito.

Toda sanción notifica al profesor con su motivo y su fecha de fin, y aparece en su
pantalla de desempeño. Una sanción invisible es solo una caída inexplicable de ingresos.

## 6.5 Endpoints

| Método y ruta | Quién |
|---|---|
| `POST /api/v1/bookings/{id}/review` | Estudiante |
| `GET /api/v1/professors/{id}/reviews?page=` | Público |
| `GET /api/v1/me/performance` | Profesor (métricas + sanciones activas) |
| `POST /api/v1/reviews/{id}/report` | Profesor |
| `GET /api/v1/admin/reviews/reported` · `POST /{id}/hide` | Admin |
| `POST /api/v1/admin/professors/{id}/sanctions` · `DELETE /{id}` | Admin (manual) |

## 6.6 Tests exigidos

- Reseñar una reserva no completada, ajena, o dos veces → error en cada caso.
- Métricas con datos sembrados: los seis indicadores dan el valor esperado.
- Arranque en frío: un profesor sin clases no queda último de forma permanente.
- Sanciones: la 3.ª ausencia bloquea reservas nuevas y **no** cancela las existentes.
- El profesor ve sus propias métricas; otro profesor recibe `403`.

**DETENTE. Fin del Bloque 6.**

---

# BLOQUE 7 — Portada marketplace, navegación y "Enseña en Orión"

**Objetivo:** que el primer contacto diga *plataforma de profesores de idiomas*, no
*academia de inglés*. Es lo que hace que el gasto en Google Ads no se desperdicie.

## 7.1 Navegación pública

```
Logo | Encuentra un profesor | Idiomas | Cómo funciona | Enseña en Orión | Nosotros
                                                    Iniciar sesión | Crear cuenta
```

Móvil: menú hamburguesa con las mismas entradas y los dos botones abajo, separados.

## 7.2 Secciones de la portada (`/`)

1. **Hero.** Titular *"Encuentra al profesor indicado. Aprende a tu manera."*, subtítulo
   sobre profesores personalizados de inglés, francés y español, y **buscador de tres
   campos** (idioma · objetivo · horario) con botón "Buscar profesor". El buscador
   redirige a `/profesores` con los filtros aplicados — es un atajo al Bloque 1, no un
   sistema nuevo.
2. **Idiomas destacados.** Tres tarjetas grandes → `/idiomas/{code}`.
3. **Conoce a los profesores.** 4 tarjetas **reales** desde `GET /professors` (los mejor
   posicionados) + "Ver todos". Si hay menos de 4 publicados, la sección se oculta
   entera; no se rellena con tarjetas falsas.
4. **Cómo funciona Orión.** Encuentra → Reserva → Aprende → Avanza.
5. **El Método ORION.** Observe · Relate · Interact · Optimize · Navigate — conservado
   como diferenciador, ya no como explicación del producto.
6. **Aprende para lo que te importa.** Trabajo · Viajes · Estudio · Conversación · Vida
   en el exterior · Crecimiento personal. Cada tarjeta enlaza a `/profesores` filtrado
   por su objetivo, así la sección *hace* algo además de comunicar.
7. **Enseña en Orión.** → `/ensena-con-orion`.
8. **CTA final.** *"Tu camino con los idiomas empieza con el profesor indicado."*

## 7.3 `/ensena-con-orion`

Propuesta de valor para el otro lado del marketplace: cómo funciona, qué gana el
profesor (con la comisión dicha **de frente** — ocultarla en la letra chica es la forma
más rápida de perder profesores tras la primera liquidación), requisitos, y el paso a
paso de la solicitud. CTA → `/aplicacion` (Bloque 2).

## 7.4 SEO y rendimiento

- `/`, `/profesores`, `/idiomas/*` y los perfiles públicos se renderizan en el servidor,
  con metadatos, Open Graph y `JSON-LD` (`Course` / `Person`) por página.
- `sitemap.xml` dinámico que incluye los perfiles publicados; `robots.txt`.
- Lighthouse ≥ 90 en móvil para la portada. Es la página a la que llega el dinero de Ads.
- El copy en español (Q2); las frases en inglés quedan como eslogan de marca.

## 7.5 Tests exigidos

- E2E: buscador de la portada → `/profesores` con los filtros correctos aplicados.
- La sección de profesores se oculta con menos de 4 publicados.
- Cada tarjeta de objetivo lleva al filtro correspondiente.
- `sitemap.xml` no incluye perfiles no publicados.

**DETENTE. Fin del Bloque 7.**

---

## A. Definition of done del programa completo

- [ ] Un profesor nuevo puede registrarse, aplicar, ser aprobado y publicar su perfil
      sin que Sofía toque WhatsApp
- [ ] Un estudiante puede filtrar por idioma, nivel, objetivo y precio, y encontrar al
      profesor correcto
- [ ] Estudiante y profesor conversan **dentro** de Orión; ningún `wa.me` sobrevive en
      correos ni en la interfaz
- [ ] Una clase se paga, se dicta, se confirma y se liquida, y las cuentas cuadran al
      peso contra la pasarela
- [ ] Un no-show se reporta, se resuelve, genera crédito y queda registrado
- [ ] Las reseñas existen y alimentan un ranking que se puede explicar en voz alta
- [ ] La portada comunica "marketplace de idiomas" en menos de cinco segundos
- [ ] `docs/ESTADO.md` actualizado tras cada bloque

---

## B. Riesgos que no se resuelven con código

Los digo porque el trabajo de un arquitecto también es señalar dónde el software no es
la solución:

**1. Contratos con los profesores actuales (Q1).** Pasar de tarifa fija a comisión del
20 % cambia lo que gana cada profesor que ya trabaja con Sofía. Eso se conversa antes de
que el sistema lo aplique, no cuando llegue la primera liquidación con menos plata.

**2. Naturaleza de la relación laboral.** Un profesor que fija su precio, su horario y
sus alumnos es un contratista; uno al que se le imponen tarifas, sanciones y
exclusividad empieza a parecerse a un empleado. La combinación de comisión obligatoria,
prohibición de contacto externo y sanciones automáticas es exactamente la que se
examina en discusiones de tercerización. Vale una consulta con abogado laboral antes de
lanzar, no después de la primera reclamación.

**3. Recaudar plata de terceros (Q4).** Retener el pago del estudiante y transferirlo
después al profesor implica facturación electrónica, retenciones y probablemente un
contrato de mandato. Consulta con contador **antes** del Bloque 4: el modelo de datos
depende de la respuesta.

**4. Tratamiento de datos.** Documentos de identidad, CV y mensajes privados con
revisión de contenido exigen política de tratamiento de datos y aviso de privacidad
actualizados (Ley 1581 de 2012), y una política de retención: cuánto tiempo se guarda
el CV de alguien que fue rechazado.

**5. La cláusula de no-elusión no se hace cumplir sola.** El filtro de contactos
incomoda, no impide. Lo que retiene profesores es el flujo de estudiantes; la cláusula
es el respaldo, no la estrategia.

**6. Oferta antes que demanda.** Un marketplace vacío no convierte. Antes de encender
Ads hacia la portada nueva conviene tener al menos 8–10 profesores aprobados y
publicados, repartidos entre los tres idiomas. Si no, el dinero paga visitas a una
página que no puede resolver lo que promete.

---

## C. Qué NO está en este brief

Para que Claude Code no lo agregue por iniciativa propia: paquetes/planes de clases,
clases grupales, tareas y recursos, integración con IA, progreso del estudiante por
idioma, app móvil nativa, integración real con Google Meet o Calendar API, notificaciones
push, multi-moneda, internacionalización completa de la interfaz, y transferencia
automática a profesores.

Si algo de eso parece necesario para completar un bloque, **detente y pregunta**.
