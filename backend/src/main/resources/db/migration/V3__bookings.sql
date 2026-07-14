CREATE TABLE bookings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id          UUID NOT NULL REFERENCES users(id),
    professor_id        UUID NOT NULL REFERENCES users(id),
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    modality            VARCHAR(20) NOT NULL
                        CHECK (modality IN ('VIRTUAL', 'IN_PERSON')),
    status              VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED'
                        CHECK (status IN ('CONFIRMED', 'CANCELLED_BY_STUDENT',
                                          'CANCELLED_BY_PROFESSOR', 'CANCELLED_BY_ADMIN',
                                          'COMPLETED', 'NO_SHOW')),
    location_note       VARCHAR(300),
    package_id          UUID,
    created_by          UUID NOT NULL REFERENCES users(id),
    cancelled_by        UUID REFERENCES users(id),
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(300),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (starts_at < ends_at)
);

CREATE UNIQUE INDEX uq_bookings_professor_slot
    ON bookings(professor_id, starts_at)
    WHERE status = 'CONFIRMED';

CREATE INDEX idx_bookings_student   ON bookings(student_id, starts_at);
CREATE INDEX idx_bookings_professor ON bookings(professor_id, starts_at);
