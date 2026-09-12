-- Bloque 9 — Diagnóstico de confianza.
--
-- Siete minutos de conversación por voz que terminan en un Confidence Score, un diagnóstico escrito
-- en lenguaje humano y tres profesores con agenda. No es un examen de nivel: mide los marcadores de
-- la confianza al hablar —cuánto tarda en arrancar, cuántas frases abandona, cuándo se vuelve al
-- español— que es lo que la marca reivindica y lo que nadie más mide.

CREATE TABLE confidence_assessments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    language_code   VARCHAR(5) NOT NULL REFERENCES languages(code),
    sequence        SMALLINT NOT NULL DEFAULT 1,

    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
                    CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED', 'FAILED')),
    mode            VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
                    CHECK (mode IN ('STANDARD', 'FROM_ZERO')),

    score           SMALLINT CHECK (score BETWEEN 0 AND 100),
    score_version   VARCHAR(10),
    signals         JSONB,
    summary         VARCHAR(600),
    observations    JSONB,

    duration_seconds INTEGER,
    turn_count       SMALLINT,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ,

    -- La base garantiza la coherencia: no existe una evaluación completada sin fecha ni sin
    -- puntaje. Misma doctrina de siempre — la regla dura vive aquí, el servicio da el 422 amable.
    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    CHECK (status <> 'COMPLETED' OR score IS NOT NULL)
);

CREATE INDEX idx_assessment_user ON confidence_assessments(user_id, started_at DESC);

-- Una sola evaluación viva por persona e idioma
CREATE UNIQUE INDEX uq_assessment_active
    ON confidence_assessments(user_id, language_code)
    WHERE status = 'IN_PROGRESS';

CREATE TABLE assessment_turns (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id     UUID NOT NULL REFERENCES confidence_assessments(id) ON DELETE CASCADE,
    turn_index        SMALLINT NOT NULL,
    speaker           VARCHAR(10) NOT NULL CHECK (speaker IN ('AI', 'USER')),
    transcript        VARCHAR(2000),

    -- Señales del turno del usuario. Latencia y duración las mide el cliente, que es el único que
    -- ve el audio; las demás las deduce el servidor de la transcripción, porque son análisis del
    -- texto y no medición — y porque un puntaje que depende de lo que diga el cliente no es
    -- reproducible.
    latency_ms        INTEGER,
    duration_ms       INTEGER,
    word_count        SMALLINT,
    self_corrections  SMALLINT,
    abandoned_clauses SMALLINT,
    filler_count      SMALLINT,
    native_switch     BOOLEAN NOT NULL DEFAULT false,

    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (assessment_id, turn_index)
);

CREATE TABLE assessment_recommendations (
    assessment_id UUID NOT NULL REFERENCES confidence_assessments(id) ON DELETE CASCADE,
    professor_id  UUID NOT NULL REFERENCES users(id),
    position      SMALLINT NOT NULL CHECK (position BETWEEN 1 AND 3),
    reason_code   VARCHAR(40) NOT NULL,
    reason_text   VARCHAR(240) NOT NULL,
    -- La métrica que dice si esta función sirve al negocio: qué diagnósticos terminan en reserva,
    -- y con cuál de las tres posiciones.
    booked_at     TIMESTAMPTZ,
    PRIMARY KEY (assessment_id, professor_id)
);

-- Consentimiento específico para el procesamiento de voz. Separado de los términos generales
-- porque la autorización para tratar datos sensibles tiene que ser previa, expresa y ESPECÍFICA.
CREATE TABLE voice_consents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    version     VARCHAR(20) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at  TIMESTAMPTZ,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(300)
);

CREATE INDEX idx_voice_consent ON voice_consents(user_id, accepted_at DESC);

-- Consumo y costo de IA. Sin esto no hay forma de saber cuánto cuesta la función, y una función de
-- costo desconocido no se puede sostener ni apagar a tiempo.
CREATE TABLE ai_usage_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature       VARCHAR(40) NOT NULL,
    actor_id      UUID REFERENCES users(id),
    provider      VARCHAR(40) NOT NULL,
    model         VARCHAR(80),
    voice_seconds INTEGER,
    input_tokens  INTEGER,
    output_tokens INTEGER,
    cost_cop      BIGINT,
    latency_ms    INTEGER,
    outcome       VARCHAR(20) NOT NULL
                  CHECK (outcome IN ('OK', 'REFUSED', 'INVALID_OUTPUT', 'ERROR', 'TIMEOUT')),
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_usage ON ai_usage_log(feature, occurred_at DESC);

-- `assessment_enabled` nace en 'true' por decisión de Pardo (11/09/2026), sabiendo que el texto de
-- consentimiento de voz todavía NO lo ha revisado un abogado. Queda escrito aquí porque es el tipo
-- de decisión que hay que poder encontrar después, y porque el interruptor está en la pantalla de
-- Ajustes: apagarlo es un cambio de dato, no un despliegue.
INSERT INTO platform_settings (key, value) VALUES
    ('assessment_enabled',              'true'),
    ('assessment_max_minutes',          '7'),
    ('assessment_min_minutes',          '3'),
    ('assessment_cooldown_days',        '90'),
    ('assessment_daily_budget_cop',     '80000'),
    ('assessment_transcript_retention_days', '365');
