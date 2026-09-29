# Los pasos desde cero para probar la fase 1 del lanzamiento, con profes

| | |
|---|---|
| **Fecha** | 29/09/2026, 12:20 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `docs/plan-de-lanzamiento.md` (semana 1: abrir a profesores), `docs/cerrar-la-casa.md` y `2026-09-29-1201-estado-actual-de-la-plataforma.md` |
| **Estado** | Hecho |

## El pedido, tal cual

> Dame la serie de pasos desde 0, ¿qué debo probar para hacer este lanzamiento con profes? La primera fase del lanzamiento

## Resumen ejecutivo

- **Historia:** como dueño de Orión, quiero una serie de pasos desde cero que diga qué probar y qué
  debe pasar en cada paso, para abrir la primera fase del lanzamiento (los profes fundadores) sabiendo
  que el camino del profe funciona en producción.

- **Feature:** una guía de prueba en `docs/prueba-fase-1.md`, en producción y con dinero de verdad.
  Sigue el camino de `frontend/e2e/flujo-profesor-completo.spec.ts` y tiene seis partes:
  0. antes de empezar;
  1. el fundador invitado;
  2. el profe que llega solo, con cambios pedidos y aprobación;
  3. la primera clase pagada, con acta y reseña;
  4. lo que puede salir mal: cancelaciones, reclamo y falta de llave Bre-B;
  5. el corte y el pago por Bre-B;
  6. listo para invitar.

  Cada paso dice qué debe pasar.

## Estado

Hecho: la guía quedó en `docs/prueba-fase-1.md` y va en el chat. La prueba la hace Pardo; lo que no
pase como dice, lo avisa con la hora y la pantalla.
