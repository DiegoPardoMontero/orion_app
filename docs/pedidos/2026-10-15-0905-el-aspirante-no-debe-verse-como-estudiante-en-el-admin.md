# En el panel del admin, quien se postula debe verse como «Aspirante a profesor», no como estudiante

## Metadatos

- **Fecha y hora:** 2026-10-15 09:05 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Commits relacionados:** `4d8da65` (en `develop`, sin desplegar)

## El pedido exacto

> Mientras el profe se postula aparece como estudiante? Mira bien eso, me refiero al panel del admin, porque si es así, necesito que aparezca como "Aspirante a Profesor", de lo contrario me confundirías enormemente.

## Resumen ejecutivo

- **Como** admin, **quiero** que quien entró por «Quiero enseñar» y todavía no está aprobado se vea
  como **«Aspirante a profesor»** en Usuarios (y en cualquier otra pantalla del admin) **para** no
  confundirlo con un estudiante.

## Qué pasaba

Sí: quien entra por «Quiero enseñar» es, en la base, un **estudiante con intención de enseñar**
(`role = STUDENT`, `signup_intent = TEACH`); para él mismo Orión lo trata como aspirante, pero la
pantalla **Usuarios** del admin leía solo el rol y lo mostraba como «Estudiante». Al aprobarlo pasa a
«Profesor».

## Cómo quedó

- En Usuarios el aspirante se ve con la etiqueta **«Aspirante a profesor»** (en durazno, distinta de
  estudiante y profesor).
- Filtro nuevo **«Aspirantes a profesor»**, y el filtro **«Estudiantes» ya no los incluye**.
- La API del admin devuelve `teacherApplicant` y acepta `role=TEACHER_APPLICANT`.
- Postulaciones (la otra pantalla del admin) ya los trataba como aspirantes: no cambia.

## Estado

**Hecho en `develop`, sin desplegar**, con el lote del 15/10.
