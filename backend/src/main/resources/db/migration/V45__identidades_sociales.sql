-- Entrar con Google, Apple o Facebook (Pardo, 22/09/2026). Una cuenta puede tener varias
-- identidades de proveedor y además su contraseña; lo que no puede pasar es que una misma identidad
-- abra dos cuentas, y de eso se encarga el UNIQUE.
--
-- Se guarda el `sub` del proveedor, que es el identificador estable. El correo se guarda como
-- constancia de con cuál se vinculó, no como llave: el correo de una cuenta de Google puede
-- cambiar, y el de Apple puede ser un relevo privado.

CREATE TABLE social_identities (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider   VARCHAR(20)  NOT NULL CHECK (provider IN ('GOOGLE', 'FACEBOOK', 'APPLE')),
    subject    VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, subject)
);

CREATE INDEX idx_social_identities_user ON social_identities(user_id);
