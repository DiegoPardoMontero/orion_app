# Orión — Deuda, ideas y notas de despliegue

> Lo que no cabe en otro sitio: deuda técnica conocida, ideas que surgieron trabajando y las
> notas de operación. **Qué hay construido está en [`ESTADO.md`](./ESTADO.md)**; el alcance de
> cada tarea, en [`briefs/`](./briefs).

---

## Deuda técnica conocida

### Calidad / tests
- **La suite levanta un Postgres por contexto de Spring** (~30 contenedores), porque cada test con
  su propio `@TestConfiguration` estrena contexto. Se probó a compartir uno solo: ahorra memoria,
  pero pone a las 46 clases a compartir base y una que no limpia rompe a otra que no tiene que ver
  con ella. Se revirtió a propósito — el porqué está escrito en `TestcontainersConfiguration`. Si
  alguna vez hace falta de verdad, el camino bueno es un contenedor y **una base por contexto**, no
  una base compartida.
- **Una suite interrumpida deja sus contenedores en pie**, y se acumulan entre corrida y corrida
  hasta que la máquina se queda sin memoria y los siguientes intentos mueren por una razón que no
  tiene nada que ver con el código. Antes de dar por malo un fallo raro:
  `docker rm -f $(docker ps -aq --filter "label=org.testcontainers=true")`.
- **La suite e2e muta estado compartido**: exige `docker compose down -v` entre corridas completas.
  *Mejora:* aislar cada test (usuarios/reservas propios con sufijo aleatorio) o un reset por test
  vía endpoint de test-only. Hoy es "una corrida por semilla".
- **Falta e2e del happy-path completo de recuperación** (con token real desde Mailpit) y de
  **subir foto** (bloqueado por Cloudinary). El backend sí los cubre con ITs.
- **Lighthouse de la landing** (DoD del minibrief: ≥95 en Perf/SEO/A11y) no se ha corrido — hazlo
  desde Chrome DevTools sobre la URL de producción.
- **Tests de componentes frontend**: hay **Vitest** con 50 tests de lógica pura (`lib/phone`,
  `fuerzaClave`, formato de fechas y horas, límites de la ficha del profesor). Falta cobertura de
  *componentes* con render (Testing Library) — p. ej. `PhoneInput`, `CambiarFoto`, los modales.
- **La suite e2e no se ha corrido desde la revisión del portal del estudiante (08/09/2026).** Se
  revisaron a mano los selectores que se tocaron (`Cancelar`, `¿Cancelar esta clase?`,
  `Sí, cancelar`, `Confirmar reserva`) y siguen coincidiendo, pero eso es leer, no ejecutar — y los
  cambios tocaron justo la pantalla de reservar y la de mis clases, que es lo que esa suite recorre.
  **Es lo primero que hay que correr.**

### Sin verificar contra el servicio real
- **La URL firmada de los documentos de Cloudinary.** El 401 del admin al abrir un PDF se arregló
  el 08/09 usando el endpoint de descarga privada, y la construcción está fijada con tests
  unitarios — pero **nunca ha hecho una llamada real**: el secreto solo vive en Railway. Si sigue
  fallando, el siguiente sospechoso es el ajuste *PDF and ZIP files delivery* en Settings →
  Security de Cloudinary, que viene desactivado por defecto y bloquea la entrega de PDF.
- **El polling de la pantalla de retorno de Wompi** está implementado y consulta cada 3 s mientras
  el pago siga pendiente. Si en producción no ocurre, la hipótesis es `ORION_APP_BASE_URL` mal
  puesta en Railway — hipótesis **sin confirmar**, porque desde aquí no se ve ese panel.
- **La triplicación del historial de pagos.** Se filtraron los intentos anulados, pero la captura
  original no llegó a verse: si lo que se vio eran pagos genuinamente `PENDING`, esos **siguen
  apareciendo** hasta que el job de expiración los anule. Nadie ha comprobado que ese job corra en
  producción.

### Riesgos de operación
- **`meet.jit.si` pide no usarse con fines comerciales** y topa el uso agregado en unos 25 usuarios
  activos al mes. Orión da clases cobradas encima de ese servicio. Es la dependencia más frágil que
  tiene el producto hoy; las salidas y su costo están en
  [`videollamada-opciones.md`](./videollamada-opciones.md).
- **El estudiante puede ser anfitrión de la videollamada.** Mitigado (sala de 32 caracteres,
  antesala, sin invitar) pero no resuelto: el control de moderador exige un token firmado, que es
  de pago.
- **Las sanciones siguen en modo observación** (`sanctions_mode = OBSERVE`). La escalera se propone
  y la confirma una persona; la suspensión automática que pide producto **no ocurre sola**.

### Accesibilidad
- Repaso formal con lector de pantalla y teclado de las pantallas nuevas (registro, /cuenta,
  reprogramar, recuperar). Los tokens ya garantizan foco visible y contraste AA, pero conviene
  auditar `aria-live` en toasts/errores y el orden de tabulación en los modales.

### UX / producto
- **Animación de deleite** de la confirmación de reserva (check dibujado + estrellas) del handoff
  v2 no se implementó; hoy usamos el banner de Rigel celebrando (suficiente, pero menos "wow").
- **Vista de reserva móvil**: la barra de confirmación no es sticky (es in-flow) para no chocar con
  el tab bar; se puede pulir con un sticky por encima del tab bar.
- **Estados de error de red** globales: hoy cada pantalla maneja el suyo; un interceptor de
  "sin conexión" global daría consistencia.

### Backend / arquitectura
- `professor_profiles.photo_url` quedó **deprecada** (se lee de `users.photo_url`): eliminarla en
  una migración futura cuando estemos seguros.
