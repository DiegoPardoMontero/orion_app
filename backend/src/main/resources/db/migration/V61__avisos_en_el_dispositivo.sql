-- Avisos en el dispositivo (Web Push, 24/09/2026). Una fila por navegador suscrito: la misma
-- persona puede tener el celular y el portátil. El endpoint es único porque lo emite el servicio de
-- push para ese navegador; suscribirse otra vez desde el mismo navegador actualiza la fila.
--
-- Cuando el servicio responde 404/410 la suscripción murió (desinstaló, revocó el permiso) y se borra.
CREATE TABLE push_subscriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint     VARCHAR(1000) NOT NULL UNIQUE,
    p256dh       VARCHAR(200)  NOT NULL,
    auth         VARCHAR(100)  NOT NULL,
    user_agent   VARCHAR(300),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_sent_at TIMESTAMPTZ
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions(user_id);
