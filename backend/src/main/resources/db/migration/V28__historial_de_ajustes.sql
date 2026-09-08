-- Bloque 9, paso 7 — Historial de cambios de los ajustes de plataforma.
--
-- `platform_settings` guarda quién cambió por última vez y cuándo, pero no qué había antes. Cuando
-- la comisión aparezca en 30 % un lunes, la pregunta va a ser «¿quién, cuándo y desde cuánto?», y
-- hoy solo se puede responder la primera mitad.

CREATE TABLE platform_setting_changes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key        VARCHAR(60) NOT NULL REFERENCES platform_settings (key),
    old_value  TEXT,
    new_value  TEXT NOT NULL,
    changed_by UUID NOT NULL REFERENCES users (id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_setting_changes ON platform_setting_changes (key, changed_at DESC);
