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
 * longitud ≥8, mayúscula y minúscula, número y símbolo. El mensaje describe qué falta —nunca un
 * escueto «débil/fuerte»— para que el usuario sepa cómo mejorarla.
 */
export type FuerzaClave = { nivel: 0 | 1 | 2 | 3 | 4; mensaje: string };

const MENSAJES_FUERZA = [
  "Mínimo 8 caracteres",
  "Muy corta todavía",
  "Débil — súmale una mayúscula",
  "Vas bien — añade un número",
  "Fuerte — un símbolo la blinda",
  "Excelente contraseña",
] as const;

export function fuerzaClave(clave: string): FuerzaClave {
  if (!clave) return { nivel: 0, mensaje: MENSAJES_FUERZA[0] };

  let nivel = 0;
  if (clave.length >= 8) nivel++;
  if (/[a-z]/.test(clave) && /[A-Z]/.test(clave)) nivel++;
  if (/[0-9]/.test(clave)) nivel++;
  if (/[^A-Za-z0-9]/.test(clave)) nivel++;

  const n = nivel as 0 | 1 | 2 | 3 | 4;
  return { nivel: n, mensaje: MENSAJES_FUERZA[n + 1] ?? MENSAJES_FUERZA[5] };
}
