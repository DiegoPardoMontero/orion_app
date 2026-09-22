# Bloque 10 — Acta de clase y práctica entre clases

> **Ubicación:** `docs/briefs/orion-bloque-10-acta-y-practica.md`
> **Features de referencia:** F2 y F3 de `orion-features-ia.md`
> **Diseño:** «Amanecer cálido premium» + lenguaje constelación del Bloque 8.
> **Dependencias:** ninguna sobre el Bloque 9 (diagnóstico). Este bloque puede
> construirse primero o en paralelo.

---

## 0. Qué construye este bloque

Un solo proyecto con dos caras:

**F2 · Acta de clase.** El profesor escribe tres líneas al terminar la clase; la IA las
convierte en un acta con cuatro secciones; el profesor la revisa, la corrige y la
publica; el estudiante la lee.

**F3 · Práctica entre clases.** De esa acta salen entre cinco y diez minutos de
ejercicios para los días intermedios, con el vocabulario y los errores de **su** clase,
con **su** profesor. Completarlos alimenta puntos y racha.

**Van juntos y no se separan.** La práctica sin actas tendría que generar contenido
genérico de internet, que es exactamente lo que la vuelve irrelevante; el acta sin
práctica es un PDF que se lee una vez.

### Lo que NO construye

Dictado por voz, actas de clases grupales, ejercicios de pronunciación o de audio
generado, corrección de texto libre abierta, práctica que no venga de una clase real, ni
ninguna generación que llegue al estudiante sin que un profesor la haya visto.

### Las cinco reglas del bloque

1. **La IA reorganiza, no inventa.** El acta solo puede contener lo que el profesor
   escribió. Si no mencionó vocabulario, no hay sección de vocabulario. Un acta con un
   dato falso sobre lo que el estudiante dijo destruye la confianza en una sola clase.
2. **Nada llega al estudiante sin aprobación humana.** El borrador es invisible hasta que
   el profesor publica. Los ejercicios salen de un acta ya publicada, es decir, ya
   revisada.
3. **La función nunca bloquea a nadie.** Si el proveedor de IA falla, el profesor
   escribe el acta a mano en los mismos cuatro campos. La IA es una mejora del camino,
   nunca el camino.
4. **Menos de treinta segundos o el profesor no vuelve.** La entrada es una caja de texto
   libre, no un formulario de ocho campos.
5. **La práctica nunca se lee como un examen.** Sin nota, sin "reprobaste", sin
   porcentaje de aciertos en primer plano.

### Módulos y dirección de dependencias

Dos módulos nuevos: **`teaching`** (actas) y **`practice`** (ejercicios).

```
scheduling ──▶ teaching ──▶ practice
                  │             │
                  └──▶ notifications ◀──┘
                                │
                         engagement (escucha, no lo importa nadie)
```

`practice` lee vocabulario de `teaching`; nadie más importa a ninguno de los dos. Si
ambos desaparecen, el marketplace sigue funcionando completo. Es la misma arista
unidireccional que ya existe con `identity → reputation`.

---

## 1. Dependencia cruzada que hay que resolver primero

**`bookings` necesita `language_code`.** La práctica se genera por idioma, y hoy el
idioma se deduce del profesor — deducción imposible cuando enseña dos.

- Si el **Bloque 8 ya se construyó**, el campo existe: no hagas nada.
- Si **no**, este bloque incluye la migración del Paso 0 del Bloque 8 (columna, backfill
  donde la deducción es inequívoca, selector de idioma al reservar). **No la pospongas:**
  el dato no se recupera hacia atrás, y cada clase que pasa sin registrarlo es una clase
  cuyo idioma nunca sabremos.

**El enganche con la gamificación es opcional.** Si el Bloque 8 no existe, `practice`
publica su evento igual y simplemente nadie lo escucha. Los ejercicios funcionan; los
puntos llegan cuando llegue `engagement`. Eso es exactamente para lo que sirve un bus de
eventos.

---

## 2. Decisiones, con su valor por defecto

Si Pardo no responde, Claude Code usa el valor de la derecha y lo anota en el PR.

