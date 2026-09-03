# Handoff: Rigel — la mascota de Orión

## Overview

Rigel es el personaje que acompaña al usuario en Orión. Toma el nombre de la estrella más brillante de la constelación. Es una estrella antropomorfa de cinco puntas, con cara expresiva, brazos delgados, guantes blancos de caricatura y zapatos negros — en la línea de las mascotas clásicas de animación, adaptada a la identidad «Amanecer cálido premium».

Este paquete es **aditivo**: no cambia ninguna pantalla ya entregada en `design_handoff_orion_v2`. Solo añade el personaje y define dónde y cómo aparece.

## About the Design Files

`Orión Mascota · Rigel.dc.html` es una **referencia de diseño en HTML** — no código de producción. Pero, a diferencia del resto del paquete, **el SVG del personaje sí se copia literal**: es el asset. Extráelo del archivo (o de la sección *SVG canónico* de abajo) y conviértelo en un componente del codebase.

`support.js` es el runtime del entorno de diseño, necesario solo para abrir el `.dc.html` en el navegador. No va al producto.

## Fidelity

Alta fidelidad. El personaje es vector puro, sin imágenes rasterizadas: escala a cualquier tamaño sin pérdida.

**Decisión pendiente del cliente:** el tono. Hay dos versiones aprobadas para elegir — hasta que se confirme, implementa la **A · Dorado** y deja el color del cuerpo como una variable.

| Tono | Cuerpo | Contorno / extremidades | Rubor | Notas |
| --- | --- | --- | --- | --- |
| **A · Dorado** (por defecto) | `#FFCE3A` | `#2B2430` | `#FF8E76` @ 80% | Brilla y se lee como estrella al instante. Añade un color nuevo al sistema, siempre como personaje — nunca como color de interfaz. |
| **B · Durazno Orión** | `#FFC189` (`--color-accent-peach`) | `#33203B` (`--color-text`) | `#E8503A` @ 55% | Cero colores nuevos. Más suave y más «de la casa», menos brillo de caricatura. |

Guantes: `#FFFFFF` en el tono A, `#FFF6EE` en el B. Todo lo demás es idéntico entre tonos.

---

## Anatomía y construcción

El personaje está construido con **partes reutilizables**. Cada pose reordena piezas; **nunca se redibuja el personaje**. Esa es la regla que sostiene su identidad.

| Parte | Cómo se construye |
| --- | --- |
| Cuerpo | Un `<polygon>` de estrella de 5 puntas con `stroke-width: 26`, `stroke-linejoin: round` y `paint-order: stroke fill` del mismo color del relleno. Ese truco es lo que le da las puntas redondeadas — no hay curvas dibujadas a mano. |
| Brillo | Elipse crema al 45%, rotada −32°, arriba a la izquierda. |
| Ojos | Círculo oscuro r10 + reflejo crema r3.4 arriba a la izquierda + una estrellita crema dentro de la pupila. |
| Rubor | Dos elipses coral, rx10 ry6.5. |
| Boca | Path de sonrisa abierta relleno `#5A2436` + lengua `#FF8FA3`. |
| Brazos | Líneas `stroke-width: 7`, `stroke-linecap: round`, **dibujadas después del cuerpo** para que se vean. |
| Guantes | Círculo de pulgar (r5–5.5) + círculo principal (r12–13), ambos con contorno de 3 px. El pulgar va **del lado opuesto al brazo**. Encima, tres líneas de costura y un arco de puño. |
| Piernas y zapatos | Líneas de 7 px + elipses rx21 ry11 en `#2B2430`. |

**Orden de pintado (obligatorio):** resplandor → piernas → zapatos → cuerpo → brillo → rubor → ojos → boca → brazos → pulgares → guantes → costuras y puños → destellos.

El error más fácil de cometer es dibujar los brazos antes del cuerpo: quedan tapados por el `stroke` de 26 px del polígono y el personaje aparece sin brazos, con los guantes flotando como botones.

### Geometría del cuerpo

`viewBox="0 0 200 210"`. Estrella de 5 puntas centrada en (100, 96), radio exterior 60, radio interior 26, con una punta arriba:

```
100,36  115.3,75  157.1,77.5  124.7,104  135.3,144.5
100,122  64.7,144.5  75.3,104  42.9,77.5  84.7,75
```

---

## SVG canónico — pose «Saludo» (tono A)

Este es el asset base. Cópialo tal cual y parametriza los colores.

