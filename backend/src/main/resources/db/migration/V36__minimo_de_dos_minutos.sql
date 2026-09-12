-- El suelo para puntuar baja de tres minutos a dos.
--
-- Los tres se calibraron contra una conversación de siete: eran el 43 % de la ventana. Con cinco
-- minutos pasaban a ser el 60 %, y alguien que conversó dos minutos y medio —más de la mitad— se
-- quedaba sin nada: ni puntaje, ni diagnóstico, ni las tres recomendaciones. Dos devuelve la
-- proporción de antes.
--
-- El filtro que de verdad impide sacar un número de la nada no es el reloj sino los cuatro turnos
-- mínimos del usuario, y ese no se mueve.
UPDATE platform_settings
   SET value = '2'
 WHERE key = 'assessment_min_minutes'
   AND value = '3';
