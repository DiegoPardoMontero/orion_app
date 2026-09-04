# Bloque 8 — Perfil del estudiante, puntos y logros

> **Ubicación:** `docs/briefs/orion-bloque-8-gamificacion.md`
> **Diseño de referencia:** `docs/design-v2/gamificacion/` — entregable «v2 · lenguaje
> constelación», dirección **1c Híbrida "Sello"**. Es contrato visual.
> **Estado del sistema al escribir esto:** Flyway V1–V19, módulos `identity`,
> `scheduling`, `catalog`, `billing`, `messaging`, `reputation`, `lifecycle`,
> `notifications`, `admin`, `shared`.

---

## 0. Qué es esto y qué problema resuelve

Orión es un marketplace de clases en vivo: el estudiante toma clase el martes y no
vuelve a saber de la plataforma hasta el martes siguiente. Entre una clase y otra no
hay ningún motivo para abrir la app. Este bloque construye ese motivo: una **ficha de
estudiante** con objetivos declarados, un **libro de puntos**, **20 logros** con forma
de estrella que se encienden, y una **personalización de avatar** que se desbloquea con
el uso.

Tres restricciones que gobiernan todo el bloque:

1. **Los puntos jamás valen dinero.** No hay descuentos, ni canje, ni saldo. Son
   puramente cosméticos. Esto no es una limitación de producto: es la defensa más barata
   contra el fraude, porque en un marketplace un par estudiante–profesor coludido puede
   fabricar clases. Con puntos cosméticos, el incentivo a hacerlo es cero.
2. **El ritmo es semanal.** Los adultos de Orión toman una o dos clases por semana. Nada
   en este bloque cuenta días ni castiga ausencias.
3. **Nada retroactivo se inventa.** Los logros se evalúan sobre datos reales existentes.
   Donde el dato no exista (ver Paso 0), se arregla el dato antes de construir encima.

**Módulo nuevo: `engagement`.** Escucha eventos de `scheduling`, `reputation` e
`identity`; **nadie depende de él**. Esa dirección de dependencia es deliberada: la
gamificación puede desaparecer entera sin romper el marketplace.

---

## Paso 0 — El idioma en la reserva (bloqueante, va primero)

`bookings` no guarda el idioma de la clase. Hoy se deduce del profesor, y un profesor
que enseña dos idiomas hace esa deducción imposible. El logro **«Dos idiomas»** depende
de este dato, y cualquier métrica por idioma también.

Esto no se puede posponer: **el dato no es recuperable hacia atrás**. Cada clase que
pasa sin registrarlo es una clase que nunca sabremos en qué idioma fue.

### `V20__booking_language.sql`

```sql
ALTER TABLE bookings ADD COLUMN language_code VARCHAR(5) REFERENCES languages(code);

-- Backfill solo donde la deducción es inequívoca:
-- el profesor enseña exactamente un idioma
UPDATE bookings b
SET language_code = pl.language_code
FROM professor_languages pl
WHERE pl.professor_id = b.professor_id
  AND (SELECT count(*) FROM professor_languages x
       WHERE x.professor_id = b.professor_id) = 1;

CREATE INDEX idx_bookings_language ON bookings(language_code)
    WHERE language_code IS NOT NULL;
```

**No pongas `NOT NULL`.** Las reservas de profesores multi-idioma quedan en `NULL`
para siempre y eso es correcto: representa "no lo sabemos", que es la verdad. Inventar
un idioma para satisfacer una constraint es peor que el hueco.

Al terminar la migración, **imprime en el log cuántas filas quedaron en `NULL`** y
déjalas listadas para revisión de Pardo. Si son pocas, él las completa a mano desde el
panel.

### Cambios de aplicación

- **Reservar exige idioma** cuando el profesor enseña más de uno: el selector aparece en
  la pantalla de reserva junto a la modalidad, con las insignias de idioma del diseño
  (§2h). Si el profesor enseña uno solo, se asigna sin preguntar.
