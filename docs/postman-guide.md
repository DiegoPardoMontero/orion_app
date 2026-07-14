# Probar Orión con Postman

Guía para ejercitar la API a mano. Cubre todo lo construido hasta la Tarea 3, Paso 4:
autenticación, disponibilidad, cupos, reservas y cancelación.

---

## 1. Antes de empezar

```bash
cd /home/diegopardo/orion_app
docker compose up -d                                  # Postgres + Mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

La API queda en `http://localhost:8080`. Credenciales de la semilla:

| Email | Rol | Clave |
|---|---|---|
| `ana@orion.local` | STUDENT | `orion123*` |
| `carlos@orion.local` | STUDENT | `orion123*` |
| `maria@orion.local` | PROFESSOR (publicada) | `orion123*` |
| `juan@orion.local` | PROFESSOR (sin publicar) | `orion123*` |
| `admin@orion.local` | ADMIN | `admin123*` |

Disponibilidad sembrada: **María** los lunes 18:00–21:00 y los miércoles 08:00–11:00;
**Juan** los martes 15:00–18:00 (pero no está publicado, así que no expone cupos).

---

## 2. Las dos cosas que hay que configurar (léelo, o nada funcionará)

Orión **no usa JWT**. Usa sesión de servidor con cookie + protección CSRF. Eso significa dos cosas:

### 2.1 Las cookies: Postman ya las maneja

Al hacer login, el servidor devuelve dos cookies: `ORION_SESSION` (tu sesión, `httpOnly`) y
`XSRF-TOKEN` (el token anti-CSRF). Postman las guarda en su *cookie jar* y las reenvía sola en
las siguientes peticiones al mismo host. **No tienes que hacer nada** para esto.

### 2.2 El header CSRF: esto sí lo tienes que configurar

Toda petición **mutante** (`POST`, `PUT`, `PATCH`, `DELETE`) debe llevar el header
`X-XSRF-TOKEN` con el mismo valor que la cookie `XSRF-TOKEN`. Sin él, el servidor responde
**403 `{"error":"Access denied"}`** — y es lo correcto: así es exactamente como se bloquea un
ataque CSRF (un sitio malicioso puede provocar que tu navegador mande la cookie, pero no puede
leerla para copiarla al header).

El login está exento (se protege con las credenciales mismas), así que ahí no hace falta.

**Configúralo una sola vez, en la colección:**

1. Crea una colección `Orión` y añádele una variable `baseUrl` = `http://localhost:8080`.
2. En la colección → pestaña **Scripts** → **Post-response**, pega esto:

```javascript
// Guarda el token CSRF en cuanto el servidor lo emita (login), para reusarlo en los POST.
const jar = pm.cookies.jar();
jar.get("localhost", "XSRF-TOKEN", (error, value) => {
    if (!error && value) {
        pm.collectionVariables.set("xsrf", value);
    }
});
```

3. En la colección → pestaña **Headers**, añade:

| Key | Value |
|---|---|
| `X-XSRF-TOKEN` | `{{xsrf}}` |
| `Content-Type` | `application/json` |

Con eso, **todas** las peticiones de la colección heredan el header y el token se refresca solo
tras cada login. Si alguna vez recibes un 403 inesperado, vuelve a ejecutar el login.

> Si prefieres no usar scripts: haz el login, abre **Cookies** (bajo el botón Send), copia el
> valor de `XSRF-TOKEN` a mano y pégalo como header `X-XSRF-TOKEN` en cada POST.

---

## 3. Recorrido completo

### Paso 1 — Login como Ana (estudiante)

```
POST {{baseUrl}}/api/v1/auth/login
```
```json
{ "email": "ana@orion.local", "password": "orion123*" }
```

**200** con tus datos. En **Cookies** verás `ORION_SESSION` y `XSRF-TOKEN`.

Comprueba la sesión:

```
GET {{baseUrl}}/api/v1/auth/me
```
→ **200** con Ana. Sin sesión daría **401**.

