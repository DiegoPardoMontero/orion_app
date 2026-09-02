-- Bloque 3: mensajería interna, notificaciones in-app y política de contacto.
-- El contacto Estudiante <-> Orión <-> Profesor reemplaza a WhatsApp, con historial auditable.

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES users(id),
    professor_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ,
    UNIQUE (student_id, professor_id));

CREATE INDEX idx_conv_student ON conversations(student_id, last_message_at DESC);
CREATE INDEX idx_conv_professor ON conversations(professor_id, last_message_at DESC);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID REFERENCES users(id),
    body TEXT NOT NULL,
    body_original TEXT,
    is_system BOOLEAN NOT NULL DEFAULT false,
    flagged_reason VARCHAR(40) CHECK (flagged_reason IN ('CONTACT_INFO','OFF_PLATFORM','OTHER')),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    notified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE INDEX idx_messages_conv ON messages(conversation_id, created_at);
CREATE INDEX idx_messages_flagged ON messages(created_at DESC) WHERE flagged_reason IS NOT NULL;
CREATE INDEX idx_messages_unread ON messages(conversation_id) WHERE read_at IS NULL;

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(140) NOT NULL,
    body VARCHAR(400),
    link_path VARCHAR(200),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE INDEX idx_notif_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notif_unread ON notifications(user_id) WHERE read_at IS NULL;