- Validación en el servicio: el idioma enviado debe estar en `professor_languages` de
  ese profesor. Si no, `422`.
- El idioma viaja en el correo de confirmación y en el `.ics`.

**Tests:** reserva con profesor de un idioma (se asigna sola) · con profesor de dos
(sin idioma → `422`; con idioma ajeno → `422`; con idioma válido → `201`) · backfill
sobre datos sembrados con ambos casos.

**DETENTE.**

---

## Paso 1 — La ficha del estudiante

Hoy un estudiante es una fila en `users` y nada más. Todo lo que sabemos de él se
deduce de sus reservas.

### `V21__student_profiles.sql`

```sql
CREATE TABLE student_profiles (
    user_id            UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    self_declared_level VARCHAR(20)
                       CHECK (self_declared_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    primary_language   VARCHAR(5) REFERENCES languages(code),
    motivation         VARCHAR(280),
    is_public          BOOLEAN NOT NULL DEFAULT false,
    birth_date         DATE,
    -- selección cosmética actual
    frame_code         VARCHAR(40) NOT NULL DEFAULT 'trazo',
    palette_code       VARCHAR(40) NOT NULL DEFAULT 'trazo',
    sky_code           VARCHAR(40) NOT NULL DEFAULT 'crema',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Objetivos del estudiante: reutiliza el catálogo que ya existe
CREATE TABLE student_goals (
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_code  VARCHAR(30) NOT NULL REFERENCES teaching_goals(code),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, goal_code)
);

-- Accesorios equipados: uno por zona de anclaje
CREATE TABLE student_accessories (
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    zone           VARCHAR(10) NOT NULL CHECK (zone IN ('z1', 'z2', 'z3')),
    accessory_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, zone)
);

-- Fila para cada estudiante existente
INSERT INTO student_profiles (user_id)
SELECT id FROM users WHERE role = 'STUDENT'
ON CONFLICT DO NOTHING;
```

**Decisiones que hay que respetar:**

- **`self_declared_level` usa el mismo vocabulario que `professor_language_levels`.**
  Dos escalas de nivel en el mismo producto es una fuente de confusión permanente.
- **`teaching_goals` se reutiliza**, no se duplica. Ya existe con siete objetivos, y
  que el estudiante y el profesor hablen el mismo vocabulario abre la puerta a
  recomendar profesores por objetivo sin ningún trabajo extra.
- **`is_public` nace en `false`.** El consentimiento es explícito y reversible.
- La fila se crea automáticamente para los estudiantes existentes y en el registro de
  los nuevos, con valores por defecto: nunca hay un estudiante sin ficha, y el código
  no tiene que manejar el caso `null`.

### La regla de visibilidad del perfil público

Tres capas, y las tres se aplican en el servidor:

1. **Su dueño** siempre lo ve completo.
2. **Un profesor** lo ve solo si tiene con ese estudiante **una reserva (en cualquier
   estado) o una conversación abierta**. "Público para los profesores" no puede
   significar *todos* los profesores: eso convierte la plataforma en un directorio
   navegable de personas.
3. **Otro estudiante** lo ve solo si `is_public = true`.

En todos los casos la vista pública **excluye**: correo, teléfono, saldo, historial de
pagos, y el detalle de con qué profesores ha practicado. Muestra: foto, nombre, avatar
con su sello y órbita, nivel declarado, objetivos, idioma principal, motivación,
estrellas encendidas y racha actual.

### Menores de edad

El público de Orión es 16+. Un perfil público con foto, nombre y metas de un menor exige
tratamiento reforzado bajo la Ley 1581 de 2012, y la solución más simple y más
defendible es no ofrecerlo:

- `birth_date` se pide **solo cuando el estudiante intenta activar el switch de perfil
  público**, no en el registro. Cero fricción para quien nunca lo active.
- Si es menor de 18, el switch queda deshabilitado con una explicación clara y amable.
  No es un error del usuario y el copy no debe sonar a rechazo.
