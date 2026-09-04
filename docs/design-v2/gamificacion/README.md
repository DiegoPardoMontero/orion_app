# Handoff: Gamificación Orión — Etapas 1 y 2 (v2 · lenguaje constelación)

Capa nueva sobre «Amanecer cálido premium». No cambia Rigel, ni el sistema de diseño, ni las pantallas ya entregadas.

## Estado

- **Etapa 1 · Direcciones** — hecha. Elegida **1c · Híbrida «Sello»**: la foto real se conserva; el progreso la rodea (órbitas, paletas, cielos) y un sello de nivel bajo la foto cambia de *forma*. Los 3 accesorios se anclan al sello.
- **Etapa 2 · Sistema completo (v2)** — hecha. Corrección de encargo aplicada: el lenguaje gráfico parte de la **constelación**. Los logros son estrellas; el escalón es brillo; el tablero es un cielo; los marcos son órbitas.
- **Etapa 3 · 7 pantallas en 390 y 1280 px** — pendiente.

## Archivos

- `Orión Gamificación · Etapa 1.dc.html` — las tres direcciones (1a, 1b, 1c) y seis muestras de logro (1d, lenguaje anterior). Referencia histórica.
- `Orión Gamificación · Etapa 2 v2.dc.html` — **fuente de verdad del sistema.** Secciones:
  - 2a · gramática de una estrella: tres brillos, tres estados, dos colores
  - 2b · el cielo: cinco constelaciones con coordenadas, reglas de líneas, cameo de Rigel
  - 2c · las 20 estrellas × 3 estados, con archivo y condición de desbloqueo
  - 2d · sello por niveles, zonas de anclaje y los 3 accesorios
  - 2e · 8 órbitas (marcos), 6 paletas, 6 cielos (fondos) — CSS puro, con condición
  - 2f · el encendido: cuatro cuadros clave + mini guía de motion + `prefers-reduced-motion`
  - 2g · racha: activa / protegida / «empezamos de nuevo»
  - 2h · insignias de idioma (EN · FR · ES, regla para DE)
  - 2i · tokens `@theme` nuevos
- `brief-gamificacion.md` — el encargo original.
- `support.js` — runtime para abrir los `.dc.html` en el navegador. No va al producto.

Los SVG de logros se generan desde datos en la clase `Component` del archivo v2 (`renderVals`): ahí están `circ`, `rect`, `poly`, los paths de los glifos, `brilloDef` y el inventario `raw` (familia · slug · nombre · condición · brillo · glifo/numeral · progreso de muestra). Exportarlos a archivos es una transcripción directa. `skyDef` tiene las coordenadas de las figuras del cielo.

## Contrato visual (resumen)

**Lienzo.** Todo SVG en `viewBox="0 0 512 512"`, centro óptico (256, 256).

**Estrella de logro.**
- Forma: cinco puntas, radio exterior 220, interior 118, trazo 50 `stroke-linejoin: round` `paint-order: stroke fill` del mismo color del relleno (puntas redondeadas).
- Brillo (escalón, por forma): 1 = estrella · 2 = + halo r 246 trazo 8 · 3 = + halo + cuatro rayos cardinales (4→30 y 482→508). Apagada, halo y rayos se dibujan discontinuos: el brillo se lee aunque no esté encendida.
- Color por familia: durazno `#FFC189` → primeros pasos, constancia, volumen; lavanda `#B9A7E6` → amplitud, compromiso. Glifo y contornos en tinta `#33203B`.
- Glifo Lucide: `translate(172 172) scale(7)`, `stroke-width 1.75`. Numerales en Bricolage Grotesque 800.
- Estados:
  - *apagada*: contorno discontinuo 10 px (16/14) al 34 %, glifo al 30 %, candado en (416, 416) r 60.
  - *progreso*: órbita r 240 (circunferencia 1508; `dasharray = avance × 1508 1508`, `rotate(-90)`), estrella al 80 % en contorno continuo, pastilla «n de N».
  - *encendida*: relleno degradado de la familia, resplandor radial r 250 (55 % → 0) detrás, glifo al 100 %.
- Nomenclatura: `logro-{familia}-{slug}-{apagada|progreso|encendida}.svg`.

**Cielo (tablero).** Fondo `--gradient-sky` (amanecer cortado antes del durazno). Figuras fijas por familia (coordenadas en 2b sobre 1728×560). Una línea entre dos estrellas se dibuja cuando ambas están encendidas; la figura cierra al completar la familia; los tramos pendientes van discontinuos al 22 %. Apagadas sobre el cielo: contorno crema al 35 %. En 390 px se apila por familia. Rigel: un solo cameo en pose Guía (90 px) señalando la próxima estrella; no aparece en móvil ni con el cielo completo.

**Sello.** Nivel 1 (registro) 4 puntas contorno · Nivel 2 (8 semanas acumuladas) 5 puntas rellenas · Nivel 3 (24 semanas) con 8 rayos, tinta invertida. `sello-nivel-{1|2|3}.svg`.

**Zonas de anclaje (orden fijo).**
- z1 Base · caja (136, 392) 240×100 · ancla superior-centro (256, 392) → `accesorio-base-orbita.svg`
- z2 Centro · círculo (256, 256) r 80 → `accesorio-centro-monograma.svg`
- z3 Corona · caja (146, 20) 220×110 · ancla inferior-centro (256, 130) → `accesorio-corona-constelacion.svg`
Un accesorio por zona; nada sale del círculo r 240; nada tapa más del 40 % de la estrella.

**Encendido (celebración).** 720 ms, cuatro cuadros: trazo (0–240, `stroke-dashoffset`→0) · encendido (240–480, `scale 1.08`, anillo crema que se expande) · reposo (480–720, glifo + tres destellos). Solo `transform`, `opacity`, `stroke-dashoffset`. Si cierra un tramo, la línea se dibuja después (320 ms). Reducido: cuadro final con fade 200 ms, sin destellos ni Rigel. Rigel cameo pose Celebración 80 px solo en la versión completa.

**Racha.** Ocho semanas como estrellas de cuatro puntas: cumplida `#E8764F` · en curso contorno · futura discontinua al 30 % · protegida lavanda con escudo. «Empezamos de nuevo» cambia solo número y copy.

**Idioma.** Disco 512 con código ISO 639-1 en Bricolage 800 (190/512, tracking −6). EN lavanda · FR durazno · ES tinta. Siguiente idioma: siguiente color (DE ciruela `#7A4A8C`). Sin banderas.

**Tokens.** Bloque `@theme` completo en 2i. Ningún color nuevo; el coral queda fuera de la gamificación.

## Copy

Español (Colombia). Se nombra lo que la persona hizo. Prohibido contar ausencias. Ejemplos aprobados en 2g.
