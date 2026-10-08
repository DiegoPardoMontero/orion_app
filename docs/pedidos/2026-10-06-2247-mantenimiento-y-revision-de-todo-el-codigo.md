# Mantenimiento y revisión de todo el código

| | |
|---|---|
| **Fecha** | 06/10/2026, 22:47 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/ESTADO.md` |
| **Estado** | En curso |

## El pedido, tal cual

> Ejecuta una revisión, actualiza lo que puedas, corre tests y verifica que todo vaya bien. SImplemente ejecuta mantenimiento y ejecuta tarea de revisión de TODO el código solo UN ORDEN DE MAGNITUD por debajo de hacer ultrareview command.

## Resumen ejecutivo

- **Historia:** como dueño de Orión, quiero una revisión de todo el código y un mantenimiento
  (dependencias al día, pruebas, verificación), para lanzar a profesores sabiendo que la base está
  sana.
- **Cómo se hace:**
  - Revisión de solo lectura por áreas: backend del dinero y las clases, backend de identidad,
    seguridad y lo demás, y frontend. Son pocos revisores a la vez, porque el equipo tiene 7,7 GB.
    Es un orden de magnitud menos que `/code-review ultra`, que lanza muchos agentes en la nube.
  - Cada hallazgo se verifica antes de tocar código. Lo confirmado se arregla con su prueba.
  - Lo que necesita una decisión de Pardo se le pregunta.
- **Mantenimiento:**
  - las dependencias con actualizaciones menores o de parche, sin cambiar versiones mayores;
  - `npm audit`;
  - `./mvnw verify`, e2e y `next build` en verde antes de subir.

## Mantenimiento (hecho, `chore(deps)` del 06/10 noche)

- **Frontend.** `npm audit` tenía 5 avisos (1 crítico: la línea 16.2 de Next, por `postcss` y
  `sharp`). Quedó en **0** con Next 16.2.10 → 16.3.8 y sus dependencias. Además: Playwright 1.63,
  React Query 5.104, Tailwind 4.3.3, lucide-react 1.52 (los 109 íconos en uso siguen existiendo),
  tipos de React 19.3.
- **Backend.** Spring Boot 4.1.0 → 4.1.1 y springdoc 3.0.3 → 3.1.1 (parches). Flyway y el driver de
  Postgres van con el BOM de Boot.
- **No se tocaron a propósito**, por ser saltos mayores que piden una tarea propia: TypeScript 7,
  ESLint 10, Vitest 5, `@types/node` 26, React 19.3, y la milestone 4.2 de Boot.
- **Comprobado:** `tsc`, `eslint` (0 errores, 2 avisos previos), 196 pruebas de Vitest y `next build`
  en verde. El `./mvnw verify` completo y los e2e van al cierre, con lo que salga de la revisión.

## La revisión (06/10 noche al 07/10)

Tres revisores de solo lectura, en paralelo: dinero y clases; identidad y seguridad; frontend. Cada
hallazgo se verificó en el código antes de tocarlo. Ningún crítico de seguridad; **uno crítico de
producto**, el primero de la lista.

### Arreglado, con prueba

1. **Aceptar una reprogramación nunca movía la clase en la base** (`4b95680`). `starts_at` y
   `ends_at` estaban marcados `updatable = false` desde el primer commit; Hibernate no escribía el
   UPDATE. La respuesta y el correo decían la hora nueva; la fila guardaba la vieja, el cupo viejo
   seguía ocupado y el nuevo, reservable. La prueba pasaba justamente porque la fila no se movía: con
   la aserción volteada falló (`expected 15:00Z but was 14:00Z`) y con el arreglo pasa.
   - Destapó dos reglas más: mover usaba la antelación de reservar (6 h) y no la de reprogramar
     (2 h), y la clase se contaba a sí misma como cupo ocupado, así que correrla media hora era
     imposible. Las dos arregladas, en proponer y en aceptar.
   - Un movimiento publica su propio evento: solo sale el correo «cambió de hora» con su .ics, y no
     los puntos, el saludo de Rigel ni las confirmaciones de reserva nueva.
   - **Para Pardo (operativo):** en producción, `select count(*) from reschedule_requests where
     status = 'ACCEPTED'`. Cada fila es una clase que se quedó en su hora original mientras a los dos
     se les dijo otra. Si hay alguna, decidir si se les avisa.
2. **El retracto perdía el saldo aplicado** (`0c8cf89`): abría la devolución por el total y el saldo
   gastado se quedaba gastado. Ahora el saldo vuelve al saldo, Wompi devuelve solo lo cobrado, y una
   clase pagada solo con saldo (o la gratis) no abre una devolución que nunca se podía cerrar.
3. **Resolver a favor del estudiante el reclamo de una clase gratis daba 500** (`0c8cf89`): abonaba
   $0 y `StudentCredit` lo rechaza; el reclamo quedaba abierto para siempre. Ahora cierra sin saldo y
   con la ausencia registrada.
4. **La purga fallaba entera (409)** para cualquier profe con certificado anual, o admin que aprobó
   o pagó una liquidación, ocultó una reseña o propuso una reprogramación (`c213f65`): cuatro FKs
   nunca tratadas. Es el único camino de una supresión de habeas data.
5. **Un admin podía desactivarse a sí mismo o al último admin activo** y dejar el panel sin entrada
   (`c213f65`); solo un UPDATE a mano lo arreglaba. Se niega, y el freno de la purga cuenta admins
   activos, no filas.
6. **Frontend** (`a441ea4`): guardar la llave Bre-B en la postulación no refrescaba la postulación y
   «Enviar a revisión» seguía apagado hasta recargar (era mío, del 29/09); «Reportar un problema»
   salía desde el minuto 0 y el servidor lo negaba 15 minutos; el «¿Salir de la clase?» del aula era
   un velo sin rol de diálogo ni foco.

### Para decidir Pardo (se le pregunta con opciones)

- Borrar una cuenta no borra sus archivos en Cloudinary (CV, foto, certificados).
- Un profe creado desde Usuarios con el correo mal escrito no puede confirmarlo nunca y, desde el
  29/09, tampoco publicar; no hay forma de corregir un correo.
- La invitación por WhatsApp se pierde si el invitado se registra con Google.
- Resolver un reclamo a favor del estudiante deja los puntos y logros que la clase ya dio; resolverlo
  a favor del profe sobre una clase aún CONFIRMED no publica el cierre (ni puntos ni «califica»).
- Sin `@Version` en reserva y pago hay carreras de milisegundos que las constraints no cierran.
- Un ensayo del admin puede dejarle una ausencia real al profe.

### Decisiones de Pardo (07/10/2026, eligiendo entre opciones)

- **Archivos en Cloudinary:** «Borrarlos también». La purga y el borrado de un documento destruyen
  el archivo, para que una supresión de la Ley 1581 quede completa. Tarea aparte, con su prueba.
- **Correo mal escrito:** «El admin lo corrige». Desde Usuarios, cambiar el correo y reenviar la
  confirmación; el profe no lo cambia solo.
- **Invitación por WhatsApp + registro con Google:** «Que Google también la consuma». El registro
  social acepta el token de la invitación como el formulario.
- **Reclamos a medias:** «Arreglar (b), dejar (a)». Resolver a favor del profe una clase aún abierta
  debe publicar el cierre (puntos y «califica»); los puntos del estudiante tras una falta del profe
  no se le quitan.
- Pendientes de preguntar: `@Version` en reserva y pago, y la ausencia que deja un ensayo.

Las cuatro son tareas nuevas, no parte de esta revisión: quedan anotadas para la siguiente sesión.

### Anotado, menor

- `robots.txt` sigue cerrando `/profesores`, que es público desde el 23/09.
- Varios pasos de la postulación y «Experiencia» del perfil siguen a dos columnas en escritorio
  (regla del 28/09: solo Mis horarios y Ayuda).

## Estado

En curso. El `./mvnw verify` completo y a solas quedó en verde (452 + 693). La sesión se cortó por el
límite de uso antes de: los e2e sobre base limpia, el `next build` final, el push a `master`, las
preguntas a Pardo y el correo. **Todo está en commits locales, nada en producción.** Al retomar:
e2e → build → push → preguntar → correo.