| # | Pregunta | Por defecto |
|---|---|---|
| **D1** | ¿El acta es obligatoria? | **No.** Obligarla produce actas de una línea escritas de mala gana. Se incentiva: el porcentaje de clases con acta aparece en el desempeño del profesor |
| **D2** | ¿Se puede editar un acta publicada? | **Sí, 72 h**, con aviso al estudiante de que se actualizó. Después queda congelada |
| **D3** | ¿El estudiante puede responder el acta? | **No.** Para eso está la mensajería interna, y el acta enlaza a ella |
| **D4** | ¿Actas retroactivas? | **No.** Solo clases completadas a partir del despliegue |
| **D5** | ¿Quién genera la práctica? | Un **job al publicarse el acta**, nunca al abrir la pantalla. Sin espera para el estudiante y sin pagar inferencia por cada visita |
| **D6** | ¿Cuánto vive un set de práctica? | **7 días.** No se acumulan: quien vuelve tras un mes no encuentra seis tareas esperándolo |
| **D7** | ¿Qué ve el proveedor de IA del estudiante? | **Solo nombre de pila, idioma, nivel y objetivo.** Nunca apellido, correo, teléfono ni identificador |
| **D8** | ¿Puede el estudiante pedir más práctica? | **No en este bloque.** Un set por acta. Evita el costo abierto y mantiene el vínculo con la clase |

---

# PARTE A · El acta (F2)

## Paso A1 — Esquema

### `Vnn__lesson_notes.sql`

> Usa el siguiente número libre de Flyway; los nombres de archivo y tabla son fijos.

```sql
CREATE TABLE lesson_notes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id       UUID UNIQUE NOT NULL REFERENCES bookings(id),
    professor_id     UUID NOT NULL REFERENCES users(id),
    student_id       UUID NOT NULL REFERENCES users(id),
    language_code    VARCHAR(5) REFERENCES languages(code),

    raw_input        VARCHAR(2000) NOT NULL,

    worked_on        VARCHAR(1200),
    recurring_issues VARCHAR(1200),
    next_steps       VARCHAR(1200),

    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'PUBLISHED')),
    origin           VARCHAR(20) NOT NULL DEFAULT 'AI_DRAFT'
                     CHECK (origin IN ('AI_DRAFT', 'MANUAL')),
    edit_ratio       NUMERIC(4,3),
    prompt_version   VARCHAR(20),

    published_at     TIMESTAMPTZ,
    last_edited_at   TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

CREATE INDEX idx_notes_student ON lesson_notes(student_id, published_at DESC)
    WHERE status = 'PUBLISHED';
CREATE INDEX idx_notes_professor_draft ON lesson_notes(professor_id)
    WHERE status = 'DRAFT';

-- Vocabulario en tabla propia: es lo que la práctica consume y lo que F5
-- usará para medir crecimiento de vocabulario.
CREATE TABLE lesson_vocabulary (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_note_id UUID NOT NULL REFERENCES lesson_notes(id) ON DELETE CASCADE,
    student_id     UUID NOT NULL REFERENCES users(id),
    language_code  VARCHAR(5) REFERENCES languages(code),
    term           VARCHAR(120) NOT NULL,
    meaning        VARCHAR(300),
    display_order  SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_vocab_note    ON lesson_vocabulary(lesson_note_id);
CREATE INDEX idx_vocab_student ON lesson_vocabulary(student_id, language_code);

-- Consumo y costo de IA. Sin esto no hay forma de saber qué cuesta la función.
CREATE TABLE ai_usage_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature       VARCHAR(40) NOT NULL,
    actor_id      UUID REFERENCES users(id),
    provider      VARCHAR(40) NOT NULL,
    model         VARCHAR(80),
    input_tokens  INTEGER,
    output_tokens INTEGER,
    cost_cop      BIGINT,
    latency_ms    INTEGER,
    outcome       VARCHAR(20) NOT NULL
                  CHECK (outcome IN ('OK','REFUSED','INVALID_OUTPUT','ERROR','TIMEOUT')),
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_usage ON ai_usage_log(feature, occurred_at DESC);

ALTER TABLE bookings ADD COLUMN note_nudge_sent_at TIMESTAMPTZ;

INSERT INTO platform_settings (key, value) VALUES
    ('ai_lesson_notes_enabled',       'true'),
    ('ai_daily_budget_cop',           '30000'),
    ('ai_note_timeout_seconds',       '25'),
    ('lesson_note_edit_window_hours', '72'),
    ('lesson_note_nudge_minutes',     '60');
```

