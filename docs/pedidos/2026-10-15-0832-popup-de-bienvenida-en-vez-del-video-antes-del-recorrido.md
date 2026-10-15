# Un pop-up grande de «¡Bienvenido a Orión!» en vez del video, justo antes del recorrido

## Metadatos

- **Fecha y hora:** 2026-10-15 08:32 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Commits relacionados:** `012d779` y `6062333` (en `develop`, sin desplegar)

## El pedido exacto

> El video de bienvenida no se va a mostrar en este momento. Quiero entonces que JUSTO antes de iniciar el recorrido haya un pop-up grande que diga: "Bienvenido a Orion!" y que sea muy llamativo, con el logo, que diga que estamos felices de tenerlo con nosotros y queremos que saque el mayor provecho de la plataforma y de sus clases cuanto antes.

## Resumen ejecutivo

- **Como** persona que entra por primera vez, **quiero** que antes del recorrido aparezca un
  pop-up grande y llamativo de «¡Bienvenido a Orión!», con el logo, que diga que estamos felices de
  tenerme y que quieren que saque el mayor provecho de la plataforma y de mis clases cuanto antes,
  **para** sentirme recibido.
- **Como** dueño, **quiero** que el video de bienvenida no se muestre por ahora **para** no enseñar
  algo que todavía no está.

Features: (1) el video de bienvenida se apaga (no se borra: vuelve cuando Pardo lo pida); (2) un
pop-up de bienvenida grande, con el logo, justo antes de que empiece el recorrido; (3) todo en
`develop`, sin desplegar.

## Cómo quedó

- El pop-up es la **primera pantalla del recorrido** la primera vez: el amanecer con la constelación y el
  logotipo «ORIÓN ✦» arriba, «¡Bienvenido a Orión!» grande, Rigel celebrando, el saludo («Estamos
  felices de tenerte con nosotros, <nombre>. Queremos que saques el mayor provecho de la plataforma y
  de tus clases cuanto antes.»), la línea «Te mostramos Orión en N pasos» y el botón «Empezar». Al
  profe recién aprobado no se le ofrece «Ahora no» (su recorrido sigue siendo obligatorio).
- Repetido desde Ayuda sale el inicio de siempre: a quien ya está dentro no se le da la bienvenida otra vez.
- **El video:** no se toca el código; se muestra solo si el ajuste `professor_welcome_video_url` tiene
  una URL, y por defecto está vacío. Si en producción lo llenaste, vacíalo en Ajustes.

## Estado

**Hecho en `develop`, sin desplegar.** Se despliega con el lote del 15/10 cuando Pardo lo diga.
