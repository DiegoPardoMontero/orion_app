# Mantenimiento y revisión de todo el código

| | |
|---|---|
| **Fecha** | 06/10/2026, 22:47 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/ESTADO.md` |
| **Estado** | En curso |

## El pedido, tal cual

> Ejecuta una revisión, actualiza lo que puedas, corre tests y verifica que todo vaya bien. SImplemente ejecuta mantenimiento y ejecuta tarea de revisión de TODO el código solo UN ORDEN DE MAGNITUD por debajo de hacer ultrareview command.

## Resumen ejecutivo

- **Historia:** como dueño de Orión, quiero una revisión de todo el código y un mantenimiento
  (dependencias al día, pruebas, verificación), para lanzar a profesores sabiendo que la base está
  sana.
- **Cómo se hace:**
  - Revisión de solo lectura por áreas: backend del dinero y las clases, backend de identidad,
    seguridad y lo demás, y frontend. Son pocos revisores a la vez, porque el equipo tiene 7,7 GB.
    Es un orden de magnitud menos que `/code-review ultra`, que lanza muchos agentes en la nube.
  - Cada hallazgo se verifica antes de tocar código. Lo confirmado se arregla con su prueba.
  - Lo que necesita una decisión de Pardo se le pregunta.
- **Mantenimiento:**
  - las dependencias con actualizaciones menores o de parche, sin cambiar versiones mayores;
  - `npm audit`;
  - `./mvnw verify`, e2e y `next build` en verde antes de subir.

## Mantenimiento (hecho, `chore(deps)` del 06/10 noche)

- **Frontend.** `npm audit` tenía 5 avisos (1 crítico: la línea 16.2 de Next, por `postcss` y
  `sharp`). Quedó en **0** con Next 16.2.10 → 16.3.8 y sus dependencias. Además: Playwright 1.63,
  React Query 5.104, Tailwind 4.3.3, lucide-react 1.52 (los 109 íconos en uso siguen existiendo),
  tipos de React 19.3.
- **Backend.** Spring Boot 4.1.0 → 4.1.1 y springdoc 3.0.3 → 3.1.1 (parches). Flyway y el driver de
  Postgres van con el BOM de Boot.
- **No se tocaron a propósito**, por ser saltos mayores que piden una tarea propia: TypeScript 7,
  ESLint 10, Vitest 5, `@types/node` 26, React 19.3, y la milestone 4.2 de Boot.
- **Comprobado:** `tsc`, `eslint` (0 errores, 2 avisos previos), 196 pruebas de Vitest y `next build`
  en verde. El `./mvnw verify` completo y los e2e van al cierre, con lo que salga de la revisión.

## Estado

En curso: la revisión del código (tres revisores de solo lectura: dinero y clases; identidad y
seguridad; frontend) y el `./mvnw verify` con las dependencias nuevas.