```html
<svg viewBox="0 0 200 210" role="img" aria-label="Rigel, la mascota de Orión, saludando">
  <!-- piernas y zapatos -->
  <g stroke="#33203B" stroke-width="7" stroke-linecap="round">
    <line x1="86" y1="142" x2="83" y2="167"></line>
    <line x1="114" y1="142" x2="117" y2="167"></line>
  </g>
  <ellipse cx="75" cy="176" rx="21" ry="11" fill="#2B2430"></ellipse>
  <ellipse cx="125" cy="176" rx="21" ry="11" fill="#2B2430"></ellipse>

  <!-- cuerpo -->
  <polygon points="100,36 115.3,75 157.1,77.5 124.7,104 135.3,144.5 100,122 64.7,144.5 75.3,104 42.9,77.5 84.7,75"
           fill="#FFCE3A" stroke="#FFCE3A" stroke-width="26"
           stroke-linejoin="round" paint-order="stroke fill"></polygon>
  <ellipse cx="80" cy="62" rx="17" ry="9" fill="#FFF6EE" opacity=".45" transform="rotate(-32 80 62)"></ellipse>

  <!-- cara -->
  <ellipse cx="70" cy="106" rx="10" ry="6.5" fill="#FF8E76" opacity=".8"></ellipse>
  <ellipse cx="130" cy="106" rx="10" ry="6.5" fill="#FF8E76" opacity=".8"></ellipse>
  <g class="rigel-eyes">
    <circle cx="85" cy="91" r="10" fill="#2B2430"></circle>
    <circle cx="115" cy="91" r="10" fill="#2B2430"></circle>
    <circle cx="81.5" cy="87" r="3.4" fill="#FFF6EE"></circle>
    <circle cx="111.5" cy="87" r="3.4" fill="#FFF6EE"></circle>
    <path d="M86.5 91.5l1.1 2.6 2.8.2-2.1 1.8.6 2.7-2.4-1.5-2.4 1.5.6-2.7-2.1-1.8 2.8-.2z" fill="#FFF6EE"></path>
    <path d="M116.5 91.5l1.1 2.6 2.8.2-2.1 1.8.6 2.7-2.4-1.5-2.4 1.5.6-2.7-2.1-1.8 2.8-.2z" fill="#FFF6EE"></path>
  </g>
  <path d="M86,101 Q100,125 114,101 Q100,108 86,101 Z" fill="#5A2436"></path>
  <path d="M93,112 Q100,122 107,112 Q100,116 93,112 Z" fill="#FF8FA3"></path>

  <!-- brazo derecho levantado (saluda) -->
  <g class="rigel-wave">
    <path d="M144,94 L166,71" stroke="#33203B" stroke-width="7" stroke-linecap="round" fill="none"></path>
    <circle cx="181" cy="77" r="5.5" fill="#FFFFFF" stroke="#33203B" stroke-width="3"></circle>
    <circle cx="172" cy="66" r="13" fill="#FFFFFF" stroke="#33203B" stroke-width="3"></circle>
    <g stroke="#33203B" stroke-width="2" stroke-linecap="round">
      <line x1="168" y1="60" x2="168" y2="68"></line>
      <line x1="172" y1="59" x2="172" y2="68"></line>
      <line x1="176" y1="60" x2="176" y2="67"></line>
      <path d="M163,73 Q172,79 181,72" fill="none" stroke-width="2.4"></path>
    </g>
  </g>

  <!-- brazo izquierdo en reposo -->
  <path d="M60,118 L37,139" stroke="#33203B" stroke-width="7" stroke-linecap="round" fill="none"></path>
  <circle cx="25" cy="152" r="5" fill="#FFFFFF" stroke="#33203B" stroke-width="3"></circle>
  <circle cx="34" cy="143" r="12" fill="#FFFFFF" stroke="#33203B" stroke-width="3"></circle>
  <g stroke="#33203B" stroke-width="2" stroke-linecap="round">
    <line x1="30" y1="138" x2="30" y2="145"></line>
    <line x1="34" y1="137" x2="34" y2="145"></line>
    <line x1="38" y1="138" x2="38" y2="144"></line>
    <path d="M26,150 Q34,155 42,149" fill="none" stroke-width="2.4"></path>
  </g>
</svg>
```

---

## Las cinco poses

Solo cambian **brazos, ojos y boca**. Cuerpo, guantes, piernas y zapatos son idénticos en las cinco. Todas están dibujadas a tamaño real en el archivo `.dc.html`.

| Pose | Dónde se usa | Qué cambia respecto a la base |
| --- | --- | --- |
| **Saludo** | Login, registro, bienvenida. La pose base. | — |
| **Celebración** | Reserva confirmada, cuenta verificada. | Ambos brazos arriba (`M58,96 L32,70` y `M142,96 L168,70`, guantes en (28,64) y (172,64)); ojos como arcos felices (`M77 93 Q85 82 93 93`); boca más abierta; tres destellos alrededor. |
| **Guía** | Estados vacíos y onboarding. | Brazo derecho horizontal señalando (`M148,96 L175,93`, guante en (180,93) con dedo índice extendido `M186 93 L196 93`); pupilas desplazadas a la derecha (85→88, 115→118); boca más pequeña. |
| **Espera** | «Revisa tu correo», cargas largas. | Brazos caídos a los lados; ojos cerrados (arcos hacia abajo `M76 91 Q85 98 94 91`); boca como línea suave; dos «z» flotando en lavanda. |
| **Ánimo** | Progreso, racha de clases, mensajes de aliento. | Brazo derecho con pulgar arriba (`M140,100 L154,84`, guante en (158,78) con pulgar extendido hacia arriba); sonrisa cerrada (arco, sin boca abierta). |

