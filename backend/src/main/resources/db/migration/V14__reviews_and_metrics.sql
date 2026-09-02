-- Bloque 6.2: reseñas de estudiantes y agregado de rating por profesor.
-- Una reseña por reserva (UNIQUE booking_id). Nunca se borra la fila: el profesor puede reportarla
-- y el admin ocultarla (is_visible=false), pero el histórico queda. El agregado de professor_metrics
-- se recalcula SOLO sobre reseñas visibles al crear/ocultar.

CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID UNIQUE NOT NULL REFERENCES bookings(id),
    student_id UUID NOT NULL REFERENCES users(id),
    professor_id UUID NOT NULL REFERENCES users(id),
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(1000),
    is_visible BOOLEAN NOT NULL DEFAULT true,
    hidden_by UUID REFERENCES users(id),
    hidden_reason VARCHAR(300),
    reported_at TIMESTAMPTZ,
    reported_reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE INDEX idx_reviews_prof ON reviews(professor_id, created_at DESC) WHERE is_visible;

CREATE TABLE professor_metrics (
    professor_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    rating_avg NUMERIC(3,2),
    rating_count INTEGER NOT NULL DEFAULT 0,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now());
