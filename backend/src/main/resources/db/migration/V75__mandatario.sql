-- Brief de liquidaciones bajo mandato, paso 5: los datos del mandatario en el comprobante.
--
-- Vacíos al nacer a propósito: mientras nadie los escriba en Ajustes, el comprobante usa el
-- responsable y el documento de los datos legales (ORION_LEGAL_NOMBRE, ORION_LEGAL_DOCUMENTO), que
-- en producción son obligatorios. Cuando Pardo los ponga según su RUT, mandan estos.
INSERT INTO platform_settings (key, value) VALUES
    ('mandatary_name', ''),
    ('mandatary_document', '')
ON CONFLICT (key) DO NOTHING;
