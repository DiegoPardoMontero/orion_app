# Brief · Liquidaciones quincenales bajo mandato

> 25/09/2026 · Decide: Pardo · Ejecuta: Claude Code
> Léelo junto con `CLAUDE.md` y `docs/ESTADO.md`. Construir algo que no esté en este brief es una
> violación de las reglas.
> **Orden:** empieza cuando estén comprometidos el paso 10 del Bloque 11 y el brief de
> `orion-brief-profe-fundador.md`. Este brief usa la comisión congelada por reserva, incluida la de
> fundador.

---

## Contexto

Orión recauda con Wompi, a nombre de Pardo como persona natural, el dinero que pagan los
estudiantes. Desde este brief, Orión funciona como **mandatario**:

- El dinero de cada clase **es del profe**. Orión lo recibe por cuenta del profe y se lo entrega.
- **El único ingreso de Orión es su comisión.**

En la contabilidad, lo recaudado es un pasivo con cada profe hasta que se le paga. Por eso el sistema
tiene que dejar trazado, clase por clase, cuánto se recibió en nombre de quién, cuánto se descontó y
cuándo se entregó.

Hoy la liquidación es manual. Este brief la convierte en un proceso quincenal con cortes fijos, un
comprobante para el profe, un certificado anual y exportaciones para contabilidad. **La
transferencia sigue siendo manual**: Pardo paga por Bre-B desde su cuenta bancaria y registra el
pago en Orión. No hay integración bancaria ni de Wompi Pagos a Terceros.

## Reglas de negocio (decisiones de Pardo)

**Mandato**
1. El profe acepta el mandato en sus términos (paso 1). Sin esa versión aceptada, sus clases se
   pueden reservar, pero sus liquidaciones quedan **retenidas** hasta que la acepte.
2. La comisión es la **congelada en cada reserva** (`commission_rate_bps`), incluida la de fundador.
   Este brief no recalcula comisiones.
3. La comisión de Wompi y el costo de la transferencia los asume Orión. Al profe no se le descuenta
   nada distinto de la comisión.

**Quincenas y cortes** (todo en `BusinessZone.BOGOTA`)
4. Hay dos cortes al mes: a las **00:00 del día 16** (cierra la quincena del 1 al 15) y a las
   **00:00 del día 1** (cierra la del 16 al último día del mes anterior).
5. **Qué es liquidable** en un corte: cada reserva que cumpla todo esto al momento del corte:
   - La clase quedó dictada y cerrada según el ciclo de vida actual.
   - Tiene pago aprobado o está pagada con saldo a favor.
   - Ya venció su plazo de reclamo, usando la regla y el ajuste que ya existen.
   - No tiene reclamos abiertos.
   - No está incluida en otra liquidación.

   Lo que todavía no cumple pasa solo al corte siguiente. Una reserva de valor neto $0 (prueba
   gratis) no genera línea.
6. Las reservas pagadas con **saldo a favor** se liquidan igual que las demás: el valor de la clase
   es del profe que la dictó.
7. **Fecha de pago comprometida:** a más tardar el **3.er día hábil** después del corte. Usa el
   cálculo de días hábiles con festivos colombianos que ya existe para el retracto.

**Liquidación**
8. Hay **una liquidación por profe y por quincena**, con líneas por reserva y líneas de ajuste.
   Cada línea lleva:
   - La fecha de la clase.
   - El estudiante: nombre y la inicial del apellido.
   - El valor bruto.
   - El porcentaje de comisión.
   - La comisión.
   - El neto.
9. **Ajustes:** si una clase ya pagada termina con un reclamo o una devolución a favor del
   estudiante, se crea un **ajuste negativo** por su neto en la siguiente liquidación del profe.
   Nunca se modifica una liquidación pagada.
10. Si el total de una liquidación queda en $0 o menos, no se paga. El saldo pasa a la siguiente como
    ajuste de arrastre.