- El servidor valida la edad en cada intento de activación. El switch deshabilitado en
  el frontend es cortesía, no seguridad.

### Endpoints

| Método y ruta | Quién |
|---|---|
| `GET /api/v1/me/student-profile` | Estudiante (vista completa) |
| `PUT /api/v1/me/student-profile` | Estudiante (nivel, idioma, motivación, objetivos) |
| `PUT /api/v1/me/student-profile/visibility` | Estudiante (switch + fecha de nacimiento) |
| `GET /api/v1/students/{id}/profile` | Profesor o estudiante — **aplica las tres capas** |

**Tests:** un profesor sin relación recibe `404` (no `403`: no confirmes la existencia
del perfil) · un profesor con reserva lo ve · un estudiante ajeno lo ve solo con
`is_public=true` · un menor no puede activar el switch ni por API directa · la vista
pública nunca incluye correo, teléfono ni saldo (assert campo por campo).

**DETENTE.**

---

## Paso 2 — El libro de puntos y el catálogo

### `V22__engagement.sql`

```sql
-- Libro de eventos: la única fuente de puntos, append-only
CREATE TABLE point_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type VARCHAR(40) NOT NULL,
    source_id   UUID,
    points      INTEGER NOT NULL CHECK (points > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotencia: un hecho concede puntos una sola vez, para siempre
CREATE UNIQUE INDEX uq_point_event_source
    ON point_events(source_type, source_id) WHERE source_id IS NOT NULL;
CREATE INDEX idx_point_events_user ON point_events(user_id, occurred_at DESC);

-- Catálogo de logros: datos, no código
CREATE TABLE achievements (
    code           VARCHAR(60) PRIMARY KEY,
    family         VARCHAR(20) NOT NULL
                   CHECK (family IN ('PRIMEROS', 'CONSTANCIA', 'VOLUMEN',
                                     'AMPLITUD', 'COMPROMISO')),
    name           VARCHAR(80)  NOT NULL,
    description    VARCHAR(200) NOT NULL,
    criteria_type  VARCHAR(40)  NOT NULL,
    criteria_params JSONB       NOT NULL DEFAULT '{}',
    target         INTEGER      NOT NULL DEFAULT 1,
    glow           SMALLINT     NOT NULL CHECK (glow BETWEEN 1 AND 3),
    points         INTEGER      NOT NULL,
    display_order  SMALLINT     NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT true
);

CREATE TABLE user_achievements (
    user_id          UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_code VARCHAR(60) NOT NULL REFERENCES achievements(code),
    progress         INTEGER     NOT NULL DEFAULT 0,
    unlocked_at      TIMESTAMPTZ,
    PRIMARY KEY (user_id, achievement_code)
);

CREATE INDEX idx_user_achievements_unlocked
    ON user_achievements(user_id) WHERE unlocked_at IS NOT NULL;

-- Cosméticos: marcos (órbitas), paletas, cielos (fondos), accesorios
CREATE TABLE cosmetics (
    code             VARCHAR(40) PRIMARY KEY,
    kind             VARCHAR(20) NOT NULL
                     CHECK (kind IN ('FRAME', 'PALETTE', 'SKY', 'ACCESSORY')),
    name             VARCHAR(60) NOT NULL,
    zone             VARCHAR(10) CHECK (zone IN ('z1', 'z2', 'z3')),
    unlock_achievement VARCHAR(60) REFERENCES achievements(code),
    is_default       BOOLEAN NOT NULL DEFAULT false,
    display_order    SMALLINT NOT NULL DEFAULT 0,
    CHECK ((kind = 'ACCESSORY') = (zone IS NOT NULL)),
    CHECK (is_default OR unlock_achievement IS NOT NULL)
);

-- Racha protegida: una semana al mes que no corta la racha
CREATE TABLE streak_protections (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start  DATE NOT NULL,
    granted_for DATE NOT NULL,        -- mes al que pertenece la protección
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, week_start)
);

-- Una protección por mes: la constraint lo garantiza, no un if
CREATE UNIQUE INDEX uq_protection_month ON streak_protections(user_id, granted_for);
```

