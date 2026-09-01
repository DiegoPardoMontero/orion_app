-- Invitaciones de profesores. El admin invita por correo; el profesor nace INACTIVE y se activa
-- al aceptar. Mismo patrón que password_reset_tokens: se guarda el HASH del token (el secreto solo
-- viaja en el correo), de un solo uso y con caducidad (7 días). ON DELETE CASCADE con el usuario.
CREATE TABLE professor_invites (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_professor_invites_user ON professor_invites (user_id);
