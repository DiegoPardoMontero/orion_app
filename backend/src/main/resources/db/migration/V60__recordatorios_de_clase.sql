-- Los recordatorios de cada clase (24/09/2026): el del día antes, el de una hora antes y el de
-- calificarla. Uno de cada uno por clase, y lo garantiza la llave primaria: si el trabajo corre dos
-- veces, o dos instancias a la vez, el segundo INSERT no entra y el segundo aviso no sale.
CREATE TABLE booking_reminders (
    booking_id UUID        NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    kind       VARCHAR(20) NOT NULL CHECK (kind IN ('DAY_BEFORE', 'HOUR_BEFORE', 'RATE')),
    sent_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (booking_id, kind)
);
