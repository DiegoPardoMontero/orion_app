-- Una reserva puede sobrevivir a la cuenta que la creó.
--
-- `created_by` era NOT NULL, y casi siempre es el propio estudiante — pero no siempre: el admin
-- puede reservar en nombre de alguien. Borrar definitivamente a ese admin chocaba contra esta FK,
-- y la única salida sin esta migración era destruir la clase de un tercero o mentir sobre quién la
-- creó. NULL dice la verdad: la cuenta que la creó ya no existe.
ALTER TABLE bookings ALTER COLUMN created_by DROP NOT NULL;
