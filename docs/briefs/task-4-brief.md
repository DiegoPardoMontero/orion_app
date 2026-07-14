# Brief · Tarea 4 — Frontend PWA y panel de administración

**Prerequisito:** Tareas 1–3 completas (DoD en verde). Adendum de Spring Boot 4.1.x vigente para los pasos de backend de esta tarea.
**Resultado de esta tarea:** la plataforma completa usable en el navegador — estudiantes exploran profesores y reservan, ambos roles gestionan sus clases, los profesores administran disponibilidad y perfil, y Pardo administra usuarios y reservas — como PWA instalable, con suite E2E de humo. El despliegue a URL pública es la Tarea 5.

---

## 0. Modo de trabajo — ajustado para frontend

Rigen las reglas del `CLAUDE.md`, con estos ajustes:

1. **La verificación de cada paso es visual, en el navegador**, con la checklist que trae cada paso. Pardo es fuerte en backend y débil en frontend: explica los conceptos de React/Next que uses (qué es un client component, qué hace un hook, por qué TanStack Query cachea e invalida) con más detalle del habitual.
2. **Los wireframes acordados son el contrato visual** (están en `docs/`): respeta su estructura y jerarquía. No busques pixel-perfect; busca que cada pantalla tenga los mismos elementos en el mismo orden.
3. **Sesgo de entrenamiento, segunda ronda:** usa la **última estable de Next.js (App Router) y Tailwind v4**. Advertencias concretas: en Tailwind v4 la configuración vive en CSS con `@theme` (no existe `tailwind.config.js` por defecto — no generes uno estilo v3); en Next.js todo va por App Router (`app/`), nada de `pages/`. Si algo no compila, consulta la documentación oficial vigente. **Nunca degrades versiones para "resolver" un error.**
4. Un paso a la vez, DETENTE al final de cada uno, un commit por paso, cero features futuras, y los pasos con backend mantienen el estándar de tests de las tareas anteriores.

---

## 1. Decisiones de arquitectura frontend — LEER ANTES DE CODIFICAR

1. **Same-origin vía proxy de Next.** El navegador solo habla con `:3000`; Next reescribe `/api/*` hacia el backend. Las cookies (`ORION_SESSION`, `XSRF-TOKEN`) fluyen sin fricción porque para el navegador existe un solo origen — sin CORS, sin preflights, sin dolores de `SameSite`. (La config CORS de la Tarea 1 queda como respaldo; con el proxy no se ejercita.)
2. **Todas las pantallas son client components con fetching en el cliente** (TanStack Query). Nada de data fetching en server components: esta app es un dashboard tras login sin necesidades de SEO, y el fetching cliente evita el reenvío de cookies en SSR — complejidad que no compra nada aquí. Explicárselo a Pardo cuando aparezca la primera pantalla.
3. **Un único `apiFetch`** (`lib/api/fetch.ts`): serializa JSON, lee la cookie `XSRF-TOKEN` y la envía como header `X-XSRF-TOKEN` en POST/PUT/PATCH/DELETE, parsea errores `{error}` del backend y los lanza tipados, y ante un `401` fuera de `/login` redirige a `/login`. Ningún componente llama `fetch` directo.
4. **Tipos generados del contrato real:** script npm `types:api` que corre `openapi-typescript` contra `http://localhost:8080/v3/api-docs` y genera `src/lib/api/schema.d.ts` (se commitea). El frontend no puede "recordar mal" un DTO: los tipos salen del backend vivo.
5. **Fechas y horas:** siempre `Intl.DateTimeFormat` con `timeZone: 'America/Bogota'` y locale `es-CO`, centralizado en `lib/format.ts` (`fechaCorta`, `horaBogota`, `rangoHoras`). Nunca la zona del dispositivo, nunca librerías de fechas — los ISO del API + Intl bastan.
6. **El frontend no reimplementa reglas de negocio.** Pinta `canCancel`, muestra los mensajes de error del API tal cual (ya vienen claros y con la voz institucional), y refresca datos cuando el servidor dice que algo cambió.
7. **UI:** Tailwind v4 con tokens propios en `@theme` — primario azul profundo (Orión, la constelación), neutrales cálidos, success/warning. Mobile-first: las vistas de estudiante/profesor viven en un contenedor centrado tipo móvil (`max-w-md`) también en desktop; el panel admin sí usa ancho completo con tablas. Copy 100 % en español siguiendo la voz del manual de Sofía (sección 37): cercana, positiva, sin expresiones prohibidas — los estados vacíos motivan ("Aún no tienes clases — explora los profesores y reserva la primera"), nunca regañan.
8. **Dependencias permitidas:** `next`, `react`, `@tanstack/react-query`, Tailwind, `openapi-typescript` (dev) y Playwright (dev). Nada más sin preguntar: sin UI kits, sin librerías de formularios, sin librerías de fechas.
9. **PWA sin service worker:** manifest completo + iconos + theme-color = instalable. Un SW con caché es la fuente clásica de "veo una versión vieja y no sé por qué"; no lo necesitamos en el MVP.
10. **Rutas en español** (App Router):