**El `CHECK ((kind = 'ACCESSORY') = (zone IS NOT NULL))`** obliga a que solo los
accesorios tengan zona de anclaje, y a que todos la tengan. El segundo `CHECK` garantiza
que todo cosmético o es inicial o tiene una forma de conseguirse: nada queda inalcanzable
por olvido.

### El catálogo, transcrito del diseño

Los 20 logros salen literalmente del inventario del entregable (`raw` en la sección 2c).
Semilla en la misma migración. `glow` es el brillo del diseño (1 estrella · 2 con halo ·
3 con halo y rayos):

| code | familia | nombre | criteria_type | target | glow | pts |
|---|---|---|---|---|---|---|
| `primeros-primera-reserva` | PRIMEROS | Primera reserva | `EVENT_ONCE` (`booking_created`) | 1 | 1 | 10 |
| `primeros-primera-clase` | PRIMEROS | Primera clase | `LESSON_COUNT` | 1 | 1 | 25 |
| `primeros-perfil-listo` | PRIMEROS | Perfil listo | `PROFILE_COMPLETE` | 3 | 1 | 15 |
| `primeros-primer-mensaje` | PRIMEROS | Primer mensaje | `EVENT_ONCE` (`message_sent`) | 1 | 1 | 10 |
| `constancia-2-semanas` | CONSTANCIA | Dos semanas seguidas | `STREAK_WEEKS` | 2 | 1 | 20 |
| `constancia-4-semanas` | CONSTANCIA | Un mes seguido | `STREAK_WEEKS` | 4 | 1 | 40 |
| `constancia-8-semanas` | CONSTANCIA | Dos meses seguidos | `STREAK_WEEKS` | 8 | 2 | 80 |
| `constancia-12-semanas` | CONSTANCIA | Un trimestre seguido | `STREAK_WEEKS` | 12 | 2 | 120 |
| `constancia-24-semanas` | CONSTANCIA | Medio año seguido | `STREAK_WEEKS` | 24 | 3 | 250 |
| `volumen-5-clases` | VOLUMEN | Cinco clases | `LESSON_COUNT` | 5 | 1 | 25 |
| `volumen-10-clases` | VOLUMEN | Diez clases | `LESSON_COUNT` | 10 | 1 | 50 |
| `volumen-25-clases` | VOLUMEN | Veinticinco clases | `LESSON_COUNT` | 25 | 2 | 100 |
| `volumen-50-clases` | VOLUMEN | Cincuenta clases | `LESSON_COUNT` | 50 | 2 | 200 |
| `volumen-100-clases` | VOLUMEN | Cien clases | `LESSON_COUNT` | 100 | 3 | 400 |
| `amplitud-tres-voces` | AMPLITUD | Tres voces | `DISTINCT_PROFESSORS` | 3 | 1 | 40 |
| `amplitud-dos-idiomas` | AMPLITUD | Dos idiomas | `DISTINCT_LANGUAGES` | 2 | 1 | 60 |
| `amplitud-presencial` | AMPLITUD | Cara a cara | `MODALITY_TAKEN` (`IN_PERSON`) | 1 | 1 | 30 |
| `compromiso-primera-resena` | COMPROMISO | Tu primera reseña | `EVENT_ONCE` (`review_written`) | 1 | 1 | 20 |
| `compromiso-objetivo` | COMPROMISO | Objetivo declarado | `EVENT_ONCE` (`goal_declared`) | 1 | 1 | 15 |
| `compromiso-mes-sin-cancelar` | COMPROMISO | Un mes sin cancelaciones | `NO_CANCELLATIONS_DAYS` | 30 | 1 | 50 |

Los textos exactos de `description` se copian del inventario del diseño — están
redactados con la voz de marca y ya fueron aprobados; no los reescribas.

**Cosméticos** (condiciones tomadas de §2e del entregable):

