-- El profesor ve cómo le fue a su estudiante, ejercicio por ejercicio (decisión de Pardo,
-- 24/09/2026; el estudiante lo sabe desde la portada de su práctica). Hasta ahora se guardaba solo la
-- última respuesta: con dos intentos, la primera se perdía, y es justo la que dice qué no sabía.
ALTER TABLE practice_items ADD COLUMN first_answer VARCHAR(600);
