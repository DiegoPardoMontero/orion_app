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
