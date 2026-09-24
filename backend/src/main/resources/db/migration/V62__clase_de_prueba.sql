-- La clase de prueba del estudiante (Q7 del brief maestro, 24/09/2026): la reserva lleva la marca
-- `is_trial`, cuesta lo que el profesor fije como precio de prueba (0 = gratis), paga la misma
-- comisión y hay una sola por pareja estudiante–profesor.
--
-- `is_trial` ya existía desde la V16, pero la usaba el ensayo del aula que lanza un administrador.
-- Esa marca sigue con su significado y su nombre propio: `is_rehearsal`. La columna nueva nace en
-- falso para todas las reservas que ya existen, porque ninguna fue una clase de prueba.
ALTER TABLE bookings RENAME COLUMN is_trial TO is_rehearsal;
ALTER TABLE bookings ADD COLUMN is_trial BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE bookings ADD CONSTRAINT ck_bookings_prueba_no_es_ensayo CHECK (NOT (is_trial AND is_rehearsal));

-- Una por pareja: el árbitro final, detrás del chequeo amable del servicio. Una prueba cancelada,
-- vencida sin pago o a la que el profesor no llegó no la gasta.
CREATE UNIQUE INDEX ux_bookings_una_prueba_por_pareja ON bookings (student_id, professor_id)
    WHERE is_trial AND status IN ('PENDING_PAYMENT', 'CONFIRMED', 'UNDER_REVIEW', 'COMPLETED', 'NO_SHOW_STUDENT');

-- El precio de prueba del profesor. NULL = no la ofrece todavía, aunque tenga el interruptor
-- encendido: sin precio no hay nada que reservar.
ALTER TABLE professor_profiles ADD COLUMN trial_price_cop BIGINT
    CHECK (trial_price_cop IS NULL OR trial_price_cop >= 0);

-- El piso de un precio de prueba que no sea gratis. Es un número de negocio: vive aquí.
INSERT INTO platform_settings (key, value) VALUES ('trial_min_price_cop', '5000');
