# Orión — Qué hace hoy

Estado al cierre de la **Tarea 3**. El MVP 1 está completo **por API**: todo lo que la academia
necesita para operar ya funciona; falta la interfaz (Tarea 4).

Este documento describe **qué** hace el sistema y **por qué** decidimos que lo haga así. Para
levantar el proyecto, mira el [README](../README.md); para probarlo a mano, la
[guía de Postman](postman-guide.md).

---

## 1. En una frase

Una estudiante entra, ve los profesores publicados, consulta los cupos **realmente libres** de
uno de ellos, reserva una clase de una hora, y recibe un correo con la invitación de calendario.
El profesor ve la clase en su agenda con el WhatsApp de la estudiante para coordinar. Si algo
cambia, cualquiera de los dos cancela —siempre que falten 24 horas o más— y el cupo vuelve a
estar disponible para otro. Cuando la clase termina, el profesor registra si la estudiante asistió.

---

## 2. Qué puede hacer cada rol

### Estudiante

| Puede | Detalle |
|---|---|
| Iniciar sesión | Sesión de servidor con cookie `httpOnly` |
| Ver el directorio de profesores | Solo los **publicados** y con cuenta activa |
| Ver el detalle de un profesor | Bio, foto y WhatsApp |
| **Consultar cupos disponibles** | El endpoint estrella: cupos reales, no horarios teóricos |
| **Reservar una clase** | Modalidad virtual o presencial, con nota de lugar opcional |
| Ver sus clases | Próximas y pasadas, con el WhatsApp del profesor |
| **Cancelar** | Solo con 24 h o más de anticipación |

**No puede:** reservar en nombre de otro estudiante (403), gestionar disponibilidad (403),
registrar asistencia (403), ni tener dos clases confirmadas que se pisen en el tiempo (422).

### Profesor

| Puede | Detalle |
|---|---|
| Gestionar su **disponibilidad semanal** | Franjas recurrentes: "los lunes de 18:00 a 21:00" |
| Gestionar **bloqueos puntuales** | Día completo (vacaciones) o rango parcial (una cita médica) |
| Editar su perfil y **publicarse o despublicarse** | Sin publicar, es invisible: no aparece en el directorio ni expone cupos |
| Ver sus clases | Con el WhatsApp de cada estudiante |
| Cancelar una clase suya | Sujeto a la misma regla de 24 horas |
| **Registrar asistencia** | Solo de clases ya terminadas |

**No puede:** reservar clases (403), ni tocar la disponibilidad o las reservas de otro profesor
(404 — no le confirmamos que existan).

### Admin

| Puede | Detalle |
|---|---|
| **Reservar en nombre de un estudiante** | Alimenta la métrica de autoservicio (ver §5) |
| **Cancelar cualquier reserva, a cualquier hora** | La válvula de "fuerza mayor" del manual corporativo — el único exento de las 24 horas |

Hoy no tiene panel propio: el CRUD de usuarios y el listado global de reservas quedaron fuera
de alcance a propósito.

---

## 3. El corazón: cómo se calculan los cupos

Es la pieza más importante del sistema y la que más cuidado recibió. Un cupo disponible es el
resultado de restar tres cosas:

```
  reglas semanales del profesor      (los miércoles de 08:00 a 11:00)
– bloqueos puntuales                 (el miércoles 15, de 09:00 a 10:00 tengo cita)
– reservas ya confirmadas            (alguien tomó las 10:00)
– lo que ya pasó                     (nunca ofrecemos un cupo que ya empezó)
= cupos disponibles                  (el miércoles 15 queda libre solo las 08:00)
```

Las reglas que gobiernan ese cálculo, y que conviene tener presentes porque explican casi
cualquier resultado que parezca raro:

**Las clases duran 60 minutos y empiezan en punto.** Una franja de 18:00 a 21:00 produce
exactamente tres cupos: 18:00, 19:00 y 20:00. Nunca uno a las 20:30 que se saldría de la franja.

**Todo se razona en hora de Bogotá.** El profesor piensa "los miércoles a las 8 de la mañana", y
eso es lo que se guarda. Los cupos se devuelven con offset explícito
(`2026-07-15T08:00:00-05:00`), nunca como una hora "flotante" sin zona ni convertidos a UTC.

