# Revisión completa de los flujos del profesor, de noche, hasta las 5 a. m.

| | |
|---|---|
| **Fecha** | 28/09/2026, 23:25 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/plan-de-lanzamiento.md` (versión 2: se abre primero a profesores) y `2026-09-27-1002-abogado-terminos-y-megareview-del-profesor.md` (la megarrevisión anterior) |
| **Plazo** | Hasta el 29/09/2026 a las 05:00 (Bogotá) |
| **Estado** | En curso |

## El pedido, tal cual

> Trabaja en los flujos del profesor, busca bugs, quiero que TODO quede perfecto, si seguimos este plan. ¿Puedes hacer esta revisión completa? La quiero a nivel de código, pantallas y alto nivel de calidad visual. Voy a dormir, tienes hasta las 5am, hora Colombia para trabajar en esto.

## Resumen ejecutivo

- **Historia:** como profe fundador que llega en la primera semana del lanzamiento, recorro todo el
  camino sin un solo tropiezo y con pantallas que se ven profesionales:
  - «Enseña con Orión» o la invitación;
  - el registro y la postulación;
  - la revisión y la primera entrada;
  - el perfil, los horarios, los datos de pago e invitar estudiantes;
  - la reserva, la clase, el acta y la práctica;
  - los mensajes, las ganancias, las liquidaciones, el desempeño y la ayuda.
- **Feature:** revisión completa del lado del profesor, en tres niveles:
  - **código** (frontend y backend);
  - **pantallas** (recorridas en el navegador, en celular y computador, en estados vacíos y con datos);
  - **calidad visual**.

  Se arregla todo lo que aparezca, con pruebas, y se deja lo que necesite una decisión de Pardo.
- **Cómo se trabaja:** en serie (el equipo se cae con muchos procesos a la vez); commits por arreglo;
  `./mvnw verify`, e2e y `next build` en verde antes del push; correo con el resumen al final.

## Estado

En curso.
