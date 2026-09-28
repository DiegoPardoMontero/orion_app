# Una sola columna, el perfil del profe más bonito, recorrido obligatorio, Rigel a los 5 minutos y cómo activar los avisos

| | |
|---|---|
| **Fecha** | 28/09/2026, 12:53 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code, después del cierre de `2026-09-27-1035-…` |
| **Quién** | Pardo |
| **Relacionado** | `2026-09-27-1035-postulacion-obligatoria-perfil-horarios-y-recorrido.md` (de ahí salieron las dos columnas) |
| **Estado** | Hecho (28/09/2026) |

## El pedido, tal cual

> Perfecto, aunque hay algo que no me gusta, no uses dobles columnas en casi nada. En horarios y en ayuda me gustó, pero no en la ficha del profesor, por favor. Revisa ese diseño y hazlo mucho mucho más bonito. Cuando se acepta una postulación, el video de Sofia es opcional, el recorrido NO. Envía el primer mensaje saludando de Rigel a los 5 minutos. Pero antes de eso, ¿cómo hago para que se envién notificaciones web y notificaciones en celular? ¿qué necesitas de mí para activarlas?

## Resumen ejecutivo

### 1. Una sola columna, salvo Horarios y Ayuda
- **Historia:** como usuario en el computador leo cada pantalla en una sola columna, salvo Mis
  horarios (la semana con Rigel al lado) y Ayuda, donde las dos columnas sí ayudan.
- **Feature:** se quitan las dos columnas de Mi perfil del profe, la ficha y los datos del
  estudiante, Invitar y Saldo.

### 2. El perfil del profe, mucho más bonito
- **Historia:** como profe, la pantalla donde edito mi perfil se ve cuidada y agradable, en una sola
  columna.
- **Feature:** rediseño de «Perfil público» en /perfil.

### 3. Al aprobarse la postulación: el video es opcional, el recorrido no
- **Historia:** como profe recién aprobado puedo saltarme el video de Sofía, pero el recorrido por la
  app lo hago siempre.
- **Feature:** el recorrido del profe recién aprobado no se puede saltar; el video sí.

### 4. El saludo de Rigel a los 5 minutos
- **Historia:** como profe recién aprobado, Rigel me escribe su primer mensaje de saludo a los 5
  minutos.
- **Feature:** el primer mensaje de Rigel sale a los 5 minutos.

### 5. Los avisos en el navegador y en el celular (pregunta)
- **Historia:** como Pardo quiero saber qué hace falta para que Orión mande avisos al navegador y al
  celular, y qué tengo que hacer yo.
- **Respuesta:** en «Estado».

## Preguntas de Pardo después

- 28/09, al cierre: «¿Qué son las claves VAPID? ¿Cómo las configuro?» → Respondida en el chat: qué
  son, en palabras simples, y el paso a paso para generarlas y ponerlas en Railway (el mismo de
  «5. Los avisos», abajo, con más detalle).

## Estado (28/09/2026): hecho

### 5. Los avisos: qué hace falta (respuesta, dada primero en el chat)

Ya están construidos (Web Push, V61) y probados. No hace falta una app nativa: son avisos web.
Llegan al computador (Chrome, Edge, Firefox, Safari), al celular Android desde Chrome (mejor con Orión
instalada en la pantalla de inicio) y al iPhone solo con Orión instalada en la pantalla de inicio
(Safari → Compartir → «Añadir a pantalla de inicio»); lo último es una limitación de Apple.

Lo único que falta lo hace Pardo, porque una de las claves es secreta:
1. En su terminal, fuera de Claude: `npx web-push generate-vapid-keys`.
2. En Railway, servicio del **backend**: `ORION_VAPID_PUBLIC_KEY` (la pública) y
   `ORION_VAPID_PRIVATE_KEY` (la privada). `ORION_VAPID_SUBJECT` ya viene con su correo.
3. Redesplegar y mirar **Administración → Sistema**.
4. Probar: entrar, campana → «Activar avisos» → «Probar».

No cambiar las claves después: invalida todas las suscripciones. Cada persona los activa desde la
campana, y cerrar sesión los apaga en ese navegador.

### Lo que quedó

1. **Una columna**: Mi perfil del profe, Cuenta, Invitar y Saldo vuelven a una sola columna, anchas.
   Mis horarios y Ayuda conservan sus dos columnas.
2. **El perfil del profe, rediseñado**:
   - portada con la foto, el nombre, el título, la ciudad y los idiomas;
   - «Vista previa», «Ver mi perfil público» y «Compartir mi enlace». La vista previa pasó de la tarjeta
     de visibilidad a la portada;
   - la tarifa con lo que recibe y, justo debajo, la visibilidad;
   - secciones con ícono y una línea de para qué sirve cada una.
3. **Recorrido obligatorio, video opcional**:
   - solo para el profe aprobado; el del estudiante y el que se repite desde Ayuda se pueden saltar;
   - la bienvenida ya no tiene «Lo veo después»;
   - el recorrido del profe no tiene «Ahora no», «Saltar» ni Esc, y se retoma donde iba si se cierra.
4. **Rigel a los 5 minutos**:
   - el primer saludo, para estudiantes y profes, llega 5 minutos después de la primera vez que la
     persona entra;
   - se cambia en Ajustes → «Minutos hasta el saludo de Rigel» (V79).

**Verificación:** `./mvnw verify` 450 + 675 en verde; `tsc`, `lint` y 190 de Vitest; e2e 104 en verde y 1 saltada (la del reclamo, que necesita una clase dentro del plazo); `next build` de producción en verde.

**Commits:** `8c77abf` (registro), `949eb47` (Rigel a los 5 minutos), `4b3c9fd` (una columna),
`94cf153` (recorrido obligatorio), `12a73dd` (perfil rediseñado), `976e861` (e2e).
