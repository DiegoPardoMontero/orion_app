# Probar la fase 1 del lanzamiento: el camino del profe, desde cero

Pedido de Pardo del 29/09/2026 (`docs/pedidos/2026-09-29-1220-…`). La fase 1 es la semana 1 de
`docs/plan-de-lanzamiento.md`: se abre Orión a **profesores**, empezando por los fundadores
invitados, y en las semanas 2 y 3 ellos dan clases a su propia gente. Esta guía es el ensayo
general, **en producción y con dinero de verdad**, antes de mandar la primera invitación. Sigue el
mismo camino que la prueba automática `frontend/e2e/flujo-profesor-completo.spec.ts`, pero con tus
manos, tu celular y tu tarjeta.

Cada paso dice qué hacer y **qué debe pasar**. Si algo no pasa así, anota la hora y la pantalla y
avísame.

## 0. Antes de empezar

1. **Cierra la casa** (`docs/cerrar-la-casa.md`). Para esta prueba es indispensable que Sistema
   muestre encendidos Wompi (en «Producción»), JaaS con su webhook, Cloudinary, el correo y OpenAI.
2. **Tres cuentas**, una por papel. Con Gmail sirve el truco del «+»: todo llega a tu buzón y Orión
   las trata como correos distintos.
   - **Admin**: tu cuenta de administrador de siempre.
   - **Profe**: `diegopardomontero+profe1@gmail.com`, invitada como fundadora (parte 1).
   - **Estudiante**: `diegopardomontero+estudiante1@gmail.com` (parte 3).
   - **Un segundo profe**, `…+profe2@gmail.com`, para el camino sin invitación (parte 2).

   El WhatsApp puede ser el mismo en todas.
3. **Dos aparatos**: el profe en el celular y el admin y el estudiante en el computador. Así se
   prueban las dos pantallas y la clase entre dos equipos de verdad.
4. **Un profe de verdad**, si puedes (el primer fundador, alguien de confianza). Hace las partes 1 y 3
   contigo, y la parte 5 le llega a su llave Bre-B. Si no, haz tú de profe con la cuenta `+profe1`.

## 1. El profe fundador invitado (el camino de la semana 1)

1. **Admin → Usuarios → «Invitar profesor»**: el correo `+profe1`, el nombre y «Enviar invitación».
   - Debe pasar: «Le enviamos la invitación», y al buzón llega el correo con el enlace.
2. **Abre el enlace en el celular.**
   - Debe pasar: «<Nombre>, queremos que seas de los primeros profes de Orión.», con el 15 % de
     fundador.
3. **«Aceptar la invitación»**: el registro viene con el correo fijo, que no se puede cambiar. Llena
   nombre, contraseña y WhatsApp, marca las tres casillas y toca «Crear cuenta y postularme».
   - Debe pasar: entras directo a la postulación, en «Datos personales · Paso 1 de 6».
4. **La postulación, paso por paso:**
   1. Foto y título.
   2. «Sobre ti», idiomas con niveles y objetivos.
   3. Experiencia: país, ciudad (escríbela entera), años y estudios.
   4. Documentos: la hoja de vida es obligatoria (PDF o imagen) y los certificados son opcionales.
   5. Acuerdo: marca «He leído y acepto el acuerdo del profesor de Orión.».
   6. Revisar y enviar: «Enviar a revisión».
   - Debe pasar: la foto y la hoja de vida suben (eso prueba Cloudinary), cada paso guarda lo suyo,
     y al final dice «Tu postulación está en la fila de revisión».
   - Además: **a tu correo de admin llega el aviso de postulación nueva.**
5. **Mientras está en revisión**, intenta editarla.
   - Debe pasar: la pantalla no deja editar y te muestra el estado.
6. **Admin → Postulaciones → «Revisar»**: mira foto, textos y hoja de vida. Toca «Empezar revisión» y
   luego «Aprobar».
   - Debe pasar: al profe le llega «Tu postulación en Orión fue aprobada», con enlaces.
7. **El profe entra por primera vez.**
   - Debe pasar, en este orden:
     1. «Falta a dónde te pagamos» (llave Bre-B). Tiene «Salir» y WhatsApp de soporte, pero no se
        cierra sin llenarla. Llénala.
     2. La bienvenida, con el video de Sofía, que es opcional.
     3. El recorrido, que es obligatorio.
   - Nada debe salir dos veces, ni siquiera al recargar.
8. **Mi perfil.**
   - Debe pasar: ya trae lo de la postulación (foto, título, bio, idiomas y ciudad).
   - En «Tus datos» se pueden corregir el nombre y el WhatsApp.
   - Pon una tarifa. Debe decir «Recibes … por clase: 15 % de comisión como profe fundador durante
     tus primeros 3 meses…».
   - Enciende «Perfil visible» y toca «Guardar cambios».
   - Si el correo no está confirmado, lo avisa y no deja publicar. El de la invitación ya viene
     confirmado.
9. **Mis horarios**: abre dos franjas, una que empiece a la media hora (por ejemplo, 5:30 a 7:30 PM),
   y bloquea una fecha.
   - Debe pasar: la fecha bloqueada no ofrece cupos.
10. **Sin sesión** (en una ventana de incógnito), abre orionidiomas.com/profesores.
    - Debe pasar: el profe aparece, con el nombre y la descripción enteros y su tarifa. En su perfil
      público se ve «Reserva tu clase».