**El `CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))`** hace que la base
garantice la coherencia: no existe acta publicada sin fecha ni fecha de publicación en un
borrador. Misma doctrina de siempre — la regla dura vive en la base, el servicio da el
422 amable.

**`edit_ratio`** guarda cuánto cambió el profesor el borrador (0 = publicó tal cual,
1 = lo reescribió entero). Es el termómetro que dice si la IA ayuda o estorba, y sin él
la función se evalúa por corazonada.

**`prompt_version`** permite saber qué versión del prompt produjo cada acta. Cuando se
ajuste el prompt, es lo que deja comparar antes y después.

**DETENTE.**

## Paso A2 — El generador

### La interfaz

Mismo patrón que `MeetingLinkProvider` y `PaymentProvider`: cambiar de proveedor no
puede tocar el dominio.

```java
public interface LessonNoteDrafter {
    LessonNoteDraft draft(LessonNoteContext context);
}

public record LessonNoteContext(
    String rawInput,
    String studentFirstName,   // solo nombre de pila (D7)
    String languageName,       // "inglés"
    String studentLevel,       // BEGINNER | INTERMEDIATE | ADVANCED | null
    String studentGoal         // CONVERSATION | BUSINESS | ... | null
) {}

public record LessonNoteDraft(
    String workedOn,
    String recurringIssues,
    String nextSteps,
    List<VocabularyItem> vocabulary,
    String promptVersion
) {}
```

Implementaciones: la real contra el proveedor contratado y **`NoopLessonNoteDrafter`
para local y tests**, que devuelve un borrador determinista. Ninguno de los 391 tests de
integración puede depender de una llamada de red, y eso no se rompe aquí.

### El prompt

En archivo versionado (`resources/prompts/lesson-note-v1.txt`), **nunca incrustado en el
código**. Debe contener, sin ambigüedad:

- Rol: asistente que ordena las notas de un profesor de idiomas.
- **Prohibición dura:** no agregar nada que el profesor no haya mencionado. Si falta
  información para una sección, esa sección va vacía. Un acta con dos secciones ciertas
  vale más que cuatro inventadas.
- Salida en **JSON estricto**: sin explicación, sin bloques de código, sin texto extra.
- Español (Colombia), voz de marca: cercana, clara, positiva, profesional.
- Se dirige al estudiante en segunda persona y **nunca lo regaña**: "vamos a trabajar
  esto" en lugar de "sigues equivocándote en esto".
- **Frases prohibidas literales:** "no sabes", "tu nivel es muy malo", "eso está
  completamente mal", "es muy fácil", "aprenderás inglés perfecto".
- Vocabulario: máximo 12 términos, cada uno con significado corto en español.
- Máximo 1.200 caracteres por sección.

### Robustez — lo que decide si esto sirve

- **Timeout de 25 s.** Al vencerse, el profesor ve el formulario manual con sus notas ya
  cargadas y un aviso honesto: *"No pudimos armar el borrador. Puedes escribirla tú; ya
  guardamos tus notas."*
- **Validación de salida:** si el JSON no parsea, trae campos de más o excede límites,
  se registra `INVALID_OUTPUT` y se cae al camino manual. **Un reintento, no más.**
- **Corte por presupuesto:** se consulta el gasto del día antes de llamar. Al tope, la
  generación se apaga y el profesor ve el camino manual, sin mensaje de error. Aviso al
  administrador al 80 %.
- Toda llamada escribe fila en `ai_usage_log`, termine como termine.

**DETENTE.**

## Paso A3 — API del acta

