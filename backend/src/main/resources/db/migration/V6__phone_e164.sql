-- Backfill de teléfonos a E.164.
--
-- 1) Limpia separadores (espacios, guiones, paréntesis), dejando solo dígitos y un posible '+'.
-- 2) Heurística de celular colombiano: 10 dígitos que empiezan por 3 → prefijo +57.
-- Los que no encajen (ni '+', ni celular CO) se dejan INTACTOS para revisión manual de Pardo.
UPDATE users
SET whatsapp_phone = regexp_replace(whatsapp_phone, '[^0-9+]', '', 'g')
WHERE whatsapp_phone IS NOT NULL;

UPDATE users
SET whatsapp_phone = '+57' || whatsapp_phone
WHERE whatsapp_phone ~ '^3[0-9]{9}$';