**Los intervalos son semiabiertos: `[inicio, fin)`.** De aquí sale la consecuencia que más
sorprende y que es deliberada: un bloqueo de **10:00 a 11:00 elimina el cupo de las 10:00 pero
no el de las 11:00**, porque ese empieza justo cuando el bloqueo termina. Lo mismo vale para los
solapes: una franja de 18:00–21:00 y otra de 21:00–22:00 se tocan pero **no** se solapan.

**Solo el futuro.** Un cupo cuyo inicio ya pasó no se ofrece, aunque la franja siga abierta.

**El cálculo vive en memoria, no en SQL.** Es una decisión consciente: a nuestro volumen (decenas
de reglas por profesor) cargar y calcular en Java es más legible, más testeable y suficientemente
rápido. La lógica está aislada en una clase pura (`SlotCalculator`) sin Spring ni base de datos,
y sus 12 tests de contrato corren en **140 milisegundos**.

---

## 4. Las reglas de negocio que el sistema hace cumplir

### La regla de las 24 horas

> *"Con menos de 24 horas de anticipación la clase se considera impartida (política Orión)."*

Estudiantes y profesores solo pueden cancelar si faltan **24 horas o más** para la clase (la
frontera es inclusiva: exactamente 24 horas todavía cuenta). El **admin está exento**.

La decisión de si una clase es cancelable **la toma siempre el servidor** y viaja ya resuelta en
el campo `canCancel` de "mis clases". El frontend solo pinta el botón; nunca reimplementa la
política. Así, el día que la academia decida cambiarla a 48 horas, se cambia en un solo sitio.

### No hay dobles reservas, ni siquiera con dos clics simultáneos

Dos estudiantes pueden pedir el mismo cupo en el mismo milisegundo. El servicio comprueba la
disponibilidad antes de insertar, pero **entre la comprobación y el INSERT hay una ventana**, y
ningún `if` en Java puede cerrarla. Quien cierra esa ventana es la base de datos: un **índice
único parcial** garantiza que no existan dos reservas confirmadas para el mismo profesor a la
misma hora. El que pierde la carrera recibe un *"Alguien acaba de tomar este cupo"*.

El índice es **parcial** (`WHERE status = 'CONFIRMED'`) y eso es lo que permite que un cupo
cancelado se pueda volver a reservar: las filas canceladas no participan en la restricción.

### Un estudiante no puede estar en dos sitios a la vez

Aunque sean profesores distintos y ambos cupos estén libres, dos clases confirmadas de un mismo
estudiante no pueden solaparse.

### Un profesor no publicado no existe

Para el resto del mundo, un profesor sin publicar es invisible: no aparece en el directorio, su
detalle responde 404 y sus cupos también, **aunque tenga disponibilidad configurada**. Si se
despublica, sus clases ya reservadas siguen en pie; simplemente deja de aceptar nuevas.

### La vida de una reserva

```
                    ┌──────────────► CANCELLED_BY_STUDENT
                    │                CANCELLED_BY_PROFESSOR
   CONFIRMED ───────┤                CANCELLED_BY_ADMIN
                    │
                    └──────────────► COMPLETED   (asistió)
                                     NO_SHOW     (no asistió)
```

**Solo `CONFIRMED` admite cambios; los demás estados son finales.** Cancelar dos veces, o
registrar asistencia dos veces, responde **409**. Una reserva no se queda "confirmada para
siempre" después de que la clase ocurra: o se completó, o el estudiante no llegó, o se canceló.

El estado nombra **al actor** que canceló (`CANCELLED_BY_STUDENT` / `_PROFESSOR` / `_ADMIN`) en
vez de un `CANCELLED` genérico. Cuando la academia quiera saber cuántas clases cancelan los
profesores, la respuesta es un `GROUP BY`.

### La asistencia solo se registra después de la clase

Un intento de registrar asistencia de una clase que aún no termina responde **422**.

---

## 5. La métrica estrella del MVP

Cada reserva guarda **quién la creó** (`created_by`), además de para quién es (`student_id`).
Cuando coinciden, la reserva fue **autoservicio**: la estudiante la hizo sola, sin que nadie de
la academia tuviera que intervenir por WhatsApp. Ese es exactamente el problema que Orión vino a
resolver, y por eso es medible desde el primer día:

```sql
select count(*) filter (where created_by = student_id) * 100.0 / count(*) as pct_autoservicio
from bookings where status = 'CONFIRMED';
```

---

