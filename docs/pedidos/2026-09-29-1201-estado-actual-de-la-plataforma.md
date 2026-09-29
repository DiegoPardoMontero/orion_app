# Estado actual de la plataforma

| | |
|---|---|
| **Fecha** | 29/09/2026, 12:01 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/ESTADO.md`, `docs/plan-de-lanzamiento.md` (versión 2) y `2026-09-28-2325-revision-completa-de-los-flujos-del-profesor.md` |
| **Estado** | Respondido |

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

## Estado

Respondido en el chat.
