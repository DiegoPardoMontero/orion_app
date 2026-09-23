/**
 * Un renderizador de markdown del subconjunto exacto que usan los documentos legales de Orión:
 * títulos de nivel 2 y 3, párrafos, negrita, enlaces, listas con viñeta, listas numeradas y tablas.
 *
 * No es un markdown completo y no pretende serlo. La alternativa era una dependencia de 40 kB en
 * una página pública para renderizar texto que escribimos nosotros y cuyo formato controlamos; con
 * un subconjunto cerrado y probado, la página pesa lo mismo que el texto.
 *
 * Devuelve un árbol de bloques, no HTML: quien pinta decide la tipografía, y nunca hay una cadena
 * de HTML sin escapar cerca de un `dangerouslySetInnerHTML`.
 */

export type Inline =
  | { tipo: "texto"; texto: string }
  | { tipo: "fuerte"; texto: string }
  | { tipo: "enlace"; texto: string; href: string };

export type Bloque =
  | { tipo: "titulo"; nivel: 2 | 3; contenido: Inline[] }
  | { tipo: "parrafo"; contenido: Inline[] }
  | { tipo: "lista"; ordenada: boolean; items: Inline[][] }
  | { tipo: "tabla"; encabezados: Inline[][]; filas: Inline[][][] };

/** `**negrita**` y `[texto](url)`. Lo demás es texto tal cual. */
export function parseInline(linea: string): Inline[] {
  const salida: Inline[] = [];
  // Una sola pasada con dos alternativas: el enlace primero, para que un `**` dentro de la
  // etiqueta de un enlace no parta el enlace en dos.
  const patron = /\[([^\]]+)\]\(([^)]+)\)|\*\*([^*]+)\*\*/g;
  let ultimo = 0;
  let m: RegExpExecArray | null;

  while ((m = patron.exec(linea)) !== null) {
    if (m.index > ultimo) {
      salida.push({ tipo: "texto", texto: linea.slice(ultimo, m.index) });
    }
    if (m[1] !== undefined) {
      salida.push({ tipo: "enlace", texto: m[1], href: m[2] });
    } else {
      salida.push({ tipo: "fuerte", texto: m[3] });
    }
    ultimo = m.index + m[0].length;
  }
  if (ultimo < linea.length) {
    salida.push({ tipo: "texto", texto: linea.slice(ultimo) });
  }
  return salida.length > 0 ? salida : [{ tipo: "texto", texto: "" }];
}

/** Parte una fila de tabla en celdas, tolerando los pipes de los extremos. */
function celdas(linea: string): string[] {
  return linea
    .replace(/^\s*\|/, "")
    .replace(/\|\s*$/, "")
    .split("|")
    .map((c) => c.trim());
}

const ES_SEPARADOR_TABLA = /^\s*\|?[\s:-]+\|[\s:|-]*$/;

export function parseMarkdown(fuente: string): Bloque[] {
  const lineas = fuente.replace(/\r\n/g, "\n").split("\n");
  const bloques: Bloque[] = [];
  let i = 0;

  while (i < lineas.length) {
    const linea = lineas[i];

    if (linea.trim() === "") {
      i += 1;
      continue;
    }

    const titulo = /^(#{2,3})\s+(.*)$/.exec(linea);
    if (titulo) {
      bloques.push({
        tipo: "titulo",
        nivel: titulo[1].length === 2 ? 2 : 3,
        contenido: parseInline(titulo[2].trim()),
      });
      i += 1;
      continue;
    }

    // Tabla: una fila de encabezados seguida de la fila separadora. Sin separador no es tabla,
    // es un párrafo que casualmente lleva pipes.
    if (linea.includes("|") && i + 1 < lineas.length && ES_SEPARADOR_TABLA.test(lineas[i + 1])) {
      const encabezados = celdas(linea).map(parseInline);
      const filas: Inline[][][] = [];
      i += 2;
      while (i < lineas.length && lineas[i].includes("|") && lineas[i].trim() !== "") {
        filas.push(celdas(lineas[i]).map(parseInline));
        i += 1;
      }
      bloques.push({ tipo: "tabla", encabezados, filas });
      continue;
    }

    const vinieta = /^\s*[-*]\s+(.*)$/.exec(linea);
    const numerada = /^\s*\d+\.\s+(.*)$/.exec(linea);
    if (vinieta || numerada) {
      const ordenada = numerada !== null;
      const items: Inline[][] = [];
      while (i < lineas.length) {
        const item = ordenada
          ? /^\s*\d+\.\s+(.*)$/.exec(lineas[i])
          : /^\s*[-*]\s+(.*)$/.exec(lineas[i]);
        if (!item) break;
        items.push(parseInline(item[1].trim()));
        i += 1;
      }
      bloques.push({ tipo: "lista", ordenada, items });
      continue;
    }

    // Párrafo: líneas seguidas hasta un blanco o el comienzo de otro bloque.
    const parrafo: string[] = [];
    while (
      i < lineas.length &&
      lineas[i].trim() !== "" &&
      !/^#{2,3}\s/.test(lineas[i]) &&
      !/^\s*[-*]\s+/.test(lineas[i]) &&
      !/^\s*\d+\.\s+/.test(lineas[i])
    ) {
      parrafo.push(lineas[i].trim());
      i += 1;
    }
    bloques.push({ tipo: "parrafo", contenido: parseInline(parrafo.join(" ")) });
  }

  return bloques;
}

/**
 * El ancla de un encabezado, para enlazar a una sección desde fuera: «7. Cancelaciones y
 * reprogramación» es `#cancelaciones-y-reprogramacion`. Sin el número, que puede moverse si se
 * inserta una sección antes, y sin tildes.
 */
export function anclaDe(contenido: Inline[]): string {
  return contenido
    .map((i) => i.texto)
    .join("")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/^\s*\d+[.)]?\s*/, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}