---

## Animación

Todas las animaciones se apagan bajo `prefers-reduced-motion: reduce`, dejando el personaje estático en su pose.

| Nombre | Qué anima | Duración | Curva |
| --- | --- | --- | --- |
| `bob` | Todo el personaje flota: `translateY(0 → −6px → 0)` | 3.6 s bucle | ease-in-out |
| `wave` | El grupo del guante levantado rota `0° → 16° → −10° → 0°`, con `transform-origin` en el codo (166px 74px) | 2.4 s bucle | ease-in-out |
| `blink` | El grupo de ojos hace `scaleY(1 → .1 → 1)` en el último 8% del ciclo, `transform-origin: 100px 91px` | 5 s bucle | ease-in-out |
| `sparkle` | Los destellos alternan `opacity .25 → 1` y `scale(.85 → 1)`, desfasados entre sí | 2.2–3.4 s bucle | ease-in-out |

```css
@keyframes bob     { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-6px)} }
@keyframes wave    { 0%,100%{transform:rotate(0deg)} 25%{transform:rotate(16deg)} 75%{transform:rotate(-10deg)} }
@keyframes blink   { 0%,92%,100%{transform:scaleY(1)} 96%{transform:scaleY(.1)} }
@keyframes sparkle { 0%,100%{opacity:.25; transform:scale(.85)} 50%{opacity:1; transform:scale(1)} }
```

---

## Dónde va — pantalla por pantalla

Implementado ya en el archivo de diseño:

- **Login 390** — hero del amanecer. Titular arriba a la izquierda, Rigel en la esquina inferior derecha, 146 px de alto. La constelación baja a la esquina inferior izquierda al 55% de opacidad. Pose: saludo.
- **Registro 1280** — panel de marca. Rigel arriba a la derecha, 236 px de alto; titular y subtítulo abajo a la izquierda, despejados. Pose: saludo. El subtítulo lo presenta: «Soy Rigel. Te acompaño desde tu primera clase hasta que hables sin pensarlo.»

Siguientes, con la misma lógica (pendientes de diseñar, descritas aquí para que no queden al criterio de cada implementación):

- **Confirmación de reserva** — pose celebración, junto al check dibujado, 140–170 px.
- **Verificación de cuenta exitosa** — pose celebración en el panel de marca.
- **«Revisa tu correo»** — pose espera, reemplazando el ícono de sobre actual.
- **Estados vacíos** (sin clases, sin resultados, sin cupos) — pose guía, señalando hacia el botón de acción.
- **404** — pose guía o ánimo, mirando hacia el hueco de la constelación.

**Regla de encuadre:** Rigel siempre se posiciona en absoluto dentro de su contenedor y **el texto nunca comparte su columna**. Si el titular y el personaje se cruzan, se mueve el personaje — nunca se encoge el titular.

---

## Reglas de uso

**Sí**

- Sobre el degradado del amanecer o sobre crema lisa.
- Una sola aparición por pantalla.
- Tamaño mínimo **96 px de alto** — por debajo, la cara se pierde.
- Acompañando momentos emocionales: bienvenida, logro, vacío, error.
- Con la animación sutil descrita arriba.

**No**

- Nunca en pantallas utilitarias: admin, disponibilidad, tablas.
- Nunca sobre un formulario ni junto al botón principal.
- Nunca como avatar de un profesor o de un estudiante.
- Nunca dando malas noticias con sonrisa — en errores va la pose de ánimo, nunca la de celebración.
- Nunca deformada, rotada ni recoloreada fuera de los dos tonos aprobados.
- Nunca recortada por un `overflow: hidden` a menos que el sangrado sea claramente intencional (mitad del cuerpo o más).

## Accesibilidad

El SVG lleva `role="img"` y un `aria-label` que describe la pose («Rigel, la mascota de Orión, saludando»). Cuando el personaje es puramente decorativo y su mensaje ya está en el texto de al lado, usa `aria-hidden="true"` en vez del label, para no duplicar el anuncio en lectores de pantalla.

## Files

- `Orión Mascota · Rigel.dc.html` — hoja de personaje: los dos tonos, las cinco poses a tamaño real, las dos pantallas aplicadas y las reglas de uso. **Es la fuente de verdad del SVG.**
- `support.js` — runtime del entorno de diseño. No es parte del producto.

Ábrelo en un navegador: es un lienzo, se puede hacer zoom y desplazarse libremente.
