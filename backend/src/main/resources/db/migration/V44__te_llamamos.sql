-- «¿Prefieres que te llame una persona?» (handoff de Meissa, 22/09/2026). Hay quien no quiere
-- hablar con una voz de IA, o todavía no se atreve, y prefiere que alguien de la academia le
-- escriba. Esto guarda su pedido hasta que alguien lo atiende.
--
-- Es un dato personal con una sola finalidad —contactarle para hablar de clases—, y la
-- autorización para usarlo así se guarda con su fecha y su IP, como cualquier otra (art. 9 del
-- Decreto 1377).

CREATE TABLE callback_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name  VARCHAR(60) NOT NULL,
    whatsapp    VARCHAR(20) NOT NULL,
    consent_at  TIMESTAMPTZ NOT NULL,
    consent_ip  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    attended_at TIMESTAMPTZ,
    attended_by UUID REFERENCES users(id) ON DELETE SET NULL
);

-- La bandeja del admin: los pendientes, del más antiguo al más nuevo.
CREATE INDEX idx_callback_pending ON callback_requests(created_at) WHERE attended_at IS NULL;
