-- Bloque 4 del brief maestro: recaudo, comisión, créditos y liquidación.
-- El brief la llamaba V13; el esquema ya iba por V15, así que esta es la V16.

-- ---------------------------------------------------------------------------------------------
-- La reserva ahora puede existir sin estar pagada.
-- ---------------------------------------------------------------------------------------------
-- NO_SHOW se conserva (hay filas con ese valor y AttendanceService lo escribe hoy). Los estados
-- RESCHEDULE_REQUESTED / UNDER_REVIEW / NO_SHOW_PROFESSOR / NO_SHOW_STUDENT que el brief lista
-- pertenecen al Bloque 5: nada en este bloque los produce y un estado que ningún código escribe
-- es andamiaje muerto. Entrarán con el código que los use.
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED_BY_STUDENT',
                      'CANCELLED_BY_PROFESSOR', 'CANCELLED_BY_ADMIN', 'EXPIRED',
                      'COMPLETED', 'NO_SHOW'));

ALTER TABLE bookings
    ADD COLUMN is_trial   BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN expires_at TIMESTAMPTZ;   -- solo tiene sentido en PENDING_PAYMENT

-- El cupo se ocupa también mientras el pago está en curso: si no, dos estudiantes llegan al
-- checkout por el mismo horario y uno paga una clase que no existe.
DROP INDEX IF EXISTS uq_bookings_professor_slot;
CREATE UNIQUE INDEX uq_bookings_professor_slot
    ON bookings(professor_id, starts_at)
    WHERE status IN ('CONFIRMED', 'PENDING_PAYMENT');

-- El job de expiración barre por esta columna; el índice parcial lo deja en una lectura mínima.
CREATE INDEX idx_bookings_pending_expiry ON bookings(expires_at)
    WHERE status = 'PENDING_PAYMENT';

-- ---------------------------------------------------------------------------------------------
-- Libro contable: una fila por reserva.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE payments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id             UUID   UNIQUE NOT NULL REFERENCES bookings(id),
    student_id             UUID   NOT NULL REFERENCES users(id),
    professor_id           UUID   NOT NULL REFERENCES users(id),
    amount_cop             BIGINT NOT NULL CHECK (amount_cop >= 0),
    credit_applied_cop     BIGINT NOT NULL DEFAULT 0 CHECK (credit_applied_cop >= 0),
    charged_cop            BIGINT NOT NULL CHECK (charged_cop >= 0),
    commission_rate_bps    INTEGER NOT NULL CHECK (commission_rate_bps BETWEEN 0 AND 10000),
    commission_cop         BIGINT NOT NULL CHECK (commission_cop >= 0),
    professor_earnings_cop BIGINT NOT NULL CHECK (professor_earnings_cop >= 0),
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'PAID', 'RELEASED',
                                             'REFUNDED', 'DISPUTED', 'CANCELLED')),
    provider               VARCHAR(20),
    provider_reference     VARCHAR(140),
    paid_at                TIMESTAMPTZ,
    released_at            TIMESTAMPTZ,
    refunded_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- La base garantizando que la contabilidad cuadra: ningún redondeo en Java puede crear plata.
    CHECK (commission_cop + professor_earnings_cop = amount_cop),
    -- Y que el crédito aplicado más lo cobrado sea exactamente el precio de la clase.
    CHECK (credit_applied_cop + charged_cop = amount_cop)
);

CREATE INDEX idx_payments_professor ON payments(professor_id, status);
CREATE INDEX idx_payments_student   ON payments(student_id, created_at DESC);
-- La referencia que viaja a la pasarela: la usa la conciliación y el webhook para reencontrar el pago.
CREATE UNIQUE INDEX uq_payments_provider_reference
    ON payments(provider, provider_reference)
    WHERE provider_reference IS NOT NULL;

-- Auditoría cruda de la pasarela: nunca se borra, nunca se edita.
CREATE TABLE payment_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id        UUID REFERENCES payments(id),
    provider          VARCHAR(20)  NOT NULL,
    provider_event_id VARCHAR(140) NOT NULL,
    event_type        VARCHAR(60)  NOT NULL,
    payload           JSONB        NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_event_id)      -- idempotencia del webhook
);

-- ---------------------------------------------------------------------------------------------
-- Créditos del estudiante: un pasivo de Orión, no un descuento al profesor.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE student_credits (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id    UUID   NOT NULL REFERENCES users(id),
    amount_cop    BIGINT NOT NULL CHECK (amount_cop > 0),
    remaining_cop BIGINT NOT NULL CHECK (remaining_cop >= 0),
    reason        VARCHAR(40) NOT NULL
                  CHECK (reason IN ('PROFESSOR_NO_SHOW', 'CANCELLED_BY_PROFESSOR',
                                    'DISPUTE_RESOLVED', 'ADMIN_ADJUSTMENT')),
    booking_id    UUID REFERENCES bookings(id),
    expires_at    TIMESTAMPTZ,
    created_by    UUID REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (remaining_cop <= amount_cop)
);

CREATE INDEX idx_credits_student ON student_credits(student_id)
    WHERE remaining_cop > 0;

-- ---------------------------------------------------------------------------------------------
-- Liquidación a profesores: el sistema calcula, una persona transfiere.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE payouts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID   NOT NULL REFERENCES users(id),
    period_start DATE   NOT NULL,
    period_end   DATE   NOT NULL,
    amount_cop   BIGINT NOT NULL CHECK (amount_cop >= 0),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    reference    VARCHAR(140),
    paid_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (period_start <= period_end)
);

CREATE INDEX idx_payouts_professor ON payouts(professor_id, status);

-- payment_id es UNIQUE en toda la tabla, no solo dentro de un payout: una clase se paga UNA vez.
CREATE TABLE payout_items (
    payout_id  UUID NOT NULL REFERENCES payouts(id) ON DELETE CASCADE,
    payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),
    PRIMARY KEY (payout_id, payment_id)
);

-- ---------------------------------------------------------------------------------------------
-- Qué crédito pagó qué parte de qué reserva.
-- ---------------------------------------------------------------------------------------------
-- El brief no la lista, pero sin ella la expiración es imposible de cuadrar: `credit_applied_cop`
-- dice CUÁNTO crédito se gastó, no DE CUÁLES filas. Al vencer una reserva hay que devolver
-- exactamente lo que se le quitó a cada crédito — con su propio vencimiento y su propio motivo —
-- y adivinarlo sería inventar plata. Una fila por (pago, crédito).
CREATE TABLE payment_credit_applications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID   NOT NULL REFERENCES payments(id),
    credit_id  UUID   NOT NULL REFERENCES student_credits(id),
    amount_cop BIGINT NOT NULL CHECK (amount_cop > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (payment_id, credit_id)
);

CREATE INDEX idx_credit_applications_payment ON payment_credit_applications(payment_id);
