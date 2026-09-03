-- Clases gratuitas: una tarifa de 0 COP.
--
-- Para qué: probar el flujo de reserva completo en producción sin mover dinero por Wompi. La
-- reserva de una clase de 0 no necesita pasarela — el importe a cobrar es 0 y `CheckoutService` ya
-- confirma en el acto cuando no hay nada que cobrar, el mismo camino que usa un crédito que cubre
-- la clase entera. Lo único que lo impedía era este CHECK.
--
-- El 0 es un valor aparte y no una rebaja del piso: entre 1 y 19.999 sigue prohibido. Una tarifa
-- así no es una clase barata, es un error de tecleo, y además queda por debajo del mínimo que
-- acepta la pasarela. Cero significa "esta clase no se cobra", y es lo que la interfaz muestra.
--
-- Quién puede ponerlo: solo un administrador, desde el endpoint de admin. El formulario del propio
-- profesor conserva el piso de 20.000 (`RateRequest`), para que nadie regale su trabajo por un
-- descuido al escribir.

ALTER TABLE professor_profiles
    DROP CONSTRAINT chk_hourly_rate;

ALTER TABLE professor_profiles
    ADD CONSTRAINT chk_hourly_rate
        CHECK (hourly_rate_cop IS NULL
               OR hourly_rate_cop = 0
               OR (hourly_rate_cop >= 20000 AND hourly_rate_cop <= 500000));
