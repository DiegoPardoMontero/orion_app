# Un indicador «Tu clase va aquí» en la barra de la videollamada

| | |
|---|---|
| **Fecha** | 01/10/2026, 02:52 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | El aula (`frontend/src/components/aula/`) |
| **Estado** | Hecho |

## El pedido, tal cual

> La barra en la videollamada me causa mucho confusión. Pareciera que algo está cargando que no sirve y tuve que hacer un esfuerzo real por darme cuenta que era la clase. Puedes dejarla pero quizá con un indicador. Por ejemplo, un punto al final de donde vaya la línea y un indicador pequeño que diga "Tu clase va aquí!". ALgo del estilo. Ah, vi que ya tienes como una estrellita al final, entonces solo el indicador.

## Resumen ejecutivo

- **Historia:** como profe o estudiante en la videollamada, quiero entender de un vistazo que la
  barra es el avance de mi clase, para no pensar que algo está cargando.
- **Feature:** la barra se queda, con su estrella al final, y suma una etiqueta pequeña junto a la
  estrella, del estilo «Tu clase va aquí».

## Estado

Hecho y en producción.

- La etiqueta «¡Tu clase va aquí!» va sobre la estrella, con una puntita que la señala. Va encima de
  la línea porque debajo empieza la videollamada.
- Cerca de los bordes se ancla a ese lado, con 8 px de margen.
- Revisado en pantalla con el aula simulada, al inicio, a la mitad y al final de la clase, a 390 y a
  1280 px.
- E2E completos sobre una base limpia: 105 de 105. `next build` en verde. Una corrida anterior se
  invalidó porque el equipo se suspendió en la noche y las sesiones vencieron a mitad.
- UAT todavía no existe, así que fue directo a `master`.
