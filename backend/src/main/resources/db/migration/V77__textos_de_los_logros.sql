-- Los textos de los logros (revisión de textos del 26/09/2026).
--
-- «Dos idiomas» pedía una clase en un segundo idioma, y desde la V42 Orión enseña solo inglés: nadie
-- puede encenderlo. Se retira como la V30 retiró «Cara a cara» cuando dejó de haber clases
-- presenciales —una estrella imposible en el cielo de alguien es peor que no tenerla—, junto con las
-- que ya se hubieran encendido con clases de francés de antes de la V42. Los puntos que dio se
-- quedan: el libro de puntos no borra, y la V30 tampoco los tocó.
--
-- No hay pieza que reasignar: la paleta «Lavanda», que era su premio en la V22, la dejó inicial la
-- V59 (con `unlock_achievement` en NULL) precisamente porque este logro ya no se podía ganar. Así que
-- la FK de `cosmetics` no apunta a él y el borrado no deja ninguna pieza inalcanzable.
DELETE FROM user_achievements WHERE achievement_code = 'amplitud-dos-idiomas';
DELETE FROM achievements       WHERE code = 'amplitud-dos-idiomas';

-- Las descripciones estaban en tercera persona («Reservó su primera clase.»), como si «Mi cielo»
-- hablara de otro. Quien las lee es el propio estudiante: se le habla de tú, como en el resto de la
-- app. Las que no nombran a nadie («5 clases tomadas.», «Una práctica entera al primer intento.») ya
-- se leen bien y se quedan como están.
UPDATE achievements SET description = 'Tu primera clase reservada.'
 WHERE code = 'primeros-primera-reserva';
UPDATE achievements SET description = 'Tu primera clase en vivo.'
 WHERE code = 'primeros-primera-clase';
UPDATE achievements SET description = 'Tu primer mensaje a un profesor.'
 WHERE code = 'primeros-primer-mensaje';
UPDATE achievements SET description = 'Tu primera reseña a un profesor.'
 WHERE code = 'compromiso-primera-resena';
UPDATE achievements SET description = 'Tu objetivo: para qué estás aprendiendo.'
 WHERE code = 'compromiso-objetivo';
UPDATE achievements SET description = 'Tu primera práctica completa.'
 WHERE code = 'practica-primera';
UPDATE achievements SET description = 'Tu ficha completa: foto, nivel, idioma, objetivo y motivación.'
 WHERE code = 'compromiso-ficha-completa';
