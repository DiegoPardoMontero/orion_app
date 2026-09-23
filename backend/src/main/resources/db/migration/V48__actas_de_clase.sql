-- Bloque 10, Parte A: el acta de clase. El profesor escribe tres líneas al terminar; la IA las
-- ordena en cuatro secciones; el profesor revisa y publica; el estudiante la lee.
-- Brief: docs/briefs/orion-bloque-10-acta-y-practica.md. Decisiones de Pardo (22/09/2026): solo la
-- Parte A, IA de OpenAI, tope de gasto propio y los valores por defecto D1–D8.
--
-- ai_usage_log ya existe (V34, diagnóstico): las actas escriben ahí con feature = 'lesson_note'.

CREATE TABLE lesson_notes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id       UUID UNIQUE NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    professor_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    student_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    language_code    VARCHAR(5) REFERENCES languages(code),

    raw_input        VARCHAR(2000) NOT NULL,

    worked_on        VARCHAR(1200),
    recurring_issues VARCHAR(1200),
    next_steps       VARCHAR(1200),

    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                     CHECK (status IN ('DRAFT', 'PUBLISHED')),
    origin           VARCHAR(20) NOT NULL DEFAULT 'AI_DRAFT'
                     CHECK (origin IN ('AI_DRAFT', 'MANUAL')),
    -- Lo que propuso la IA, tal cual, para medir al publicar cuánto lo cambió el profesor. El brief
    -- pide el edit_ratio pero no dónde guardar el original; sin él no hay contra qué compararlo.
    original_draft   TEXT,
    edit_ratio       NUMERIC(4,3) CHECK (edit_ratio IS NULL OR edit_ratio BETWEEN 0 AND 1),
    prompt_version   VARCHAR(20),

    published_at     TIMESTAMPTZ,
    last_edited_at   TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- No existe acta publicada sin fecha ni fecha de publicación en un borrador.
    CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

CREATE INDEX idx_notes_student ON lesson_notes(student_id, published_at DESC)
    WHERE status = 'PUBLISHED';
CREATE INDEX idx_notes_professor ON lesson_notes(professor_id, created_at DESC);

-- En tabla propia: es lo que consumirá la práctica (Parte B) y lo que medirá el crecimiento de
-- vocabulario.
CREATE TABLE lesson_vocabulary (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_note_id UUID NOT NULL REFERENCES lesson_notes(id) ON DELETE CASCADE,
    student_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    language_code  VARCHAR(5) REFERENCES languages(code),
    term           VARCHAR(120) NOT NULL,
    meaning        VARCHAR(300),
    display_order  SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_vocab_note    ON lesson_vocabulary(lesson_note_id);
CREATE INDEX idx_vocab_student ON lesson_vocabulary(student_id, language_code);

-- El recordatorio sale una vez por clase y nunca insiste.
ALTER TABLE bookings ADD COLUMN note_nudge_sent_at TIMESTAMPTZ;

INSERT INTO platform_settings (key, value) VALUES
    ('ai_lesson_notes_enabled',       'true'),
    ('ai_daily_budget_cop',           '30000'),
    ('ai_note_timeout_seconds',       '25'),
    ('lesson_note_edit_window_hours', '72'),
    ('lesson_note_nudge_minutes',     '60');

-- D4: sin actas retroactivas. No hace falta tocar las reservas viejas: el servicio solo admite actas
-- (y el recordatorio solo avisa) de clases cerradas después de que esta migración se aplicó, fecha
-- que Flyway ya guarda en flyway_schema_history.
