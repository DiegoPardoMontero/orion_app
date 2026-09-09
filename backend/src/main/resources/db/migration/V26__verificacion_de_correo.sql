-- Bloque 9, paso 3 — Verificación de correo.
--
-- Hoy cualquiera se registra con el correo de otro y recibe sus confirmaciones y sus invitaciones
-- de calendario. Mismo patrón que password_reset_tokens (V5): en la base vive el HASH del token,
-- nunca el token; es de un solo uso y caduca.

CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    -- El correo al que se envió. Si la persona cambia de correo antes de verificar, el token
    -- viejo deja de valer: verificaría una dirección que ya nadie usa.
    email       VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id);

ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Las cuentas anteriores se dan por verificadas. Llevan tiempo recibiendo correo del sistema —si
-- la dirección no existiera, ya lo sabríamos— y bloquearlas retroactivamente castigaría a quien ya
-- usa Orión por una regla que se estrena hoy.
UPDATE users SET email_verified_at = created_at;