---

### Paso 2 — Ver el directorio de profesores

```
GET {{baseUrl}}/api/v1/professors
```

→ **200** con **solo María**. Juan no aparece: su perfil no está publicado.
**Copia el `id` de María** — lo necesitas para todo lo demás. (En *Scripts → Post-response*
puedes automatizarlo: `pm.collectionVariables.set("mariaId", pm.response.json()[0].id);`)

```
GET {{baseUrl}}/api/v1/professors/{{mariaId}}
```
→ **200** con bio y WhatsApp. Con el id de Juan daría **404** (existe, pero no está publicado —
no revelamos perfiles ocultos).

---

### Paso 3 — Consultar cupos

```
GET {{baseUrl}}/api/v1/professors/{{mariaId}}/slots
```

→ **200**:

```json
{
  "professorId": "...",
  "timezone": "America/Bogota",
  "slots": [
    { "startsAt": "2026-07-15T08:00:00-05:00", "endsAt": "2026-07-15T09:00:00-05:00" }
  ]
}
```

Sin parámetros el rango es **hoy → hoy+6**. Prueba también:

| Petición | Resultado |
|---|---|
| `?from=2026-07-15&to=2026-07-15` | solo ese día |
| `?from=2026-07-15&to=2026-09-15` | **400** — el rango máximo son 31 días |
| `?from=2026-07-20&to=2026-07-15` | **400** — `from` posterior a `to` |
| con el id de Juan | **404** — tiene reglas, pero no está publicado |

**Copia un `startsAt` completo, con el offset `-05:00`.** Es literalmente lo que hay que mandar
para reservar.

---

### Paso 4 — Reservar

```
POST {{baseUrl}}/api/v1/bookings
```
```json
{
  "professorId": "{{mariaId}}",
  "startsAt": "2026-07-20T18:00:00-05:00",
  "modality": "VIRTUAL",
  "locationNote": "Google Meet"
}
```

→ **201** con `status: CONFIRMED`. Guarda el `id` de la reserva.

> **Elige un cupo lejano** (los lunes de María, no el miércoles más cercano). Un cupo a menos de
> 24 horas se reserva bien, pero luego **no lo podrás cancelar** — y quieres probar la
> cancelación en el paso siguiente.

Ahora vuelve a `GET /slots`: **el cupo reservado ya no aparece**.

Errores que vale la pena provocar:

| Qué haces | Resultado |
|---|---|
| Reservar el mismo cupo otra vez | **422** `"El cupo no está disponible"` |
| Un horario que no es cupo (p. ej. `03:00:00-05:00`) | **422** |
| `"modality": "TELEPATIA"` | **400** `"modality debe ser VIRTUAL o IN_PERSON"` |
| Con el id de Juan (no publicado) | **404** |
| Añadir `"studentId"` con el id de Carlos, siendo Ana | **403** — no reservas en nombre de otro |
| Quitar el header `X-XSRF-TOKEN` | **403** — CSRF haciendo su trabajo |
| Login como María (profesora) y reservar | **403** — los profesores no reservan |

**Solapes:** reserva un cupo, y luego intenta reservar **otro profesor a la misma hora** →
**422**: un estudiante no puede estar en dos clases a la vez. (Para probarlo tendrías que
publicar a Juan primero; ver Paso 7.)

---

### Paso 5 — Mis clases

```
GET {{baseUrl}}/api/v1/me/bookings
GET {{baseUrl}}/api/v1/me/bookings?scope=past
```

→ **200** con la contraparte y su WhatsApp:

```json
[{
  "startsAt": "2026-07-20T18:00:00-05:00",
  "status": "CONFIRMED",
  "canCancel": true,
  "counterpart": { "fullName": "María Gómez", "whatsappPhone": "+57..." }
}]
```

`canCancel` **lo calcula el servidor** aplicando la regla de 24 horas. Si reservaste un cupo
cercano, verás `canCancel: false`.

