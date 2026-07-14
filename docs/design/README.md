# Handoff: Orión Language Academy — MVP 1 (identidad "Aurora cálida")

## Overview
Identidad visual y pantallas de alta fidelidad del MVP 1 de **Orión Language Academy**, una PWA mobile-first para reservar clases personalizadas de inglés en Colombia. Cubre: Login, /profesores, /profesores/[id] (agenda y reserva), /mis-clases (estudiante y profesor), /disponibilidad (profesor) y el panel Admin (desktop), con sus estados vacío/cargando/error y modales.

La estructura y jerarquía de cada pantalla siguen el contrato de `wireframes-mvp1.html` (incluido). **No reorganizar pantallas ni añadir/quitar elementos.**

## About the Design Files
Los archivos de este paquete son **referencias de diseño creadas en HTML** — prototipos que muestran el aspecto y comportamiento previstos, no código de producción. La tarea es **recrear estos diseños en el stack del proyecto: Next.js/React con Tailwind CSS v4 y componentes propios (sin UI kit)**, usando los tokens del bloque `@theme` de abajo.

- `Orión MVP1.dc.html` — todas las pantallas del MVP 1 (fuente de verdad visual).
- `Orión Direcciones.dc.html` — exploración de direcciones (histórico; la elegida es "Aurora cálida", opción 2a).
- `wireframes-1784061542752.html` — contrato estructural original.

Nota: los `.dc.html` usan un runtime propio de la herramienta de diseño (`support.js`, tags `<x-dc>`, atributos `style-hover`/`style-active`/`style-focus`). Léelos como especificación: los atributos `style-*` equivalen a los pseudo-estados CSS `:hover`/`:active`/`:focus`.

## Fidelity
**Alta fidelidad (hifi).** Colores, tipografía, espaciados, radios y estados son finales. Recrear pixel-perfect con Tailwind v4.

## Identidad visual — "Aurora cálida"

