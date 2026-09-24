-- La práctica, gamificada (24/09/2026, pedido de Pardo): una familia de logros propia en «Mi cielo»
-- y un bono de 5 puntos por constelación perfecta (todo el set al primer intento).
ALTER TABLE achievements DROP CONSTRAINT achievements_family_check;
ALTER TABLE achievements ADD CONSTRAINT achievements_family_check
    CHECK (family IN ('PRIMEROS', 'CONSTANCIA', 'VOLUMEN', 'AMPLITUD', 'COMPROMISO', 'PRACTICA'));

-- Lo que engagement necesita saber de cada práctica terminada, guardado por él mismo: la práctica se
-- lo cuenta en su evento, y engagement no lee las tablas de otro módulo (por eso se puede borrar
-- entero). Una fila por set: el evento que llega dos veces no cuenta dos veces.
CREATE TABLE practice_tallies (
    practice_set_id    UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    perfect            BOOLEAN NOT NULL,
    listening_correct  SMALLINT NOT NULL DEFAULT 0,
    second_try_correct SMALLINT NOT NULL DEFAULT 0,
    completed_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_practice_tallies_user ON practice_tallies(user_id);

INSERT INTO achievements
    (code, family, name, description, criteria_type, criteria_params, target, glow, points, display_order)
VALUES
    ('practica-primera', 'PRACTICA', 'Primera constelación',
     'Completó su primera práctica.', 'PRACTICE_COUNT', '{}', 1, 1, 10, 21),
    ('practica-perfecta', 'PRACTICA', 'Constelación perfecta',
     'Una práctica entera al primer intento.', 'PRACTICE_PERFECT', '{}', 1, 1, 20, 22),
    ('practica-5-constelaciones', 'PRACTICA', 'Cinco constelaciones',
     '5 prácticas completadas.', 'PRACTICE_COUNT', '{}', 5, 1, 25, 23),
    ('practica-20-constelaciones', 'PRACTICA', 'Veinte constelaciones',
     '20 prácticas completadas.', 'PRACTICE_COUNT', '{}', 20, 2, 60, 24),
    ('practica-oido-fino', 'PRACTICA', 'Oído fino',
     '10 ejercicios de escucha acertados.', 'LISTENING_CORRECT', '{}', 10, 1, 20, 25),
    ('practica-segunda-oportunidad', 'PRACTICA', 'Segunda oportunidad',
     '10 ejercicios resueltos al segundo intento.', 'SECOND_TRY_CORRECT', '{}', 10, 1, 15, 26);
