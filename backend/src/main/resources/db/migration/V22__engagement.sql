-- Gamificación: el libro de puntos, el catálogo de logros y los cosméticos.
--
-- Los puntos JAMÁS valen dinero. No hay descuento, ni canje, ni saldo: son puramente cosméticos.
-- No es una limitación de producto sino la defensa más barata contra el fraude — en un marketplace
-- un par estudiante–profesor coludido puede fabricar clases, y con puntos cosméticos el incentivo
-- a hacerlo es cero.

-- El libro de eventos: la única fuente de puntos, y solo se le añade.
CREATE TABLE point_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type VARCHAR(40) NOT NULL,
    source_id   UUID,
    points      INTEGER NOT NULL CHECK (points > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotencia: un hecho concede puntos una sola vez, para siempre. Es lo que hace que reprocesar
-- un evento reenviado, o recalcular desde cero, no duplique nada — sin un solo `if`.
CREATE UNIQUE INDEX uq_point_event_source
    ON point_events(source_type, source_id) WHERE source_id IS NOT NULL;
CREATE INDEX idx_point_events_user ON point_events(user_id, occurred_at DESC);

-- El catálogo de logros es DATO, no código: un logro nuevo del mismo tipo es un INSERT.
CREATE TABLE achievements (
    code            VARCHAR(60) PRIMARY KEY,
    family          VARCHAR(20) NOT NULL
                    CHECK (family IN ('PRIMEROS', 'CONSTANCIA', 'VOLUMEN', 'AMPLITUD', 'COMPROMISO')),
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(200) NOT NULL,
    criteria_type   VARCHAR(40)  NOT NULL,
    criteria_params JSONB        NOT NULL DEFAULT '{}',
    target          INTEGER      NOT NULL DEFAULT 1,
    glow            SMALLINT     NOT NULL CHECK (glow BETWEEN 1 AND 3),
    points          INTEGER      NOT NULL,
    display_order   SMALLINT     NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT true
);

CREATE TABLE user_achievements (
    user_id          UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_code VARCHAR(60) NOT NULL REFERENCES achievements(code),
    progress         INTEGER     NOT NULL DEFAULT 0,
    unlocked_at      TIMESTAMPTZ,
    PRIMARY KEY (user_id, achievement_code)
);

CREATE INDEX idx_user_achievements_unlocked
    ON user_achievements(user_id) WHERE unlocked_at IS NOT NULL;

-- Cosméticos: marcos (órbitas), paletas, cielos (fondos) y accesorios.
--
-- La clave es (kind, code) y no `code` a secas, porque el diseño reutiliza nombres entre familias:
-- `trazo` es marco y paleta, `noche` y `amanecer` son paleta y cielo. Con una PK simple habría que
-- inventar prefijos —`paleta-trazo`— y entonces el valor por defecto de `student_profiles`, que el
-- propio diseño fija en `trazo`, dejaría de casar. La clave compuesta respeta los dos.
CREATE TABLE cosmetics (
    kind               VARCHAR(20) NOT NULL
                       CHECK (kind IN ('FRAME', 'PALETTE', 'SKY', 'ACCESSORY')),
    code               VARCHAR(40) NOT NULL,
    name               VARCHAR(60) NOT NULL,
    zone               VARCHAR(10) CHECK (zone IN ('z1', 'z2', 'z3')),
    unlock_achievement VARCHAR(60) REFERENCES achievements(code),
    is_default         BOOLEAN NOT NULL DEFAULT false,
    display_order      SMALLINT NOT NULL DEFAULT 0,

    PRIMARY KEY (kind, code),

    -- Solo los accesorios tienen zona de anclaje, y todos la tienen.
    CHECK ((kind = 'ACCESSORY') = (zone IS NOT NULL)),
    -- Todo cosmético o es inicial o tiene forma de conseguirse: nada queda inalcanzable por olvido.
    CHECK (is_default OR unlock_achievement IS NOT NULL)
);

-- Racha protegida: una semana al mes que no corta la racha.
CREATE TABLE streak_protections (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start  DATE NOT NULL,
    granted_for DATE NOT NULL,          -- primer día del mes al que pertenece la protección
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, week_start)
);