Haz login como **María** y repite: verás la misma clase, pero la contraparte ahora es **Ana**.
Un ADMIN recibe **403** aquí: no tiene clases propias.

> Los teléfonos de la semilla vienen vacíos. Para verlos:
> ```bash
> docker exec -it orion-postgres psql -U orion -d orion -c \
>   "update users set whatsapp_phone='+573001112233' where email='ana@orion.local';
>    update users set whatsapp_phone='+573009998877' where email='maria@orion.local';"
> ```

---

### Paso 6 — Cancelar

```
POST {{baseUrl}}/api/v1/bookings/{{bookingId}}/cancel
```
```json
{ "reason": "Me salió un viaje" }
```

(El `reason` es opcional; puedes mandar `{}`.)

→ **200** con `status: CANCELLED_BY_STUDENT`. Vuelve a `GET /slots`: **el cupo reapareció**.

| Qué haces | Resultado |
|---|---|
| Cancelar otra vez la misma reserva | **409** `"La reserva ya no está confirmada"` |
| Cancelar una reserva de otro (login como Carlos) | **404** — no confirmamos que exista |
| Cancelar con **menos de 24 h** por delante | **422** `"Con menos de 24 horas de anticipación la clase se considera impartida (política Orión)"` |
| Lo mismo, pero logueado como **ADMIN** | **200** con `CANCELLED_BY_ADMIN` — es la válvula de fuerza mayor |
| Cancelar como la **profesora** (María) | **200** con `CANCELLED_BY_PROFESSOR` |

**Para probar la regla de 24 horas:** reserva el cupo más cercano de María (el próximo miércoles
a las 08:00 si estás a menos de un día) e intenta cancelarlo como Ana → 422. Luego repite como
admin → 200.

---

### Paso 6.5 — Los correos (Mailpit)

Cada reserva y cada cancelación envía **un correo a cada participante**. En desarrollo no sale
nada a internet: los captura Mailpit.

Abre **http://localhost:8025** después de reservar. Deberías ver 2 correos:

- **Para Ana:** *"¡Listo! Tu clase con María Gómez quedó agendada"*
- **Para María:** *"Nueva clase agendada con Ana Ramírez"*

Cada uno lleva la hora **en Bogotá**, el **link de WhatsApp** de la contraparte, un botón
**"Añadir a Google Calendar"** y un adjunto **`clase-orion.ics`**. Descarga el `.ics` y ábrelo
con tu calendario: la clase debe aparecer a la hora correcta (el archivo guarda la hora en UTC
y el calendario la traduce a tu zona).

Al cancelar llegan 2 correos más, **sin adjunto** (la clase ya no existe, no hay nada que
añadir al calendario) y con el motivo si lo indicaste.

Para vaciar la bandeja entre pruebas: botón **Delete all** en Mailpit, o
`curl -X DELETE http://localhost:8025/api/v1/messages`.

> Si Mailpit está caído, las reservas **siguen funcionando**: el correo se envía después de
> confirmar la reserva y su fallo solo se registra en el log. Es deliberado.

---

### Paso 6.6 — Registrar asistencia

Solo funciona sobre clases **ya terminadas**, así que necesitas una en el pasado. Insértala
directamente:

```bash
docker exec -it orion-postgres psql -U orion -d orion -c "
insert into bookings (student_id, professor_id, starts_at, ends_at, modality, created_by)
select s.id, p.id, now() - interval '1 day', now() - interval '1 day' + interval '1 hour',
       'VIRTUAL', s.id
from users s, users p
where s.email = 'ana@orion.local' and p.email = 'maria@orion.local';"
```

Login como **María** y busca el id con `GET /api/v1/me/bookings?scope=past`.

```
POST {{baseUrl}}/api/v1/bookings/{{bookingId}}/attendance
```
```json
{ "present": true, "notes": "Excelente participación" }
```

