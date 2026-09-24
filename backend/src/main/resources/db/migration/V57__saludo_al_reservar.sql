-- El saludo al reservar (24/09/2026): al estudiante le llega, en su chat con el profe, un mensaje a
-- nombre del profe con el día y la hora de la clase. Lo escribe Orión, y se dice: «automated»
-- pinta la marca «Enviado por Orión» debajo de la burbuja.
ALTER TABLE messages ADD COLUMN automated BOOLEAN NOT NULL DEFAULT FALSE;

-- A qué reserva saluda. Una sola vez por reserva: si el evento se repite (un reintento, una clase
-- movida), la constraint es la que no deja un segundo saludo.
ALTER TABLE messages ADD COLUMN booking_id UUID REFERENCES bookings (id) ON DELETE SET NULL;
CREATE UNIQUE INDEX ux_messages_saludo_por_reserva ON messages (booking_id) WHERE automated;
