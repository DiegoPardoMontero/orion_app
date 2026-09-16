# Encargo: Aula de Orión — antesala y cierre de la clase

Capa nueva sobre «Amanecer cálido premium». NO cambia el sistema de diseño, ni a Rigel, ni las pantallas ya entregadas. Fuente de verdad del sistema: `Orión Fundamentos v2.dc.html` y las pantallas de Estudiante y de Profesor y Admin.

## Contexto

Orión es un marketplace colombiano de clases particulares de idiomas. El estudiante reserva una hora con un profesor y la clase se da por videollamada. Hasta ahora la videollamada se abría en Jitsi público, fuera de la plataforma; ahora se embebe dentro de Orión con Jitsi JaaS.

Restricción dura: **la videollamada en sí es un iframe de Jitsi y no se diseña.** Los mosaicos de vídeo, la barra de controles y el modo mosaico son de Jitsi y no se pueden restilizar. No diseñes la pantalla «en llamada». Diseña lo de antes y lo de después.

Clases de 55 minutos, uno a uno. La sala abre 10 minutos antes de la hora y el acceso expira 15 minutos después del final. El profesor entra como moderador; el estudiante no.

## Lo que hay que diseñar

### A · La antesala
La pantalla en la que espera antes de entrar. Es el momento en que alguien va a hablar un idioma que no domina con un desconocido: el diseño tiene que bajarle el pulso, no subírselo.

Tiene que resolver, en una sola pantalla:
- Con quién va a hablar: foto real, nombre, su título, el idioma y el nivel.
- Qué clase es: fecha, hora de Bogotá, cuánto dura.
- Prueba de micrófono y cámara antes de entrar, con vista previa de sí mismo.
- El botón de entrar, y su estado deshabilitado.
- Cuatro estados del reloj:
  1. faltan más de 10 minutos — no se puede entrar, cuenta atrás
  2. la sala ya está abierta — se puede entrar
  3. la clase ya empezó y la otra persona ya está dentro
  4. la clase ya terminó o el acceso expiró

Dos variantes: la del estudiante y la del profesor. El profesor entra como moderador y eso debe notarse sin ser amenazante.

### B · El cierre
Lo que ve al colgar, sin recargar la página.
- Estudiante: que la clase terminó, cuánto duró, y calificar al profesor (1 a 5 estrellas y un comentario opcional). Calificar es opcional y saltárselo no puede sentirse como una falta.
- Profesor: que la clase terminó, cuánto duró, y marcar asistencia — incluido el caso de que el estudiante no se presentó.
- Un caso feo que hay que resolver con cuidado: se cayó la conexión antes de tiempo. No es lo mismo que terminar, y volver a entrar tiene que ser lo primero.

## Reglas

- Español de Colombia. Tuteo. Nada de jerga técnica: «sala», no «room».
- Rigel, la mascota, puede aparecer — hay una guía. Si aparece en la antesala tiene que calmar, no celebrar.
- 390 px y 1280 px.
- Accesibilidad: el estado nunca solo por color; foco visible; respeta `prefers-reduced-motion`.
- Tokens nuevos, si hacen falta, declarados aparte en una sección `@theme`.
- Entrega en el mismo formato `.dc.html` que las etapas anteriores, con las secciones numeradas y el contrato visual escrito.
