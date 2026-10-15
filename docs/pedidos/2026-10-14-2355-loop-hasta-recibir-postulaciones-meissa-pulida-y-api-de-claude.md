# En loop hasta recibir postulaciones, Meissa muy pulida y la API de Claude para todo el texto

## Metadatos

- **Fecha y hora:** 2026-10-14 23:55 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Briefs relacionados:** `docs/plan-de-lanzamiento.md` (v3), `docs/briefs/orion-brief-frances.md`,
  `docs/briefs/orion-bloque-12-adolescentes.md`, `docs/prueba-fase-1.md`
- **Commits relacionados:** `f2731d4` (Claude), `89ab07b` (swagger), `64833d2`, `d31b0c7`, `e47c19b`, `754887a`,
  `352cdd2`, `19c9b62` (Bloque 12 y Meissa)

## El pedido exacto

> ¿Pueds mantenerte en loop implementando esto a la perfección, haciendo pruebas y dejando absolutamente TODO listo para que empecemos a recibir postulaciones y a aprobar profesores? También quiero que refines lo más que puedas la parte de Meissa, debe quedar MUY pulido, por favor. Y, por otro lado, necesito que uses la API de Claude para todo lo que es acta, ejercicios, la parte de texto de diagnóstico, TODO lo que puedas, yo te paso la API, ¿te parece bien? ¿Dónde te la dejo subida? Y, ¿alguna pregunta/pregunta(s) antes de que me vaya a dormir?

## Resumen ejecutivo

- **Como** dueño de Orión, **quiero** que Claude siga trabajando en bucle, con pruebas, hasta que todo
  lo necesario para **recibir postulaciones y aprobar profesores** esté listo, **para** abrir a
  profes el 19/10 sin sorpresas.
- **Como** dueño, **quiero** la experiencia de **Meissa** (el diagnóstico hablado) muy pulida **para**
  que la primera impresión de Orión sea impecable.
- **Como** dueño, **quiero** que **la API de Claude** haga todo el trabajo de texto con IA (el acta, los
  ejercicios de práctica, el resumen y las revisiones del diagnóstico, y lo demás que se pueda)
  **para** depender menos de OpenAI. La **voz** de Meissa sigue en OpenAI (Realtime): Claude no tiene
  API de voz en tiempo real.

Features: (1) QA de punta a punta del embudo del profesor (Enseña con Orión → postulación → revisión →
aprobación → perfil → horarios → Bre-B → publicado) con arreglos; (2) refinamiento de Meissa; (3)
proveedor Claude para acta, práctica, revisores, resumen del diagnóstico y traducción en vivo, con
OpenAI como respaldo configurable; (4) terminar francés (probado con llaves reales) y el Bloque 12.

## Dónde van las llaves

- **Claude:** en `~/.orion/anthropic-key` (un archivo con la llave y nada más, permisos 600), escrito
  desde la terminal de Pardo, nunca por el chat. En producción, la variable `ANTHROPIC_API_KEY` en
  Railway, en el servicio del backend.
- **OpenAI (nueva):** en `~/.orion/openai-key`, igual; en Railway, `OPENAI_API_KEY`. Hace falta para
  la voz de Meissa aunque el texto pase a Claude.

## Decisiones de Pardo (14/10/2026, 22:55, eligiendo entre opciones)

1. **Modelos de Claude:** «Opus 5.5 para acta y práctica; Haiku 5.5 para lo rápido». Opus 5.5
   (`claude-opus-5-5`) para el acta, los ejercicios de práctica y el resumen del diagnóstico; Haiku
   5.5 (`claude-haiku-5-5`) para la traducción en vivo de Meissa y los revisores. La voz de Meissa
   sigue en OpenAI Realtime.
2. **Meissa, qué pulir:** todo: la conversación (guion, tono, ritmo, cierre), la pantalla (estados,
   subtítulo, traducción, errores), el resultado (texto, puntaje, los tres profes), la robustez
   (cortes, reintentos, límites de OpenAI) **y, en sus palabras, «las interrupciones, la lectura de la
   conversación, la experiencia de usuario»**.
3. **Decisiones mientras duerme:** «Decido lo más conservador y te lo listo». Cada una queda en este
   archivo, en la sección «Decisiones tomadas por Claude», con las alternativas.
4. **Llaves:** «Te dejo la de Claude, confírmame que la tienes. La de OpenAI sí mañana». Las pruebas
   reales de la voz de Meissa y del francés hablado esperan a la llave de OpenAI.

**23:05, Pardo:** «Me voy a dormir, no pidas absolutamente nada más. Sigue trabajando en loop hasta las
8am, con todo lo que te he pedido ya.» Desde aquí no hay más preguntas: cada decisión va abajo.

## Decisiones tomadas por Claude (conservadoras, para revisar por la mañana)

1. **Cómo entra Claude (23:10–00:20).** Proveedor de texto `orion.ai.text-provider` =
   `${ORION_TEXT_PROVIDER:${ORION_VOICE_PROVIDER:scripted}}`: sin la variable nueva, producción sigue
   como estaba (OpenAI para el texto si la voz es OpenAI). **Para encender Claude en Railway:**
   `ANTHROPIC_API_KEY` y `ORION_TEXT_PROVIDER=claude`. Alternativa descartada: Claude por defecto en
   cuanto haya llave (cambiaría producción sin que nadie lo pida).
