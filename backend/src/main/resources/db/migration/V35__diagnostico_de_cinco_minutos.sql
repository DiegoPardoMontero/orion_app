-- La conversación pasa de siete minutos a cinco.
--
-- Cinco es una cifra más natural para quien la va a oír: «hablemos cinco minutos» se acepta sin
-- pensarlo y «siete» hace calcular. El corte sigue siendo duro y el guion se reparte dentro de esa
-- ventana; lo que cambia es cuánto se pide, no cómo se mide.
--
-- La condición sobre el valor no es adorno: si alguien ya lo movió desde la pantalla de Ajustes,
-- esta migración no tiene por qué pisarle la decisión. Solo corrige el valor con el que nació.
UPDATE platform_settings
   SET value = '5'
 WHERE key = 'assessment_max_minutes'
   AND value = '7';
