/**
 * Lógica pura de teléfonos para el `PhoneInput`: lista curada de países (LatAm + US + ES) y el
 * parseo de un E.164 a país + número local. Separada del componente para poder testearla sin React.
 */
export const PAISES = [
  { code: "CO", flag: "🇨🇴", dial: "57" },
  { code: "MX", flag: "🇲🇽", dial: "52" },
  { code: "AR", flag: "🇦🇷", dial: "54" },
  { code: "CL", flag: "🇨🇱", dial: "56" },
  { code: "PE", flag: "🇵🇪", dial: "51" },
  { code: "EC", flag: "🇪🇨", dial: "593" },
  { code: "VE", flag: "🇻🇪", dial: "58" },
  { code: "US", flag: "🇺🇸", dial: "1" },
  { code: "ES", flag: "🇪🇸", dial: "34" },
];

// Indicativos más largos primero, para que "+593" gane sobre "+59"/"+5".
const POR_DIAL = [...PAISES].sort((a, b) => b.dial.length - a.dial.length);

/** Parte un E.164 (o dígitos sueltos) en país + número local. Colombia por defecto. */
export function parseTelefono(value?: string): { dial: string; local: string } {
  const digits = (value ?? "").replace(/\D/g, "");
  const pais = POR_DIAL.find((p) => digits.startsWith(p.dial));
  if (!pais) return { dial: "57", local: digits };
  return { dial: pais.dial, local: digits.slice(pais.dial.length) };
}

/** Compone el E.164 a partir de indicativo + número local. Local vacío → "" (sin teléfono). */
export function componerE164(dial: string, local: string): string {
  const digits = local.replace(/\D/g, "");
  return digits ? `+${dial}${digits}` : "";
}
