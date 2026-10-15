# «Francés e inglés» en todo, los dos certificados obligatorios, sus enlaces y un previsualizador

## Metadatos

- **Fecha y hora:** 2026-10-15 08:14 (America/Bogota, UTC−5)
- **Canal:** chat de Claude Code
- **Sesión:** f5bedb76
- **Briefs relacionados:** `docs/briefs/orion-brief-frances.md`, `docs/briefs/orion-bloque-12-adolescentes.md`
- **Commits relacionados:** `8033bce` (textos), `b51e693` (enlaces), `1115d89` (previsualizador y el ensayo en el idioma del profe); todos en `develop`
- **Regla nueva de Pardo:** «YA ESTOY MANEJANDO USUARIOS REALES, NO tumbes prod y avísame cuando estos
  cambios estén listos antes de hacer despliegue.» → nada se sube a `master` (Railway despliega
  `master`) hasta que él lo diga; el trabajo va en la rama `develop`.

## El pedido exacto

> Sí, está perfecto. Una cosa más, cambia en todo los sitios donde se pueda que diga "Francés e Inglés",
> por ejemplo vi que en la landing no está perfecto esto. Cuando pidas en el registro del profe el
> certificado de Consulta de Registro por Inhabilidades y el Certificado de Antecedentes Judiciales,
> esto no debería ser obligatorio? Y un detalle, coloca el link directamente en Orion en esa pantalla
> para que puedan ir a consultarlo sin tener que ellos mismos hacer la búsqueda en Google. Que con un
> link les abra la página donde lo consultan. Quiero también un pre-visualizador de los documentos que
> han subido, algo donde puedan ver preview o el link real. YA ESTOY MANEJANDO USUARIOS REALES, NO
> tumbes prod y avísame cuando estos cambios estén listos antes de hacer depsliegue.

## Resumen ejecutivo

- **Como** visitante o usuario de Orión, **quiero** que en todos los textos se lea «francés e inglés»
  (los dos idiomas, en ese orden) **para** que nadie crea que Orión es solo de inglés. Pardo vio que
  en la portada «no está perfecto».
- **Como** Orión, **quiero** que la consulta del Registro de inhabilidades y el certificado de
  antecedentes judiciales sean **obligatorios** para el profe **para** que todos los profes del
  lanzamiento, que incluye adolescentes, estén verificados.
- **Como** profe, **quiero** un **enlace directo** en esa pantalla a la página oficial donde se
  consulta cada certificado **para** no tener que buscarla en Google.
- **Como** profe (y como admin), **quiero** **previsualizar** los documentos que subí, o abrir el
  archivo real, **para** confirmar que subí el correcto.
- **Como** dueño con usuarios reales, **quiero** que nada se despliegue hasta que yo lo diga.

Features: (1) textos «francés e inglés» en portada, páginas públicas, correos y pantallas; (2) los dos
certificados obligatorios (dónde, según la respuesta de Pardo abajo); (3) enlaces oficiales de la
Policía Nacional en la pantalla de documentos; (4) previsualizador de documentos subidos (PDF e
imágenes en la pantalla, y el enlace al archivo); (5) todo en `develop`, sin push a `master`.

## Preguntas a Pardo (08:20, con AskUserQuestion)

1. **¿Dónde obligatorios los dos certificados?** Opciones: en la postulación para enviarla; para
   publicarse; solo para dar clase a adolescentes (como hoy). **Pardo: «Solo para dar clase a
   adolescentes (como hoy)».** Siguen opcionales; se añaden los enlaces oficiales y el previsualizador.
2. **¿Orden de los idiomas?** «Francés e inglés» en todo, o dejar «inglés y francés» y completar lo que
   falta. **Pardo: «Dejar "inglés y francés", completar lo que falta».**

## Enlaces oficiales (verificados el 15/10/2026)

- Consulta de inhabilidades por delitos sexuales contra menores (Ley 1918 de 2018), Policía Nacional:
  `https://inhabilidades.policia.gov.co:8080/`
- Consulta de antecedentes judiciales, Policía Nacional: `https://antecedentes.policia.gov.co:7005/WebJudicial/`

## Lo hecho (15/10, 08:15–09:00)

- **Textos:** la portada («Por qué ahora» y la misión), la pregunta frecuente de Meissa, los
  ejemplos del titular del profe y de la motivación del estudiante, y el cierre de la práctica ya
  nombran los dos idiomas. Las cifras del 2,2 % y el 24 % se quedan: su fuente habla de inglés.
- **Documentos:** siguen siendo opcionales (respuesta 1). En «Clases con adolescentes» cada uno lleva
  el enlace oficial de la Policía Nacional. **Para Pardo y el abogado:** el formulario de inhabilidades
  pide nombre y NIT de la *empresa consultante*; está pensado para que una entidad consulte a un
  candidato, así que un profe que se consulte a sí mismo puede tener fricción.
- **Previsualizador:** el profe (postulación y «Clases con adolescentes») y el admin (revisión de la
  postulación) ven el PDF o la imagen dentro de Orión, con «Abrir en otra pestaña» y «Descargar». Los
  bytes pasan por el backend con la sesión; el enlace de Cloudinary no sale del servidor.
- **De paso:** el ensayo del admin tomaba siempre inglés; ahora toma el primer idioma del profe.
- **Probado en local:** `verify` completo, tsc, lint, vitest y los e2e. El previsualizador con
  Cloudinary real **no** se pudo probar en local (el secreto solo está en Railway): en local
  muestra el aviso «No podemos abrir documentos en este momento».

## Pardo, 09:15

> Cuando puedas hacer push y despliegue (me importa sobre todo el video de bienvenida para los profes) y bueno estaría perfecto que arregles lo demás, pero avisame cuando todo esté listo para hacer un despliegue y yo te autorizo.

Lo que más le importa es el pop-up de bienvenida de los profes (pedido de las 08:32). El lote de
`develop` del 15/10 (08:14, 08:32, 08:40, 08:45, 09:05 y 09:10) se despliega entero cuando él lo
autorice, tras el aviso de «listo».

**09:20:** «Dale, me mandas el correo entonces, pero lo más pronto posible que puedas terminar todo,
mejor.» → el aviso de «listo» va por correo a diegopardomontero@gmail.com, además del chat.

**09:44, Pardo:** «Adelante, despliega, intenta que sea lo más rápido posible y avísame cuando
terminaste.» → `master` avanzó a `c57c2e4` (fast-forward de `develop`) y se hizo push a las 09:44:55;
Railway despliega solo.

## Estado

**Desplegado** (push a `master` 15/10 09:44). Pendiente de que Pardo abra un documento real en el
previsualizador. Cuando Pardo diga, se hace merge a `master` y push; Railway lo
despliega. Lo primero que hay que mirar en producción es el previsualizador con un documento real.
