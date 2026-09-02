-- Gate del Bloque 2: un profesor sin postulación APPROVED no aparece en el marketplace. Los
-- profesores que YA existían antes de este flujo (alta por invitación del piloto) no tienen
-- postulación y desaparecerían al desplegar. Aquí se les crea una APPROVED de alta histórica.
-- En una base nueva (tests) esto no selecciona nada: es idempotente y seguro.
INSERT INTO teacher_applications (user_id, status, submitted_at, reviewed_at, decision_note, created_at, updated_at)
SELECT u.id, 'APPROVED', now(), now(), 'Alta histórica: profesor previo al flujo de postulación', now(), now()
FROM users u
WHERE u.role = 'PROFESSOR'
  AND NOT EXISTS (SELECT 1 FROM teacher_applications ta WHERE ta.user_id = u.id);

INSERT INTO teacher_application_events (application_id, event_type, actor_id, note, created_at)
SELECT ta.id, 'APPROVED', NULL, ta.decision_note, now()
FROM teacher_applications ta
WHERE ta.decision_note = 'Alta histórica: profesor previo al flujo de postulación'
  AND NOT EXISTS (SELECT 1 FROM teacher_application_events e WHERE e.application_id = ta.id);
