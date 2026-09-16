# Orión — Resumen de negocio

**11 de septiembre de 2026.** Qué hace hoy la plataforma, cómo gana dinero, qué trabajo exige
operarla y qué decisiones siguen abiertas. Escrito para decidir, no para programar.

---

## Qué es Orión hoy

Un **marketplace de clases particulares de idiomas**. El estudiante busca profesor, ve su agenda,
reserva la hora que le sirve y paga en el momento. La clase se da por videollamada, dura 60 minutos
y empieza en punto. No hay matrícula, ni paquetes obligatorios, ni permanencia: se paga la clase que
se reserva.

Está **en producción** en `orionidiomas.com`, con el ciclo completo funcionando de punta a punta:
buscar, reservar, pagar, dar la clase, calificar y liquidar al profesor.

Tres idiomas en el catálogo (inglés, español y francés) y siete objetivos de aprendizaje
—conversación, negocios, exámenes, viaje, académico, entrevista y general—, que son los filtros con
los que un estudiante encuentra profesor.

---

## Cómo gana dinero

**Orión retiene el 15 % de cada clase.** La comisión se congela en el momento de la reserva: si
mañana se cambia el porcentaje, las clases ya vendidas conservan el suyo.

El recorrido del dinero:

1. El estudiante paga por **Wompi** (PSE, tarjeta o Nequi). Los datos de su tarjeta nunca pasan por
   Orión.
2. El cobro queda **retenido**. El profesor todavía no tiene ese dinero.
3. Cuando la clase se dicta, el pago **se libera**: pasa a ser del profesor y entra en la siguiente
   liquidación.
4. El administrador genera la liquidación de un período, **transfiere por fuera** y vuelve a
   marcarla como pagada con la referencia de la transferencia.

El profesor pone su tarifa, entre **$20.000 y $500.000** por hora. Ve el desglose antes de
guardarla: cuánto retiene Orión y cuánto le queda.

**El saldo a favor es un pasivo de Orión, no un descuento al profesor.** Cuando un estudiante paga
con saldo, el profesor cobra su tarifa completa y la comisión se calcula sobre el precio de la
clase. La diferencia la pone Orión.

---

## Lo que hoy puede hacer cada persona

### El estudiante
Se registra solo, busca profesor filtrando por idioma, nivel, objetivo, precio, días y franja
horaria, y reserva. Ve sus clases en agenda o calendario, entra a la videollamada desde la tarjeta o
desde el correo, y puede escribirle a su profesor por mensajería interna.

Si algo cambia, tiene salidas: **reprogramar** (proponer otro horario, sin tocar el pago),
**cancelar** (siempre se puede) y **reportar un problema** si el profesor no llegó. Ve su saldo a
favor y de dónde salió cada peso, califica a su profesor, y tiene un panel de progreso con rachas y
19 logros.

### El profesor
Se postula desde la web con un asistente: idiomas y niveles, objetivos, experiencia, estudios,
tarifa y documentos. Una persona revisa la postulación y decide. Mientras espera **no es un
estudiante**: no busca, no reserva, no tiene saldo — lo único que tiene es su solicitud.

Aprobado, publica su perfil, abre su disponibilidad semanal con excepciones puntuales, y recibe
reservas. Ve sus ganancias clase por clase con la comisión exacta, su desempeño (calificación,
cumplimiento, estudiantes distintos) y cualquier restricción activa con su motivo.

### La administración
Entra a un panel que dice qué espera una decisión: reclamos, pagos por revisar, sanciones propuestas
y reseñas reportadas. Si está todo en cero, lo dice.

Desde ahí: revisa postulaciones con sus documentos, concilia pagos, genera liquidaciones y las
exporta a CSV para la contadora, resuelve reclamos, atiende soporte, hace devoluciones, cambia los
ajustes de la plataforma con historial de quién cambió qué, e invita profesores por correo.

---

## Lo que ocurre sin que nadie lo pida

| Proceso | Cada cuánto | Qué hace |
|---|---|---|
| Cierre de clases | 1 hora | Cierra las terminadas hace más de 24 h sin reclamo y libera su pago. **Si se detiene, nadie cobra.** |
| Expiración de reservas | 5 minutos | Libera los cupos de quien no pagó a tiempo y devuelve el saldo aplicado |
| Métricas y ranking | 3:00 a. m. | Recalcula el desempeño de cada profesor y el orden del buscador |
| Vigilante | 15 minutos | Comprueba que los tres anteriores sigan corriendo y avisa por correo si uno se cae **y cuando vuelve** |