- **Marcos:** `trazo` (inicial) · `orbita` ← primera-clase · `orbita-doble` ← 2-semanas ·
  `constelacion-iii` ← tres-voces · `constelacion-v` ← 4-semanas · `halo` ← 10-clases ·
  `orbita-amanecer` ← 12-semanas · `cielo` ← 24-semanas.
- **Paletas:** `trazo` (inicial) · `durazno` ← primera-clase · `lavanda` ← dos-idiomas ·
  `ciruela` ← 25-clases · `noche` ← 12-semanas · `amanecer` ← 24-semanas.
- **Cielos:** `crema` (inicial) · `bruma` ← perfil-listo · `alba` ← 5-clases ·
  `constelacion` ← 8-semanas · `noche` ← 50-clases · `amanecer` ← 24-semanas.
- **Accesorios:** `z1 base-orbita` ← 10-clases · `z2 centro-monograma` ← perfil-listo ·
  `z3 corona-constelacion` ← 24-semanas.

El diseño da a `cielo` (marco) la condición "medio año seguido **o** cien clases". El
modelo solo admite un desbloqueo por cosmético, así que **usa `24-semanas`** y anota la
alternativa como deuda: si más adelante hace falta, `unlock_achievement` se convierte en
tabla puente. No compliques el modelo hoy por un caso.

**Sellos:** el nivel del sello es **derivado**, no almacenado — nivel 1 al registrarse,
2 con `constancia-8-semanas`, 3 con `constancia-24-semanas`. Guardarlo sería una tercera
copia de la misma verdad.

**DETENTE.**

---

## Paso 3 — El motor de logros

### Cómo entran los puntos

