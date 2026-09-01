-- Foto para todos: la columna vive ahora en users (antes solo la tenía professor_profiles).
-- Se hace backfill desde el perfil del profesor; a partir de aquí la app lee users.photo_url.
-- professor_profiles.photo_url queda DEPRECADA (se deja de leer; se eliminará en una migración
-- futura, cuando estemos seguros de que nada la usa).
ALTER TABLE users ADD COLUMN photo_url VARCHAR(500);

UPDATE users u
SET photo_url = p.photo_url
FROM professor_profiles p
WHERE p.user_id = u.id AND p.photo_url IS NOT NULL;
