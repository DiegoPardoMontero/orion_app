# Plan de lanzamiento de Orión (versión 3: la retoma del 14/10/2026)

Pedido `docs/pedidos/2026-10-14-1847-retomar-el-proyecto-y-lanzar-pronto.md`. El proyecto estuvo en
pausa del 08/10 al 14/10. Esta versión reemplaza a la 2 (del 28/09): conserva su lógica —**primero los
profesores, y a estudiantes que pagan solo con 8 a 10 profes publicados**— y vuelve a anclar las
semanas en hoy. Lo que no cambió (anuncios, cuentas del negocio, metas y semáforo) está al final, tal
cual estaba.

## Dónde estamos (comprobado el 14/10/2026 por la tarde)

**El código.**
- `master` local y `origin/master` son el mismo commit. Lo último es el «Backup snapshot» de Pardo del
  08/10, que subió los arreglos de la revisión del 06–07/10 (reprogramar nunca movía la clase, el
  retracto perdía el saldo, el reclamo de la clase gratis daba 500, la purga fallaba, el último admin).
- `./mvnw verify` a solas en verde el 07/10 (452 unitarias + 693 de integración). Nada del backend
  cambió desde entonces.
- Hoy: `next build` en verde. Los e2e sobre base limpia: ver «Verificación» en `docs/ESTADO.md`.
- **No se puede saber desde fuera si Railway desplegó el push del 08/10.** Se mira en Railway →
  Deployments: el último despliegue debe decir el commit `add3313`. Si dice uno anterior, los arreglos
  del 07/10 no están en producción.
- **UAT no existe** (`uat.orionidiomas.com` no resuelve) y `develop` va 19 commits detrás de `master`.
  Mientras no se monte, todo va a `master` y Railway lo despliega; no hay puerta intermedia.

**Producción.**
- `orionidiomas.com` responde. El catálogo público muestra **4 perfiles publicados**: Mariana Quiroga
  Lozano, Valentina Ríos Betancur y «Diego Pardo Test 28/09» son cuentas de prueba de Pardo
  (`+orion2…6`); **Andrés Rosero** no aparece entre las pruebas y hay que confirmar si es real.
- **Nadie se postuló durante la pausa**: el último correo que Orión mandó fue el 30/09 (un
  recordatorio de Rigel a una cuenta de prueba). Cero postulaciones nuevas, cero tickets.
- `robots.txt` sigue cerrando `/profesores` (anotado el 07/10, aún sin arreglar).

**Tres cosas nuevas, con fecha, que no estaban en ningún documento.**
1. **OpenAI apaga `gpt-4.1-nano` el 23/10/2026** (correo del 08/10). Es el modelo por defecto de
   `orion.assessment.translation.model`: la traducción en vivo de las frases de Meissa en el
   diagnóstico, con corte de 5 s. El 23/10 esa traducción empieza a fallar en silencio (se descarta
   sin reintentar). Arreglo: pasar el valor por defecto a `gpt-5-nano` (el comentario del propio
   código lo midió en 1–3 s, dentro del corte) y comprobar que ninguna variable de Railway fije el
   modelo viejo. Los demás modelos (`gpt-5-mini`, `gpt-realtime-2.1-mini`, `gpt-4o-mini-transcribe`)
   no están en la lista.
2. **Railway tiene un límite duro de USD 10 al mes y va en USD 7,51** (correo del 14/10; el ciclo
   cierra el 16/10). Pasado el límite, «no se pueden crear despliegues». Cada push a `master` es un
   despliegue. **Subir el límite antes del próximo push y, en todo caso, antes de lanzar.**
3. **Wompi facturó** (factura WO680298, 02/10). No es un problema; es un costo fijo que entra en el
   punto de equilibrio y hasta ahora no se había contado.

## Lo que cambió el 14/10 por la noche (pedido de las 21:35)

Pardo y Sofía deciden que el primer lanzamiento es **con inglés y francés** y que **incluye
adolescentes (13 a 17)**, con la propuesta del Bloque 12 **aprobada por el abogado tal cual**. Orden
elegido: **profes ya, con francés; adolescentes antes de abrir a estudiantes.** El francés entra en la
semana 0; adolescentes ocupa las semanas 1 y 2 en paralelo con el reclutamiento. El fundador por
invitación ya funciona (se otorga al aprobar) y el botón «Hacer fundador» ya existe en Usuarios.

## Semana 0 (14 al 18/10): cerrar la casa, arreglar lo que muerde al invitar y reabrir el francés

El objetivo de la semana es poder invitar al primer profe real, de inglés o de francés, el lunes 19/10
sin que nada le falle.

