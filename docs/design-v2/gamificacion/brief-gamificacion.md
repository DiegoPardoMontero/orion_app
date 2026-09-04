# Prompt para Claude Design — Sistema visual de gamificación · Orión

> **Antes de enviar, adjunta:** (1) `docs/orion-mascota-guia.md` — Rigel ya existe, con
> 6 poses y 2 tonos: **este documento es un contrato, no una sugerencia**; (2) el bloque
> de tokens `@theme` de la dirección «Amanecer cálido premium»; (3) capturas del panel
> de progreso actual (`/perfil` del estudiante, escritorio y móvil); (4) las 6 poses de
> Rigel como archivos.

---

Eres el **diseñador de producto senior** de Orión, un marketplace colombiano de clases
de idiomas en vivo (inglés, francés y español). La plataforma está construida y en
producción: los estudiantes buscan profesor, reservan, pagan y toman clase. El sistema
de diseño «Amanecer cálido premium» (coral / durazno / lavanda sobre crema) ya está
implementado, y la mascota **Rigel** ya existe.

Este encargo es una **capa nueva**: el sistema visual de un programa de logros, puntos
y personalización de avatar para estudiantes adultos.

## Qué es y qué NO es este encargo

**Es:** un sistema de recompensas visuales — iconografía de logros, marcos de avatar,
paletas desbloqueables, fondos, un puñado de accesorios ilustrados, y las pantallas
donde todo eso vive.

**No es:** rediseñar Rigel, rediseñar el sistema de diseño, ni reorganizar pantallas
existentes. Rigel tiene su guía y sus tres niveles de uso (Protagonista / Cameo /
Firma); todo lo que dibujes tiene que convivir con esa guía, no reemplazarla. Si crees
que algo de la guía estorba, **dilo y espera respuesta** en lugar de cambiarlo.

## El público, que es lo que más restringe

Adultos colombianos de 16 años en adelante, con metas laborales, académicas o de
migración. Muchos llegan con vergüenza de hablar el idioma. Toman **una o dos clases
por semana**, no una diaria.

Esto tiene tres consecuencias de diseño que no son negociables:

1. **Nada infantil.** Esto no es Duolingo. Un adulto que estudia inglés para conseguir
   trabajo no quiere que su plataforma le hable como a un niño de ocho años. El tono es
   el de una app de bienestar o de finanzas personales bien hecha: cálida, sobria,
   adulta. Rigel es simpático; el sistema alrededor es elegante.
2. **La ausencia nunca se castiga visualmente.** Ni rojos de alarma, ni contadores
   regresivos, ni caras tristes por una semana sin clase. La voz de marca de Orión
   prohíbe explícitamente el lenguaje que regaña, y un icono puede regañar sin palabras.
3. **Ritmo semanal, no diario.** Las rachas son de semanas. Cualquier visual que sugiera
   "hoy no hiciste nada" está mal calibrado para este producto.

## El proceso — en este orden, esperando aprobación entre etapas

### Etapa 1 · La decisión de fondo: ¿qué se personaliza?

Los estudiantes ya suben **foto de perfil real** y esas fotos se usan en todo el
producto (mensajes, reservas, "con quién has practicado"). Los accesorios necesitan un
lienzo, y una foto no lo es. Preséntame **tres direcciones** para resolverlo, cada una
con un boceto del avatar en tres estados de progreso (recién llegado · intermedio ·
avanzado):

- **A · Compañero.** El estudiante tiene su propia estrella acompañante junto a la foto,
  y los accesorios van sobre ella. Resuelve el lienzo, pero **cuidado**: si esa estrella
  es Rigel, Rigel deja de ser la voz de Orión para convertirse en mascota de cada
  usuario. Si tomas este camino, propón cómo se distingue.
- **B · Marco.** La foto se conserva intacta y todo lo desbloqueable rodea: marcos,
  anillos de progreso, fondos, insignias ancladas al borde. Cero riesgo de marca, menos
  espacio expresivo.
- **C · Híbrida.** Marcos sobre la foto + una estrella pequeña como insignia de nivel.

Para cada una: nombre, racional, qué se desbloquea, y **cómo se ve el estudiante que
todavía no ha desbloqueado nada** — ese estado inicial es el que más gente va a ver.

Junto a las direcciones, dibuja **seis iconos de logro de muestra** en sus dos estados
(bloqueado y desbloqueado) para que se pueda juzgar el lenguaje gráfico.

Elegiré una con la fundadora, posiblemente mezclando rasgos. **Detente y espera.**

### Etapa 2 · El sistema completo de la dirección elegida

**Iconos de logro — 20 piezas.** Cubriendo estas familias, con nombres en español
(Colombia):

| Familia | Ejemplos |
|---|---|
| Primeros pasos | Primera clase reservada · primera clase tomada · perfil completo · primer mensaje al profesor |
| Constancia | 2, 4, 8, 12 y 24 semanas seguidas |
| Volumen | 5, 10, 25, 50 y 100 clases |
| Amplitud | Clases con 3 profesores distintos · dos idiomas · una clase presencial |
| Compromiso | Primera reseña escrita · objetivo declarado · un mes sin cancelaciones |

Cada icono en **tres estados**: bloqueado, en progreso (con anillo o barra) y
desbloqueado. Los primeros cuatro logros tienen que poder conseguirse en la **primera
semana** de uso; diséñalos sabiendo que son los que más se van a ver.

**Escalones.** Si propones bronce/plata/oro o equivalente, que se distingan por
**forma, no solo por color** — hay daltonismo y hay pantallas malas.

