# Handoff: Orión · Design v2 — «Amanecer cálido premium»

## Overview

Orión es una plataforma de reserva de clases de inglés (estudiantes, profesores y admin). Este paquete entrega la **identidad visual v2 y su sistema de componentes**: la dirección elegida es **1b · Amanecer cálido premium** — el cielo justo antes del amanecer, donde la noche de Orión se disuelve en coral y durazno. Formas pill, sombras tintadas de ciruela, energía optimista y el degradado del amanecer como firma de marca.

Objetivo del rediseño: que el producto se sienta **premium y cálido** (calidez que da confianza para hablar), no genérico ni corporativo.

## About the Design Files

Los archivos `.dc.html` de este bundle son **referencias de diseño creadas en HTML** — prototipos que muestran la apariencia y el comportamiento buscados, **no código de producción para copiar directamente**.

La tarea es **recrear estos diseños en el entorno existente del codebase destino** (React, Vue, Next, SwiftUI, nativo…) usando sus patrones y librerías establecidas. Si aún no existe entorno, elige el framework más apropiado para el proyecto e implementa allí. El bloque `@theme` de la sección *Design Tokens* está escrito para **Tailwind CSS v4**; si el proyecto usa otra solución de estilos, tradúcelo a su equivalente (CSS variables, tokens de tema, etc.) conservando los nombres semánticos.

Los archivos abren en cualquier navegador. `support.js` es el runtime del entorno de diseño — **no es parte del producto**, no lo lleves al codebase.

## Fidelity

**Alta fidelidad (hifi).** Colores, tipografía, espaciado, radios, sombras, estados e interacciones son finales y están tokenizados. Recrea la UI con fidelidad de píxel usando las librerías del codebase.

El paquete está completo:

1. **Fundamentos** — tokens + biblioteca de componentes con todos sus estados (`Orión Fundamentos v2.dc.html`).
2. **Las 13 pantallas del MVP**, cada una dibujada en 390 px Y en 1280 px — diseñadas por breakpoint, no encogidas (los tres archivos `Orión Pantallas v2 · …`).
3. **Guía responsive por patrón** que describe cómo se transforma cada layout entre 390 y 1280 px.

Los marcos de las pantallas se muestran al 65% dentro del lienzo para poder compararlos; **sus medidas reales son 390 y 1280 px** (cada marco lleva su etiqueta). Toma las medidas de los valores en el código, no de lo que mide en pantalla.

---

## Design Tokens

Bloque canónico para Tailwind v4. Es la fuente de verdad de todo valor del sistema.

```css
@theme {
  /* — Marca y acción — */
  --color-primary: #E8503A;
  --color-primary-strong: #C93A26;
  --color-primary-soft: #FDE4E0;
  --color-on-primary: #FFF6EE;
  --color-accent-lavender: #B9A7E6;
  --color-accent-lavender-soft: #EFE9F9;
  --color-accent-peach: #FFC189;
  --color-accent-peach-soft: #FFE9D6;
  --color-night: #2E1E4E;

  /* — Superficies y bordes — */
  --color-surface: #FFF6EE;
  --color-surface-raised: #FFFFFF;
  --color-surface-sunken: #F4EAE0;
  --color-border: #EADFD4;
  --color-border-strong: #D8C7B8;

  /* — Texto — */
  --color-text: #33203B;
  --color-text-secondary: #5E4E6B;
  --color-text-muted: #7A6B85;
  --color-text-on-night: #FFF6EE;

  /* — Estados — */
  --color-success: #2E6B4A;      --color-success-bg: #DEF3E7;
  --color-warning: #8A5A33;      --color-warning-bg: #FFE9D6;
  --color-error: #B03024;        --color-error-bg: #FDE4E0;
  --color-info: #5E4A8A;         --color-info-bg: #EFE9F9;

  /* — Tipografía — */
  --font-display: "Bricolage Grotesque", ui-sans-serif, system-ui, sans-serif;
  --font-sans: "Figtree", ui-sans-serif, system-ui, sans-serif;
  --text-display: clamp(2.125rem, 1.4rem + 3.2vw, 3.25rem);
  --text-h1: clamp(1.75rem, 1.25rem + 2.1vw, 2.5rem);
  --text-h2: clamp(1.375rem, 1.1rem + 1.1vw, 1.75rem);
  --text-h3: clamp(1.125rem, 1rem + 0.5vw, 1.375rem);
  --text-body: clamp(0.9375rem, 0.9rem + 0.2vw, 1rem);
  --text-sm: 0.8125rem;
  --text-label: 0.75rem;

  /* — Espaciado (base 4) — */
  --space-1: 4px;   --space-2: 8px;   --space-3: 12px;
  --space-4: 16px;  --space-5: 20px;  --space-6: 24px;
  --space-8: 32px;  --space-10: 40px; --space-14: 56px;
  --space-18: 72px; --space-24: 96px;

  /* — Radios — */
  --radius-sm: 8px;
  --radius-base: 14px;
  --radius-card: 24px;
  --radius-pill: 999px;

  /* — Elevación — */
  --shadow-sm: 0 1px 2px rgba(51,32,59,.06);
  --shadow-md: 0 4px 12px rgba(51,32,59,.08);
  --shadow-lg: 0 18px 44px rgba(51,32,59,.10);
  --shadow-primary: 0 10px 24px rgba(232,80,58,.35);
  --shadow-focus: 0 0 0 4px rgba(232,80,58,.22);

  /* — Motion — */
  --duration-fast: 140ms;
  --duration-base: 220ms;
  --duration-slow: 380ms;
  --ease-standard: cubic-bezier(0.2, 0.8, 0.2, 1);
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in: cubic-bezier(0.4, 0, 1, 1);

  /* — Layout — */
  --tap-min: 44px;
  --header-h: 64px;
  --tabbar-h: 68px;
  --sidebar-w: 248px;
}
```

