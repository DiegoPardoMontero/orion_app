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

## Estado

En curso.
