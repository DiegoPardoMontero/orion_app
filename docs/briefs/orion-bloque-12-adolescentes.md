# Bloque 12 · Orión para adolescentes (13 a 17 años) — PROPUESTA

> 25/09/2026 · Decide: Pardo · Propone: Claude · **Estado (14/10/2026): APROBADA por el abogado tal
> cual** (Pardo, pedido `docs/pedidos/2026-10-14-2135-…`). Entra en el primer lanzamiento, **antes de
> abrir a estudiantes que pagan**. Las cuentas siguen siendo de mayores de 18: el adolescente es un
> perfil bajo la cuenta del adulto.

La propuesta completa, con los borradores legales y las preguntas para el abogado, está publicada
como página: https://claude.ai/artifact/PKrHkCLJ6zCfTq7Q48RiCF

## Decisiones de Pardo (25/09)

1. **Edades: de 13 a 17 años.** Menores de 13 siguen fuera.
2. **El adolescente no tiene usuario propio.** Entra desde la cuenta de su padre, madre o tutor, que es
   el titular: acepta, paga, reserva y habla con el profe.
3. **Sin IA al lanzar.** Nada del adolescente va a OpenAI: ni Meissa, ni el acta ni la práctica generadas.
4. **Sin grabación**, y el adulto confirma en cada clase que está presente o cerca.

## Decisiones de Pardo (14/10/2026), que completan las del 25/09

5. **Edades: de 13 a 17**, confirmado. Menores de 13 siguen fuera.
6. **Profes habilitados: el admin verifica y lo marca en Usuarios.** El profe acepta el código de
   conducta (con constancia, como el acuerdo del profesor) y sube dos documentos: la consulta del
   Registro de inhabilidades por delitos sexuales contra menores (Ley 1918 de 2018) y el certificado de
   antecedentes judiciales. El admin los revisa y enciende «Habilitado para adolescentes», con
   **renovación anual** (la propuesta decía 6 meses; Pardo eligió un año). Vencida, el profe deja de
   aparecer para adolescentes.
7. **Sin franja horaria.** Cualquier cupo del profe sirve; el adulto decide. (La propuesta traía 6:00
   a 21:00; descartada.)
8. **Textos legales 1.2, pedidos al entrar.** Términos 1.2 y Política 1.2 con el capítulo de
   adolescentes; la autorización del representante legal y el código de conducta como documentos
   nuevos versionados. `pendientesAlEntrar` ya pide al entrar lo que no se ha aceptado, así que
   publicar la 1.2 basta para que todos la acepten. **Decidido (14/10, 22:55):** la Política 1.2 lleva
   también el párrafo de OpenAI, 8x8 y login social, sin revisión del abogado, por decisión de Pardo
   («las dos cosas, publicar ya»); se le manda al abogado para la siguiente ronda.

9. **Prueba gratis: una por cada hijo** (14/10, 23:20). El índice `ux_bookings_una_prueba_por_pareja`
   cuenta también el perfil del adolescente.
10. **Puntos propios por hijo, visibles solo al adulto** (14/10, 23:20). Cada perfil de adolescente
    lleva puntos, racha y logros; se ven dentro de la cuenta del adulto y nunca en público. Se
    construye después del núcleo (perfiles, autorización, habilitación, reserva, aula): es la última
    pieza del bloque.

## La propuesta, en corto

- **Cuenta familiar**: perfiles de adolescente dentro de la cuenta del adulto, con nombre de pila, año de
  nacimiento, nivel y objetivos. Sin foto, correo, teléfono, documento ni colegio. Nunca públicos.
- **Autorización del representante legal** (Ley 1581 art. 7; Decreto 1377 art. 12): pantalla aparte,
  texto versionado, casilla propia, «el adolescente fue escuchado», constancia (versión, fecha, IP) y
  revocación con borrado.
- **Profes habilitados para adolescentes**: consulta del Registro de inhabilidades por delitos sexuales
  contra menores (Ley 1918 de 2018), antecedentes, código de conducta aceptado y renovación periódica.
  Solo ellos aparecen para un perfil de adolescente, y la reserva lo comprueba en el servidor.
- **La clase**: se abre desde la cuenta del adulto, con su confirmación; sin grabación; franja horaria
  propuesta de 6:00 a. m. a 9:00 p. m.
- **Mensajes y reseñas**: siempre con el adulto; el nombre del adolescente no aparece en nada público.
- **Seguridad**: «Reportar un problema de seguridad» con prioridad máxima, inhabilitación inmediata del
  profe, conservación de evidencia y denuncia según el protocolo que defina el abogado.
- **Textos legales**: capítulo en Términos y Política, autorización del representante y código de
  conducta del profe (borradores en la página).

## Cómo se construye (plan del 14/10/2026, noche)

