-- Brief de liquidaciones bajo mandato, paso 3: liquidaciones quincenales con cortes fijos.
--
-- Orión recibe el dinero de cada clase por cuenta del profe (mandato, V72) y se lo entrega cada
-- quincena, menos la comisión. La liquidación deja trazado, clase por clase, cuánto se recibió en
-- nombre de quién, cuánto se descontó y cuándo se entregó.
--
-- Pardo, 25/09/2026: las liquidaciones que existían eran pruebas y se borran. El modelo nuevo empieza
-- de cero con el primer corte; las clases que ya estaban liberadas entran en él.

DELETE FROM payout_items;
DELETE FROM payouts;

-- Las líneas reemplazan a los ítems: además del pago, guardan la reserva, la comisión congelada y un
-- texto que el profe entiende. Y hay líneas que no son clases (ajustes y arrastres).
DROP TABLE payout_items;

ALTER TABLE payouts DROP CONSTRAINT payouts_status_check;
ALTER TABLE payouts DROP CONSTRAINT payouts_amount_cop_check;
ALTER TABLE payouts ALTER COLUMN status SET DEFAULT 'DRAFT';
ALTER TABLE payouts
    ADD COLUMN cutoff_at          TIMESTAMPTZ  NOT NULL,
    ADD COLUMN hold_reason        VARCHAR(120),
    ADD COLUMN gross_cop          BIGINT       NOT NULL,
    ADD COLUMN commission_cop     BIGINT       NOT NULL,
    ADD COLUMN adjustments_cop    BIGINT       NOT NULL,
    ADD COLUMN committed_pay_date DATE         NOT NULL,
    ADD COLUMN approved_at        TIMESTAMPTZ,
    ADD COLUMN approved_by        UUID REFERENCES users(id),
    ADD COLUMN paid_on            DATE,
    ADD COLUMN paid_by            UUID REFERENCES users(id),
    ADD COLUMN payee_key_type     VARCHAR(20),
    ADD COLUMN payee_key          VARCHAR(100),
    ADD COLUMN payee_holder       VARCHAR(150),
    ADD COLUMN holder_verified    BOOLEAN      NOT NULL DEFAULT false,
    ADD COLUMN updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now();

-- `amount_cop` sigue siendo lo que se le entrega al profe: el neto.
ALTER TABLE payouts ADD CONSTRAINT payouts_status_check
    CHECK (status IN ('DRAFT', 'APPROVED', 'PAID', 'ON_HOLD', 'CARRIED_OVER'));
ALTER TABLE payouts ADD CONSTRAINT payouts_net_check
    CHECK (amount_cop = gross_cop - commission_cop + adjustments_cop);
-- Retenida siempre dice por qué; las demás, no.
ALTER TABLE payouts ADD CONSTRAINT payouts_hold_reason_check
    CHECK ((status = 'ON_HOLD') = (hold_reason IS NOT NULL));
-- Pagada es una afirmación con respaldo: fecha, referencia, la llave usada y el titular verificado.
ALTER TABLE payouts ADD CONSTRAINT payouts_paid_check
    CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND paid_on IS NOT NULL AND reference IS NOT NULL
                                AND payee_key IS NOT NULL AND payee_holder IS NOT NULL AND holder_verified
                                AND approved_at IS NOT NULL AND amount_cop > 0));
-- Una liquidación por profe y por quincena: el corte que corre dos veces choca aquí.
ALTER TABLE payouts ADD CONSTRAINT uq_payout_professor_period UNIQUE (professor_id, period_start);

CREATE TABLE payout_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_id           UUID         NOT NULL REFERENCES payouts(id) ON DELETE CASCADE,
    kind                VARCHAR(25)  NOT NULL
                        CHECK (kind IN ('CLASS', 'LATE_CANCELLATION', 'REFUND_ADJUSTMENT', 'CARRY_OVER')),
    booking_id          UUID REFERENCES bookings(id),
    payment_id          UUID REFERENCES payments(id),
    class_at            TIMESTAMPTZ,
    student_label       VARCHAR(160),
    gross_cop           BIGINT       NOT NULL,
    commission_rate_bps INT,
    commission_cop      BIGINT       NOT NULL,
    net_cop             BIGINT       NOT NULL,
    description         VARCHAR(200) NOT NULL,
    CHECK (kind NOT IN ('CLASS', 'LATE_CANCELLATION')
           OR (booking_id IS NOT NULL AND payment_id IS NOT NULL AND net_cop = gross_cop - commission_cop))
);

CREATE INDEX idx_payout_lines_payout ON payout_lines(payout_id);
-- Una reserva entra una sola vez en una liquidación como clase: es la garantía de no pagar dos veces.
CREATE UNIQUE INDEX uq_payout_line_booking ON payout_lines(booking_id)
    WHERE kind IN ('CLASS', 'LATE_CANCELLATION');

-- Lo que espera el próximo corte: una devolución sobre una clase ya pagada, o un saldo arrastrado.
CREATE TABLE payout_adjustments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id      UUID         NOT NULL REFERENCES users(id),
    booking_id        UUID REFERENCES bookings(id),
    kind              VARCHAR(25)  NOT NULL CHECK (kind IN ('REFUND_ADJUSTMENT', 'CARRY_OVER')),
    amount_cop        BIGINT       NOT NULL,
    description       VARCHAR(200) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    applied_payout_id UUID REFERENCES payouts(id) ON DELETE SET NULL
);

CREATE INDEX idx_payout_adjustments_pending ON payout_adjustments(professor_id) WHERE applied_payout_id IS NULL;
-- Una devolución por reserva: el evento que la registra puede llegar dos veces.
CREATE UNIQUE INDEX uq_payout_adjustment_refund ON payout_adjustments(booking_id) WHERE kind = 'REFUND_ADJUSTMENT';

-- Cada corte que corrió, una vez: el trabajo programado lo mira para no repetirlo, y el admin ve
-- cuándo corrió y cuántas liquidaciones creó.
CREATE TABLE payout_cuts (
    period_start    DATE PRIMARY KEY,
    period_end      DATE        NOT NULL,
    cutoff_at       TIMESTAMPTZ NOT NULL,
    ran_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    payouts_created INT         NOT NULL
);
