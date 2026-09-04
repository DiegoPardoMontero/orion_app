-- El idioma de la clase.
--
-- Hasta aquí se deducía del profesor, y un profesor que enseña dos idiomas hacía esa deducción
-- imposible. El dato no es recuperable hacia atrás: cada clase que pasa sin registrarlo es una
-- clase que nunca sabremos en qué idioma fue. De ahí que esto vaya antes que nada.

ALTER TABLE bookings ADD COLUMN language_code VARCHAR(5) REFERENCES languages(code);

-- Backfill solo donde la deducción es inequívoca: el profesor enseña exactamente un idioma.
UPDATE bookings b
SET language_code = pl.language_code
FROM professor_languages pl
WHERE pl.professor_id = b.professor_id
  AND (SELECT count(*) FROM professor_languages x
       WHERE x.professor_id = b.professor_id) = 1;

-- Deliberadamente SIN NOT NULL. Las reservas de profesores multi-idioma quedan en NULL para
-- siempre, y eso es correcto: representa "no lo sabemos", que es la verdad. Inventar un idioma
-- para satisfacer una constraint sería peor que el hueco.
CREATE INDEX idx_bookings_language ON bookings(language_code)
    WHERE language_code IS NOT NULL;

-- Cuántas quedaron sin idioma, para que Pardo las complete a mano desde el panel. Sale como
-- NOTICE en el log de la migración: es el único momento en que el recuento es interesante.
DO $$
DECLARE
    sin_idioma INTEGER;
    total      INTEGER;
BEGIN
    SELECT count(*) INTO sin_idioma FROM bookings WHERE language_code IS NULL;
    SELECT count(*) INTO total FROM bookings;
    IF sin_idioma > 0 THEN
        RAISE NOTICE 'V20: % de % reservas quedaron sin idioma (profesor multi-idioma o sin idiomas declarados). Revisar en el panel de admin.', sin_idioma, total;
    ELSE
        RAISE NOTICE 'V20: las % reservas existentes quedaron con idioma.', total;
    END IF;
END $$;
