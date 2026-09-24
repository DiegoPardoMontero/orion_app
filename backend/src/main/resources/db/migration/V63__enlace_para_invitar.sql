-- El enlace para invitar estudiantes (24/09/2026): orionidiomas.com/p/maria-gomez en vez de la
-- dirección del perfil con su UUID, que nadie copia a mano ni se ve bien en una historia. Se genera
-- la primera vez que el profesor abre «Invitar estudiantes» y ya no cambia: un enlace compartido
-- tiene que seguir funcionando aunque el profesor cambie la forma de escribir su nombre.
ALTER TABLE professor_profiles ADD COLUMN public_slug VARCHAR(60);
CREATE UNIQUE INDEX ux_professor_profiles_public_slug ON professor_profiles (public_slug)
    WHERE public_slug IS NOT NULL;
