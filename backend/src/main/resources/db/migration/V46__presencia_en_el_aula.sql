-- Lo que pasa dentro de la sala, contado por 8x8 y no por el navegador (webhook de JaaS,
-- 22/09/2026). Quién entró y cuándo, quién está dentro ahora y cuánto habló cada uno.
--
-- Una fila por persona y clase. La primera entrada no se pisa nunca: es la que dice si el profesor
-- llegó tarde (dato informativo, decisión de Pardo: no alimenta sanciones). «Dentro» se decide por
-- el evento más reciente según la hora de 8x8, no por el orden de llegada: los webhooks pueden
-- llegar desordenados.

CREATE TABLE room_participations (
    booking_id      UUID        NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    first_joined_at TIMESTAMPTZ,
    last_event_at   TIMESTAMPTZ NOT NULL,
    inside          BOOLEAN     NOT NULL DEFAULT false,
    speaking_ms     INTEGER     CHECK (speaking_ms IS NULL OR speaking_ms >= 0),
    PRIMARY KEY (booking_id, user_id)
);

-- Un evento reenviado se procesa una vez: la llave de idempotencia de 8x8 es el árbitro, igual que
-- el provider_event_id de Wompi.
CREATE TABLE video_webhook_events (
    idempotency_key VARCHAR(80) PRIMARY KEY,
    event_type      VARCHAR(40) NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
