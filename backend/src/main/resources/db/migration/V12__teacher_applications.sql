-- Bloque 2: Teacher Application. Nadie enseña en Orión sin que un humano lo apruebe, y esa decisión
-- queda registrada con quién, cuándo y por qué. (La tabla notifications llega en el Bloque 3 / V13.)

CREATE TABLE teacher_applications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id),
    status        VARCHAR(25) NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'UNDER_REVIEW',
                                    'CHANGES_REQUESTED', 'APPROVED', 'REJECTED')),
    submitted_at  TIMESTAMPTZ,
    reviewed_by   UUID REFERENCES users(id),
    reviewed_at   TIMESTAMPTZ,
    decision_note TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Una sola aplicación viva por persona; las cerradas (APPROVED/REJECTED) quedan como historia.
CREATE UNIQUE INDEX uq_application_open ON teacher_applications(user_id)
    WHERE status IN ('DRAFT', 'PENDING_REVIEW', 'UNDER_REVIEW', 'CHANGES_REQUESTED');

CREATE TABLE teacher_application_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES teacher_applications(id) ON DELETE CASCADE,
    event_type     VARCHAR(30) NOT NULL
                   CHECK (event_type IN ('CREATED', 'SUBMITTED', 'REVIEW_STARTED',
                                         'CHANGES_REQUESTED', 'RESUBMITTED',
                                         'APPROVED', 'REJECTED')),
    actor_id       UUID REFERENCES users(id),
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_events ON teacher_application_events(application_id, created_at);

CREATE TABLE teacher_documents (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    application_id UUID REFERENCES teacher_applications(id) ON DELETE SET NULL,
    doc_type       VARCHAR(30) NOT NULL
                   CHECK (doc_type IN ('CV', 'TEACHING_CERTIFICATE', 'UNIVERSITY_DEGREE',
                                       'LANGUAGE_CERTIFICATION', 'OTHER')),
    file_name      VARCHAR(200) NOT NULL,
    storage_key    VARCHAR(500) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     INTEGER      NOT NULL,
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_teacher_docs ON teacher_documents(user_id, doc_type);

-- Aceptación versionada de términos (necesaria para el Teacher Agreement).
CREATE TABLE agreement_acceptances (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_code VARCHAR(40) NOT NULL,
    version       VARCHAR(20) NOT NULL,
    accepted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(300)
);

CREATE UNIQUE INDEX uq_acceptance ON agreement_acceptances(user_id, document_code, version);

ALTER TABLE users ADD COLUMN phone_verified_at TIMESTAMPTZ;

-- Bitácora de acciones del admin: quién hizo qué, para siempre (incluye quién miró qué CV).
CREATE TABLE admin_audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID NOT NULL REFERENCES users(id),
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   UUID,
    detail      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_created ON admin_audit_log(created_at DESC);
