-- Un estudiante no postula a profesor desde su cuenta (Pardo, 25/09/2026), y las postulaciones que
-- ya existían así se borran «como si nunca hubiera pasado».
--
-- Se borran las de toda cuenta que hoy es de estudiante (rol STUDENT, intención LEARN), en cualquier
-- estado. Eso incluye a los aspirantes rechazados: al rechazarlos, su cuenta vuelve a ser de
-- estudiante y en la base no se distinguen de quien postuló con el botón (decidido por Pardo).
-- No se toca a nadie que haya tenido una postulación aprobada: esa cuenta fue de profesor.
--
-- Con la postulación se va lo que ella dejó: su bitácora (en cascada), los documentos, el acuerdo del
-- profesor aceptado y el perfil de profesor que el formulario llenaba al avanzar. Los archivos de los
-- documentos en Cloudinary no los alcanza una migración y quedan huérfanos allá.

create temporary table postularon_siendo_estudiantes as
select distinct a.user_id
from teacher_applications a
join users u on u.id = a.user_id
where u.role = 'STUDENT'
  and u.signup_intent = 'LEARN'
  and not exists (select 1 from teacher_applications aprobada
                  where aprobada.user_id = a.user_id and aprobada.status = 'APPROVED');

delete from teacher_documents
where user_id in (select user_id from postularon_siendo_estudiantes);

delete from teacher_applications
where user_id in (select user_id from postularon_siendo_estudiantes);

delete from agreement_acceptances
where document_code = 'TEACHER_AGREEMENT'
  and user_id in (select user_id from postularon_siendo_estudiantes);

delete from professor_metrics
where professor_id in (select user_id from postularon_siendo_estudiantes);

-- Idiomas, niveles y objetivos del perfil se van en cascada.
delete from professor_profiles
where user_id in (select user_id from postularon_siendo_estudiantes);

drop table postularon_siendo_estudiantes;
