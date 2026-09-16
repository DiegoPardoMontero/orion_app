-- La conversación pasa de cinco minutos a dos.
--
-- Dos minutos es lo que Pardo pidió tras oír la primera conversación de verdad (13/09/2026): con
-- cinco, el arco se diluye y la persona ya sabe a los dos minutos si esto le sirve. Menos tiempo
-- también es menos API por diagnóstico, pero esa no es la razón: la razón es que dos minutos se
-- aceptan sin pensarlo y cinco ya se negocian.
--
-- El suelo baja con el techo, y no por simetría. Con máximo 2 y mínimo 2 el suelo dejaba de ser un
-- suelo: cualquiera que colgara un segundo antes del corte se quedaba sin puntaje, sin diagnóstico
-- y sin las tres recomendaciones. Uno devuelve la proporción que tenían tres sobre siete y dos
-- sobre cinco — cerca de la mitad de la ventana.
--
-- Lo que de verdad impide sacar un número de la nada no es el reloj sino los cuatro turnos mínimos
-- del usuario (`ConfidenceScoreCalculator.TURNOS_MINIMOS`), y ese no se mueve. En dos minutos caben
-- unos cinco o seis turnos, así que sigue habiendo margen, pero es el margen más estrecho que ha
-- tenido: si empieza a haber conversaciones sin puntaje, el sospechoso es este número y no el reloj.
--
-- Como en V35 y V36, la condición sobre el valor evita pisar una decisión tomada desde Ajustes.
UPDATE platform_settings
   SET value = '2'
 WHERE key = 'assessment_max_minutes'
   AND value = '5';

UPDATE platform_settings
   SET value = '1'
 WHERE key = 'assessment_min_minutes'
   AND value = '2';
