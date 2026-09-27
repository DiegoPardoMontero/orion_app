const PALABRAS = [
  "aurora", "brisa", "cielo", "duna", "estrella", "faro", "galaxia", "horizonte",
  "isla", "jardin", "lluvia", "manglar", "nube", "orbita", "puerto", "quinua",
  "rio", "selva", "trueno", "valle",
];

/**
 * Contraseña legible: tres palabras y un número. El admin se la va a dictar al usuario por
 * WhatsApp, así que tiene que poder leerse en voz alta sin equivocarse — nada de "xK9#p2".
 * Se genera con crypto, no con Math.random.
 */
export function generarClave(): string {
  const valores = new Uint32Array(4);
  crypto.getRandomValues(valores);

  const palabras = Array.from({ length: 3 }, (_, i) => PALABRAS[valores[i] % PALABRAS.length]);
  const numero = (valores[3] % 90) + 10; // dos dígitos

  return `${palabras.join("-")}-${numero}`;
}

/**
 * Fuerza de la contraseña para el medidor de 4 segmentos del registro. Un punto por cada regla:
 * longitud ≥8, mayúscula y minúscula, número y símbolo. El mensaje dice la regla que falta —nunca un
 * escueto «débil/fuerte»— para que el usuario sepa cómo mejorarla.
 */
export type FuerzaClave = { nivel: 0 | 1 | 2 | 3 | 4; mensaje: string };

const MINIMO = 8;

export function fuerzaClave(clave: string): FuerzaClave {
  if (!clave) return { nivel: 0, mensaje: `Mínimo ${MINIMO} caracteres` };

  const larga = clave.length >= MINIMO;
  const mayuscula = /[A-Z]/.test(clave);
  const minuscula = /[a-z]/.test(clave);
  const numero = /[0-9]/.test(clave);
  const simbolo = /[^A-Za-z0-9]/.test(clave);

  let puntos = 0;
  if (larga) puntos++;
  if (mayuscula && minuscula) puntos++;
  if (numero) puntos++;
  if (simbolo) puntos++;
  const nivel = puntos as 0 | 1 | 2 | 3 | 4;

  // La longitud va primero y tapa lo demás: el servidor rechaza una clave corta por variada que sea,
  // así que llamarla «fuerte» sería mentirle a quien la escribe.
  if (!larga) return { nivel, mensaje: `Muy corta todavía: usa al menos ${MINIMO} caracteres` };

  const falta = !mayuscula
    ? "una mayúscula"
    : !minuscula
      ? "una minúscula"
      : !numero
        ? "un número"
        : !simbolo
          ? "un símbolo"
          : null;
  if (!falta) return { nivel, mensaje: "Excelente contraseña" };
  if (nivel === 3) return { nivel, mensaje: `Fuerte: ${falta} la blinda` };
  if (nivel === 2) return { nivel, mensaje: `Vas bien: añade ${falta}` };
  // Con solo el largo le faltan dos reglas para llegar a «Fuerte» (no tiene número ni símbolo), así
  // que se nombran dos: con una sola, al cumplirla el botón seguiría apagado.
  return { nivel, mensaje: `Débil: súmale ${falta} y un número` };
}

/**
 * Si la clave sirve para crear la cuenta o cambiarla: «Fuerte» o «Excelente» (Pardo, 27/09/2026).
 * El nivel solo no basta: una clave corta y variada también llega a 3 puntos.
 */
export function esFuerte(clave: string): boolean {
  return clave.length >= MINIMO && fuerzaClave(clave).nivel >= 3;
}