`engagement` **no llama a nadie**: escucha. Los módulos existentes publican eventos de
dominio, y el patrón es el que ya usa `notifications`:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void on(LessonCompletedEvent e) { … }
```

Eventos que consume, con quién los publica:

| Evento | Publica | Fuente de puntos |
|---|---|---|
| `LessonCompletedEvent` | `lifecycle` (cierre automático) | 25 pts · `source_type=LESSON` |
| `BookingCreatedEvent` | `scheduling` | logro únicamente, 0 pts directos |
| `ReviewCreatedEvent` | `reputation` | 20 pts · `source_type=REVIEW` |
| `MessageSentEvent` | `messaging` | logro únicamente |
| `StudentProfileUpdatedEvent` | `identity` | logro únicamente |

Si alguno de esos eventos aún no existe, **créalo en el módulo dueño** —un record
inmutable publicado con `ApplicationEventPublisher`— y no metas lógica de gamificación
dentro de ese módulo. La regla es estricta: `scheduling` no sabe qué es un punto.

Mañana un foro publica `PostCreatedEvent` y no se toca nada más. Ese es el motivo de que
esto sea un libro de eventos y no un contador en `users`.

### Cuándo cuenta una clase

Una clase otorga puntos y avanza logros **solo cuando llega a `COMPLETED`** — que en
esta arquitectura lo decide el cierre automático de 24 h, no la hora de fin. Una clase
cancelada o con ausencia **nunca** cuenta.

**Clases gratuitas (tarifa 0):** por defecto **no cuentan**. Las fija un administrador
para probar el flujo en producción, y no queremos que las pruebas contaminen el perfil
de nadie. Ajuste nuevo `platform_settings.gamification_count_free_lessons = false`, para
poder cambiar de opinión sin desplegar si el piloto las usa con estudiantes reales.

### Los ocho evaluadores

El catálogo es data-driven pero **no** con un motor genérico de reglas en JSONB: eso es
un intérprete a medio hacer que nadie sabe depurar. Ocho evaluadores tipados cubren los
20 logros, y un logro nuevo del mismo tipo es un `INSERT`:

| `criteria_type` | Cómo calcula el progreso |
|---|---|
| `LESSON_COUNT` | Reservas `COMPLETED` del estudiante (excluyendo gratuitas) |
| `STREAK_WEEKS` | Racha actual en semanas, del `StreakCalculator` |
| `DISTINCT_PROFESSORS` | Profesores distintos con al menos una clase completada |
| `DISTINCT_LANGUAGES` | `language_code` distintos y no nulos en clases completadas |
| `MODALITY_TAKEN` | Clases completadas de la modalidad del parámetro |
| `EVENT_ONCE` | ¿Existe al menos un evento del tipo del parámetro? |
| `PROFILE_COMPLETE` | Campos diligenciados: foto, objetivo, idioma principal (0–3) |
| `NO_CANCELLATIONS_DAYS` | Días corridos desde la última cancelación del estudiante |

Cada uno es una clase que implementa `AchievementEvaluator` y declara qué
`criteria_type` atiende. `AchievementService` los recorre tras cada evento, actualiza
`progress` en `user_achievements` y, cuando `progress >= target` y `unlocked_at` es
nulo, **desbloquea**: fija la marca, escribe el `point_event` del logro y publica
`AchievementUnlockedEvent`.

**El cálculo va en clases puras**, sin Spring ni reloj del sistema, igual que
`SlotCalculator` y `RankingCalculator`. Es lo que hace que los 20 logros se puedan
probar con datos en memoria.

### La racha, con protección

`LearningProgress` ya calcula la racha semanal del panel. **Extiéndelo, no lo
dupliques** — dos definiciones de racha en el mismo producto es un bug esperando.

Reglas nuevas:

- Semana = lunes a domingo en `America/Bogota`. Una semana cuenta si tuvo al menos una
  clase completada.
- **Una semana de protección al mes**, automática: si una semana queda vacía y el
  estudiante no ha usado su protección del mes, se registra en `streak_protections` y la
  racha continúa. El `UNIQUE (user_id, granted_for)` garantiza que no se use dos veces,
  sin `if`.
- La protección se consume al evaluar la racha, no al reservar. Es retroactiva y
  silenciosa: el estudiante se entera cuando ve su racha intacta, con el estado
  "protegida" del diseño (§2g).
- Perder la racha **no borra la mejor marca**, que se conserva y se muestra.

### Recálculo

`POST /api/v1/admin/engagement/recompute?userId=` — reevalúa todo desde cero para un
estudiante. Sirve para el backfill inicial y para arreglar a alguien si un evento se
perdió. Es idempotente gracias al índice único de `point_events`: reprocesar no duplica
puntos.

**Backfill inicial:** córrelo para todos los estudiantes existentes al desplegar, dentro
de una tarea de arranque que se ejecute una sola vez. Los estudiantes actuales aparecen
con sus estrellas ya encendidas por las clases que ya tomaron — que es lo justo, y evita
que el primer contacto con la función sea un cielo completamente vacío.

**Tests exigidos (este es el paso crítico):**

- Un evaluador por test, con datos sembrados y `Clock` congelado.
- Doble procesamiento del mismo `LessonCompletedEvent` → un solo `point_event`.
- Clase cancelada, con no-show y gratuita → no suman.
- Racha: semana vacía con protección disponible (continúa) · sin protección (se corta) ·
  dos semanas vacías seguidas (se corta aunque haya protección) · cambio de mes renueva
  la protección.
- Desbloqueo: `progress` llega a `target` → `unlocked_at`, puntos, evento publicado, y
  no se vuelve a desbloquear al llegar el siguiente evento.
- `recompute` sobre un estudiante con historial produce exactamente el mismo estado que
  el procesamiento incremental. **Este test es el que protege todo el bloque.**

**DETENTE.**

---

## Paso 4 — API de lectura y notificaciones

| Método y ruta | Quién | Para qué |
|---|---|---|
| `GET /api/v1/me/engagement` | Estudiante | Puntos, racha (actual, mejor, protección), sello derivado, resumen del cielo |
| `GET /api/v1/me/achievements` | Estudiante | Los 20 con estado, progreso `n de N` y fecha de desbloqueo |
| `GET /api/v1/me/cosmetics` | Estudiante | Catálogo con `unlocked: true/false` y su condición **legible** |
| `PUT /api/v1/me/cosmetics` | Estudiante | Equipar marco, paleta, cielo y accesorios por zona |
| `GET /api/v1/me/streak?weeks=12` | Estudiante | Las 12 semanas para el mapa de constancia |

**`PUT /me/cosmetics` valida en el servidor que cada pieza esté desbloqueada.** Un `422`
si no. Confiar en que el frontend solo muestre lo desbloqueado es cómo alguien se pone
la corona con `curl`.

`GET /me/cosmetics` devuelve la condición **en texto**, no el código del logro: el
estudiante tiene que leer "Con diez clases", no `volumen-10-clases`. Sale de
`achievements.description`.

**El mapa de constancia cambia a 12 semanas.** El panel actual muestra un mapa del
último año; con una o dos clases por semana esa cuadrícula está 98 % vacía y comunica
abandono en lugar de progreso. Reemplázalo por las últimas 12 semanas, una celda por
semana, con los estados del diseño (§2g): cumplida, en curso, futura, protegida.

**Notificaciones.** `AchievementUnlockedEvent` crea fila en `notifications` (in-app, ya
existe) con `link_path` al tablero de logros. **Sin correo** — un correo por cada
estrella es exactamente cómo se enseña a la gente a ignorar tus correos. Excepción
única: los hitos de brillo 3 (`24-semanas` y `100-clases`), que sí merecen uno.

Si se desbloquean varios logros en el mismo evento, **una sola notificación agrupada**
("Encendiste 2 estrellas"), no tres seguidas.

**DETENTE.**

---

## Paso 5 — Frontend: los componentes del sistema

### La decisión que más código ahorra

El diseño entrega 20 logros × 3 estados = **60 SVG**. No los exportes como 60 archivos.

El entregable está construido sobre datos: un único `path` de estrella
(`poly(5, 220, 118)`), un halo, cuatro rayos, un glifo Lucide y un color por familia.
Todo eso ya vive en `achievements`. Construye **un componente**:

```tsx
<EstrellaLogro
  familia="CONSTANCIA"     // color: durazno | lavanda
  brillo={2}               // 1 estrella · 2 + halo · 3 + halo y rayos
  estado="progreso"        // apagada | progreso | encendida
  progreso={{ hecho: 5, total: 8 }}
  glifo="calendar-plus"    // Lucide, o numeral
