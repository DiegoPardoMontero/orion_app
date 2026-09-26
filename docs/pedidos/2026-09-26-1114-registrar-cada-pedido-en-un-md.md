# Registrar cada pedido del chat en un .md

| | |
|---|---|
| **Fecha** | 26/09/2026, 11:14 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code, mientras corría la verificación de la sexta tanda |
| **Quién** | Pardo |
| **Relacionado** | `CLAUDE.md` → «Registro de pedidos»; memoria `registro-de-pedidos` |
| **Estado** | Hecho |

## El pedido, tal cual

> Necesito que guardes una regla de aquí en adelante. Todo lo  que te pida por chat lo vas a guardar en un .md con el nombre que determines con qué te pedí exactamente y el resumen ejecutivo (sibre todo a nivel de historias de usuario y features) por cada uno de esots mensajes, así como timestamp y otra metadata que quieras incluir. ¿Entendido? Guarda esta regla a nivel de repo por si esta sesión acaba. Y sigue trabajando.

## Resumen ejecutivo

**Historia de usuario.** Como dueño de Orión quiero que cada cosa que pido por chat quede escrita en
el repo, con mis palabras exactas y un resumen en historias de usuario, para poder revisar qué pedí,
cuándo y en qué quedó, aunque la sesión de Claude se acabe.

**Feature.** Carpeta `docs/pedidos/` con un archivo por mensaje:

- Nombre `AAAA-MM-DD-HHMM-<qué-pidió>.md`, en hora de Bogotá.
- Metadatos: fecha y hora, canal, y briefs, commits y artifacts relacionados.
- El texto exacto del pedido.
- Resumen ejecutivo en historias de usuario y features.
- Estado: hecho, en curso o pendiente de una respuesta.
- Índice en `docs/pedidos/README.md`.

**Decisión.** La regla vive en `CLAUDE.md`, que es lo primero que lee cualquier sesión nueva, y
también en la memoria de Claude.