### Firma de marca — degradado del amanecer

```css
--gradient-dawn: linear-gradient(180deg, #2E1E4E 0%, #7A4A8C 46%, #E8764F 82%, #FFC189 100%);
```

Vive **solo** en paneles de marca y heros de autenticación. Nunca detrás de texto de lectura, nunca como fondo de pantalla completa.

### Avatares

Iniciales sobre `linear-gradient(135deg, #E8764F, #B9A7E6)`, texto `--color-on-primary`, `font-display` 800, `border-radius: 50%`. Tamaños: 34px (sidebar), 46–56px (listados), 88–92px (perfil).

### Tipografía — cargas

Google Fonts: `Bricolage Grotesque` (400–800, variable `opsz 12..96`) y `Figtree` (400–800).

| Token | Familia · peso | Tamaño | line-height | Uso |
| --- | --- | --- | --- | --- |
| display | Bricolage 700 | 34→52px | 1.08 | Hero de auth |
| h1 | Bricolage 700 | 28→40px | 1.15 | Título de pantalla |
| h2 | Bricolage 700 | 22→28px | 1.25 | Título de sección |
| h3 | Bricolage 600 | 18→22px | 1.30 | Nombre de profesor, título de card |
| body | Figtree 400 | 15→16px | 1.65 | Texto corrido (`text-wrap: pretty`) |
| sm | Figtree 500 | 13px | 1.55 | Texto de apoyo, metadatos |
| label | Figtree 700 | 12px, `letter-spacing:.04em` | — | Labels de formulario y de grupo |
| cifras | Bricolage 800 | 22–28px | 1.1 | Estadísticas («120+», «4 años») |

### Accesibilidad (reglas duras)

- Ningún target táctil por debajo de **44px**, incluidos los chips de hora.
- Contraste AA en todo texto. `--color-text` 12.4:1, `--color-text-secondary` 7.1:1, `--color-text-muted` 4.6:1 sobre `--color-surface`.
- **El coral `#E8503A` nunca se usa para texto pequeño sobre fondo claro** — para eso, `--color-primary-strong` `#C93A26`.
- Focus visible diseñado en **todo** elemento interactivo: `--shadow-focus` (ring de 4px, coral al 22%), siempre por fuera del borde, vía `:focus-visible`.
- **Un solo elemento coral por grupo.** Si el botón principal es coral, el chip seleccionado usa tinta ciruela `#33203B`.
- Toda animación se apaga bajo `prefers-reduced-motion: reduce`, dejando el estado final visible sin transición.

---

## Componentes

Todos los estados están dibujados en `Orión Fundamentos v2.dc.html`. Especificación:

### Botón

Altura **52px** (44–48px en variantes compactas), `border-radius: var(--radius-pill)`, `font-sans` 700, 15px, padding horizontal 26px cuando no es full-width.

