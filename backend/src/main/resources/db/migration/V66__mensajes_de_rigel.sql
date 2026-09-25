-- Mensajes de Rigel (24/09/2026): mensajes oficiales de Orión en «Mensajes», a estudiantes y
-- profesores —la bienvenida, cómo funciona, y los primeros pasos con botones que llevan a cada
-- cosa—. «No quiero que sea excesivamente invasivo»: pocos, y cada uno una sola vez.
--
-- Se guarda el tipo y sus datos, no el texto: el texto y los botones los escribe el código, así
-- que corregir una frase no es una migración.
CREATE TABLE rigel_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       VARCHAR(40) NOT NULL CHECK (kind IN (
                   'WELCOME_STUDENT', 'WELCOME_PROFESSOR',
                   'FIRST_BOOKING_STUDENT', 'FIRST_BOOKING_PROFESSOR',
                   'FIRST_CLASS_STUDENT', 'FIRST_CLASS_PROFESSOR',
                   'COME_BACK')),
    params     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);

-- Una vez cada uno, lo decide la base. «¿Seguimos?» es el único que se puede repetir (a lo sumo uno
-- al mes, lo controla el proceso que lo manda).
CREATE UNIQUE INDEX ux_rigel_una_vez ON rigel_messages (user_id, kind) WHERE kind <> 'COME_BACK';
CREATE INDEX idx_rigel_por_usuario ON rigel_messages (user_id, created_at);
