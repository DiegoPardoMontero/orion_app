-- Bloque 9, paso 6 — Tickets de soporte.
--
-- El art. 50 de la Ley 1480 de 2011 exige un mecanismo, en el mismo medio donde se contrata, que
-- deje CONSTANCIA de la fecha y hora de la petición y permita SEGUIMIENTO. Un número de WhatsApp
-- no cumple eso: no deja constancia y no se puede auditar. Un ticket sí. Van los dos — el ticket
-- es el registro, WhatsApp es el trato.
--
-- Las categorías de habeas data son, además, el canal de derechos del titular que exige la Ley
-- 1581. No hace falta un formulario aparte: hace falta que este exista y responda a tiempo.

CREATE TABLE support_tickets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Legible por teléfono: "ORN-4F2A19". Cuando alguien llama por WhatsApp, esto es lo que dice.
    code        VARCHAR(12) NOT NULL UNIQUE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category    VARCHAR(30) NOT NULL,
    subject     VARCHAR(160) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    -- La clase de la que habla, si habla de una. ON DELETE SET NULL: borrar una reserva no puede
    -- llevarse por delante el reclamo que alguien puso sobre ella.
    booking_id  UUID REFERENCES bookings (id) ON DELETE SET NULL,
    -- Solo las categorías con plazo fijado por ley. NULL en las demás: inventar un vencimiento
    -- donde la ley no lo pone sería confundir una promesa comercial con una obligación.
    due_at      TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE support_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id  UUID NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    author_id  UUID NOT NULL REFERENCES users (id),
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- La bandeja del admin ordena por lo que vence antes; la de cada persona, por lo más reciente.
CREATE INDEX idx_tickets_por_vencer ON support_tickets (due_at) WHERE status <> 'CLOSED';
CREATE INDEX idx_tickets_por_usuario ON support_tickets (user_id, created_at DESC);
CREATE INDEX idx_tickets_por_estado ON support_tickets (status, created_at DESC);
CREATE INDEX idx_mensajes_por_ticket ON support_messages (ticket_id, created_at);