## 6. Correos

Cada **reserva** y cada **cancelación** envía un correo a **ambos** participantes. En desarrollo
los captura Mailpit (http://localhost:8025) y no sale nada a internet.

Los de confirmación llevan la hora en Bogotá, el **link de WhatsApp** de la contraparte (que es
el canal real por el que se coordinan), un adjunto **`.ics`** para cualquier calendario y un link
**"Añadir a Google Calendar"**. Los de cancelación no llevan adjunto: la clase ya no existe, no
hay nada que añadir.

**Un fallo del servidor de correo nunca tumba una reserva.** El envío ocurre *después* de que la
transacción confirme, y su error solo se registra en el log. La contrapartida es igual de
importante: si la transacción hace rollback, **no se envía nada** — nadie recibe la confirmación
de una clase que no llegó a existir.

---

## 7. Seguridad

**Sesión de servidor con cookie `httpOnly`, no JWT.** La cookie de sesión no es legible por
JavaScript, así que un ataque XSS no puede robarla; y la sesión se puede invalidar en el servidor
en cualquier momento, cosa que un JWT autocontenido no permite.

**CSRF activo.** Toda petición que modifica algo exige el header `X-XSRF-TOKEN` con el valor de
una cookie que el JavaScript legítimo sí puede leer. Un sitio malicioso puede provocar que tu
navegador envíe la cookie de sesión, pero **no puede leerla** para copiarla al header. Sin ese
header, la petición muere con 403.

**Los errores nunca revelan de más.** Un login fallido responde lo mismo si la clave está mal que
si el usuario está inactivo (decir cuál sería confirmar que el email existe). Pedir un recurso
ajeno —la regla de otro profesor, la reserva de otro estudiante— responde **404 y no 403**,
porque un 403 confirmaría que ese recurso existe.

---

## 8. Cómo está construido

**Monolito modular** en Java 21 + Spring Boot 4.1, sobre PostgreSQL 16.

| Módulo | De qué se ocupa |
|---|---|
| `identity` | Usuarios, roles, perfiles de profesor, directorio público |
| `scheduling` | Disponibilidad, cálculo de cupos, reservas, asistencia |
| `notifications` | Correos (se entera de las reservas por eventos, no por llamadas) |
| `shared` | Seguridad, manejo de errores, el reloj |

Cuatro decisiones que atraviesan todo el proyecto:

**Flyway es el dueño del esquema.** Hibernate corre en modo `validate`: nunca crea ni altera una
tabla, solo comprueba que las entidades coincidan con lo que las migraciones definieron. Si
divergen, la aplicación **no arranca**. Cuatro migraciones hasta hoy: identidad, disponibilidad,
reservas, asistencia.

**Las invariantes duras viven en la base.** El código valida para dar buenos mensajes; la base
garantiza que la regla no se pueda violar jamás, ni siquiera bajo concurrencia.

**Toda lectura de la hora pasa por un único `Clock` inyectado.** Nunca `Instant.now()` suelto. Eso
permite que los tests **congelen el tiempo** y verifiquen de forma determinista cosas como "una
clase a 23 horas y 59 minutos no se puede cancelar", sin depender de qué día sea hoy.

**Los módulos se hablan en una sola dirección.** `scheduling` conoce a `identity`, nunca al revés.
Y `notifications` no lo conoce nadie: se suscribe a eventos. Entre módulos no hay relaciones JPA,
solo UUIDs; la integridad la garantizan las claves foráneas de la base.

**123 tests** en verde. Los de la lógica de cupos son puros (sin Spring, sin base de datos) y
corren en milisegundos; el resto levantan un **PostgreSQL real** con Testcontainers, nunca un H2
en modo compatibilidad — si un test pasa, pasa contra la base que corre en producción.

---

## 9. Lo que todavía no existe

Deliberadamente fuera de alcance hasta ahora:

- **Frontend** (Tarea 4) — hoy todo se opera por API.
- **Panel de admin**: CRUD de usuarios y listado global de reservas.
- **Recordatorios automáticos** 24 h antes de la clase.
- **Reprogramar** como operación atómica: en el MVP se cancela y se vuelve a reservar.
- **Pagos y paquetes de clases** (MVP 2).
- **Sincronización real con Google Calendar** y links de Meet (MVP 2) — hoy solo se manda un
  `.ics` y un link que precarga el evento.
