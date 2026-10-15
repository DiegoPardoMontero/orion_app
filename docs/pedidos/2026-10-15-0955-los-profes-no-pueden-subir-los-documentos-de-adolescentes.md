# Los profes no pueden subir el certificado de antecedentes ni la consulta del Registro

## Metadatos

- **Fecha y hora:** 2026-10-15 09:55 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Commits relacionados:** `1012726`; desplegado a `master` el 15/10 a las 13:59 con la autorización de Pardo («Sí, hagamos ese despliegue»), con las pruebas de integración del tema en verde y el verify completo corriendo después
- **Contexto:** producción con profes reales reportando el fallo.

## El pedido exacto

> Me dijeron que no pudieron subir los documentos de Antecedentes ni el otro que se requiere para lo de los adolsecentes. Ayudame con eso, revisa esa lógica, verifica que se pueda y ajusta esos cambios.

## Resumen ejecutivo

- **Como** profe, **quiero** subir la consulta del Registro de inhabilidades y el certificado de
  antecedentes desde «Clases con adolescentes» **para** que el admin me habilite.

## Qué pasaba

La sección «Clases con adolescentes» de Mi perfil subía los dos certificados por el endpoint de la
**postulación** (`POST /me/teacher-application/documents`), y ese endpoint exige que la postulación se
pueda editar. Un profe **aprobado** ya no puede editarla, así que recibía un 409: «Tu postulación ya
fue aprobada: tu perfil se edita en "Mi perfil"». Es decir: ningún profe real podía subirlos. Las pruebas
no lo vieron porque guardaban los documentos directo en la base. De paso, el tope de seis documentos
por postulación también podía bloquear a un profe que ya los tuviera todos.

## Cómo quedó

- Endpoints propios en Mi perfil: `POST /api/v1/me/profile/minors/documents` (archivo + tipo
  `REGISTRY_CHECK` o `BACKGROUND_CHECK`) y `DELETE /api/v1/me/profile/minors/documents/{id}`. No pasan
  por la postulación; devuelven la habilitación actualizada.
- Los dos certificados **no cuentan** en el tope de seis de la postulación; tienen el suyo (tres
  archivos por tipo).
- La habilitación (`GET /me/profile/minors`) ahora trae los certificados subidos; la pantalla los lee de
  ahí, no de la postulación (un profe creado por el admin ni siquiera tiene postulación).
- Prueba de integración nueva: un profe aprobado con seis documentos sube los dos certificados por el
  endpoint real, los ve en su habilitación y quita uno; el de otro profe responde 404.

## Estado

**Desplegado** (15/10 13:59).
