/**
 * La tarifa como la escribe un colombiano: «50.000». Con un campo numérico eso se leía 50 y el
 * profe veía «La tarifa debe estar entre $20.000 y $500.000» sin entender por qué (Pardo,
 * 27/09/2026). El campo es de texto: se queda con los dígitos y los muestra con puntos de miles.
 */

/** Hasta nueve cifras: de sobra para ver el aviso de «más de $500.000» sin que el número se desborde. */
const MAX_CIFRAS = 9;

/** «50.000», «50,000», «50 000» y «$50.000» son 50000. Sin ceros a la izquierda. */
export function soloDigitos(texto: string): string {
  return texto.replace(/\D/g, "").replace(/^0+(?=\d)/, "").slice(0, MAX_CIFRAS);
}

/** «50000» → «50.000». A mano y no con Intl: el campo no puede depender de cómo agrupe cada navegador. */
export function conMiles(digitos: string): string {
  return digitos.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}

/**
 * Dónde dejar el cursor después de reformatear: justo detrás de la misma cantidad de cifras que
 * tenía a su izquierda. Sin esto, escribir en medio del número lo mandaba al final.
 */
export function posicionTrasCifras(formateado: string, cifras: number): number {
  if (cifras <= 0) return 0;
  let vistas = 0;
  for (let i = 0; i < formateado.length; i++) {
    if (/\d/.test(formateado[i]) && ++vistas === cifras) return i + 1;
  }
  return formateado.length;
}
