# Fundador por invitación, fundador desde el admin, francés y niños

## Metadatos

- **Fecha y hora:** 2026-10-14 21:35 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Briefs relacionados:** `docs/briefs/orion-brief-profe-fundador.md`, `docs/briefs/orion-bloque-12-adolescentes.md`,
  `docs/plan-de-lanzamiento.md` (v3)
- **Commits relacionados:** (se completan a medida que avanza)
- **Artifacts relacionados:** —

## El pedido exacto

> Adelante, sin embargo, quiero que incluyas otra cosa. Quiero que verifiques que cuando los profes entran a través del enlace de invitación tienen el 15% de fundador activado.Quiero que desde el admin exista la posibilidad de dar profe fundador a los que no lo tienen. Luego, ya llegamos a la conclusión con Sofía, necesitamos hacer este primer lanzamiento CON inglés y francés, y que también incluya niños. Hazme todas las preguntas necesarias antes de empezar a implementar esto.

## Resumen ejecutivo

- **Como** admin, **quiero** la certeza de que el profe que entra por el enlace de invitación de
  fundador queda con el 15 % **para** que la promesa de la invitación se cumpla sola.
- **Como** admin, **quiero** poder dar el beneficio de fundador a un profe que no lo tiene **para**
  corregir a quien entró por otra puerta o convencer a uno que dudaba.
- **Como** estudiante, **quiero** encontrar profes de **francés** además de inglés **para** que el
  primer lanzamiento cubra los dos idiomas que la academia enseña.
- **Como** padre o madre, **quiero** que mi hijo menor de edad pueda tomar clases en Orión **para**
  que el lanzamiento incluya **niños** (decisión de Pardo y Sofía).

Features: verificación del fundador por invitación (sin código si ya funciona); fundador desde el
admin (comprobar si ya existe); francés en el catálogo, la postulación, el diagnóstico, el acta y la
práctica; menores de edad con autorización del representante legal.

## Lo que ya existía (comprobado en el código y sus pruebas, 14/10)

- **Fundador por invitación:** el 15 % se otorga cuando el admin **aprueba** la postulación de quien
  entró por una invitación marcada «fundador» (`TeacherApplicationService` → `FounderService.grantOnApproval`;
  probado en `InvitacionDeProfesoresIT`, incluida la invitación por WhatsApp y, desde hoy, la que se
  consume al entrar con Google). La casilla «fundador» viene marcada por defecto al invitar.
- **Fundador desde el admin:** en Usuarios, cada profe aprobado tiene el botón «Hacer fundador» /
  «Quitar fundador» (`POST`/`DELETE /api/v1/admin/professors/{id}/founder`, brief del profe fundador,
  paso 2). No se ve en los aspirantes todavía en revisión.
- **Francés:** el modelo multiidioma existe y está apagado desde la V42 (15/09): `languages.is_active`,
  `professor_languages`, `professor_language_levels`, `bookings.language_code`. Meissa (`ScenarioPrompts`),
  el acta y la práctica ya reciben el código del idioma; el frontend quitó los selectores y dice
  «inglés» en 28 archivos.
- **Adolescentes:** `docs/briefs/orion-bloque-12-adolescentes.md` (propuesta del 25/09, con sus
  decisiones: 13 a 17, sin usuario propio, sin IA, sin grabación) y los borradores legales en
  https://claude.ai/artifact/PKrHkCLJ6zCfTq7Q48RiCF.

## Decisiones de Pardo (14/10/2026, 21:40–21:55, eligiendo entre opciones)

1. **Fundador:** «Nada: así está bien». Se documenta; no se toca código.
2. **Francés, alcance:** «Todo: catálogo, Meissa, acta y práctica».
3. **Edades:** «De 13 a 17, como el Bloque 12».
4. **Abogado:** «Ya la aprobó tal cual» (la propuesta del 25/09 y sus borradores).
5. **Orden:** «Profes ya, con francés; adolescentes antes de abrir a estudiantes». Esta semana se
   cierra la casa y se reabre el francés; se invita a profes de inglés y francés desde el 19/10;
   adolescentes queda listo antes de que entren estudiantes que pagan.
6. **Profes habilitados para adolescentes:** «El admin verifica y lo marca en Usuarios»: el profe
   acepta el código de conducta y sube la consulta del Registro de inhabilidades y los antecedentes;
   el admin los revisa y enciende «Habilitado para adolescentes», con renovación anual.
7. **Textos legales 1.2:** «Publicar 1.2 y pedir que la acepten al entrar». La 1.2 trae el capítulo
   de adolescentes y, en la Política, OpenAI, 8x8 y el login social; se arregla de paso que publicar
   una versión nueva sí pida aceptarla (cláusula 15).
8. **Franja horaria para adolescentes:** «Sin franja: cualquier cupo del profe».
9. **Política 1.2 (22:55):** «Las dos cosas, publicar ya». La 1.2 lleva el capítulo de adolescentes **y**
   el párrafo de OpenAI, 8x8 y el login social, y se publica sin que el abogado haya visto ese
   párrafo. Claude lo recomendó en contra (el párrafo de datos no tiene revisión jurídica); Pardo lo
   reafirmó eligiendo la opción. Queda anotado para que el abogado lo lea en la siguiente ronda.

## Estado

**En curso.** Semana 0 terminada. **Francés hecho** (`304a9fc`; falta probar Meissa, acta y práctica
en francés con la llave real). Sigue adolescentes (Bloque 12 con las decisiones de hoy).
