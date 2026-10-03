-- La reseña de una clase de prueba gratis ya no cuenta en el promedio ni en el ranking (Pardo,
-- 01/10/2026): el estudiante la puede dejar y el profe la ve, pero no mueve sus estrellas. El código
-- ya la excluye al recalcular (ReviewRepository.aggregateVisible); esto pone al día los profes que
-- ya tenían una, sin esperar a su próxima reseña. Misma regla: visibles y de clases que no son de
-- prueba, y el promedio redondeado a dos decimales.
UPDATE professor_metrics m
   SET rating_avg   = s.promedio,
       rating_count = s.total,
       computed_at  = now()
  FROM (SELECT p.professor_id,
               round(avg(r.rating)::numeric, 2) AS promedio,
               count(r.id)                     AS total
          FROM professor_metrics p
          LEFT JOIN reviews r
                 ON r.professor_id = p.professor_id
                AND r.is_visible
                AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.id = r.booking_id AND b.is_trial)
         GROUP BY p.professor_id) s
 WHERE m.professor_id = s.professor_id;
