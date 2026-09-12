-- Antelación mínima para reservar.
--
-- Hasta ahora se podía agendar una clase para dentro de veinte minutos: el cupo aparecía libre y
-- nadie lo impedía. Eso deja al profesor sin margen para prepararse y, si no ve el aviso, con un
-- estudiante esperando en una sala a la que nadie le dijo que entrara.
--
-- Seis horas y no doce: la frontera que decide el dinero al cancelar son doce, así que exigir doce
-- para reservar haría que toda reserva naciera ya dentro de la franja sin devolución — quien
-- reservara y se arrepintiera al minuto lo perdería todo.
INSERT INTO platform_settings (key, value) VALUES ('booking_min_lead_hours', '6')
ON CONFLICT (key) DO NOTHING;
