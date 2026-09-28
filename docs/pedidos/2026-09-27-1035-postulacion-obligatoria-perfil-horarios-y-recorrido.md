# La postulación con todo obligatorio, el perfil visible arriba, horarios arrastrando y un recorrido más rápido

| | |
|---|---|
| **Fecha** | 27/09/2026, 10:35 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code, mientras seguía la megarrevisión del flujo del profesor |
| **Quién** | Pardo |
| **Relacionado** | `2026-09-27-1002-abogado-terminos-y-megareview-del-profesor.md` (los arreglos de esa revisión van juntos) |
| **Estado** | Hecho (28/09/2026) |

## El pedido, tal cual

> Te pido ya cosas directamente del proceso de postulación. No permitas avanzar hasta el final sin llenar campos obligatorios, es mala UX que dejemos que avance sin foto y al final pedirle la foto y que tenga que devolverse 6 pantallas. Pidele todo de manera obligatoria. En País y Ciudad, haz un catálogo, es decir, un dropdown donde pueda escribir y se busque automáticamente. Y primero debe colocar país y luego le aparece para colocar la ciudad. Recuerda que todos los campos son obligatorios. Quiero que lo del "Perfil Visible" aparezca mejor arriba, que la persona sepa fácilmente si está visible o no y lo pueda cambiar, pero que esté inmediatamente abajo de la tarifa. Que puedan darle un botón y puedan ver una preview sencilla de cómo se vería su perfil. Que la contraseña al crear cuenta, tenga que ser fuerte al menos. Fuerte o Excelente. Así como en ayuda, y en otras secciones de la página, necesito que utilices todo o casi todo el espacio de ancho, por favor. Con lo que te dije de que TODO es obligatorio, la página de "Revisar y enviar" debería mostar una preview de lo que colocaste de información y permitirte darle a "Editar" para que cambies y lo que quieras y guardar después los cambios antes de enviar. ¿De acuerdo? Necesito que en el recorrido, tanto del estudiante como del profesor, revises por qué se demoran tanto en salir los pasos 4 y 5 y 6, realmente son un par de segundos, pero eso ya quita la atención de la persona. Los "te llevo hasta allá", siempre también deben scrollear así sea un poco, para que se note la animación. El "Mis horarios", me gustaría que esté la función de crear franjas arrastrando. Como cuando se cra una reu de Microsoft teams o meet en Calendar o el calendario de Teams. Revisa bien esa interfaz y agregale a Rigel en la derecha, quiero que genere más motivación y esté más linda visualmente. En Mis Clases, si aún no tiene agendadas, recomiéndale abajo como dos botones medianos que digan algo como "Invita tus estudiantes" y otro que diga "Revisa tu perfil" o algo así, ese no estoy seguro cómo redactarlo porque no quiero que suene a que estamos atacando a su perfil.

## Resumen ejecutivo

### Postulación
1. **Historia:** como aspirante no puedo pasar al siguiente paso sin llenar lo obligatorio de ese
   paso, así no me entero al final de que me faltaba la foto y tengo que devolverme seis pantallas.
   **Feature:** todos los campos son obligatorios, cada paso valida antes de «Siguiente» y dice qué
   falta.
2. **Historia:** como aspirante elijo mi país y después mi ciudad en un desplegable en el que puedo
   escribir para buscar.
   **Feature:** buscador de país y, una vez elegido, buscador de ciudades de ese país (catálogo de
   GeoNames, CC BY 4.0).
3. **Historia:** en «Revisar y enviar» veo todo lo que escribí, y si toco «Editar» cambio esa parte,
   la guardo y vuelvo a la revisión antes de enviar.
   **Feature:** resumen completo con «Editar» por sección y «Guardar y volver a revisar».

### Perfil del profe
4. **Historia:** veo arriba, justo debajo de la tarifa, si mi perfil está visible, y lo cambio desde
   ahí.
   **Feature:** el control de visibilidad se sube debajo de la tarifa.
5. **Historia:** con un botón veo cómo me ven los estudiantes.
   **Feature:** vista previa sencilla del perfil público.
6. **Historia:** armo mis horarios arrastrando sobre el calendario, como en Google Calendar o Teams,
   con Rigel acompañándome a la derecha.
   **Feature:** franjas por arrastre en la rejilla semanal y Rigel en «Mis horarios».
7. **Historia:** sin clases agendadas, Mis clases me sugiere qué hacer.
   **Feature:** dos botones medianos: «Invita a tus estudiantes» y uno sobre el perfil, redactado
   sin que suene a crítica.

### Cuenta y recorrido
8. **Historia:** al crear la cuenta, mi contraseña tiene que ser «Fuerte» o «Excelente».
   **Feature:** registro, restablecer contraseña y el servidor exigen ese nivel.