```
app/
├── login/page.tsx
└── (app)/                      # shell autenticado: header, nav por rol, guard
    ├── layout.tsx
    ├── profesores/page.tsx             # STUDENT
    ├── profesores/[id]/page.tsx        # STUDENT — agenda y reserva
    ├── mis-clases/page.tsx             # STUDENT y PROFESSOR
    ├── disponibilidad/page.tsx         # PROFESSOR
    ├── perfil/page.tsx                 # PROFESSOR
    └── admin/
        ├── usuarios/page.tsx           # ADMIN
        └── reservas/page.tsx           # ADMIN
```

---

## 2. Paso 0 — Scaffold, proxy, `apiFetch` y shell

- Generar el proyecto en `frontend/` con `create-next-app` (TypeScript, ESLint, Tailwind, App Router, directorio `src/`).
- `next.config` con las reescrituras **exactas**:

```js
async rewrites() {
  const api = process.env.API_URL ?? 'http://localhost:8080';
  return [
    { source: '/api/:path*',      destination: `${api}/api/:path*` },
    { source: '/actuator/:path*', destination: `${api}/actuator/:path*` },
  ];
}
```

- Tokens de diseño en el CSS global con `@theme` (primario, neutrales, success, warning, radios).
- `lib/api/fetch.ts` según la decisión 3 (incluye el helper que lee la cookie `XSRF-TOKEN` del `document.cookie`).
- `lib/format.ts` según la decisión 5.
- `QueryClientProvider` en el layout raíz; shell mínimo con header "Orión".
- Script `types:api` en `package.json` y primera generación de tipos (backend arriba).
- Página de inicio provisional que consulta `/actuator/health` vía proxy y muestra "Backend: UP".

**Verificación (la ejecuta Pardo, backend y compose arriba):** `npm run dev` → `http://localhost:3000` muestra "Backend: UP" (prueba el proxy de punta a punta); `npm run types:api` regenera `schema.d.ts` sin errores.

**DETENTE aquí y espera confirmación.**

---

## 3. Paso 1 — Autenticación, guardas y navegación por rol

- `/login`: formulario email/contraseña (client component), errores del API mostrados tal cual, y al autenticar redirige por rol: `STUDENT → /profesores`, `PROFESSOR → /mis-clases`, `ADMIN → /admin/usuarios`.
- `AuthProvider` sobre TanStack Query que consulta `/api/v1/auth/me`; el layout de `(app)` es la guarda: sin sesión → `/login`; con sesión pinta el nav según rol (STUDENT: Profesores, Mis clases · PROFESSOR: Mis clases, Disponibilidad, Perfil · ADMIN: Usuarios, Reservas) + botón salir (POST logout con CSRF → `/login`).
- Guarda por rol: entrar a una ruta que no corresponde al rol redirige al home del rol (sin pantalla de error).

**Verificación en navegador:** entrar con los 5 usuarios semilla y comprobar nav y home de cada rol; refrescar mantiene la sesión; visitar `/admin/usuarios` como Ana redirige; logout regresa a login; una ruta protegida sin sesión redirige.

**DETENTE aquí y espera confirmación.**

---

## 4. Paso 2 — Flujo del estudiante: explorar y reservar

La pantalla que paga las cuentas. Contrato visual: wireframe "explorar y reservar".

- `/profesores`: tarjetas desde `GET /api/v1/professors` — avatar (foto o iniciales sobre fondo de color), nombre, headline, botón "Ver agenda". Estado vacío con voz de marca.
- `/profesores/[id]`: encabezado con perfil y bio (`GET /professors/{id}`); **una sola consulta** a `GET /professors/{id}/slots` (defaults: 7 días) y agrupación por fecha en el cliente → chips de día (los que tienen cupos) y chips de hora del día seleccionado; selector de modalidad (Virtual | Presencial); nota de lugar opcional (visible siempre, con placeholder distinto según modalidad); botón "Confirmar reserva" → `POST /api/v1/bookings` con el `startsAt` exacto del cupo.
- Éxito → banner de confirmación ("¡Clase reservada! Te enviamos la confirmación al correo") y redirección a `/mis-clases`. Error `422`/`409` → mostrar el mensaje del API e invalidar la query de cupos para refrescar la agenda.

