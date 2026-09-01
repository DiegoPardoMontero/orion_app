-- Tokens de recuperación de contraseña.
--
-- Se guarda SOLO el hash del token (SHA-256, 64 hex), nunca el token en claro: si la base se
-- filtra, los enlaces no sirven. El token en claro vive únicamente en el correo del usuario.
-- Un token es de un solo uso (used_at) y caduca (expires_at). ON DELETE CASCADE: si se borra el
-- usuario, sus tokens se van con él.
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);
