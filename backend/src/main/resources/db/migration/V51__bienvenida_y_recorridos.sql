-- La bienvenida (23/09/2026): el video de Sofía para el profesor recién aprobado y los recorridos
-- guiados de la plataforma. Lo que se guarda es qué vio ya cada persona, para no repetírselo.
--
-- Un paso visto es una fila; no verlo es no tenerla. UNIQUE (user_id, step) hace idempotente el
-- «ya lo vi»: marcarlo dos veces —dos pestañas, un doble clic— deja una sola fila.
CREATE TABLE onboarding_steps (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    step         VARCHAR(40) NOT NULL
                 CHECK (step IN ('WELCOME_VIDEO', 'TOUR_PROFESSOR', 'TOUR_STUDENT')),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, step)
);

-- Vacío mientras no haya video: la bienvenida simplemente no aparece. Es un ajuste y no una
-- constante porque el video se graba, se regraba y se cambia sin desplegar.
INSERT INTO platform_settings (key, value) VALUES ('professor_welcome_video_url', '');