**Marcos, paletas y fondos.** Al menos 8 marcos, 6 paletas y 6 fondos, todos
producibles con **CSS puro** (gradientes, bordes, sombras, `conic-gradient`,
`clip-path`): sin imágenes externas, sin SVG pesado. Ordénalos por dificultad de
desbloqueo, y que el marco inicial —el que se tiene sin haber logrado nada— se vea
digno, no castigado.

**Accesorios ilustrados — exactamente 3.** Uno por cada zona de anclaje que definas
(por ejemplo: cabeza, cara, mano). Solo tres: son la prueba de concepto del sistema de
capas, no el catálogo. Si el sistema funciona, se encargan más después.

**Estados del sistema de racha:** activa, congelada (existe un congelamiento mensual
que perdona una semana), y recién perdida — esta última es la más delicada del encargo:
tiene que leerse como *"empezamos de nuevo"*, jamás como *"fallaste"*.

**Detente y espera revisión.**

### Etapa 3 · Las pantallas, en 390 px y 1280 px

1. **Perfil del estudiante — vista privada** (lo que ve su dueño): avatar, objetivos
   declarados, estadísticas, racha, logros y ajustes de privacidad.
2. **Perfil del estudiante — vista pública** (lo que ve un profesor con quien tiene
   clase): sin datos de contacto, sin estadísticas económicas. Deja explícito qué se ve
   y qué no.
3. **Tablero de logros completo**, con filtros por familia y progreso visible en los
   bloqueados. Un logro gris sin decir cuánto falta no motiva a nadie.
4. **El momento de desbloqueo** — la celebración. Aquí va el único derroche permitido de
   todo el encargo, y aun así tiene que caber en CSS y tener alternativa estática para
   `prefers-reduced-motion`.
5. **Personalizador de avatar**: elegir marco, paleta, fondo y accesorios, con lo
   bloqueado a la vista y su condición de desbloqueo escrita.
6. **Mapa de constancia de las últimas 12 semanas** (reemplaza al mapa anual actual: con
   una o dos clases por semana, una cuadrícula de 365 días se ve vacía y comunica
   abandono en lugar de progreso).
7. **Estados vacíos**: estudiante sin logros, sin objetivos, sin clases.

## Encargo adicional, aprovechando el viaje

**Chips de idioma en SVG.** Hoy se usan emoji de bandera y **no se renderizan en
Windows**. Diseña la insignia de idioma para inglés, francés y español — pensada para
que agregar alemán mañana sea dibujar una pieza más siguiendo la misma regla. Evita
banderas nacionales si encuentras una solución mejor: un idioma no es un país (el
español de Colombia no se representa bien con la bandera de España).

## Contrato técnico — para que esto sea implementable tal cual

Lo implementa a mano un solo ingeniero con Tailwind v4 y componentes propios. Tu
entregable es **contrato visual**, no código de producción, pero tiene que ser
construible sin adivinar:

- **Todo SVG con `viewBox` de 512×512** y un mismo centro óptico, para que las capas
  compongan sin recalcular nada.
- **Zonas de anclaje declaradas** para los accesorios (coordenadas y orden de
  apilamiento). Sin esto, cada accesorio nuevo es una negociación.
- **Nomenclatura de archivos** explícita y predecible:
  `logro-{familia}-{nombre}-{estado}.svg`, `marco-{nombre}.svg`,
  `accesorio-{zona}-{nombre}.svg`.
- **`currentColor`** en todo lo que deba heredar color del tema; los tokens existentes
  mandan. Si necesitas tokens nuevos, decláralos con el mismo formato `@theme` y
  **nómbralos por función**, no por color.
- Contraste **WCAG AA** en todo texto; foco visible diseñado; objetivos táctiles ≥ 44 px.
- Toda animación con su alternativa para `prefers-reduced-motion`.
- El estado bloqueado **no puede distinguirse solo por saturación**: forma, candado o
  silueta tienen que hacer el trabajo también.
- Peso: cada SVG por debajo de 8 KB. Son decenas de piezas en una PWA que se instala.

## Voz y copy

Español (Colombia): cercana, clara, positiva, profesional. Sin lorem ipsum.

- **Prohibido:** "Perdiste tu racha", "Llevas 3 semanas sin practicar", "No has logrado
  nada", cualquier cosa que cuente ausencias.
- **Preferido:** "Vas 3 semanas seguidas", "Estás a 2 clases de tu próximo logro",
  "Cada clase cuenta", "Lo importante es continuar practicando".
- Los logros se nombran por lo que la persona **hizo**, no por lo que le **falta**.

## Entregable final

1. Todas las piezas como **archivos exportables** para `docs/design-v2/gamificacion/`,
   con la nomenclatura acordada.
2. El **inventario en tabla**: pieza · archivo · estados · condición de desbloqueo
   sugerida. Es lo que se convierte en el catálogo de la base de datos, así que
   necesita estar completo.
3. Los **tokens nuevos** como bloque `@theme` copiable.
4. La **guía de capas del avatar**: zonas, orden de apilamiento, qué combina con qué.
5. Mini guía de motion de las celebraciones: qué se anima, cuánto, con qué easing.

## El listón

Un adulto de 34 años que estudia inglés para migrar tiene que poder mostrarle esta
pantalla a un colega **sin que le dé pena**. Si un elemento se ve como una app para
niños o como una plantilla de "sistema de puntos" genérico, no está listo. La prueba
final es simple: ¿esto lo motiva sin infantilizarlo?