11. **Estados:** `BORRADOR` → `APROBADA` → `PAGADA`, más `RETENIDA`:
    - `RETENIDA` se usa cuando faltan los datos de pago del profe o la aceptación del mandato.
    - Una liquidación `PAGADA` es inmutable.
    - Cuando se resuelve el motivo, una liquidación `RETENIDA` vuelve a `BORRADOR`.

**Datos de pago del profe**
12. Llave Bre-B, tipo y número de documento, y **nombre del titular**. La llave debe estar **a
    nombre del profe**. Al registrar el pago, el admin confirma que el nombre que mostró su banco
    coincide con el titular.
13. Estos datos solo los ve completos el admin. El profe los ve enmascarados. Cualquier cambio le
    envía un correo al profe («Cambiaron tus datos de pago…») como protección contra fraude.

**Reportes**
14. Al pagar, el profe recibe la notificación `PAYOUT_PAID`, que ya existe, más un correo con su
    comprobante.
15. **Certificado anual** por profe y año: lo recibido en su nombre, la comisión cobrada, lo
    entregado y las retenciones practicadas (hoy $0). El sistema genera el borrador; lo firma un
    contador público. El admin sube el PDF firmado y el profe lo descarga.

---

## Paso 0 · Inventario (sin código)

Antes de escribir código, documenta en la sección **Hallazgos**, al final de este brief, cómo
funciona hoy:

- El modelo actual de liquidación y pago al profe: tablas, estados, pantallas admin de pagos,
  `/ganancias`, `PAYOUT_PAID` y `EarningsAndPayoutsIT`.
- El ciclo de vida de la reserva: qué estado significa «dictada y cerrada».
- La regla y el ajuste del plazo de reclamo, y cómo se sabe si hay un reclamo abierto.
- Cómo se registran hoy la devolución y el saldo a favor.
- El cálculo de días hábiles y festivos.
- Cómo se versionan y se aceptan los textos legales.
- Cómo se guardan los documentos privados (URL firmada).

Propón cómo encaja este brief **extendiendo** lo que existe, sin duplicar modelos. Si alguna regla
choca con el comportamiento actual, o si el cambio implica migrar liquidaciones ya hechas,
**detente y consúltalo con Pardo**. Este paso termina con un commit que solo toca el brief.

## Paso 1 · Mandato en los términos del profe

- Agrega la cláusula del **Anexo A** a los términos del profe como una nueva versión.
- Los profes existentes deben aceptarla. Usa el mecanismo de aceptación que exista. Si no hay
  versionado con aceptación, detente y repórtalo.
- Guarda la fecha de aceptación y la versión aceptada, porque son la prueba del mandato.
- Mientras un profe no la acepte, sus liquidaciones quedan `RETENIDA` con el motivo «Falta aceptar
  los términos».
- **Pruebas:** un IT de la aceptación y de la retención por no aceptarla.

## Paso 2 · Datos de pago del profe

**Migración:** `professor_payout_details`, uno por profe, con estos campos:
- Tipo de llave Bre-B (celular, cédula, correo o alfanumérica).
- La llave.
- El tipo y el número de documento.
- El nombre del titular.
- `updated_at`.

Valida el formato de la llave según su tipo.

**Profe:**
- En una sección **privada** del perfil: formulario, vista enmascarada y el texto «La llave debe
  estar a tu nombre. Solo el equipo de Orión ve estos datos completos.»
- Si le faltan los datos, `/ganancias` muestra un aviso con un enlace al formulario. Es el único
  aviso nuevo de este brief.

**Admin:** ve los datos completos solo en el flujo de pago de una liquidación.

**Correo** al profe en cada cambio de sus datos de pago.

**Pruebas:**
- Validaciones de la llave.
- Enmascaramiento.
- Que solo el admin vea los datos completos, y que otro profe o un estudiante reciban 403/404.
- El correo en cada cambio.

## Paso 3 · Motor de liquidación

