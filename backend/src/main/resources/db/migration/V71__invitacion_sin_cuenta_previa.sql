-- La invitación de profesores ya no crea la cuenta (decisión de Pardo del 25/09/2026).
--
-- Antes, invitar creaba al profesor inactivo («Profesor invitado») y el enlace lo activaba ya
-- aprobado, sin revisión y sin aceptar los Términos. Ahora la invitación guarda el correo, el nombre
-- con el que se saluda al profe, quién lo invita y si trae el beneficio de fundador; el invitado crea
-- su cuenta en el registro de profesor —acepta Términos y política ahí—, lleva su postulación y
-- Sofía la aprueba. El token se consume al crear la cuenta, que queda en user_id.

-- «Te invita Sofía, directora académica»: el cargo lo escribe cada admin una vez.
ALTER TABLE users ADD COLUMN job_title VARCHAR(80);

ALTER TABLE professor_invites
    ADD COLUMN email          VARCHAR(254),
    ADD COLUMN professor_name VARCHAR(80),
    ADD COLUMN invited_by     UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN founder        BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE professor_invites i
   SET email = lower(u.email)
  FROM users u
 WHERE u.id = i.user_id;

ALTER TABLE professor_invites ALTER COLUMN email SET NOT NULL;
ALTER TABLE professor_invites ALTER COLUMN user_id DROP NOT NULL;
CREATE INDEX idx_professor_invites_email ON professor_invites (email);

-- Las invitaciones pendientes del modelo viejo crearon una cuenta que nadie activó. Se sueltan de
-- ella y la cuenta se borra: el mismo enlace sigue sirviendo y ahora lleva al registro, donde esa
-- persona crea su cuenta de verdad. Solo se toca la cuenta que fabricó el modelo viejo —«Profesor
-- invitado», sin contraseña puesta, inactiva y sin clases—: un profesor real desactivado por el admin
-- no cumple eso y se queda como está.
CREATE TEMPORARY TABLE cuentas_de_invitacion AS
SELECT u.id
  FROM users u
  JOIN professor_invites i ON i.user_id = u.id
 WHERE i.used_at IS NULL
   AND u.role = 'PROFESSOR'
   AND u.status = 'INACTIVE'
   AND u.password_set = FALSE
   AND u.full_name = 'Profesor invitado'
   AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.professor_id = u.id);

UPDATE professor_invites
   SET user_id = NULL
 WHERE user_id IN (SELECT id FROM cuentas_de_invitacion);

DELETE FROM users
 WHERE id IN (SELECT id FROM cuentas_de_invitacion);

DROP TABLE cuentas_de_invitacion;
