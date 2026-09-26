# Bloque 11 · Refinamiento de la plataforma (24/09/2026)

Pedido de Pardo del 24/09/2026 por la tarde, con sus respuestas a tres preguntas. Es el alcance
cerrado de este bloque: lo que no está aquí no se construye.

## Lo que se pide

1. **Login con Google**: falla muchas veces. Encontrar por qué y arreglarlo, con su prueba.
2. **Contraseña para quien entró con Google**: hoy no hay cómo tener o cambiar una contraseña.
3. **Recorrido guiado**: dejarlo idéntico al handoff `design_handoff_orion_bienvenida_recorrido`
   («Recorrido.zip»): auditar, corregir y verificar como dice su `CLAUDE_CODE_PROMPT.md`. Además,
   **que se mueva entre pantallas** (lleva a la pantalla de cada paso y enfoca el elemento real), no
   solo que ilumine los íconos de la navegación.
4. **Mis clases**: además del switch «Próximas / Pasadas», un enlace visible «Ver clases pasadas»
   debajo de la lista, para quien no ve el switch.
5. **Quitar «Otro horario»** (reprogramar): los cambios de horario traen más problemas de los que
   resuelven. Se cancela y se reserva otra.
6. **Mensaje automático al reservar**: al estudiante, en su chat con el profe, **a nombre del
   profe** y con una marca discreta de «enviado por Orión». Saluda, recuerda el día y la hora de la
   clase y dice quién lo saluda. Que no suene genérico, y con al menos una ⭐.
7. **Clase de prueba**: revisar si la lógica está implementada, porque es sumamente importante.
8. **La ficha del estudiante**, con énfasis:
   - recordatorios relativamente invasivos para completarla: una franja con Rigel en todas las
     pantallas hasta completarla (se puede cerrar por un día), una notificación en la app al día 1 y
     al día 3, y un correo al día 2;
   - un logro por completarla, con sus puntos;
   - Rigel recuerda que con la ficha completa —y visible, que es lo recomendado— es más fácil.
9. **Perfil**: quitar del todo «Si hay que cancelar» y «Preguntas» (también lo que de eso haya en
   «Mis datos»), y fusionar «Mi ficha» con «Mis datos» de modo que se entienda qué es público y
   qué no.
10. **Avatar**: al menos dos opciones de paleta, de órbita, etc., habilitadas desde el primer día,
    para que haya algo que elegir.
11. **Estudiante**: «Mi agenda» pasa a llamarse «Mis clases».
12. **Confidence Score** (el del diagnóstico): va después de «Mi cielo»; no es tan relevante.
13. **Puntos** (solo estudiantes, el sistema actual): mostrarlos por todos lados —perfil, la
    tarjeta de abajo con el nombre y el rol, y demás— y que se ganen con muchas más acciones. No
    sirven para nada más que mostrar experiencia en la plataforma.
14. **Correos y notificaciones**: asegurarse de que dentro de la plataforma y por correo salgan
    todos los que hacen falta.
15. **Notificaciones push** (escritorio y celular): recordar clases, recordar calificar, etc. Sin
    ser demasiado invasivas.
16. **Un documento para Pardo con TODOS los flujos de pantallas**, para probarlos todos.

## Respuestas de Pardo (24/09)

- Mensaje al reservar: al estudiante, a nombre del profe (no de Orión), con la marca de Orión.
- Puntos: solo estudiantes.
- Ficha: franja fija + notificación (días 1 y 3) + correo (día 2) + logro «Ficha completa».

## Lo que se encontró al revisar (24/09)

- **Google**: el `state` del login y el registro a medio completar viven en la sesión en memoria
  de Tomcat. Cualquier reinicio o despliegue mientras alguien está en Google hace fallar el regreso
  («No pudimos entrar»), y también saca a todos de su sesión. Un segundo clic o una segunda pestaña
  pisan el `state` del primero. Quien empieza en `www.` vuelve al dominio sin `www.` y sin cookie.
  Dentro de Instagram o TikTok Google no deja entrar, y la pantalla no lo avisa. Todos los errores
  terminan en el mismo «No pudimos entrar».
- **Contraseña con Google**: la cuenta nace con una contraseña al azar que nadie conoce, y
  «Cambiar contraseña» pide la actual: es un callejón sin salida. «Olvidé mi contraseña» sí sirve.
- **Recorrido**: solo ilumina íconos de la navegación; no cambia de pantalla, no guarda el paso, no
  existen «¿Seguimos donde íbamos?» ni «Repetir el recorrido», y la bienvenida es un modal.