**Lógica pura:** `PayoutCalculator` (sin Spring, con el «ahora» por parámetro):
- Recibe las reservas candidatas, los ajustes pendientes y el instante del corte.
- Devuelve las líneas y el total.
- Hace el cálculo de cortes: dado un instante, cuál es la quincena y cuándo es el próximo corte.
- Hace el cálculo de la fecha de pago comprometida, el 3.er día hábil.

**Migraciones:**
- `payouts`:
  - Profe, período (inicio y fin) e instante del corte.
  - Estado y motivo de retención.
  - Total bruto, total de comisión, total de ajustes y total neto.
  - Fecha de pago comprometida.
  - `approved_at`, `paid_at`, referencia de la transferencia, llave usada y titular verificado.
- `payout_lines`:
  - La liquidación, la reserva (nula en los ajustes) y el tipo (`CLASE`, `AJUSTE_DEVOLUCION`,
    `ARRASTRE`).
  - Bruto, `commission_rate_bps`, comisión y neto.
  - Un texto descriptivo.
- **Invariantes en la base:**
  - Único `(professor_id, period_start)`.
  - Único parcial: una reserva solo puede estar en una línea `CLASE`.
  - CHECK de estados.
  - CHECK de que el neto sea igual al bruto menos la comisión en las líneas `CLASE`.

**Trabajo programado de corte:**
- Corre en cada corte y crea los borradores.
- Es idempotente: si corre dos veces, el índice único lo impide.
- Usa `TransactionTemplate` y lo vigila `JobWatchdog`.
- El admin puede **regenerar** un `BORRADOR` si entretanto se cerró un reclamo, pero nunca una
  liquidación aprobada o pagada.

**Ajustes por devolución:** cuando se aprueba una devolución o un reclamo sobre una reserva que ya
está en una liquidación `PAGADA`, se registra un ajuste pendiente que entra en el siguiente corte.
Engánchalo al evento que ya exista.

**Pruebas:**
- Unitarias del calculador:
  - Clases dentro y fuera del plazo de reclamo.
  - Reclamo abierto.
  - Saldo a favor.
  - Prueba de $0.
  - Comisión de fundador.
  - Arrastre negativo.
  - Cortes en fin de mes (febrero y meses de 30 y 31 días).
  - Días hábiles con festivo.
- IT del corte completo con el reloj movido, incluido que no se dupliquen las liquidaciones.
- `EarningsAndPayoutsIT` sigue en verde, con la comisión esperada calculada con el ajuste vigente.

## Paso 4 · Flujo del admin

**`/admin/pagos`**, o la pantalla que corresponda según el inventario. Una vista por quincena:
- Cada profe con su estado, su neto y la fecha comprometida.
- El total a transferir.
- Las liquidaciones retenidas, con su motivo.

**Detalle de una liquidación:**
- Sus líneas.
- Botón **Aprobar**.
- Botón **Registrar pago**, que pide:
  - Fecha.
  - Referencia de la transferencia Bre-B.
  - Casilla obligatoria: «Confirmo que el nombre que mostró mi banco coincide con el titular».
- La llave se muestra completa **solo aquí**, con un botón para copiarla.

**Al registrar el pago:**
- La liquidación pasa a `PAGADA`.
- Se envía `PAYOUT_PAID` (campana y push).
- Se envía el correo con el comprobante.

Si existe un registro de auditoría del admin, cada aprobación y cada pago quedan anotados.

**Pruebas:**
- IT de todo el flujo.
- Que no se pueda pagar una liquidación sin aprobar, retenida o sin la casilla.
- Que una liquidación pagada no se pueda modificar.
- Un e2e: el admin aprueba y registra un pago, y el profe ve el comprobante.

## Paso 5 · Lo que ve el profe

**`/ganancias`**, extendiendo lo que existe:
- **Próximo corte** y **fecha estimada de pago**.
- **Clases por liquidar**, con el motivo en lenguaje llano:
  - «En plazo de reclamo hasta el 18 de octubre».
  - «Tiene un reclamo abierto».
  - «Entra en el corte del 1 de noviembre».
