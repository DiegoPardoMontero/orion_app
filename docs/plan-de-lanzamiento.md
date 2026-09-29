# Plan de lanzamiento de Orión (versión 2)

Recomendación del 28/09/2026 (pedido `docs/pedidos/2026-09-28-2051-como-lanzar-orion-por-fases.md`).
La versión 1 suponía que la academia ya tenía estudiantes y profes; Pardo aclaró que **arranca desde
cero: 0 estudiantes y 0 profes**, que piensan abrir en una semana y que pueden gastar unos
**$200.000 al mes en anuncios**. Esta versión reemplaza a la primera.

Cada condición sale de algo que ya está escrito en `docs/ESTADO.md` («Pendiente / bloqueos
conocidos») o en `docs/briefs/orion-brief-maestro-marketplace.md` (sección B, «Riesgos que no se
resuelven con código»).

## Lo primero: abrir en una semana, sí, pero a los profesores

Con 0 profes, abrirle Orión a estudiantes en una semana sería abrir una vitrina vacía: quien llegue
no encuentra con quién reservar, se va y no vuelve, y cada peso de anuncios paga esa visita. Es justo
lo que el brief maestro advierte (riesgo 6): antes de anuncios, al menos **8 a 10 profes aprobados y
publicados**.

Hoy `orionidiomas.com` ya es pública, con el catálogo abierto y sin profes. No hace daño mientras no
llegue nadie, y por eso no hay que ponerle anuncios todavía.

**Propuesta**: la semana que viene se abre, pero **a profesores**. A estudiantes que pagan se abre
cuando haya 8 a 10 profes publicados y el piloto haya salido bien: con buen ritmo, entre la semana 4 y
la 6.

## Semana 1 (del 29/09 al 5/10): abrir a profesores y cerrar la casa

### Conseguir los profes fundadores

- **Meta**: de 10 a 15 candidatos esta semana, para llegar a 8–10 publicados en tres o cuatro
  semanas.
- **Dónde buscarlos**:
  - la red de Sofía;
  - estudiantes y egresados de licenciaturas en idiomas;
  - grupos de profes de idiomas en Facebook y LinkedIn;
  - sobre todo, **profes que ya dan clases por su cuenta**, porque traen a sus propios estudiantes.
- **Con qué**, que ya está construido: la página «Enseña con Orión», la invitación de profe fundador
  desde Usuarios (15 % de comisión durante sus primeros 3 meses de clases) y la postulación con
  revisión.
- **Revisar el mismo día**: la postulación le promete al aspirante una respuesta en 3 días hábiles
  (Ajustes → «Plazo para revisar una postulación»). Con 12 personas esperando, tres días matan el
  impulso. O Sofía revisa el mismo día, o se baja ese plazo a 1.
- **Lo que se le ofrece al profe**:
  - cobro por Wompi (tarjeta, PSE, Nequi) sin perseguir a nadie;
  - su agenda con cupos y su aula virtual;
  - el acta de cada clase y la práctica con IA para su estudiante;
  - la liquidación cada quincena, con comprobante;
  - un perfil público y su propio enlace para compartir.

### Cerrar la casa (lo que se puede hacer en una semana)

| Qué | Quién |
|---|---|
| Todas las integraciones en verde en Administración → Sistema: Wompi, JaaS, Cloudinary, Resend, OpenAI, avisos | Pardo |
| Que `WOMPI_API_BASE_URL` apunte a producción (por defecto es el sandbox: se «cobraría» dinero de prueba sin enterarse) | Pardo |
| El webhook de JaaS (sin él, la antesala dice «aún no ha entrado» en cada clase real) | Pardo |
| Rotar la llave privada de Wompi y la de OpenAI (pasaron por el chat) y poner un tope mensual de gasto en OpenAI | Pardo |
| **Una transacción real de punta a punta**, con Pardo de estudiante y el primer profe: una clase pagada por Wompi en producción, una devolución desde el panel de Wompi que aparece como saldo y, en el corte siguiente, la liquidación por Bre-B con su comprobante | Pardo + primer profe |
| Probar si la IP real llega detrás del proxy de Railway (`X-Forwarded-For`) | Claude |
| Que Sofía lea el texto del resultado del diagnóstico: es el único lugar donde Orión le dice a alguien algo sobre sí mismo | Sofía |

### Agendar esta semana (su respuesta va antes del primer cobro a alguien que no sea de la familia)

- **Contador**: facturación electrónica de la comisión, retención en la fuente y el contrato de
  mandato (pendiente desde el 02/09). Hoy el responsable legal que muestran los textos es Pardo como
  persona (`ORION_LEGAL_*`). Si eso alcanza para facturar comisiones y liquidar a los profes, o si
  hace falta una sociedad, lo responde el contador, no el código.
- **Abogado laboral**: que la relación con el profe sea de contratista. La comisión obligatoria, la
  no-elusión y las sanciones juntas son la combinación que se examina (brief maestro, riesgo 2).
- **Abogado**: la política de datos 1.2 tiene que nombrar a OpenAI, a 8x8 y lo que se toma al entrar
  con Google o Facebook. La línea del beneficio de fundador va en los Términos del profesor.

