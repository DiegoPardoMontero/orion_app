-- Bloque 5 del brief maestro: toda clase termina en un estado definido y auditable, y el dinero
-- se mueve en consecuencia. El brief la llamaba V14; el esquema ya iba por V16.

-- ---------------------------------------------------------------------------------------------
-- Estados nuevos de la reserva
-- ---------------------------------------------------------------------------------------------
-- NO_SHOW se parte en dos. El genérico no distinguía quién faltó, y esa es justamente la
-- diferencia que decide si el profesor cobra: si el estudiante no llega, el profesor reservó su
-- hora y estuvo ahí; si el que falta es el profesor, el estudiante recupera su dinero. Las filas
-- existentes son todas del primer caso (AttendanceService solo registra la asistencia DEL
-- ESTUDIANTE), así que se migran a NO_SHOW_STUDENT.
UPDATE bookings SET status = 'NO_SHOW_STUDENT' WHERE status = 'NO_SHOW';

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE bookings ADD CONSTRAINT bookings_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED_BY_STUDENT',
                      'CANCELLED_BY_PROFESSOR', 'CANCELLED_BY_ADMIN', 'EXPIRED',
                      'COMPLETED', 'UNDER_REVIEW', 'NO_SHOW_PROFESSOR', 'NO_SHOW_STUDENT'));

-- El brief lista además RESCHEDULE_REQUESTED. NO se añade, y es deliberado: una solicitud de
-- reprogramación pendiente vive en su propia tabla, con su índice único parcial. Mientras nadie la
-- acepta, la clase SIGUE siendo a la hora original —el propio brief lo dice: si nadie responde, la
-- solicitud vence y la reserva sigue su curso—. Sacarla de CONFIRMED solo conseguiría que dejara
-- de contar como clase confirmada en media docena de sitios que sí tienen que verla.

-- Marca de cierre. Es lo que hace idempotente al job de autocompletado: una reserva con
-- completed_at ya se cerró, aunque el job vuelva a pasar por ella.
ALTER TABLE bookings ADD COLUMN completed_at TIMESTAMPTZ;

-- ---------------------------------------------------------------------------------------------
-- Reprogramación: se propone, no se impone
-- ---------------------------------------------------------------------------------------------
CREATE TABLE reschedule_requests (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id         UUID NOT NULL REFERENCES bookings(id),
    requested_by       UUID NOT NULL REFERENCES users(id),
    proposed_starts_at TIMESTAMPTZ NOT NULL,
    proposed_ends_at   TIMESTAMPTZ NOT NULL,
    reason             VARCHAR(300),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    resolved_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (proposed_starts_at < proposed_ends_at)
);

-- Una sola solicitud abierta por reserva: dos propuestas vivas a la vez es una negociación que
-- nadie sabe cerrar.
CREATE UNIQUE INDEX uq_reschedule_open ON reschedule_requests(booking_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_reschedule_booking ON reschedule_requests(booking_id, created_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Disputas: el estudiante reclama, una persona resuelve
-- ---------------------------------------------------------------------------------------------
CREATE TABLE disputes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    opened_by       UUID NOT NULL REFERENCES users(id),
    reason_code     VARCHAR(40) NOT NULL
                    CHECK (reason_code IN ('PROFESSOR_NO_SHOW', 'PROFESSOR_LATE',
                                           'TECHNICAL_PROBLEM', 'LESSON_NOT_HELD', 'OTHER')),
    description     VARCHAR(1000),
    status          VARCHAR(25) NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED_FOR_STUDENT',
                                      'RESOLVED_FOR_PROFESSOR', 'DISMISSED')),
    resolution_note VARCHAR(1000),
    resolved_by     UUID REFERENCES users(id),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_dispute_open ON disputes(booking_id)
    WHERE status IN ('OPEN', 'UNDER_REVIEW');

CREATE INDEX idx_disputes_status ON disputes(status, created_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Ausencias del profesor: el hecho, separado de su castigo
-- ---------------------------------------------------------------------------------------------
-- Una fila por ausencia CONFIRMADA (la disputa se resolvió a favor del estudiante). Es el insumo
-- de las sanciones del Bloque 6, y vive aparte a propósito: el hecho es permanente, la sanción
-- caduca.
CREATE TABLE professor_absences (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID NOT NULL REFERENCES users(id),
    booking_id   UUID NOT NULL UNIQUE REFERENCES bookings(id),
    dispute_id   UUID REFERENCES disputes(id),
    occurred_at  TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_absences_prof ON professor_absences(professor_id, occurred_at DESC);

-- ---------------------------------------------------------------------------------------------
-- Sanciones
-- ---------------------------------------------------------------------------------------------
-- ACCOUNT_SUSPENDED es SIEMPRE manual (created_by NOT NULL): ningún automatismo debería poder
-- cerrarle la cuenta a alguien que vive de ella.
CREATE TABLE professor_sanctions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    professor_id UUID NOT NULL REFERENCES users(id),
    type         VARCHAR(30) NOT NULL
                 CHECK (type IN ('WARNING', 'VISIBILITY_REDUCED',
                                 'BOOKINGS_SUSPENDED', 'PROFILE_HIDDEN',
                                 'ACCOUNT_SUSPENDED')),
    reason       VARCHAR(300) NOT NULL,
    -- PROPOSED = el sistema calculó que correspondía pero no la aplicó (modo observación).
    -- ACTIVE = surte efecto. REVOKED = el admin la levantó.
    state        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                 CHECK (state IN ('PROPOSED', 'ACTIVE', 'REVOKED')),
    starts_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    ends_at      TIMESTAMPTZ,
    created_by   UUID REFERENCES users(id),   -- NULL = propuesta o aplicada por el sistema
    revoked_by   UUID REFERENCES users(id),
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (type <> 'ACCOUNT_SUSPENDED' OR created_by IS NOT NULL)
);

-- Sin now() en el predicado: Postgres exige que las funciones de un índice parcial sean
-- IMMUTABLE, y now() no lo es (el índice tendría que reconstruirse a cada segundo). El filtro por
-- vigencia lo pone la consulta; el índice solo acota a las activas, que son un puñado.
CREATE INDEX idx_sanctions_active ON professor_sanctions(professor_id, ends_at)
    WHERE state = 'ACTIVE';

-- ---------------------------------------------------------------------------------------------
-- Ajustes nuevos
-- ---------------------------------------------------------------------------------------------
-- sanctions_mode = OBSERVE: se calcula la sanción que correspondería y se registra como PROPOSED
-- para que una persona la confirme. ENFORCE la aplica sola. Decisión de Pardo (02/09/2026):
-- arrancar en OBSERVE — con pocos profesores, un automatismo puede sacar a alguien del
-- marketplace sin que nadie lo mire. Encenderlo es un UPDATE, no un despliegue.
INSERT INTO platform_settings (key, value) VALUES
    ('sanctions_mode',            'OBSERVE'),
    ('reschedule_min_hours',      '2'),
    ('dispute_report_window_hours', '24')
ON CONFLICT (key) DO NOTHING;