| Variante | Normal | Hover | Focus | Active | Loading | Disabled |
| --- | --- | --- | --- | --- | --- | --- |
| Primario | bg `--color-primary`, texto `--color-on-primary`, `--shadow-primary` | bg `--color-primary-strong` | + `--shadow-focus` | bg `--color-primary-strong`, `scale(.98)`, sombra reducida a `0 4px 10px rgba(232,80,58,.28)` | spinner 16px (borde 2px, `rgba(255,246,238,.35)` con `border-top-color` `--color-on-primary`, `spin .7s linear infinite`) + copy en gerundio («Entrando…») | `opacity: .42`, `cursor: not-allowed` |
| Secundario | transparente, borde 1.5px `--color-primary`, texto `--color-primary-strong` | bg `--color-primary-soft` | + `--shadow-focus` | bg `#FBD3CC`, borde `#C93A26`, texto `#A32A1D`, `scale(.98)` | spinner en `--color-primary-strong` | `opacity: .42` |
| Fantasma | transparente, texto `--color-text-secondary`, 600 | bg `--color-surface-sunken`, texto `--color-text` | + `--shadow-focus` | bg `--color-border`, `scale(.98)` | spinner en `--color-text-secondary` | `opacity: .42` |
| Destructivo | transparente, borde 1.5px `#F0BEB6`, texto `--color-error` | bg `--color-error-bg` | + `--shadow-focus` | `scale(.98)` | — | `opacity: .42` |

Variantes adicionales: **con ícono** (gap 10px, ícono 20px antes del label), **compacto** 44px (14px de texto, padding 20px), **icon-only** 48px circular sobre `--color-surface-sunken` (hover `--color-border`).

Transición: `--duration-fast` con `--ease-standard`. Todo botón lleva `white-space: nowrap` — las etiquetas nunca se parten dentro de la píldora.

### Input

Altura **52px**, padding `0 18px`, `border-radius: var(--radius-base)`, borde 1.5px `--color-border`, fondo `--color-surface` (o `--color-surface-raised` cuando va sobre superficie tintada), texto 15px `font-sans`.

| Estado | Borde | Extras |
| --- | --- | --- |
| Normal | `--color-border` | Texto de ayuda en `--color-text-muted` 12px |
| Focus | `--color-primary` | `--shadow-focus`, fondo `--color-surface-raised` |
| Válido | `#7FC49F` | Ícono `check` 20px en `--color-success` a la derecha (padding-right 46px); mensaje `--color-success` 12px/600 |
| Error | `--color-error` | `box-shadow: 0 0 0 4px rgba(176,48,36,.14)`, ícono `circle-alert` en `--color-error`; mensaje `--color-error` 12px/600 |
| Disabled | `--color-border` | fondo `--color-surface-sunken`, texto `--color-text-muted`, `opacity: .75`, `cursor: not-allowed` |

**Validación en vivo:** el campo valida al salir del foco (`blur`) y se re-valida en cada tecla una vez marcado como inválido — nunca se muestra error mientras el usuario escribe por primera vez. Reglas: correo requiere `usuario@dominio.tld`; contraseña mínimo 8 caracteres.

**Medidor de contraseña:** 4 segmentos de 5px, `border-radius: pill`, gap 5px. Segmentos activos en `--color-success`, inactivos en `--color-border`. Nivel = longitud ≥8 (1) + mayúscula y minúscula (2) + número (3) + símbolo (4). Mensaje descriptivo debajo, nunca solo «débil/fuerte».

**Textarea:** mismo estilo, `padding: 14px 18px`, `resize: vertical`, 3 filas por defecto.
**Select:** mismo estilo, `appearance: none` + ícono `chevron-down` 20px `--color-text-muted` a la derecha, `pointer-events: none`.

### Chips (selección de fecha y hora)

Píldoras. La clave del sistema: **cada grupo tiene su familia de color**, para que el ojo distinga «qué día» de «qué hora» sin leer labels.

**Fechas — familia durazno:** normal bg `--color-accent-peach-soft` / texto `#8A5A33` / 600; hover bg `#FFDCBB`; focus + `--shadow-focus`; **seleccionado bg `#33203B` / texto `--color-on-primary` / 700**; sin cupo bg `#F4EFE9` / texto `#BBAF9F` / `line-through` / no interactivo.

**Horas — familia lavanda:** normal bg `--color-accent-lavender-soft` / texto `#5E4A8A` / 600; hover bg `#E2D7F4`; **seleccionado bg `--color-primary` / texto `--color-on-primary` / 700 + glow en bucle** (`@keyframes glow`, 2.4s ease-in-out, sombra oscilando entre `0 8px 18px rgba(232,80,58,.3)` y `0 8px 26px rgba(232,80,58,.5)`); sin cupo igual que en fechas.

