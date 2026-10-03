# ¿Qué más nos falta?

| | |
|---|---|
| **Fecha** | 01/10/2026, 10:37 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/plan-de-lanzamiento.md`, `docs/cerrar-la-casa.md`, `docs/prueba-fase-1.md`, `docs/ambiente-de-pruebas.md`, `docs/ESTADO.md` («Pendiente / bloqueos conocidos») |
| **Estado** | En curso |

## El pedido, tal cual

> ¿Qué más nos falta?

## Resumen ejecutivo

- **Historia:** como dueño de Orión, quiero la lista de lo que falta para la fase 1 (abrir a
  profesores), separada entre lo mío, lo de Claude y lo que espera una decisión, para saber qué hacer
  hoy.
- **Respuesta:** en el chat, con lo que dicen el plan de lanzamiento, las guías, ESTADO y los pedidos
  abiertos al 01/10/2026.

## Decisiones de Pardo (eligiendo entre opciones)

- **La reseña de una clase de prueba gratis:** «No cuenta en el promedio». El estudiante la puede
  dejar y el profe la ve, pero no mueve sus estrellas ni el ranking.
- **«5 cupos» en Mis horarios:** «Decir las clases que caben», por ejemplo «hasta 3 clases» en una
  franja de 6 a 9 PM.
- **Lo siguiente de Claude:** probar si la IP real llega detrás del proxy de Railway (`X-Forwarded-For`).

## Estado

Hecho; queda una revisión anotada.

- **La reseña de una clase de prueba gratis no cuenta en el promedio ni en el ranking** (`1c2e0af`):
  - `ReviewRepository.aggregateVisible` deja fuera las reservas de prueba;
  - la V81 recalcula las métricas guardadas;
  - la reseña se sigue viendo en la lista;
  - probado en `ProfessorRatingIT`.
- **«Mis horarios» cuenta las clases que caben, no las horas de inicio** (`6d9af92`):
  - «Una franja de 6 a 9 PM te deja dar hasta 3 clases, y el estudiante elige a qué hora empieza»;
  - lo mismo en el contador de la semana, el consejo de Rigel, la vista previa al arrastrar, el
    formulario de la franja y el aviso al abrirla.
- **La prueba de la IP del proxy:** en los intentos del 02/10, el freno de intentos fallidos del login
  no se activó en producción. La investigación quedó ahí y se anotó en ESTADO («Pendiente») para
  revisarla con los registros de Railway.
- El plan de lanzamiento queda al día: la decisión 2 (el estudiante cancela una clase pagada) ya estaba
  resuelta, y la 3 (la reseña de prueba) se decidió hoy.