-- Una protección por mes. Lo garantiza la constraint, no un `if`.
CREATE UNIQUE INDEX uq_protection_month ON streak_protections(user_id, granted_for);

-- ---------------------------------------------------------------------------
-- Los 20 logros del diseño. Los textos de `description` son los del inventario del
-- entregable: están redactados con la voz de marca y ya fueron aprobados.
-- ---------------------------------------------------------------------------

INSERT INTO achievements
    (code, family, name, description, criteria_type, criteria_params, target, glow, points, display_order)
VALUES
    ('primeros-primera-reserva', 'PRIMEROS', 'Primera reserva',
     'Reservó su primera clase.', 'EVENT_ONCE', '{"event":"booking_created"}', 1, 1, 10, 1),
    ('primeros-primera-clase', 'PRIMEROS', 'Primera clase',
     'Tomó su primera clase en vivo.', 'LESSON_COUNT', '{}', 1, 1, 25, 2),
    ('primeros-perfil-listo', 'PRIMEROS', 'Perfil listo',
     'Foto, objetivo e idioma completos.', 'PROFILE_COMPLETE', '{}', 3, 1, 15, 3),
    ('primeros-primer-mensaje', 'PRIMEROS', 'Primer mensaje',
     'Escribió a un profesor antes de la clase.', 'EVENT_ONCE', '{"event":"message_sent"}', 1, 1, 10, 4),

    ('constancia-2-semanas', 'CONSTANCIA', 'Dos semanas seguidas',
     '2 semanas consecutivas con clase.', 'STREAK_WEEKS', '{}', 2, 1, 20, 5),
    ('constancia-4-semanas', 'CONSTANCIA', 'Un mes seguido',
     '4 semanas consecutivas.', 'STREAK_WEEKS', '{}', 4, 1, 40, 6),
    ('constancia-8-semanas', 'CONSTANCIA', 'Dos meses seguidos',
     '8 semanas consecutivas. Sube el sello a nivel 2.', 'STREAK_WEEKS', '{}', 8, 2, 80, 7),
    ('constancia-12-semanas', 'CONSTANCIA', 'Un trimestre seguido',
     '12 semanas consecutivas.', 'STREAK_WEEKS', '{}', 12, 2, 120, 8),
    ('constancia-24-semanas', 'CONSTANCIA', 'Medio año seguido',
     '24 semanas consecutivas. Sube el sello a nivel 3.', 'STREAK_WEEKS', '{}', 24, 3, 250, 9),

    ('volumen-5-clases', 'VOLUMEN', 'Cinco clases',
     '5 clases tomadas.', 'LESSON_COUNT', '{}', 5, 1, 25, 10),
    ('volumen-10-clases', 'VOLUMEN', 'Diez clases',
     '10 clases tomadas.', 'LESSON_COUNT', '{}', 10, 1, 50, 11),
    ('volumen-25-clases', 'VOLUMEN', 'Veinticinco clases',
     '25 clases tomadas.', 'LESSON_COUNT', '{}', 25, 2, 100, 12),
    ('volumen-50-clases', 'VOLUMEN', 'Cincuenta clases',
     '50 clases tomadas.', 'LESSON_COUNT', '{}', 50, 2, 200, 13),
    ('volumen-100-clases', 'VOLUMEN', 'Cien clases',
     '100 clases tomadas.', 'LESSON_COUNT', '{}', 100, 3, 400, 14),

    ('amplitud-tres-voces', 'AMPLITUD', 'Tres voces',
     'Clases con 3 profesores distintos.', 'DISTINCT_PROFESSORS', '{}', 3, 1, 40, 15),
    ('amplitud-dos-idiomas', 'AMPLITUD', 'Dos idiomas',
     'Al menos una clase en un segundo idioma.', 'DISTINCT_LANGUAGES', '{}', 2, 1, 60, 16),
    ('amplitud-presencial', 'AMPLITUD', 'Cara a cara',
     'Tomó una clase presencial.', 'MODALITY_TAKEN', '{"modality":"IN_PERSON"}', 1, 1, 30, 17),

    ('compromiso-primera-resena', 'COMPROMISO', 'Tu primera reseña',
     'Escribió una reseña a un profesor.', 'EVENT_ONCE', '{"event":"review_written"}', 1, 1, 20, 18),
    ('compromiso-objetivo', 'COMPROMISO', 'Objetivo declarado',
     'Definió para qué está aprendiendo.', 'EVENT_ONCE', '{"event":"goal_declared"}', 1, 1, 15, 19),
    ('compromiso-mes-sin-cancelar', 'COMPROMISO', 'Un mes sin cancelaciones',
     '30 días con todas las clases cumplidas.', 'NO_CANCELLATIONS_DAYS', '{}', 30, 1, 50, 20);