Padding: 11px 17px (fechas) / 13px 0 centrado en grid (horas). Grid de horas: 3 columnas en móvil, 3–4 en desktop, gap 10px. `white-space: nowrap` obligatorio.

### Badge

Píldora, padding `7px 13px`, 12px/700. Con punto de 7px del mismo color del texto cuando denota estado de clase.

`Confirmada` success · `Pendiente` warning · `Cancelada` error · `Impartida` info · neutros (`Virtual`, `A1–B1`) bg `--color-surface-sunken` / texto `--color-text-secondary`, sin punto.

### Tabs

Fila con gap 26px sobre `border-bottom: 1.5px solid --color-surface-sunken`. Activa: texto `--color-text` 700 + `border-bottom: 2.5px solid --color-primary` (con `margin-bottom: -1.5px`). Inactiva: `--color-text-secondary` 600, hover a `--color-text`. Deshabilitada: `#BBAF9F`, `cursor: not-allowed`.

### Toggle / segmented

**Segmented:** pista `--color-surface-sunken`, `border-radius: pill`, padding 4px, gap 4px. Opción activa bg `--color-surface-raised`, texto `--color-text` 700, `box-shadow: 0 2px 8px rgba(51,32,59,.12)`. Inactiva `--color-text-muted` 600.

**Switch:** 52×30px, `border-radius: pill`, padding 3px. Encendido bg `--color-primary` con pulgar a la derecha; apagado bg `--color-border` con pulgar a la izquierda. Pulgar 24px circular blanco. Transición `--duration-base` / `--ease-standard`.

### Card

`border-radius: var(--radius-card)` (24px), fondo `--color-surface-raised`, padding 24px, gap interno 14–16px. **Nunca borde de acento a la izquierda.**

- **Card de profesor:** `--shadow-md`; hover `--shadow-lg` + `translateY(-2px)`. Avatar 56px + nombre h3 + especialidad `sm`; fila de badges; pie con próximo cupo (`sm` 600) y botón compacto «Reservar».
- **Card de clase próxima:** `--shadow-sm`. Fecha/hora en h3 Bricolage 700 20px, «con María P. · 50 min» en body; badge de estado arriba a la derecha; línea con ícono `video`/`map-pin` explicando modalidad; acciones: primario «Entrar a la clase» + fantasma con borde «Reprogramar».
- **Card de aviso (cancelación <24 h):** fondo `--color-surface`, borde `1.5px dashed --color-border`; dentro, bloque `--color-warning-bg` `border-radius: 16px` padding 16px con ícono `clock` y copy en `--color-warning`.

### Skeleton

Silueta **exacta** del contenido real (mismos tamaños y radios) rellena con
`linear-gradient(90deg, #F4EAE0 25%, #FBF2EA 50%, #F4EAE0 75%)`, `background-size: 420px 100%`, `animation: shimmer 1.4s linear infinite` (`@keyframes shimmer { 0%{background-position:-420px 0} 100%{background-position:420px 0} }`). Escalona el `animation-delay` 0.1s por elemento. Bajo `reduced-motion`: estático en `#F4EAE0`.

### Estado vacío

Centrado, gap 14px: constelación de Orión en líneas `--color-border` 1.4px con estrellas en los tres acentos (dos de ellas con `twinkle`), h2 Bricolage 700 22px, párrafo body `--color-text-secondary` máx. 340px, botón primario 48px. Copy con la voz de marca: «Aún no tienes clases» + «Explora los profesores y reserva la primera. Cada proceso es diferente; lo importante es empezar.»

### Toast

Fondo `--color-night` `#33203B`, `border-radius: 16px`, padding `15px 18px`, texto `--color-on-primary` 14px/600, `--shadow-lg` reforzada (`0 18px 44px rgba(51,32,59,.24)`). Ícono 20px a la izquierda: éxito `#8FE0B4`, error `#FFB3A6`, info `#D5C8F5`. Cierre opcional a la derecha en `rgba(255,246,238,.6)`.

**Un solo toast a la vez, 5s**, `aria-live="polite"`. Entra desde abajo en móvil (sobre las tabs) y desde la derecha en desktop (esquina inferior derecha); sale con fade `--duration-fast`.

### Modal

Backdrop `rgba(51,32,59,.45)` con fade. Panel `--color-surface-raised`, `radius-card`, padding 28px, máx. **440px**, `--shadow-lg` reforzada. Entrada: `opacity 0→1` + `scale(.96→1)` + `translateY(12px→0)` en `--duration-base` / `--ease-out`.

