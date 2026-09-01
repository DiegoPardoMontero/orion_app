# Brief · Pulido v1 — feedback de producto (sept 2026)

**Origen:** revisión de Pardo sobre la app en producción (PDF en `docs/feedback/`). Rigen `CLAUDE.md`, los tokens de `docs/design-v2/` y `MASCOTA.md`. Un paso a la vez, DETENTE, tests al estándar de siempre.

## Decisiones — LEER ANTES

1. **Regla de iteración visual post-Design v2:** los ajustes menores de esta lista los propones tú y se aprueban con **capturas en el DETENTE** (móvil y desktop). Si un cambio exige reorganizar una pantalla de forma estructural más allá de lo aquí descrito, pausa y avisa — Claude Design dibuja, tú no.
2. **Teléfonos en formato E.164** con selector de país (Colombia +57 por defecto). Sin librerías nuevas: lista curada de países (LatAm + US + ES).
3. **Fotos para todos, con subida real.** `users.photo_url` reemplaza a la del perfil de profesor, y la app sube archivos a **Cloudinary** vía backend — muere el flujo manual de URLs. (Adelantamos esta deuda porque los estudiantes no van a usar Cloudinary a mano.)
4. **Link de videollamada automático, no campo.** Las reservas virtuales generan sala **Jitsi** al confirmarse (`meet.jit.si`, navegador, sin cuentas), detrás de una interfaz `MeetingLinkProvider` para que el MVP 2 la cambie por Google Meet real sin tocar nada más. El campo de texto desaparece; el de lugar queda solo para presencial.
5. **Los profesores entran por INVITACIÓN del admin, no por registro abierto.** Preserva la curaduría de Sofía (modelo de academia, no marketplace de tutores) y mata la fricción actual de claves temporales por WhatsApp. Las aplicaciones inbound de profesores llegarán con el CRM (MVP 5).
6. **Landing:** NO va en este brief — ya existe `orion-minibrief-landing-v1.md`. No duplicar trabajo.
7. **Mascota:** se autoriza corregir el **orden de capas** del SVG de la pose del panel de auth (la mano alzada debe quedar detrás del cuerpo): reordenar nodos sí; redibujar formas no — si exige redibujar, se le pide a Claude Design.

---

## Paso 0 — Quick wins visuales (y un bug de API)

1. **Foto del profesor en Mis Clases.** Causa raíz: el objeto `counterpart` del endpoint `/me/bookings` solo lleva `{id, fullName, whatsappPhone}` — la foto nunca viaja. Añadir `photoUrl` (y `headline`) al DTO en el backend y renderizarla en las tarjetas (con fallback de iniciales). Aplica a ambos roles.
2. **Tarjetas de `/profesores`:** headline completo sin truncar; bio visible con clamp de 3 líneas y "ver más" hacia el detalle; cuerpo de texto un punto mayor. La pantalla de detalle ya resuelve la bio completa.
3. **Mascota:** el fix de capas de la decisión 7.

**Verificación:** capturas en 390 y 1280 de las tres cosas. **DETENTE.**

---

## Paso 1 — Teléfono con país y prefijo automático

- **Componente `PhoneInput`:** selector de país (bandera + prefijo, Colombia preseleccionada) + número local; produce y muestra E.164. Se usa en: registro, perfil, formulario de invitación (Paso 6) y creación de usuarios del admin.
- **Backend:** utilidad central de normalización a E.164 + migración `V7__phone_e164.sql` de backfill: números de 10 dígitos que empiezan por 3 → prefijo `+57` (heurística de celular colombiano); los que no encajen se dejan intactos y se listan en el log de migración para revisión manual de Pardo.
- Los links `wa.me` usan el E.164 sin el `+` (ya normalizado, dejan de romperse con números extranjeros).

**Tests:** normalización (con y sin prefijo, con espacios/guiones), backfill. **Verificación:** registrar un usuario con país distinto a Colombia y comprobar su link de WhatsApp. **DETENTE.**

---

## Paso 2 — Fotos para todos (estudiantes incluidos)

- **`V8__user_photos.sql`:** `users.photo_url VARCHAR(500)` + backfill desde `professor_profiles.photo_url`; la columna vieja queda deprecada (se deja de leer; se eliminará en una migración futura).
- **`POST /api/v1/me/photo`** (multipart, cualquier rol autenticado): valida tipo (`jpeg/png/webp`) y tamaño (≤ 5 MB), sube a Cloudinary (credenciales por env `CLOUDINARY_URL`), persiste la URL entregada con transformación de avatar (`c_fill,g_face,w_400,h_400`). Rate limit ligero reusando el patrón de auth (proteger cuota).
- **UI:** "Cambiar foto" en el menú de usuario de **ambos roles** — en el perfil del profesor reemplaza al campo de URL. El fallback de iniciales se mantiene en todo el sistema.

**Tests:** validaciones de tipo/tamaño, persistencia, 401. **Verificación:** Ana sube su foto desde el teléfono → el profesor la ve en sus clases; María cambia la suya sin tocar Cloudinary. **DETENTE.**

---

## Paso 3 — Link de videollamada automático