**Estado al 15/10 por la mañana:** pasos 1 a 9 hechos (`64833d2`, `d31b0c7`, `e47c19b` y el del
reporte de seguridad); faltan el 10 (puntos por hijo) y el 11 (los 18 años y la portada).

El principio que ordena todo: **la reserva sigue siendo del adulto.** `bookings.student_id` es el
representante legal; el adolescente es una columna más (`adolescent_profile_id`, nula para los
adultos). Así pagos, saldo, reclamos, retracto, reseñas, mensajes y liquidaciones no cambian: todos
cuelgan de `student_id`. Lo del menor son tres puertas en el servidor y una capa de pantallas.

| Paso | Qué | Dónde |
|---|---|---|
| 1 | **V83**: `adolescent_profiles` (id, guardian_user_id, first_name, birth_year con CHECK de 13–17 al crear, self_declared_level, motivation, created_at, closed_at); `guardian_authorizations` (adolescent_profile_id, document_version, heard_confirmed, ip, user_agent, accepted_at, revoked_at); `bookings.adolescent_profile_id`; `professor_profiles.minors_cleared_until/at/by`; `teacher_documents.doc_type` admite `REGISTRY_CHECK` y `BACKGROUND_CHECK`; `legal_documents.code` admite `GUARDIAN_AUTHORIZATION` y `MINORS_CODE_OF_CONDUCT`; el índice de la prueba gratis cuenta el perfil | `identity`, `scheduling`, `legal` |
| 2 | **Perfiles y autorización**: `GET/POST/PATCH/DELETE /api/v1/me/adolescents`; crear exige aceptar la autorización (versión vigente) y «fue escuchado»; borrar = revocar + supresión de lo suyo (lo de pagos se queda); `AdolescentProfileIT` | `identity` |
| 3 | **Profes habilitados**: el profe sube los dos documentos y acepta el código de conducta (`legal_acceptances`); el admin ve los tres en Usuarios y enciende «Habilitado para adolescentes» (`POST/DELETE /api/v1/admin/professors/{id}/minors`), vigente un año; el perfil público dice si está habilitado; `HabilitacionParaAdolescentesIT` | `identity`, `admin` |
| 4 | **Las tres puertas**: buscador `?forMinor=true` solo devuelve habilitados vigentes; `BookingService.create` con `adolescentProfileId` comprueba que el perfil es del actor, que la autorización está viva, que el profe está habilitado y que el perfil sigue con menos de 18; el aula no entrega el token de una clase de adolescente hasta que el adulto confirma «estoy presente o cerca» (`POST /bookings/{id}/classroom/guardian-present`, `guardian_present_confirmed_at`) | `identity`, `scheduling` |
| 5 | **Sin IA**: `PracticeListener` genera con `GeneradorSinIa` cuando la reserva tiene perfil; el borrador por IA y el dictado del acta responden 422 para esas clases (el profe la escribe a mano); nada del menor sale a OpenAI | `practice`, `teaching` |
| 6 | **Lo que ve cada uno**: Mis clases dice «Para <nombre>»; el profe ve el perfil del adolescente (nombre, año, nivel, objetivo) en `/estudiantes/[id]` y en la antesala; los avisos, mensajes y reseñas siguen con el adulto; el nombre del menor no aparece en ninguna reseña pública | `scheduling`, `messaging`, `reputation` |
| 7 | **Pantallas**: en `/cuenta`, «Adolescentes a mi cargo» (crear con la autorización en pantalla aparte, editar, revocar); al reservar, «¿Para quién es la clase?» cuando hay perfiles y el profe está habilitado; en Usuarios, el interruptor y los documentos; en Mi perfil del profe, la sección «Clases con adolescentes» con los documentos y el código | frontend |
| 8 | **Textos legales 1.2** (`terms-1.2.md`, `privacy-1.2.md`, `guardian-authorization-1.0.md`, `minors-code-of-conduct-1.0.md`), publicados por migración con la fecha; `pendientesAlEntrar` se encarga de pedirlos; `PoliticaDeTratamientoTest` apunta a la 1.2 | `legal` |
| 9 | **Seguridad**: «Reportar un problema de seguridad» para el adulto en la clase y en los mensajes → ticket de soporte con prioridad máxima, inhabilitación inmediata del profe para menores y alerta al admin | `support`, `identity` |
| 10 | **Puntos propios por hijo** (decisión 10): contador, racha y logros por perfil, visibles solo al adulto | `engagement` |
| 11 | **Al cumplir 18 y la portada**: aviso al adulto; una sección para familias en la portada cuando todo lo anterior esté en producción | `engagement`, frontend |

Fuera del plan hasta que lo pida Pardo: menores de 13, usuario propio del adolescente, IA con sus
datos, grabación.

## Fuera

Menores de 13, usuario propio del adolescente, IA con sus datos y grabación de clases.
