# Plan de lanzamiento de Orión

Recomendación del 28/09/2026 (pedido `docs/pedidos/2026-09-28-2051-como-lanzar-orion-por-fases.md`).
Cada condición sale de algo que ya está escrito en `docs/ESTADO.md` («Pendiente / bloqueos
conocidos») o en `docs/briefs/orion-brief-maestro-marketplace.md` (sección B, «Riesgos que no se
resuelven con código»). Las duraciones son estimadas; las fechas las pone Pardo.

## El principio: primero los profesores

Un marketplace sin profesores no convierte a nadie: el brief maestro pide al menos 8–10 profesores
aprobados y publicados, repartidos entre los idiomas, antes de gastar un peso en anuncios (riesgo 6).
Y el 23/09 ya se decidió lanzar primero a los profesores. Todo lo de abajo se ordena alrededor de eso.

## Fase 0 — Cerrar la casa (1 a 2 semanas, antes de que entre nadie de afuera)

Lo que solo pueden cerrar Pardo, el contador y el abogado va primero, porque no se arregla con código.

| Qué | Quién | De dónde sale |
|---|---|---|
| Facturación electrónica de la comisión, retención en la fuente y el contrato de mandato | Contador | Brief maestro, riesgo 3; pendiente desde el 02/09 |
| Si la relación con el profesor es de contratista: comisión obligatoria + no-elusión + sanciones | Abogado laboral | Brief maestro, riesgo 2 |
| Política de datos 1.2: nombrar a OpenAI, a 8x8 y lo que se toma al entrar con Google o Facebook | Abogado; Claude publica la versión | ESTADO, pendiente |
| La línea del beneficio de fundador en los Términos del profesor | Abogado | Decisiones del 25/09 |
| Todas las integraciones en verde en Administración → Sistema: Wompi, JaaS, Cloudinary, Resend, OpenAI, avisos | Pardo | Memoria «integraciones sin verificar» |
| Revisar que `WOMPI_API_BASE_URL` apunte a producción (por defecto es el sandbox: se «cobraría» dinero de prueba sin enterarse) | Pardo | ESTADO, config de producción |
| El webhook de JaaS configurado (sin él, la antesala dice «aún no ha entrado» en cada clase real) | Pardo | ESTADO, pendiente |
| **Una transacción real de punta a punta**: una clase pequeña pagada por Wompi en producción, una devolución desde el panel de Wompi que aparece como saldo, y una liquidación por Bre-B con su comprobante | Pardo + un profe de confianza | Nunca se ha hecho; las pruebas e2e saltan Wompi a propósito |
| Rotar la llave privada de Wompi y la de OpenAI (las dos pasaron por el chat) y poner un tope mensual de gasto en OpenAI | Pardo | ESTADO, pendiente |
| Probar si la IP real llega detrás del proxy de Railway (`X-Forwarded-For`) | Claude | ESTADO, seguridad |
| Decidir qué pasa con una clase pagada que el estudiante cancela: saldo a favor o devolución | Pardo | Decisión abierta desde el 02/09 |
| Decidir si la reseña de una clase de prueba gratis cuenta en el ranking | Pardo | Revisión de seguridad del 25/09 |
| Que Sofía lea el texto del resultado del diagnóstico: es el único lugar donde Orión le dice a alguien algo sobre sí mismo | Sofía | ESTADO, pendiente desde el 17/09 |
| Sanciones en modo observación (como están): con pocos profes, nada se oculta sin que una persona lo mire | — | Decisión del 02/09 |

**No frenan el lanzamiento** (se atienden en paralelo): los avisos en el dispositivo (Pardo: «no es
vital»; los recordatorios también van por la campana y el correo), la política de seguridad del
navegador (CSP), que el avatar personalizado solo lo vea su dueño, y el resto de la lista de
seguridad «para decidir» del 22/09.

**Sale de la Fase 0 cuando**: el contador y el abogado respondieron, Sistema está todo en verde y la
transacción real cerró completa, incluido el comprobante de pago al profe.

## Fase 1 — Gente de casa (2 semanas)