**Claude, en este orden** (cada paso con su prueba, `verify` en verde y un commit):
1. ✅ Los e2e sobre base limpia (105 en verde) y el push de lo del 07/10.
2. ✅ `gpt-5-nano` como traductor de Meissa.
3. ✅ **La invitación por WhatsApp se consume también al registrarse con Google.**
4. ✅ **El admin corrige un correo mal escrito y reenvía la confirmación.**
5. ✅ `robots.txt` abre `/profesores`.
6. ✅ El reclamo de un ensayo no deja ausencia al profe (decisión del 14/10).
7. ✅ **Francés** (`docs/briefs/orion-brief-frances.md`, commit `304a9fc`): FR activo, la puerta del
   diagnóstico pregunta el idioma, Meissa, acta y práctica lo nombran, textos «inglés y francés»,
   `/idiomas/FR` en el sitemap. **Falta** probarlo con la llave real de OpenAI (la local venció).
8. Las dos tareas restantes del 07/10 van **después** de abrir a estudiantes, porque solo muerden ahí:
   la purga borra también los archivos de Cloudinary, y resolver un reclamo a favor del profe sobre
   una clase aún abierta publica el cierre. `@Version` queda para después del lanzamiento.

**Pardo** (todo es configuración en paneles; el detalle está en `docs/cerrar-la-casa.md`):
- **Una llave nueva de OpenAI en `~/.orion/openai-key`** (la local venció) y comprobar en Sistema que
  la de producción siga viva: sin ella no se puede probar Meissa en francés, y si producción usa la
  misma, el diagnóstico ya está caído.
- ✅ Railway: límite subido (14/10). Falta mirar en Deployments qué commit corre.
- Administración → Sistema: todas las integraciones encendidas; «Pagos (Wompi)» debe decir
  **Producción**, no SANDBOX (`WOMPI_API_BASE_URL`).
- El webhook de JaaS (sin él la antesala dice «aún no ha entrado» en cada clase real).
- Rotar la llave privada de Wompi y la de OpenAI; tope mensual de gasto en OpenAI.
- Apagar «Perfil visible» de «Diego Pardo Test 28/09» (sigue en el catálogo) y decidir qué pasa con
  Mariana y Valentina, que son de prueba y también se ven.
- En producción: `select count(*) from reschedule_requests where status = 'ACCEPTED'`. Cada fila es
  una clase que se quedó en su hora original mientras a los dos se les dijo otra (bug del 07/10).
- Plazo de revisión de postulaciones: bajarlo a 1 día en Ajustes, o que Sofía revise el mismo día.
- Agendar al contador (facturación de la comisión, retención, mandato) y al abogado (contratista;
  la política de datos 1.2 con OpenAI, 8x8 y el login social).

**Sofía:** leer el texto del resultado del diagnóstico y la página «Enseña con Orión»: es lo primero
que va a ver cada profe invitado.

## Semanas 1 y 2 (19/10 al 1/11): los profes fundadores, y adolescentes en construcción

**Adolescentes (Bloque 12, aprobado por el abogado el 14/10; pasos 1 a 8 construidos la noche del
14 al 15/10, faltan los puntos por hijo y lo de los 18 años):** cuenta familiar bajo el adulto, sin
usuario propio, sin IA ni grabación; autorización del representante legal con constancia; profes
habilitados por el admin tras revisar el código de conducta, la consulta del Registro de inhabilidades
(Ley 1918) y los antecedentes, con renovación anual; sin franja horaria; textos legales 1.2 que se
piden al entrar. Tiene que estar listo **antes de que entre el primer estudiante que paga**.

- **Meta:** 10 a 15 candidatos, para llegar a 8–10 publicados a comienzos de noviembre. Dónde
  buscarlos y qué ofrecerles no cambió: está en la versión 2 (al final).
- **La transacción real de punta a punta** con el primer profe y Pardo de estudiante: clase pagada por
  Wompi en producción, cancelación con más de 12 h que vuelve como saldo, acta y práctica.
- **El corte del 16/10 no paga nada** (no hubo clases). **El del 1/11** recoge las clases del 16 al
  31/10 y se paga el tercer día hábil: el 2/11 es festivo, así que **alrededor del 5/11**. Esa es la
  primera quincena real: corte, aprobar, pagar por Bre-B, comprobante.
- **Sale cuando:** registro, postulación, aprobación, perfil, horarios, reserva, pago, clase por JaaS
  en celular y computador, acta, práctica, calificación, cancelación de cada lado, reclamo, devolución
  y liquidación se vieron funcionar al menos una vez; ningún error con el dinero; el contador y el
  abogado respondieron.