Además salen correos con marca en cada momento que importa: confirmación de clase con invitación de
calendario, cancelación, recuperación de contraseña, decisión sobre una postulación y avisos de
mensajes.

---

## Lo que cumple por ley

Esto no es una funcionalidad: es la condición para poder cobrar.

- **Términos y condiciones y política de tratamiento de datos**, versionados, con constancia de
  quién aceptó qué, cuándo, desde qué IP y con qué navegador.
- **Solo mayores de 18 años.** La ley colombiana exige autorización del representante legal para
  tratar datos de menores, y ese trámite no existe en la plataforma.
- **Derecho de retracto**: 5 días hábiles desde la compra, con devolución al medio de pago original
  dentro de los 15 días calendario siguientes.
- **Canal de soporte con plazos legales**: consulta de datos personales (10 días hábiles), reclamo
  de datos personales (15 días hábiles) y retracto (15 días calendario). La bandeja se ordena por lo
  que vence antes, no por lo más reciente.
- **Verificación de correo** antes de poder reservar, y límites de intentos en las puertas públicas.

---

## De qué tamaño es lo construido

| | |
|---|---|
| Pruebas automáticas | **391** de integración contra base de datos real · **171** unitarias · **50** de lógica del frontend |
| Pruebas de navegador | **15 de 16** (la que falta exige llaves de prueba de la pasarela) |
| Cambios de esquema | 32 migraciones versionadas |
| Módulos | 13 |

Cada regla de negocio con número —la comisión, las ventanas, los umbrales— **se cambia desde el
panel, no desplegando**. Y queda registrado quién la cambió y desde qué valor.

---

## Lo que todavía cuesta trabajo humano

- **Las transferencias a profesores.** Orión calcula y cuadra al peso, pero la transferencia la hace
  una persona.
- **Las devoluciones al medio de pago.** Wompi no lo permite por API: se hacen en su panel y aquí se
  deja la referencia.
- **El retracto** se detecta, se congela y se avisa solo; lo único manual es mover el dinero.
- **Soporte y devoluciones no aparecen en la primera fila del panel**, que es donde se mira cada
  mañana — y son justo las dos cosas con plazo legal. Hay que entrar a sus pantallas.

---

## Riesgos abiertos

**La videollamada es la dependencia más frágil.** Las salas son del servicio público de Jitsi, que
**pide no usarse con fines comerciales** y topa el uso en unos 25 usuarios activos al mes. Orión da
clases cobradas encima de eso. Además, ahí manda quien entra primero: si el estudiante se conecta
antes que el profesor, puede silenciar y expulsar. Se apretó lo que se podía sin cuenta nueva, pero
la solución real cuesta dinero: la opción recomendada arranca **gratis hasta 25 usuarios activos al
mes y son 99 USD/mes hasta 300**.

**Los textos legales no los ha revisado un abogado.** Siguen la ley artículo por artículo y las
secciones obligatorias están cubiertas por pruebas, pero eso no certifica que la redacción proteja.
Antes del primer cliente que paga en volumen, que los lea alguien habilitado para firmarlos.

**Las sanciones a profesores están en modo observación.** El sistema detecta ausencias y
cancelaciones de último momento, calcula la sanción que correspondería y la propone — pero no se
aplica sola. Es una decisión deliberada mientras la red de profesores sea pequeña.

**Queda una pieza sin comprobar contra el servicio real**: la apertura de los documentos de una
postulación desde el panel. El arreglo está hecho y probado, pero solo se confirma abriendo un PDF
en producción.

---

## Las cifras que definen el negocio

| Cifra | Qué es |
|---|---|
| **15 %** | Comisión de Orión, congelada en cada reserva |
| **60 min** | Duración de una clase, siempre en punto |
| **$20.000 – $500.000** | Rango de tarifa por hora del profesor |
| **20 minutos** | Lo que se aparta un cupo esperando el pago |
| **12 horas** | Frontera que decide el dinero al cancelar. Cancelar se puede siempre |
| **5 días hábiles** | Plazo de retracto, desde la compra |
| **15 días calendario** | Plazo legal para devolver al medio de pago |
| **24 horas** | Plazo para reclamar tras la clase, y para que se cierre sola |
| **18 años** | Edad mínima para tener cuenta |

Todas las de arriba, salvo las que fija una ley, se cambian desde el panel sin desplegar nada.