**Quiénes**: Pardo, Sofía, 2 o 3 profes de mucha confianza de Sofía, y 5 a 10 estudiantes cercanos
(familia, amigos o alumnos actuales de la academia que acepten probar).

**Cómo**:
- Cuentas reales y pagos reales pequeños. Si se prefiere no cobrarles, el admin puede darles saldo
  («Ajuste de Orión»), pero al menos tres clases tienen que pasar por Wompi.
- **La prueba tiene que cruzar un corte de liquidación** (00:00 del 1 o del 16), para que una
  quincena completa —corte, aprobar, pagar, comprobante— corra una vez con gente real.

**Lista de lo que hay que ver funcionar al menos una vez**: registro, postulación, aprobación, perfil
y horarios, reserva, pago, clase por JaaS en celular y en computador, acta, práctica, calificación,
cancelación de cada lado, reclamo, devolución y liquidación.

**Sale de la Fase 1 cuando**:
- toda la lista pasó;
- no hubo ningún error con el dinero;
- menos de 1 de cada 10 clases tuvo una falla técnica;
- la quincena se pagó a tiempo.

## Fase 2 — Profes fundadores (2 a 3 semanas; puede empezar al final de la Fase 1)

**Meta**: 8 a 10 profes publicados, repartidos entre los idiomas, con perfil completo, horarios
abiertos y, idealmente, la primera clase gratis encendida.

**Cómo**:
- **Antes** de que un profe que ya trabaja con Sofía entre a Orión, la conversación sobre cómo va a
  ganar: pasar de tarifa fija a comisión cambia su ingreso (brief maestro, riesgo 1). Esa charla va
  antes, no cuando llegue la primera liquidación con menos plata.
- Por la invitación con revisión: 15 % de comisión durante sus primeros 3 meses de clases.
- Una sesión de bienvenida en grupo de 30 minutos, y un grupo de WhatsApp de fundadores para
  preguntas y comentarios.
- Cada profe invita a sus propios estudiantes con su enlace: es la primera demanda, y la más tibia.

**Sale de la Fase 2 cuando**: hay 8 a 10 profes publicados y cada uno tiene al menos una clase
dictada en Orión.

## Fase 3 — Estudiantes en grupo controlado (4 semanas, sin anuncios)

**Quiénes**: la base de la academia, los estudiantes que invitan los profes y los referidos.

**Qué medir y metas de partida** (son referencias iniciales para ajustar con datos reales, no cifras
de la industria):

| Métrica | Meta de partida |
|---|---|
| De registrado a primera clase | 40 % o más |
| De clase de prueba gratis a primera clase pagada | 30 % o más |
| Segunda clase dentro de los 14 días siguientes a la primera pagada | 50 % o más |
| Clases con falla técnica | menos del 5 % |
| Clases con reclamo | menos del 3 % |
| Ausencias (del profe o del estudiante) | menos del 10 % |

**Semáforo**:
- **Verde**, se abre al público: todas las metas se cumplen dos semanas seguidas.
- **Amarillo**, se ajusta y se mide otra vez: la gente se registra pero no llega a clase, o no
  repite.
- **Rojo**, se para: hay errores con el dinero o las clases fallan. Se arregla antes de seguir.

## Fase 4 — Apertura pública (desde la semana 8 a 10, si la Fase 3 dio verde)

- Anuncios pequeños y solo en Colombia, el video de Sofía y el programa de referidos.
- **La cuenta que decide si el anuncio es una apuesta o un plan**: una clase de $45.000 le deja a
  Orión entre $6.750 (15 %, fundador) y $9.000 (20 %), antes de lo que cobre Wompi por transacción.
  Un estudiante que toma 8 clases deja entre $54.000 y $72.000. Si conseguir a ese estudiante por
  anuncios (CAC) cuesta $40.000, casi toda la ganancia se va en traerlo. El presupuesto sube solo
  cuando se sabe cuántas clases toma en promedio un estudiante y cuánto cuesta traerlo.

## Lo que falta saber para ajustar este plan

1. Cuántos estudiantes activos y cuántos profes tiene hoy la academia, y si hoy pagan.
2. La fecha a la que se apunta para abrir al público.
3. El presupuesto mensual para anuncios.
