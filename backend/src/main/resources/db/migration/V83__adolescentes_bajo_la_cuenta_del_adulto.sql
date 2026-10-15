-- Orión para adolescentes de 13 a 17 años (Bloque 12).
--
-- Propuesta del 25/09/2026 aprobada por el abogado de Pardo tal cual (14/10/2026). El principio que
-- ordena el esquema: LA RESERVA SIGUE SIENDO DEL ADULTO. `bookings.student_id` es el representante
-- legal, que paga, reserva, habla con el profe y reseña; el adolescente es un perfil bajo su cuenta
-- —sin usuario, sin correo, sin teléfono— y una columna más en la reserva. Así pagos, saldo,
-- reclamos, retracto, reseñas, mensajes y liquidaciones no cambian: todos cuelgan de student_id.
-- Las cuentas siguen siendo solo de mayores de 18.

-- 1. El perfil del adolescente. Lo mínimo para dar la clase: nombre de pila, año de nacimiento (no la
--    fecha), nivel y objetivo. Nada de foto, correo, teléfono, documento ni colegio (Ley 1581 art. 7:
--    se trata lo que responde a su interés superior, y nada más).
CREATE TABLE adolescent_profiles (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guardian_user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    first_name           VARCHAR(60) NOT NULL,
    birth_year           SMALLINT NOT NULL,
    self_declared_level  VARCHAR(20) CHECK (self_declared_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    motivation           VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Cerrado = la autorización se revocó o el joven cumplió 18. El perfil deja de servir para reservar;
    -- la fila se conserva solo mientras haya clases que la referencien, y la supresión la borra.
    closed_at            TIMESTAMPTZ
);
CREATE INDEX idx_adolescent_profiles_guardian ON adolescent_profiles (guardian_user_id) WHERE closed_at IS NULL;

-- 2. La autorización del representante legal (Decreto 1377 de 2013, art. 12): quién, qué versión del
--    texto, cuándo, desde dónde, y que el adolescente fue escuchado. Una por perfil y versión; revocar
--    la marca, no la borra: la constancia de que existió es lo que protege a todos.
CREATE TABLE guardian_authorizations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    adolescent_profile_id UUID NOT NULL REFERENCES adolescent_profiles(id) ON DELETE CASCADE,
    guardian_user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    document_version      VARCHAR(20) NOT NULL,
    heard_confirmed       BOOLEAN NOT NULL,
    ip_address            VARCHAR(45),
    user_agent            VARCHAR(300),
    accepted_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at            TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_guardian_authorization_viva
    ON guardian_authorizations (adolescent_profile_id) WHERE revoked_at IS NULL;

-- 3. La reserva para un adolescente: la columna, y la confirmación del adulto de que está presente o
--    cerca, que es lo que abre el aula (se registra una vez por clase).
ALTER TABLE bookings
    ADD COLUMN adolescent_profile_id UUID REFERENCES adolescent_profiles(id),
    ADD COLUMN guardian_present_confirmed_at TIMESTAMPTZ;
CREATE INDEX idx_bookings_adolescent ON bookings (adolescent_profile_id) WHERE adolescent_profile_id IS NOT NULL;

-- La prueba gratis es una por cada hijo (Pardo, 14/10/2026): el índice cuenta el perfil. Para los
-- adultos (perfil nulo) sigue siendo una por pareja estudiante–profesor, porque COALESCE lo deja en
-- un valor fijo.
DROP INDEX ux_bookings_una_prueba_por_pareja;
CREATE UNIQUE INDEX ux_bookings_una_prueba_por_pareja
    ON bookings (student_id, professor_id, COALESCE(adolescent_profile_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE is_trial AND (
        status IN ('PENDING_PAYMENT', 'CONFIRMED', 'UNDER_REVIEW', 'COMPLETED', 'NO_SHOW_STUDENT')
        OR (status = 'CANCELLED_BY_STUDENT' AND cancelled_at >= starts_at));

-- 4. Profes habilitados para adolescentes: lo enciende el admin tras revisar el código de conducta
--    aceptado, la consulta del Registro de inhabilidades (Ley 1918 de 2018) y los antecedentes. Vence
--    al año (Pardo, 14/10/2026) y se renueva igual. Vencido o nulo, el profe no aparece para un
--    perfil de adolescente y la reserva lo rechaza.
ALTER TABLE professor_profiles
    ADD COLUMN minors_cleared_until DATE,
    ADD COLUMN minors_cleared_at    TIMESTAMPTZ,
    ADD COLUMN minors_cleared_by    UUID REFERENCES users(id);

-- Los dos documentos que sustentan la habilitación.
ALTER TABLE teacher_documents DROP CONSTRAINT teacher_documents_doc_type_check;
ALTER TABLE teacher_documents ADD CONSTRAINT teacher_documents_doc_type_check
    CHECK (doc_type IN ('CV', 'TEACHING_CERTIFICATE', 'UNIVERSITY_DEGREE', 'LANGUAGE_CERTIFICATION', 'OTHER',
                        'REGISTRY_CHECK', 'BACKGROUND_CHECK'));

-- 5. Dos documentos legales nuevos, versionados como los demás: la autorización del representante
--    legal y el código de conducta del profe para clases con menores. Sus textos entran con la 1.2.
ALTER TABLE legal_documents DROP CONSTRAINT legal_documents_code_check;
ALTER TABLE legal_documents ADD CONSTRAINT legal_documents_code_check
    CHECK (code IN ('TERMS', 'PRIVACY', 'TEACHER_AGREEMENT', 'GUARDIAN_AUTHORIZATION', 'MINORS_CODE_OF_CONDUCT'));
