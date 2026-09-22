-- El diagnóstico sin cuenta (Pardo, 22/09/2026): cada paso antes de la conversación cuesta gente,
-- y pedir una cuenta justo antes de hablar un idioma que no dominas era el más caro de todos.
--
-- Quien lo hace sin cuenta es un «lead»: un nombre de pila, las dos declaraciones que la ley exige
-- antes de tratar su voz, y una llave de dispositivo (la cookie ORION_LEAD, de la que aquí solo se
-- guarda el hash). Cuando esa persona crea su cuenta o entra, el diagnóstico pasa a ella.

CREATE TABLE assessment_leads (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name          VARCHAR(60)  NOT NULL,
    -- SHA-256 de la llave del dispositivo. La llave en claro solo vive en la cookie: con una copia
    -- de la base no se puede suplantar a nadie.
    token_hash          VARCHAR(64)  NOT NULL UNIQUE,
    -- Orión es solo para mayores de 18 (art. 7 de la Ley 1581). Aquí se declara como en el alta.
    adult_declared_at   TIMESTAMPTZ  NOT NULL,
    -- La constancia de la autorización de voz (art. 9 del Decreto 1377): qué versión, cuándo y
    -- desde dónde. Es la misma que guarda voice_consents para quien tiene cuenta, y se copia allí
    -- cuando el lead se reclama.
    consent_version     VARCHAR(20)  NOT NULL,
    consent_accepted_at TIMESTAMPTZ  NOT NULL,
    consent_ip          VARCHAR(45),
    consent_user_agent  VARCHAR(300),
    claimed_by          UUID REFERENCES users(id) ON DELETE CASCADE,
    claimed_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((claimed_by IS NULL) = (claimed_at IS NULL))
);

-- Lo que busca el job de retención: los que nadie reclamó, por antigüedad.
CREATE INDEX idx_leads_unclaimed ON assessment_leads(created_at) WHERE claimed_at IS NULL;

-- Un diagnóstico es de una cuenta, de un lead, o de los dos: al reclamarse conserva el lead para
-- poder decir de dónde vino. Lo que no puede es no ser de nadie.
ALTER TABLE confidence_assessments ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE confidence_assessments
    ADD COLUMN lead_id UUID REFERENCES assessment_leads(id) ON DELETE CASCADE;
ALTER TABLE confidence_assessments
    ADD CONSTRAINT ck_assessment_owner CHECK (user_id IS NOT NULL OR lead_id IS NOT NULL);

-- Una viva por lead e idioma, igual que por persona (uq_assessment_active). Solo mientras el lead
-- no tiene cuenta: después manda el índice de siempre, el del usuario.
CREATE UNIQUE INDEX uq_assessment_active_lead
    ON confidence_assessments(lead_id, language_code)
    WHERE status = 'IN_PROGRESS' AND user_id IS NULL;
CREATE INDEX idx_assessment_lead ON confidence_assessments(lead_id) WHERE lead_id IS NOT NULL;

-- Cuánto se guarda el diagnóstico de quien nunca creó cuenta. No lo fija una ley: es la promesa de
-- no quedarnos con la voz transcrita de un desconocido más de lo necesario, y por eso es un ajuste.
INSERT INTO platform_settings (key, value) VALUES ('assessment_lead_retention_days', '30');
