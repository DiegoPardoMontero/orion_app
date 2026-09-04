-- La ficha del estudiante.
--
-- Hasta aquí un estudiante era una fila en `users` y nada más: todo lo que sabíamos de él se
-- deducía de sus reservas. Esta tabla es donde vive lo que él declara.

CREATE TABLE student_profiles (
    user_id             UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,

    -- Mismo vocabulario que professor_language_levels a propósito: dos escalas de nivel en el
    -- mismo producto es una fuente de confusión permanente, y compartirla deja el emparejamiento
    -- estudiante–profesor resuelto sin trabajo extra.
    self_declared_level VARCHAR(20)
                        CHECK (self_declared_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    primary_language    VARCHAR(5) REFERENCES languages(code),
    motivation          VARCHAR(280),

    -- Nace en false: el consentimiento es explícito y reversible.
    is_public           BOOLEAN NOT NULL DEFAULT false,

    -- Solo se pide cuando alguien intenta activar el perfil público; quien nunca lo active no
    -- tiene que dar su fecha de nacimiento.
    birth_date          DATE,

    -- Selección cosmética actual. Los valores por defecto son los cosméticos iniciales, así que
    -- un perfil recién creado ya tiene un avatar válido y el código nunca ve un null.
    frame_code          VARCHAR(40) NOT NULL DEFAULT 'trazo',
    palette_code        VARCHAR(40) NOT NULL DEFAULT 'trazo',
    sky_code            VARCHAR(40) NOT NULL DEFAULT 'crema',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Los objetivos del estudiante reutilizan el catálogo del profesor. Que los dos lados hablen el
-- mismo vocabulario es lo que abre la puerta a recomendar por objetivo sin trabajo extra.
CREATE TABLE student_goals (
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_code  VARCHAR(30) NOT NULL REFERENCES teaching_goals(code),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, goal_code)
);

-- Accesorios equipados: uno por zona de anclaje, y la PK compuesta lo garantiza.
CREATE TABLE student_accessories (
    user_id        UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    zone           VARCHAR(10) NOT NULL CHECK (zone IN ('z1', 'z2', 'z3')),
    accessory_code VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, zone)
);

-- Ficha para cada estudiante que ya existe. A partir de aquí nunca hay un estudiante sin ficha,
-- y el código no tiene que manejar el caso nulo.
INSERT INTO student_profiles (user_id)
SELECT id FROM users WHERE role = 'STUDENT'
ON CONFLICT DO NOTHING;
