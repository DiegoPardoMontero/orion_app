-- Bloque 1 del brief maestro: idiomas, objetivos, precio del profesor y ajustes de plataforma.
-- Convierte a Orión de "academia de inglés" en marketplace multi-idioma.

-- Catálogo de idiomas: tabla, no enum, para que agregar alemán sea un INSERT sin rediseño.
CREATE TABLE languages (
    code          VARCHAR(5)  PRIMARY KEY,
    name_es       VARCHAR(60) NOT NULL,
    name_en       VARCHAR(60) NOT NULL,
    flag_emoji    VARCHAR(8),
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    display_order SMALLINT    NOT NULL DEFAULT 0
);

INSERT INTO languages (code, name_es, name_en, flag_emoji, display_order) VALUES
    ('EN', 'Inglés',  'English', '🇬🇧', 1),
    ('FR', 'Francés', 'French',  '🇫🇷', 2),
    ('ES', 'Español', 'Spanish', '🇪🇸', 3);

-- Objetivos de aprendizaje (los del documento, sección FILTROS).
CREATE TABLE teaching_goals (
    code          VARCHAR(30) PRIMARY KEY,
    name_es       VARCHAR(60) NOT NULL,
    name_en       VARCHAR(60) NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    display_order SMALLINT    NOT NULL DEFAULT 0
);

INSERT INTO teaching_goals (code, name_es, name_en, display_order) VALUES
    ('CONVERSATION', 'Conversación',        'Conversation',     1),
    ('TRAVEL',       'Viajes',              'Travel',           2),
    ('BUSINESS',     'Negocios',            'Business',         3),
    ('ACADEMIC',     'Académico',           'Academic',         4),
    ('EXAMS',        'Exámenes',            'Exams',            5),
    ('INTERVIEW',    'Entrevistas',         'Interview',        6),
    ('GENERAL',      'Aprendizaje general', 'General learning', 7);

-- Qué idiomas enseña cada profesor.
CREATE TABLE professor_languages (
    professor_id  UUID       NOT NULL REFERENCES professor_profiles(user_id) ON DELETE CASCADE,
    language_code VARCHAR(5) NOT NULL REFERENCES languages(code),
    is_native     BOOLEAN    NOT NULL DEFAULT false,
    PRIMARY KEY (professor_id, language_code)
);

-- A qué niveles, por idioma.
CREATE TABLE professor_language_levels (
    professor_id  UUID        NOT NULL,
    language_code VARCHAR(5)  NOT NULL,
    level         VARCHAR(20) NOT NULL
                  CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    PRIMARY KEY (professor_id, language_code, level),
    FOREIGN KEY (professor_id, language_code)
        REFERENCES professor_languages(professor_id, language_code) ON DELETE CASCADE
);

-- Para qué objetivos.
CREATE TABLE professor_goals (
    professor_id UUID        NOT NULL REFERENCES professor_profiles(user_id) ON DELETE CASCADE,
    goal_code    VARCHAR(30) NOT NULL REFERENCES teaching_goals(code),
    PRIMARY KEY (professor_id, goal_code)
);

-- Perfil enriquecido y precio. compensation_model modela Q1 (comisión) sin borrar el pasado:
-- el default FIXED_FEE conserva a quien ya existe; la migración de datos (V11) mueve a COMMISSION.
ALTER TABLE professor_profiles
    ADD COLUMN hourly_rate_cop     BIGINT,
    ADD COLUMN compensation_model  VARCHAR(20) NOT NULL DEFAULT 'FIXED_FEE'
                                   CHECK (compensation_model IN ('FIXED_FEE', 'COMMISSION')),
    ADD COLUMN country_code        VARCHAR(2),
    ADD COLUMN city                VARCHAR(80),
    ADD COLUMN native_language     VARCHAR(5) REFERENCES languages(code),
    ADD COLUMN years_experience    SMALLINT,
    ADD COLUMN education           VARCHAR(300),
    ADD COLUMN is_certified        BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN accepts_trial       BOOLEAN NOT NULL DEFAULT true,
    ADD CONSTRAINT chk_hourly_rate
        CHECK (hourly_rate_cop IS NULL
               OR (hourly_rate_cop >= 20000 AND hourly_rate_cop <= 500000));

CREATE INDEX idx_prof_lang ON professor_languages(language_code);
CREATE INDEX idx_prof_rate ON professor_profiles(hourly_rate_cop) WHERE is_published;

-- Ajustes de plataforma: todo umbral de negocio vive aquí, no en el código.
CREATE TABLE platform_settings (
    key        VARCHAR(60) PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES users(id)
);

-- Valores según las decisiones acordadas con Pardo (01/09/2026):
--   Q3 = cancelación 12 h para ambos actores (no la asimétrica del brief).
--   Q6 = comisión 20 % (2000 bps).
INSERT INTO platform_settings (key, value) VALUES
    ('commission_rate_bps',            '2000'),
    ('student_cancel_hours',           '12'),
    ('professor_cancel_hours',         '12'),
    ('no_show_report_minutes',         '15'),
    ('payment_hold_minutes',           '20'),
    ('auto_complete_hours',            '24'),
    ('require_phone_verification',     'false'),
    ('contact_policy_mode',            'MASK');
