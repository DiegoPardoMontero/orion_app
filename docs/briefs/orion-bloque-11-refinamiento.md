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
