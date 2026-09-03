-- Bloque 6 del brief maestro: que el buen comportamiento se premie con visibilidad en vez de
-- administrarse a punta de castigos. El brief la llamaba V15; el esquema ya iba por V17.

-- ---------------------------------------------------------------------------------------------
-- Métricas de desempeño
-- ---------------------------------------------------------------------------------------------
-- La tabla ya existía desde la V14 con el agregado de reseñas. Aquí crece con el resto de
-- indicadores y con el puntaje que ordena el buscador.
ALTER TABLE professor_metrics
    ADD COLUMN lessons_completed    INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN attendance_rate      NUMERIC(5,2),
    ADD COLUMN cancellation_rate    NUMERIC(5,2),
    ADD COLUMN reschedule_rate      NUMERIC(5,2),
    ADD COLUMN response_rate        NUMERIC(5,2),
    ADD COLUMN avg_response_minutes INTEGER,
    ADD COLUMN active_students      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN profile_completeness SMALLINT,
    ADD COLUMN ranking_score        NUMERIC(6,2),
    ADD COLUMN window_days          SMALLINT NOT NULL DEFAULT 90;

-- El buscador ordena por esta columna, no calcula nada: el ranking se recalcula de noche.
CREATE INDEX idx_metrics_ranking ON professor_metrics(ranking_score DESC NULLS LAST);

-- ---------------------------------------------------------------------------------------------
-- Pesos del ranking y umbrales, en la base para poder ajustarlos sin desplegar
-- ---------------------------------------------------------------------------------------------
-- En milésimas (300 = 0.30) para no meter decimales en una tabla de texto. La fórmula es una
-- hipótesis: revísala cuando haya 200 clases reales, no antes.
INSERT INTO platform_settings (key, value) VALUES
    ('ranking_weight_rating',        '300'),
    ('ranking_weight_attendance',    '250'),
    ('ranking_weight_response',      '150'),
    ('ranking_weight_lessons',       '150'),
    ('ranking_weight_completeness',  '100'),
    ('ranking_weight_retention',      '50'),
    -- Con menos de estas clases el profesor no tiene historial que juzgar: recibe un puntaje
    -- neutro. Sin esto, el profesor nuevo puntúa 0, nunca aparece, nunca recibe su primera
    -- reserva, y la oferta se estanca en los mismos de siempre.
    ('ranking_cold_start_lessons',     '5'),
    ('metrics_window_days',           '90'),
    ('sanction_penalty_points',       '15')
ON CONFLICT (key) DO NOTHING;