9. **Historia:** en el recorrido, los pasos 4, 5 y 6 salen enseguida, y «Te llevo hasta allá» se
   nota porque la pantalla se desplaza.
   **Feature:** quitar la demora de esos pasos y desplazar siempre un poco la pantalla.
10. **Historia:** Ayuda y las demás pantallas aprovechan el ancho de la pantalla en el computador.
    **Feature:** contenedores anchos en escritorio.

## Respuestas de Pardo

- 27/09, al ver el plan: «Vale, envíame un correo cuando termines. COn resumen ejecutivo en el correo
  y en la terminal.» → Al cerrar: correo con resumen ejecutivo, y el mismo resumen en la terminal.
- 27/09, 21:44, al ver que la sesión de la mañana se había cortado a las 11:27 con el trabajo de los
  agentes sin integrar: «Eso, sigue trabajando en TODO por favor. Hasta que termines.» → Se retoma
  todo: integrar lo que dejaron los agentes, terminar lo que quedó a medias y hacer lo que faltaba.
- 27/09, 21:55: «Espera, voy a reiniciar mi computador, guarda tareas y te diré cuando continuar.»
  → Todo quedó en commit en las ramas de los agentes (ver «Estado»).
- 28/09, 10:53, después del reinicio: «¿Cuál es el estado actual? Tuve que apagarlo y no sé cómo
  quedó» → Revisado: las siete ramas siguen intactas y en commit (solo `ade98…` tiene sin versionar
  su carpeta `capturas/`), ninguna toca un archivo de otra y la única migración nueva es la V78. No
  quedó nada corriendo. Se retoma cuando Pardo diga.
- 28/09, 10:59: «Continúa entonces y termina. Y lo del reclamo no lo entiendo, sí, déjalo así, no lo
  cambies. Termina y me vas el estado actual» → Se retoma la lista de «Lo que falta» hasta el final.
  El A5 (asistencia registrada al terminar la clase ⇒ el estudiante ya no abre reclamo formal) **se
  queda como está**; en el cierre va explicado en palabras simples por si quiere volver a él.

## Estado (28/09/2026): hecho

Las siete ramas de la mañana del 27/09 están en `master`, junto con lo que faltaba. Por punto del
resumen:

| # | Qué | Quedó |
|---|---|---|
| 1 | Postulación obligatoria paso a paso | «Siguiente» no avanza y dice qué falta |
| 2 | País y ciudad con buscador | País con bandera; ciudad del catálogo del país (GeoNames). También en /perfil |
| 3 | «Revisar y enviar» con «Editar» | Todo a la vista, «Editar» por sección, «Guardar y volver a revisar» |
| 4 | «Perfil visible» debajo de la tarifa | «Tu perfil está visible / oculto», en verde o ámbar, justo debajo de la tarifa |
| 5 | Ver cómo lo ven | «Vista previa: así te ven» (tarjeta del buscador + «Sobre ti», con lo escrito sin guardar) y su perfil público completo |
| 6 | Horarios arrastrando, con Rigel | Arrastrar abre una franja (media hora), clic abre una hora, se mueve y se estira, «Deshacer»; Rigel a la derecha con los cupos. Probado arrastrando en el navegador |
| 7 | Mis clases sin clases | «Invita a tus estudiantes» y «Mira cómo te ven los estudiantes» (abre su perfil público) |
| 8 | Contraseña Fuerte o Excelente | En registro, restablecer y cambiar contraseña, en la pantalla y en el servidor |
| 9 | Recorrido | Pasos 4–6 al instante; «Te llevo hasta allá» siempre desliza |
| 10 | Usar el ancho | Hasta 1600 px; Ayuda, Mi perfil, Cuenta, Invitar y Saldo en dos columnas |

**Verificación:** `./mvnw verify` con 450 unitarias y 674 de integración en verde; `tsc`, `lint` y
190 pruebas de Vitest en verde; e2e sobre la base recreada y sin la prueba de Wompi, 104 en verde y 1 saltada (la del reclamo, que necesita una clase dentro del plazo); `next build` de producción en verde. Revisión visual con capturas a 390, 1440 y 1920 px.

**Commits:** las ramas integradas (`43e3f43`…`5c3248f` de backend; `e718b35`…`b7849f8` de
frontend) y lo de hoy: `99a3067` (contraseña en el servidor), `bd9a32b` (perfil: visibilidad, vista
previa y ciudad), `97af1d1` (Mis clases → perfil público), `ba9a83f` (cambiar contraseña),
`22f34fd` y `ff55b76` (el ancho), `caa958a` y `025c70b` (e2e).

**Cierre:** push a `master` el 28/09 y correo a Pardo con el resumen ejecutivo, el mismo de la terminal.

**Queda como está (Pardo, 28/09):** si el profe registra la asistencia apenas termina la clase, el
estudiante ya no puede abrir un reclamo formal (A5 de la revisión).
