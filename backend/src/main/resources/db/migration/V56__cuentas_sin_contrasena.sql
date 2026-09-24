-- Quien entra con Google (o Microsoft) nace con una contraseña al azar que nadie conoce, y
-- «Cambiar contraseña» le pedía la actual: un callejón sin salida (24/09/2026). La marca dice si
-- la contraseña la eligió la persona; si no, la pantalla ofrece crear una sin pedir la actual.
ALTER TABLE users ADD COLUMN password_set BOOLEAN NOT NULL DEFAULT TRUE;

-- Las cuentas que nacieron desde un proveedor: su identidad social se creó junto con la cuenta.
-- Las que ya existían y solo se vincularon conservan la contraseña que tenían.
UPDATE users u
   SET password_set = FALSE
 WHERE EXISTS (SELECT 1
                 FROM social_identities s
                WHERE s.user_id = u.id
                  AND abs(extract(epoch FROM s.created_at - u.created_at)) < 120);

-- Los profesores invitados que todavía no aceptaron tampoco eligieron una: la eligen al aceptar.
UPDATE users u
   SET password_set = FALSE
 WHERE NOT EXISTS (SELECT 1 FROM professor_invites i WHERE i.user_id = u.id AND i.used_at IS NOT NULL)
   AND EXISTS (SELECT 1 FROM professor_invites i WHERE i.user_id = u.id);