Estructura: título h2 Bricolage 700 22px → párrafo body `--color-text-secondary` → acciones en fila (fantasma con borde «Mantener» + destructivo «Sí, cancelar»), ambas 48px y `flex: 1`.

Comportamiento: foco atrapado dentro del modal, `Esc` cierra, el foco vuelve al disparador, `aria-modal="true"` + `aria-labelledby`. En móvil se presenta como **hoja inferior** a todo el ancho con radio solo en las esquinas superiores.

### Tabla (admin)

Utilitario elevado: densidad mayor que el resto del producto, cero decoración. Contenedor `radius-card` + `--shadow-md`, `overflow: hidden`.

Cabecera de tarjeta: título h3 + búsqueda (input píldora de 40px con ícono `search` a 14px del borde) + botón «Filtros» fantasma con borde de 40px.
Header de tabla: fondo `--color-surface`, texto `--color-text-muted` 11px/700 `letter-spacing:.1em` mayúsculas, padding `13px 24px`; **sticky** al hacer scroll.
Filas: padding 16px, altura mínima **56px**, separadas por `1px solid --color-surface-sunken`. Zebra opcional en `#FFFBF7`. Hover de fila `--color-surface`. Última celda: menú `dots-three-vertical` alineado a la derecha.
Columnas: Estudiante · Profesor · Fecha · Modalidad · Estado (badge) · acciones — `1.4fr 1.2fr 1fr .9fr .8fr .5fr`.

### Navegación

**Móvil (390):** header 64px con logotipo «ORIÓN ✦» en `--color-primary` Bricolage 800 16px + campana (target 44px) + avatar 36px; borde inferior `1px solid --color-surface-sunken`. Tab bar inferior 68px + `env(safe-area-inset-bottom)`, 4 entradas, cada una ícono 21px + label 11px. **Activa:** ícono relleno de coral dentro de píldora `--color-primary-soft` (padding `5px 14px`) + label 700 coral. Inactiva: `--color-text-muted` 600, ícono en trazo.

**Desktop (1280):** sidebar fija de **248px**, fondo `--color-surface`, borde derecho `1px solid --color-surface-sunken`, padding `24px 16px`, sin tabs. Entradas de 48px, `border-radius: pill`, gap 12px con ícono 20px. Activa: bg `--color-primary-soft`, texto `--color-primary-strong` 700, ícono relleno. Hover: bg `--color-surface-sunken`. Separadores de sección: línea de 1px + label 11px/700 mayúsculas en `--color-text-muted`. Al pie, tarjeta de usuario píldora blanca con avatar 34px + nombre 13px/700 + rol 11px `--color-text-muted`.

El activo **nunca se marca solo por color** — siempre color + peso + relleno de ícono.

**Entradas por rol:**
- Estudiante: Profesores · Mis clases · Mensajes · Perfil
- Profesor: Mis clases · Disponibilidad · Mensajes · Mi perfil
- Admin: Reservas · Profesores · Estudiantes · Ajustes

### Iconografía

**Lucide**, `stroke-width: 1.75`, `stroke-linecap`/`linejoin: round`. 20px junto a texto, 21–26px en navegación y acciones, área táctil 44px siempre. Los íconos toman el color del texto que acompañan (`currentColor`) — nunca coral sobre coral. El ícono de la tab activa es la única excepción: relleno.

Set usado: `calendar`, `clock`, `video`, `map-pin`, `mail`, `user`, `search`, `pencil`, `bell`, `check`, `x`, `star`, `chevron-down`, `circle-alert`, `triangle-alert`, `circle-info`, `eye`, `settings`, `dots-three-vertical`.

### Constelación de marca

Ocho estrellas de Orión con las líneas del asterismo, en `viewBox="0 0 120 130"`:

```
Meissa 58,8 · Betelgeuse 28,26 · Bellatrix 88,20
Cinturón: Alnitak 50,58 · Alnilam 58,63 · Mintaka 66,68
Saiph 34,112 · Rigel 92,106
Líneas: 58,8→28,26 · 58,8→88,20 · 28,26→50,58 · 88,20→66,68
        50,58→58,63 · 58,63→66,68 · 50,58→34,112 · 66,68→92,106
```

Sobre el degradado del amanecer: líneas `rgba(255,255,255,.32)` 0.9px, estrellas `--color-on-primary` (Betelgeuse y Rigel en `--color-accent-peach`, radio mayor). Animación: `twinkle` de opacidad `1 → .2 → 1` por estrella, duraciones 3–5s con fases desfasadas; **sin movimiento de posición**.

---

## Interactions & Behavior

### Motion — tabla canónica

