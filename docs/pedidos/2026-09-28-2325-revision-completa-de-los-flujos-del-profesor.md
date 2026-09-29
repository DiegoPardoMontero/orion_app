# Revisión completa de los flujos del profesor, de noche, hasta las 5 a. m.

| | |
|---|---|
| **Fecha** | 28/09/2026, 23:25 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/plan-de-lanzamiento.md` (versión 2: se abre primero a profesores) y `2026-09-27-1002-abogado-terminos-y-megareview-del-profesor.md` (la megarrevisión anterior) |
| **Plazo** | Hasta el 29/09/2026 a las 05:00 (Bogotá) |
| **Estado** | En curso |

## El pedido, tal cual

> Trabaja en los flujos del profesor, busca bugs, quiero que TODO quede perfecto, si seguimos este plan. ¿Puedes hacer esta revisión completa? La quiero a nivel de código, pantallas y alto nivel de calidad visual. Voy a dormir, tienes hasta las 5am, hora Colombia para trabajar en esto.

## Resumen ejecutivo

- **Historia:** como profe fundador que llega en la primera semana del lanzamiento, recorro todo el
  camino sin un solo tropiezo y con pantallas que se ven profesionales:
  - «Enseña con Orión» o la invitación;
  - el registro y la postulación;
  - la revisión y la primera entrada;
  - el perfil, los horarios, los datos de pago e invitar estudiantes;
  - la reserva, la clase, el acta y la práctica;
  - los mensajes, las ganancias, las liquidaciones, el desempeño y la ayuda.
- **Feature:** revisión completa del lado del profesor, en tres niveles:
  - **código** (frontend y backend);
  - **pantallas** (recorridas en el navegador, en celular y computador, en estados vacíos y con datos);
  - **calidad visual**.

  Se arregla todo lo que aparezca, con pruebas, y se deja lo que necesite una decisión de Pardo.
- **Cómo se trabaja:** en serie (el equipo se cae con muchos procesos a la vez); commits por arreglo;
  `./mvnw verify`, e2e y `next build` en verde antes del push; correo con el resumen al final.

## Estado

En curso. El terminal de Pardo se cayó a las 00:08 del 29/09; nada se perdió (todo estaba en commits).

**Hecho hasta las 23:56** (commits locales, sin push):

- Ganancias: las fechas dicen «Desde» y «Hasta», y la explicación ya no sale dos veces (`388acda`).
- Perfil público en el computador: la foto va encima del nombre y el calendario se ajusta a su contenido (`ed0ae89`).
- Postulación: en una columna, la revisión en el orden de los pasos y los ejemplos dicen «Ej.:» (`e49067e`).
- El profe que crea el admin desde Usuarios nace aprobado y puede publicar (`23069a7`).
- Los aspirantes ya no reciben los recordatorios de ficha de estudiante, que además les gastaban los del perfil (`53c9746`).
- Al equipo le llega un correo cuando entra o vuelve una postulación (`7d8dd43`).
- El filtro de disponibilidad del buscador ve las franjas unidas y las horas y media (`bb28246`).
- Un acta publicada se rechaza antes de llamar a la IA, y el correo de mensaje nuevo enlaza la
  conversación (`e0372f8`).

La verificación dirigida (9 pruebas de integración y 3 unitarias) terminó en verde a las 00:10:
77 de integración y 36 unitarias, sin fallos. Faltan el `./mvnw verify` completo, los e2e y `next build`.

**Pendiente:**

- Backend, **crítico (dinero)**: al marcar asistencia (incluso «no asistió» a los 15 min sin haber
  entrado a la sala) el pago queda liberado y el estudiante ya no puede reclamar ni entrar. Un pago
  liberado tampoco se puede reembolsar, así que Orión terminaría pagando dos veces.
- Backend, menores: la gracia de la cancelación tardía se cuenta desde que se crea la reserva y no
  desde que se confirma. Además, la postulación se puede editar en revisión, la nota de «cambios» vieja
  sigue apareciendo al reenviar, y un profe aprobado puede crear borradores sueltos por la API.
- Frontend, **alto**: el diálogo de datos de pago no tiene salida (ni «Salir» ni soporte).
- Frontend, medios:
  - solo hay 9 indicativos de país;
  - «Enseña con Orión» promete el 15 % a todos, pero solo se da por invitación;
  - el campo de ciudad borra lo escrito;
  - el profe no puede cambiar su nombre ni su WhatsApp;
  - la «X» de las notificaciones no se ve en el celular y borra al tocar.
- Frontend: 10 menores (nota de asistencia que nunca se vuelve a mostrar, saltos de línea de la bio
  y del chat, «$-5.000», preguntas frecuentes, etc.).
- Al final: ESTADO, push y el correo con el resumen.

Revisado sin hallazgo: el saludo de Rigel sí llegó (23:34), solo faltaba que corriera el proceso.

## Decisiones de Pardo (29/09/2026, 00:20)

Contestadas en el chat, eligiendo entre opciones:

- **Asistencia y reclamos:** «Reclamo abierto 24 h». El estudiante puede reclamar dentro de su ventana
  aunque el profe ya haya marcado asistencia, y el reclamo congela el pago hasta que el admin decida.
  «No asistió» no exige, por ahora, que el profe haya estado en la sala.
- **Fundador 15 %:** «Decir “por invitación”». La página dice que el 15 % es para los fundadores
  invitados; la regla de dinero no cambia.
- **Indicativos del WhatsApp:** «No, por ahora solo esos». Se quedan los 9 países: el lanzamiento
  recluta profes en Colombia.

Segunda tanda, 29/09/2026 (00:25), también eligiendo entre opciones:

- **Aviso de datos de pago:** «Obligatorio + Salir y soporte». Sigue sin poder cerrarse para usar la
  app, pero trae «Salir» y el enlace a WhatsApp de soporte.
- **Postulación en revisión:** «Bloqueada en revisión». Solo se edita en borrador o cuando el equipo
  pide cambios. Se cierra también en la API y se corrigen las preguntas frecuentes.
- **Nota privada de la hoja de cierre:** «Quitar el campo». Lo que importa de la clase ya va en el acta.
- **Sala con un reclamo abierto:** «Abierta hasta el final». Los dos pueden entrar hasta que termine la
  hora de la clase.
- **Nombre y WhatsApp del profe:** «Sí, en su perfil». Se editan en «Editar mi perfil»; los
  comprobantes ya emitidos conservan el nombre con el que salieron.
- **Correo sin verificar:** «Exigirlo de verdad». Un profe sin el correo verificado no puede publicar
  su perfil.
