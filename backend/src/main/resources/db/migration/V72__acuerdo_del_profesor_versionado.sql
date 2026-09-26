-- Brief de liquidaciones bajo mandato, paso 1 (decisión de Pardo del 25/09/2026).
--
-- El «Acuerdo del profesor» era texto fijo en el frontend (`/aplicacion`), con su versión «1.0»
-- escrita en el código. Desde aquí es un documento legal más: vive en `legal_documents`, con sus
-- versiones, y la constancia de quién aceptó cuál sigue en `agreement_acceptances`, que ya
-- guardaba el código TEACHER_AGREEMENT. La versión 2.0 trae la cláusula de mandato de recaudo: es
-- la prueba de que Orión recibe el dinero de las clases por cuenta del profe.
--
-- El texto no se siembra aquí sino en LegalDocumentSeeder, como los Términos: es contenido, no
-- esquema. Esta migración solo abre la puerta al código nuevo.

ALTER TABLE legal_documents DROP CONSTRAINT legal_documents_code_check;
ALTER TABLE legal_documents ADD CONSTRAINT legal_documents_code_check
    CHECK (code IN ('TERMS', 'PRIVACY', 'TEACHER_AGREEMENT'));
