-- Brief de liquidaciones bajo mandato, paso 6: reportes contables y certificado anual.
--
-- La UVT de 2026, $52.374 (el valor del brief). Es un ajuste y no una constante
-- porque cambia cada año: el indicador del panel expresa el recaudo del año en UVT, junto a la
-- referencia de 3.500 UVT.
INSERT INTO platform_settings (key, value) VALUES ('uvt_cop', '52374') ON CONFLICT (key) DO NOTHING;

-- El certificado de ingresos recibidos para terceros, uno por profe y por año. El sistema arma el
-- borrador; lo firma un contador público, y el admin sube aquí el PDF firmado. Se guarda privado,
-- con URL firmada, como los documentos de la postulación.
CREATE TABLE payout_certificates (
    professor_id UUID        NOT NULL REFERENCES users(id),
    year         INT         NOT NULL CHECK (year BETWEEN 2020 AND 2100),
    storage_key  VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by  UUID        NOT NULL REFERENCES users(id),
    PRIMARY KEY (professor_id, year)
);