- **Clase de prueba**: decidida en el brief maestro (Q7: `is_trial`, precio libre del profesor, la
  misma comisión, una por par estudiante–profesor) y **nunca construida**. El interruptor «Ofrezco
  clase de prueba» y la insignia pública existen, pero no cambian nada al reservar. `is_trial` hoy lo
  usan las clases de ensayo del admin.
- **Notificaciones**: no hay recordatorios de clase ni de calificar; reservar y cancelar solo
  mandan correo; la práctica lista, la respuesta de soporte, los pagos, las sanciones y la reseña
  recibida no avisan; no hay push. El correo sale por Resend en producción.
- **Ficha**: nada recuerda completarla; la tarjeta de visibilidad pide una fecha de nacimiento que
  el backend ya no recibe. **Avatar**: al empezar hay una sola opción de cada cosa, y la paleta
  lavanda ya no se puede conseguir. **Puntos**: solo se ven en «Mi cielo».
- **«Otro horario»**: el profe proponía con los cupos equivocados, y nadie podía aceptar ni rechazar
  una propuesta desde la pantalla.

## Orden de trabajo

1. Navegación y perfil (Mis clases, Ver clases pasadas, sin Otro horario, perfil fusionado).
2. Google: estado del login en cookie firmada, errores con nombre, aviso en navegadores de apps,
   `www` → dominio; contraseña para cuentas de Google.
3. Mensaje automático al reservar, y aviso en la app al reservar y al cancelar.
4. La ficha: franja con Rigel, recordatorios, logro «Ficha completa».
5. Avatar con dos opciones desde el inicio.
6. Puntos en todas partes y con más acciones.
7. Notificaciones y correos que faltan, con un correo de prueba desde Sistema.
8. Notificaciones push.
9. Recorrido idéntico al diseño, moviéndose entre pantallas.
10. Clase de prueba.
11. Documento de todos los flujos, ESTADO y manual.

## Segunda tanda (24/09, tarde) — después de todo lo anterior

Pardo, textual en lo esencial:

12. **Escritorio**: «los menús más pegados a la derecha, como la pestaña de Mensajes»: usar mejor
    la pantalla en escritorio (las pantallas centradas y angostas desperdician el ancho).
13. **Recordar la práctica** en otras partes de la plataforma, no solo al entrar a sus clases. «No
    TAN invasivo», pero que se recuerde dentro de la plataforma.
14. **Postulación del profe**: el país, de una lista desplegable, con su emoji (bandera).
15. **Ficha del profe**: lo mismo que la del estudiante (franja, avisos, correo), hasta que llene su
    disponibilidad, complete su perfil y lo publique.
16. **Fusionar «Disponibilidad»** con lo demás del profesor (su perfil).
17. **Landing en el celular**: Rigel no se ve en el hero. «ES VITAL que aparezca (incluso antes que
    todo)».
18. **«Invitar estudiantes»** en vez del enlace propio del profe: que genere un link para compartir
    en redes o mandarlo directamente a sus estudiantes.

## Tercera tanda (24/09, noche) — y después el Wireflow

Pardo, textual en lo esencial:

19. **«Editar perfil» no puede estar abajo del todo** (estudiante y profe): «me ha pasado incluso
    que intento corregir campos sin haberle dado ahí antes. Haz algo más usable y coherente».
    Decisión: sin modo de edición. Los campos se editan directo y, en cuanto algo cambia, aparece
    una barra fija «Tienes cambios sin guardar · Descartar · Guardar cambios» que no se va hasta
    decidir; salir de la página con cambios pregunta. En el profe, la tarifa entra en ese mismo
    guardado (fuera el «Guardar tarifa» suelto).
20. **Videollamada que se puede minimizar**: «que yo pueda minimizar y seguir viendo lo de Orión».
    Si no se puede, algo parecido: que al ir a otra parte de Orión o darle atrás, la llamada no se
    cierre. Con «algo de animación o algo interesante». Decisión: la llamada vive en el armazón de
    la app; al salir del aula se encoge a una ventana flotante (arrastrable, con micrófono,
    cámara, volver y colgar) y al volver se agranda, sin cortar la conexión.
21. **Las tres estrellas de «Mi ficha»** (debajo del nombre y el nivel) no se entienden: qué son,
    por qué tienen colores distintos. Que se expliquen solas.
22. **Filtrar por horas exactas**, varias a la vez (hoy solo «Mañana / Tarde / Noche» y de a una).
23. **Mensajes de Rigel**: mensajes oficiales de Orión en «Mensajes», a estudiantes y profesores:
    bienvenida, cómo funciona, con botones que llevan a la racha, las clases, actualizar algo,
    completar tareas. «No quiero que sea excesivamente invasivo». Decisión: un hilo fijo de Rigel
    arriba de la bandeja, solo lectura, con pocos mensajes y cada uno una sola vez (bienvenida,
    primera reserva, primera clase, y uno al mes como mucho si el estudiante lleva tiempo sin
    clases); sin campana, sin correo y sin push.
