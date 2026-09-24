-- La pista del «Casi…» (diseño de la práctica, 24/09/2026). Hasta ahora el primer fallo mostraba
-- la explicación, que casi siempre dice la respuesta; la pista ayuda a acertar sin darla, y la
-- explicación queda para cuando el ejercicio se cierra.
ALTER TABLE practice_items ADD COLUMN hint VARCHAR(400);