→ **201** con `bookingStatus: "COMPLETED"`. Con `"present": false` → `"NO_SHOW"`.

| Qué haces | Resultado |
|---|---|
| Registrar una clase que **aún no termina** | **422** `"La clase aún no termina"` |
| Registrar dos veces la misma clase | **409** — ya no está `CONFIRMED` |
| Registrar una clase cancelada | **409** |
| Registrar la clase de **otro profesor** | **404** |
| Registrar como **Ana** (estudiante) | **403** |

Vuelve a `GET /api/v1/me/bookings?scope=past`: la clase ahora aparece como `COMPLETED`.

---

### Paso 7 — Como profesora: disponibilidad y perfil

Login como `maria@orion.local`.

```
GET  {{baseUrl}}/api/v1/me/availability/rules
POST {{baseUrl}}/api/v1/me/availability/rules
```
```json
{ "weekday": 5, "startTime": "14:00", "endTime": "16:00" }
```

`weekday` es ISO: **1 = lunes … 7 = domingo**. Las reglas deben empezar y terminar **en punto**.

| Qué haces | Resultado |
|---|---|
| `{"weekday":1,"startTime":"20:00","endTime":"22:00"}` | **400** — solapa con su lunes 18:00–21:00 |
| `{"weekday":1,"startTime":"21:00","endTime":"22:00"}` | **201** — toca el borde pero no solapa |
| `{"weekday":2,"startTime":"09:30","endTime":"11:30"}` | **400** — minutos no alineados a `:00` |
| `{"weekday":8,...}` | **400** — fuera de 1–7 |
| `DELETE /rules/{id}` de una regla de Juan | **404** |
| Cualquiera de estas como **Ana** | **403** |

**Bloqueos puntuales:**

```
POST {{baseUrl}}/api/v1/me/availability/exceptions
```
```json
{ "date": "2026-07-15", "startTime": "09:00", "endTime": "10:00", "reason": "Cita médica" }
```

Omite `startTime`/`endTime` para bloquear el **día completo**. Mandar solo uno de los dos → **400**.

Ahora consulta los cupos de María para ese día: **el de las 09:00 desapareció, pero el de las
08:00 y el de las 10:00 siguen**. Eso es la semántica de intervalos semiabiertos: el bloqueo
`[09:00, 10:00)` mata solo la clase que empieza a las 09:00.

**Perfil:**

```
PUT {{baseUrl}}/api/v1/me/profile
```
```json
{
  "headline": "Profesora de inglés conversacional",
  "bio": "Diez años enseñando.",
  "photoUrl": null,
  "isPublished": false
}
```

Con `isPublished: false`, María desaparece del directorio, su detalle da **404** y sus cupos
también. Vuelve a ponerlo en `true` para dejarlo como estaba.

---

## 4. Dejar la base como la semilla

```bash
docker exec -it orion-postgres psql -U orion -d orion -c \
  "delete from attendance_records; delete from bookings; delete from availability_exceptions;"
curl -X DELETE http://localhost:8025/api/v1/messages     # vacía Mailpit
```

Si además creaste reglas nuevas, bórralas por la API o resetea del todo:

```bash
docker compose down -v && docker compose up -d      # borra el volumen; la semilla se recrea al arrancar
```

---

## 5. Si algo falla

| Síntoma | Causa casi siempre |
|---|---|
| **403** en un POST/PUT/DELETE | Falta el header `X-XSRF-TOKEN`, o el token caducó — repite el login |
| **401** en todo | No hay sesión: haz login, y comprueba que Postman guarda cookies para `localhost` |
| **422** al reservar un cupo que sí ves | El `startsAt` no coincide **exactamente**: mándalo tal cual, con el offset `-05:00` |
| **404** en cupos o detalle | Ese profesor no está publicado (Juan lo está por defecto) |
| Cupos vacíos | El rango consultado no incluye ningún lunes ni miércoles (los días de María) |