- **Historial de liquidaciones** con su estado.
- Una explicación corta y fija:

  > «Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la
  > comisión.»

**Comprobante de cada liquidación:**
- Una página imprimible con estilos de impresión, para guardarla como PDF desde el navegador. No
  agregues una librería de PDF.
- Contenido:
  - Los datos del mandatario: Pardo, con su cédula o NIT según su RUT, desde un ajuste.
  - Los datos del profe.
  - El período.
  - Las líneas, los ajustes y los totales.
  - La fecha, la referencia del pago y la llave enmascarada.
- El mismo contenido va en el correo de pago.

**Pruebas:** vitest de los textos de estado y un e2e del comprobante.

## Paso 6 · Reportes contables y certificado anual

**Ajustes nuevos** en `platform_settings` (validados en `SettingDefinition` y editables en Ajustes):
- Nombre y documento del mandatario.
- `uvt_cop`, con valor inicial 52374.

**Exportaciones del admin** (CSV, UTF-8 con BOM para que abra bien en Excel):
1. **Libro de mandato por rango de fechas.** Una fila por reserva con pago aprobado:
   - La fecha del pago y la referencia de Wompi.
   - El estudiante.
   - El profe (documento y nombre).
   - Bruto, comisión y neto.
   - La liquidación en la que quedó incluida y su fecha de pago.
2. **Resumen anual por profe.** Lo recibido en su nombre, la comisión, lo entregado y lo pendiente al
   31 de diciembre. Sirve para el certificado y para la información exógena.
3. **Comisiones por mes.** Es el ingreso propio de Orión.

**Indicador en el panel admin**, para el año en curso:
- Comisiones acumuladas y recaudo total acumulado.
- El recaudo total expresado también en UVT, junto a la referencia de **3.500 UVT**.
- El texto: «El recaudo pasa por tu cuenta bancaria: súmalo a tus otras consignaciones del año».

**Certificado anual:**
- Por profe y año, una página imprimible titulada «Certificado de ingresos recibidos para
  terceros», con:
  - El mandatario y el mandante.
  - El año.
  - Lo recibido en su nombre, la comisión cobrada, lo entregado y las retenciones practicadas ($0).
  - Espacio para el nombre, la tarjeta profesional y la firma del contador público.
- El admin sube el PDF firmado. Se guarda privado con URL firmada, como los documentos que ya
  existen.
- El profe lo descarga desde `/ganancias` solo cuando ya fue subido. Mientras no exista, no se
  muestra nada, para no dejar UI muerta.

**Pruebas:**
- IT de las exportaciones: los totales cuadran con las liquidaciones, y lo pendiente al cierre del
  año aparece como pendiente.
- IT de los permisos del certificado: solo el profe dueño y el admin pueden verlo.

---

## Fuera de alcance

- Transferencias automáticas, sea por Wompi Pagos a Terceros o por una API bancaria.
- IVA sobre la comisión y facturación electrónica. Si Pardo pasa a ser responsable de IVA, irá en
  otro brief.
- Retenciones en la fuente. Hoy Orión no es agente retenedor.
- Conciliación automática contra los reportes de Wompi. Pardo concilia a mano con el libro de
  mandato.

## Al terminar

- Actualiza `docs/ESTADO.md` con el modelo de mandato, las quincenas y los estados de liquidación.
- Agrega a la lista de verificación del paso 11 estos flujos:
  - Aceptar el mandato.
  - Registrar los datos de pago.
  - El corte.
  - Aprobar y pagar.
  - El comprobante.
  - Un ajuste por devolución.
  - Las exportaciones.
  - El certificado.
- Deja anotado para Pardo que el manual técnico necesita esta sección.

---

## Anexo A · Cláusula de mandato (texto aprobado por Pardo)