## Semanas 3 a 5 (2 al 22/11): estudiantes en grupo controlado, sin anuncios

Los estudiantes que traen los profes, el contenido de Sofía, los referidos y el diagnóstico gratis.
Las metas y el semáforo son los de la versión 2 (abajo). **Verde dos semanas seguidas con 8–10 profes
publicados = se abre al público**, con anuncios desde ahí.

## Decisiones de Pardo

1. **El 15 % sobre los estudiantes que el profe ya tenía.** Sigue abierta desde el 28/09 y es la
   objeción que va a oír de casi todos los profes la primera semana.
2. **`@Version` en reserva y pago** (revisión del 07/10): sin él hay carreras de milisegundos que las
   constraints no cierran. Opciones: ponerlo ahora, o anotarlo y esperar a tener volumen.
3. **La ausencia que deja un ensayo del admin** (07/10): un ensayo puede dejarle una falta real al
   profe. Opciones: que el ensayo nunca cuente, o dejarlo y avisar al admin.
4. **Qué hacer con los perfiles de prueba** que hoy se ven en el catálogo.
5. ~~Clase pagada que el estudiante cancela~~ y ~~reseña de la clase de prueba~~: resueltas (ver v2).

## No frena el lanzamiento

Los avisos en el dispositivo, la CSP, el avatar personalizado visible para otros, el resto de la
lista de seguridad del 22/09, el freno de intentos de login que no saltó en producción el 02/10 (se
revisa con los registros de Railway antes de abrir al público, no antes de invitar profes).

---

# Lo que no cambió desde la versión 2 (28/09/2026)

## Conseguir los profes fundadores

- **Dónde buscarlos**: la red de Sofía; estudiantes y egresados de licenciaturas en idiomas; grupos
  de profes de idiomas en Facebook y LinkedIn; sobre todo, **profes que ya dan clases por su cuenta**,
  porque traen a sus propios estudiantes.
- **Con qué**, que ya está construido: la página «Enseña con Orión», la invitación de profe fundador
  desde Usuarios (por correo o por WhatsApp; 15 % de comisión durante sus primeros 3 meses de clases)
  y la postulación con revisión.
- **Lo que se le ofrece al profe**: cobro por Wompi (tarjeta, PSE, Nequi) sin perseguir a nadie; su
  agenda con cupos y su aula virtual; el acta de cada clase y la práctica con IA para su estudiante; la
  liquidación cada quincena, con comprobante; un perfil público y su propio enlace para compartir.

## Metas de partida y semáforo (semanas de estudiantes)

| Métrica | Meta de partida |
|---|---|
| De registrado a primera clase | 40 % o más |
| De clase de prueba gratis a primera clase pagada | 30 % o más |
| Segunda clase dentro de los 14 días siguientes a la primera pagada | 50 % o más |
| Clases con falla técnica | menos del 5 % |
| Clases con reclamo | menos del 3 % |
| Ausencias (del profe o del estudiante) | menos del 10 % |

- **Verde**, se abre al público: 8–10 profes publicados y las metas se cumplen dos semanas seguidas.
- **Amarillo**, se ajusta y se mide otra vez: la gente se registra pero no llega a clase, o no repite.
- **Rojo**, se para: hay errores con el dinero o las clases fallan. Se arregla antes de seguir.

## Los anuncios: con $200.000 al mes son un termómetro, no el motor

- **Nada en anuncios para estudiantes** hasta el verde. Si reclutar profes va lento, una parte puede
  ir a promocionar «Enseña con Orión».
- **Con el semáforo en verde**: los $200.000 van al diagnóstico gratis. Se mide el costo por
  diagnóstico, por registro y por primera clase.
- **La cuenta**: registros al mes = $200.000 ÷ costo por registro. El crecimiento de los primeros
  meses viene de los profes y sus estudiantes, no de los anuncios.

## Las cuentas del negocio

- **Lo que deja una clase**: a $45.000, entre $6.750 (15 %, profe fundador) y $9.000 (20 %), antes de
  lo que cobre Wompi por transacción.
- **Punto de equilibrio**: clases al mes = costos fijos mensuales ÷ lo que deja una clase. Los costos
  fijos son Railway (hoy con tope de USD 10), OpenAI, JaaS, Resend, Cloudinary, Wompi, el dominio y
  los anuncios. *Ejemplo ilustrativo*: con $500.000 al mes de costos, unas 74 clases al mes al 15 %, o
  56 al 20 %.
- **Lo que falta saber**: los costos fijos mensuales de hoy.
