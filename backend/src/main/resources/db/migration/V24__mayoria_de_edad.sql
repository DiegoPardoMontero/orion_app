-- Bloque 9, paso 1 — Orión acepta solo mayores de 18.
--
-- El art. 7 de la Ley 1581 de 2012 prohíbe tratar datos de niños, niñas y adolescentes salvo los
-- de naturaleza pública; el art. 12 del Decreto 1377 de 2013 solo lo permite con autorización del
-- representante legal, otorgada después de escuchar al menor. Orión no tiene ese flujo y no lo va
-- a tener por ahora, así que la puerta se cierra en el registro.

-- Constancia de la declaración: quién dijo ser mayor de edad y cuándo. Sin fecha no hay prueba,
-- y una autorización que no se puede probar no existe (art. 9 Ley 1581).
ALTER TABLE users ADD COLUMN age_confirmed_at TIMESTAMPTZ;

-- Las cuentas anteriores se quedan en NULL a propósito: marcarlas como confirmadas sería
-- fabricar una constancia que nadie dio. Se les pide en su próxima entrada.

-- Minimización (art. 4, literal c, Ley 1581): birth_date existía para una sola regla —impedir que
-- un menor publicara su ficha— y esa regla desaparece, porque ya no hay menores. Un dato personal
-- sin finalidad declarada no se conserva.
DO $$
DECLARE
    con_fecha BIGINT;
BEGIN
    SELECT count(*) INTO con_fecha FROM student_profiles WHERE birth_date IS NOT NULL;
    RAISE NOTICE 'V24: se eliminan % fechas de nacimiento por minimización de datos.', con_fecha;
END $$;

ALTER TABLE student_profiles DROP COLUMN birth_date;
