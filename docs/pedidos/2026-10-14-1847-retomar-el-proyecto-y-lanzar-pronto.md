# Retomar el proyecto y lanzar pronto

## Metadatos

- **Fecha y hora:** 2026-10-14 18:47 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Briefs relacionados:** `docs/plan-de-lanzamiento.md` (versión 3, escrita en este pedido),
  `docs/cerrar-la-casa.md`, `docs/ESTADO.md`
- **Commits relacionados:** el commit de documentación de esta sesión (plan v3, ESTADO, pedido)
- **Artifacts relacionados:** —

## El pedido exacto

> ¿Cuál es el estado actual? El proyecto había sido pausado porque con Sofía necesitabamos enfocarnos en otras cosas. Ya estamos de vuelta y necesitamos hacer un lanzamiento pronto. Me ayudas a remotar d ela mejor forma posible?

## Resumen ejecutivo

- **Como** fundador que vuelve tras una pausa, **quiero** un resumen fiel de en qué quedó Orión
  **para** no arrancar a ciegas.
- **Como** fundador, **quiero** un plan de retoma orientado a lanzar pronto **para** saber qué
  falta de verdad, qué está pendiente de mí y en qué orden atacarlo.

Features: ninguna nueva. Es una lectura de estado y un plan de retoma.

### Lo que se comprobó (no estaba escrito en ningún lado)

- `master` = `origin/master`; lo del 07/10 lo subió Pardo con su «Backup snapshot» del 08/10. No se
  sabe desde fuera si Railway lo desplegó (health responde 401): se mira en Deployments.
- UAT no existe (`uat.orionidiomas.com` no resuelve); `develop` va 19 commits atrás. Todo va a `master`.
- El catálogo de producción tiene 4 perfiles publicados: 3 de prueba de Pardo y «Andrés Rosero», por
  confirmar. Nadie se postuló durante la pausa: ningún correo de Orión desde el 30/09.
- **OpenAI apaga `gpt-4.1-nano` el 23/10** (correo del 08/10): es el traductor en vivo de Meissa.
- **Railway: límite duro de USD 10, en 7,51 el 14/10**; pasado el límite no hay despliegues.
- `next build` en verde hoy y **105 e2e en verde** sobre base limpia (lo que el 07/10 quedó sin correr).

### Lo que se entregó

- `docs/plan-de-lanzamiento.md` **versión 3**: semanas re-ancladas en el 14/10, lo nuevo con fecha,
  el orden de trabajo de Claude y la lista de Pardo, y las decisiones abiertas.

### Semana 0, lo de Claude (en marcha la misma noche)

1. ✅ Traductor de Meissa en `gpt-5-nano` (`16a643a`; verify 453 + 700 en verde; subido).
2. ✅ El reclamo de un ensayo no deja ausencia al profe (`EnsayoDelActaIT` 9/9).
3. ✅ La invitación por WhatsApp se consume también al registrarse con Google (`7c66664`; verify 453 + 703).
4. ✅ El admin corrige un correo sin confirmar y la confirmación va al nuevo (`70b2d9f`; `AdminUsersIT` 14/14).
5. ✅ `robots.txt` abre `/profesores` (`f6f4f44`).

## Estado

**Hecho; pendiente de las respuestas de Pardo.** Plan v3 escrito, ESTADO al día, e2e en verde,
correo enviado. El commit de documentación queda en local hasta que Pardo suba el límite de Railway
(cada push es un despliegue).

### Decisiones de Pardo (14/10/2026, 19:05, eligiendo entre opciones)

- **Railway:** «Ya lo subí, haz push».
- **Traductor de Meissa:** «Sí, gpt-5-nano».
- **Pendientes del 07/10:** «(a) después, (b) ahora»: `@Version` queda anotado para después del
  lanzamiento; la ausencia que deja un ensayo del admin se arregla en la semana 0.
- **Catálogo:** «Los ocultas tú desde Mi perfil»: Pardo apaga «Perfil visible» de sus tres cuentas de
  prueba; Andrés Rosero es real y se queda.

### Las preguntas, tal como se hicieron

1. Confirmar que suba el límite de Railway antes de que Claude haga push.
2. Cambiar el traductor de Meissa a `gpt-5-nano` antes del 23/10 (sí / otro modelo).
3. `@Version` en reserva y pago: ahora o después del lanzamiento.
4. La ausencia que deja un ensayo del admin: que nunca cuente, o dejarla y avisar.
5. Qué hacer con los perfiles de prueba visibles en el catálogo y si Andrés Rosero es real.
