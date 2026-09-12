-- Los pesos de las cinco dimensiones del Confidence Score.
--
-- Van en ajustes para poder afinar la curva sin desplegar. Eso abre un riesgo que el propio brief
-- señala —si los pesos cambian, la curva histórica cambia sola y deja de significar algo— y por eso
-- `score_version` no es la constante «v1» sino «v1.» más la huella de los pesos con los que se
-- calculó: dos fórmulas distintas no pueden compartir versión, y la curva sigue siendo comparable
-- dentro de cada una.
--
-- Los umbrales de cada dimensión (qué latencia vale 100, cuál vale 0) SÍ viven en el código. Son la
-- definición de la métrica, no una política: cambiarlos es cambiar qué significa el número, y eso
-- merece pasar por una revisión, no por un campo de texto en una pantalla.
INSERT INTO platform_settings (key, value) VALUES
    ('score_weight_arranque',    '25'),
    ('score_weight_continuidad', '25'),
    ('score_weight_extension',   '20'),
    ('score_weight_autonomia',   '20'),
    ('score_weight_soltura',     '10');
