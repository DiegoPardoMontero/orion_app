import type { FichaEstudiante } from "@/lib/gamificacion";

/**
 * Lo que hace completa una ficha (24/09/2026): foto, nivel, idioma, para qué lo aprende y su
 * motivación. Es la misma regla que usa el backend para el logro «Ficha completa» y para los
 * recordatorios; aquí solo se nombra lo que falta, en palabras.
 */
export function faltanDeLaFicha(f: Pick<FichaEstudiante, "photoUrl" | "selfDeclaredLevel" | "primaryLanguage" | "goalCodes" | "motivation">): string[] {
  const faltan: string[] = [];
  if (!f.photoUrl) faltan.push("tu foto");
  if (!f.selfDeclaredLevel) faltan.push("tu nivel");
  if (!f.primaryLanguage) faltan.push("el idioma");
  if (f.goalCodes.length === 0) faltan.push("para qué lo aprendes");
  if (!f.motivation?.trim()) faltan.push("tu motivación");
  return faltan;
}

/** Los puntos del logro «Ficha completa» (V58). */
export const PUNTOS_FICHA_COMPLETA = 25;