- **`V9__meeting_links.sql`:** `bookings.meeting_link VARCHAR(300)`.
- Interfaz **`MeetingLinkProvider`** en `scheduling` + implementación `JitsiMeetingLinkProvider`: al crear una reserva **VIRTUAL** genera `https://meet.jit.si/OrionIdiomas-{8 chars del bookingId}`. Comentario en la interfaz: *"MVP 2: GoogleMeetProvider vía Calendar API reemplaza esta implementación."*
- **El campo de link desaparece del formulario de reserva.** Para PRESENCIAL, el campo de lugar se mantiene con placeholder claro ("¿Dónde se encontrarán?").
- El link aparece como botón **"Unirse a la clase"** en Mis Clases (solo virtuales), en los correos de confirmación y recordatorio, y en la `DESCRIPTION` del `.ics`.
- Nota de producto para Pardo/Sofía: avisar a los profesores que la sala es Jitsi (abre en navegador, sin cuenta). Si un profesor prefiere su propio Meet, lo comparte por WhatsApp como siempre — el botón es el default, no una cárcel.

**Tests:** virtual genera link, presencial no, el link viaja en correo e .ics. **Verificación:** reservar virtual → botón visible + link en Mailpit → la sala abre. **DETENTE.**

---

## Paso 4 — Pantalla de reserva: perfil compacto + semana en desktop

1. **Columna del perfil:** rediseñar para bios reales (cortas): foto grande, nombre, chips de modalidad/niveles, bio sin océanos de espacio vacío. Propuesta tuya con captura.
2. **Vista semanal de cupos en desktop (≥ 1024 px):** 7 columnas (semana navegable ← →) en lugar de chips de día + hora. **Móvil conserva los chips** — la grilla semanal en 390 px es scroll horizontal infernal; decisión consciente, no pereza.

**Verificación:** capturas en 390 / 768 / 1280 + el e2e de reserva sigue verde. **DETENTE.**

---

## Paso 5 — Disponibilidad sin scroll (desktop)

- **Desktop:** los 7 días como grilla de columnas + panel de fechas bloqueadas al costado → todo visible sin scroll en 1280×800.
- **Móvil:** filas densas; los días sin franjas colapsan a una línea. Honestidad física: en 390 px, "cero scroll" con 7 días + bloqueos + targets de 44 px no coexisten — el objetivo móvil es la semana completa visible above the fold.

**Verificación:** capturas de ambos. **DETENTE.**

---

## Paso 6 — Registro de profesores por invitación

- **`V10__professor_invites.sql`:** ampliar el CHECK de `auth_tokens.purpose` con `'PROFESSOR_INVITE'` (expiración: 7 días).
- **`POST /api/v1/admin/professors/invite`** `{email}`: crea el usuario `PROFESSOR` en estado `INACTIVE` (hash aleatorio, perfil vacío sin publicar), genera el token y envía el correo de invitación con voz de marca ("Sofía te invita a enseñar en Orión"). Email ya existente → `409`. Reenvío permitido (invalida el token anterior).
- **Pantalla `/invitacion?token=…`** (misma suite visual de auth, split-screen): el profesor completa nombre, teléfono (`PhoneInput` del Paso 1), contraseña, foto (Paso 2), headline y bio → la cuenta pasa a `ACTIVE` con `email_verified_at = now` → aterriza directo en `/disponibilidad` con su coach de bienvenida. Publicar el perfil sigue el flujo de siempre.
- **Panel admin:** los invitados aparecen como "Invitación pendiente" con botón de reenviar. Solo `ADMIN` invita; token de un solo uso.
- Con esto **muere el flujo de claves temporales por WhatsApp**. Contárselo a Sofía: es su proceso de onboarding el que cambia (para mejor).

**Tests de contrato:** invitar feliz, email duplicado, aceptar feliz, token expirado/reusado, reenvío, el INACTIVE no puede loguearse antes de aceptar. **Verificación E2E:** invitación real vía Mailpit hasta quedar con disponibilidad configurada. **DETENTE. Fin del brief.**

---

## Definition of done

- [ ] Fotos visibles en Mis Clases (ambos roles) y subida real funcionando para todos — el flujo manual de Cloudinary queda muerto
- [ ] Teléfonos E.164 con país en todos los formularios; backfill aplicado; links de WhatsApp correctos
- [ ] Reservas virtuales con sala automática en app, correos e .ics; el campo manual eliminado
- [ ] Reserva en desktop con vista semanal y perfil compacto; móvil intacto; disponibilidad sin scroll en desktop
- [ ] Invitación de profesores de punta a punta; "Invitación pendiente" visible en admin
- [ ] Mascota con las capas corregidas; cards de profesores con la descripción visible
- [ ] Todos los e2e previos en verde + los nuevos; `docs/ESTADO.md` y la tabla viva de `MASCOTA.md` actualizados

## Fuera de alcance

Registro abierto/aplicaciones de profesores (MVP 5 con CRM), Google Meet real y Calendar API (MVP 2), recorte/edición de foto en el cliente, notificaciones push, y cualquier reorganización de pantallas no listada aquí. Si algo parece faltar, pregunta antes de agregarlo.