| Qué | Cómo se anima | Duración | Easing |
| --- | --- | --- | --- |
| Botón hover/active | `background` + `scale(.98)` al presionar | 140ms | ease-standard |
| Chip seleccionable | `background` + `color`; el seleccionado recibe glow en bucle | 140ms (glow 2.4s) | ease-standard |
| Input focus | `border-color` + ring de 4px | 140ms | ease-standard |
| Entrada de página / lista | `opacity 0→1` + `translateY(8px→0)`, escalonado 40ms por ítem (máx. 6) | 220ms | ease-out |
| Modal | backdrop fade; panel `scale(.96→1)` + `translateY(12px→0)` | 220ms | ease-out |
| Toast | entra desde abajo (móvil) o derecha (desktop); sale con fade 140ms | 220ms | ease-out |
| Skeleton | shimmer horizontal en bucle; se reemplaza por contenido con fade | 1400ms bucle | linear |
| Constelación | twinkle de opacidad por estrella, fases desfasadas | 3–5s bucle | ease-in-out |
| **Confirmación de reserva** | el momento de deleite: check dibujado con `stroke-dashoffset` + tres estrellas que aparecen escalonadas | 380ms | ease-out |

El movimiento **confirma acciones y orienta; nunca decora.**

### Flujo — reserva de clase (el flujo crítico)

1. Lista de profesores → tap en card o «Reservar» → pantalla de reserva del profesor.
2. La agenda carga con skeleton; por defecto **no** hay día preseleccionado.
3. Seleccionar día → el grid de horas se recarga con las horas de ese día (fade 220ms). La selección de hora previa se limpia.
4. Seleccionar hora → el chip pasa a coral con glow; la barra de confirmación se activa y muestra el resumen `mié 16 · 10:00 · Virtual · María P.`.
5. Modalidad (segmented) y nota (opcional). Si es Presencial, el campo de lugar pasa a requerido.
6. «Confirmar reserva» → botón en loading → animación de éxito (check dibujado + estrellas) → toast «Clase reservada — te enviamos la confirmación» → navegación a Mis clases con la nueva clase resaltada.
7. **Cupo ocupado en carrera:** si el slot se ocupó entre selección y confirmación, el botón sale de loading, el chip pasa a estado «sin cupo» y aparece el toast de error «Ese cupo se acaba de ocupar — elige otro horario». No se pierde el resto del formulario.

El botón de confirmar está **deshabilitado** hasta tener día + hora. En móvil la barra de confirmación es sticky al fondo (resumen + botón full-width).

### Flujo — cancelación

Cancelar abre el modal de confirmación. **Si faltan menos de 24 h**, no se ofrece cancelar: se muestra la card de aviso («Faltan menos de 24 h — la clase se considera impartida») con la acción alternativa «Escribir a [profesor]».

### Estados que toda pantalla debe implementar

**Loading** (skeleton con la silueta real) · **Vacío** (constelación + copy de aliento + acción) · **Error de carga** (mensaje + «Reintentar») · **Error de campo** (validación en vivo, ver Input) · **Éxito** (toast; animación de deleite solo en la confirmación de reserva) · **Offline / sin permiso** cuando aplique.

---

## State Management

Por pantalla, el estado mínimo:

- **Auth:** `email`, `password`, `showPassword`, `errors: {field → message}`, `touched`, `submitting`, `authError`.
- **Lista de profesores:** `filters: {nivel, modalidad, disponibilidad}`, `query`, `teachers`, `loading`, `error`, `page`.
- **Reserva:** `teacher`, `selectedDate`, `selectedSlot`, `modality: 'virtual'|'presencial'`, `note`, `slotsByDate`, `loadingSlots`, `submitting`, `conflictError`.
- **Mis clases:** `tab: 'proximas'|'pasadas'|'canceladas'`, `classes`, `loading`, `cancelTarget` (para el modal), `cancelling`.
- **Disponibilidad (profesor):** `weekStart`, `blocks: Set<'YYYY-MM-DD|HH:mm'>`, `dirty`, `saving`. Guardado explícito con toast de confirmación; advertir al salir con cambios sin guardar.
- **Admin:** `query`, `filters`, `sort: {column, dir}`, `page`, `rows`, `loading`, `rowMenuOpen`.
- **Global:** `session {user, role}`, `toast` (cola de uno), `prefersReducedMotion`.

Datos requeridos: catálogo de profesores con disponibilidad por día; slots por profesor+fecha (deben re-consultarse al confirmar para detectar carreras); reservas del usuario; para admin, listado paginado con filtros del lado servidor.

