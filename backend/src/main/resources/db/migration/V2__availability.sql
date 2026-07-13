CREATE TABLE availability_rules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    weekday      SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_time   TIME NOT NULL,
    end_time     TIME NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (start_time < end_time)
);

CREATE INDEX idx_availability_rules_professor ON availability_rules(professor_id);

CREATE TABLE availability_exceptions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exception_date DATE NOT NULL,
    start_time     TIME,
    end_time       TIME,
    reason         VARCHAR(200),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time)
    )
);

CREATE INDEX idx_availability_exceptions_prof_date
    ON availability_exceptions(professor_id, exception_date);