> **Mandato de recaudo.** Al aceptar estos términos, le encargas a Orión (Diego Pardo) recibir en tu
> nombre el valor que pagan tus estudiantes por tus clases. Ese dinero es tuyo: Orión lo recibe por
> tu cuenta y te lo entrega descontando la comisión de Orión, más el IVA sobre esa comisión cuando
> la ley lo exija. Orión no es tu empleador: tú fijas tu precio, tus horarios y dónde más enseñas.
>
> **Liquidación.** Cada quincena, con cortes el día 15 y el último día de cada mes, liquidamos las
> clases que dictaste cuyo plazo de reclamo ya venció sin reclamos abiertos. Te pagamos a más tardar
> el tercer día hábil después del corte, por transferencia Bre-B a la llave registrada a tu nombre.
> Si después de pagada una clase se resuelve un reclamo o una devolución a favor del estudiante, ese
> valor se descuenta de tu siguiente liquidación.
>
> **Certificado.** Cada año te entregamos un certificado con lo recibido en tu nombre, la comisión
> cobrada y lo que te entregamos, para tu declaración de renta. Declarar tus ingresos es tu
> responsabilidad.

---

## Hallazgos

*(Paso 0, 25/09/2026. Inventario del código en `master` después de `e31a531`.)*

### Cómo funciona hoy

**Liquidación (V16).**
- `payouts`: `professor_id`, `period_start`/`period_end` (DATE), `amount_cop`, `status` en `PENDING`,
  `PAID` o `CANCELLED` (nadie escribe `CANCELLED`), `reference` y `paid_at`.
- `payout_items(payout_id, payment_id UNIQUE)`: es la garantía de que una clase no se paga dos veces.
- Ninguna migración posterior las toca.
- `PayoutService.generate(desde, hasta)` es manual, con las fechas que escriba el admin, y toma los
  pagos `RELEASED` por `released_at`. `markPaid` exige una referencia y publica `PayoutPaidEvent`.
- La auditoría del admin (`admin_audit_log`) no registra nada de pagos.

**Pantallas.**
- `/admin/pagos` tiene dos pestañas:
  - «Conciliación»: pagos, con el botón de abonar saldo.
  - «Liquidaciones»: generar por rango de fechas, CSV, referencia y «Marcar transferida».
- `/ganancias` muestra cuatro cifras (Retenido, Por cobrar, En camino, Transferido) y la lista por
  clase. Esa lista filtra por `created_at` del pago; la liquidación, por `released_at`.

**`PAYOUT_PAID`.** Ya existe en la campana, el push y el correo (`AvisosDeCuenta`,
`AvisosEnElDispositivo`, `CorreosDeAviso`).

**`EarningsAndPayoutsIT`** prueba este recorrido: retenido, liberado al registrar asistencia,
liquidación `PENDING`, «En camino», `PAID` con referencia, CSV, «Transferido» y un único `PAYOUT_PAID`.

**Comisión.** `commission_rate_bps` vive en **`payments`**, no en `bookings`, congelada al cobrar
por `CommissionPolicy`, con la de fundador incluida. El brief se aplica sobre esa columna.

**Ciclo de vida.**
- «Dictada y cerrada» = `BookingStatus.isClosedAsHeld()`, es decir, `COMPLETED` o `NO_SHOW_STUDENT`.
- El dinero pasa a `RELEASED` por cuatro caminos:
  - (a) el profe registra asistencia, y es inmediato;
  - (b) el cierre automático, 24 h después del fin (`auto_complete_hours`), salvo con un reclamo
    abierto;
  - (c) **una cancelación tardía del estudiante**, dentro de `student_cancel_hours`: el dinero va
    al profe y la reserva queda `CANCELLED_BY_STUDENT`;
  - (d) un reclamo resuelto a favor del profe o descartado.

**Reclamos.**
- El plazo es `dispute_report_window_hours` (24 h después del fin); lo evalúa
  `DisputeService.requireWithinReportWindow`.
- Un reclamo está abierto si su estado es `OPEN` o `UNDER_REVIEW`: índice único parcial por reserva,
  `DisputeRepository.findBookingIdsWithOpenDispute()`.
