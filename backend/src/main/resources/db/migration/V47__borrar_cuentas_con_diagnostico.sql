-- Dos claves foráneas de la V34 que impedían borrar cuentas.
--
-- ai_usage_log.actor_id: desde que cada sesión de voz se carga al abrirse, casi todo estudiante que
-- probó el diagnóstico tiene filas aquí. Sin regla de borrado, la purga de su cuenta fallaba. El
-- gasto se queda —cuenta para el tope del día y las cifras del mes— y solo pierde a quién se cargó.
ALTER TABLE ai_usage_log
    DROP CONSTRAINT ai_usage_log_actor_id_fkey,
    ADD CONSTRAINT ai_usage_log_actor_id_fkey
        FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL;

-- assessment_recommendations.professor_id: un profesor recomendado alguna vez no se podía borrar.
-- La recomendación vive en el resultado de otra persona, que se queda con las restantes.
ALTER TABLE assessment_recommendations
    DROP CONSTRAINT assessment_recommendations_professor_id_fkey,
    ADD CONSTRAINT assessment_recommendations_professor_id_fkey
        FOREIGN KEY (professor_id) REFERENCES users(id) ON DELETE CASCADE;
