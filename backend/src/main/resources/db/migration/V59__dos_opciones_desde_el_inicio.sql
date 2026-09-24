-- Dos opciones de cada cosa desde el primer día (decisión de Pardo, 24/09/2026): con una sola
-- órbita, una sola paleta y un solo cielo, «personalizar el avatar» no tenía nada que elegir hasta
-- la primera clase. Se eligieron las que menos le quitan a un logro:
--   · la órbita «Órbita»: la primera clase sigue dando la paleta «Durazno»;
--   · la paleta «Lavanda»: la pedía «Dos idiomas», que ya no se puede conseguir (solo inglés);
--   · el cielo «Bruma»: «Perfil listo» sigue dando el accesorio «Centro monograma»;
--   · el accesorio «Base órbita»: «Diez clases» sigue dando la órbita «Halo».
UPDATE cosmetics SET is_default = TRUE, unlock_achievement = NULL
 WHERE (kind, code) IN (('FRAME', 'orbita'), ('PALETTE', 'lavanda'), ('SKY', 'bruma'), ('ACCESSORY', 'base-orbita'));
