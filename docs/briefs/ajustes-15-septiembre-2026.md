# AJUSTES 15 DE SEPTIEMBRE (2026)

Encargo de Pardo del 15/09/2026, recogido literalmente y **en espera**. La condición que él mismo
puso: *«necesito que primero funcione todo perfecto de lo de la videollamada»*. No se empieza nada
de aquí hasta que lo diga.

Es una tanda grande y no homogénea: hay un cambio de modelo de negocio, uno de modelo de datos, un
rediseño de la mascota, reorganización de navegación y un bug de agendamiento. Conviene trocearlo
cuando llegue el momento, no atacarlo de una.

---

## 1. Transversal · Orión enseña SOLO inglés

**El más grande, y el que toca más capas.** Hoy la plataforma es multiidioma de verdad: hay tabla
`languages`, `professor_languages`, `professor_language_levels`, filtro por idioma en el buscador,
`language_code` en `bookings`, landings por idioma (`/idiomas/[code]`), el `DiscoIdioma`, insignias
EN/FR/ES en gamificación y el diagnóstico de voz parametrizado por `{{IDIOMA}}`.

Hay que revisar **archivos, términos, imágenes, botones, lógica y modelo de datos**.

> **Decisión pendiente antes de tocar nada:** ¿se *borra* el multiidioma o se *esconde*? Borrarlo es
> más limpio de leer y mucho más caro de revertir; esconderlo (una sola fila en `languages`, el
> selector fuera) deja la puerta abierta a francés sin rehacer el esquema. Preguntar a Pardo.

## 2. Pantalla 404

- La estrella parece hacer **un gesto grosero** con la mano derecha (la izquierda vista desde ella).

## 3. Landing

- Fuera el trío **IDIOMA / OBJETIVO / HORARIO**. Se quiere algo más versátil, del estilo
  «Agenda tu primera clase de prueba, hoy, sin costo», o que empuje al diagnóstico:
  «Prueba tu inglés en 2 minutos y recibe un diagnóstico real gratis».
- El banner final con Rigel **también parece hacer el gesto grosero**.
- **Quitar por completo la sección de Idiomas.**
- Añadir el **Método ORION®** con animaciones, color y presencia: tiene que leerse como
  acompañamiento real, no como un bloque más. Texto base en el anexo de abajo.
- La sección **Nosotros** no gusta: se quiere **minimalista** — una palabra grande y luego la
  descripción. Y más color.
- **Revisar la estrella (Rigel) en todo el sitio.** Las manos tienen algo raro: la parte inferior
  lleva una línea semicircular que no encaja, y el dedo extra a veces queda muy raro. Se pide
  además una **mano normal negra**, de dedos delgados, completamente negra y muy caricaturesca,
  para usar donde haga falta.
- Más color en **«Cómo funciona Orión»**.

## 4. Estudiante

### Buscar profesor
- **Banner horizontal completo pero bajo de alto**, con Rigel, que motive a estudiar hoy / a
  encontrar al profe correcto.
- Los filtros gustan, pero **todo lo de idiomas hay que revisarlo** (ver punto 1).

### Perfil
- **«Protegida» sigue sin entenderse.** No se cambió.
- «Mi ficha», «Tus datos», «Si hay que cancelar» y «Preguntas frecuentes» deben ser **secciones o
  páginas separadas**.
- **«Mi cielo» se siente desconectado**: incorporarlo dentro del perfil.
- **El avatar personalizado (marco, accesorios) tiene que verse en TODA la plataforma**, en cada
  sitio donde aparezca ese perfil. Hoy no se propaga.

### Pagos y saldo
- Reordenar: **lo más abajo posible**.

### Navegación
- **«Buscar profesor» dentro de «Mis clases»**, quizá renombrando el apartado.
- **Poder borrar notificaciones.**

## 5. Profesor

### Disponibilidad
- Mostrar **el día completo de 5:00 a 23:00** aunque el profesor no haya abierto nada todavía.

### Perfil
- Igual que el estudiante: **separar «cancelar» y «preguntas frecuentes»** en otras secciones.

## 6. Transversal · Los campos no deben parecer editables

Para estudiante y profesor: los campos no pueden aparecer como editables directamente. Debe haber
un **botón «Editar» o un lápiz** que habilite la escritura.

## 7. Transversal · Notificaciones por detrás

En algunas pantallas el panel de notificaciones **se abre detrás de otra capa** y no se ve.
Revisar el apilamiento en **todas** las pantallas.

## 8. Transversal · Medias horas

**No se puede reservar a las 12:30, 1:30, etc.** Se quiere permitir **cada media hora**: 5:00,
5:30, 6:00, 6:30… Revisar la interfaz de disponibilidad y lo que haga falta.

> **Ojo, esto es más profundo de lo que parece.** `SlotCalculator.SLOT_CADENCE` está en 1 hora y la
> clase dura 55 minutos justamente para dejar 5 de margen entre clases. Con cadencia de 30 minutos,
> dos clases consecutivas se pisarían: 5:00–5:55 y 5:30–6:25 se solapan. Hay que decidir **qué pasa
> con la duración**: o las clases bajan a 25/30 minutos, o la cadencia de 30 minutos solo aplica al
> *inicio* y el motor debe impedir solapes reales (que ya hace, vía el índice único, pero entonces
> abrir 5:30 inutiliza 5:00 y 6:00, y eso hay que mostrarlo en la interfaz o el profesor no lo
> entenderá). **Preguntar a Pardo antes de implementar.**
>
> Nota suya: *«no hay profes reales aún»* — o sea que se puede cambiar el esquema sin migrar datos.

---

## Anexo · Método ORION®

Marco de acompañamiento del aprendizaje, **no** metodología impuesta al profesor. Cada profesor
mantiene plena libertad pedagógica; el método vive en la capa de seguimiento y feedback con IA, que
estructura el progreso del estudiante sobre teoría educativa establecida:

- **O**bserve — *hipótesis del noticing* (Schmidt) e input comprensible (Krashen): identificar lo
  que el estudiante aún no percibe en el idioma.
- **R**elate — *aprendizaje significativo* (Ausubel): conectar lo nuevo con lo que el estudiante ya
  sabe y con su contexto real.
- **I**nteract — *hipótesis de la interacción y del output* (Long, Swain): el aprendizaje se
  consolida produciendo lenguaje, no solo recibiéndolo.
- **O**ptimize — *modelo de feedback efectivo* (Hattie & Timperley) y repetición espaciada:
  corrección priorizada sobre 2-3 puntos por clase, reforzada en el tiempo.
- **N**avigate — *feed forward y aprendizaje autorregulado* (Zimmerman): cada clase cierra con el
  siguiente paso concreto y medible dentro de la zona de desarrollo próximo (Vygotsky).