-- ---------------------------------------------------------------------------
-- Cosméticos (§2e del entregable).
--
-- El diseño da a `cielo` (marco) la condición «medio año seguido O cien clases». El modelo admite
-- un desbloqueo por cosmético, así que se usa 24-semanas y la alternativa queda anotada como deuda:
-- si algún día hace falta, `unlock_achievement` se convierte en tabla puente. No se complica el
-- modelo hoy por un caso.
-- ---------------------------------------------------------------------------

INSERT INTO cosmetics (code, kind, name, zone, unlock_achievement, is_default, display_order) VALUES
    ('trazo',              'FRAME', 'Trazo',              NULL, NULL,                     true,  1),
    ('orbita',             'FRAME', 'Órbita',             NULL, 'primeros-primera-clase', false, 2),
    ('orbita-doble',       'FRAME', 'Órbita doble',       NULL, 'constancia-2-semanas',   false, 3),
    ('constelacion-iii',   'FRAME', 'Constelación III',   NULL, 'amplitud-tres-voces',    false, 4),
    ('constelacion-v',     'FRAME', 'Constelación V',     NULL, 'constancia-4-semanas',   false, 5),
    ('halo',               'FRAME', 'Halo',               NULL, 'volumen-10-clases',      false, 6),
    ('orbita-amanecer',    'FRAME', 'Órbita amanecer',    NULL, 'constancia-12-semanas',  false, 7),
    ('cielo',              'FRAME', 'Cielo',              NULL, 'constancia-24-semanas',  false, 8),

    ('trazo',              'PALETTE', 'Trazo',            NULL, NULL,                     true,  1),
    ('durazno',            'PALETTE', 'Durazno',          NULL, 'primeros-primera-clase', false, 2),
    ('lavanda',            'PALETTE', 'Lavanda',          NULL, 'amplitud-dos-idiomas',   false, 3),
    ('ciruela',            'PALETTE', 'Ciruela',          NULL, 'volumen-25-clases',      false, 4),
    ('noche',              'PALETTE', 'Noche',            NULL, 'constancia-12-semanas',  false, 5),
    ('amanecer',           'PALETTE', 'Amanecer',         NULL, 'constancia-24-semanas',  false, 6),

    ('crema',              'SKY', 'Crema',                NULL, NULL,                     true,  1),
    ('bruma',              'SKY', 'Bruma',                NULL, 'primeros-perfil-listo',  false, 2),
    ('alba',               'SKY', 'Alba',                 NULL, 'volumen-5-clases',       false, 3),
    ('constelacion',       'SKY', 'Constelación',         NULL, 'constancia-8-semanas',   false, 4),
    ('noche',              'SKY', 'Noche',                NULL, 'volumen-50-clases',      false, 5),
    ('amanecer',           'SKY', 'Amanecer',             NULL, 'constancia-24-semanas',  false, 6),

    ('base-orbita',        'ACCESSORY', 'Base órbita',        'z1', 'volumen-10-clases',     false, 1),
    ('centro-monograma',   'ACCESSORY', 'Centro monograma',   'z2', 'primeros-perfil-listo', false, 2),
    ('corona-constelacion','ACCESSORY', 'Corona constelación','z3', 'constancia-24-semanas', false, 3);

-- Las clases gratuitas no cuentan para la gamificación: las fija un administrador para probar el
-- flujo en producción, y las pruebas no deben contaminar el perfil de nadie. Es un ajuste para
-- poder cambiar de opinión sin desplegar si el piloto las usa con estudiantes reales.
INSERT INTO platform_settings (key, value) VALUES
    ('gamification_count_free_lessons', 'false')
ON CONFLICT (key) DO NOTHING;