## Semanas 2 y 3 (del 6 al 19/10): fundadores publicando y piloto con su gente

La «gente de casa» de la versión 1 no existe, así que las dos fases van juntas: los primeros
fundadores publican y cada uno invita a 2 o 3 de sus estudiantes o conocidos. Pueden usar la primera
clase gratis y después pagar clases reales pequeñas.

- **Cruzar un corte de liquidación**: las clases del piloto entre el 5 y el 14 de octubre entran al
  corte del 16/10 y se pagan alrededor del 20 o 21 de octubre (tercer día hábil, con festivos). Así
  una quincena completa —corte, aprobar, pagar, comprobante— corre una vez con gente real.
- **Lo que hay que ver funcionar al menos una vez**: registro, postulación, aprobación, perfil y
  horarios, reserva, pago, clase por JaaS en celular y en computador, acta, práctica, calificación,
  cancelación de cada lado, reclamo, devolución y liquidación.
- **Sale cuando**:
  - toda la lista pasó;
  - no hubo ningún error con el dinero;
  - menos de 1 de cada 10 clases tuvo una falla técnica;
  - la quincena se pagó a tiempo;
  - el contador y el abogado respondieron.

## Semanas 4 a 6: estudiantes en grupo controlado, sin anuncios

**El motor**:
- los estudiantes que traen los profes;
- el contenido de Sofía (el video de bienvenida, redes);
- los referidos;
- el diagnóstico gratis como puerta de entrada.

**Metas de partida** (son referencias iniciales para ajustar con datos reales, no cifras de la
industria):

| Métrica | Meta de partida |
|---|---|
| De registrado a primera clase | 40 % o más |
| De clase de prueba gratis a primera clase pagada | 30 % o más |
| Segunda clase dentro de los 14 días siguientes a la primera pagada | 50 % o más |
| Clases con falla técnica | menos del 5 % |
| Clases con reclamo | menos del 3 % |
| Ausencias (del profe o del estudiante) | menos del 10 % |

**Semáforo**:
- **Verde**, se abre al público: 8–10 profes publicados y las metas se cumplen dos semanas seguidas.
- **Amarillo**, se ajusta y se mide otra vez: la gente se registra pero no llega a clase, o no
  repite.
- **Rojo**, se para: hay errores con el dinero o las clases fallan. Se arregla antes de seguir.

## Los anuncios: con $200.000 al mes son un termómetro, no el motor

- **Mes 1: nada en anuncios para estudiantes**, porque no hay profes que ofrecerles. Si reclutar
  profes va lento, una parte puede ir a promocionar «Enseña con Orión».
- **Desde el mes 2**, con el semáforo en verde: los $200.000 van al diagnóstico gratis, la entrada con
  menos fricción. Se mide el costo por diagnóstico, por registro y por primera clase: los clics los da
  la plataforma de anuncios, y los registros y las clases, la administración de Orión.
- **La cuenta**: registros al mes = $200.000 ÷ costo por registro. El costo por registro no se sabe
  hasta medirlo, y con este presupuesto van a ser pocos. Por eso el crecimiento de los primeros meses
  viene de los profes y sus estudiantes, no de los anuncios.

## Las cuentas del negocio

- **Lo que deja una clase**: a $45.000, entre $6.750 (15 %, profe fundador) y $9.000 (20 %), antes de
  lo que cobre Wompi por transacción.
- **Punto de equilibrio**: clases al mes para cubrir los costos = costos fijos mensuales ÷ lo que deja
  una clase. Los costos fijos son Railway, OpenAI, JaaS, Resend, Cloudinary, el dominio y los
  anuncios.
  - *Ejemplo ilustrativo, no son las cifras de Orión*: con $500.000 al mes de costos, harían falta
    unas 74 clases al mes al 15 %, o 56 al 20 %.
  - Con la cifra real, este número dice cuántos profes activos y cuántas clases por profe hacen
    falta.

## Decisiones de Pardo

1. **El 15 % sobre los estudiantes que el profe ya tenía.** El fundador que trae a sus propios
   alumnos le paga a Orión el 15 % de esas clases. Lo que recibe a cambio está arriba («Lo que se le
   ofrece al profe»). Es la objeción que va a oír de casi todos los profes la primera semana, y la
   respuesta es de Pardo.
2. **Qué pasa con una clase pagada que el estudiante cancela**: saldo a favor o devolución. Abierta
   desde el 02/09.
3. **Si la reseña de una clase de prueba gratis cuenta en el ranking** (revisión de seguridad del
   25/09).
4. Las sanciones siguen en modo observación: con pocos profes, nada se oculta sin que una persona lo
   mire (decisión del 02/09).

## No frena el lanzamiento

- Los avisos en el dispositivo: Pardo dice «no es vital», y los recordatorios también van por la
  campana y el correo.
- La política de seguridad del navegador (CSP).
- Que el avatar personalizado solo lo vea su dueño.
- El resto de la lista de seguridad «para decidir» del 22/09.

## Lo que falta saber

- **Los costos fijos mensuales de hoy.** Con esa cifra, el punto de equilibrio deja de ser un ejemplo.
