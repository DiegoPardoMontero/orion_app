import { fechaCorta, horaBogota } from "./format";

/**
 * WhatsApp es el canal real por el que estudiante y profesor coordinan, así que el link va con
 * el mensaje ya escrito: nadie tiene que redactar el "hola, soy fulano" desde cero.
 *
 * wa.me exige el número en dígitos, sin "+" ni espacios.
 */
export function linkWhatsapp({
  telefono,
  contraparte,
  yo,
  inicioIso,
}: {
  telefono?: string | null;
  contraparte: string;
  yo: string;
  inicioIso: string;
}): string | null {
  const digitos = (telefono ?? "").replace(/\D/g, "");
  if (!digitos) return null;

  const mensaje = `Hola ${contraparte}, soy ${yo}. Te escribo por nuestra clase de Orión del ${fechaCorta(
    inicioIso,
  )} a las ${horaBogota(inicioIso)}.`;

  return `https://wa.me/${digitos}?text=${encodeURIComponent(mensaje)}`;
}
