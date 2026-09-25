-- La clase de prueba es GRATIS si el profesor la ofrece (24/09/2026, Pardo: «NO es una clase con
-- descuento»). Reemplaza el precio propio de la V62.
--
-- Ofrecerla ahora es regalar una hora, así que tiene que ser una decisión del profesor y no un valor
-- por defecto: la columna nacía en true (V10) sin que nadie la hubiera elegido. Se queda encendida
-- solo para quien ya la había fijado en 0 —la ofrecía gratis a sabiendas—; quien no le puso precio o
-- la cobraba la vuelve a encender desde su perfil si quiere regalarla.
UPDATE professor_profiles SET accepts_trial = false
 WHERE accepts_trial AND (trial_price_cop IS NULL OR trial_price_cop <> 0);
ALTER TABLE professor_profiles ALTER COLUMN accepts_trial SET DEFAULT false;
ALTER TABLE professor_profiles DROP COLUMN trial_price_cop;

-- El piso del precio de prueba ya no tiene sentido.
DELETE FROM platform_setting_changes WHERE key = 'trial_min_price_cop';
DELETE FROM platform_settings WHERE key = 'trial_min_price_cop';
