# Mascota de Orión — Guía de uso y sistema de apariciones

> **Para Claude Code.** Este documento extiende el adendum de la mascota del
> brief de rediseño y es parte del contrato visual (`docs/design-v2/`). Define
> dónde aparece la mascota, en qué nivel, y cuánta autonomía tienes para
> proponer apariciones nuevas. Nombre del personaje: pendiente (decisión de
> Pardo y Sofía); mientras tanto, en código y copy es "la mascota".

## Regla raíz

La mascota vive en momentos de **bienvenida, celebración, espera, vacío y
orientación**. Nunca aparece en errores, acciones destructivas, malas noticias
(cancelaciones, "la clase se considera impartida", fallos de validación) ni en
el **panel admin**. Un personaje alegre junto a una mala noticia se lee como
burla; la ausencia también es diseño.

Presupuesto de densidad por vista: **1 Protagonista O 1 Cameo, nunca ambos**.
Los Signos son ambientales y no cuentan, pero no se apilan de forma visible.

---

## Nivel 1 · Protagonista — cuerpo entero, un momento por pantalla

**Lista cerrada (donde el diseño ya la puso):** login, registro, confirmación
de reserva, verificación exitosa, "revisa tu correo", estados vacíos, 404.

- **Autonomía: ninguna.** Añadir un protagonista nuevo es una decisión de marca
  → requiere aprobación explícita de Pardo ANTES de implementar.
- Técnica: pose del set SVG oficial, tamaño y posición según el mockup,
  `aria-hidden="true"`, animación de entrada solo si el mockup la define y
  siempre con alternativa `prefers-reduced-motion`.

---

## Nivel 2 · Cameo — medio cuerpo, asomándose, 60–90 px

Guía sin robar protagonismo. **Lista inicial aprobada, con sus condiciones:**

1. **Asomándose por el borde de la tarjeta de agenda** cuando el estudiante
   elige su primer cupo. Condición: usuario sin reservas previas (el mismo dato
   que alimenta el estado vacío de Mis clases).
2. **Coach mark de onboarding** la primera vez que se entra a Profesores.
   Mecánica: flag en `localStorage` (`orion_onboarding_profesores`),
   descartable con un toque, aparece una sola vez.
