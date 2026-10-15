# Desde el admin, habilitar para adolescentes sin exigir los certificados; y avisarle al profe

## Metadatos

- **Fecha y hora:** 2026-10-15 10:10 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Commits relacionados:** (se completan)

## El pedido exacto

> Dejame que los certificados judiciales y de registro de inhabilidades SOLO DESDE EL PANEL DEL ADMIN, sean opcionales, es decir, yo puedo ver si los ha subido o no, pero puedo habilitarlo para adolescentes desde el administrador sin que esto lo bloquee, por favor. Además, no sé si ya lo haces pero por correo y por mensaje de Orion interno comunicale que ya está habilitado para dar clase a adolescentes también.

## Resumen ejecutivo

- **Como** admin, **quiero** poder encender «Habilitado para adolescentes» aunque el profe no haya
  subido la consulta del Registro ni los antecedentes **para** no quedarme bloqueado; sigo viendo qué
  subió y qué no.
- **Como** profe, **quiero** que me avisen por correo y por notificación dentro de Orión cuando me
  habiliten para adolescentes **para** saber que ya puedo recibir esas reservas.

Features: (1) el admin habilita sin los dos certificados (siguen visibles y siguen pidiéndose al profe);
(2) correo y notificación al profe al habilitarlo.

## Decisión tomada por Claude

El **código de conducta** sigue siendo obligatorio para habilitar: lo acepta el propio profe (con
constancia de versión, fecha e IP) y es la pieza que el abogado puso como condición; el admin no puede
aceptarlo por él. Solo los dos certificados pasan a ser opcionales para el admin.

## Cómo quedó

- `grant` solo exige el código de conducta; `missing` sigue listando los certificados que faltan y el
  admin los ve en el diálogo de Usuarios («Puedes habilitarlo igual; el profe los sube desde Mi perfil»).
  El botón «Habilitar por un año» se deshabilita solo si el profe no aceptó el código.
- Al habilitar: `MinorsClearanceGrantedEvent` → correo al profe («Ya puedes dar clase a adolescentes en
  Orión», con la fecha hasta la que vale y el recordatorio del código) y notificación `MINORS_CLEARED`
  en su campana, las dos AFTER_COMMIT. Antes no se le avisaba de ninguna forma.
- Prueba de integración: el admin habilita sin certificados, el profe recibe el correo y la notificación.

## Estado

**13:55, Pardo:** «Sí, hagamos ese despliegue» → se despliega con las pruebas de integración del tema en verde, sin esperar los e2e completos, junto con el arreglo de las 09:55.
