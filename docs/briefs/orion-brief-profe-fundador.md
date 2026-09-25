# Brief · Profe fundador: 15 % de comisión durante 3 meses

> 25/09/2026 · Decide: Pardo · Ejecuta: Claude Code
> Léelo junto con `CLAUDE.md` y `docs/ESTADO.md`. Construir algo que no esté en este brief es una
> violación de las reglas. **Empieza cuando el paso 10 del Bloque 11 (clase de prueba) ya esté
> comprometido y subido.**

---

## Contexto

La decisión Q1 del brief maestro fijaba una comisión del 20 % para todos. Este brief la modifica:

- La comisión de Orión **sigue siendo 20 %**.
- Los **profes fundadores** pagan **15 % durante sus primeros 3 meses de clases**.

Es un beneficio con fecha de fin que el profe conoce desde el primer día. Así, cuando pase a 20 %,
lo vive como el final de un beneficio y no como una subida. El mensaje oficial es:

> «La comisión de Orión es 20 %. Como profe fundador, tienes 15 % durante tus primeros 3 meses de
> clases.»

## Reglas de negocio

1. **Comisión base: 20 %.** Es el ajuste que ya existe y no cambia.
2. **Fundador: 15 % durante 3 meses.**
3. **Quién es fundador:**
   - Los profes que entran por una invitación de Sofía.
   - El admin también puede **otorgar o quitar** el beneficio a mano, para profes que entraron antes
     de esto.
4. **Cuándo empieza a contar:**
   - El conteo arranca con la **primera clase pagada**: la primera reserva del profe con pago
     aprobado, o su primera clase de prueba de $0 confirmada.
   - Mientras el conteo no haya empezado, el beneficio ya aplica.
5. **Cuándo termina:** `founder_until` = la fecha de inicio **en zona Bogotá** + 3 meses
   calendario, a las 00:00 de Bogotá.
6. **Qué reservas llevan el 15 %:**
   - La comisión se sigue congelando **al crear la reserva** (regla actual).
   - Una reserva creada antes de `founder_until` lleva 15 %, aunque la clase se dicte después.
   - Una reserva creada exactamente en `founder_until` o después lleva la comisión base.
7. **Clase de prueba:** lleva la misma comisión que una clase normal (Q7). Si el profe es fundador,
   también es 15 %.
8. **La promesa se congela al otorgarla.** El porcentaje y la duración se copian al perfil del profe
   en ese momento. Si después se cambian los ajustes, no le afecta a quien ya es fundador.
9. **El conteo no se reinicia.** Si la primera clase pagada se cancela o se reembolsa, los 3 meses
   siguen corriendo.
10. **Quitar el beneficio solo afecta reservas nuevas.** Las que ya existen conservan su comisión.

## Antes de escribir código

Revisa cómo funcionan hoy estas piezas:

- `/invitacion` y la forma en que el admin crea invitaciones.
- El diseño de la pantalla de invitación que Pardo ya te pasó.
- Dónde se calcula y se congela `commission_rate_bps`, y con qué fórmula se redondea (COP sin
  decimales).
- El ajuste de comisión en `platform_settings` / `SettingDefinition`.
- Todos los lugares donde el profe escribe su tarifa: el editor de perfil, el precio de prueba y, si
  lo pide, el asistente de postulación.
- `/ganancias`: confirma que muestra la comisión **congelada en cada reserva** y no el ajuste global.
- A qué módulo pertenece `ProfessorProfile` y qué evento se publica cuando se aprueba un pago.

Si algo de esto no coincide con lo que asume el brief (por ejemplo, que las invitaciones no las cree
el admin, o que el inicio del conteo obligue a una dependencia nueva entre módulos), **detente y
repórtaselo a Pardo** antes de seguir.

---

## Paso 1 · Modelo, política de comisión e inicio del conteo

**Migración** (la siguiente libre, probablemente la V63), sobre `professor_profiles`:

| Columna | Tipo | Nota |
|---|---|---|
| `founder_rate_bps` | `INTEGER NULL` | CHECK entre 0 y 10000 |
| `founder_period_months` | `SMALLINT NULL` | CHECK > 0 |
| `founder_granted_at` | `TIMESTAMPTZ NULL` | |
| `founder_started_at` | `TIMESTAMPTZ NULL` | |
| `founder_until` | `TIMESTAMPTZ NULL` | |

CHECKs de coherencia:

- `founder_rate_bps`, `founder_period_months` y `founder_granted_at` son todos nulos o ninguno lo es.
- `founder_started_at` y `founder_until` son los dos nulos o los dos no nulos.
- `founder_started_at` exige que el profe tenga `founder_rate_bps`.

Ajustes nuevos:

- `founder_commission_rate_bps = 1500`
- `founder_period_months = 3`

Van validados en `SettingDefinition` y editables en la pantalla de Ajustes. **Solo se leen al
otorgar el beneficio.**

**Política pura.** Es una clase sin Spring, con el «ahora» por parámetro, igual que
`SlotCalculator`. Por ejemplo, `CommissionPolicy.effectiveRate(baseRateBps, founderTerms,
bookingCreatedAt)`:

| Situación | Comisión |
|---|---|
| No es fundador | La base |
| Fundador, conteo sin empezar | La de fundador |
| Fundador, reserva creada antes de `founder_until` | La de fundador |
| Fundador, reserva creada en `founder_until` o después | La base |

**Al crear la reserva:** congela `commission_rate_bps` usando la política. Aplica igual a la clase
de prueba.

**Inicio del conteo:**

- Ocurre al aprobarse el primer pago del profe, o al confirmarse su primera prueba de $0.
- Hazlo con un `UPDATE … SET founder_started_at = …, founder_until = … WHERE id = ? AND
  founder_rate_bps IS NOT NULL AND founder_started_at IS NULL`. Es idempotente: la base decide y no
  se usan locks.
- Usa el `Clock` de `shared/config` y `BusinessZone.BOGOTA`.

**Pruebas:**

- Unitarias de la política:
  - No fundador.
  - Conteo sin empezar.
  - Dentro del periodo.
  - Exactamente en `founder_until`.
  - Después del periodo.
- Unitaria del cálculo de `founder_until`: fin de mes (31 de octubre + 3 meses) y cruce de año.
- `FounderCommissionIT`, con estos casos:
  - El profe fundador recibe su primera reserva al 15 %.
  - El pago aprobado arranca el conteo.
  - Una reserva creada dentro del periodo va al 15 %.
  - Con el reloj movido pasado `founder_until`, la reserva va al 20 %.
  - Cambiar los ajustes después no altera al profe que ya es fundador.
  - La clase de prueba va al 15 %.
  - Un profe no fundador va al 20 %.
  - Cancelar la primera clase no reinicia el conteo.
- `EarningsAndPayoutsIT` y sus parecidos deben seguir en verde: calcula la comisión esperada con la
  política, no con un número fijo.

## Paso 2 · Otorgar el beneficio: invitación y admin

**Invitaciones:**

- Las invitaciones que crea el admin traen la marca de fundador, **activada por defecto**.
- Cuando el invitado queda como profe, se copian a su perfil el porcentaje y la duración vigentes, y
  se fija `founder_granted_at`.

**Admin**, en el detalle del profe en `/admin/usuarios`:

- Muestra uno de estos estados:
  - «Fundador · 15 % · sin empezar».
  - «Fundador · 15 % hasta el 12 de enero de 2027».
  - «Fundador · terminó el 12 de enero de 2027».
  - «Sin beneficio de fundador».
- Acciones **Otorgar** y **Quitar**:
  - Quitar solo afecta reservas nuevas.
  - Si existe un registro de auditoría del admin, anótalo ahí.

**API:**

- El endpoint público de la invitación devuelve si trae el beneficio, con qué porcentaje y por
  cuánto tiempo. Así la pantalla lo muestra antes de que exista la cuenta.
- La respuesta del perfil propio del profe (`/me/…`) devuelve:
  - `baseRateBps`.
  - `founder { rateBps, periodMonths, startedAt, until, status }`, con `status` en
    `NOT_STARTED | ACTIVE | ENDED`.
  - `founder` es `null` si el profe no es fundador.
- Regenera los tipos con `npm run types:api`.

**Pruebas:** IT de otorgar y quitar, que solo el admin pueda hacerlo, y el flujo de invitación que
llega a profe con el beneficio.

## Paso 3 · Mostrárselo al profe

**Pantalla de invitación**, según el diseño que ya tienes. Solo aparece si la invitación trae el
beneficio.

> **La comisión de Orión es 20 %. Como profe fundador, tienes 15 % durante tus primeros 3 meses de
> clases.**
> Los 3 meses empiezan a contar desde tu primera clase pagada.

Los números salen de la API, no quedan escritos en el código.