---

## Screens / Views

### Las 13 pantallas

Todas están dibujadas en 390 y 1280 px. A continuación, las dos insignia en detalle; el resto se lee directamente de los archivos, que llevan el mismo nivel de acabado y una nota de su momento de deleite.

| # | Pantalla | Archivo |
| --- | --- | --- |
| 01 | Login | Auth |
| 02 | Registro | Auth |
| 03 | Revisa tu correo · verificado · enlace expirado | Auth |
| 04 | Recuperar contraseña (solicitar + establecer nueva) | Auth |
| 05 | Lista de profesores (con skeleton y card sin cupos) | Estudiante |
| 06 | Reserva de clase + pantalla de confirmación | Estudiante |
| 07 | Mis clases (+ cancelación bloqueada <24 h + modal) | Estudiante |
| 08 | Disponibilidad del profesor | Profesor y Admin |
| 09 | Perfil del profesor (con vista previa en vivo) | Profesor y Admin |
| 10 | Admin: reservas y usuarios (+ comportamiento móvil) | Profesor y Admin |
| 11 | App shell por rol | Estudiante |
| 12 | 404 y error de carga | Auth |
| 13 | Estados vacíos clave | Estudiante |

#### Las dos insignia, en detalle

#### 1. Login (`/login`)

**Propósito:** entrar al producto; primer contacto con la marca.

**390px** — Hero de **280px** con `--gradient-dawn`, constelación de 150px arriba a la derecha, logotipo «ORIÓN ✦» arriba a la izquierda (`--color-on-primary` Bricolage 800 15px) y titular display 30px/1.15 anclado abajo: «Tu inglés está a punto de amanecer.» Debajo, formulario con padding `30px 28px`, gap 16px: h2 «Qué bueno verte» → campo Correo → campo Contraseña → enlace «¿Olvidaste tu contraseña?» (`--color-primary-strong` 13px/600, alineado a la derecha) → botón primario full-width «Entrar» → «¿Primera vez? **Crea tu cuenta**» centrado 13px. Al pie, tagline en itálica `#B4A5BE` 12px: «Learn with confidence. Transform your opportunities.»

**1280px** — Split **53/47**: formulario de 400px centrado en la mitad izquierda (mismo contenido, h2 a 34px), panel de marca a la derecha con `margin: 20px`, `border-radius: 22px`, el degradado, constelación de 320px y el titular a 42px. **No** una columna estrecha centrada.

#### 2. Reserva de clase (`/profesores/:id`)

**Propósito:** elegir día, hora y modalidad, y confirmar.

**390px** — Padding 28px, gap 16px. Fila de vuelta (`←`) + avatar 46px + nombre h3 + especialidad. Bio en card blanca `radius-base` padding `14px 16px`. Grupo «Elige un día» (chips durazno en fila con wrap). Grupo «Cupos disponibles» (grid 3 columnas de chips lavanda). Grupo «Modalidad» (segmented). Input de nota. Barra sticky al fondo: botón primario full-width con el resumen en la etiqueta («Confirmar reserva · mié 16, 10:00») + microcopy centrado 11px «Recibirás confirmación por correo con invitación al calendario».

**1280px** — Header de 68px con nav horizontal (logotipo + entradas, activa en píldora `--color-accent-peach-soft`, avatar 38px a la derecha). Cuerpo en dos columnas con gap 48px, padding `32px 48px 48px`: izquierda **420px** con vuelta, avatar 92px, nombre h1 36px, fila de badges (Conversación / A1–B1 / Virtual y presencial), bio 15px/1.7 y dos tarjetas de estadística blancas (`120+ clases dictadas`, `4 años con adultos`). Derecha: tarjeta blanca `radius-card` padding 36px con `--shadow-lg` que contiene la agenda completa (mismos grupos, grid de horas a 3 columnas, modalidad y nota en fila) y, al pie, resumen a la izquierda + botón «Confirmar reserva» a la derecha.

### Guía responsive por patrón (para las pantallas restantes)

Breakpoints: **base 360–639** (diseño a 390) · **sm 640** · **md 768** · **lg 1024** · **xl 1280** (diseño a 1280).

