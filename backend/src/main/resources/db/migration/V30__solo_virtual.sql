-- Orión no da clases presenciales. La modalidad se queda en la tabla, pero con un solo valor
-- posible: quitarla del todo obligaría a tocar la entidad, los DTO y el histórico entero para
-- borrar un dato que mañana podría volver, y el CHECK ya impide que nadie escriba el otro valor.

DO $$
DECLARE
    presenciales BIGINT;
    con_lugar    BIGINT;
BEGIN
    SELECT count(*) INTO presenciales FROM bookings WHERE modality = 'IN_PERSON';
    SELECT count(*) INTO con_lugar FROM bookings
        WHERE modality = 'IN_PERSON' AND location_note IS NOT NULL;
    RAISE NOTICE 'V30: % reserva(s) presencial(es) pasan a virtuales; % perdían su nota de lugar.',
        presenciales, con_lugar;
END $$;

-- La nota de lugar describía una dirección física: en una clase virtual no significa nada y
-- dejarla puesta haría que la tarjeta enseñara un sitio al que nadie tiene que ir.
UPDATE bookings
   SET modality = 'VIRTUAL',
       location_note = NULL
 WHERE modality = 'IN_PERSON';

-- Las reescritas se quedan sin sala: nacieron sin ella. La interfaz ya oculta el botón de entrar
-- cuando no hay enlace, así que no hay nada que romper — y son clases del pasado.

ALTER TABLE bookings DROP CONSTRAINT bookings_modality_check;
ALTER TABLE bookings ADD  CONSTRAINT bookings_modality_check CHECK (modality = 'VIRTUAL');

-- El logro «Cara a cara» pedía tomar una clase presencial. Ya no hay forma de conseguirlo, y una
-- estrella imposible en el cielo de alguien es peor que no tenerla: se retira junto con las que
-- ya se hubieran encendido.
DELETE FROM user_achievements WHERE achievement_code = 'amplitud-presencial';
DELETE FROM achievements       WHERE code = 'amplitud-presencial';