24. **La clase de prueba es GRATIS** si el profesor la ofrece; no es una clase con descuento.
    Reemplaza la Q7 del paso 10 (precio propio). El interruptor queda como «Ofrezco la primera
    clase gratis»; se quita el precio y el ajuste `trial_min_price_cop`.
25. **Wireflow de toda la app**: todas las pantallas con su captura, por rol, con las flechas de
    cómo se llega de una a otra, los caminos más críticos marcados y los casos para probar, para
    que Pardo y Sofía la recorran entera.

## La noche del 24 al 25/09 — trabajo autónomo hasta las 08:00

Pardo aprobó esta lista para la noche, con estas reglas: un commit por arreglo y push de cada paso
verde; lo que sea un bug o un hueco claro se arregla con su prueba, y lo dudoso o lo que cambie el
comportamiento para los usuarios se anota, no se toca; nada fuera de los briefs.

26. **Recorrer el Wireflow** probando con Playwright cada caso automatizable, y mostrar en la página
    «Claude ya lo probó» en una colección aparte, sin tocar las marcas de Pardo y Sofía.
27. **Arreglar los bugs claros** que salgan.
28. **Convertir esas pruebas en e2e.**
29. **Revisión de seguridad** de lo nuevo del Bloque 11.
30. **Pulir lo visual roto en celular** según las capturas, y listar el resto.
31. **Sesiones que sobrevivan a los despliegues** (Spring Session JDBC), con su prueba.
32. Al final: reporte publicado como página, ESTADO, manual y Wireflow actualizados.

Lo que salió está en `docs/ESTADO.md` («La noche del 24 al 25/09/2026»).

## Cuarta tanda (25/09, mañana)

Pedidos de Pardo del 25/09, en sus palabras donde importan:

33. **Quitar «Tus puntos» del perfil del estudiante**: «no quiero que le expliques nada de cómo
    funcionan, qué hacen». Los puntos siguen junto al nombre, sin enlace a una explicación.
34. **Sin «Enseñar en Orión» al final de «Mi ficha y mis datos»**, y sin postulación desde una cuenta
    de estudiante: «no lo quiero así». Postula quien entra por «Quiero enseñar» (y el profesor
    invitado por el admin); un estudiante que quiera enseñar crea otra cuenta. Las que ya existían:
    «borra las postulaciones ya existentes desde cuenta estudiante, haz de cuenta que nunca pasó»
    (también las de aspirantes rechazados, que quedan como cuenta de estudiante).
35. **El diagnóstico es voluntario**: «hay personas con miedo a que la diagnostiquen y que le digan
    que está mal». Mostrar la opción de registrarse directamente sin hacerlo, recordar que queda
    disponible más adelante, y decirles: «puedes hablarle en español, simplemente para que te
    conozca, entienda tu contexto y por qué quieres aprender inglés». El guion no cambia, pero
    quien habla en español también ve su Confidence Score: «no pasa nada que le muestre el
    Confidence Score y que le recomiende profes principiantes, tiene todo el sentido del mundo».
36. **Una clase no sale de «Próximas» al empezar** («si se me cae la conexión, llego un minuto tarde
    o lo que sea, ya no puedo verla»): pasa a «Pasadas» cuando le quedan 5 minutos, y hasta entonces
    conserva «Unirse a la clase».

## Quinta tanda (25/09, noche)

Pedidos de Pardo del 25/09 por la noche:

37. **El nombre y la descripción corta del profe se leen enteros** en `/profesores/<id>`: «no puedo
    verlo completo y es sumamente importante que así sea». Nada de «…».
38. **El WhatsApp es obligatorio, no opcional.** En el registro (con contraseña y con Google o
    Facebook) y en «Mi cuenta», donde ya no se puede borrar. Quien ya tiene cuenta sin número lo
    deja al entrar. El admin puede crear un usuario sin él: se lo pedirá la app a esa persona.
39. **El panel del admin sin scroll horizontal**: «odio ese scroll… aprovecha mejor el espacio o
    reduce los botones o colócales iconos, como sea». En escritorio y en celular.
40. **Que se vea que la clase dura 55 minutos**, «de x hora a y hora», en todo lo que acompaña a
    reservar una clase: «puede parecer que la clase dura 30 min si uno ve el horario». Los horarios
    del perfil del profe, el resumen antes de pagar, la vuelta de Wompi, el correo, el saludo en el
    chat, los avisos y recordatorios, y «Tu próxima clase».

