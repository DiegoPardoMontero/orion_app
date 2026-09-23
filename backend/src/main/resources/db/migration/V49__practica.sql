-- La práctica entre clases (Bloque 10, Parte B). Pardo decidió desplegarla encendida y que
-- practicar también cuente para la racha semanal (22/09/2026).

CREATE TABLE practice_sets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- Un acta, un set, para siempre: republicar un acta editada no crea otro.
    lesson_note_id    UUID UNIQUE NOT NULL REFERENCES lesson_notes(id) ON DELETE CASCADE,
    professor_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    language_code     VARCHAR(5) REFERENCES languages(code),

    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'READY', 'IN_PROGRESS', 'COMPLETED', 'EXPIRED', 'FAILED')),
    -- Lo que el acta decía al publicarse: el vocabulario, los errores recurrentes y lo trabajado.
    -- La generación es diferida y no puede depender de leer el acta después: el acta es de otro
    -- módulo, y además se puede corregir, y el set se hizo con lo que decía ese día.
    material          JSONB NOT NULL,
    estimated_minutes SMALLINT,
    item_count        SMALLINT NOT NULL DEFAULT 0,
    correct_count     SMALLINT NOT NULL DEFAULT 0,
    generation_attempts SMALLINT NOT NULL DEFAULT 0,

    expires_at        TIMESTAMPTZ NOT NULL,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

CREATE INDEX idx_practice_student ON practice_sets(student_id, created_at DESC);
CREATE INDEX idx_practice_active  ON practice_sets(student_id) WHERE status IN ('READY', 'IN_PROGRESS');
CREATE INDEX idx_practice_pending ON practice_sets(created_at) WHERE status = 'PENDING';

CREATE TABLE practice_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    practice_set_id UUID NOT NULL REFERENCES practice_sets(id) ON DELETE CASCADE,
    item_index      SMALLINT NOT NULL,
    item_type       VARCHAR(30) NOT NULL
                    CHECK (item_type IN ('FILL_BLANK', 'FIX_SENTENCE', 'MATCH_MEANING',
                                         'ORDER_DIALOGUE', 'WRITE_SENTENCE')),
    prompt          VARCHAR(600) NOT NULL,
    payload         JSONB        NOT NULL,   -- opciones, piezas, pares
    expected        VARCHAR(600),
    explanation     VARCHAR(400) NOT NULL,
    source_term     VARCHAR(120),            -- término de vocabulario del que salió

    answer          VARCHAR(600),
    is_correct      BOOLEAN,
    attempts        SMALLINT NOT NULL DEFAULT 0,
    answered_at     TIMESTAMPTZ,

    UNIQUE (practice_set_id, item_index)
);

INSERT INTO platform_settings (key, value) VALUES
    ('practice_enabled',          'true'),
    ('practice_set_ttl_days',     '7'),
    ('practice_items_per_set',    '4'),
    ('practice_max_attempts',     '2'),
    -- Uno por función: la práctica tiene su propio tope de gasto de IA, aparte del acta.
    ('practice_daily_budget_cop', '20000');