| Método y ruta | Quién | Para qué |
|---|---|---|
| `POST /api/v1/bookings/{id}/lesson-note/draft` | Profesor de la reserva | Genera borrador desde `rawInput` |
| `GET /api/v1/bookings/{id}/lesson-note` | Profesor o estudiante de la reserva | El acta, con visibilidad según rol |
| `PUT /api/v1/lesson-notes/{id}` | Profesor autor | Guardar cambios |
| `POST /api/v1/lesson-notes/{id}/publish` | Profesor autor | Publicar |
| `GET /api/v1/me/lesson-notes?status=DRAFT` | Profesor | Borradores pendientes |
| `GET /api/v1/me/lesson-notes` | Estudiante | Sus actas publicadas, paginadas |

**Reglas de autorización, cada una con su test:**

- Crear o editar exige ser **el profesor de esa reserva**. Otro profesor: `403`.
- La reserva debe estar `COMPLETED`. Otro estado: `422` con mensaje claro.
- El `GET` del estudiante devuelve el acta **solo si está publicada**. Un borrador es
  `404` para él, no `403`: no se confirma que exista algo que no debería ver.
- Acta publicada hace más de 72 h: `422` al editar.
- Tercero sin relación con la reserva: `404`.

**La respuesta al estudiante nunca incluye `raw_input`, `edit_ratio`, `origin` ni
`prompt_version`.** Lo que el profesor escribió en crudo es su cuaderno privado.

**DETENTE.**

## Paso A4 — Publicación, eventos y recordatorio

Al publicar, en la misma transacción: se fija `published_at`, se calcula `edit_ratio`
contra el borrador original y se publica `LessonNotePublishedEvent`.

Los consumidores escuchan con `@TransactionalEventListener(phase = AFTER_COMMIT)`, como
todo lo demás:

- **`notifications`**: notificación in-app *"Valentina publicó el resumen de tu clase del
  martes."* con enlace. **Sin correo** — un correo por acta es cómo se enseña a la gente
  a ignorar los correos.
- **`practice`**: encola la generación del set (Parte B).

### El recordatorio, una sola vez

Job cada 15 minutos: para cada clase completada hace más de `lesson_note_nudge_minutes`
(60), sin acta y sin recordatorio enviado, crea **una** notificación in-app al profesor:
*"¿Nos cuentas cómo estuvo tu clase con Mariana?"*

Idempotente vía `bookings.note_nudge_sent_at`, el mismo patrón de `reminder_sent_at` que
ya existe. **Una vez y nunca insiste.** Un sistema que persigue al profesor todos los
días es un sistema que el profesor aprende a ignorar.

**DETENTE.**

## Paso A5 — Pantallas del acta

Todo con los tokens `@theme` existentes. **Coral (`--color-primary`) es el color de la
acción**: botones de generar y publicar. El contenido del acta usa superficie elevada y
los acentos durazno y lavanda. Cada pantalla en **390 px y 1280 px**, foco visible,
objetivos táctiles ≥ 44 px, contraste AA, alternativa para `prefers-reduced-motion`.

### A5.1 · Profesor — cerrar la clase

En la tarjeta de la clase recién terminada, dentro de "Mis clases":

> **Cuéntanos cómo estuvo**
> *Escribe o dicta lo que se te venga a la cabeza. Nosotros le damos forma.*
> [ área de texto, 4 filas, contador discreto ]
> **[ Generar acta ]**   ·   *Ahora no*

El campo acepta cualquier cosa: *"trabajamos past simple, sigue diciendo 'I go
yesterday', le costó 'used to', quedamos en ver condicionales"*. Mínimo 20 caracteres
para habilitar el botón.

Durante la generación, estado de espera con la constelación dibujándose, máximo 25
segundos. **Nunca un spinner genérico.** Rigel puede aparecer como Cameo según su guía.

### A5.2 · Profesor — revisar el borrador

Las cuatro secciones, **todas editables en línea**, con el vocabulario como chips que se
pueden quitar y una entrada para agregar.

Arriba, insignia **Borrador** sobre `--color-warning-bg`, y una línea sin letra chica:
> *Revísala antes de publicar. Lo que publiques es lo que verá tu estudiante.*

Abajo: **[ Publicar ]** en coral y **[ Guardar sin publicar ]** como secundario.
En escritorio, dos columnas: acta a la izquierda, sus notas originales a la derecha para
contrastar. En móvil, las notas originales van en un acordeón cerrado.