## Sexta tanda (26/09)

Pedidos de Pardo del 26/09, con los 18 commits anteriores ya subidos:

41. **Quitar del landing** el párrafo bajo los botones del inicio: «Dos minutos de conversación,
    gratis y sin crear cuenta. Al terminar sabes en qué nivel estás y te mostramos tres profesores
    que encajan contigo. Puedes hacerlo en español si prefieres.» `/diagnostico` se queda como está.
42. **Los 6 archivos de la sesión anterior**, comiteados y subidos (aviso de fin de fundador,
    limpieza de `accept-invite`, registro con invitación vencida).
43. **Sin el nombre de Pardo** en los acuerdos «y en general»: decir «Orión» y «los representantes
    legales de Orión». Límite de ley: las tablas de identificación de los Términos (art. 50 de la
    Ley 1480) y de la Política (art. 13 del Decreto 1377) y el certificado anual de ingresos para
    terceros tienen que identificar a quien responde con nombre y documento; salen de
    `ORION_LEGAL_*`, así que con una sociedad basta cambiar esas variables. Términos y Política pasan
    a la 1.1 (redacción, sin aceptación nueva); el acuerdo del profesor 2.0 se corrige en su sitio
    porque ningún profe lo ha aceptado en producción.
44. **Un solo «Aceptar los nuevos acuerdos»** con un botón que los despliega todos, en vez del
    acuerdo metido en una caja con scroll. «NO HAY PROFES EN PRODUCCIÓN, por ahora todos van a entrar
    a aceptar los nuevos acuerdos»: sin «Ahora no». Se le pide al profe aprobado; el aspirante lo
    acepta en su postulación.
45. **Los datos de pago son obligatorios**: «son IMPORTANTÍSIMOS, los profes sí o sí deben
    llenarlos». El profe aprobado que no los tiene no puede seguir hasta registrarlos, como con el
    WhatsApp.

## Auditoría del recorrido contra el handoff (paso 9)

Diferencias encontradas y corregidas:

- **Bienvenida**: era un modal de 720 con un reproductor genérico → pantalla completa sin
  navegación (capturas 01/02): chip «Tu postulación fue aprobada», título 30/40, video 350×197 /
  760×428 con portada noche, play coral 64/84, «Sofía · Directora académica».
- **«Lo veo después»** solo aplazaba por la sesión → marca la bienvenida, lleva a la agenda y Rigel
  ofrece el recorrido ahí una sola vez.
- **Textos** distintos a §7 («Busca tu profesor», «¡Hola, X! Te muestro…») → palabra por palabra.
- **Tarjeta**: radio 18, sin círculo del guía, contador en mayúsculas coral → radio 22, círculo de
  52 (#FFF1C9 / #EFE9F9) con el personaje de 40, «1 de 8 · Meissa» 12/700, título 19, texto 15/1.5,
  ayuda de teclado en escritorio, flecha de 16 a 45°.
- **Progreso**: segmentos iguales en coral → hecho 8 lavanda / actual 20 tinta / pendiente 8.
- **Botones**: «Atrás» oculto en el paso 1 y sin «Terminar» → deshabilitado a .45 y «Terminar».
- **Foco**: velo rgba(29,20,36,.62) y anillo de 2 → rgba(46,30,78,.74), margen de 6 del fondo local
  y anillo de 3 #FFC189.
- **Solo iluminaba íconos** → cada paso lleva a su pantalla e ilumina el elemento (Unirse, Actas,
  clase pasada, filtros, horarios del perfil, Mi cielo), con la navegación de respaldo.
- Faltaban la píldora «Te llevo hasta allá», `inert`, `aria-live`, el paso guardado con «¿Seguimos
  donde íbamos?» y «Repetir» desde el paso 1 → hechos.
- **Ayuda**: una tarjeta con dos botones → la lista de la captura 03 y el video en modal de 880.

Lo que queda distinto, a propósito o por límite:

- Con YouTube, Vimeo o Drive el reproductor es el del proveedor: los controles del diseño solo
  aplican a un archivo de video, y la duración solo se muestra si sale del archivo.
- No hay pruebas `toHaveScreenshot`: las capturas se compararon a mano al mismo tamaño; el e2e cubre
  el comportamiento (pantallas, retomar, terminar).
- En la barra inferior del móvil el ítem iluminado conserva su color de texto.
- El paso 2 del profesor lleva a Disponibilidad: cambiará al fusionarla con el perfil (paso 16).