11. **Invitar**: el profe copia su enlace y lo abre en otra ventana.
    - Debe pasar: el enlace lleva a su perfil público.
12. **Admin → Usuarios**: busca al profe.
    - Debe pasar: dice «Fundador · 15 %».

## 2. El profe que llega solo, por «Enseña con Orión» (una vez)

1. En incógnito, abre orionidiomas.com/ensena-con-orion.
   - Debe pasar: la comisión dice que el 15 % de fundador es por invitación.
2. «Crea tu cuenta y postúlate» con el correo `+profe2`.
   - Debe pasar: «Quiero enseñar» ya viene elegido y llega el correo para confirmar.
3. Confirma el correo con el enlace.
   - Debe pasar: vuelves a tu postulación, no al buscador.
4. Llena la postulación y envíala.
5. **Admin: «Empezar revisión» → «Pedir cambios»**, con una nota.
   - Debe pasar: al profe le llega el correo «necesita cambios» con la nota. En su estado ve el
     comentario y el botón «Editar y reenviar».
6. El profe corrige y reenvía.
   - Debe pasar: al admin le llega el aviso de que volvió.
7. Admin: «Aprobar».
   - Debe pasar: este profe tiene la comisión normal (20 %), no la de fundador.
8. **Opcional**: con una tercera cuenta, prueba «Rechazar».
   - Debe pasar: le llega el correo con la nota.

## 3. La primera clase de verdad, con dinero

1. **Estudiante** (`+estudiante1`, en el computador): regístrate desde «Quiero aprender», confirma el
   correo, entra al perfil del profe y elige un cupo.
2. «Continuar al pago» → Wompi → **paga de verdad** (tarjeta, PSE o Nequi).
   - Debe pasar: vuelves a Orión y ves «¡Clase reservada!». Admin → Pagos la muestra pagada.
3. **El profe** (en el celular).
   - Debe pasar: la clase aparece en su agenda, la campana dice «Nueva clase con …» y le llega el
     correo con el enlace a la sala.
   - Si activó los avisos desde la campana, también le suena el celular.
4. **Mensajes**: el estudiante le escribe al profe desde su perfil («Enviar mensaje») y el profe
   responde.
   - Debe pasar: al profe le llega el correo de mensaje nuevo, con el enlace a la conversación.
5. **La clase**: los dos entran a la sala a la hora.
   - Debe pasar: la antesala muestra al otro cuando entra (eso prueba el webhook de JaaS), y se ven y
     se oyen.
   - Prueba también a salir y volver a entrar.
6. **Al terminar**, el profe registra la asistencia: «Asistió».
   - Debe pasar: le aparece «Contar cómo estuvo».
7. **El acta**: el profe escribe o dicta sus notas, toca «Generar acta», la revisa y toca «Publicar».
   - Debe pasar: el borrador sale con IA en unos segundos. El estudiante ve «Resumen de tu clase» en
     Pasadas, y después su práctica.
8. **El estudiante califica** la clase (en Pasadas, «Calificar»).
9. **Ganancias del profe.**
   - Debe pasar: la clase aparece con el precio, la comisión del 15 % y lo que recibe. En «Clases por
     liquidar» dice por qué espera, por ejemplo «En plazo de reclamo hasta el …».

## 4. Lo que puede salir mal (cada caso una vez)

Cada caso con una clase pagada nueva:

1. **El estudiante cancela con más de 12 h de anticipación.**
   - Debe pasar: el valor vuelve como saldo a favor (Pagos → saldo).
2. **El estudiante cancela con menos de 12 h.**
   - Debe pasar: el aviso dice que la clase se cobra, y el dinero va para el profe.
3. **El profe cancela.**
   - Debe pasar: el estudiante recupera el valor completo como saldo.
4. **Reclamo.** A los 15 minutos de empezar, el profe marca «No asistió» y el estudiante toca
   «Reportar un problema».
   - Debe pasar: el reclamo entra aunque el profe ya haya marcado la asistencia, y la sala sigue
     abierta hasta el final.
   - Resuélvelo en Admin → Reclamos. A favor del estudiante: recupera el valor como saldo. A favor del
     profe: el pago se libera.
5. **Un profe sin llave Bre-B** (el de la parte 2).
   - Debe pasar: Ganancias dice «Para pagarte necesitamos tu llave Bre-B».

## 5. El corte y el pago al profe

1. Espera el corte: las 00:00 del 1 o del 16. Las clases cuyo plazo de reclamo (24 h) venció entran
   en esa quincena.
2. **Admin → Pagos → pestaña «Liquidaciones»**: abre la del profe y revisa las líneas. Luego toca
   «Aprobar».
3. Transfiérele por Bre-B a su llave. Comprueba que el titular que muestra tu banco sea el profe.
4. **«Registrar pago»**, con la referencia y la fecha.
   - Debe pasar: el profe recibe el aviso, y en Ganancias ve la liquidación pagada con su comprobante.

## 6. Listo para invitar

Cuando las partes 1, 3 y 5 salen bien, y la 2 y la 4 al menos una vez:

- Oculta o desactiva las cuentas de prueba, incluido «Diego Pardo Test 28/09».
- Revisa el plazo de revisión de postulaciones (Ajustes); el plan recomienda revisar el mismo día.
- Manda las invitaciones de los fundadores desde Admin → Usuarios → «Invitar profesor».