- **Código muerto en `BookingService.cancel`**: `isAdmin` y `window` quedaron sin uso al quitar el
  bloqueo por ventana, y `lateCancellationMessage` puede haberse quedado sin llamadores.
- **El cielo de logros quedó en 19 estrellas.** «Cara a cara» pedía una clase presencial y se retiró
  con la V30; AMPLITUD tiene dos. Falta decidir si se diseña un reemplazo.
- **Rate limiting** del endpoint de fotos: el brief pedía uno ligero; hoy solo validamos
  tipo/tamaño. Añadir un throttle por usuario (p. ej. en memoria o con la caché) para proteger la
  cuota de Cloudinary.
- **Índices**: revisar que `bookings` tenga índices para las consultas de "mis clases" (por
  student_id/professor_id + starts_at). A escala de decenas está bien; documentarlo.

---

## Ideas para futuros briefs

Propuestas para futuros briefs, alineadas con el negocio (confianza al hablar, adultos, curaduría
de academia). Las tachadas ya se hicieron.

1. **Confidence Score® de verdad** (MVP 2). Ya lo teaseamos en la landing. Un score simple por
   estudiante que sube con clases completadas + una micro-encuesta post-clase ("¿qué tan cómodo te
   sentiste hablando hoy?"). Es el gancho de retención y el diferenciador real.
2. **Recordatorio automático 24 h / 1 h antes** por correo (y opcional WhatsApp). El backend de
   notificaciones ya existe; es añadir un job programado que dispare `BookingReminderEvent`.
3. ~~**"Reservar de nuevo con este profesor"**~~ — **hecho** (03/09/2026), en el panel de progreso
   del estudiante: la lista "Con quién has practicado" lleva directo a su agenda.
4. ~~**Racha de clases**~~ — **hecha** (03/09/2026): racha actual, mejor racha y mapa del último año
   en el panel del estudiante, con Rigel cambiando de pose según el estado.
5. **Página pública del profesor** (`/p/<slug>`) para que Sofía comparta perfiles en redes — SEO
   + funnel, reutiliza el detalle que ya existe.
6. **Onboarding del estudiante** de 2 pasos tras el registro (nivel aproximado + objetivo) para
   pre-filtrar profesores y personalizar el saludo de Rigel. **Aprobado por producto (03/09/2026),
   sin construir**: las decisiones de arquitectura están en un documento aparte. La primera es que
   hoy un estudiante no tiene ficha propia.
7. **Modo "sin cámara"** / preferencias de clase en el perfil, para bajar la ansiedad de hablar
   (coherente con la misión).
8. **Métricas para Sofía** en el panel admin: tasa de recompra, no-shows por profesor, cupos
   desaprovechados. Datos para decidir a quién invitar.
9. **Bloque de disponibilidad recurrente "copiar semana"** para profesores — hoy se cargan franjas
   una a una.
10. **i18n-ready**: el copy está en español hardcodeado; extraerlo facilitaría una versión en
    inglés para el mercado que "aprende inglés en inglés".
11. **Un logro que reemplace a «Cara a cara»** en la familia AMPLITUD, que se quedó con dos al
    desaparecer las clases presenciales.
12. **Prórroga de los plazos de habeas data.** La ley permite ampliar 5 días una consulta y 8 un
    reclamo, avisando y motivando. Hoy no hay botón: si hace falta, se responde a mano dentro del
    plazo original.

---

## Notas de operación y despliegue

> **La lista de variables de entorno vive en [`../README.md`](../README.md#5-variables-de-entorno)**,
> con cuáles son obligatorias en producción y qué pasa si faltan. Aquí solo lo que no es una
> variable.

- **Producción no corre el `DevDataSeeder`** (perfil `local`): la base arranca solo con el
  administrador. Para el piloto: admin crea profesor → profesor publica perfil + disponibilidad →
  estudiante se registra y reserva.
- **Flyway va por V31.** Cada cambio de esquema es una migración nueva; nunca editar una aplicada.
  Las dos últimas mueven datos, no solo esquema: la **V30** reescribe las reservas presenciales como
  virtuales y estrecha el CHECK a un solo valor, y la **V31** añade el tipo de falta del profesor.
- **Configurar el webhook en el panel de Wompi** apuntando al **dominio público del frontend**
  —`https://<dominio>/api/v1/webhooks/payments/wompi`—, no al del backend: en Railway el backend
  vive en la red interna y Wompi no lo alcanza. Sin esto ningún pago confirma una clase.
- **Desarrollo local necesita claves de SANDBOX de Wompi** (prefijo `_test_`) o no se puede
  reservar. La excepción es Ana, que arranca con saldo a favor y por eso puede reservar sin pasarela.
- **La suite de humo asume semilla fresca** por corrida completa (`docker compose down -v`).
- **Rotar la llave privada de Wompi**: viajó por chat. Nunca estuvo en el código ni se usa en este
  flujo —el checkout se firma con el secreto de integridad y el webhook se verifica con el de
  eventos—, pero un secreto de producción que no se usa es superficie de ataque gratis.
- **Cómo comprobar un despliegue desde fuera**, sin entrar a Railway:

  ```bash
  curl -s https://orionidiomas.com/actuator/health     # {"status":"UP"} → arrancó, y Flyway pasó
  curl -so /dev/null -w '%{http_code}\n' \
       https://orionidiomas.com/api/v1/auth/me          # 401 en JSON → el backend responde
  ```

  Que el backend arranque **es** la prueba de que las migraciones corrieron: con Flyway roto o con
  una variable obligatoria ausente, no levanta.