3. **Sobre el hero de "Tu próxima clase" cuando falta menos de una hora**, en
   pose de ánimo. Cálculo en cliente con la hora de la reserva. Restricción
   dura: si la tarjeta muestra el aviso de cancelación bloqueada ("menos de
   24 h"), ese mensaje queda limpio — la mascota va junto al hero, jamás junto
   al aviso.
4. **Al pie del perfil del profesor cuando está incompleto** (sin foto, sin bio
   o sin publicar), animando a completarlo. Solo en la vista del propio
   profesor, nunca en la pública.
5. **En el correo de confirmación** — ⚠️ este es distinto: es territorio del
   backend (`notifications`) y los clientes de correo **no soportan SVG** de
   forma fiable. Requiere export PNG @2x de la pose + decidir imagen alojada
   vs. adjunta (CID). Además, la "invitación al calendario" (.ics) **no tiene
   superficie visual** — la mascota va en el email que la contiene, no en el
   evento. Trátalo como mini-paso propio coordinado con el backend; **no
   bloquea la Fase C**.

**Autonomía: proponer, no imponer.** Puedes identificar cameos nuevos; se
listan en el resumen del paso correspondiente (pantalla, pose, tamaño, por qué)
y Pardo los aprueba en el DETENTE antes de implementarlos.

Reglas de los cameos: máximo uno visible por vista; nunca tapa contenido
interactivo ni invade los 44 px de un target táctil vecino; en móvil se
verifica en 390 px que no empuje el layout.

---

## Nivel 3 · Signo — silueta o cara, 20–40 px, presencia ambiental

**Lista inicial aprobada:**

- **Spinner de carga:** la estrella girando suave en lugar del círculo, en
  TODOS los loaders. CSS puro; bajo `prefers-reduced-motion`, pulso de opacidad
  en vez de giro.
- **Skeletons:** una estrellita tenue en la esquina, latiendo por opacidad (no
  por movimiento); quieta bajo `prefers-reduced-motion`.
- **Favicon e íconos de la PWA:** derivar del SVG maestro: `favicon.svg` +
  `favicon.ico`, PWA 192 y 512 (+ variante maskable), `apple-touch-icon`. El
  splash de Android sale solo del manifest con estos íconos y `theme_color`.
- **Bullets** de listas de beneficios y de requisitos de contraseña —
  **solo** en listas cortas de guía/marketing (auth, onboarding), no en toda
  lista de la app.
- **Toasts de éxito:** la cara pequeña en lugar del check — **solo** en éxito;
  error y advertencia conservan sus iconos semánticos de siempre.

**Excluido por decisión de diseño (revocable por Pardo): el avatar por defecto
del profesor.** Las iniciales sobre color identifican a *personas distintas*;
si todos los profesores sin foto comparten la misma cara de mascota, la lista
pierde escaneabilidad y la marca canibaliza la identidad de la gente. Se
mantienen las iniciales.

**Autonomía: libre.** Los signos nuevos los decides tú sin aprobación previa
**si pasan la rúbrica** de abajo; los mencionas en el resumen del paso para
que queden registrados.

---

## Los dos elementos que son FEATURES, no decoración

1. **Marcador de racha en Mis clases** ("una estrella por clase — se van
   encendiendo"). Spec mínima aprobada: fuente = reservas `COMPLETED` del
   estudiante; render = hasta 5 estrellas encendidas y, si hay más, `+N`;
   texto accesible "N clases completadas" (aquí la mascota SÍ comunica, no es
   decorativa). Nada de metas, premios ni gamificación adicional sin
   aprobación de Pardo.
2. **Botón flotante de ayuda.** Un FAB sin destino es una puerta falsa.
   Propuesta: destino = WhatsApp de soporte de Orión (`wa.me/<número>`).
   **BLOQUEADO hasta que Pardo confirme el número**; si no se confirma, no se
   construye.

---

## Rúbrica para decidir una aparición nueva

Antes de colocar la mascota en un sitio no listado, responde en orden:

1. ¿El momento es de bienvenida, celebración, espera, vacío u orientación?
   (Error, borrado, cancelación, mala noticia o admin → **NO**, sin excepción.)
2. ¿Ya hay un Protagonista o Cameo en la vista? → **NO** (presupuesto de densidad).
3. ¿Tapa contenido, roba foco o invade targets táctiles? → **NO**.
4. Elige el nivel por función: pico emocional = Protagonista (**requiere
   aprobación**), guía contextual = Cameo (**se lista para el DETENTE**),
   ambiente = Signo (**libre**).
5. Accesibilidad: `aria-hidden` si es decorativa; texto accesible si comunica
   (como la racha); toda animación con su alternativa `prefers-reduced-motion`.
6. Performance: solo poses del set oficial vía el componente; Lighthouse ≥ 90
   sigue vigente — la mascota no tiene presupuesto aparte.

---

## Reglas técnicas

- **Toda aparición** usa el componente del adendum: `<MascotaEstrella pose
  size />` con los SVG oficiales de `docs/design-v2/assets/` (optimizados con
  SVGO al importar). 
- **Las poses no se dibujan ni se aproximan.** Si un uso aprobado necesita una
  pose que no existe en el set (p. ej. "ánimo" para el hero, "asomándose" para
  el cameo de agenda), **DETENTE y avisa a Pardo** — se le pide a Claude
  Design, que es quien dibuja.
- Mantén al final de este archivo una **tabla viva "Dónde vive la mascota"**
  (pantalla → nivel → pose → desde qué paso), actualizada en el mismo commit
  de cada paso que la toque.

## Dónde vive la mascota (tabla viva — actualizar en cada paso)

| Pantalla / lugar | Nivel | Pose | Desde |
|---|---|---|---|
| Landing `/` — hero | Protagonista | saludo | minibrief landing v1 |
| Login — hero | Protagonista | saludo | design v2 |
| Registro — panel de marca | Protagonista | saludo | design v2 |
| Mis clases — banner de reserva confirmada | Cameo | celebración | design v2 |
| Estados vacíos (estudiante/profesor) | Cameo | guía | design v2 |
| 404 (`not-found`) | Protagonista | ánimo | design v2 |
| Favicon + íconos PWA | — (marca) | cara | favicon |