**Verificación en navegador:** como Ana, reservar un cupo real de María → aparece el banner, el correo llega a Mailpit (`localhost:8025`) con .ics y link de Google Calendar; volver a la agenda → el cupo ya no está. Con dos pestañas, intentar reservar el mismo cupo en ambas → la segunda muestra el mensaje de conflicto y la agenda se refresca.

**DETENTE aquí y espera confirmación.**

---

## 5. Paso 3 — Mis clases (estudiante y profesor)

Contrato visual: wireframe "mis clases".

- Tabs Próximas | Pasadas (`GET /me/bookings?scope=`). Tarjeta: fecha y hora en Bogotá (`lib/format.ts`), chip de modalidad, nota de lugar si existe, contraparte con avatar y nombre.
- **Botón WhatsApp**: `https://wa.me/<phone>?text=<mensaje>` con mensaje precargado "Hola <contraparte>, soy <yo>. Te escribo por nuestra clase de Orión del <fecha> a las <hora>." (URL-encoded; teléfono normalizado a dígitos).
- **Cancelar** según `canCancel`: habilitado → modal de confirmación con motivo opcional → `POST /bookings/{id}/cancel` → invalidar queries. Deshabilitado y clase futura → el texto institucional bajo el botón, como el wireframe: "Faltan menos de 24 h — la clase se considera impartida".
- Vista del profesor: idéntica, con la contraparte estudiante, y en Pasadas: si la clase terminó en estado `CONFIRMED` → botón "Registrar asistencia" (modal asistió/no asistió + notas → `POST /bookings/{id}/attendance`); tras registrar, la tarjeta muestra el estado (`Completada` / `No asistió`).

**Verificación en navegador:** como Ana ver la clase con María, abrir el link de WhatsApp (se abre con el mensaje precargado), cancelar una clase a más de 24 h → desaparece de Próximas y el cupo reaparece en la agenda de María; como María, ver la clase de Ana; con una reserva pasada (insertada por psql), registrar asistencia y ver el estado cambiar.

**DETENTE aquí y espera confirmación.**

---

## 6. Paso 4 — Portal del profesor: disponibilidad y perfil

Contrato visual: wireframe "disponibilidad".

- `/disponibilidad`: lista de lunes a domingo; por día, chips de franjas existentes con eliminar (confirmación) y "+ añadir" (modal con selects de hora en punto, inicio y fin) → CRUD contra `/me/availability/rules`; los errores de solape del API se muestran tal cual. Sección "Fechas bloqueadas": lista de excepciones futuras con eliminar, y "Bloquear una fecha" (modal: fecha + switch "todo el día" que oculta o muestra los campos de hora) → `/me/availability/exceptions`.
- `/perfil`: formulario headline / bio / URL de foto (campo de texto en el MVP — sin upload) + toggle "Perfil visible" con aviso al desactivar ("Los estudiantes dejarán de verte y no podrán reservar contigo"). `PUT /me/profile`.

**Verificación en navegador:** como María, crear una franja nueva y verla convertida en cupos desde la cuenta de Ana; bloquear el próximo miércoles por la mañana → esos cupos desaparecen para Ana; despublicar el perfil → María desaparece del listado de Ana; republicar.

**DETENTE aquí y espera confirmación.**

---

## 7. Paso 5 — Admin: endpoints y panel

**Backend primero** (módulo `identity`/`scheduling` según corresponda, con el estándar de tests de siempre):

- `GET /api/v1/admin/users?role=&q=` — lista con filtro por rol y búsqueda por nombre/email.
- `POST /api/v1/admin/users` — `{email, fullName, whatsappPhone, role (STUDENT|PROFESSOR), password}`; email duplicado → `409`; si es PROFESSOR, crea su perfil vacío sin publicar.
- `PATCH /api/v1/admin/users/{id}` — `{fullName?, whatsappPhone?, status?}`. Sin cambio de rol ni de email en el MVP.
- `GET /api/v1/admin/bookings?from=&to=&professorId=&status=` — orden descendente por `starts_at`, tope de 200 filas (decisión consciente: sin paginación formal en el MVP).
- `GET /api/v1/admin/metrics` — `{ bookingsLast7Days, selfServicePctAllTime }`: reservas con `created_at` en los últimos 7 días, y % histórico de reservas con `created_by = student_id`.

Tests backend mínimos: cada endpoint con su rol (`403` a no-admin), email duplicado `409`, creación de profesor genera perfil, filtros de reservas, y las dos métricas con datos conocidos.

**Frontend:**

