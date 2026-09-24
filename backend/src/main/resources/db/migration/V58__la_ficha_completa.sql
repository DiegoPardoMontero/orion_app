-- La ficha del estudiante, con énfasis (decisión de Pardo, 24/09/2026): un logro por completarla y
-- recordatorios hasta que lo haga. Completa = foto, nivel, idioma, para qué lo aprende y motivación.

INSERT INTO achievements
    (code, family, name, description, criteria_type, criteria_params, target, glow, points, display_order)
VALUES
    ('compromiso-ficha-completa', 'COMPROMISO', 'Ficha completa',
     'Completó su ficha: foto, nivel, idioma, objetivo y motivación.', 'EVENT_ONCE',
     '{"event":"ficha_completa"}', 1, 1, 25, 19);

-- Los recordatorios que ya salieron, uno por paso: día 1 en la app, día 2 por correo, día 3 en la
-- app otra vez. La llave primaria es la que impide repetir uno.
CREATE TABLE profile_reminders (
    user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    step     SMALLINT    NOT NULL CHECK (step BETWEEN 1 AND 3),
    sent_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, step)
);