| Patrón | 390 px — manda el pulgar | 1280 px — se usa el espacio |
| --- | --- | --- |
| Shell | Header 64px + tabs inferiores 68px con safe-area; contenido padding 20px | Sidebar fija 248px, sin tabs; contenido padding 48px, ancho máx. 1180px |
| Auth (login, registro, recuperar) | Hero compacto de 280px con degradado y constelación; formulario debajo, botón full-width | Split 53/47: formulario de 400px a la izquierda, panel de marca a la derecha |
| Lista de profesores | 1 columna de cards apiladas; filtros en fila con scroll horizontal y desvanecido al borde | Rejilla de 3 columnas (2 en lg); filtros como barra fija superior con todos los chips visibles |
| Reserva de clase | Perfil arriba, agenda abajo; grid de horas 3×n; barra de confirmación sticky al fondo | Dos columnas: perfil 420px + tarjeta de agenda; resumen y botón en el pie de la tarjeta |
| Mis clases | Tabs superiores + cards apiladas; acciones secundarias en menú de tres puntos | Próxima clase destacada a todo el ancho + rejilla de 2 columnas para el resto; acciones visibles en la card |
| Disponibilidad (profesor) | Un día a la vez con selector de día arriba; bloques de hora como chips en grid de 3 | Vista semanal 7 columnas × franjas; arrastrar para marcar rangos, con resumen lateral |
| Tabla admin | No hay tabla: cada fila se convierte en card con las 3 columnas clave y menú de acciones; filtros en hoja inferior | Tabla completa de 6 columnas, header sticky, búsqueda y filtros en la cabecera de la tarjeta |
| Modal / toast | Modal como hoja inferior a todo el ancho, radio solo arriba; toast sobre las tabs | Modal centrado de 440px máx.; toast en la esquina inferior derecha |

---

## Voz de marca (copywriting)

Cálida, directa, en segunda persona y en español de Colombia neutro. Alienta sin infantilizar; nunca culpa al usuario.

- Títulos cortos y humanos: «Qué bueno verte», «Reserva tu clase», «Aún no tienes clases».
- Errores que explican y proponen: «Falta el dominio del correo — por ejemplo, @gmail.com», no «Correo inválido».
- Estados vacíos que invitan: «Cada proceso es diferente; lo importante es empezar.»
- Confirmaciones que cuentan qué pasa después: «Clase reservada — te enviamos la confirmación.»
- Botones en infinitivo o imperativo corto: «Entrar», «Reservar», «Confirmar reserva». En loading, gerundio: «Entrando…».
- Tagline de marca (solo en auth y comunicaciones): «Learn with confidence. Transform your opportunities.»

Sin emoji en la interfaz. La única excepción es el glifo `✦` del logotipo «ORIÓN ✦».

---

## Assets

- **Fuentes:** Google Fonts — Bricolage Grotesque y Figtree. Autoalojarlas si el proyecto lo requiere.
- **Íconos:** Lucide (paquete del framework correspondiente), weight/stroke 1.75.
- **Constelación de Orión:** SVG inline, coordenadas en la sección *Constelación de marca*. No es un archivo de imagen.
- **Fotografía:** el diseño **no usa fotos**. Los avatares son iniciales sobre degradado. Si el producto añade fotos de profesores, respetar el círculo, el mismo tamaño y el degradado como fallback.
- No se usan assets de marca de terceros.

---

## Files

En este bundle:

- `Orión Fundamentos v2.dc.html` — **el archivo principal de referencia.** Tokens, tipografía, espaciado, motion, iconografía y la biblioteca completa de componentes con todos sus estados, más la navegación por rol y la tabla responsive. Empieza por aquí.
- `Orión Pantallas v2 · Auth.dc.html` — pantallas 01–04 y 12: login, registro, revisa tu correo, verificado, enlace expirado, recuperar y establecer contraseña, 404 y error de carga.
- `Orión Pantallas v2 · Estudiante.dc.html` — pantallas 05, 06, 07, 11 y 13: app shell por rol, lista de profesores, reserva de clase, confirmación, mis clases con cancelación bloqueada y modal, y los estados vacíos clave.
- `Orión Pantallas v2 · Profesor y Admin.dc.html` — pantallas 08, 09 y 10: disponibilidad (lista en móvil, rejilla semanal en desktop), perfil del profesor con vista previa en vivo, y el panel admin con métricas, tabla y su comportamiento móvil documentado.
- `Orión Direcciones v2.dc.html` — las tres direcciones exploradas. **La aprobada es `1b · Amanecer cálido premium`**; 1a y 1c quedan solo como registro de la decisión — no implementarlas.
- `support.js` — runtime del entorno de diseño, necesario para abrir los `.dc.html` en el navegador. **No es parte del producto.**

Ábrelos en un navegador. Los archivos de pantallas son lienzos: se puede hacer zoom y desplazarse libremente para recorrer las pantallas.
