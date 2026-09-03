/**
 * Los límites de la ficha pública del profesor, en palabras. Espejo de `ProfessorProfile` en el
 * backend, que es quien manda: aquí solo se replican para poder avisar mientras se escribe.
 *
 * Enterarse de un límite al guardar —después de redactar tres párrafos— es la peor forma de
 * conocerlo, y con dos formularios que escriben los mismos campos (el perfil propio y la
 * postulación) tener la cuenta en un solo sitio evita que se separen.
 */
export const MIN_PALABRAS_TITULAR = 5;
export const MIN_PALABRAS_BIO = 20;
export const MAX_PALABRAS_BIO = 100;

/** La misma cuenta que hace el backend: palabras separadas por espacios. */
export function contarPalabras(texto: string): number {
  return texto.trim() ? texto.trim().split(/\s+/).length : 0;
}

export type EstadoTexto = { estado: "vacio" | "corto" | "largo" | "ok"; mensaje: string };

function plural(n: number): string {
  return `${n} ${n === 1 ? "palabra" : "palabras"}`;
}

/**
 * Qué decirle a quien escribe, según lo que lleva. Vacío no es un error —todavía no ha escrito—,
 * así que solo informa; pasarse o quedarse corto sí se marcan en rojo.
 */
export function estadoTitular(texto: string): EstadoTexto {
  const n = contarPalabras(texto);
  if (n === 0) return { estado: "vacio", mensaje: `Mínimo ${MIN_PALABRAS_TITULAR} palabras` };
  if (n < MIN_PALABRAS_TITULAR) {
    return {
      estado: "corto",
      mensaje: `${plural(n)}. Te faltan ${MIN_PALABRAS_TITULAR - n} para el mínimo.`,
    };
  }
  return { estado: "ok", mensaje: `${plural(n)}` };
}

export function estadoBio(texto: string): EstadoTexto {
  const n = contarPalabras(texto);
  if (n === 0) {
    return { estado: "vacio", mensaje: `Entre ${MIN_PALABRAS_BIO} y ${MAX_PALABRAS_BIO} palabras` };
  }
  if (n < MIN_PALABRAS_BIO) {
    return {
      estado: "corto",
      mensaje: `${plural(n)}. Te faltan ${MIN_PALABRAS_BIO - n} para el mínimo.`,
    };
  }
  if (n > MAX_PALABRAS_BIO) {
    return {
      estado: "largo",
      mensaje: `Te pasaste por ${plural(n - MAX_PALABRAS_BIO)}. Máximo ${MAX_PALABRAS_BIO}.`,
    };
  }
  return { estado: "ok", mensaje: `${n} de ${MAX_PALABRAS_BIO} palabras` };
}