**Camino manual:** si la generación falla o está apagada, se muestran los mismos cuatro
campos vacíos con las notas ya guardadas arriba. Mismo diseño, cero mensajes de error
técnico.

### A5.3 · Estudiante — leer el acta

En la tarjeta de la clase pasada, expandible:

> **Lo que trabajaron** · **Para tener presente** · **Palabras nuevas** · **Lo que sigue**

El vocabulario como chips en lavanda. Debajo, **[ Practicar esto ]** en coral — que es la
puerta a la Parte B. **Si la Parte B no está desplegada, el botón no existe**: nada de
interfaz muerta.

Si el acta se editó después de publicada, una línea discreta: *"Actualizada el 12 de
septiembre."*

### A5.4 · Estados vacíos

- Estudiante sin actas: *"Todavía no hay resúmenes. Aparecerán aquí después de tus
  clases."*
- Profesor sin borradores pendientes: *"Estás al día."*

Ambos invitan, ninguno lamenta. Ninguna pantalla de este bloque usa las frases prohibidas
del manual de marca.

**DETENTE. Fin de la Parte A.**

---

# PARTE B · La práctica (F3)

## Paso B1 — Esquema

### `Vnn__practice.sql`

```sql
CREATE TABLE practice_sets (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_note_id UUID UNIQUE NOT NULL REFERENCES lesson_notes(id) ON DELETE CASCADE,
    professor_id   UUID NOT NULL REFERENCES users(id),
    language_code  VARCHAR(5) REFERENCES languages(code),

    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING','READY','IN_PROGRESS',
                                     'COMPLETED','EXPIRED','FAILED')),
    estimated_minutes SMALLINT,
    item_count        SMALLINT NOT NULL DEFAULT 0,
    correct_count     SMALLINT NOT NULL DEFAULT 0,

    expires_at     TIMESTAMPTZ NOT NULL,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

CREATE INDEX idx_practice_student ON practice_sets(student_id, created_at DESC);
CREATE INDEX idx_practice_active  ON practice_sets(student_id)
    WHERE status IN ('READY','IN_PROGRESS');

CREATE TABLE practice_items (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    practice_set_id UUID NOT NULL REFERENCES practice_sets(id) ON DELETE CASCADE,
    item_index     SMALLINT NOT NULL,
    item_type      VARCHAR(30) NOT NULL
                   CHECK (item_type IN ('FILL_BLANK','FIX_SENTENCE','MATCH_MEANING',
                                        'ORDER_DIALOGUE','WRITE_SENTENCE')),
    prompt         VARCHAR(600)  NOT NULL,
    payload        JSONB         NOT NULL,   -- opciones, piezas, huecos
    expected       VARCHAR(600),
    explanation    VARCHAR(400)  NOT NULL,
    source_term    VARCHAR(120),             -- término de vocabulario del que salió

    answer         VARCHAR(600),
    is_correct     BOOLEAN,
    attempts       SMALLINT NOT NULL DEFAULT 0,
    answered_at    TIMESTAMPTZ,

    UNIQUE (practice_set_id, item_index)
);

INSERT INTO platform_settings (key, value) VALUES
    ('practice_enabled',          'true'),
    ('practice_set_ttl_days',     '7'),
    ('practice_items_per_set',    '4'),
    ('practice_max_attempts',     '2');
```

**`lesson_note_id` es `UNIQUE`:** un acta genera exactamente un set, para siempre.
Republicar un acta editada no crea un segundo set.

**`expires_at` se fija al crear**, a 7 días. El set viejo no compite con el nuevo.

**DETENTE.**

## Paso B2 — Generación diferida

Al recibir `LessonNotePublishedEvent`, `practice` crea el set en `PENDING` y encola el
trabajo. Un job lo procesa y lo deja en `READY`. **Nunca se genera al abrir la pantalla:**
el estudiante no espera y no se paga inferencia por cada visita.

### La interfaz

```java
public interface PracticeGenerator {
    List<GeneratedItem> generate(PracticeContext context);
}

public record PracticeContext(
    String languageName,
    String studentLevel,
    String studentGoal,
    List<VocabularyItem> vocabulary,   // del acta
    String recurringIssues,            // del acta
    String workedOn,                   // del acta
    int itemCount
) {}
```

