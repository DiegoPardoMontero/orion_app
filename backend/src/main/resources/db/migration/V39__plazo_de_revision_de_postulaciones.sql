-- Cuántos días hábiles tarda Orión en revisar una postulación de profesor.
--
-- Hasta ahora el aspirante enviaba su postulación y no se le decía nada: ni cuánto esperar, ni
-- cuándo preocuparse. Sofía lo pidió como letrero y Pardo fijó el número en tres (14/09/2026).
--
-- Va en `platform_settings` y no en el código por la razón de siempre: es una promesa de negocio,
-- y el día que no se pueda cumplir hay que poder cambiarla desde Ajustes sin desplegar. Una promesa
-- de plazo incumplida es peor que no haber prometido nada.
INSERT INTO platform_settings (key, value) VALUES
    ('application_review_business_days', '3')
ON CONFLICT (key) DO NOTHING;
