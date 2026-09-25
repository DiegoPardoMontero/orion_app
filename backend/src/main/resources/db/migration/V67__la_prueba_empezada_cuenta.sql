-- Una clase de prueba que el estudiante cancela cuando ya empezó cuenta como usada.
--
-- Desde la V65 la prueba es gratis, y eso abrió un hueco que con las clases pagadas no existía:
-- cancelar siempre se puede, así que quien tomaba la prueba entera y la cancelaba después —antes de
-- que el profesor registrara la asistencia o de que la cerrara el autocompletado— la dejaba fuera
-- del índice de la V62 y podía pedir otra, gratis, sin fin. Con una clase pagada cancelar tarde
-- libera el pago al profesor; con la gratis no costaba nada.
--
-- Cancelarla ANTES de que empiece sigue sin gastarla, y si la cancela el profesor, tampoco: el
-- estudiante no pierde su prueba por algo que no decidió.
DROP INDEX ux_bookings_una_prueba_por_pareja;
CREATE UNIQUE INDEX ux_bookings_una_prueba_por_pareja ON bookings (student_id, professor_id)
    WHERE is_trial AND (
        status IN ('PENDING_PAYMENT', 'CONFIRMED', 'UNDER_REVIEW', 'COMPLETED', 'NO_SHOW_STUDENT')
        OR (status = 'CANCELLED_BY_STUDENT' AND cancelled_at >= starts_at));
