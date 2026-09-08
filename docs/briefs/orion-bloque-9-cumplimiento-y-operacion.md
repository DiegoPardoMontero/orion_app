# Bloque 9 — Cumplimiento legal y operación real

> **Ubicación:** `docs/briefs/orion-bloque-9-cumplimiento-y-operacion.md`
> **Estado del sistema al escribir esto:** Flyway V1–V23; módulos `identity`, `scheduling`,
> `catalog`, `billing`, `messaging`, `notifications`, `reputation`, `lifecycle`, `admin`,
> `engagement`, `shared`.
> **Decisiones de Pardo (08/09/2026):** responsable = **persona natural**; **solo mayores de
> 18**; retracto **al medio de pago** y el resto como saldo; tickets para **estudiantes y
> profesores**, con WhatsApp visible.

---

## 0. Qué es esto y qué problema resuelve

Orión está construido y no se puede lanzar. No por lo que le falta al producto, sino por lo que
le falta alrededor: cobra dinero real sin contrato con el cliente, guarda fecha de nacimiento de
personas sin autorización de tratamiento, no verifica que un correo exista, no tiene forma de
enterarse de que dejó de pagar a sus profesores, y no tiene dónde recibir un reclamo.

Este bloque cierra eso. No añade una sola funcionalidad de marketplace.

**Tres restricciones que gobiernan el bloque:**

1. **La ley es un requisito, no una opinión.** Donde una norma fija un plazo o una forma, el
   código la implementa tal cual y el comentario cita el artículo. No se "interpreta a la baja"
   para simplificar.
2. **Nada de UI muerta.** Regla permanente de Pardo: cada elemento visible necesita endpoint
   real. Un formulario de tickets que no crea tickets es peor que no tenerlo.
3. **La constancia importa tanto como el acto.** Una autorización de datos que no se puede
   probar no existe (art. 9 Ley 1581: el responsable debe conservar prueba de la autorización).
   Todo lo que este bloque registra, lo registra con fecha, IP y versión del documento aceptado.

### El marco legal que aplica

| Norma | Qué obliga | Dónde aterriza |
|---|---|---|
| **Ley 1581 de 2012**, art. 4, 7, 9, 13-15 | Autorización previa, expresa e informada; prohibición de tratar datos de menores; derechos del titular; plazos de consultas (10 días hábiles) y reclamos (15 días hábiles) | Pasos 1, 2, 3, 7 |
| **Decreto 1377 de 2013**, art. 5, 12, 13 | Forma de la autorización y su prueba; datos de menores solo con autorización del representante legal; contenido obligatorio de la Política de Tratamiento | Pasos 1, 2 |
| **Ley 1480 de 2011** (Estatuto del Consumidor), art. 47, 50, 51, 53 | Derecho de retracto; información de identidad del proveedor; reversión del pago; régimen de portales de contacto | Pasos 2, 6 |
| **Ley 2439 de 2024** | Devolución en **15 días calendario** y **al mismo medio de pago**; mecanismo de PQR con constancia y seguimiento | Pasos 6, 7 |

**RNBD:** Orión **no** debe registrar sus bases ante la SIC. La obligación alcanza a sociedades
y entidades sin ánimo de lucro con activos sobre 100.000 UVT y a personas jurídicas públicas;
una persona natural está excluida. Las obligaciones de fondo aplican igual — la exención es de
registro, no de cumplimiento.

---

## Paso 0 — La identidad del responsable, en un solo sitio (bloqueante)

Los documentos legales y la pantalla de ayuda repiten los mismos datos. Si viven en el texto,
cambiar de dirección obliga a editar cinco archivos y olvidar el sexto.

Van en configuración, bajo `orion.legal`:

