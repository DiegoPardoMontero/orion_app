# Estado actual de la plataforma

| | |
|---|---|
| **Fecha** | 29/09/2026, 12:01 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/ESTADO.md`, `docs/plan-de-lanzamiento.md` (versión 2) y `2026-09-28-2325-revision-completa-de-los-flujos-del-profesor.md` |
| **Estado** | Hecho |

## El pedido, tal cual

> ¿Cuál es el estado actual de la plataforma?

## Resumen ejecutivo

- **Historia:** como dueño de Orión, quiero saber en qué punto está la plataforma, para decidir qué
  hacer esta semana del lanzamiento a profesores.
- **Respuesta** (con lo comprobado el 29/09 a las 12:01):
  - Producción responde y ya tiene lo de la revisión nocturna: la página «Enseña con Orión» dice
    «Profes fundadores, por invitación».
  - En el catálogo público hay 4 profes publicados. Uno es «Diego Pardo Test 28/09», una cuenta de
    prueba que se ve en público.
  - `master` va al día con `origin/master` y las pruebas están en verde: 451 unitarias, 684 de
    integración y 105 e2e.
  - El resto sale de `docs/ESTADO.md` («Pendiente / bloqueos conocidos») y de la semana 1 del plan de
    lanzamiento: lo que falta para cobrarle a alguien de verdad es sobre todo configuración y
    asesoría (Pardo, Sofía, contador y abogados), no código.
- **Desde aquí no se puede comprobar** si las integraciones están encendidas en producción (Wompi,
  JaaS, Cloudinary, Resend, OpenAI, avisos): se ven en Administración → Sistema, con sesión de admin.

## Respuestas de Pardo (29/09, 12:05, eligiendo entre opciones)

- **Nombre del profe en los comprobantes:** «Dejarlo así» (queda anotado también en el pedido de la
  revisión nocturna).
- **«Diego Pardo Test 28/09» en el catálogo público:** «Ocultarlo». Desde aquí no hay acceso a
  producción (ni Railway ni sesión de admin), así que lo hace Pardo. Se le recomendó entrar con esa
  cuenta, ir a Mi perfil, apagar «Perfil visible» y guardar: así la cuenta sigue sirviendo para sus
  pruebas. Desactivarla desde Admin → Usuarios también la saca del catálogo, pero ya no puede entrar.
- **Siguiente paso:** «Guía para cerrar la casa»: la lista paso a paso de lo que le toca a Pardo
  (variables de Railway, webhook de JaaS, claves VAPID, rotar llaves y tope de OpenAI).
  - **Historia:** como dueño de Orión, quiero una guía paso a paso para dejar producción lista para
    cobrar de verdad, y así no olvidar ninguna llave ni configuración antes del primer profe.

## Estado

Hecho. La guía quedó en `docs/cerrar-la-casa.md` y va entera en el chat. Lo que queda es de Pardo
(los pasos de la guía) y, cuando él diga, la prueba de la IP detrás del proxy.