Con `NoopPracticeGenerator` determinista para local y tests.

### Los cinco tipos de ejercicio

| Tipo | Qué pide | De dónde sale |
|---|---|---|
| `FILL_BLANK` | Completar la frase con el término correcto | Vocabulario del acta |
| `FIX_SENTENCE` | Corregir una frase que contiene el error recurrente | `recurring_issues` |
| `MATCH_MEANING` | Emparejar término con significado | Vocabulario |
| `ORDER_DIALOGUE` | Ordenar 4–5 intervenciones de una conversación corta | `worked_on` |
| `WRITE_SENTENCE` | Escribir una frase propia usando un término | Vocabulario |

Un set trae **4 ejercicios** (`practice_items_per_set`) de tipos variados, nunca cuatro
iguales.

### Reglas duras de generación

- **Todo ítem se ancla a contenido del acta.** `FILL_BLANK` y `MATCH_MEANING` deben
  referenciar un `term` real en `source_term`. Un ítem que no se pueda anclar **se
  descarta**.
- Si tras validar quedan **menos de dos** ítems, el set queda `FAILED` y no se le ofrece
  al estudiante. **Mejor ninguna práctica que práctica inventada.**
- `WRITE_SENTENCE` no tiene respuesta exacta: se evalúa que use el término y tenga
  estructura mínima, y la retroalimentación es siempre constructiva. Nunca se marca como
  incorrecta una frase válida que el generador no previó.
- Salida en JSON validada contra esquema por tipo. Inválida → se descarta ese ítem, no el
  set entero.
- Todo en texto. **Nada de voz.** Es la función más barata de las cinco y así debe
  quedarse.

**DETENTE.**

## Paso B3 — API de la práctica

| Método y ruta | Quién | Para qué |
|---|---|---|
| `GET /api/v1/me/practice` | Estudiante | Set activo (o vacío) con duración y clase de origen |
| `POST /api/v1/practice-sets/{id}/start` | Su dueño | `READY` → `IN_PROGRESS` |
| `POST /api/v1/practice-items/{id}/answer` | Su dueño | Responder; devuelve resultado y explicación |
| `POST /api/v1/practice-sets/{id}/complete` | Su dueño | Cerrar y devolver resumen |
| `GET /api/v1/me/practice/history` | Estudiante | Sets anteriores |
| `GET /api/v1/professors/me/students/{id}/practice` | Profesor con reserva | **Resumen agregado**, nunca respuestas |

**Reglas:**

- Solo el dueño del set opera sobre él. Otro: `404`.
- Un set `EXPIRED` no acepta respuestas: `422`.
- Máximo 2 intentos por ítem (`practice_max_attempts`); después se muestra la respuesta
  con su explicación y se sigue.
- Completar un set ya completado es **idempotente**: devuelve el resumen, no recalcula
  nada ni vuelve a emitir eventos.
- Job diario marca `EXPIRED` los vencidos.

**DETENTE.**

## Paso B4 — Enganche con la gamificación

Al completar, `practice` publica `PracticeCompletedEvent(studentId, setId, itemCount,
correctCount, completedAt)`.

`engagement` lo escucha con `AFTER_COMMIT` y escribe un `point_event` con
`source_type = 'PRACTICE'` y `source_id = setId`. El índice único
`(source_type, source_id)` garantiza que completar dos veces **no otorga puntos dos
veces**, sin un solo `if`.

**Nada del módulo `engagement` se modifica.** Eso es exactamente para lo que se diseñó el
libro de eventos: una fuente nueva no toca el dominio de la gamificación.

**Consecuencia de producto que hay que decidir a conciencia:** hoy la racha semanal solo
avanza reservando y pagando una clase. Con la práctica, también avanza practicando. La
racha pasa de medir consumo a medir esfuerzo, y un estudiante con una semana apretada no
pierde su constancia por no tener plata ese martes. **Me parece mejor así, pero toca el
Bloque 8 ya definido: confírmalo con Sofía antes de implementarlo.**

