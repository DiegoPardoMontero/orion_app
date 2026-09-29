# Revisión completa de los flujos del profesor, de noche, hasta las 5 a. m.

| | |
|---|---|
| **Fecha** | 28/09/2026, 23:25 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/plan-de-lanzamiento.md` (versión 2: se abre primero a profesores) y `2026-09-27-1002-abogado-terminos-y-megareview-del-profesor.md` (la megarrevisión anterior) |
| **Plazo** | Hasta el 29/09/2026 a las 05:00 (Bogotá) |
| **Estado** | Hecho; dos preguntas pendientes para Pardo |

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

Hecho el 29/09 a la 01:20, con dos preguntas pendientes para Pardo (abajo). La verificación está en
`docs/ESTADO.md`: `./mvnw verify` 451 + 684, e2e 105 y `next build`, todo en verde. Después vinieron
el push a `master` y el correo con el resumen. El terminal de Pardo se cayó a las 00:08 del 29/09 y
no se perdió nada: todo estaba en commits.

**Hecho hasta las 23:56 del 28/09:**

- Ganancias: las fechas dicen «Desde» y «Hasta», y la explicación ya no sale dos veces (`388acda`).
- Perfil público en el computador: la foto va encima del nombre y el calendario se ajusta a su contenido (`ed0ae89`).
- Postulación: en una columna, la revisión en el orden de los pasos y los ejemplos dicen «Ej.:» (`e49067e`).
- El profe que crea el admin desde Usuarios nace aprobado y puede publicar (`23069a7`).
- Los aspirantes ya no reciben los recordatorios de ficha de estudiante, que además les gastaban los del perfil (`53c9746`).
- Al equipo le llega un correo cuando entra o vuelve una postulación (`7d8dd43`).
- El filtro de disponibilidad del buscador ve las franjas unidas y las horas y media (`bb28246`).
- Un acta publicada se rechaza antes de llamar a la IA, y el correo de mensaje nuevo enlaza la
  conversación (`e0372f8`).

**Hecho el 29/09, con las decisiones de abajo:**

- **Crítico (dinero):** marcar la asistencia ya no cierra el reclamo del estudiante (`9b0ca84`).
  - Una clase cerrada, sea «asistió» o «no asistió», se puede reclamar hasta 24 h después del final,
    y el reclamo congela el pago aunque ya estuviera liberado.
  - Hay un solo reclamo por clase.
  - Si gana el profe, la clase vuelve a «no asistió» con su hora de cierre original.
  - Con un reclamo abierto, la sala sigue abierta hasta el final y la clase se queda en «Próximas»
    con su botón para entrar.
- «Enseña con Orión» y las preguntas frecuentes dicen que el 15 % de fundador es por invitación (`f78ce6c`).
- El aviso de datos de pago sigue siendo obligatorio, pero trae «Salir» y WhatsApp de soporte (`c730534`).
  - Los diálogos ya no dejan escapar el foco con Tab.
  - Tampoco le quitan el foco al campo en el que se escribe cada vez que la página se redibuja.
- La postulación en revisión o aprobada ya no se edita por la API, y las preguntas frecuentes ya no
  dicen que sí (`eeb2041`). Un profe aprobado ya no puede crear borradores sueltos.
- El profe edita su nombre y su WhatsApp en «Mi perfil», y publicar exige el correo confirmado
  (`60465dd`).
  - El perfil lo avisa junto al interruptor.
  - La barra de correo sin confirmar dice lo que de verdad bloquea.
- Sin la «nota privada» de asistencia, que nadie volvía a ver (`3facb0c`).
- La «X» de las notificaciones se ve en el celular, y abrir una notificación pregunta antes de perder
  cambios sin guardar (`a8adeb8`).
- Detalles (`dda8cb4`):
  - la ciudad escrita no se borra al salir del campo;
  - la bio y el chat conservan sus saltos de línea;
  - los valores negativos se escriben «− $5.000»;
  - un error en las fechas bloqueadas ya no se muestra como «Ninguna»;
  - el «hoy» del calendario es el de Bogotá;
  - la sesión se refresca en cuanto cambia el estado de la postulación.
- La gracia de la cancelación tardía del profe cuenta desde que la reserva se pagó (`e6ac40b`).
- El enlace a «Cómo le fue» de la ficha del estudiante baja hasta esa sección (`e860b93`).
- La descripción corta del profe se lee entera en las tarjetas de clase y en la antesala (`2bb4f65`).

Pruebas por partes, todas en verde:
- las del ciclo de la clase, pagos y liquidaciones;
- las 21 de identidad;
- 195 unitarias del frontend;
- lint y tipos.

Las pantallas cambiadas se revisaron en local a 390 y 1440 px: el perfil con «Tus datos», el aviso
de correo, el aviso de pago con «Salir», la campana y la clase reciente de Ana con «Reportar un
problema».

**Descartado tras revisarlo:**

- El saludo de Rigel sí llegó (23:34); solo faltaba que corriera el proceso.
- El límite de 10 MB del proxy de Next no aplica: el frontend no tiene `proxy.ts`, y las
  reescrituras a `/api` no lo usan.
- La nota vieja de «cambios pedidos» que sigue en una postulación reenviada no se muestra en ninguna
  pantalla del aspirante; el admin la ve como contexto.

**Pendiente de una respuesta de Pardo:**

- **Corrección de lo que se le dijo:** al preguntarle por el nombre, la opción decía que los
  comprobantes ya emitidos conservan el nombre con el que salieron. **No es así.** El comprobante del
  estudiante toma el nombre actual del profe, así que corregir el nombre lo corrige también en los
  comprobantes viejos. Las liquidaciones usan el titular de la llave Bre-B, que es otro dato.
  Congelar el nombre en cada pago exige una migración. ¿Se deja así o se congela?
- Queda sin revertir un detalle: si el reclamo se resuelve a favor del estudiante, los puntos y
  logros que ganó al registrarse la asistencia se quedan. No es dinero.
- La regla del reclamo **reemplaza lo que Pardo dijo el 28/09** («lo del reclamo no lo entiendo, sí,
  déjalo así»). Se aplica la decisión del 29/09, que tomó con el caso del minuto 15 delante; va dicho
  en el correo.
- Anotado sin tocar: en «Mis horarios», «una franja de 6 a 9 PM ya abre 5 cupos» cuenta horas de
  inicio que se pisan. Son 5 horas posibles, pero en 3 horas caben máximo 3 clases.

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
- **Nombre y WhatsApp del profe:** «Sí, en su perfil». Se editan en «Editar mi perfil». (La opción
  decía que los comprobantes ya emitidos conservan el nombre: no es cierto; ver «Pendiente».)
- **Correo sin verificar:** «Exigirlo de verdad». Un profe sin el correo verificado no puede publicar
  su perfil.
