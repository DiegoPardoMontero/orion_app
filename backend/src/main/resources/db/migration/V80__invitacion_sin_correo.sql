-- Invitar sin correo (Pardo, 29/09/2026): de muchos profes solo se tiene el WhatsApp. El admin
-- escribe el nombre, Orión le da el enlace y él lo manda por WhatsApp. Sigue siendo un enlace por
-- persona, de un solo uso y con 7 días, así que el beneficio de fundador no se reparte solo.
-- Sin correo, el invitado escribe el suyo al registrarse y lo confirma como cualquiera: el enlace no
-- llegó a ningún buzón que pruebe que es suyo.
ALTER TABLE professor_invites ALTER COLUMN email DROP NOT NULL;
