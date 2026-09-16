# Handoff: Aula de Orión — antesala y cierre de la clase

Capa nueva sobre «Amanecer cálido premium». No cambia el sistema, ni a Rigel, ni las pantallas entregadas. La videollamada es un iframe de Jitsi JaaS y **no se diseña**: aquí está lo de antes (antesala) y lo de después (cierre y conexión caída).

## Archivos

- `Orión Aula · Antesala y Cierre.dc.html` — el diseño. Abrir en el navegador (necesita `support.js` al lado). Secciones numeradas 00–07.
- `Orión Fundamentos v2.dc.html` — tokens y componentes base (`@theme` completo en la sección 02). Fuente de verdad del sistema.
- `Orión Pantallas v2 · Estudiante.dc.html` y `Orión Pantallas v2 · Profesor y Admin.dc.html` — app shell, «Mis clases» y patrones de card/botón que la antesala reutiliza.
- `brief-aula.md` — el encargo original.
- `support.js` — runtime para abrir los `.dc.html`. No forma parte del producto.

## Mapa de secciones → rutas

| Sección | Qué es | Ruta sugerida | Rol |
| --- | --- | --- | --- |
| 00 | Contrato visual (reglas) | — | — |
| 01 | Antesala · 390 · 4 estados del reloj | `/aula/:bookingId` | estudiante |
| 02 | Antesala · 1280 · estado «abierta» | `/aula/:bookingId` | estudiante |
| 03 | Antesala · 390 estado «cerrada» y 1280 estado «empezó» | `/aula/:bookingId` | profesor (moderador) |
| 04 | Cierre · 390 y 1280 · calificación opcional | misma ruta, hoja sobre el iframe | estudiante |
| 05 | Cierre · asistencia (asistió / no se presentó) | misma ruta, hoja sobre el iframe | profesor |
| 06 | Conexión caída · estudiante y profesor | misma ruta, hoja sobre el iframe | ambos |
| 07 | Tokens nuevos `@theme`, accesibilidad, movimiento | — | — |

## Máquina de estados de la antesala

Entrada: `startsAt`, `endsAt = startsAt + 55 min`, `now` (hora de Bogotá), `otherParticipantInRoom` (Jitsi `participantJoined`).

| Estado | Condición | Chip (ícono + texto) | Botón |
| --- | --- | --- | --- |
| 1 Cerrada | `now < startsAt − 10 min` | reloj · «La sala abre a las HH:MM» + «Faltan N min» (Bricolage 800) | deshabilitado, `aria-disabled`, fondo `--color-surface-sunken`, texto `--color-text-muted` |
| 2 Abierta | `startsAt − 10 min ≤ now < startsAt` | punto `breathe` verde · «Sala abierta» + «Empieza en N min» | coral, `glow` |
| 3 Empezó | `startsAt ≤ now < endsAt` | check verde · «María te espera» / «Ana te espera» + «Empezó hace N min». Si la otra persona NO está: reloj · «Aún no ha entrado». | coral, sin `glow`; texto «Entrar ahora» |
| 4 Terminó | `now ≥ endsAt + 15 min` (o `now ≥ endsAt` y nadie dentro) | candado gris · «Esta clase terminó a las HH:MM» | sin botón de entrar; «Escribirle a X» (coral) + «Ver mis clases» (secundario) |

Verbos: estudiante «Entrar a la sala» · profesor «Abrir la sala» (estado 2) / «Entrar con Ana» (estado 3).
Profesor: chip lavanda «Anfitrión» (`--color-host` / `--color-host-bg`) junto al kicker y una línea de qué puede hacer. Sin Rigel.
Rigel solo en la antesala del estudiante, estados 1 y 2, pose tranquila (ojos suaves, brazos abajo), animación `bob` únicamente. Copy fijo: «Respira. María sabe que estás aprendiendo — hablar con miedo también es hablar.»

La cuenta atrás se actualiza cada segundo en pantalla pero se anuncia (`aria-live="polite"`) solo al cambiar de estado.

## Vista previa (antes de entrar)

Bloque `--color-preview-bg #1E1430`, radio 24, 210 px de alto en móvil / 16:10 en desktop. Badge «Así te verás». Dos píldoras (Micrófono, Cámara) de 40/44 px con `--color-preview-control`; el medidor del micrófono son tres barras durazno con `level`. Apagado: la píldora pasa a crema con ícono tachado y texto «Encender»; el preview muestra ícono de cámara tachada + «Cámara apagada»; debajo, aviso durazno «Entrarás sin cámara — puedes encenderla dentro». Cuando todo está listo: línea verde con check «Micrófono y cámara listos» (+ «Conexión buena» en desktop).

Configuración Jitsi al entrar: `prejoinPageEnabled: false` (la antesala reemplaza la prejoin de Jitsi), `startWithAudioMuted` / `startWithVideoMuted` según los toggles, `userInfo.displayName` con el nombre real.

