-- Bloque 9, paso 2 — Términos y Política de tratamiento, versionados.
--
-- El texto vive en la base y no en el código del frontend (que es donde estaba el acuerdo del
-- profesor). La razón es la prueba: cuando alguien pregunte qué aceptó en marzo, la respuesta
-- tiene que ser el texto de marzo, no el que esté desplegado hoy.
--
-- Las ACEPTACIONES no estrenan tabla: `agreement_acceptances` (V12) ya guarda usuario, documento,
-- versión, fecha, IP y user-agent, que es exactamente la constancia que exige el art. 9 de la Ley
-- 1581 de 2012. Crear una tabla paralela habría dejado dos sitios donde buscar lo mismo.

CREATE TABLE legal_documents (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(30) NOT NULL CHECK (code IN ('TERMS', 'PRIVACY')),
    version        VARCHAR(20) NOT NULL,
    title          VARCHAR(160) NOT NULL,
    -- Markdown. El servidor no lo renderiza: lo hace el frontend, que es quien sabe de tipografía.
    body           TEXT NOT NULL,
    effective_from DATE NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);

-- Dos versiones del mismo documento no pueden entrar en vigor el mismo día. No impide que
-- convivan varias versiones —eso es justo lo que queremos, el histórico— sino que haya un día
-- con empate: la vigente es "la de mayor effective_from ya cumplido", y un empate la volvería
-- ambigua justo cuando alguien pregunte qué aceptó.
CREATE UNIQUE INDEX uq_legal_vigencia_por_dia
    ON legal_documents (code, effective_from);

CREATE INDEX idx_legal_por_codigo
    ON legal_documents (code, effective_from DESC);
