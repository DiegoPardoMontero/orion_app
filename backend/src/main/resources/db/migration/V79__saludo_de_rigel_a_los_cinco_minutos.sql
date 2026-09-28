-- El primer saludo de Rigel en «Mensajes» llega unos minutos después de la primera entrada, y no
-- en el mismo instante (Pardo, 28/09/2026: «a los 5 minutos»). Al entrar, el profe recién aprobado
-- tiene delante la bienvenida y el recorrido; un mensaje sin leer ahí compite con ellos.
--
-- El saludo se sigue guardando la primera vez que la persona pide su hilo, pero con la fecha de
-- cuando debe verse; hasta entonces el hilo no lo muestra ni lo cuenta. Cero lo deja como antes.
INSERT INTO platform_settings (key, value) VALUES ('rigel_welcome_delay_minutes', '5')
ON CONFLICT (key) DO NOTHING;