```yaml
orion:
  legal:
    responsable: ${ORION_LEGAL_NOMBRE}        # nombre completo de la persona natural
    documento: ${ORION_LEGAL_DOCUMENTO}       # "C.C. 1.234.567.890"
    domicilio: ${ORION_LEGAL_DOMICILIO}       # dirección de notificaciones judiciales
    ciudad: ${ORION_LEGAL_CIUDAD}
    correo: ${ORION_LEGAL_CORREO}             # habeas data y PQR
    whatsapp: ${ORION_LEGAL_WHATSAPP}         # E.164
    horario: ${ORION_LEGAL_HORARIO}
```

**En el perfil `prod` no tienen valor por defecto: si falta uno, la aplicación no arranca.**
Es deliberado — un Orión en producción sin domicilio de notificaciones incumple el art. 50 de
la Ley 1480, y es mejor que no levante a que levante mintiendo. En `local` traen valores de
ejemplo marcados visiblemente como tales.

---

## Paso 1 — Mayoría de edad y minimización (el agravante)

Hoy `student_profiles.birth_date` guarda la fecha de nacimiento de cualquiera, y la única regla
que la usa es la que impide a un menor publicar su ficha. Es decir: el sistema sabe que tiene
menores y aun así trata sus datos sin autorización del representante legal. El art. 7 de la Ley
1581 lo prohíbe y el art. 12 del Decreto 1377 solo lo permite con esa autorización, otorgada
después de escuchar al menor.

Pardo decidió **aceptar solo mayores de 18**. Eso convierte el problema en dos cambios:

### `V24__mayoria_de_edad.sql`

```sql
-- Constancia de la declaración: quién dijo ser mayor de edad y cuándo.
ALTER TABLE users ADD COLUMN age_confirmed_at TIMESTAMPTZ;

-- Las cuentas anteriores no declararon nada. Se les pide en su próxima entrada;
-- marcarlas como confirmadas sería fabricar una constancia que nadie dio.

-- Minimización (art. 4.c Ley 1581): birth_date existía para una regla que ya no
-- existe. Un dato sin finalidad no se conserva.
ALTER TABLE student_profiles DROP COLUMN birth_date;
```

> El `DROP COLUMN` destruye datos. Es intencionado y es lo correcto: conservarlos exigiría una
> finalidad declarada que ya no hay. La migración deja el conteo de filas afectadas en un
> `RAISE NOTICE`.

### Registro

Casilla obligatoria y **separada** de las demás: «Declaro que soy mayor de 18 años».
Sin marcarla, el registro responde 422. `RegistrationService` sella `age_confirmed_at`.

### Cuentas anteriores

`FreshPrincipalFilter` ya relee al usuario en cada petición; sobre eso, un gate suave: quien no
tenga `age_confirmed_at` ve un diálogo de una sola casilla al entrar, y no puede reservar hasta
firmarlo. No se le cierra la cuenta ni se le pierde el saldo.

**Se cae la regla del menor en `engagement`**: la visibilidad de la ficha deja de depender de la
edad. El interruptor sigue decidiendo si otros estudiantes la ven.

---

## Paso 2 — Documentos legales versionados y su aceptación

Módulo nuevo **`legal`**. Depende solo de `shared`. Es `identity` quien depende de él, no al revés.


### `V25__documentos_legales.sql`

Solo una tabla, no dos. **`agreement_acceptances` ya existía** desde la V12 —creada para el
acuerdo del profesor— con exactamente la forma que hacía falta: usuario, documento, versión,
fecha, IP y user-agent, más un índice único por los tres primeros. Es literalmente la constancia
que exige el art. 9 de la Ley 1581. Crear una `legal_acceptances` en paralelo habría dejado dos
sitios donde buscar la misma respuesta.

```sql
CREATE TABLE legal_documents (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(30) NOT NULL CHECK (code IN ('TERMS', 'PRIVACY')),
    version        VARCHAR(20) NOT NULL,
    title          VARCHAR(160) NOT NULL,
    body           TEXT NOT NULL,              -- markdown, con marcadores {{...}}
    effective_from DATE NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (code, version)
);
```