/>
```

Sesenta archivos de 8 KB son casi medio megabyte en una PWA instalable, más un archivo
nuevo por cada logro futuro. Un componente parametrizado son 3 KB y un logro nuevo es
una fila. Las medidas exactas están en el contrato visual del `README.md` del
entregable: radio exterior 220, interior 118, trazo 50 con `paint-order: stroke fill`,
halo r 246, rayos cardinales, glifo en `translate(172 172) scale(7)`, órbita de progreso
r 240 con circunferencia 1508.

**Sí van como archivos estáticos**, porque son ilustración y no composición: los 3
accesorios, los 3 sellos y los 3 discos de idioma. Nueve archivos, a
`frontend/public/gamificacion/`.

**Marcos, paletas y fondos son CSS puro** — `radial-gradient`, `conic-gradient`,
`box-shadow`, `mask` — tal como los entregó el diseño. Cero imágenes. Los valores exactos
están en §2e.

### Composición del avatar (dirección 1c «Sello»)

La foto real **no se toca**: el progreso la rodea. Orden de capas fijo:
cielo (fondo) → marco/órbita → foto → sello bajo la foto → accesorios z1 → z2 → z3.
Ningún accesorio sale del círculo r 240 ni tapa más del 40 % de la estrella. Las zonas
de anclaje están en el `README.md` del entregable con sus coordenadas.

### Tokens

Agrega el bloque `@theme` de §2i tal cual. Son **alias por función sobre colores que ya
existen** — ningún color nuevo entra al sistema. El coral (`--color-primary`) queda
deliberadamente fuera de la gamificación: es el color de la acción, y si también celebra
logros deja de significar "toca aquí".

### Motion

La celebración dura 720 ms en cuatro cuadros: trazo (`stroke-dashoffset`) → encendido
(`scale 1.08` + anillo) → reposo (glifo + destellos). Solo `transform` y `opacity`, que
es lo que el compositor puede animar sin repintar. Con `prefers-reduced-motion`: el
cuadro final con un fundido de 200 ms, sin destellos y sin Rigel.

**Rigel aparece exactamente dos veces** en todo el bloque: un cameo en el cielo señalando
la próxima estrella (no en móvil, no con el cielo completo) y un cameo en la celebración
completa. Su guía manda; ante la duda, menos Rigel.

**DETENTE.**

---

## Paso 6 — Las pantallas

> **No arranques este paso sin la Etapa 3 del diseño** (las 7 pantallas en 390 y
> 1280 px). Los pasos 0–5 no dependen de ella; este sí. Si no ha llegado, detente aquí
> y avísale a Pardo — no improvises maquetación.

Pantallas a construir, con su mockup:

1. **Perfil del estudiante · privado** — avatar compuesto, objetivos, estadísticas,
   racha de 12 semanas, últimas estrellas encendidas, ajustes de privacidad.
2. **Perfil del estudiante · público** — la vista que ve el profesor. Enlace desde la
   tarjeta de la clase y desde el hilo de mensajes.
3. **Tablero de logros (el cielo)** — cinco constelaciones con las coordenadas de §2b;
   líneas que se dibujan cuando ambas estrellas están encendidas; en 390 px se apila por
   familia.
4. **El encendido** — la celebración, con su alternativa reducida.
5. **Personalizador de avatar** — lo bloqueado a la vista con su condición legible.
6. **Mapa de constancia de 12 semanas** — reemplaza el mapa anual del panel actual.
7. **Estados vacíos** — sin logros, sin objetivos, sin clases. El primero es el que más
   gente va a ver: tiene que invitar, no lamentar.

**Copy:** el del diseño, aprobado con la voz de marca. Se nombra lo que la persona hizo;
nunca se cuentan ausencias. Prohibido "Perdiste tu racha" y equivalentes.

**E2E (Playwright):** estudiante completa perfil → se enciende «Perfil listo» y aparece
la notificación · toma su primera clase (con el cierre automático forzado) → se encienden
dos estrellas y suben los puntos · equipa un marco desbloqueado y persiste · intenta
equipar uno bloqueado por API → `422` · activa el perfil público y otro estudiante lo ve;
lo desactiva y deja de verlo.

**DETENTE. Fin del brief.**

---

## Definition of done

- [ ] `bookings.language_code` poblado donde es deducible, con el resto listado para
      revisión manual
- [ ] Todo estudiante tiene ficha; el perfil público es privado por defecto y los menores
      no pueden activarlo
- [ ] Los 20 logros del diseño existen en el catálogo con sus textos originales
- [ ] El backfill dejó a los estudiantes actuales con sus estrellas ya encendidas
- [ ] `recompute` produce el mismo estado que el procesamiento incremental
- [ ] Ningún módulo existente importa clases de `engagement`
- [ ] El mapa anual fue reemplazado por 12 semanas
- [ ] `docs/ESTADO.md` actualizado

## Fuera de alcance

Foros y puntos por participación, tablas de clasificación entre estudiantes, logros para
profesores, canje de puntos por cualquier cosa con valor económico, accesorios más allá
de los tres del contrato, y compartir el perfil fuera de la plataforma.

Si algo de esto parece necesario para completar un paso, **detente y pregunta**.

---

## Nota sobre las tablas de clasificación

Están fuera de alcance a propósito, y conviene que quede escrito por qué: un ranking
público entre estudiantes adultos que aprenden idiomas por vergüenza de hablar
convierte una herramienta de motivación en una de comparación. El estudiante que va
último no acelera: se va. Si algún día se prueba, que sea entre grupos privados y con
salida voluntaria, nunca global y nunca por defecto.