Si el Bloque 8 no existe todavía, el evento se publica y nadie lo escucha. La práctica
funciona igual.

**DETENTE.**

## Paso B5 — Pantallas de la práctica

### B5.1 · La invitación

En el panel del estudiante, debajo de la próxima clase:

> **Para esta semana** · 4 min
> *Del martes con Valentina: past simple y cinco palabras nuevas.*
> **[ Practicar ]**

Si no hay set activo, **la tarjeta no aparece**. Nada de "no tienes práctica disponible".

### B5.2 · El ejercicio

Un ítem por pantalla. Arriba, progreso como **cuatro puntos** que se encienden — el mismo
lenguaje de constelación del Bloque 8, no una barra genérica. Se puede salir y volver
donde iba.

Respuesta inmediata. Correcta: confirmación breve en lavanda y avanza. Incorrecta:

> *Casi. Aquí va **went**, porque estás hablando de ayer.*
> **[ Intentar otra vez ]**

**Nunca rojo de alarma.** El error usa `--color-warning-bg`, no `--color-error-bg`: un
error de práctica no es un fallo del sistema, y el color lo tiene que decir.

### B5.3 · El cierre

> **Listo. Cuatro minutos bien usados.**
> *Te quedó clara la diferencia entre* went *y* gone*. Para repasar: los irregulares en
> pasado.*
> **+15 puntos**

Si desbloqueó un logro, se enciende la estrella con la celebración del Bloque 8, con su
alternativa para `prefers-reduced-motion`.

**Nunca "3 de 4" ni un porcentaje en primer plano.** Se muestra lo logrado y lo que
conviene repasar. La práctica no es un examen y la pantalla no puede insinuar que lo es.

### B5.4 · Lo que ve el profesor

En la ficha del estudiante, antes de la clase:

> **Practicó 3 de 4 veces esta semana.** Le costó el past simple irregular.

**Resumen agregado, nunca las respuestas una por una.** Si el estudiante siente que sus
ejercicios son vigilados, deja de arriesgarse a equivocarse — y equivocarse en privado es
justamente el valor de la función.

**DETENTE. Fin de la Parte B.**

---

## Paso C1 — Panel de administración y calidad

Fila en el tablero del admin, junto a lo que ya existe:

| Métrica | Por qué importa |
|---|---|
| Actas generadas y publicadas hoy | Volumen real de uso |
| **% de clases completadas con acta** | Si es bajo, el flujo estorba y hay que arreglarlo |
| **Distribución de `edit_ratio`** (sin editar / edición menor / reescrita) | **Si más de la mitad se reescriben por completo, la función se revisa antes de ampliarla** |
| Sets generados, completados y expirados | Si expiran más de los que se completan, la práctica no engancha |
| Gasto de IA del día contra el tope | Control de costo |
| `outcome` de `ai_usage_log` | Salud del proveedor |

En desempeño del profesor se agrega **% de clases con acta**. Informativo, **nunca
sancionable**: las sanciones siguen en modo observación y esto no las alimenta.

**DETENTE.**

## Paso C2 — Pruebas exigidas

**Del acta:**

- Generar sobre reserva no completada → `422`; sobre reserva ajena → `403`.
- Timeout del proveedor → camino manual con `rawInput` conservado; fila `TIMEOUT` en
  `ai_usage_log`.
- JSON inválido → un reintento, luego camino manual; fila `INVALID_OUTPUT`.
- Presupuesto agotado → la generación se apaga y el profesor ve el camino manual.
- Borrador invisible para el estudiante (`404`), visible tras publicar.
- Publicar dos veces la misma acta: idempotente, un solo evento.
- Editar a las 71 h funciona; a las 73 h → `422`.
- `edit_ratio` = 0 publicando sin tocar nada; cercano a 1 reescribiendo todo.
- La respuesta al estudiante **no contiene** `raw_input`, `edit_ratio`, `origin` ni
  `prompt_version` (assert campo por campo).
- El recordatorio se envía una sola vez por reserva.

**De la práctica:**