**La entidad de aceptaciones se muda de `identity` a `legal`.** Sin eso había un ciclo: `identity`
necesitaba `legal` para registrar la aceptación en el alta, y `legal` necesitaba `identity` para la
entidad de aceptación. Como una aceptación de documento legal es, precisamente, algo de `legal`, la
mudanza deja una sola dirección —`identity → legal`— y de paso pone en su sitio el acuerdo del
profesor, que llevaba viviendo en el módulo equivocado.

**El texto se guarda con marcadores y se rellena al leerlo.** El cuerpo conserva
`{{responsable}}`, `{{domicilio}}` y compañía. Así, cambiar el domicilio de notificaciones lo
actualiza en todos los documentos sin publicar una versión nueva — que es lo que necesita quien va
a notificar algo. Lo que queda congelado por versión son las cláusulas, que es lo que se aceptó.

### Dos casillas, no una

La autorización de tratamiento de datos debe ser **específica**: empaquetarla junto a la
aceptación de los términos la vicia. En el registro van separadas y ambas obligatorias:

- ☐ Acepto los **Términos y condiciones**
- ☐ Autorizo el **tratamiento de mis datos personales** conforme a la Política

Cada una registra su propia fila. Y una tercera, ya del Paso 1, para la mayoría de edad.

### Los textos

Se escriben en `backend/src/main/resources/legal/` y los siembra un `ApplicationRunner`
idempotente. Interpolan los valores de `orion.legal` (Paso 0) — el texto nunca lleva el NIT
escrito a mano.

**La Política de Tratamiento debe contener, por el art. 13 del Decreto 1377:** nombre y datos
de contacto del responsable · tratamiento y finalidad · derechos del titular · área que atiende
peticiones · procedimiento para ejercer los derechos · fecha de entrada en vigencia. El test
`PoliticaCompletaTest` verifica que las seis secciones existan; es un test de contenido legal,
no de formato.

**Los Términos deben decir, entre otras cosas:** que Orión es un **portal de contacto** entre
estudiante y profesor (art. 53 Ley 1480) y qué implica; la comisión; el ciclo de una clase;
**la política de cancelación** (12 h) y **el derecho de retracto** con su plazo real; que el
profesor es contratista independiente; y los canales de atención.

### Pantallas

`/terminos` y `/privacidad`, públicas, renderizadas desde la base (no hardcodeadas), con la
versión y la fecha de vigencia visibles. El pie de la landing deja de decir «próximamente».

### Cambio de versión

Publicar una versión nueva exige re-aceptación: al entrar, quien no haya aceptado la vigente ve
el diálogo con lo que cambió. Sin re-aceptar no puede reservar.

---

## Paso 3 — Verificación de correo

Hoy cualquiera se registra con el correo de otro y recibe sus confirmaciones. Vive en `identity`.

### `V26__email_verification.sql`

```sql
CREATE TABLE email_verifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256, nunca el token
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
```

Mismo patrón que `password_reset` (V5): token con hash, expiración de 24 h, un solo uso.

**Qué se bloquea sin verificar:** reservar. Nada más. Puede entrar, buscar profesores, ver
perfiles y escribir a soporte. El correo es lo que hace llegar la confirmación y el `.ics`; sin
él verificado, una reserva es una promesa a un buzón que nadie sabe si existe.

**Las cuentas anteriores** se marcan verificadas en la migración: llevan tiempo recibiendo
correo del sistema y bloquearlas retroactivamente rompería a quien ya usa Orión.

Reenvío con límite (Paso 4). Correo con la marca, como todo lo demás.

---

## Paso 4 — Límite de intentos

Sin dependencia nueva. A la escala de Orión —decenas de usuarios, una instancia— un contador en
memoria con ventana deslizante es suficiente y honesto; una solución distribuida sería
arquitectura para un problema que no tenemos.

`RateLimitFilter` en `shared/security`, con cubos por IP y por identificador:

| Endpoint | Límite |
|---|---|
| `POST /auth/login` | 5 por 15 min y por (IP + correo) |
| `POST /auth/register` | 3 por hora y por IP |
| `POST /auth/forgot-password` | 3 por hora y por correo |
| Reenvío de verificación | 3 por hora y por usuario |

