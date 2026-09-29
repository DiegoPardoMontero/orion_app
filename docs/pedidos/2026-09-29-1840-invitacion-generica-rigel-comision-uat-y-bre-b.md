# Invitación genérica por WhatsApp, Rigel de la invitación, la comisión más discreta, un ambiente de pruebas y la llave Bre-B en la postulación

| | |
|---|---|
| **Fecha** | 29/09/2026, 18:40 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/prueba-fase-1.md`, `docs/cerrar-la-casa.md`, `docs/plan-de-lanzamiento.md` |
| **Estado** | En curso |

## El pedido, tal cual

> Necesito algo urgentemente. Debo tener un botón genérico de "Invitar profesor". No de todos los profes tengo el correo y me gustaría tener un enlace para simplemente enviar un mensaje "genérico" de Whatsapp. ¿Me podrías ayudar con esto? En la pantalla de invitación, Rigel se ve raro. Las manos tienen esa línea negra sobrepuesta que quitamos hace tanto. Toma el diseño de Rigel real y aplicalo. Si bien me gusta nuestra transparencia de comisión, quiero que no se mencione mucho el tema ni la cifra (ni siquiera el descuento por ser profe fundador) hasta que el profe coloque la tarifa. Esto para evitar que el profesor se retracte nada más entrar a registrarse. Por otra parte, ¿qué tan complejo es crear un ambiente de pruebas? Una réplica exacta de la página pero para DEV/UAT, allí podríamos probar nuevas features, crear usuarios de prueba, etc. Una cosa, con respecto a la primera entrada del profe, quiero que quizá lo de la llave Bre-B se lo dejemos en el cuestionario. Me parece que es sumamente importante y no tiene mucho sentido darles la bienvenida y seguir pidiendo cosas antes de sepan nada. El webhook de JaaS no está, es muy importante? De resto todo aparece encendido a excepción de Ingresar con Facebook, Microsoft y Apple (estos no me importan mucho por ahora). ¿Alguna pregunta hasta aquí te de lo que te pido crear/ajustar?

## Resumen ejecutivo

- **Invitación sin correo, por WhatsApp** (urgente)
  - Historia: como admin, quiero un botón «Invitar profesor» que me dé un enlace para mandar por
    WhatsApp con un mensaje genérico, para invitar a profes de los que no tengo el correo.
- **Rigel en la pantalla de invitación**
  - Historia: como profe invitado, quiero ver a Rigel como es, sin la línea negra sobre las manos.
- **La comisión, discreta hasta la tarifa**
  - Historia: como profe que llega, no quiero ver la cifra de la comisión, ni el descuento de
    fundador, hasta que pongo mi tarifa, para no echarme para atrás al registrarme.
- **Ambiente de pruebas (DEV/UAT)**
  - Pregunta: qué tan complejo es tener una réplica de la página para probar funciones nuevas y
    crear usuarios de prueba.
- **La llave Bre-B en la postulación**
  - Historia: como profe aprobado, quiero entrar a la bienvenida sin que me pidan más datos, porque
    la llave Bre-B ya la di en la postulación.
- **El webhook de JaaS**
  - Pregunta: qué tan importante es. Pardo reporta que en Sistema lo demás está encendido, salvo
    entrar con Facebook, Microsoft y Apple, que por ahora no le importan.

## Respondido en el chat

- **Webhook de JaaS:** no bloquea (las clases funcionan), pero sin él la antesala dice siempre «aún no
  ha entrado» y no queda registro de presencia. Con la regla nueva del reclamo, esa presencia es la
  evidencia para resolverlo. Se recomienda configurarlo antes de la primera clase pagada (paso 3 de
  `docs/cerrar-la-casa.md`).
- **Ambiente de pruebas:** moderado.
  - Pardo, en Railway: otro ambiente con su base, un subdominio y las llaves de sandbox de Wompi.
  - Claude, en el código: una franja de «pruebas», que no se indexe y datos de prueba.
  - La forma de trabajar cambia: `develop` va a UAT antes de pasar a `master`.
- **Rigel de la invitación:** la página usaba `/rigel/rigel-saluda.svg`, del paquete de diseño, con
  la línea negra vieja. Se cambia por el componente `Rigel`, sin pregunta.
- **Comisión:** el acuerdo del profesor 2.0 no dice la cifra («la verás antes de publicar»). Los
  Términos 1.1 sí la dicen (`{{comision}}`) y, como texto legal, se quedan.

## Decisiones de Pardo (29/09, 18:50, eligiendo entre opciones)

- **Invitación:** «Un enlace por persona». En Usuarios → «Invitar profesor»:
  - el correo pasa a ser opcional; se escribe el nombre y se marca si es fundador;
  - Orión da el enlace y un botón «Enviar por WhatsApp» con el mensaje ya escrito;
  - cada enlace es para una sola persona y vence en 7 días.
- **Comisión:** «Sin cifra».
  - «Enseña con Orión»: «Tú pones tu tarifa. Antes de publicar ves, clase por clase, cuánto
    recibes. Sin cuotas por adelantado».
  - Lo mismo en la invitación, el registro, las preguntas frecuentes y la descripción para Google.
  - La cifra aparece por primera vez al poner la tarifa.
- **Llave Bre-B:** «Paso obligatorio propio».
  - La postulación pasa a 7 pasos, con «Pagos» antes del «Acuerdo», y no se envía sin la llave.
  - Si la postulación se rechaza, la llave se borra.
  - El aviso al entrar queda solo para los profes que ya existen sin llave.
- **UAT:** «Sí, después de lo urgente». Primero va a producción lo de este pedido. Después, el código
  de UAT y la guía de Railway, y desde entonces todo pasa por UAT antes de producción.

## Estado

En curso: la invitación por WhatsApp primero.