- **Concepto:** tinta violeta-nocturna (la constelación de Orión) + coral de amanecer. Cálida, enérgica y adulta — espíritu Preply/Duolingo sin infantilizar.
- **Tipografía:** **Sora** (Google Fonts), única familia. Fallback: `ui-sans-serif, system-ui, sans-serif`. Pesos usados: 400, 600, 700, 800.
- **Forma:** pill en todo control interactivo (`border-radius: 999px`); tarjetas y bloques a 20–28 px.
- **Motivo de marca:** constelación animada (estrellas que titilan sobre el degradado nocturno) en cabeceras hero; estrella de 4 puntas coral como guiño en el wordmark y estados especiales.
- **Wordmark:** "Orión" en Sora 800 + estrella coral (SVG `M12 2l2.6 6.6L21 11l-6.4 2.4L12 20l-2.6-6.6L3 11l6.4-2.4z`). No invertir en logo elaborado.
- **Iconografía:** Lucide (https://lucide.dev), stroke 2.2, `stroke-linecap/linejoin: round`. Iconos usados: arrow-left, arrow-right, search, calendar, clock, video, map-pin, mail, lock, eye, pencil, check, x, plus, alert-circle, wifi-off, users, more-vertical, chevron-down, star (relleno), logo WhatsApp.

## Design Tokens (Tailwind v4 — copiable)

```css
@theme {
  --color-primary: #241E4E;          /* tinta nocturna — botones secundarios, seleccionado */
  --color-primary-strong: #3A3272;   /* hover / énfasis */
  --color-on-primary: #FFFFFF;
  --color-accent: #FF5C38;           /* coral — CTA principal, selección de día */
  --color-accent-strong: #FF6E4E;    /* hover del CTA */
  --color-accent-pressed: #C93D1E;   /* sombra táctil (box-shadow 0 5px 0) */
  --color-accent-bg: #FFE8E0;        /* tinte coral — avatares, chips */
  --color-on-accent-bg: #9A2F12;     /* texto sobre tinte coral */
  --color-surface: #FFFDF8;          /* fondo de página */
  --color-surface-raised: #FFFFFF;   /* tarjetas */
  --color-surface-sunken: #F2EFE7;   /* pistas de tabs/segmentos, skeleton base */
  --color-border: #E5E0D4;           /* bordes de inputs y chips */
  --color-border-subtle: #EEE9DD;    /* bordes de tarjetas */
  --color-text: #221D33;
  --color-text-secondary: #6A657A;
  --color-text-muted: #979283;
  --color-text-disabled: #BBB6A8;
  --color-success: #1E6B4E;
  --color-success-bg: #E4F4EE;
  --color-warning: #8A4B12;
  --color-warning-bg: #FFF4E8;
  --color-error: #B3261E;
  --color-error-bg: #FDE9E4;
  --color-error-text: #8C2318;       /* texto de párrafo sobre error-bg */
  --color-info: #3A3272;             /* tinte lavanda — bloques informativos */
  --color-info-bg: #EDEAFB;
  --font-sans: "Sora", ui-sans-serif, system-ui, sans-serif;
  --radius-base: 999px;              /* pill: botones, chips, inputs, badges, tabs */
  --radius-card: 20px;               /* tarjetas y bloques de sección */
  --radius-sheet: 28px;              /* contenedor de pantalla / modales grandes: 24px */
}
```

Tokens extra usados en los heros nocturnos: degradado `linear-gradient(160deg, #241E4E 0%, #352B68 70%, #4A3A7E 100%)`, texto secundario sobre oscuro `#C9BFF0`, estrellas `#FFD9A8` / `#FF9C7E` / blanco, punto "en línea" `#4ADE80`.

## Escala de espaciado
4 / 8 / 10 / 12 / 14 / 16 / 20 / 24 / 28 px. Padding de pantalla: 20 px lateral. Gap entre tarjetas: 12 px. Gap interno de secciones: 12–16 px. Targets táctiles ≥ 44 px (botones principales: 52–56 px de alto).

## Tipografía (escala usada)
- Título de pantalla: 20px / 800
- Título hero (login): 34px / 800
- Título de modal: 16px / 800
- Nombre en tarjeta: 15px / 700
- Cuerpo / botones: 13–15.5px / 600–700
- Meta / badges: 11.5–12.5px / 600–700
- Micro-texto legal: 11.5–12px / 400, color muted

## Componentes y estados

### Botón primario (CTA coral)
- Pill, `bg #FF5C38`, texto blanco 700, padding 16px vertical, **sombra táctil `box-shadow: 0 5px 0 #C93D1E`**.
- Hover: `bg #FF6E4E`. Active/pressed: `translateY(4px)` + `box-shadow: 0 1px 0 #C93D1E` (el botón "se hunde").
- Variante tinta: `bg #241E4E`, hover `#3A3272`, sin sombra táctil (acciones secundarias fuertes: "Ver agenda", "Registrar asistencia").

### Botón secundario / outline
- Pill, borde 1.5px `#241E4E` (o `#E5E0D4` neutro), fondo transparente. Hover: tinte (`#EDEAFB` para tinta, `#F2EFE7` neutro).
- Peligro: borde/texto `#B3261E`, hover fondo `#FDE9E4`. Botón destructivo sólido (modal): `bg #B3261E`, hover `#97201A`.

### Botón deshabilitado
- `bg #F5F2EA`, texto `#BBB6A8`, sin borde, `cursor: not-allowed`. (Ej.: Cancelar con <24 h.)

### Chips seleccionables (días y horas)
- Base: pill blanca, texto `#6A657A` 600, hover fondo `#FFE8E0`.
- Día seleccionado: `bg #FF5C38`, texto blanco 700, sombra `0 3px 0 #C93D1E`.
- Hora seleccionada: `bg #241E4E`, texto blanco 700, animación `pulseGlow` (ver Animaciones).
- Hora ocupada: fondo `rgba(255,255,255,0.45)`, texto `#B3AECB` con `line-through`, no interactiva.

### Inputs
- Pill, borde 1.5px `#E5E0D4`, fondo blanco, padding 13–14px (44px a la izquierda si lleva icono Lucide `#B3ADA0`).
- Focus: borde `#FF5C38` (y `outline: 2px` accesible). Error: borde `#E2544F`.
- Textarea (notas): radio 18px, mismo tratamiento.

### Tarjetas
- Fondo blanco, borde 1.5px `#EEE9DD`, radio 20px, padding 16px. Hover (si clicable): borde `#FF5C38`.
- Bloques de sección con color: melocotón `#FFF4E8` (títulos `#8A4B12`), lavanda `#EDEAFB` (`#3A3272`), menta `#E4F4EE` (`#1E6B4E`). Cada bloque lleva su icono Lucide junto al título.

### Badges de modalidad
- Virtual: `bg #E4F4EE`, texto `#1E6B4E` 700, icono video. Presencial: `bg #FFF4E8`, texto `#8A4B12`, icono map-pin. Pill, 11.5px.

### Tabs / segmento
- Pista pill `bg #F2EFE7` padding 4px; opción activa pill `bg #241E4E` texto blanco 700; inactiva texto `#6A657A`, hover fondo blanco.

### Toggle
- 52×30px pill. On: `bg #1E6B4E`, knob blanco a la derecha. Transición `left 0.15s`.

### Modales
- Backdrop `rgba(20,16,46,0.45)`. Hoja `bg #FFFDF8`, radio 24px, padding 24/20px, sombra `0 24px 48px rgba(20,16,46,0.35)`. Dos botones al pie (secundario + primario) a partes iguales.

### Avatares (iniciales)
- Círculo con iniciales 700. Rotación de tintes: coral `#FFE8E0`/`#9A2F12`, lavanda `#EDEAFB`/`#3A3272`, melocotón `#FFF4E8`/`#8A4B12`, menta `#E4F4EE`/`#1E6B4E`. En hero: 56px con borde 2.5px `#FF5C38` y punto verde "en línea".

### Estados por pantalla
- **Cargando:** skeleton con shimmer (gradiente `#F2EFE7 → #FAF8F1 → #F2EFE7`, animación 1.4s linear infinita) replicando la silueta de las tarjetas.
- **Vacío:** icono en círculo lavanda 60px + estrella coral titilando, copy motivador ("Aún no tienes clases — explora los profesores y reserva la primera.") + CTA coral.
- **Error:** icono en círculo `#FDE9E4` (wifi-off / alert-circle `#B3261E`), copy tranquilizador, botón outline "Reintentar". En login: banner `#FDE9E4` con texto `#8C2318` + borde de input en error.

## Screens / Views

### 1 · Login
Hero nocturno (degradado + constelación animada, padding 44/24px): wordmark "Orión" 34px + estrella coral, eslogan "Aprende con confianza. Transforma tus oportunidades." en `#C9BFF0`. Cuerpo: campo correo (icono mail), campo contraseña (icono lock + eye), CTA coral "Entrar" con flecha, link "¿Olvidaste tu contraseña?" centrado.

### 2 · /profesores
Hero nocturno compacto: título "Profesores" 20px + menú ⋯ (círculo `rgba(255,255,255,0.14)`), buscador pill blanco con icono search **dentro del hero**. Lista de tarjetas de profesor: avatar 48px, nombre 15/700, headline 12.5 `#6A657A`, chip verde "Próximo: mié 10:00" (icono clock), botón tinta "Ver agenda ›".

### 3 · /profesores/[id] — agenda y reserva (pantalla estrella)
Hero nocturno: back circular, kicker "RESERVAR CLASE" `#C9BFF0`, avatar 56px con borde coral + punto verde, nombre 18/700, estrella dorada + "Conversación y confianza · 4 años", 2 chips translúcidos ("100% conversacional", "Lic. en lenguas"). Cuerpo: bloque melocotón "Elige un día" (chips de día), bloque lavanda "Cupos disponibles" con contador "5 libres" (grid 3 col de horas), bloque menta "Modalidad" (segmento Virtual/Presencial con iconos), input nota con icono lápiz, CTA coral "Confirmar reserva" con check, micro-texto con icono mail. Estado extra: tarjeta "¡Clase reservada!" (círculo menta con check + estrella titilando, resumen, botón outline "Ir a mis clases").

### 4 · /mis-clases
Título 20/800 + tabs pill Próximas/Pasadas. Tarjeta de clase: fecha con icono calendar coral + badge modalidad; contraparte con avatar 34px; fila de botones `WhatsApp` (outline verde con logo) y `Cancelar` (outline neutro, hover peligro). Clase presencial muestra el lugar bajo el nombre. **Cancelar bloqueado** (<24 h): botón deshabilitado + micro-texto "Faltan menos de 24 h — la clase se considera impartida" con icono info. El botón obedece `canCancel` del API.
Variante profesor (Pasadas): tarjeta CONFIRMED con botón tinta "Registrar asistencia" → modal "¿Cómo fue la clase con Ana?" (chips Asistió/No asistió, textarea de notas, Ahora no / Guardar coral); tras registrar, badge "Completada" (check) o "No asistió". Modal de cancelación: "¿Cancelar esta clase?" + copy amable + Mantener clase / Sí, cancelar (rojo).

### 5 · /disponibilidad (profesor)
Título + subtítulo con icono clock "Horario semanal recurrente · hora de Bogotá". Bloque lavanda con los días (Lunes/Miércoles/Viernes): cabecera día 13.5/700 `#3A3272` + botón "+" circular blanco (hover coral); franjas como pills tinta "18:00–21:00" con "✕" circular translúcido (hover coral); separadores finos `rgba(58,50,114,0.12)`; día vacío: "Sin franjas — toca + para añadir" en `#7A6FC9`. Bloque melocotón "Fechas bloqueadas": fila pill blanca "Vie 15 ago · todo el día · viaje" con ✕, botón dashed "Bloquear una fecha". Bloque menta "Perfil visible" con toggle verde. Modal nueva franja: selects Desde/Hasta (horas en punto), Cancelar / Añadir franja coral. El "✕" de franja elimina **con confirmación**.

### 6 · Admin (desktop, 1080px, utilitario)
Barra superior tinta: wordmark + "Panel de administración" + avatar AD. Strip de 2 métricas (tarjetas lavanda/melocotón: icono circular blanco, cifra 24/800, etiqueta). Tabla Usuarios (Nombre, Correo, Rol, Estado, Editar) y tabla Reservas con filtros pill (Todas/Confirmadas/Canceladas/Completadas). Tablas: cabecera `#F7F4EC` uppercase 11.5/700 `#979283`, filas 13px con hover `#FBF9F3`, badges pill de rol/estado con los tintes semánticos. Prioriza densidad y legibilidad.

## Interactions & Behavior
- Navegación: /profesores → "Ver agenda" → /profesores/[id] → "Confirmar reserva" → estado confirmado → "Ir a mis clases".
- Selección de día recarga los cupos (mostrar skeleton breve si aplica); seleccionar hora activa el CTA.
- CTA coral: hover aclara, pressed se hunde (`translateY(4px)`, `transition: transform 0.1s`).
- Focus visible en todo control: `outline 2px` coral con offset — nunca el anillo azul por defecto.
- WhatsApp abre deep link `wa.me` con el número de la contraparte.
- `canCancel` viene del API; el frontend no recalcula la regla de 24 h, solo pinta deshabilitado + micro-texto.
- Perfil visible (toggle) publica/oculta al profesor con confirmación optimista.

### Animaciones (CSS keyframes)
```css
@keyframes twinkle { 0%,100% { opacity: .3; } 50% { opacity: 1; } }        /* estrellas hero, 2.2–3.5s, delays escalonados */
@keyframes pulseGlow { 0%,100% { box-shadow: 0 0 0 0 rgba(36,30,78,.35); } 50% { box-shadow: 0 0 0 7px rgba(36,30,78,0); } }  /* hora seleccionada, 2s */
@keyframes shimmer { 0% { background-position: -300px 0; } 100% { background-position: 300px 0; } }  /* skeleton, 1.4s linear */
```
Respetar `prefers-reduced-motion: reduce` desactivando twinkle/pulseGlow.

## State Management
- Sesión (rol estudiante/profesor/admin) → define /mis-clases y navegación.
- Reserva: `{ profesorId, día, hora, modalidad, nota }`; horas ocupadas vienen calculadas del backend.
- Mis clases: tabs (upcoming/past), por clase `{ canCancel, estado: CONFIRMED|COMPLETED|NO_SHOW|CANCELLED }`.
- Disponibilidad: franjas recurrentes por día, fechas bloqueadas, `perfilVisible`.
- Fetch: mostrar skeleton al cargar, estado de error con Reintentar, estados vacíos motivadores.

## Voz y copy
Español (Colombia), cercana, positiva, profesional. Prohibido: "No sabes inglés", "Tu nivel es muy malo", "Eso está completamente mal", "Es muy fácil", "Aprenderás inglés perfecto". Preferido: "Estás avanzando", "Cada proceso es diferente", "Lo importante es continuar practicando". Estados vacíos motivan, nunca regañan. Todo el copy visible está en los HTML — usarlo literal.

## Accesibilidad
- Contraste AA en todo texto (los pares token de arriba ya cumplen; texto coral solo ≥ 15px/700 o sobre tinte).
- Targets ≥ 44px; toggle y "✕" con `aria-label`.
- Hora ocupada: además del tachado, `aria-disabled`.

## Assets
Sin imágenes raster. Todo es SVG inline (iconos Lucide, estrellas de constelación, logo WhatsApp) + Google Font Sora. Avatares = iniciales sobre tinte (patrón definitivo hasta que haya fotos).

## Files
- `Orión MVP1.dc.html` — todas las pantallas, estados y modales (fuente de verdad).
- `Orión Direcciones.dc.html` — exploración de direcciones (contexto histórico).
- `wireframes-1784061542752.html` — contrato estructural.
- `orion-claude-design-prompt.md` — brief original con requisitos completos.
