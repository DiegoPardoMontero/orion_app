-- Orión enseña solo inglés.
--
-- Decisión de Pardo (15/09/2026). Se apaga, no se borra: la elección explícita fue esconder el
-- modelo multiidioma y no desmontarlo. Las tablas `languages`, `professor_languages` y
-- `professor_language_levels` siguen ahí, igual que `bookings.language_code`, y por eso una clase
-- de francés dada en su momento sigue sabiendo que fue de francés. Reabrir un idioma algún día es
-- volver a poner `is_active` en true y devolver los selectores a la interfaz; borrarlo habría sido
-- una migración de vuelta y la pérdida del histórico.
--
-- El resto del backend no necesita cambios: todo lo que ofrece idiomas lee `activeLanguages()`, que
-- filtra por esta bandera. Con una sola fila activa, la plataforma se vuelve monoidioma sola.
UPDATE languages
   SET is_active = false
 WHERE code <> 'EN';

-- Y los profesores que tuvieran otro idioma publicado dejan de ofrecerlo. Sin esto, el buscador
-- seguiría mostrando «enseña francés» leyendo una fila que ya no corresponde a nada ofrecible.
-- Se borra la oferta, no el histórico de clases: son tablas distintas.
DELETE FROM professor_language_levels
 WHERE language_code <> 'EN';

DELETE FROM professor_languages
 WHERE language_code <> 'EN';
