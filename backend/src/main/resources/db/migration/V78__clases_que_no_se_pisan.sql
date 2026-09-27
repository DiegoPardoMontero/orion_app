-- Dos clases del mismo profesor no se pueden pisar, aunque empiecen a horas distintas.
--
-- Hasta aquí el árbitro de la doble reserva era el índice único de la V16, (professor_id,
-- starts_at) sobre las reservas que ocupan cupo. Bastaba mientras los cupos iban de hora en hora:
-- dos clases solapadas tenían que empezar a la misma hora. Desde que los cupos salen cada media
-- hora ya no: la de las 17:00 (hasta las 17:55) y la de las 17:30 tienen distinto starts_at, y si
-- dos estudiantes pasan a la vez el chequeo amable de BookingService, el índice deja entrar a los
-- dos y el profesor queda con dos clases al mismo tiempo.
--
-- La regla de verdad es «ningún par de intervalos [inicio, fin) del mismo profesor se cruza», y eso
-- en Postgres es una restricción EXCLUDE: igualdad en professor_id y solapamiento (&&) en el rango.
-- La igualdad sobre un UUID dentro de un índice GiST necesita btree_gist, que es una extensión de
-- contrib (viene con la imagen oficial de Postgres y es «trusted» desde la 13).
--
-- Los estados son los mismos del índice de la V16 y de BookingStatus.occupiesSlot(): CONFIRMED y
-- PENDING_PAYMENT. Una reserva cancelada, vencida o ya cerrada deja de ocupar el horario. El rango
-- es semiabierto '[)', como todo el dominio: una clase que termina a las 18:00 no choca con la que
-- empieza a las 18:00.
--
-- El índice único de la V16 se queda. Lo que prohíbe ya lo prohíbe esta restricción (misma hora de
-- inicio implica solapamiento), pero es la red si el bloque de abajo no llega a crearla.
--
-- Por qué el DO: si producción ya tuviera dos reservas activas que se pisan, el ALTER fallaría y
-- con él el despliegue entero. Eso no puede pasar por una fila vieja. Si hay solapes, la migración
-- NO crea la restricción, deja una advertencia en el log de Flyway con cuántos pares hay, y todo lo
-- demás sigue como antes (el índice único sigue siendo el árbitro). Para recuperarla: resolver a
-- mano esas reservas (cancelar o mover una de cada par) y aplicar una migración nueva con el mismo
-- ALTER TABLE. La consulta de los pares es la de abajo.
CREATE EXTENSION IF NOT EXISTS btree_gist;

DO $$
DECLARE
    solapes INTEGER;
BEGIN
    SELECT count(*) INTO solapes
      FROM bookings a
      JOIN bookings b
        ON a.professor_id = b.professor_id
       AND a.id < b.id
     WHERE a.status IN ('CONFIRMED', 'PENDING_PAYMENT')
       AND b.status IN ('CONFIRMED', 'PENDING_PAYMENT')
       AND tstzrange(a.starts_at, a.ends_at, '[)') && tstzrange(b.starts_at, b.ends_at, '[)');

    IF solapes = 0 THEN
        EXECUTE $ddl$
            ALTER TABLE bookings ADD CONSTRAINT ex_bookings_professor_no_overlap
                EXCLUDE USING gist (
                    professor_id WITH =,
                    tstzrange(starts_at, ends_at, '[)') WITH &&
                ) WHERE (status IN ('CONFIRMED', 'PENDING_PAYMENT'))
        $ddl$;
    ELSE
        RAISE WARNING 'V78: % par(es) de reservas activas del mismo profesor se pisan; NO se creó ex_bookings_professor_no_overlap (ver el comentario de la V78).', solapes;
    END IF;
END $$;
