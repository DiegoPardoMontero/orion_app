# Los avisos no suenan, y la campana no muestra si están activos ni pide permiso

| | |
|---|---|
| **Fecha** | 28/09/2026, 15:28 (Bogotá, UTC−5) |
| **Canal** | Chat de Claude Code |
| **Quién** | Pardo |
| **Relacionado** | `2026-09-28-1253-una-columna-perfil-mas-bonito-recorrido-obligatorio-rigel-y-avisos.md` (ahí se explicó cómo activarlos y probarlos) |
| **Estado** | En curso |

## El pedido, tal cual

> No está sonando nada, pero tampoco veo que esté desactivado o que me pida permisos. Revisa bien

## Resumen ejecutivo

- **Historia:** como usuario de Orión, en la campana veo si los avisos en este dispositivo están
  activos, apagados o no disponibles aquí, y puedo activarlos; al activarlos, el navegador me pide
  permiso y los avisos suenan.
- **Contexto:** Pardo puso las claves VAPID en Railway y Sistema las muestra activas, pero al
  probar no suena nada y la campana no le muestra ni «Activar avisos» ni que estén apagados.
- **Feature:** encontrar por qué y arreglarlo.

## Estado (28/09/2026): arreglado; falta que Pardo diga en qué dispositivo probó

**Diagnóstico.**
- Las claves y el envío están bien: se probó de punta a punta en local, con claves desechables. El
  servidor firma y cifra el aviso, el servicio de push de Google lo entrega y el service worker lo
  muestra.
- En producción, `sw.js` se sirve bien y es igual al del repositorio.
- El problema era la campana. Su pie de avisos **se escondía sin decir nada** en cuatro casos: el
  navegador no admite avisos (Safari en un iPhone sin instalar Orión); Orión los tenía apagados;
  mientras revisaba (la primera consulta puede tardar unos 3 segundos); y ante cualquier error.
  Pardo abría la campana y no veía nada.

Aparecieron además dos fallas:
- **«Activos» falso.** Una suscripción que el navegador guardó pero el servidor no (un envío que
  falló) mostraba «activos», y «Probar» respondía «No llegó a ningún dispositivo».
- **Incógnito.** En una ventana de incógnito, Chrome no tiene avisos, y la campana decía solo «No se
  pudo. Inténtalo otra vez.».

**Arreglo** (`2003347`):
- La campana siempre dice en qué están los avisos aquí: «Revisando…», no disponibles en este navegador
  (en iPhone, cómo instalar Orión), apagados en Orión, activos, inactivos o bloqueados.
- Un fallo dice su motivo: el mensaje del servidor, el permiso negado o la ventana privada.
- Al abrir la campana, la suscripción del navegador se vuelve a mandar al servidor, así que «activos»
  quiere decir que Orión sabe a dónde avisar.
- El servidor deja en el log qué servicio de push rechazó.

**Verificación:** `./mvnw verify` con 450 unitarias y 675 de integración en verde; Vitest 195; `next build` en verde. En el navegador: el pie
dice «Revisando…» al abrir, y en incógnito explica por qué no se puede.

**Para cerrar:** saber en qué dispositivo y navegador probó Pardo. Si fue Safari en el iPhone sin
instalar Orión, ya la campana se lo dice, y es la limitación de Apple.
