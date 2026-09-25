# Orión · Invitación personal al profesor — handoff

Pantalla pública a la que llega un profesor desde el enlace que le envía el admin. Le cuenta que Orión está arrancando, que fue invitado al primer grupo («Profes fundadores») y lo lleva a crear su cuenta.

## Contenido del paquete

- `_CLAUDE_CODE_PROMPT.md`: el prompt para pegar en Claude Code.
- `capturas/invitacion-390.png` y `capturas/invitacion-1280.png`: la referencia visual, a 2x.
- `disenos/Profesor-Invitacion.dc.html`: el diseño vivo. Ábrelo con `support.js` en la misma carpeta; es solo referencia y no es código de producción.
- `assets/rigel-saluda.svg`: Rigel en la pose de saludo, en SVG real. No lo redibujes.

## Ruta y datos

- Ruta sugerida: `/invitacion/[token]`. Es pública y no lleva navegación de app.
- Del token se obtienen:
  - `nombreProfesor`, que es opcional.
  - `email`.
  - `invitadoPor`, con nombre y cargo.
  - `venceEl`, una fecha.
  - `estado`: `vigente` | `vencida` | `usada`.

## Tokens (Tailwind v4, en `@theme`)

```css
--color-coral: #E8503A;      /* botón principal; un solo elemento coral por pantalla */
--color-coral-texto: #C93A26;/* enlaces y texto pequeño coral */
--color-tinta: #33203B;      /* texto principal */
--color-tinta-suave: #5E4E6B;/* texto secundario */
--color-tinta-tenue: #7A6B85;/* notas */
--color-crema: #FFF6EE;      /* fondo */
--color-lavanda-suave: #EFE9F9; --color-lavanda-texto: #5E4A8A; /* etiqueta */
--color-durazno: #FFC189;    /* avatar de quien invita */
--gradient-amanecer: linear-gradient(180deg,#2E1E4E,#7A4A8C 46%,#E8764F 82%,#FFC189);
--font-display: "Bricolage Grotesque"; --font-texto: "Figtree";
```

## Layout

**Móvil (390):**
- El hero con el degradado del amanecer mide 300 px de alto.
  - El logo «ORIÓN ✦» va en crema, a 20 px de la izquierda y 22 px de arriba, en 16 px 800.
  - Rigel mide 160 px de ancho y va centrado a 78 px de arriba.
  - Lleva una constelación decorativa de 5 a 7 puntos y 3 líneas en crema al 35 %.
- El contenido tiene un padding de 24/20 px y un gap de 18 px.
  - Etiqueta: una píldora de 12 px 700 con la estrella de Lucide.
  - Titular: Bricolage 700, 30 px, line-height 1.1.
  - Cuerpo: 16 px, line-height 1.55.
  - Quien invita: un avatar durazno de 36 px con la inicial y un texto de 14 px.
- El pie queda pegado abajo, con un padding de 16/20/28 px.
  - El botón ocupa todo el ancho: 52 px de alto, en píldora, con sombra `0 10px 24px rgba(232,80,58,.35)`.
  - Debajo va una nota centrada de 12 px.

**Desktop (1280):**
- La pantalla es un grid de `520px 1fr`.
- El panel izquierdo lleva el degradado a todo el alto.
  - El logo va a 48/30 px, en 18 px.
  - Rigel mide 260 px y va a unos 300 px de arriba.
  - El eslogan «Find your right teacher, learn your way» va en tinta, Bricolage 700 de 22 px, a 44 px del borde inferior.
- La columna derecha se centra en vertical, con un padding horizontal de 80 px, un max-width de 720 px y un gap de 26 px.
  - El titular va en 46 px y el cuerpo en 18 px.
  - El avatar mide 44 px.
  - El botón mide 56 px de alto y lleva un padding de 34 px.
- La etiqueta, el logo, «Te invita…» y el enlace «Inicia sesión» llevan `white-space: nowrap`.

## Copy final

- Etiqueta: **Invitación personal · Profes fundadores**
- Titular: **{nombre}, queremos que seas de los primeros profes de Orión.** Sin nombre: **Queremos que seas de los primeros profes de Orión.**
- Cuerpo: **Estamos lanzando Orión y abrimos las puertas a un grupo pequeño de profesores. Nos alegra mucho contar contigo desde el comienzo.**
- Quien invita: **Te invita {nombre}, {cargo}**
- Botón: **Aceptar la invitación**
- Nota: **Es solo para ti y vence el {d de mes}. ¿Ya tienes cuenta? Inicia sesión**

## Comportamiento

- **Aceptar la invitación** lleva al registro de profesor con el correo de la invitación ya puesto y sin posibilidad de editarlo. El token se consume al crear la cuenta, no al abrir el enlace.
- **Inicia sesión** lleva al login. Si el correo coincide con una cuenta existente, la invitación se enlaza a esa cuenta.
- **Estado `vencida`** es la misma pantalla, pero sin el bloque de quien invita y sin la nota.
  - Titular: **Esta invitación ya venció.**
  - Texto: **Escríbele a quien te invitó y te enviamos un enlace nuevo.**
  - No lleva botón coral.
- **Estado `usada`** también quita el bloque de quien invita.
  - Titular: **Esta invitación ya se usó.**
  - Texto: el mismo que en `vencida`.
  - El botón principal es «Inicia sesión».
- Un token inexistente se muestra igual que `vencida`. Así no se revela si el token existió.

## Accesibilidad

- Rigel y la constelación son decorativos: van con `aria-hidden="true"`.
- El botón es un `<button>`, o un `<a>` si navega, con un foco visible de anillo tinta de 3 px.
- Los contrastes cumplen AA: tinta sobre crema y crema sobre coral para el texto del botón en 15 px 700. Los enlaces van en `#C93A26`.
- Respeta `prefers-reduced-motion`; la pantalla es estática por defecto.

## Checklist

- [ ] Ruta `/invitacion/[token]` con los 3 estados.
- [ ] Fallback del titular cuando no hay nombre.
- [ ] Fecha de vencimiento formateada en `es-CO`, en el formato «9 de octubre».
- [ ] Rigel importado desde `assets/rigel-saluda.svg`, sin redibujarlo.
- [ ] Sin textos partidos a 1280: revisar los `nowrap`.
- [ ] Revisión visual contra las dos capturas.

## Decisiones abiertas

- ¿«Profes fundadores» tiene algún beneficio real? Si lo tiene, se agrega una línea al cuerpo. Si no, queda solo como reconocimiento.
- ¿Cuántos días dura la invitación? El diseño usa una fecha de ejemplo.
