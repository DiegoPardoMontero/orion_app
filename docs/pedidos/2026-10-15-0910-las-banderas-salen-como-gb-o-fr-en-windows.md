# Las banderas salen como «GB» o «FR» en vez de la bandera (Windows); arreglarlo

## Metadatos

- **Fecha y hora:** 2026-10-15 09:10 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code (mensaje enviado mientras se trabajaba el lote del 15/10)
- **Sesión:** f5bedb76
- **Commits relacionados:** `f3134b2` (en `develop`, sin desplegar)
- **Contexto:** producción con usuarios reales; ya se están registrando aspirantes a profesor.

## El pedido exacto

> Muchas veces en la aplicación no sale la bandera sino que sale GB o FR, incluyendo muchas veces cuando el profe se registra. Imaigno que es por el emoji en Windows o algo así. ARREGLA ESO, es fundamental que eso se vea bien. YA ESTAMOS EN PROD, ten cuidado porque ya se están registrando aspirantes a profesores.

## Resumen ejecutivo

- **Como** usuario de Orión en Windows (o en cualquier equipo sin fuente de emojis de banderas),
  **quiero** ver la bandera de cada idioma y país como imagen **para** que la pantalla se vea bien y
  no como dos letras sueltas («GB», «FR»).

## Qué pasa

Las banderas son emojis de «indicadores regionales» (🇬🇧 = las letras G y B en una fuente de emoji).
Windows no trae esa fuente para banderas y pinta las letras; el Chromium sin fuentes de emoji (como
el de las capturas automáticas) pinta cajas. Es un problema del sistema, no de Orión, así que la
solución es no depender del emoji: banderas en SVG.

## Cómo se arregla

- Un componente `<Bandera>` que pinta la bandera como **SVG** (paquete `flag-icons`, 271 banderas),
  a partir del código ISO («CO») o del emoji que manda el catálogo de idiomas («🇫🇷» se traduce a
  «FR»). Sin código válido no pinta nada.
- Se cambia en **todos** los sitios: el selector de país de la postulación y del perfil, el indicativo
  del WhatsApp (el `<select>` nativo no admite imágenes: muestra «CO +57» y la bandera va encima como
  imagen), la puerta de Meissa, la portada de cada idioma, la ciudad con país en la tarjeta del profe,
  su perfil público, Mi perfil, la revisión de la postulación y la del admin.
- El buscador de la portada usa un `<select>` nativo: ahí va solo el nombre del idioma, sin bandera.
- El disco del idioma («EN», «FR» en un círculo de color) no es una bandera y se queda como está: es
  diseño, no un emoji roto.

## Estado

**Hecho en `develop`, sin desplegar.** Capturas revisadas a 390 px: la puerta de Meissa, el catálogo, el perfil del profe y el indicativo del WhatsApp muestran la bandera como imagen.
