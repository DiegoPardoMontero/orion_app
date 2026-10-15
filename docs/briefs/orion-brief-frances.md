# Brief · El francés vuelve al catálogo (14/10/2026)

> Decide: Pardo (con Sofía) · Escribe: Claude · Pedido: `docs/pedidos/2026-10-14-2135-…`
> Decisión: **«Todo: catálogo, Meissa, acta y práctica»**. Primer lanzamiento con inglés y francés.

## Qué hay

El modelo multiidioma existe desde el inicio y la **V42 (15/09/2026) lo apagó sin desmontarlo**:
`languages.is_active = false` para todo lo que no sea EN, y se borró la oferta (no el histórico) de
francés de los profes. Casi toda la interfaz ya es condicional al número de idiomas activos:

| Pieza | Estado hoy | Qué hace falta |
|---|---|---|
| `languages` (EN, FR, ES sembrados) | FR inactivo | **V82**: `is_active = true` para FR. ES sigue apagado. |
| `GET /catalog/languages` | devuelve solo activos | nada |
| Postulación («Idiomas que enseñas») y Mi perfil | ya leen el catálogo; con un idioma no muestran selector | nada en código; aparece solo |
| Buscador `/profesores` y perfil del profe | selector si hay más de un idioma; tarjetas con banderas | nada en código |
| Landing `/idiomas/[code]` | existe por idioma activo | sitemap: listar `/idiomas/FR` |
| Reserva | `bookings.language_code` | si el profe enseña dos, el estudiante elige al reservar (ya existe, `[id]/page.tsx:231–290`) |
| Diagnóstico con Meissa | `ScenarioPrompts` ya traduce EN/FR/ES; el guion habla «{{LANGUAGE}}»; el frontend manda `languageCode: "EN"` fijo | **la puerta del diagnóstico pregunta el idioma** (inglés / francés) y lo manda; el resultado y la etiqueta dicen el idioma |
| `SignalExtractor.nativeSwitch` | detecta la «rama en español» por acentos `[ñáéíóúü¿¡]` | **en francés, `é` y `ü` son francés**: para FR el conjunto es `[ñáíóú¿¡]` + palabras funcionales. Prueba unitaria. |
| Traducción en vivo (`OpenAiTraductorDeFrases`) | traduce «lo que dice Meissa» al español | nada: la frase en francés se traduce igual |
| Acta (`LessonNoteService`) | pasa «inglés» para EN y el código crudo para otros | **nombre del idioma** («francés») reutilizando el mapa de `ScenarioPrompts` (moverlo a `shared`) |
| Práctica (`OpenAiPracticeGenerator`) | manda `Idioma de la clase: FR` | el prompt ya lo lee; comprobar que los ejercicios salen en francés |
| Textos del frontend que dicen «inglés» | 28 archivos, 68 menciones | los de producto («Arma la frase en inglés», «sin voz en inglés», «La palabra, en inglés») pasan a depender del idioma de la clase o a decir «el idioma»; los de la portada y «Enseña con Orión» dicen «inglés y francés» (Sofía los revisa) |
| Síntesis de voz del navegador en la práctica | `lib/voz.ts` busca voz `en-*` | elegir `fr-*` cuando la clase es de francés |
| Ensayo del admin | `IDIOMA_DEL_ENSAYO = "EN"` | se deja en inglés |
| Meissa, guion v5 | escrito para «{{IDIOMA}}» | **probar en francés con la llave real** (la local venció: Pardo pone una nueva) |

## Alcance cerrado

1. **V82** activa FR. Nada más en la base.
2. **Diagnóstico**: elegir idioma en la puerta (`Puerta.tsx`), `languageCode` real en el `POST`, el
   resultado nombra el idioma; `nativeSwitch` sabe de francés; el `lead` sin cuenta guarda el idioma
   (ya existe la columna).
3. **Acta y práctica**: nombre del idioma en el acta; textos de la práctica según el idioma; voz
   `fr-*`. Comprobar una práctica real en francés con la llave.
4. **Textos**: «inglés y francés» en portada, `/ensena-con-orion`, registro, correos de Rigel donde
   diga «inglés»; los de producto, por idioma de la clase. `TextosDelActaTest` y los e2e que fijan
   «Inglés» se actualizan (los e2e del profe eligen el botón «Inglés»: siguen funcionando porque el
   botón sigue existiendo; se añade un caso que agrega francés).
5. **Sitemap**: `/idiomas/FR`.
6. **Pruebas** (lo que quedó hecho el 14/10): `CatalogoDeIdiomasIT` comprueba que el catálogo
   público devuelve inglés y francés, y no español (es la V82); `ProfessorSearchIT` ya filtraba por
   FR; `SignalExtractorTest` tiene la frase en francés con acentos que **no** cuenta como español;
   los 105 e2e corren sobre la base con la V82. **Pendiente:** un e2e en que un profe agrega francés
   en Mi perfil y un visitante lo ve en `/idiomas/FR`, y la prueba de Meissa, el acta y la práctica
   en francés con la llave real.

## Fuera

- Español como idioma enseñado (ES sigue apagado).
- Traducir la interfaz al francés: Orión habla español con sus usuarios.
- Un diagnóstico en francés para menores (los adolescentes no tienen IA).