- Resuelto a favor del estudiante, el pago pasa a `REFUNDED` y el estudiante recibe **saldo a
  favor**, nunca una devolución al medio de pago. Evento: `DisputeResolved`.
- **Solo una reserva `CONFIRMED` admite un reclamo**: en cuanto el profe registra asistencia, el
  estudiante ya no puede reclamar, aunque siga dentro de las 24 h.

**Devoluciones.**
- Retracto: `refund_requests` (`RETRACTO`/`ADMIN`), el pago pasa a `REFUND_PENDING` y luego a
  `REFUNDED`. Solo antes de la clase. No publica ningún evento: `RetractionMailer` manda el correo
  directo.
- `Payment.refund()` no admite un pago `RELEASED`.

**Saldo a favor.** `student_credits` (motivo, vencimiento) y `payment_credit_applications`. Una clase
pagada con saldo tiene su fila en `payments` como cualquier otra, con `charged_cop` = 0.

**Días hábiles.** `PlazoLegal` es pura y cuenta **de lunes a viernes, sin festivos, a propósito**: su
javadoc explica que así la fecha llega antes que la legal, nunca después. **No existe ningún
calendario de festivos** en el código.

**Textos legales.**
- `legal_documents` guarda solo `TERMS` y `PRIVACY`, con versión y vigencia, y el texto con
  marcadores `{{…}}` que salen de Ajustes.
- Las aceptaciones van en `agreement_acceptances(user_id, document_code, version, accepted_at, ip,
  user_agent)`.
- `LegalDocumentService.pendientes()` existe pero **nadie lo llama**: publicar una versión nueva no
  le pide a nadie aceptarla, aunque la cláusula 14 lo promete.
- **No hay «términos del profe» como documento.** El «Acuerdo del profesor» (`TEACHER_AGREEMENT`,
  versión `"1.0"` fija en `TeacherApplicationService`) es texto fijo en `/aplicacion`, se acepta al
  postular y no habla de mandato.

**Documentos privados.** `DocumentStorage`/`CloudinaryDocumentStorage` (`type=authenticated`) con URL
firmada de 5 min; el admin la pide por `AdminTeacherDocumentsController`, y cada lectura queda
auditada (`VIEW_DOCUMENT`).

**Infraestructura.**
- Ajustes: `SettingDefinition` con tipos `ENTERO`, `BOOLEANO`, `OPCION` y `ENLACE`. **No hay tipo
  texto.** Un ajuste nuevo necesita su entrada en el enum y su fila en una migración.
- Trabajos programados: el patrón de `LessonAutoCompleteJob`, con `JobRunRegistry` (en memoria) y
  `JobWatchdog`.
- Correo: `MailTransport` + `EmailLayout`.
- Panel del admin: `DashboardResponse` (`GET /admin/dashboard`).

### Cómo encaja, extendiendo lo que existe

- **Una sola liquidación.** `payouts` se extiende (V72): corte, motivo de retención, bruto, comisión,
  ajustes y neto (`amount_cop` sigue siendo el neto), fecha comprometida, aprobación, pago, llave
  usada y titular verificado. Los estados pasan a `DRAFT`, `APPROVED`, `PAID` y `ON_HOLD`: los
  identificadores en inglés, como el resto del código, y en pantalla «Borrador», «Aprobada»,
  «Pagada» y «Retenida».
- **Líneas.** `payout_items` se reemplaza por `payout_lines` (`CLASS`, `REFUND_ADJUSTMENT`,
  `CARRY_OVER`), con los datos de las que ya existan. El único parcial de `booking_id` en las líneas
  `CLASS` hereda la garantía de hoy.
- **Ajustes pendientes.** `payout_adjustments`: lo que se descuenta en el próximo corte.
- **Qué se liquida.** Lo decide `PayoutCalculator`, puro. Liquidable = pago `RELEASED` + reserva
  cerrada + fin + `dispute_report_window_hours` ≤ corte + sin reclamo abierto + neto > 0. Así el
  brief no cambia cuándo se libera el dinero: agrega la espera del plazo antes de liquidar.
