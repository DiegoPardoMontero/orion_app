-- Recordar la práctica (24/09/2026: «que lo recuerde a través de la plataforma, no TAN invasivo»):
-- una sola vez, dos días después de estar lista, si nadie la empezó y todavía le queda tiempo. La
-- marca es de la práctica y no de un recordatorio aparte: una práctica, un recordatorio.
ALTER TABLE practice_sets ADD COLUMN reminded_at TIMESTAMPTZ;