- `/admin/usuarios`: tabla (nombre, email, rol, estado, WhatsApp) con filtro y búsqueda; "Crear usuario" (modal con generador de contraseña legible — 3 palabras + número — y botón copiar, para que Pardo la comparta por WhatsApp); activar/inactivar en línea.
- `/admin/reservas`: strip superior con las dos métricas; filtros (rango de fechas, profesor, estado); tabla con fecha/hora, estudiante, profesor, modalidad, estado y columna "Autoservicio" (sí/no comparando `created_by`).

**Verificación en navegador:** crear un profesor real desde el panel, entrar con él en una ventana de incógnito y configurar su disponibilidad; inactivarlo desde el panel → ya no puede iniciar sesión; el strip de métricas refleja las reservas hechas durante las pruebas.

**DETENTE aquí y espera confirmación.**

---

## 8. Paso 6 — PWA, cambio de contraseña, estados y suite E2E

- **PWA:** `manifest.webmanifest` (name "Orión Language Academy", short_name "Orión", `display: standalone`, colores del tema, iconos 192 y 512 — placeholder generado está bien), metadata de viewport y theme-color. Sin service worker (decisión 9).
- **Cambio de contraseña:** backend `POST /api/v1/me/password` — `{currentPassword, newPassword}` (mínimo 8; actual incorrecta → `422`) con sus tests; frontend: opción en el menú del usuario con modal simple.
- **Pasada de estados en todas las pantallas:** loading (skeletons simples), vacíos (voz de marca), errores (mensaje + reintentar). Revisión completa en viewport móvil (~390 px).
- **Playwright — suite de humo** (`npm run e2e`, arranca `next dev` solo y asume backend + compose arriba, datos semilla):
  1. Login y logout de cada rol llegan a su home.
  2. Ana reserva un cupo de María → aparece en Mis clases y desaparece de la agenda.
  3. Ana cancela una clase a más de 24 h → vuelve el cupo a la agenda.
  4. María ve la reserva de Ana en sus próximas clases.

**Verificación:** instalar la PWA (Chrome: "Instalar aplicación") y abrirla standalone; cambiar la contraseña de Ana y volver a entrar; `npx playwright test` en verde.

**DETENTE aquí. Fin de la Tarea 4 — MVP 1 completo en local.**

---

## 9. Definition of done (checklist final)

- [ ] Los 5 usuarios semilla entran y cada rol ve solo su navegación y sus rutas
- [ ] Flujo completo estudiante: explorar → reservar → correo con .ics → aparece en Mis clases → cancelar libera el cupo
- [ ] WhatsApp con mensaje precargado funciona desde Mis clases (ambos roles)
- [ ] Profesor gestiona franjas, bloqueos y perfil, y sus cambios se reflejan de inmediato en lo que ve el estudiante
- [ ] Registro de asistencia desde Pasadas con estados visibles
- [ ] Panel admin: crear/inactivar usuarios, tabla de reservas con filtros y métricas (autoservicio visible)
- [ ] Errores del API visibles con sus mensajes; ninguna regla de negocio duplicada en el frontend
- [ ] PWA instalable; revisión móvil completa; cambio de contraseña operativo
- [ ] `npx playwright test` verde; tests backend nuevos verdes; README actualizado (cómo correr frontend + e2e)
- [ ] Un commit por paso; cero features futuras

## 10. Fuera de alcance de esta tarea — NO construir

Despliegue y configuración de producción (Tarea 5), service worker y modo offline, subida de archivos/fotos, paginación real, notificaciones push, modo oscuro, i18n, edición de reservas (se cancela y se vuelve a reservar), integración con la API de Google Calendar, y cualquier pantalla de features del MVP 2+. Si algo parece faltar, pregunta antes de agregarlo.

## Adendum al brief — Entregable de Claude Design (aprobado por Pardo)

1. En `docs/design/` está el diseño de alta fidelidad (pantallas, fundamentos
   y tokens). Es el **contrato visual** del frontend.
2. **Prohibido copiar código de `docs/design/` al frontend.** Es referencia
   visual. La arquitectura del brief (decisiones 1–10: apiFetch, TanStack
   Query, tipos generados, client components, dependencias) sigue mandando.
3. Acción inmediata: localiza el bloque de tokens `@theme` del entregable e
   instálalo en el CSS global reemplazando los provisionales. Si el diseño
   define una Google Font, cárgala con `next/font` (una sola familia). Si usa
   Lucide, queda aprobada la dependencia `lucide-react` — ninguna otra.
4. Al construir cada pantalla (Pasos 2–5): wireframes = contrato de
   estructura; el diseño de esa pantalla = contrato de estilo. Ante conflicto
   entre ambos, gana el wireframe y le preguntas a Pardo.
5. La checklist visual de cada paso suma una pregunta: "¿se parece al diseño?"