- **Pantallas.**
  - El admin: la pestaña «Liquidaciones» de `/admin/pagos` pasa a ser la vista por quincena; se va
    el rango libre de fechas.
  - El profe: `/ganancias` conserva sus cifras y suma el próximo corte, las clases por liquidar con
    su motivo y el historial.
- **Datos, certificado y auditoría.**
  - Los datos de pago del profe, en `professor_payout_details`.
  - El certificado firmado, con `DocumentStorage`, como los documentos de la postulación.
  - Aprobar, pagar, regenerar y ver la llave completa quedan en `admin_audit_log`.
- **Ajustes.** Un tipo `TEXTO` en `SettingDefinition` para el nombre y el documento del mandatario.
  Hoy esos datos están en las variables `ORION_LEGAL_NOMBRE` y `ORION_LEGAL_DOCUMENTO`, pero esas no
  se editan desde Ajustes.
- **Ajuste por devolución.** Se engancha a `DisputeResolved` a favor del estudiante y a la
  confirmación de una devolución. Con las reglas de hoy **no se dispara nunca** para una clase ya
  liquidada: no admite reclamo después de cerrada, el retracto es antes de la clase y un pago
  `RELEASED` no se puede devolver. Queda listo y probado por si esas reglas cambian. El arrastre
  (regla 10) sí funciona desde el primer día.

### Choques con el comportamiento actual (para Pardo)

1. **Liquidaciones ya hechas.** Cambiar los estados y las líneas obliga a migrar lo que exista en
   producción. Desde aquí no se ve si hay liquidaciones reales.
2. **Dónde va el mandato.** No existen unos «términos del profe» ni un flujo para aceptar una
   versión nueva: hay que construir ese flujo (paso 1) y decidir en qué documento va la cláusula.
3. **Festivos.** El brief pide «el cálculo de días hábiles con festivos que ya existe»: no existe, y
   el de hoy no cuenta festivos a propósito.
4. **Cancelaciones tardías del estudiante.** Hoy su dinero se libera al profe, pero la reserva no
   queda «dictada y cerrada», así que la regla 5 la dejaría fuera de toda liquidación.
5. **Nota, no bloquea:** el estudiante pierde el derecho a reclamar en cuanto el profe registra
   asistencia, aunque esté dentro de las 24 h. El brief no lo cambia (la liquidación espera el plazo
   igual), pero la cláusula de mandato habla de «clases cuyo plazo de reclamo ya venció».

Las respuestas de Pardo van abajo, en «Decisiones».

### Decisiones (Pardo, 25/09/2026, noche)

1. **Las liquidaciones de hoy se borran.** Solo hubo pruebas: la migración elimina las que existan
   (`payouts` y `payout_items`) y el modelo nuevo empieza de cero con el primer corte.
2. **El mandato va en el «Acuerdo del profesor» v2.** El acuerdo deja de ser texto fijo en
   `/aplicacion` y pasa a `legal_documents` como documento con versión (`TEACHER_AGREEMENT`): la
   1.0 con el texto de hoy y la 2.0 con la cláusula del Anexo A. Los profes actuales la aceptan en
   una ventana al entrar y los nuevos al postular. Sin la 2.0 aceptada, sus liquidaciones quedan
   `ON_HOLD` con el motivo «Falta aceptar los términos».
3. **Festivos solo para las liquidaciones.** Calendario de festivos de Colombia (Ley 51 de 1983 y
   Semana Santa), como clase pura con sus pruebas, para la fecha de pago comprometida. `PlazoLegal`
   (retracto, habeas data, soporte) sigue sin festivos.
4. **La cancelación tardía del estudiante entra en la liquidación**, como línea propia con el texto
   «Cancelación tardía del estudiante», con su comisión como cualquier clase: la política de
   cancelación ya le da ese dinero al profe.
