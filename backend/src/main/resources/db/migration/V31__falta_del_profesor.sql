-- Una ausencia deja de ser solo «no se presentó».
--
-- La política de cancelación pone en el mismo saco al profesor que no llega y al que avisa con
-- menos de 12 horas: las dos dejan al estudiante sin clase el mismo día. Las dos cuentan para la
-- escalera de sanciones, pero no son lo mismo y la fila tiene que poder decir cuál fue — si no,
-- el admin lee «ausencia» sobre un profesor que sí avisó.

ALTER TABLE professor_absences
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'NO_SHOW'
    CHECK (kind IN ('NO_SHOW', 'LATE_CANCELLATION'));

-- Las que ya existían salieron todas de un reclamo resuelto: son NO_SHOW de verdad, y el default
-- ya las deja bien. Se quita el default para que a partir de ahora quien inserte lo diga.
ALTER TABLE professor_absences ALTER COLUMN kind DROP DEFAULT;