Al excederse: **429** con JSON y `Retry-After`. Nunca un redirect ni un HTML.

> **Ojo con el bloqueo por correo en login:** limitar por correo permite a un tercero dejar sin
> acceso a alguien fallando adrede. Por eso el cubo es por **(IP + correo)**, no por correo
> solo: molesta al atacante sin dejar fuera al titular desde su propia red.

Cuando haya proxy delante (Railway), la IP sale de `X-Forwarded-For` y solo se confía en el
proxy conocido.

---

## Paso 5 — Retracto y devoluciones

Aquí conviven dos derechos distintos que hoy están mezclados en uno solo:

| | **Cancelación ordinaria** | **Derecho de retracto** (art. 47 Ley 1480) |
|---|---|---|
| Cuándo | Hasta 12 h antes de la clase | Dentro de **5 días hábiles** desde la reserva |
| Excepción | — | **No aplica si la clase ya empezó** con acuerdo del estudiante |
| Devolución | **Saldo a favor**, inmediato | **Al mismo medio de pago**, máx. **15 días calendario** |
| Quién decide | El estudiante | El estudiante; Orión no puede negarlo |

Una clase reservada para dentro de tres semanas y cancelada al día siguiente **da derecho a
retracto**: dinero de vuelta a la tarjeta, no saldo. Una clase para mañana, no — su prestación
está por comenzar con acuerdo del consumidor.

### `V27__reembolsos.sql`

```sql
CREATE TABLE refund_requests (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id     UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    payment_id     UUID NOT NULL REFERENCES payments(id),
    student_id     UUID NOT NULL REFERENCES users(id),
    reason         VARCHAR(30) NOT NULL CHECK (reason IN ('RETRACTO', 'DISPUTA', 'ADMIN')),
    amount_cop     BIGINT NOT NULL CHECK (amount_cop > 0),
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'PAID', 'REJECTED')),
    due_at         TIMESTAMPTZ NOT NULL,     -- creación + 15 días calendario
    requested_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at    TIMESTAMPTZ,
    resolved_by    UUID REFERENCES users(id),
    wompi_reference VARCHAR(120),            -- constancia de la devolución manual
    note           TEXT,
    UNIQUE (booking_id)
);
```

### Flujo

1. El estudiante ve «Retractarme» en una clase que aún está dentro de los 5 días hábiles y no ha
   empezado. El botón dice qué implica: devolución al medio de pago, hasta 15 días.
2. La reserva se cancela, el cupo se libera, el pago pasa a `REFUND_PENDING` y **nunca se le
   libera al profesor**.
3. Se crea el `refund_request` con su `due_at`.
4. Administración lo ve en «Pagos → Devoluciones» con los días restantes, hace la devolución en
   el panel de Wompi y la marca con la referencia. Sin referencia no se puede marcar como pagada
   — misma regla que ya rige las liquidaciones.
5. Correo al estudiante en los dos momentos: al aceptar el retracto y al confirmar la devolución.

### La alarma

Un `refund_request` a menos de 5 días de su `due_at` **es una alerta por correo** (Paso 8), no
una fila más en una tabla. Incumplir ese plazo es lo que sanciona la SIC.

### Y la política que estaba pendiente

Cancelar una clase pagada **fuera** de retracto y **dentro** de las 12 h: el estudiante pierde el
valor, el profesor lo cobra. Deja de estar "por decidir" y queda **escrito en los Términos**, que
es donde tiene efecto.

---

## Paso 6 — Soporte: tickets y WhatsApp

Módulo nuevo **`support`**. El art. 50 de la Ley 1480 exige un mecanismo, en el mismo medio, que
deje **constancia de la fecha y hora** de la petición y permita **seguimiento**. Un número de
WhatsApp no cumple eso; un ticket sí. Van los dos: el ticket es el registro, WhatsApp es el trato.

