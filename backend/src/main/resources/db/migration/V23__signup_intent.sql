-- Con qué intención se creó la cuenta.
--
-- Hasta ahora, quien se registraba desde «Postúlate para dar clases» nacía exactamente igual que
-- un estudiante: mismo rol, mismo menú, mismo acceso a reservar clases con otros profesores. La
-- postulación quedaba a un lado, como un trámite paralelo, y la cuenta se comportaba como si nunca
-- hubiera existido.
--
-- Esta columna es lo que distingue las dos puertas de entrada. No sustituye al rol —el rol sigue
-- diciendo qué es la cuenta hoy— sino que dice a qué vino, que es lo que decide qué ve mientras
-- espera una decisión.
ALTER TABLE users
    ADD COLUMN signup_intent VARCHAR(10) NOT NULL DEFAULT 'LEARN'
        CHECK (signup_intent IN ('LEARN', 'TEACH'));

-- Las cuentas que ya existen se quedan en LEARN a propósito, incluidas las de estudiantes que
-- postularon para enseñar. Marcarlas TEACH les cerraría hoy mismo las clases que ya tienen
-- reservadas y el saldo que ya pagaron. La regla nueva vale para quien se registre de ahora en
-- adelante; a quien ya está dentro no se le quita nada.