- Publicar un acta encola el set; el job lo deja `READY` con 4 ítems.
- Acta sin vocabulario ni errores → set `FAILED`, **no se ofrece nada** al estudiante.
- Generación con 1 ítem válido → `FAILED`; con 2 → `READY`.
- Tercer intento sobre un ítem → `422`.
- Set expirado no acepta respuestas.
- Completar dos veces → **un solo** `point_event` (prueba del índice único).
- Responder un ítem de otro estudiante → `404`.
- El resumen del profesor no incluye respuestas individuales (assert campo por campo).
- `WRITE_SENTENCE` con una frase válida no prevista **no se marca incorrecta**.

**Transversales:**

- Un test que recorra todos los textos del bloque y **falle si aparece alguna frase
  prohibida** del manual de marca. Es barato y cubre el peor fallo posible.
- Ningún módulo existente importa clases de `teaching` ni de `practice`.
- Con `practice_enabled = false`, el botón "Practicar esto" no se renderiza y el acta
  funciona igual.

**E2E (Playwright), con los generadores deterministas:**

1. Profesor cierra clase → escribe 3 líneas → genera → edita una sección → publica.
2. Estudiante recibe notificación → lee el acta → entra a practicar → completa los 4
   ítems → ve el cierre y sus puntos.
3. Profesor ve "practicó 1 de 1 esta semana" en la ficha antes de la siguiente clase.
4. Variante de fallo: el proveedor cae → el profesor escribe el acta a mano y la publica
   igual.

---

## Definition of done

- [ ] `bookings.language_code` existe y se está poblando
- [ ] Un profesor pasa de tres líneas a acta publicada en menos de 30 segundos
- [ ] Si la IA falla, el profesor publica a mano sin fricción añadida
- [ ] Ningún acta llega al estudiante sin aprobación humana
- [ ] Un acta publicada genera automáticamente su set de práctica
- [ ] Todo ejercicio se ancla a contenido real del acta; sin anclaje no hay ejercicio
- [ ] Completar práctica otorga puntos una sola vez, sin tocar `engagement`
- [ ] El panel muestra `edit_ratio` y gasto de IA contra el tope
- [ ] Tope de gasto con aviso al 80 % y apagado automático al 100 %
- [ ] Ningún módulo existente importa `teaching` ni `practice`
- [ ] `docs/ESTADO.md` actualizado

## Fuera de alcance

Dictado por voz, ejercicios de audio o pronunciación, actas de clases grupales, práctica
bajo demanda sin clase previa, corrección de texto libre abierta, exportar el acta a PDF,
y que el estudiante responda el acta (para eso está la mensajería).

Si algo de esto parece necesario para completar un paso, **detente y pregunta**.

---

## Orden de ejecución sugerido

| Paso | Qué |
|---|---|
| 1 | Dependencia cruzada: `bookings.language_code` |
| 2 | A1 → A4: esquema, generador, API y eventos del acta |
| 3 | A5: pantallas del acta |
| 4 | **Desplegar y usar con profesores reales durante una o dos semanas** |
| 5 | B1 → B5: la práctica completa |
| 6 | C1: panel y métricas |

El paso 4 no es relleno. El `edit_ratio` de las primeras cincuenta actas reales dice si
el prompt sirve, y ajustar el prompt **antes** de construir la práctica encima es más
barato que descubrir después que los ejercicios salen de actas mediocres.

---

## Lo que hay que resolver fuera del código

**Los profesores se enteran por Sofía, no por la interfaz.** La diferencia entre "la IA
me reemplaza" y "la IA me quita lo aburrido" es enteramente de encuadre, y el encuadre se
pone en una conversación antes del lanzamiento. Un marketplace que pierde su oferta no
tiene segunda oportunidad, y esta es justo la función que un profesor podría leer mal.

**El acta es un dato del estudiante.** Contiene observaciones sobre su desempeño escritas
por un tercero. Conviene que la política de tratamiento de datos lo contemple y que el
estudiante pueda descargarla y pedir su borrado como cualquier otro dato suyo — la misma
puerta que ya existe para el resto.

**Nada de esto antes de resolver el video.** Construir sobre clases que dependen de un
proveedor que prohíbe el uso comercial es construir el segundo piso sin el primero.