### `V28__soporte.sql`

```sql
CREATE TABLE support_tickets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(12) NOT NULL UNIQUE,      -- "ORN-4F2A19", legible por teléfono
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category     VARCHAR(30) NOT NULL,
    subject      VARCHAR(160) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                 CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    booking_id   UUID REFERENCES bookings(id) ON DELETE SET NULL,
    due_at       TIMESTAMPTZ,                      -- solo las categorías con plazo legal
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE support_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id  UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    author_id  UUID NOT NULL REFERENCES users(id),
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Las categorías con plazo legal

| Categoría | Plazo | Fuente |
|---|---|---|
| `HABEAS_DATA_CONSULTA` | 10 días hábiles | art. 14 Ley 1581 |
| `HABEAS_DATA_RECLAMO` | 15 días hábiles | art. 15 Ley 1581 |
| `RETRACTO` | 15 días calendario | Ley 2439 de 2024 |
| `PAGO`, `CLASE`, `CUENTA`, `OTRO` | sin plazo legal | — |

El `due_at` lo calcula el servidor al crear el ticket. Las tres primeras categorías **son el
canal de derechos del titular** que exige la ley: no hace falta un formulario aparte, hace falta
que este esté y responda a tiempo.

### Quién abre y qué ve

Estudiantes y profesores abren tickets. Cada quien ve los suyos. El administrador los ve todos,
ordenados por plazo restante — lo que vence antes, primero.

### Pantallas

- `/ayuda` — abrir ticket, ver los propios, y el botón de WhatsApp con el horario de atención.
- `/ayuda/[code]` — el hilo.
- `/admin/soporte` — bandeja, con el vencimiento en rojo cuando falten menos de 2 días.

---

## Paso 7 — Pantalla de ajustes

`AdminSettingsController` ya existe (`GET` y `PUT /api/v1/admin/settings/{key}`) y no tiene
pantalla. Hoy cambiar la comisión exige un cliente HTTP.

**Es la pantalla más peligrosa de Orión**: un cero de más en `commission_rate_bps` cambia lo que
cobra cada reserva desde ese instante. Las protecciones son parte del entregable, no un extra:

1. **Agrupados por dominio** —dinero, plazos, reputación, gamificación— y cada uno con su
   explicación en español, no solo la clave.
2. **Validación por tipo y rango en el servidor**, no en el formulario: `commission_rate_bps`
   entre 0 y 5000, las ventanas en horas positivas, los enumerados contra sus valores válidos.
   Un `PUT` fuera de rango responde 422 con los valores admitidos.
3. **Los sensibles piden confirmación escrita**, como la purga: para cambiar la comisión hay que
   escribir el valor nuevo otra vez.
4. **Historial visible**: quién cambió qué, de qué a qué y cuándo. `platform_settings` ya guarda
   `updated_by`; falta la tabla de historial y mostrarla.

### `V29__settings_historial.sql`

```sql
CREATE TABLE platform_setting_changes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key         VARCHAR(80) NOT NULL,
    old_value   TEXT,
    new_value   TEXT NOT NULL,
    changed_by  UUID NOT NULL REFERENCES users(id),
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

> **Lo que NO se toca desde ahí:** las llaves de Wompi, el correo y los datos del Paso 0. Eso es
> configuración de despliegue, no un ajuste de negocio, y ponerlo en una pantalla web es
> convertir una sesión de administrador robada en una fuga de credenciales.

---

## Paso 8 — Observabilidad con alerta al correo

Dos preguntas que hoy nadie puede responder: *¿algo se rompió?* y *¿los procesos siguen
corriendo?* La segunda es la cara: si el job de cierre se detiene, nadie cobra y el síntoma
tarda semanas.

Sin servicio externo: el correo ya funciona y llega. Vive en `shared/observability`.

### Errores

Un `@ControllerAdvice` por debajo del `GlobalExceptionHandler` que ya existe captura lo que
llegó a 500, calcula una **firma** (clase de excepción + primer frame propio) y manda un correo
a `orion.alerts.to`.

