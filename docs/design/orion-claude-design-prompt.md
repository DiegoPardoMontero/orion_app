# Prompt para Claude Design — Identidad visual y pantallas de Orión (MVP 1)

> **Antes de enviar:** adjunta (o pega) junto a este prompt el archivo
> `docs/wireframes-mvp1.html` del repositorio. Es el contrato estructural.

---

Eres el diseñador de producto de **Orión Language Academy**, una plataforma web
(PWA, mobile-first) para aprender inglés con clases personalizadas en Colombia.
Necesito que definas su identidad visual y diseñes en alta fidelidad las
pantallas del MVP 1, partiendo de los wireframes adjuntos.

## Contexto de marca

- **Orión** toma su nombre de la constelación: durante siglos fue punto de
  orientación para viajeros. La marca simboliza dirección, crecimiento,
  descubrimiento y propósito.
- Eslogan institucional: *"Learn with confidence. Transform your opportunities."*
- El corazón del producto es la **confianza comunicativa**: ayudamos a adultos
  que saben más inglés del que se atreven a hablar. El diseño debe transmitir
  calidez y seguridad, jamás intimidar.
- Público: adultos colombianos (16+) con objetivos laborales, académicos o de
  migración. Estética **profesional y humana** — nada infantil, nada corporativo frío.
- Personalidad de marca: humana, profesional, cercana, inspiradora.

## Qué necesito de ti

1. **Identidad visual completa:** paleta con valores hex exactos (primario,
   neutrales, semánticos success/warning/error), tipografía (máximo una familia
   de Google Fonts, con fallback de sistema), radios de borde, escala de
   espaciado. Dirección sugerida como punto de partida: azul profundo de cielo
   nocturno (la constelación) con un acento cálido — pero proponme tú.
2. **Diseño de alta fidelidad** de las pantallas listadas abajo, en viewport
   móvil (~390 px). El panel de administración es la excepción: va en desktop.
3. **Estados de componentes:** botón primario/secundario/deshabilitado, chips
   seleccionables (días y horas), inputs, tarjetas, badges de modalidad
   (Virtual/Presencial), tabs, toggle, modal de confirmación. Y por pantalla:
   estado vacío, cargando y error.
4. **Bloque final de design tokens** en formato Tailwind v4 (`@theme`),
   copiable como texto (ver formato exacto al final).

## Contrato estructural — no negociable

La **estructura y jerarquía** de cada pantalla está fijada por los wireframes
adjuntos: qué elementos hay y en qué orden. Tu trabajo es la capa visual
(color, tipografía, ritmo, estados), **no** reorganizar pantallas ni añadir o
quitar elementos.

Pantallas a diseñar:

1. **Login** — email + contraseña (sin wireframe: hazla coherente con el sistema).
2. **/profesores** — lista de tarjetas de profesor (avatar o iniciales, nombre,
   headline, próximo cupo, ver agenda).
3. **/profesores/[id] — agenda y reserva.** La pantalla más importante del
   producto: perfil breve, chips de día, chips de hora, modalidad, nota
   opcional, confirmar. Dale el mayor cuidado.
4. **/mis-clases** — tabs Próximas/Pasadas, tarjetas de clase con contraparte,
   botón WhatsApp, cancelar (incluye el estado bloqueado con el texto
   "Faltan menos de 24 h — la clase se considera impartida").
5. **/disponibilidad** (profesor) — franjas recurrentes por día, fechas
   bloqueadas, toggle "Perfil visible".
6. **Admin** (desktop, utilitario) — tabla de usuarios, tabla de reservas con
   filtros y un strip con 2 métricas. Prioriza densidad y legibilidad sobre
   personalidad: lo usa una sola persona.

## Voz y copy

- Todo el texto visible en **español (Colombia)**, con la voz institucional:
  cercana, clara, positiva, profesional.
- Expresiones prohibidas por el manual de marca: "No sabes inglés", "Tu nivel
  es muy malo", "Eso está completamente mal", "Es muy fácil", "Aprenderás
  inglés perfecto".
- Expresiones preferidas: "Estás avanzando", "Vamos a trabajar en esta
  habilidad", "Podemos mejorar este aspecto", "Cada proceso es diferente",
  "Lo importante es continuar practicando".
- Estados vacíos que motivan, nunca regañan. Ejemplo de referencia:
  *"Aún no tienes clases — explora los profesores y reserva la primera."*

## Restricciones técnicas (para que el diseño sea implementable tal cual)

- Se implementa con **Tailwind v4 y componentes propios** (sin UI kit): nada de
  glassmorphism pesado, gradientes complejos, ilustraciones custom por pantalla
  ni efectos que no salgan de utilidades estándar.
- Una sola familia de color de acento; contraste mínimo **WCAG AA** en todo
  texto; targets táctiles ≥ 44 px.
- Avatares: iniciales sobre fondo de color cuando no hay foto (patrón ya
  presente en los wireframes).
- Iconografía: propone un set consistente (Lucide o similar) y úsalo en todas
  las pantallas.
- Logo: por ahora basta un wordmark tipográfico "Orión" (puede llevar un guiño
  sutil a estrella/constelación). La marca gráfica definitiva la decidirá la
  fundadora — no inviertas en logotipos elaborados.

## Proceso

1. Empieza proponiendo **dos direcciones visuales** aplicadas a una sola
   pantalla (la de agenda y reserva) con su mini-paleta y tipografía.
2. Cuando elija una, aplícala a todas las pantallas y estados.
3. Cierra con la página de fundamentos y el bloque de tokens.

## Entregable final

- Las pantallas diseñadas (móvil; admin en desktop).
- Una página de **fundamentos**: paleta con hex, tipografía y escala, y los
  componentes con sus estados.
- El bloque de **tokens copiable** exactamente en este formato:

```css
@theme {
  --color-primary: #______;
  --color-primary-strong: #______;   /* hover / énfasis */
  --color-on-primary: #______;
  --color-surface: #______;          /* fondo de página */
  --color-surface-raised: #______;   /* tarjetas */
  --color-border: #______;
  --color-text: #______;
  --color-text-secondary: #______;
  --color-text-muted: #______;
  --color-success: #______;
  --color-success-bg: #______;
  --color-warning: #______;
  --color-warning-bg: #______;
  --color-error: #______;
  --color-error-bg: #______;
  --font-sans: "______", ui-sans-serif, system-ui, sans-serif;
  --radius-base: ___px;
  --radius-card: ___px;
}
```

Si un token adicional te resulta necesario, añádelo con nombre en el mismo
estilo y coméntalo.
