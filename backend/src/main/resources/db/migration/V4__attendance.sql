CREATE TABLE attendance_records (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
    present     BOOLEAN NOT NULL,
    notes       VARCHAR(500),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
