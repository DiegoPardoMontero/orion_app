-- Bloque 9, paso 5 — Derecho de retracto y devoluciones al medio de pago.
--
-- El art. 47 de la Ley 1480 de 2011 da 5 días hábiles para retractarse de una compra a distancia,
-- salvo que la prestación del servicio ya haya comenzado con acuerdo del consumidor. La devolución
-- va al MISMO medio de pago y en 15 días calendario (plazo de la Ley 2439 de 2024).
--
-- Wompi no expone reembolso por API, así que el movimiento del dinero lo hace una persona en su
-- panel. Todo lo demás —detectar que aplica, congelar el pago, calcular el plazo, avisar, y no
-- dejar cerrar sin referencia— es automático. Lo manual queda reducido a la transferencia.

-- Un estado propio, y no reutilizar DISPUTED. Un pago en retracto no está en disputa: no hay nada
-- que decidir, la ley ya decidió. Mezclarlos habría hecho que la conciliación mostrara como
-- "requiere decisión" algo que solo requiere ejecutarse, y habría escondido justo lo que hay que
-- vigilar: cuántos días quedan.
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'PAID', 'RELEASED', 'REFUND_PENDING',
                      'REFUNDED', 'DISPUTED', 'CANCELLED'));

CREATE TABLE refund_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    payment_id      UUID NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES users (id),
    reason          VARCHAR(20) NOT NULL CHECK (reason IN ('RETRACTO', 'ADMIN')),
    amount_cop      BIGINT NOT NULL CHECK (amount_cop > 0),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PAID')),
    -- Creación + 15 días calendario. Se sella al crearla y no se recalcula: el plazo corre desde
    -- que se ejerció el derecho, no desde la última vez que alguien miró la pantalla.
    due_at          TIMESTAMPTZ NOT NULL,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    resolved_by     UUID REFERENCES users (id),
    -- La constancia de la devolución hecha en Wompi. Sin ella no se puede cerrar: la misma regla
    -- que ya rige las liquidaciones, y por la misma razón.
    wompi_reference VARCHAR(140),
    note            TEXT,
    -- Una reserva no puede tener dos devoluciones abiertas. El árbitro es esto, no un if.
    UNIQUE (booking_id)
);

CREATE INDEX idx_refunds_por_vencer ON refund_requests (due_at) WHERE status = 'PENDING';
CREATE INDEX idx_refunds_por_estudiante ON refund_requests (student_id, requested_at DESC);

-- Motivo nuevo de crédito: la cancelación del propio estudiante dentro de plazo. Hasta ahora ese
-- pago se quedaba esperando una decisión manual del admin, aunque los Términos ya prometían el
-- saldo. La decisión se tomó y está escrita en el contrato, así que se automatiza.
--
-- Va aparte de CANCELLED_BY_PROFESSOR porque la conciliación necesita distinguir quién soltó la
-- clase: mezclarlos haría parecer que un profesor cancela mucho más de lo que cancela.
ALTER TABLE student_credits DROP CONSTRAINT student_credits_reason_check;
ALTER TABLE student_credits ADD CONSTRAINT student_credits_reason_check
    CHECK (reason IN ('PROFESSOR_NO_SHOW', 'CANCELLED_BY_PROFESSOR', 'CANCELLED_BY_STUDENT',
                      'DISPUTE_RESOLVED', 'ADMIN_ADJUSTMENT'));