**Campos de tarifa** (tarifa regular, precio de prueba y, si aplica, el asistente de postulación).
Muestra una ayuda que se recalcula en vivo mientras el profe escribe:

| Estado | Texto (ejemplo con $60.000) |
|---|---|
| Fundador, sin empezar | «Recibes $51.000 por clase: 15 % de comisión como profe fundador durante tus primeros 3 meses de clases. Después recibirás $48.000 (20 %).» |
| Fundador, activo | «Recibes $51.000 por clase: 15 % de comisión como profe fundador hasta el 12 de enero de 2027. Después recibirás $48.000 (20 %).» |
| No fundador o ya terminó | «Recibes $48.000 por clase (comisión de Orión: 20 %).» |

Detalles:

- Con precio de prueba $0, la ayuda dice «Clase gratis: no pagas comisión.»
- El cálculo del frontend usa **exactamente el mismo redondeo que el backend**. Cúbrelo con vitest,
  incluidos valores que no den exactos.
- Las fechas van en es-CO y en zona Bogotá.

**Pruebas:**

- Vitest de la ayuda en sus cuatro estados.
- Un e2e en el que la invitación muestra el bloque de fundador y el editor de perfil muestra la ayuda
  con los montos correctos.

## Paso 4 · Aviso antes de que termine

- **14 días antes de `founder_until`**, un aviso en la campana y un correo al profe:

  > «Tu comisión de profe fundador (15 %) termina el 12 de enero de 2027. Desde ese día, las
  > reservas nuevas llevan la comisión estándar de Orión, 20 %. Las reservas que ya tengas conservan
  > el 15 %.»

- El aviso se manda **una sola vez**: agrega la columna `founder_expiry_notified_at` o una
  constraint equivalente.
- Va en un trabajo programado, con `TransactionTemplate` y vigilado por `JobWatchdog`. Puede ser uno
  de los existentes si encaja con naturalidad.
- **Pruebas:** un IT con el reloj movido que verifique que el aviso sale una sola vez, y que no sale
  para no fundadores ni para fundadores cuyo conteo no ha empezado.

---

## Fuera de alcance

- Una comisión distinta según de dónde vino el estudiante.
- El texto legal de los términos del profe. Pardo lo manda aparte; no lo redactes.
- Cambios visuales en `/ganancias`, más allá de corregirla si resulta que no usa la comisión
  congelada por reserva.

## Al terminar

- Actualiza `docs/ESTADO.md`: la decisión Q1 queda modificada por este brief.
- Agrega los flujos de fundador a la lista de verificación del paso 11: invitación, ayuda en la
  tarifa, estados en el admin y aviso de vencimiento.
- Deja anotado para Pardo que el manual técnico necesita esta sección.

---

## Decisiones de Pardo del 25/09 (lo que cambia de este brief)

El brief no tenía el último estado. Esto manda sobre lo de arriba:

1. **La base hoy es 15 %, no 20 %** (V40 la bajó para todos). Este bloque la sube a **20 %**.
2. **Todos los profes que ya están en Orión quedan como fundadores**, sin que el admin los marque uno
   por uno: nadie pasa de 15 % a 20 % de golpe. Sus 3 meses cuentan desde su primera clase pagada,
   con la misma regla que los nuevos (si ya la tuvieron, desde esa fecha).
3. **La invitación ya no crea la cuenta del profe.** El invitado pasa por la revisión como cualquier
   aspirante: la pantalla de invitación (`design_handoff_orion_invitacion`) → «Aceptar la invitación»
   → registro de profesor con el correo de la invitación puesto y bloqueado (acepta Términos y
   política de datos ahí) → su postulación → Sofía la aprueba. El token se consume al crear la
   cuenta, y **el beneficio de fundador se otorga al aprobarse la postulación**, con el porcentaje
   y la duración vigentes en ese momento. Los enlaces viejos (`/invitacion?token=`) siguen sirviendo.
4. **La invitación dura 7 días**, como hoy.
5. **«Te invita Sofía, directora académica»**: el nombre sale de la cuenta del admin que invita, y el
   cargo es un campo nuevo de esa cuenta, que el admin escribe una vez.
6. La pantalla de invitación lleva la línea del beneficio (la «decisión abierta» del handoff: sí hay
   beneficio real), con los números de la API.
7. La clase de prueba es gratis desde la V65: la ayuda de la tarifa solo dice «Clase gratis: no
   pagas comisión» para la prueba; ya no hay «precio de prueba».
8. Las migraciones van desde la V70 (la última es la V69).