**Con freno**: máximo un correo por firma cada hora, y un tope diario global. Un error en bucle
manda un correo, no seiscientos — una bandeja saturada es exactamente igual de ciega que una
vacía. El correo lleva firma, conteo en la ventana, ruta, método, id de usuario si lo hay, y la
traza recortada. **Nunca** cuerpos de petición, cookies ni cabeceras de autorización.

### Latido de los procesos

`platform_jobs` ya registra la última corrida de cada job. Un `JobWatchdog` cada 15 minutos
compara contra el intervalo esperado, con margen:

| Proceso | Espera | Alerta si lleva sin correr |
|---|---|---|
| Cierre de clases | 1 h | 3 h |
| Expiración de reservas | 5 min | 30 min |
| Métricas y ranking | diario 3:00 | 30 h |

Un correo por proceso caído y por día. Y otro, distinto, cuando vuelve: saber que se arregló
importa tanto como saber que se rompió.

### Vencimientos

El mismo mecanismo cubre lo del Paso 5 y el 6: devoluciones y tickets con plazo legal a menos de
2 días de vencer, en un solo correo diario de resumen.

---

## Paso 9 — Filtro por disponibilidad en el buscador

Lo que de verdad pregunta quien busca clase es «¿quién puede los martes por la noche?», y hoy no
se puede filtrar por eso.

Se añaden dos parámetros a `GET /api/v1/professors`:

- `day` — uno o varios días de la semana
- `from` / `to` — franja horaria en hora de Bogotá

**El filtro es sobre las reglas de disponibilidad publicadas, no sobre cupos libres calculados.**
Es una decisión, y la razón es que un cupo libre depende del instante y de las reservas vivas:
filtrar por eso obligaría a correr `SlotCalculator` sobre cada profesor de cada página, y el
resultado cambiaría entre la búsqueda y el clic. Filtrar por la regla contesta «este profesor
suele tener martes por la noche», que es la pregunta real. La disponibilidad exacta se ve al
entrar al perfil, donde ya se calcula bien.

En la interfaz va dentro de **Avanzado**, junto a los demás. El contador de filtros activos los
cuenta.

---

## Orden de ejecución y commits

Un commit por paso, mensaje convencional en inglés, `./mvnw verify` en verde antes de cerrar
cada uno.

| # | Paso | Migración | Depende de |
|---|---|---|---|
| 0 | Identidad del responsable | — | — |
| 1 | Mayoría de edad y minimización | V24 | 0 |
| 2 | Documentos legales y aceptación | V25 | 0, 1 |
| 3 | Verificación de correo | V26 | — |
| 4 | Límite de intentos | — | 3 |
| 5 | Retracto y devoluciones | V27 | 2 |
| 6 | Soporte: tickets y WhatsApp | V28 | 0 |
| 7 | Pantalla de ajustes | V29 | — |
| 8 | Observabilidad y alertas | — | 5, 6 |
| 9 | Filtro por disponibilidad | — | — |

Los pasos 0-2 son los que desbloquean el lanzamiento. Del 3 en adelante se puede reordenar
según convenga, salvo las dependencias de la tabla.

---

## Lo que este bloque NO hace

- **No constituye la sociedad.** Orión opera como persona natural por decisión de Pardo; si eso
  cambia, cambian los datos del Paso 0 y se publica una versión nueva de los documentos.
- **No reembolsa por API.** Wompi no lo expone. El Paso 5 construye el seguimiento, la alarma y
  la constancia; la devolución la hace una persona en el panel de Wompi.
- **No abre Orión a menores.** Si algún día se decide, es un bloque propio: autorización
  verificable del representante legal, constancia de que el menor fue escuchado, y una ficha que
  nunca puede ser pública.
- **No sustituye a un abogado.** Los textos siguen la ley y el uso del sector, y las seis
  secciones obligatorias están cubiertas por test. Antes de publicar, que los lea alguien
  habilitado para firmarlos.