2. **Esfuerzo y cortes.** Acta: Opus, esfuerzo bajo, el mismo corte `ai_note_timeout_seconds`, un
   reintento. Práctica: Opus, esfuerzo medio, 90 s (corre en segundo plano); su revisión: Haiku. Resumen
   del diagnóstico: Opus, esfuerzo bajo, 25 s. Traducción en vivo y «Tu frase»: Haiku sin razonamiento,
   5 y 8 s. Sin reintentos del SDK (`maxRetries(0)`): cada función ya decide. Alternativa: Opus con
   esfuerzo alto en todo (mejor texto, más lento que los cortes de la pantalla).
3. **Sin «fallbacks» de servidor ante un rechazo del modelo.** Un `stop_reason: refusal` se trata como
   salida inválida y se cae al camino manual (el profe escribe el acta; la práctica sale sin IA). La
   alternativa —`fallbacks: "default"` por la API beta— es un parámetro que no probé; va en la ronda de
   pulido si aparece algún rechazo en el uso real.
4. **`httpclient5` excluido del SDK.** Llegaba de paso y, con él en el classpath, Spring cambiaba su
   cliente HTTP por defecto (y el de las pruebas) a uno con jarra de cookies: 105 pruebas de integración
   rojas por sesiones mezcladas. El SDK habla por OkHttp, así que no le hace falta.
5. **Textos legales 1.2 vigentes desde el 14/10/2026** (misma fecha para la autorización 1.0 y el
   código de conducta 1.0), publicados por el sembrador, como las versiones anteriores. Quien entre
   verá «Aceptar los nuevos acuerdos» una vez, como con la 1.1.
6. **Los puntos de una clase de un adolescente, mientras no exista el paso 10 (00:25).** Hoy la clase
   dictada «para Sofi» le suma los 25 puntos al libro del adulto, porque la reserva es suya y el paso 10
   (puntos propios por hijo) es la última pieza del bloque. Contradice la decisión 10 solo en apariencia:
   no hay estudiantes abiertos todavía, y el paso 10 moverá esos puntos al perfil del hijo (columna
   `adolescent_profile_id` en `point_events`, el total del adulto deja de contarlos). Lo dejo escrito
   para que lo leas, no para que lo descubras.

## Lo hecho durante la noche (se va completando)

- **Claude** (`f2731d4`): acta, práctica, resumen del diagnóstico, traducción en vivo y revisores, con
  pruebas contra un servidor de mentira. En Railway: `ANTHROPIC_API_KEY` + `ORION_TEXT_PROVIDER=claude`.
- **Bloque 12, pasos 1 a 9** (`64833d2`, `d31b0c7`, `e47c19b` y el reporte de seguridad): la base,
  los perfiles con autorización, la habilitación de profes, las tres puertas, el apagado de la IA,
  todas las pantallas (Cuenta, perfil del profe, reservar «para», Mis clases, antesala, Usuarios,
  buscador) y el reporte de seguridad. Faltan los puntos por hijo (10) y lo de los 18 años (11).
- **Meissa, primera ronda de pulido** (sin la llave de OpenAI, todo lo que no necesita voz real):
  - **Interrupciones de verdad:** si la persona habla encima de Meissa, se cancela la respuesta y se
    vacía el audio pendiente (`response.cancel` + `output_audio_buffer.clear`). Antes, por WebRTC el
    audio ya enviado seguía sonando encima de la persona.
  - **Errores con nombre:** permiso del micrófono negado, sin micrófono, micrófono ocupado y caída de
    la conexión (estado `failed`/`disconnected` por más de 6 s) tienen su aviso en español; antes
    todo era «se cortó la conversación».
  - **La espera inicial** dice «Meissa te saluda en un momento…» en vez de «…».
  - **Releer la conversación: NO se hizo, a propósito.** Lo construí y lo quité: una prueba
    (`DiagnosticoIT.laTranscripcionNoSale`) protege una regla deliberada del 22/09: la transcripción
    de lo que dice la persona nunca sale del servidor por la API. Cambiarla es decisión tuya, no mía.
    Si con «la lectura de la conversación» querías releerla al final, dímelo y lo enciendo (está
    escrito, es un commit). Si querías el subtítulo durante la charla, eso ya existe y lo pulí.
  - **Pendiente con la llave real:** el guion v6 (tono, ritmo, cierre, francés), medir latencias y
    probar el francés hablado.

- **Verificación de la madrugada (00:10–01:00):** `next build` en verde; e2e completo sobre base limpia,
  107 de 107 (el único rojo era un selector del spec nuevo, corregido); el spec de adolescentes reserva
  la prueba gratis para Sofi y comprueba que la madre conserva la suya, lo que destapó que la pantalla
  preguntaba la disponibilidad de la prueba sin decir para qué hijo (`19c9b62`); capturas de las diez
  pantallas nuevas a 390 y 1280 px, con un desborde de la fila del adolescente a 390 px arreglado.

## Estado

**En curso.** Pardo duerme; el loop sigue solo hasta las 8:00 y termina con el correo de resumen.
