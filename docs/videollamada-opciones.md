# La videollamada: qué hay, qué falta y qué costaría arreglarlo

**Escrito el 08/09/2026**, a raíz del punto 4 de la revisión del portal del estudiante: *«el
estudiante no debe poder ser anfitrión ni tomar control de la app durante la sesión»*.

## El problema es real

Orión crea hoy una sala pública en `meet.jit.si`. En ese servicio **manda quien entra primero**. Si
el estudiante se conecta antes que el profesor, es él quien puede silenciar a los demás, expulsar y
terminar la reunión. No es un descuido de configuración: el control de moderador se concede con un
**token firmado**, y el servicio público no acepta ninguno.

Hay un segundo problema que nadie había mirado: los propios términos de `meet.jit.si` **piden no
usarlo con fines comerciales** y topan el uso agregado en unos 25 usuarios activos al mes. Orión ya
lo está usando en producción para clases que se cobran. Aunque hoy no nos corten, es una dependencia
que no controlamos sosteniendo el producto entero.

## Lo que ya se apretó (08/09/2026, sin cuenta nueva ni costo)

- **El nombre de sala pasó de 8 a 32 caracteres.** Con 8 eran 32 bits: probando nombres se entra en
  clases ajenas. Ya no.
- **Antesala obligatoria** (`prejoinPageEnabled`): nadie cae dentro de la clase con el micrófono
  abierto sin darse cuenta.
- **Sin botón de invitar** (`disableInviteFunctions`): la sala es de esa clase y de nadie más.

Nada de esto impide que el estudiante sea moderador. **Eso no se puede arreglar sin pagar.**

## Las dos salidas

| | **Jitsi JaaS** (8x8) | **Zoom** |
|---|---|---|
| Control de anfitrión | Sí: token JWT por participante, el profesor entra como moderador y el estudiante no | Sí: el profesor es *host*, el estudiante *participant* |
| Sala de espera | Sí | Sí |
| Cuánto cambia el código | Poco: la misma sala, firmando un JWT al generar el enlace | Más: crear la reunión por API en cada reserva, guardar dos enlaces (join y start) y renovar credenciales OAuth |
| Precio | Gratis hasta 25 usuarios activos/mes · **99 USD/mes** hasta 300 · 499 hasta 1.500 · 999 hasta 3.000 · 0,99 USD por usuario extra | **14,16 USD/mes por anfitrión** (anual) o 16,99 mensual. Se paga por profesor que dé clase a la vez |
| Se rompe si… | Se pasa de tramo: salta al siguiente de golpe | Dos profesores dan clase a la misma hora con una sola licencia |

*Precios consultados el 08/09/2026; confirmar antes de contratar.*

## Lo que yo recomendaría

**JaaS**, por dos razones que no son el precio:

1. **Es el cambio más pequeño.** Ya usamos Jitsi. Pasar a JaaS es firmar un JWT en
   `JitsiMeetingLinkProvider` y cambiar el dominio; el resto del sistema no se entera. Zoom obliga a
   llamar a su API en cada reserva, a manejar tokens OAuth que caducan y a decidir qué pasa cuando
   esa llamada falla justo al confirmar una clase que el estudiante ya pagó.
2. **El costo crece con el uso, no con la plantilla.** Con Zoom se paga por profesor; con decenas de
   profesores dando pocas clases cada uno —que es exactamente Orión hoy— eso sale caro y absurdo.

El tramo gratuito de JaaS (25 usuarios activos al mes) probablemente **ya cubre el piloto**, y a
diferencia del servicio público, usarlo así es legítimo.

## Lo que hace falta para decidir

- Cuántos estudiantes distintos se esperan al mes en los próximos seis meses. Si son menos de 25, la
  migración a JaaS es gratis y se puede hacer ya.
- Si Sofía quiere grabar las clases: cambia la comparación (JaaS cobra la grabación aparte).