## Cierre (al colgar, sin recargar)

Evento `readyToClose` / `videoConferenceLeft` → fade del iframe 220 ms → hoja de cierre 380 ms `--ease-out` (radio 28, sombra `0 -18px 44px rgba(51,32,59,.18)`). Con `prefers-reduced-motion`, aparece sin desplazamiento.

**Estudiante** (sección 04): cabecera sobre gradiente «cielo» (`#2E1E4E → #7A4A8C 55% → #E8764F`) con «Hablaste N minutos en inglés.» y el rango real; hoja crema con «¿Cómo te fue con María?», 5 estrellas (`radiogroup`, 52 px, valor en texto: 1 «Mejorable» · 2 «Regular» · 3 «Buena» · 4 «Muy buena» · 5 «Excelente»), textarea opcional, y dos botones del mismo tamaño: «Ahora no» (secundario) y «Enviar» (coral). Enlace «Reservar la siguiente con María». Saltarse la calificación no muestra ningún aviso ni recordatorio.
Rigel (pose Ánimo) solo en desktop, con la línea «Tres clases con María. La constelación va tomando forma.» (N = clases con ese profesor).

**Profesor** (sección 05): chip «Sala cerrada», duración real, tres cifras en desktop (minutos, hora de entrada, clases con el estudiante). Asistencia obligatoria: dos tarjetas `role="radio"` (borde 2 px coral + punto relleno + ícono; «Ana asistió» / «Ana no se presentó»). Al elegir «no se presentó»: aviso durazno «La clase se registra como no asistida por Ana. Tu tiempo cuenta como impartido. Ana recibe un aviso amable, no un regaño.» y aparece «Escribirle a Ana». Nota privada para la próxima clase. Botón «Cerrar la clase». Sin Rigel.
Si el estudiante nunca entró, el titular es «Estuviste N min en la sala» y la segunda tarjeta viene preseleccionada.

## Conexión caída (sección 06)

Disparador: `connectionFailed`, `suspendDetected` o `participantLeft` antes de `endsAt` con acceso vigente. **No es el cierre.** Tono aviso (`--color-warning` / `--color-warning-bg`), nunca error.

- Estudiante: «Se perdió la conexión. La clase no terminó.» + minutos que llevaba + hasta qué hora sigue abierta la sala. Card «María sigue en la sala». Línea «Buscando conexión…» con spinner. Acciones en este orden: **Volver a la sala** (coral, reingresa al mismo iframe sin recargar) → Escribirle a María (secundario) → enlace discreto «No puedo continuar — terminar la clase» (lleva al cierre con duración parcial).
- Profesor: «A Ana se le cayó la conexión.» + card con «Intentando reconectar · m:ss». **Volver a la sala y esperarla** (coral) → Escribirle a Ana → enlace «Dar la clase por terminada (N min)».
- Si el acceso ya expiró al reconectar, se salta directo al cierre con la duración registrada y la línea «La conexión se cortó a las HH:MM».
- Asistencia: si el estudiante entró y se cayó, cuenta como asistió; «no se presentó» solo si nunca entró.

## Tokens nuevos

Están en la sección 07 del diseño. Resumen: `--color-preview-bg #1E1430`, `--color-preview-control rgba(255,246,238,.14)`, `--color-preview-control-on #FFF6EE`, `--color-presence #2E6B4A`, `--color-presence-off #D8C7B8`, `--color-host #5E4A8A`, `--color-host-bg #EFE9F9`; duraciones `breathe 2400ms`, `glow 3000ms`, `sheet 380ms`, `level 900ms`; layout `--preview-h-mobile 210px`, `--preview-ratio-desktop 16/10`, `--lobby-max-w 1088px`, `--sheet-radius 28px`. Todo lo demás viene de Fundamentos v2.

## Accesibilidad

- Estado nunca solo por color: chip con ícono + texto + frase de qué hacer.
- Botón deshabilitado con `aria-disabled` y motivo visible.
- Foco: `--shadow-focus` en todos los controles, también sobre el preview oscuro.
- Estrellas y tarjetas de asistencia como `radiogroup` / `radio` con valor leído en texto.
- `prefers-reduced-motion` apaga `breathe`, `glow`, `bob`, `level`, `spin` y el desplazamiento de la hoja.

## Copy (español de Colombia, tuteo)

«sala», nunca «room» · «Entrar a la sala» / «Abrir la sala» / «Entrar ahora» / «Entrar con Ana» · «Así te verás» · «Micrófono y cámara listos» · «Sala abierta» · «María te espera» · «No pasa nada. Entra cuando estés listo» · «Esta clase terminó a las 10:55» · «Hablaste 52 minutos en inglés.» · «¿Cómo te fue con María?» · «Ahora no» / «Enviar» · «Ana asistió» / «Ana no se presentó» · «Cerrar la clase» · «Se perdió la conexión. La clase no terminó.» · «Volver a la sala».
